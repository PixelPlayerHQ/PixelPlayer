package com.theveloper.pixelplay.youtube

import android.content.Context
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liked YouTube Music tracks. These lists are not device files.
 */
@Singleton
class YoutubeMusicStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _liked = MutableStateFlow(read(KEY_LIKED))
    private val _recent = MutableStateFlow(read(KEY_RECENT))
    private val _likedSongIds = MutableStateFlow(idsOf(_liked.value))

    val liked: StateFlow<List<YoutubeHit>> = _liked.asStateFlow()
    val recent: StateFlow<List<YoutubeHit>> = _recent.asStateFlow()
    val likedSongIds: StateFlow<Set<String>> = _likedSongIds.asStateFlow()
    private val catalog = LinkedHashMap<String, CatalogTrack>()
    private val creditLookups = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private val _catalogVersion = MutableStateFlow(0)
    val catalogVersion: StateFlow<Int> = _catalogVersion.asStateFlow()

    init {
        synchronized(catalog) {
            readCatalog().forEach { track -> catalog[track.songId] = track }
        }
        rememberHits(_liked.value + _recent.value)
    }

    fun toggleLike(hit: YoutubeHit) {
        rememberHits(listOf(hit))
        _liked.value = toggle(_liked.value, hit)
        _likedSongIds.value = idsOf(_liked.value)
        persist(KEY_LIKED, _liked.value)
    }

    fun toggleLike(song: Song) {
        hitFromSong(song)?.let(::toggleLike)
    }

    fun rememberRecent(hit: YoutubeHit) {
        if (hit.url.isBlank() || hit.kind != YoutubeHit.Kind.TRACK) return
        val next = listOf(hit) + _recent.value.filterNot { it.url == hit.url }
        _recent.value = next.take(MAX_RECENT)
        persist(KEY_RECENT, _recent.value)
        rememberHits(listOf(hit))
    }

    fun rememberRecent(song: Song) {
        hitFromSong(song)?.let(::rememberRecent)
    }

    /** Keep titles and artists for tracks that are not in the on-device library. */
    fun rememberHits(hits: List<YoutubeHit>) {
        val tracks = hits.filter { it.kind == YoutubeHit.Kind.TRACK && it.url.isNotBlank() }
        if (tracks.isEmpty()) return
        var changed = false
        synchronized(catalog) {
            tracks.forEach { hit ->
                val (artist, album) = credits(hit)
                if (upsert(
                        CatalogTrack(
                            songId = YoutubeClient.songIdForUrl(hit.url),
                            title = hit.title.trim(),
                            artist = artist,
                            album = album,
                            artworkUrl = hit.thumbnailUrl,
                            videoId = YoutubeClient.videoIdFromUrl(hit.url),
                        )
                    )
                ) {
                    changed = true
                }
            }
            if (changed) persistCatalog()
        }
        if (changed) _catalogVersion.value += 1
    }

    fun rememberPlayback(
        songId: String,
        title: String,
        artist: String,
        album: String,
        artworkUrl: String?,
        videoId: String = "",
    ) {
        val id = songId.trim()
        if (!id.startsWith("yt_") || title.isBlank()) return
        val changed = synchronized(catalog) {
            val updated = upsert(
                CatalogTrack(
                    songId = id,
                    title = title.trim(),
                    artist = cleanName(artist),
                    album = cleanAlbum(album),
                    artworkUrl = artworkUrl,
                    videoId = videoId.trim(),
                    aiAsked = false,
                )
            )
            if (updated) persistCatalog()
            updated
        }
        if (changed) _catalogVersion.value += 1
    }

    fun creditGapsForAi(playedSongIds: Set<String>, limit: Int): List<CatalogCreditGap> {
        if (playedSongIds.isEmpty() || limit <= 0) return emptyList()
        return synchronized(catalog) {
            val tracks = catalog.values.filter { track ->
                track.songId in playedSongIds &&
                    !track.aiAsked &&
                    track.title.isNotBlank() &&
                    (track.artist.isBlank() || track.album.isBlank())
            }.take(limit)
            if (tracks.isEmpty()) return@synchronized emptyList()
            tracks.forEach { track ->
                catalog[track.songId] = track.copy(aiAsked = true)
            }
            persistCatalog()
            tracks.map { track ->
                CatalogCreditGap(
                    songId = track.songId,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    artworkUrl = track.artworkUrl,
                    videoId = track.videoId,
                )
            }
        }
    }

    fun releaseAiCreditGaps(songIds: Set<String>) {
        if (songIds.isEmpty()) return
        synchronized(catalog) {
            var changed = false
            songIds.forEach { songId ->
                val track = catalog[songId] ?: return@forEach
                if (!track.aiAsked) return@forEach
                catalog[songId] = track.copy(aiAsked = false)
                changed = true
            }
            if (changed) persistCatalog()
        }
    }

    fun creditGaps(playedSongIds: Set<String>, limit: Int): List<CatalogCreditGap> {
        if (playedSongIds.isEmpty() || limit <= 0) return emptyList()
        return synchronized(catalog) {
            catalog.values.asSequence()
                .filter { track ->
                    track.songId in playedSongIds &&
                        track.videoId.length == 11 &&
                        track.videoId !in creditLookups &&
                        (track.artist.isBlank() || track.album.isBlank())
                }
                .take(limit)
                .map { track ->
                    CatalogCreditGap(
                        songId = track.songId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        artworkUrl = track.artworkUrl,
                        videoId = track.videoId,
                    )
                }
                .toList()
        }
    }

    fun claimCreditLookup(videoId: String): Boolean = creditLookups.add(videoId)

    fun releaseCreditLookup(videoId: String) {
        creditLookups.remove(videoId)
    }

    fun songsForStats(): List<Song> {
        val tracks = synchronized(catalog) { catalog.values.toList() }
        return tracks.map { it.toSong() }
    }

    private fun idsOf(hits: List<YoutubeHit>): Set<String> =
        hits.map { YoutubeClient.songIdForUrl(it.url) }.toSet()

    private fun toggle(current: List<YoutubeHit>, hit: YoutubeHit): List<YoutubeHit> {
        if (hit.url.isBlank() || hit.kind != YoutubeHit.Kind.TRACK) return current
        val without = current.filterNot { it.url == hit.url }
        return if (without.size == current.size) listOf(hit) + current else without
    }

    private fun hitFromSong(song: Song): YoutubeHit? {
        val url = YoutubeClient.watchUrlForSongId(song.id) ?: return null
        return YoutubeHit(
            url = url,
            title = song.title.ifBlank { "Unknown title" },
            artist = song.displayArtist,
            durationSec = song.duration.coerceAtLeast(0L) / 1000L,
            thumbnailUrl = song.albumArtUriString,
            kind = YoutubeHit.Kind.TRACK,
            album = song.album,
        )
    }

    private fun read(key: String): List<YoutubeHit> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        return runCatching { decode(raw) }.getOrDefault(emptyList())
    }

    private fun persist(key: String, hits: List<YoutubeHit>) {
        prefs.edit().putString(key, encode(hits.take(MAX_TRACKS))).apply()
    }

    private fun encode(hits: List<YoutubeHit>): String {
        val array = JSONArray()
        hits.forEach { hit ->
            array.put(
                JSONObject()
                    .put("url", hit.url)
                    .put("title", hit.title)
                    .put("artist", hit.artist)
                    .put("durationSec", hit.durationSec)
                    .put("thumbnailUrl", hit.thumbnailUrl ?: "")
                    .put("album", hit.album)
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<YoutubeHit> {
        val array = JSONArray(raw)
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val url = item.optString("url")
                if (url.isBlank()) continue
                add(
                    YoutubeHit(
                        url = url,
                        title = item.optString("title").ifBlank { "Unknown title" },
                        artist = item.optString("artist"),
                        durationSec = item.optLong("durationSec"),
                        thumbnailUrl = item.optString("thumbnailUrl").ifBlank { null },
                        kind = YoutubeHit.Kind.TRACK,
                        album = item.optString("album"),
                    )
                )
            }
        }
    }

    private fun upsert(track: CatalogTrack): Boolean {
        if (track.songId.isBlank() || track.title.isBlank()) return false
        val merged = catalog[track.songId]?.merge(track) ?: track
        if (merged == catalog[track.songId]) return false
        catalog.remove(track.songId)
        catalog[track.songId] = merged
        while (catalog.size > MAX_CATALOG) {
            val eldest = catalog.keys.firstOrNull() ?: break
            catalog.remove(eldest)
        }
        return true
    }

    private fun persistCatalog() {
        val array = JSONArray()
        catalog.values.forEach { track ->
            array.put(
                JSONObject()
                    .put("songId", track.songId)
                    .put("title", track.title)
                    .put("artist", track.artist)
                    .put("album", track.album)
                    .put("artworkUrl", track.artworkUrl ?: "")
                    .put("videoId", track.videoId)
                    .put("aiAsked", track.aiAsked)
            )
        }
        prefs.edit().putString(KEY_CATALOG, array.toString()).apply()
    }

    private fun readCatalog(): List<CatalogTrack> {
        val raw = prefs.getString(KEY_CATALOG, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val songId = item.optString("songId")
                    val title = item.optString("title")
                    if (songId.isBlank() || title.isBlank()) continue
                    add(
                        CatalogTrack(
                            songId = songId,
                            title = title,
                            artist = cleanName(item.optString("artist")),
                            album = cleanAlbum(item.optString("album")),
                            artworkUrl = item.optString("artworkUrl").ifBlank { null },
                            videoId = item.optString("videoId"),
                            aiAsked = item.optBoolean("aiAsked"),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun credits(hit: YoutubeHit): Pair<String, String> {
        val parts = hit.artist.split('•', '·')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val artist = cleanName(parts.firstOrNull().orEmpty().ifBlank { hit.artist })
        val fromByline = parts.getOrNull(1).orEmpty()
        val album = cleanAlbum(hit.album).ifBlank { cleanAlbum(fromByline) }
        return artist to album
    }

    private fun cleanName(value: String): String {
        val trimmed = value.trim().removeSuffix(" - Topic").trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.equals("Unknown Artist", ignoreCase = true)) return ""
        if (trimmed.equals("YouTube Music", ignoreCase = true)) return ""
        return trimmed
    }

    private fun cleanAlbum(value: String): String {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.equals("YouTube Music", ignoreCase = true)) return ""
        if (trimmed.equals("Unknown Album", ignoreCase = true)) return ""
        if (trimmed.contains("view", ignoreCase = true)) return ""
        if (DURATION.matches(trimmed)) return ""
        return trimmed
    }

    private data class CatalogTrack(
        val songId: String,
        val title: String,
        val artist: String,
        val album: String,
        val artworkUrl: String?,
        val videoId: String = "",
        val aiAsked: Boolean = false,
    ) {
        fun merge(incoming: CatalogTrack): CatalogTrack = copy(
            title = incoming.title.ifBlank { title },
            artist = incoming.artist.ifBlank { artist },
            album = incoming.album.ifBlank { album },
            artworkUrl = incoming.artworkUrl ?: artworkUrl,
            videoId = incoming.videoId.ifBlank { videoId },
            aiAsked = aiAsked || incoming.aiAsked,
        )

        fun toSong(): Song = Song(
            id = songId,
            title = title,
            artist = artist,
            artistId = -1L,
            artists = emptyList(),
            album = album,
            albumId = -1L,
            albumArtist = artist.ifBlank { null },
            path = "",
            contentUriString = "",
            albumArtUriString = artworkUrl,
            duration = 0L,
            genre = null,
            mimeType = null,
            bitrate = null,
            sampleRate = null,
        )
    }

    companion object {
        private const val PREFS = "youtube_music"
        private const val KEY_LIKED = "liked"
        private const val KEY_RECENT = "recent"
        private const val KEY_CATALOG = "catalog"
        private const val MAX_TRACKS = 200
        private const val MAX_RECENT = 24
        private const val MAX_CATALOG = 2000
        private val DURATION = Regex("""^\d+:\d{2}(?::\d{2})?$""")
    }
}

data class CatalogCreditGap(
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUrl: String?,
    val videoId: String,
)
