package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Navidrome/Subsonic cached data.
 */
@Dao
interface NavidromeDao {

    // ─── Songs ─────────────────────────────────────────────────────────

    @Query("SELECT * FROM navidrome_songs ORDER BY date_added DESC")
    fun getAllNavidromeSongs(): Flow<List<NavidromeSongEntity>>

    @Query("SELECT * FROM navidrome_songs ORDER BY date_added DESC")
    suspend fun getAllNavidromeSongsList(): List<NavidromeSongEntity>

    @Query("SELECT * FROM navidrome_songs WHERE playlist_id = :playlistId ORDER BY date_added DESC")
    fun getSongsByPlaylist(playlistId: String): Flow<List<NavidromeSongEntity>>

    @Query("SELECT * FROM navidrome_songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    fun searchSongs(query: String): Flow<List<NavidromeSongEntity>>

    @Query("SELECT * FROM navidrome_songs WHERE id IN (:ids)")
    fun getSongsByIds(ids: List<String>): Flow<List<NavidromeSongEntity>>

    @Query("SELECT * FROM navidrome_songs WHERE navidrome_id = :navidromeId LIMIT 1")
    suspend fun getSongByNavidromeId(navidromeId: String): NavidromeSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<NavidromeSongEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: NavidromeSongEntity)

    @Query("DELETE FROM navidrome_songs WHERE id = :songId")
    suspend fun deleteSong(songId: String)

    @Query("DELETE FROM navidrome_songs WHERE playlist_id = :playlistId")
    suspend fun deleteSongsByPlaylist(playlistId: String)

    // ─── Playlists ─────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: NavidromePlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<NavidromePlaylistEntity>)

    @Query("SELECT * FROM navidrome_playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<NavidromePlaylistEntity>>

    @Query("SELECT * FROM navidrome_playlists")
    suspend fun getAllPlaylistsList(): List<NavidromePlaylistEntity>

    @Query("SELECT * FROM navidrome_playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: String): NavidromePlaylistEntity?

    @Query("DELETE FROM navidrome_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM navidrome_playlists")
    suspend fun getPlaylistCount(): Int

    @Query("SELECT DISTINCT navidrome_id FROM navidrome_songs")
    suspend fun getAllDistinctNavidromeIds(): List<String>

    @Query("DELETE FROM navidrome_songs WHERE playlist_id = '__library__'")
    suspend fun clearLibrarySongs()

    // ─── Clear All ─────────────────────────────────────────────────────

    @Query("DELETE FROM navidrome_songs")
    suspend fun clearAllSongs()

    @Query("DELETE FROM navidrome_playlists")
    suspend fun clearAllPlaylists()
}
