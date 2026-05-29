package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageDao {

    // ======== AI Usage Tracking ========

    @Query("SELECT * FROM ai_usage ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUsages(limit: Int): Flow<List<AiUsageEntity>>

    @Query("SELECT * FROM ai_usage WHERE provider = :provider ORDER BY timestamp DESC LIMIT :limit")
    fun getUsagesByProvider(provider: String, limit: Int): Flow<List<AiUsageEntity>>

    @Query("SELECT * FROM ai_usage WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getUsagesSince(sinceTimestamp: Long): Flow<List<AiUsageEntity>>

    @Query("SELECT COUNT(*) FROM ai_usage")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT SUM(promptTokens) FROM ai_usage")
    fun getTotalPromptTokens(): Flow<Int?>

    @Query("SELECT SUM(outputTokens) FROM ai_usage")
    fun getTotalOutputTokens(): Flow<Int?>

    @Query("SELECT SUM(thoughtTokens) FROM ai_usage")
    fun getTotalThoughtTokens(): Flow<Int?>

    @Query("SELECT DISTINCT provider FROM ai_usage")
    fun getUsedProviders(): Flow<List<String>>

    @Query("SELECT SUM(promptTokens) FROM ai_usage WHERE provider = :provider")
    fun getPromptTokensByProvider(provider: String): Flow<Int?>

    @Query("SELECT SUM(outputTokens) FROM ai_usage WHERE provider = :provider")
    fun getOutputTokensByProvider(provider: String): Flow<Int?>

    @Query("SELECT SUM(promptTokens + outputTokens) FROM ai_usage")
    fun getTotalTokens(): Flow<Int?>

    // ======== Insert Operations ========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: AiUsageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(usages: List<AiUsageEntity>)

    // ======== Delete Operations ========

    @Query("DELETE FROM ai_usage")
    suspend fun clearAll()

    @Query("DELETE FROM ai_usage WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldUsages(beforeTimestamp: Long)

    @Query("DELETE FROM ai_usage WHERE provider = :provider")
    suspend fun clearByProvider(provider: String)

    // ======== Legacy/Compat Methods ========

    @Query("SELECT * FROM ai_usage")
    suspend fun getAllUsagesOnce(): List<AiUsageEntity>

    @Query("SELECT COUNT(*) FROM ai_usage")
    suspend fun getUsageCount(): Int

    @Query("DELETE FROM ai_usage")
    suspend fun clearUsage()
}