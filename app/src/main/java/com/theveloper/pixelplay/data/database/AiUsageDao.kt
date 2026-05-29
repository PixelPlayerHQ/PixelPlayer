package com.theveloper.pixelplay.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.theveloper.pixelplay.data.ai.ApiCallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AiUsageDao {
    // Existing AiUsageEntity methods
    @Query("SELECT * FROM ai_usage")
    suspend fun getAllUsagesOnce(): List<AiUsageEntity>

    @Query("SELECT COUNT(*) FROM ai_usage")
    suspend fun getUsageCount(): Int

    @Insert
    suspend fun insertUsage(usage: AiUsageEntity)

    @Insert
    suspend fun insertAll(usages: List<AiUsageEntity>)

    @Query("SELECT * FROM ai_usage ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentUsages(limit: Int): Flow<List<AiUsageEntity>>

    @Query("SELECT SUM(promptTokens) FROM ai_usage")
    fun getTotalPromptTokens(): Flow<Int?>

    @Query("SELECT SUM(outputTokens) FROM ai_usage")
    fun getTotalOutputTokens(): Flow<Int?>

    @Query("SELECT SUM(thoughtTokens) FROM ai_usage")
    fun getTotalThoughtTokens(): Flow<Int?>

    @Query("DELETE FROM ai_usage")
    suspend fun clearUsage()

    @Query("DELETE FROM ai_usage")
    suspend fun clearAll()

    // New ApiCallRecord methods for API call tracking
    @Insert
    suspend fun insertCall(record: ApiCallRecord)

    @Insert
    suspend fun insertCalls(records: List<ApiCallRecord>)

    @Query("SELECT * FROM api_call_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentCalls(limit: Int): Flow<List<ApiCallRecord>>

    @Query("SELECT * FROM api_call_records WHERE provider = :provider ORDER BY timestamp DESC LIMIT :limit")
    fun getCallsByProvider(provider: String, limit: Int): Flow<List<ApiCallRecord>>

    @Query("SELECT * FROM api_call_records WHERE timestamp >= :sinceTimestamp ORDER BY timestamp DESC")
    fun getCallsSince(sinceTimestamp: Long): Flow<List<ApiCallRecord>>

    @Query("SELECT COUNT(*) FROM api_call_records")
    fun getTotalCallCount(): Flow<Int>

    @Query("SELECT SUM(inputTokens) FROM api_call_records")
    fun getTotalInputTokensFromCalls(): Flow<Long?>

    @Query("SELECT SUM(outputTokens) FROM api_call_records")
    fun getTotalOutputTokensFromCalls(): Flow<Long?>

    @Query("SELECT SUM(inputTokens + outputTokens) FROM api_call_records")
    fun getTotalTokens(): Flow<Long?>

    @Query("SELECT AVG(latencyMs) FROM api_call_records WHERE success = 1")
    fun getAverageLatency(): Flow<Long?>

    @Query("SELECT COUNT(*) FROM api_call_records WHERE success = 1")
    fun getSuccessfulCallCount(): Flow<Int>

    @Query("SELECT DISTINCT provider FROM api_call_records")
    fun getUsedProviders(): Flow<List<String>>

    @Query("SELECT SUM(inputTokens) FROM api_call_records WHERE provider = :provider")
    fun getInputTokensByProvider(provider: String): Flow<Long?>

    @Query("SELECT SUM(outputTokens) FROM api_call_records WHERE provider = :provider")
    fun getOutputTokensByProvider(provider: String): Flow<Long?>

    @Delete
    suspend fun deleteCall(record: ApiCallRecord)

    @Query("DELETE FROM api_call_records WHERE timestamp < :beforeTimestamp")
    suspend fun deleteCallsBefore(beforeTimestamp: Long)

    @Query("DELETE FROM api_call_records")
    suspend fun clearApiCalls()

    @Query("DELETE FROM api_call_records WHERE provider = :provider")
    suspend fun clearCallsByProvider(provider: String)
}

