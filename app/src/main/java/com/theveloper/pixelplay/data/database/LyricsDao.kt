package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LyricsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(lyrics: LyricsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(lyrics: List<LyricsEntity>)

    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: Long): LyricsEntity?

    @Query("DELETE FROM lyrics WHERE songId = :songId")
    suspend fun deleteLyrics(songId: Long)

    @Query("DELETE FROM lyrics")
    suspend fun deleteAll()

    @Query("SELECT * FROM lyrics")
    suspend fun getAll(): List<LyricsEntity>

    @Query("SELECT songId FROM lyrics WHERE songId IN (:songIds) AND content != ''")
    suspend fun getSongIdsWithLyrics(songIds: List<Long>): List<Long>

    @Transaction
    suspend fun replaceAll(lyrics: List<LyricsEntity>) {
        deleteAll()
        if (lyrics.isNotEmpty()) insertAll(lyrics)
    }
}
