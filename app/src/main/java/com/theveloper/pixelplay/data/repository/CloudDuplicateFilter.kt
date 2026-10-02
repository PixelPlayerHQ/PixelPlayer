package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.DuplicateSongMatcher
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the "Hide duplicate songs" option for cloud sources merged into the unified
 * `songs` table. A cloud song is skipped when the same track already comes from a source
 * with higher priority (see [SOURCE_PRIORITY]) or appears earlier in the same source.
 */
@Singleton
class CloudDuplicateFilter @Inject constructor(
    private val musicDao: MusicDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository
) {

    /** Returns null when the option is disabled. */
    suspend fun startSession(sourceType: Int): Session? {
        if (!userPreferencesRepository.skipDuplicateSongsFlow.first()) return null
        val matcher = DuplicateSongMatcher()
        val sourceIndex = SOURCE_PRIORITY.indexOf(sourceType)
        val prioritySources = if (sourceIndex >= 0) SOURCE_PRIORITY.take(sourceIndex) else SOURCE_PRIORITY
        if (prioritySources.isNotEmpty()) {
            musicDao.getSongDuplicateKeysBySourceTypes(prioritySources).forEach {
                matcher.add(it.id, it.title, it.artistName, it.duration)
            }
        }
        return Session(matcher)
    }

    /**
     * Moves favorites and playlist entries of the hidden songs to the copies that are kept.
     * Must run before the hidden songs are deleted, because deleting a song also deletes
     * its favorite flag.
     */
    suspend fun transferUserData(session: Session) {
        val hiddenToKept = session.hiddenToKept
        if (hiddenToKept.isEmpty()) return
        hiddenToKept.forEach { (hiddenId, keptId) -> musicDao.copyFavorite(hiddenId, keptId) }
        playlistPreferencesRepository.replaceSongIdsInAllPlaylists(
            hiddenToKept.entries.associate { (hiddenId, keptId) -> hiddenId.toString() to keptId.toString() }
        )
    }

    class Session internal constructor(private val matcher: DuplicateSongMatcher) {
        private val hidden = LinkedHashMap<Long, Long>()

        val hiddenToKept: Map<Long, Long> get() = hidden
        val hiddenCount: Int get() = hidden.size

        /** Returns false when the song duplicates one that is already kept. */
        fun keep(songId: Long, title: String, artist: String, durationMs: Long): Boolean {
            val keptId = matcher.findDuplicate(title, artist, durationMs)
            return when (keptId) {
                null -> {
                    matcher.add(songId, title, artist, durationMs)
                    true
                }
                // The same cloud song listed twice (e.g. in two playlists) maps to one row.
                songId -> true
                else -> {
                    hidden[songId] = keptId
                    false
                }
            }
        }
    }

    companion object {
        val SOURCE_PRIORITY = listOf(
            SourceType.LOCAL,
            SourceType.TELEGRAM,
            SourceType.NETEASE,
            SourceType.GDRIVE,
            SourceType.QQMUSIC,
            SourceType.NAVIDROME,
            SourceType.JELLYFIN
        )
    }
}
