package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QqMusicDao {

    // ─── Songs ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM qqmusic_songs ORDER BY date_added DESC")
    fun getAllQqMusicSongs(): Flow<List<QqMusicSongEntity>>

    @Query("SELECT * FROM qqmusic_songs ORDER BY date_added DESC")
    suspend fun getAllQqMusicSongsList(): List<QqMusicSongEntity>

    @Query("SELECT * FROM qqmusic_songs WHERE playlist_id = :playlistId ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistId: Long): Flow<List<QqMusicSongEntity>>

    @Query("SELECT * FROM qqmusic_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<QqMusicSongEntity>>

    @Query("SELECT * FROM qqmusic_songs WHERE id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<QqMusicSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<QqMusicSongEntity>)

    @Query("DELETE FROM qqmusic_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM qqmusic_songs WHERE playlist_id = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: Long)

    // ─── Playlists ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: QqMusicPlaylistEntity)

    @Query("SELECT * FROM qqmusic_playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<QqMusicPlaylistEntity>>

    @Query("SELECT * FROM qqmusic_playlists")
    suspend fun getAllPlaylistsList(): List<QqMusicPlaylistEntity>

    @Query("DELETE FROM qqmusic_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
