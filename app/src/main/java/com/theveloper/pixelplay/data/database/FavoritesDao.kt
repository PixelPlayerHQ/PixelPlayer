package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

@Dao
interface FavoritesDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setFavorite(favorite: FavoritesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoritesEntity>)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun removeFavorite(songId: Long)

    @Query("SELECT isFavorite FROM favorites WHERE songId = :songId")
    suspend fun isFavorite(songId: Long): Boolean?

    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 ORDER BY songId")
    fun getFavoriteSongIdsRaw(): Flow<List<Long>>

    fun getFavoriteSongIds(): Flow<List<Long>> = getFavoriteSongIdsRaw().distinctUntilChanged()

    @Query("SELECT songId FROM favorites WHERE isFavorite = 1 ORDER BY songId")
    suspend fun getFavoriteSongIdsOnce(): List<Long>

    @Query("SELECT * FROM favorites")
    suspend fun getAllFavoritesOnce(): List<FavoritesEntity>

    @Query("DELETE FROM favorites")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(favorites: List<FavoritesEntity>) {
        clearAll()
        if (favorites.isNotEmpty()) insertAll(favorites)
    }
}
