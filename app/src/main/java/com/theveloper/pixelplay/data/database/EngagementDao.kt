package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAO for song engagement statistics.
 * Provides efficient database operations for tracking play counts and durations.
 */
@Dao
interface EngagementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagement(engagement: SongEngagementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEngagements(engagements: List<SongEngagementEntity>)

    @Query("SELECT * FROM song_engagements WHERE song_id = :songId")
    suspend fun getEngagement(songId: String): SongEngagementEntity?

    @Query("SELECT * FROM song_engagements")
    suspend fun getAllEngagements(): List<SongEngagementEntity>

    @Query("SELECT * FROM song_engagements")
    fun getAllEngagementsFlow(): Flow<List<SongEngagementEntity>>

    @Query("SELECT play_count FROM song_engagements WHERE song_id = :songId")
    suspend fun getPlayCount(songId: String): Int?

    @Query("DELETE FROM song_engagements WHERE song_id = :songId")
    suspend fun deleteEngagement(songId: String)

    @Query("DELETE FROM song_engagements WHERE song_id NOT IN (SELECT CAST(id AS TEXT) FROM songs)")
    suspend fun deleteOrphanedEngagements()

    @Query("DELETE FROM song_engagements")
    suspend fun clearAllEngagements()

    /**
     * Increments play count and updates last played timestamp atomically.
     * More efficient than read-modify-write pattern.
     */
    @Query("""
        INSERT INTO song_engagements (song_id, play_count, total_play_duration_ms, last_played_timestamp)
        VALUES (:songId, 1, :durationMs, :timestamp)
        ON CONFLICT(song_id) DO UPDATE SET
            play_count = play_count + 1,
            total_play_duration_ms = total_play_duration_ms + :durationMs,
            last_played_timestamp = :timestamp
    """)
    suspend fun recordPlay(songId: String, durationMs: Long, timestamp: Long)

    /**
     * Get top songs by play count for quick access.
     */
    @Query("SELECT * FROM song_engagements ORDER BY play_count DESC LIMIT :limit")
    suspend fun getTopPlayedSongs(limit: Int): List<SongEngagementEntity>

    /**
     * Get recently played songs ordered by last played timestamp.
     */
    @Query("SELECT * FROM song_engagements WHERE last_played_timestamp > 0 ORDER BY last_played_timestamp DESC LIMIT :limit")
    suspend fun getRecentlyPlayedSongs(limit: Int): List<SongEngagementEntity>

    @Transaction
    suspend fun replaceAll(engagements: List<SongEngagementEntity>) {
        clearAllEngagements()
        if (engagements.isNotEmpty()) upsertEngagements(engagements)
    }
}
