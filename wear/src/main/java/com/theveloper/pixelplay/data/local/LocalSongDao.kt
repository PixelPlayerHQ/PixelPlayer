package com.theveloper.pixelplay.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for querying locally stored songs on the watch.
 */
@Dao
interface LocalSongDao {

    @Query("SELECT * FROM local_songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE songId = :songId")
    suspend fun getSongById(songId: String): LocalSongEntity?

    @Query("SELECT songId FROM local_songs")
    fun getAllSongIds(): Flow<List<String>>

    @Query("SELECT songId FROM local_songs")
    suspend fun getAllSongIdsOnce(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: LocalSongEntity)

    @Query(
        "UPDATE local_songs " +
            "SET isFavorite = :isFavorite, favoriteSyncPending = :favoriteSyncPending " +
            "WHERE songId = :songId"
    )
    suspend fun updateFavoriteState(songId: String, isFavorite: Boolean, favoriteSyncPending: Boolean)

    @Query("UPDATE local_songs SET favoriteSyncPending = :favoriteSyncPending WHERE songId = :songId")
    suspend fun updateFavoritePending(songId: String, favoriteSyncPending: Boolean)

    @Query("SELECT * FROM local_songs WHERE favoriteSyncPending = 1")
    suspend fun getPendingFavoriteSongs(): List<LocalSongEntity>

    @Query("UPDATE local_songs SET paletteSeedArgb = :paletteSeedArgb WHERE songId = :songId")
    suspend fun updatePaletteSeed(songId: String, paletteSeedArgb: Int)

    @Query("UPDATE local_songs SET artworkPath = :artworkPath WHERE songId = :songId")
    suspend fun updateArtworkPath(songId: String, artworkPath: String?)

    @Query("DELETE FROM local_songs WHERE songId = :songId")
    suspend fun deleteById(songId: String)

    @Query("SELECT SUM(fileSize) FROM local_songs")
    suspend fun getTotalStorageUsed(): Long?
}
