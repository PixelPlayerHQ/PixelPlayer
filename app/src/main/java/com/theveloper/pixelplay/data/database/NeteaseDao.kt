package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NeteaseDao {

    // ─── Songs ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM netease_songs ORDER BY date_added DESC")
    fun getAllNeteaseSongs(): Flow<List<NeteaseSongEntity>>

    @Query("SELECT * FROM netease_songs ORDER BY date_added DESC")
    suspend fun getAllNeteaseSongsList(): List<NeteaseSongEntity>

    /** Lightweight count — use this for guard checks instead of loading all songs. */
    @Query("SELECT COUNT(*) FROM netease_songs")
    suspend fun getNeteaseCount(): Int

    @Query("SELECT * FROM netease_songs WHERE playlist_id = :playlistId ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistId: Long): Flow<List<NeteaseSongEntity>>

    @Query("SELECT * FROM netease_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<NeteaseSongEntity>>

    @Query("SELECT * FROM netease_songs WHERE id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<NeteaseSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<NeteaseSongEntity>)

    @Query("DELETE FROM netease_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM netease_songs WHERE playlist_id = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: Long)

    // ─── Playlists ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: NeteasePlaylistEntity)

    @Query("SELECT * FROM netease_playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<NeteasePlaylistEntity>>

    @Query("SELECT * FROM netease_playlists")
    suspend fun getAllPlaylistsList(): List<NeteasePlaylistEntity>

    @Query("DELETE FROM netease_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    // ─── Clear All ─────────────────────────────────────────────────────

    @Query("DELETE FROM netease_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM netease_playlists")
    suspend fun clearAllPlaylists()
}
