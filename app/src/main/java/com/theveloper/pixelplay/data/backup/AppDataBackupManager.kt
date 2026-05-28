package com.theveloper.pixelplay.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.database.LyricsEntity
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.SongEngagementEntity
import com.theveloper.pixelplay.data.database.TransitionDao
import com.theveloper.pixelplay.data.database.TransitionRuleEntity
import com.theveloper.pixelplay.data.preferences.PreferenceBackupEntry
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

enum class BackupSection(
    val key: String,
    val label: String,
    val description: String
) {
    PLAYLISTS(
        key = "playlists",
        label = "Playlists",
        description = "Your custom playlists and ordering preferences."
    ),
    GLOBAL_SETTINGS(
        key = "global_settings",
        label = "Global Settings",
        description = "Themes, behavior, playback, and app preferences."
    ),
    FAVORITES(
        key = "favorites",
        label = "Favorites",
        description = "Songs marked as favorite."
    ),
    LYRICS(
        key = "lyrics",
        label = "Saved Lyrics",
        description = "Lyrics you've saved or imported."
    ),
    SEARCH_HISTORY(
        key = "search_history",
        label = "Search History",
        description = "Recent search terms in the app."
    ),
    TRANSITIONS(
        key = "transitions",
        label = "Transition Rules",
        description = "Custom transition settings between songs."
    ),
    ENGAGEMENT_STATS(
        key = "engagement_stats",
        label = "Engagement Stats",
        description = "Play count and listening duration per song."
    ),
    PLAYBACK_HISTORY(
        key = "playback_history",
        label = "Playback History",
        description = "Timeline-based listening history for stats."
    );

    companion object {
        val defaultSelection: Set<BackupSection> = entries.toSet()
    }
}

enum class BackupOperationType {
    EXPORT,
    IMPORT
}

data class BackupTransferProgressUpdate(
    val operation: BackupOperationType,
    val step: Int,
    val totalSteps: Int,
    val title: String,
    val detail: String,
    val section: BackupSection? = null
) {
    val progress: Float
        get() = if (totalSteps > 0) (step.toFloat() / totalSteps).coerceIn(0f, 1f) else 0f
}

data class PlaybackHistoryBackupEntry(
    val songId: String,
    val timestamp: Long,
    val durationMs: Long,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null
)

data class AppDataBackupPayload(
    val formatVersion: Int = 2,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val availableSections: Set<String> = emptySet(),
    val globalSettings: List<PreferenceBackupEntry>? = null,
    val playlists: List<PreferenceBackupEntry>? = null,
    // Legacy field from JSON v1 backups
    val preferences: List<PreferenceBackupEntry>? = null,
    val favorites: List<FavoritesEntity>? = null,
    val lyrics: List<LyricsEntity>? = null,
    val searchHistory: List<SearchHistoryEntity>? = null,
    val transitions: List<TransitionRuleEntity>? = null,
    val engagementStats: List<SongEngagementEntity>? = null,
    val playbackHistory: List<PlaybackHistoryBackupEntry>? = null
)

@Deprecated("Use BackupManager instead. This class is retained for legacy format reference only.")
@Singleton
class AppDataBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val favoritesDao: FavoritesDao,
    private val lyricsDao: LyricsDao,
    private val searchHistoryDao: SearchHistoryDao,
    private val transitionDao: TransitionDao,
    private val engagementDao: EngagementDao,
    private val playbackStatsRepository: PlaybackStatsRepository
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val playlistPreferenceKeys = setOf(
        "user_playlists_json_v1",
        "playlist_song_order_modes",
        "playlists_sort_option"
    )
    private val pxplMagic = byteArrayOf(
        'P'.code.toByte(),
        'X'.code.toByte(),
        'P'.code.toByte(),
        'L'.code.toByte()
    )
    private val gzipMagic = byteArrayOf(0x1f, 0x8b.toByte())

    suspend fun exportToUri(
        uri: Uri,
        sections: Set<BackupSection>,
        onProgress: (BackupTransferProgressUpdate) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedSections = BackupSection.entries.filter { it in sections }
            val totalSteps = selectedSections.size + 4
            var step = 0

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.EXPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Preparing backup",
                detail = "Building your selected backup sections."
            )

            val allPreferences = if (
                BackupSection.GLOBAL_SETTINGS in sections || BackupSection.PLAYLISTS in sections
            ) {
                userPreferencesRepository.exportPreferencesForBackup()
            } else {
                emptyList()
            }
            val (playlistPreferences, globalPreferences) = splitPreferences(allPreferences)

            var playlists: List<PreferenceBackupEntry>? = null
            var globalSettings: List<PreferenceBackupEntry>? = null
            var favorites: List<FavoritesEntity>? = null
            var lyrics: List<LyricsEntity>? = null
            var searchHistory: List<SearchHistoryEntity>? = null
            var transitions: List<TransitionRuleEntity>? = null
            var engagementStats: List<SongEngagementEntity>? = null
            var playbackHistory: List<PlaybackHistoryBackupEntry>? = null

            selectedSections.forEach { section ->
                reportProgress(
                    onProgress = onProgress,
                    operation = BackupOperationType.EXPORT,
                    step = ++step,
                    totalSteps = totalSteps,
                    title = "Collecting ${section.label}",
                    detail = section.description,
                    section = section
                )
                when (section) {
                    BackupSection.PLAYLISTS -> playlists = playlistPreferences
                    BackupSection.GLOBAL_SETTINGS -> globalSettings = globalPreferences
                    BackupSection.FAVORITES -> favorites = favoritesDao.getAllFavoritesOnce()
                    BackupSection.LYRICS -> lyrics = lyricsDao.getAll()
                    BackupSection.SEARCH_HISTORY -> searchHistory = searchHistoryDao.getAll()
                    BackupSection.TRANSITIONS -> transitions = transitionDao.getAllRulesOnce()
                    BackupSection.ENGAGEMENT_STATS -> engagementStats = engagementDao.getAllEngagements()
                    BackupSection.PLAYBACK_HISTORY -> {
                        playbackHistory = playbackStatsRepository.exportEventsForBackup().map { event ->
                            PlaybackHistoryBackupEntry(
                                songId = event.songId,
                                timestamp = event.timestamp,
                                durationMs = event.durationMs,
                                startTimestamp = event.startTimestamp,
                                endTimestamp = event.endTimestamp
                            )
                        }
                    }
                }
            }

            val payload = AppDataBackupPayload(
                availableSections = selectedSections.mapTo(mutableSetOf()) { it.key },
                globalSettings = globalSettings,
                playlists = playlists,
                favorites = favorites,
                lyrics = lyrics,
                searchHistory = searchHistory,
                transitions = transitions,
                engagementStats = engagementStats,
                playbackHistory = playbackHistory
            )

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.EXPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Packaging backup",
                detail = "Compressing selected data into .pxpl."
            )
            val bytes = encodePayload(payload)

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.EXPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Writing file",
                detail = "Saving backup to selected location."
            )
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Unable to open output stream")

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.EXPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Backup complete",
                detail = "Your PixelPlay backup was created successfully."
            )
        }
    }

    suspend fun importFromUri(
        uri: Uri,
        sections: Set<BackupSection>,
        onProgress: (BackupTransferProgressUpdate) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val selectedSections = BackupSection.entries.filter { it in sections }
            val totalSteps = selectedSections.size + 4
            var step = 0

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.IMPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Opening backup file",
                detail = "Reading selected backup file."
            )

            val rawBytes = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes()
            } ?: error("Unable to open backup file")

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.IMPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Decoding backup",
                detail = "Extracting backup package."
            )
            val payload = decodePayload(rawBytes)

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.IMPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Validating data",
                detail = "Verifying backup content and version."
            )
            require(payload.formatVersion >= 1) { "Backup format is not supported." }

            var preferencesHandled = false
            selectedSections.forEach { section ->
                reportProgress(
                    onProgress = onProgress,
                    operation = BackupOperationType.IMPORT,
                    step = ++step,
                    totalSteps = totalSteps,
                    title = "Restoring ${section.label}",
                    detail = section.description,
                    section = section
                )

                when (section) {
                    BackupSection.PLAYLISTS,
                    BackupSection.GLOBAL_SETTINGS -> {
                        if (!preferencesHandled) {
                            restorePreferences(payload, sections)
                            preferencesHandled = true
                        }
                    }
                    BackupSection.FAVORITES -> {
                        payload.favorites?.let { favorites ->
                            favoritesDao.clearAll()
                            if (favorites.isNotEmpty()) {
                                favoritesDao.insertAll(favorites)
                            }
                        }
                    }
                    BackupSection.LYRICS -> {
                        payload.lyrics?.let { lyrics ->
                            lyricsDao.deleteAll()
                            if (lyrics.isNotEmpty()) {
                                lyricsDao.insertAll(lyrics)
                            }
                        }
                    }
                    BackupSection.SEARCH_HISTORY -> {
                        payload.searchHistory?.let { history ->
                            searchHistoryDao.clearAll()
                            if (history.isNotEmpty()) {
                                searchHistoryDao.insertAll(history)
                            }
                        }
                    }
                    BackupSection.TRANSITIONS -> {
                        payload.transitions?.let { rules ->
                            transitionDao.clearAllRules()
                            if (rules.isNotEmpty()) {
                                transitionDao.setRules(rules)
                            }
                        }
                    }
                    BackupSection.ENGAGEMENT_STATS -> {
                        payload.engagementStats?.let { stats ->
                            engagementDao.clearAllEngagements()
                            if (stats.isNotEmpty()) {
                                engagementDao.upsertEngagements(stats)
                            }
                        }
                    }
                    BackupSection.PLAYBACK_HISTORY -> {
                        payload.playbackHistory?.let { entries ->
                            playbackStatsRepository.importEventsFromBackup(
                                events = entries.map { entry ->
                                    PlaybackStatsRepository.PlaybackEvent(
                                        songId = entry.songId,
                                        timestamp = entry.timestamp,
                                        durationMs = entry.durationMs,
                                        startTimestamp = entry.startTimestamp,
                                        endTimestamp = entry.endTimestamp
                                    )
                                },
                                clearExisting = true
                            )
                        }
                    }
                }
            }

            reportProgress(
                onProgress = onProgress,
                operation = BackupOperationType.IMPORT,
                step = ++step,
                totalSteps = totalSteps,
                title = "Restore complete",
                detail = "Selected sections were restored successfully."
            )
        }
    }

    private fun splitPreferences(entries: List<PreferenceBackupEntry>): Pair<List<PreferenceBackupEntry>, List<PreferenceBackupEntry>> {
        val playlists = entries.filter { it.key in playlistPreferenceKeys }
        val globals = entries.filterNot { it.key in playlistPreferenceKeys }
        return playlists to globals
    }

    private suspend fun restorePreferences(
        payload: AppDataBackupPayload,
        selectedSections: Set<BackupSection>
    ) {
        val legacyPreferences = payload.preferences.orEmpty()
        val playlistEntries = payload.playlists ?: legacyPreferences.filter { it.key in playlistPreferenceKeys }
        val globalEntries = payload.globalSettings ?: legacyPreferences.filterNot { it.key in playlistPreferenceKeys }

        val restorePlaylists = BackupSection.PLAYLISTS in selectedSections
        val restoreGlobals = BackupSection.GLOBAL_SETTINGS in selectedSections

        when {
            restorePlaylists && restoreGlobals -> {
                val merged = (globalEntries + playlistEntries).distinctBy { it.key }
                if (merged.isNotEmpty()) {
                    userPreferencesRepository.importPreferencesFromBackup(
                        entries = merged,
                        clearExisting = true
                    )
                }
            }
            restoreGlobals -> {
                if (globalEntries.isNotEmpty()) {
                    userPreferencesRepository.clearPreferencesExceptKeys(playlistPreferenceKeys)
                    userPreferencesRepository.importPreferencesFromBackup(
                        entries = globalEntries,
                        clearExisting = false
                    )
                }
            }
            restorePlaylists -> {
                if (playlistEntries.isNotEmpty()) {
                    userPreferencesRepository.clearPreferencesByKeys(playlistPreferenceKeys)
                    userPreferencesRepository.importPreferencesFromBackup(
                        entries = playlistEntries,
                        clearExisting = false
                    )
                }
            }
        }
    }

    private fun encodePayload(payload: AppDataBackupPayload): ByteArray {
        val jsonBytes = gson.toJson(payload).toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        output.write(pxplMagic)
        GZIPOutputStream(output).use { gzip ->
            gzip.write(jsonBytes)
        }
        return output.toByteArray()
    }

    private fun decodePayload(rawBytes: ByteArray): AppDataBackupPayload {
        val json = when {
            isPxplFormat(rawBytes) -> {
                val compressed = rawBytes.copyOfRange(pxplMagic.size, rawBytes.size)
                GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
            }
            isGzipPayload(rawBytes) -> {
                GZIPInputStream(ByteArrayInputStream(rawBytes)).bufferedReader().use { it.readText() }
            }
            else -> rawBytes.toString(Charsets.UTF_8)
        }
        return gson.fromJson(json, AppDataBackupPayload::class.java) ?: error("Backup file is invalid")
    }

    private fun isPxplFormat(rawBytes: ByteArray): Boolean {
        if (rawBytes.size <= pxplMagic.size) return false
        return rawBytes.copyOfRange(0, pxplMagic.size).contentEquals(pxplMagic)
    }

    private fun isGzipPayload(rawBytes: ByteArray): Boolean {
        if (rawBytes.size < 2) return false
        return rawBytes[0] == gzipMagic[0] && rawBytes[1] == gzipMagic[1]
    }

    private fun reportProgress(
        onProgress: (BackupTransferProgressUpdate) -> Unit,
        operation: BackupOperationType,
        step: Int,
        totalSteps: Int,
        title: String,
        detail: String,
        section: BackupSection? = null
    ) {
        onProgress(
            BackupTransferProgressUpdate(
                operation = operation,
                step = step,
                totalSteps = totalSteps,
                title = title,
                detail = detail,
                section = section
            )
        )
    }
}