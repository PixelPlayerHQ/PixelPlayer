package com.theveloper.pixelplay.data.ai

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_call_records")
data class ApiCallRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long,
    val provider: String,
    val model: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val latencyMs: Long,
    val success: Boolean,
    val requestType: String = "unknown"
)

data class UsageStats(
    val totalInputTokens: Int = 0,
    val totalOutputTokens: Int = 0,
    val totalCalls: Int = 0,
    val successRate: Float = 0f,
    val avgLatencyMs: Long = 0L,
    val costEstimate: Double = 0.0
)

data class ModelSettings(
    val modelName: String,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val systemPrompt: String? = null,
    val retryAttempts: Int = 3,
    val timeoutMs: Int = 60000,
    val enabled: Boolean = true
)

data class ProviderPricing(
    val provider: String,
    val inputTokenCostPerMillion: Double = 0.0,
    val outputTokenCostPerMillion: Double = 0.0
) {
    fun estimateCost(inputTokens: Long, outputTokens: Long): Double {
        return (inputTokens * inputTokenCostPerMillion / 1_000_000) +
               (outputTokens * outputTokenCostPerMillion / 1_000_000)
    }
}
