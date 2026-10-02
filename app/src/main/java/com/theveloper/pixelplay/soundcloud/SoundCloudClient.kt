package com.theveloper.pixelplay.soundcloud

import com.theveloper.pixelplay.data.model.Song
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudParsingHelper
import org.schabi.newpipe.extractor.services.soundcloud.SoundcloudService
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * SoundCloud access via NewPipe Extractor (web api-v2).
 *
 * Likes / Tracks / Playlists use the user's **public** profile URL
 * (`soundcloud.com/{username}/…`) — not OAuth. Private likes won't show.
 */
@Singleton
class SoundCloudClient @Inject constructor(
    private val sessionApi: SoundCloudSessionApi,
    private val playability: SoundCloudPlayabilityStore,
) {
    private val downloader = SoundCloudDownloader()
    private val runtimeClientId = AtomicReference<String?>(null)
    private val resolveCache = ConcurrentHashMap<String, CachedResolve>()
    private val _likedPermalinks = MutableStateFlow<Set<String>>(emptySet())
    val likedPermalinks: StateFlow<Set<String>> = _likedPermalinks.asStateFlow()

    private data class CachedResolve(
        val track: SoundCloudResolvedTrack,
        val cachedAtMs: Long,
    )

    fun setClientIdOverride(clientId: String?) {
        val trimmed = clientId?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            runtimeClientId.set(null)
            return
        }
        runtimeClientId.set(trimmed)
        injectClientId(trimmed)
        sessionApi.updateSession(
            token = downloader.oauthToken,
            cookies = downloader.cookieHeader,
            clientIdValue = trimmed,
        )
    }

    fun setSession(oauthToken: String?, cookieHeader: String?) {
        downloader.oauthToken = oauthToken?.trim()?.takeIf { it.isNotEmpty() }
        downloader.cookieHeader = cookieHeader?.trim()?.takeIf { it.isNotEmpty() }
        sessionApi.updateSession(
            token = downloader.oauthToken,
            cookies = downloader.cookieHeader,
            clientIdValue = runtimeClientId.get(),
        )
    }

    val hasSession: Boolean get() = sessionApi.hasSession

    @Throws(Exception::class)
    fun loadFeed(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadFeed(limit)

    @Throws(Exception::class)
    fun loadRecentlyPlayed(limit: Int = 20): List<SoundCloudSearchHit> =
        sessionApi.loadRecentlyPlayed(limit)

    @Throws(Exception::class)
    fun loadMyLikes(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyLikes(limit)

    @Throws(Exception::class)
    fun loadMyTracks(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyTracks(limit)

    @Throws(Exception::class)
    fun loadMyPlaylists(limit: Int = 40): List<SoundCloudSearchHit> = sessionApi.loadMyPlaylists(limit)

    @Throws(Exception::class)
    fun setTrackLiked(trackUrl: String, liked: Boolean) {
        val url = trackUrl.trim()
        sessionApi.setTrackLiked(url, liked)
        _likedPermalinks.update { current -> if (liked) current + url else current - url }
    }

    fun rememberLikes(urls: Collection<String>) {
        val clean = urls.map { it.trim() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        _likedPermalinks.update { it + clean }
    }

    fun permalinkForSong(songId: String): String? = permalinkForSongId(songId)

    @Throws(Exception::class)
    fun loadDiscover(limit: Int = 30): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val extractor = soundCloud().kioskList.defaultKioskExtractor
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        return info.relatedItems
            .asSequence()
            .filterIsInstance<StreamInfoItem>()
            .map { it.toHit() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    @Throws(Exception::class)
    fun search(query: String, limit: Int = 30): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val trimmed = query.trim()
        require(trimmed.isNotEmpty()) { "Query is empty" }
        val extractor = soundCloud().getSearchExtractor(trimmed)
        extractor.fetchPage()
        return extractor.initialPage.items
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    @Throws(Exception::class)
    fun loadUserLikes(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.LIKES, limit)

    @Throws(Exception::class)
    fun loadUserTracks(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.TRACKS, limit)

    @Throws(Exception::class)
    fun loadUserPlaylists(username: String, limit: Int = 40): List<SoundCloudSearchHit> =
        loadChannelTab(username, ChannelTabs.PLAYLISTS, limit)

    @Throws(Exception::class)
    fun loadPlaylistTracks(playlistUrl: String, limit: Int = 100): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        try {
            val info = PlaylistInfo.getInfo(soundCloud(), playlistUrl.trim())
            return info.relatedItems
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .map { it.toHit() }
                .filter { it.url.isNotBlank() }
                .take(limit)
                .toList()
        } catch (t: Throwable) {
            if (looksLikeClientIdOrRateLimit(t)) {
                refreshClientIdFromWeb()
                val info = PlaylistInfo.getInfo(soundCloud(), playlistUrl.trim())
                return info.relatedItems
                    .asSequence()
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toHit() }
                    .filter { it.url.isNotBlank() }
                    .take(limit)
                    .toList()
            }
            throw wrapSoundCloudError(t, "Couldn't open this playlist")
        }
    }

    @Throws(Exception::class)
    fun resolveTrack(url: String): SoundCloudResolvedTrack {
        val key = url.trim()
        resolveCache[key]?.takeIf {
            System.currentTimeMillis() - it.cachedAtMs < RESOLVE_CACHE_TTL_MS
        }?.let { return it.track }

        val resolved = try {
            resolveTrackOnce(key)
        } catch (t: Throwable) {
            if (looksLikeClientIdOrRateLimit(t)) {
                refreshClientIdFromWeb()
                resolveTrackOnce(key)
            } else {
                throw wrapSoundCloudError(t, "No playable stream")
            }
        }
        resolveCache[key] = CachedResolve(resolved, System.currentTimeMillis())
        return resolved
    }

    private fun resolveTrackOnce(url: String): SoundCloudResolvedTrack {
        ensureClientIdReady()
        val trimmedUrl = url.trim()
        val info = StreamInfo.getInfo(soundCloud(), trimmedUrl)
        val stream = pickBestAudioStream(info.audioStreams)
            ?: throw IllegalStateException(
                "No progressive/plain stream available. DRM-protected tracks can't be played or downloaded.",
            )
        return SoundCloudResolvedTrack(
            url = info.url.orEmpty().ifBlank { trimmedUrl },
            title = info.name.orEmpty().ifBlank { "Unknown title" },
            artist = info.uploaderName.orEmpty().ifBlank { "Unknown artist" },
            durationMs = info.duration.coerceAtLeast(0L) * 1000L,
            streamUrl = stream.content,
            mimeType = stream.format?.mimeType,
            artworkUrl = info.thumbnails.maxByOrNull { it.height }?.url,
        )
    }

    fun isKnownUnplayable(url: String): Boolean = playability.isUnplayable(url)

    fun knowsPlayability(url: String): Boolean = playability.knows(url)

    fun rememberPlayable(url: String) {
        playability.remember(url, playable = true)
    }

    fun rememberUnplayable(url: String) {
        playability.remember(url, playable = false)
    }

    /**
     * Cheap api-v2 check (no stream extraction). Null means the probe failed and
     * the row should stay unchanged so a blip doesn't gray the whole list.
     */
    fun probePlayable(url: String): Boolean? {
        val key = url.trim()
        if (playability.isUnplayable(key)) return false
        if (playability.knows(key)) return true
        resolveCache[key]?.let {
            playability.remember(key, playable = true)
            return true
        }
        val playable = sessionApi.playbackAvailability(key) ?: return null
        playability.remember(key, playable)
        return playable
    }

    fun isPermanentStreamFailure(error: Throwable): Boolean {
        if (looksLikeClientIdOrRateLimit(error)) return false
        val msg = generateSequence(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return msg.contains("no playable stream") ||
            msg.contains("could not get any stream") ||
            msg.contains("no progressive") ||
            msg.contains("drm") ||
            msg.contains("encrypted")
    }

    fun fileTags(track: SoundCloudResolvedTrack): SoundCloudFileTags {
        val fromApi = runCatching { sessionApi.loadFileTags(track.url) }.getOrNull()
        val artist = fromApi?.artist?.takeUnless { it.isSoundCloudPlaceholder() }
            ?: track.artist.takeUnless { it.isSoundCloudPlaceholder() }
            ?: "Unknown artist"
        val title = fromApi?.title?.takeIf { it.isNotBlank() } ?: track.title
        val album = fromApi?.album?.takeUnless { it.isSoundCloudPlaceholder() } ?: artist
        val genre = fromApi?.genre?.takeUnless { it.isSoundCloudPlaceholder() }
        val artwork = fromApi?.artworkUrl?.takeIf { it.isNotBlank() } ?: track.artworkUrl
        return SoundCloudFileTags(
            title = title,
            artist = artist,
            album = album,
            albumArtist = artist,
            genre = genre,
            artworkUrl = artwork,
        )
    }

    fun toSong(track: SoundCloudResolvedTrack, permalinkUrl: String = track.url): Song {
        // Prefer the list permalink so UI can match the now-playing row without resolving again.
        val id = songIdForUrl(permalinkUrl.ifBlank { track.url })
        val permalink = permalinkUrl.ifBlank { track.url }.trim()
        if (permalink.isNotEmpty()) rememberPermalink(id, permalink)
        return Song(
            id = id,
            title = track.title,
            artist = track.artist,
            artistId = -1L,
            artists = emptyList(),
            album = "SoundCloud",
            albumId = -1L,
            albumArtist = track.artist,
            path = track.streamUrl,
            contentUriString = track.streamUrl,
            albumArtUriString = track.artworkUrl,
            duration = track.durationMs,
            genre = "SoundCloud",
            lyrics = null,
            isFavorite = false,
            trackNumber = 0,
            discNumber = null,
            year = 0,
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            mimeType = track.mimeType,
            bitrate = null,
            sampleRate = null,
            telegramFileId = null,
            telegramChatId = null,
            neteaseId = null,
            gdriveFileId = null,
            qqMusicMid = null,
            navidromeId = null,
            jellyfinId = null,
        )
    }

    private fun loadChannelTab(username: String, tab: String, limit: Int): List<SoundCloudSearchHit> {
        ensureClientIdReady()
        val user = username.trim().removePrefix("@").removePrefix("https://soundcloud.com/")
            .substringBefore('/')
        require(user.isNotEmpty()) { "Set your SoundCloud username in SoundCloud settings" }

        val url = "https://soundcloud.com/$user" + when (tab) {
            ChannelTabs.LIKES -> "/likes"
            ChannelTabs.TRACKS -> "/tracks"
            ChannelTabs.PLAYLISTS -> "/sets"
            ChannelTabs.ALBUMS -> "/albums"
            else -> ""
        }
        val link = soundCloud().channelTabLHFactory.fromUrl(url)
        val info = ChannelTabInfo.getInfo(soundCloud(), link)
        return info.relatedItems
            .asSequence()
            .mapNotNull { it.toHitOrNull() }
            .filter { it.url.isNotBlank() }
            .take(limit)
            .toList()
    }

    /**
     * Scrapes a fresh web client_id via NewPipe and injects it.
     * Call when prefs are empty or the cached id has stopped working.
     */
    @Throws(Exception::class)
    fun refreshClientIdFromWeb(): String {
        clearInjectedClientId()
        val scraped = try {
            SoundcloudParsingHelper.clientId()
        } catch (t: Throwable) {
            throw IllegalStateException(
                "Couldn't auto-fetch SoundCloud client_id. Paste one in SoundCloud settings. (${t.message})",
                t,
            )
        }
        val trimmed = scraped.trim()
        require(trimmed.isNotEmpty()) { "SoundCloud returned an empty client_id" }
        setClientIdOverride(trimmed)
        Timber.i("SoundCloud: refreshed client_id from web")
        return trimmed
    }

    private fun InfoItem.toHitOrNull(): SoundCloudSearchHit? = when (this) {
        is StreamInfoItem -> toHit()
        is PlaylistInfoItem -> SoundCloudSearchHit(
            url = url.orEmpty(),
            title = name.orEmpty().ifBlank { "Playlist" },
            artist = uploaderName.orEmpty().ifBlank { "SoundCloud" },
            durationSec = 0,
            thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
            kind = SoundCloudSearchHit.Kind.PLAYLIST,
            streamCount = streamCount.coerceAtLeast(0L),
        )
        else -> null
    }

    private fun StreamInfoItem.toHit() = SoundCloudSearchHit(
        url = url.orEmpty(),
        title = name.orEmpty().ifBlank { "Unknown title" },
        artist = uploaderName.orEmpty().ifBlank { "Unknown artist" },
        durationSec = duration.coerceAtLeast(0L),
        thumbnailUrl = thumbnails.maxByOrNull { it.height }?.url,
        kind = SoundCloudSearchHit.Kind.TRACK,
    )

    private fun ensureClientIdReady() {
        var id = runtimeClientId.get()?.trim().orEmpty()
        if (id.isEmpty()) {
            id = refreshClientIdFromWeb()
        }
        injectClientId(id)
    }

    private fun clearInjectedClientId() {
        try {
            val field = SoundcloudParsingHelper::class.java.getDeclaredField("clientId")
            field.isAccessible = true
            synchronized(SoundcloudParsingHelper::class.java) {
                field.set(null, null)
            }
        } catch (t: Throwable) {
            Timber.w(t, "SoundCloud: could not clear injected client_id")
        }
    }

    private fun injectClientId(clientId: String) {
        try {
            val field = SoundcloudParsingHelper::class.java.getDeclaredField("clientId")
            field.isAccessible = true
            synchronized(SoundcloudParsingHelper::class.java) {
                field.set(null, clientId)
            }
        } catch (t: Throwable) {
            throw IllegalStateException("Could not inject SoundCloud client_id: ${t.message}", t)
        }
    }

    private fun looksLikeClientIdOrRateLimit(t: Throwable): Boolean {
        val msg = generateSequence(t) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase()
        return msg.contains("clientid") ||
            msg.contains("client_id") ||
            msg.contains("client id") ||
            msg.contains("rate limited") ||
            msg.contains("429") ||
            msg.contains("recaptcha")
    }

    private fun wrapSoundCloudError(t: Throwable, prefix: String): IllegalStateException {
        val msg = generateSequence(t) { it.cause }
            .mapNotNull { it.message }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
        val friendly = when {
            msg.contains("429", ignoreCase = true) ||
                msg.contains("rate limited", ignoreCase = true) ->
                "$prefix: SoundCloud rate-limited you. Wait a bit and try again."
            msg.contains("client", ignoreCase = true) ->
                "$prefix: client_id missing/expired. Open SoundCloud settings and save a fresh client_id (or retry)."
            else -> "$prefix. $msg"
        }
        return IllegalStateException(friendly, t)
    }

    private fun pickBestAudioStream(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        // Progressive HTTP starts fastest (single file). Prefer it over HLS when available.
        val progressive = streams.filter { it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        if (progressive.isNotEmpty()) {
            return progressive.maxWithOrNull(
                compareBy<AudioStream> { it.averageBitrate }.thenBy { it.bitrate },
            )
        }
        // Plain HLS only — skip encrypted/DRM. Prefer a mid/low bitrate for quicker first buffer.
        val hls = streams.filter {
            it.deliveryMethod == DeliveryMethod.HLS && !it.content.contains("encrypted")
        }
        if (hls.isNotEmpty()) {
            return hls.minWithOrNull(
                compareBy<AudioStream> {
                    val br = it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate
                    if (br > 0) br else Int.MAX_VALUE
                },
            ) ?: hls.first()
        }
        return streams.firstOrNull { !it.content.contains("encrypted") } ?: streams.firstOrNull()
    }

    private fun soundCloud(): SoundcloudService = ServiceList.SoundCloud

    companion object {
        const val EXTRA_PERMALINK = "com.theveloper.pixelplay.soundcloud.PERMALINK"
        private val permalinkBySongId = ConcurrentHashMap<String, String>()
        private const val RESOLVE_CACHE_TTL_MS = 15 * 60 * 1000L

        fun rememberPermalink(songId: String, permalink: String) {
            val id = songId.trim()
            val url = permalink.trim()
            if (id.isNotEmpty() && url.startsWith("http")) permalinkBySongId[id] = url
        }

        fun permalinkForSongId(songId: String): String? = permalinkBySongId[songId]

        fun songIdForUrl(url: String): String =
            "sc_${url.trim().hashCode().toUInt()}"
    }
}
