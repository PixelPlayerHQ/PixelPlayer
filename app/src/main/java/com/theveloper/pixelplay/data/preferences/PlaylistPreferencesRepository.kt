package com.theveloper.pixelplay.data.preferences

import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.database.toEntity
import com.theveloper.pixelplay.data.database.toPlaylist
import com.theveloper.pixelplay.data.model.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistPreferencesRepository @Inject constructor(
    private val localPlaylistDao: LocalPlaylistDao,
    private val userPreferencesRepository: UserPreferencesRepository
) {
    private val migrationMutex = Mutex()
    @Volatile
    private var migrationChecked = false

    val userPlaylistsFlow: Flow<List<Playlist>> = localPlaylistDao.observePlaylistsWithSongs()
        .onStart { ensureMigratedIfNeeded() }
        .map { rows ->
            rows.map { row ->
                row.playlist.toPlaylist(
                    songIds = row.songs.sortedBy { it.sortOrder }.map { it.songId }
                )
            }
        }

    val playlistSongOrderModesFlow: Flow<Map<String, String>> =
        userPreferencesRepository.playlistSongOrderModesFlow
    val playlistsSortOptionFlow: Flow<String> = userPreferencesRepository.playlistsSortOptionFlow
    val showTelegramCloudPlaylistsFlow: Flow<Boolean> =
        userPreferencesRepository.showTelegramCloudPlaylistsFlow
    val telegramTopicDisplayModeFlow: Flow<TelegramTopicDisplayMode> =
        userPreferencesRepository.telegramTopicDisplayModeFlow
    suspend fun setTelegramTopicDisplayMode(mode: TelegramTopicDisplayMode) =
        userPreferencesRepository.setTelegramTopicDisplayMode(mode)

    suspend fun createPlaylist(
        name: String,
        songIds: List<String> = emptyList(),
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverImageUri: String? = null,
        coverColorArgb: Int? = null,
        coverIconName: String? = null,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
        customId: String? = null,
        source: String = "LOCAL"
    ): Playlist {
        ensureMigratedIfNeeded()
        val now = System.currentTimeMillis()
        val newPlaylist = Playlist(
            id = customId ?: UUID.randomUUID().toString(),
            name = name,
            songIds = songIds,
            createdAt = now,
            lastModified = now,
            isAiGenerated = isAiGenerated,
            isQueueGenerated = isQueueGenerated,
            coverImageUri = coverImageUri,
            coverColorArgb = coverColorArgb,
            coverIconName = coverIconName,
            coverShapeType = coverShapeType,
            coverShapeDetail1 = coverShapeDetail1,
            coverShapeDetail2 = coverShapeDetail2,
            coverShapeDetail3 = coverShapeDetail3,
            coverShapeDetail4 = coverShapeDetail4,
            source = source,
        )
        localPlaylistDao.upsertPlaylist(newPlaylist.toEntity())
        localPlaylistDao.replacePlaylistSongs(newPlaylist.id, newPlaylist.songIds)
        return newPlaylist
    }

    suspend fun deletePlaylist(playlistId: String) {
        ensureMigratedIfNeeded()
        localPlaylistDao.deletePlaylist(playlistId)
        clearPlaylistSongOrderMode(playlistId)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        val updated = existing.copy(
            name = newName,
            lastModified = System.currentTimeMillis()
        )
        localPlaylistDao.upsertPlaylist(updated.toEntity())
    }

    suspend fun updatePlaylist(playlist: Playlist) {
        ensureMigratedIfNeeded()
        val updated = playlist.copy(lastModified = System.currentTimeMillis())
        localPlaylistDao.upsertPlaylist(updated.toEntity())
        localPlaylistDao.replacePlaylistSongs(updated.id, updated.songIds)
    }

    suspend fun addSongsToPlaylist(playlistId: String, songIdsToAdd: List<String>) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        val merged = (existing.songIds + songIdsToAdd).distinct()
        updatePlaylist(existing.copy(songIds = merged))
    }

    suspend fun addOrRemoveSongFromPlaylists(songId: String, playlistIds: List<String>): MutableList<String> {
        ensureMigratedIfNeeded()
        val currentPlaylists = userPlaylistsFlow.first()
        val removedPlaylistIds = mutableListOf<String>()

        currentPlaylists.forEach { playlist ->
            val shouldContain = playlist.id in playlistIds
            val hasSong = songId in playlist.songIds
            when {
                shouldContain && !hasSong -> {
                    addSongsToPlaylist(playlist.id, listOf(songId))
                }
                !shouldContain && hasSong -> {
                    removeSongFromPlaylist(playlist.id, songId)
                    removedPlaylistIds.add(playlist.id)
                }
            }
        }
        return removedPlaylistIds
    }

    suspend fun removeSongFromPlaylist(playlistId: String, songIdToRemove: String) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        updatePlaylist(existing.copy(songIds = existing.songIds.filterNot { it == songIdToRemove }))
    }

    suspend fun reorderSongsInPlaylist(playlistId: String, newSongOrderIds: List<String>) {
        ensureMigratedIfNeeded()
        val existing = userPlaylistsFlow.first().find { it.id == playlistId } ?: return
        updatePlaylist(existing.copy(songIds = newSongOrderIds))
    }

    suspend fun setPlaylistSongOrderMode(playlistId: String, modeValue: String) =
        userPreferencesRepository.setPlaylistSongOrderMode(playlistId, modeValue)

    suspend fun clearPlaylistSongOrderMode(playlistId: String) =
        userPreferencesRepository.clearPlaylistSongOrderMode(playlistId)

    suspend fun setPlaylistSongOrderModes(modes: Map<String, String>) =
        userPreferencesRepository.setPlaylistSongOrderModes(modes)

    suspend fun setPlaylistsSortOption(optionKey: String) =
        userPreferencesRepository.setPlaylistsSortOption(optionKey)

    suspend fun setShowTelegramCloudPlaylists(show: Boolean) =
        userPreferencesRepository.setShowTelegramCloudPlaylists(show)

    suspend fun getPlaylistsOnce(): List<Playlist> {
        ensureMigratedIfNeeded()
        return userPlaylistsFlow.first()
    }

    suspend fun replaceAllPlaylists(playlists: List<Playlist>) {
        ensureMigratedIfNeeded()
        localPlaylistDao.replaceAllPlaylistsTransactional(
            playlists.map { playlist -> playlist.toEntity() to playlist.songIds }
        )
        userPreferencesRepository.clearLegacyUserPlaylists()
    }

    suspend fun removeSongFromAllPlaylists(songId: String) {
        ensureMigratedIfNeeded()
        val playlists = userPlaylistsFlow.first()
        playlists.forEach { playlist ->
            if (songId in playlist.songIds) {
                updatePlaylist(
                    playlist.copy(
                        songIds = playlist.songIds.filterNot { it == songId }
                    )
                )
            }
        }
    }

    suspend fun resetPlaylistPreferencesToDefaults() {
        setPlaylistSongOrderModes(emptyMap())
        setPlaylistsSortOption(SortOption.PlaylistNameAZ.storageKey)
    }

    private suspend fun ensureMigratedIfNeeded() {
        if (migrationChecked) return
        migrationMutex.withLock {
            if (migrationChecked) return
            val roomCount = localPlaylistDao.getPlaylistCount()
            if (roomCount == 0) {
                val legacy = userPreferencesRepository.getLegacyUserPlaylistsOnce()
                legacy.forEach { playlist ->
                    localPlaylistDao.upsertPlaylist(playlist.toEntity())
                    localPlaylistDao.replacePlaylistSongs(playlist.id, playlist.songIds)
                }
                if (legacy.isNotEmpty()) {
                    userPreferencesRepository.clearLegacyUserPlaylists()
                }
            }
            migrationChecked = true
        }
    }
}
