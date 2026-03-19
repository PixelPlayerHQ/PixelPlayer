package com.theveloper.pixelplay.data.backup.module

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SongSummary
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistsModuleHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    @BackupGson private val gson: Gson
) : BackupModuleHandler {

    override val section = BackupSection.PLAYLISTS

    override suspend fun export(): String = withContext(Dispatchers.IO) {
        val allPlaylists = playlistPreferencesRepository.getPlaylistsOnce()

        // Only export local/AI playlists — cloud playlists (Telegram, Netease, QQMusic)
        // are tied to service auth and would be empty on restore
        val playlists = allPlaylists.filter { it.source in LOCAL_SOURCES }

        // Build a set of cloud song IDs to exclude from backup
        val cloudSongIds = buildCloudSongIdSet()

        // Get metadata for local songs so we can match them on restore
        val allLocalSummaries = musicDao.getAllLocalSongSummaries()
        val summaryById = allLocalSummaries.associateBy { it.id.toString() }

        // Filter cloud songs out of playlists and collect metadata
        val songMetadata = mutableMapOf<String, SongMetadataEntry>()
        val filteredPlaylists = playlists.map { playlist ->
            val localSongIds = playlist.songIds.filter { id -> id !in cloudSongIds }
            // Collect metadata for matched local songs
            localSongIds.forEach { id ->
                if (id !in songMetadata) {
                    summaryById[id]?.let { summary ->
                        songMetadata[id] = SongMetadataEntry(
                            title = summary.title,
                            artist = summary.artistName,
                            album = summary.albumName,
                            duration = summary.duration
                        )
                    }
                }
            }
            playlist.copy(songIds = localSongIds)
        }

        // Encode cover images as Base64
        val coverImages = mutableMapOf<String, String>()
        filteredPlaylists.forEach { playlist ->
            val uri = playlist.coverImageUri ?: return@forEach
            readFileAsBase64(uri)?.let { coverImages[playlist.id] = it }
        }

        val payload = PlaylistsBackupPayload(
            playlists = filteredPlaylists,
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first(),
            songMetadata = songMetadata.ifEmpty { null },
            coverImages = coverImages.ifEmpty { null }
        )
        gson.toJson(payload)
    }

    override suspend fun countEntries(): Int = withContext(Dispatchers.IO) {
        val playlists = playlistPreferencesRepository.getPlaylistsOnce()
            .count { it.source in LOCAL_SOURCES }
        val orderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first()
        val sortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        playlists + orderModes.size + if (sortOption.isNotBlank()) 1 else 0
    }

    override suspend fun snapshot(): String = withContext(Dispatchers.IO) {
        // Snapshot captures the current state as-is (including cloud songs) for rollback
        val payload = PlaylistsBackupPayload(
            playlists = playlistPreferencesRepository.getPlaylistsOnce(),
            playlistSongOrderModes = playlistPreferencesRepository.playlistSongOrderModesFlow.first(),
            playlistsSortOption = playlistPreferencesRepository.playlistsSortOptionFlow.first()
        )
        gson.toJson(payload)
    }

    override suspend fun restore(payload: String) = withContext(Dispatchers.IO) {
        val element = JsonParser.parseString(payload)
        if (element.isJsonArray) {
            restoreLegacyPreferenceEntries(payload)
            return@withContext
        }

        val parsed = runCatching {
            gson.fromJson(payload, PlaylistsBackupPayload::class.java)
        }.getOrNull() ?: PlaylistsBackupPayload()

        val backupPlaylists = parsed.playlists.orEmpty()
        val songMetadata = parsed.songMetadata
        val coverImages = parsed.coverImages

        // Resolve song IDs against the current device library
        val resolvedPlaylists = if (songMetadata != null && songMetadata.isNotEmpty()) {
            resolvePlaylists(backupPlaylists, songMetadata)
        } else {
            // No metadata available (legacy backup or snapshot rollback) — keep IDs as-is
            backupPlaylists
        }

        // Restore cover images and update playlist URIs
        val finalPlaylists = if (coverImages != null && coverImages.isNotEmpty()) {
            restoreCoverImages(resolvedPlaylists, coverImages)
        } else {
            resolvedPlaylists
        }

        playlistPreferencesRepository.replaceAllPlaylists(finalPlaylists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(parsed.playlistSongOrderModes.orEmpty())
        playlistPreferencesRepository.setPlaylistsSortOption(
            parsed.playlistsSortOption ?: SortOption.PlaylistNameAZ.storageKey
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    override suspend fun rollback(snapshot: String) = restore(snapshot)

    // ---- Cover image helpers ----

    private fun readFileAsBase64(path: String): String? {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() == 0L) return null
            val bytes = file.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cover image: $path", e)
            null
        }
    }

    private fun restoreCoverImages(
        playlists: List<Playlist>,
        coverImages: Map<String, String>
    ): List<Playlist> {
        return playlists.map { playlist ->
            val base64 = coverImages[playlist.id] ?: return@map playlist
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val fileName = "playlist_cover_${playlist.id}.jpg"
                val file = File(context.filesDir, fileName)
                file.writeBytes(bytes)
                playlist.copy(coverImageUri = file.absolutePath)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to restore cover image for playlist ${playlist.id}", e)
                playlist.copy(coverImageUri = null)
            }
        }
    }

    // ---- Song matching ----

    /**
     * Resolves backup song IDs to current device song IDs using metadata matching.
     *
     * Strategy:
     * 1. Direct ID match + metadata verification → confirmed
     * 2. If direct ID exists but metadata doesn't match → try metadata match (avoids false positives)
     * 3. If direct ID doesn't exist → try metadata match
     * 4. Metadata match: title + artist (case-insensitive), disambiguate with album + duration
     * 5. No confident match → song is dropped from the playlist (kept as unresolved would risk false matches)
     */
    private suspend fun resolvePlaylists(
        playlists: List<Playlist>,
        songMetadata: Map<String, SongMetadataEntry>
    ): List<Playlist> {
        val localSummaries = musicDao.getAllLocalSongSummaries()
        val currentSongsById = localSummaries.associateBy { it.id.toString() }

        // Build index for metadata matching: normalized "title|artist" → list of candidates
        val metadataIndex = mutableMapOf<String, MutableList<SongSummary>>()
        localSummaries.forEach { song ->
            val key = normalizeMatchKey(song.title, song.artistName)
            metadataIndex.getOrPut(key) { mutableListOf() }.add(song)
        }

        // Build the resolution map: backup songId → resolved songId (or null if unresolved)
        val resolutionCache = mutableMapOf<String, String?>()
        var totalSongs = 0
        var resolvedCount = 0
        var unresolvedCount = 0

        playlists.forEach { playlist ->
            playlist.songIds.forEach { songId ->
                if (songId !in resolutionCache) {
                    totalSongs++
                    val resolved = resolveSongId(songId, songMetadata, currentSongsById, metadataIndex)
                    resolutionCache[songId] = resolved
                    if (resolved != null) resolvedCount++ else unresolvedCount++
                }
            }
        }

        if (unresolvedCount > 0) {
            Log.w(TAG, "Playlist restore: $resolvedCount/$totalSongs songs resolved, $unresolvedCount unresolved")
        }

        // Apply resolution to playlists, dropping unresolved songs
        return playlists.map { playlist ->
            val resolvedSongIds = playlist.songIds.mapNotNull { songId ->
                resolutionCache[songId]
            }
            playlist.copy(songIds = resolvedSongIds)
        }
    }

    private fun resolveSongId(
        backupSongId: String,
        songMetadata: Map<String, SongMetadataEntry>,
        currentSongsById: Map<String, SongSummary>,
        metadataIndex: Map<String, List<SongSummary>>
    ): String? {
        val meta = songMetadata[backupSongId]

        // 1. Try direct ID match
        val directMatch = currentSongsById[backupSongId]
        if (directMatch != null) {
            if (meta == null) {
                // No metadata to verify — accept direct match (same-device restore)
                return backupSongId
            }
            // Verify metadata matches to avoid false positives (e.g., reused MediaStore ID)
            if (metadataMatches(meta, directMatch)) {
                return backupSongId
            }
            // Direct ID exists but is a different song — fall through to metadata matching
        }

        // 2. No metadata available — can't do metadata matching
        if (meta == null) {
            return if (directMatch != null) backupSongId else null
        }

        // 3. Try metadata matching
        val matchKey = normalizeMatchKey(meta.title, meta.artist)
        val candidates = metadataIndex[matchKey] ?: return null

        if (candidates.size == 1) {
            return candidates[0].id.toString()
        }

        // Multiple candidates — disambiguate with album and duration
        val albumMatch = candidates.filter { candidate ->
            normalizeText(candidate.albumName) == normalizeText(meta.album)
        }
        if (albumMatch.size == 1) {
            return albumMatch[0].id.toString()
        }

        // Try duration (within 2 second tolerance)
        val durationCandidates = (albumMatch.ifEmpty { candidates }).filter { candidate ->
            kotlin.math.abs(candidate.duration - meta.duration) <= DURATION_TOLERANCE_MS
        }
        if (durationCandidates.size == 1) {
            return durationCandidates[0].id.toString()
        }

        // Ambiguous — no confident match
        return null
    }

    private fun metadataMatches(meta: SongMetadataEntry, song: SongSummary): Boolean {
        return normalizeText(meta.title) == normalizeText(song.title) &&
            normalizeText(meta.artist) == normalizeText(song.artistName)
    }

    private fun normalizeMatchKey(title: String, artist: String): String {
        return "${normalizeText(title)}|${normalizeText(artist)}"
    }

    private fun normalizeText(text: String): String {
        return text.trim().lowercase()
    }

    private suspend fun buildCloudSongIdSet(): Set<String> {
        val cloudIds = mutableSetOf<String>()
        musicDao.getAllTelegramSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllNeteaseSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllGDriveSongIds().mapTo(cloudIds) { it.toString() }
        musicDao.getAllQqMusicSongIds().mapTo(cloudIds) { it.toString() }
        return cloudIds
    }

    // ---- Legacy format ----

    private suspend fun restoreLegacyPreferenceEntries(payload: String) {
        val type = TypeToken.getParameterized(List::class.java, PreferenceBackupEntry::class.java).type
        val entries: List<PreferenceBackupEntry> = gson.fromJson(payload, type)

        val playlists = entries.firstOrNull { it.key == LEGACY_USER_PLAYLISTS_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val playlistType = TypeToken.getParameterized(List::class.java, Playlist::class.java).type
                    gson.fromJson<List<Playlist>>(raw, playlistType)
                }.getOrDefault(emptyList())
            }
            .orEmpty()

        val playlistSongOrderModes = entries.firstOrNull { it.key == LEGACY_PLAYLIST_ORDER_MODES_KEY }
            ?.stringValue
            ?.let { raw ->
                runCatching {
                    val mapType = TypeToken.getParameterized(
                        Map::class.java,
                        String::class.java,
                        String::class.java
                    ).type
                    gson.fromJson<Map<String, String>>(raw, mapType)
                }.getOrDefault(emptyMap())
            }
            .orEmpty()

        val playlistsSortOption = entries.firstOrNull { it.key == LEGACY_PLAYLIST_SORT_OPTION_KEY }
            ?.stringValue
            ?: SortOption.PlaylistNameAZ.storageKey

        playlistPreferencesRepository.replaceAllPlaylists(playlists)
        playlistPreferencesRepository.setPlaylistSongOrderModes(playlistSongOrderModes)
        playlistPreferencesRepository.setPlaylistsSortOption(playlistsSortOption)
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    // ---- Data classes ----

    /** Song metadata stored alongside playlists for cross-device matching. */
    data class SongMetadataEntry(
        val title: String,
        val artist: String,
        val album: String,
        val duration: Long
    )

    private data class PlaylistsBackupPayload(
        val playlists: List<Playlist>? = null,
        val playlistSongOrderModes: Map<String, String>? = null,
        val playlistsSortOption: String? = null,
        /** Song metadata for cross-device matching. Key = songId from backup. Null in legacy/snapshot payloads. */
        val songMetadata: Map<String, SongMetadataEntry>? = null,
        /** Base64-encoded cover images. Key = playlist ID. Null if no custom covers. */
        val coverImages: Map<String, String>? = null
    )

    companion object {
        private const val TAG = "PlaylistsModuleHandler"
        private const val DURATION_TOLERANCE_MS = 2000L

        /** Playlist sources that are backed up. Cloud-sourced playlists are excluded. */
        private val LOCAL_SOURCES = setOf("LOCAL", "AI")

        const val LEGACY_USER_PLAYLISTS_KEY = "user_playlists_json_v1"
        const val LEGACY_PLAYLIST_ORDER_MODES_KEY = "playlist_song_order_modes"
        const val LEGACY_PLAYLIST_SORT_OPTION_KEY = "playlists_sort_option"
        val PLAYLIST_KEYS = setOf(
            LEGACY_USER_PLAYLISTS_KEY,
            LEGACY_PLAYLIST_ORDER_MODES_KEY,
            LEGACY_PLAYLIST_SORT_OPTION_KEY
        )
    }
}
