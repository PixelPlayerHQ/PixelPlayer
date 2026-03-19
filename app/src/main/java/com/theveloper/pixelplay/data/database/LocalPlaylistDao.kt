package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {
    @Transaction
    @Query("SELECT * FROM playlists ORDER BY last_modified DESC")
    fun observePlaylistsWithSongs(): Flow<List<PlaylistWithSongsEntity>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylistWithSongs(playlistId: String): Flow<PlaylistWithSongsEntity?>

    @Query("SELECT * FROM playlist_songs WHERE playlist_id = :playlistId ORDER BY sort_order ASC")
    fun observePlaylistSongs(playlistId: String): Flow<List<PlaylistSongEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): PlaylistEntity?

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun getPlaylistCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylist(entity: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(entity: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistSongs(entities: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun clearPlaylistSongs(playlistId: String)

    @Query("DELETE FROM playlist_songs")
    suspend fun clearAllPlaylistSongs()

    @Query("DELETE FROM playlists")
    suspend fun clearAllPlaylists()

    @Transaction
    suspend fun replacePlaylistSongs(playlistId: String, songIds: List<String>) {
        clearPlaylistSongs(playlistId)
        if (songIds.isEmpty()) return
        val rows = songIds.mapIndexed { index, songId ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = songId,
                sortOrder = index
            )
        }
        upsertPlaylistSongs(rows)
    }

    @Transaction
    suspend fun replaceAllPlaylistsTransactional(playlists: List<Pair<PlaylistEntity, List<String>>>) {
        clearAllPlaylistSongs()
        clearAllPlaylists()
        playlists.forEach { (entity, songIds) ->
            upsertPlaylist(entity)
            replacePlaylistSongs(entity.id, songIds)
        }
    }
}
