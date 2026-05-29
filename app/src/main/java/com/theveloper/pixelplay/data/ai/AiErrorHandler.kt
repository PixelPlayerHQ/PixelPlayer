package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.ai.provider.AiProviderException
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI error handler with fallbacks and error recovery.
 * Provides graceful degradation when AI services fail.
 */
@Singleton
class AiErrorHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger,
    private val preferencesRepo: AiPreferencesRepository
) {
    /**
     * Error categories for AI operations.
     */
    enum class ErrorCategory {
        NETWORK_ERROR,
        API_KEY_ERROR,
        RATE_LIMIT_ERROR,
        MODEL_UNAVAILABLE_ERROR,
        TIMEOUT_ERROR,
        PARSING_ERROR,
        EMPTY_RESPONSE_ERROR,
        UNKNOWN_ERROR
    }

    /**
     * Result of error analysis with recovery suggestions.
     */
    data class ErrorAnalysis(
        val category: ErrorCategory,
        val message: String,
        val userMessage: String,
        val canRetry: Boolean,
        val suggestedFallback: AiProvider?,
        val recoveryAction: RecoveryAction
    )

    enum class RecoveryAction {
        RETRY_SAME_PROVIDER,
        SWITCH_PROVIDER,
        USE_FALLBACK_RESPONSE,
        DISABLE_AI_FEATURES,
        SHOW_ERROR
    }

    /**
     * Analyzes an error and returns recovery suggestions.
     */
    suspend fun analyzeError(error: Throwable, currentProvider: AiProvider): ErrorAnalysis {
        val cause = findRootCause(error)
        val message = cause.message ?: "Unknown error"
        val category = categorizeError(message, error)
        val userMessage = getUserMessage(category, message)
        val canRetry = canRetryOperation(category)
        val fallback = getFallbackProvider(currentProvider, category)
        val recoveryAction = determineRecoveryAction(category, canRetry, fallback)

        aiLogger.log("op" to "ERROR_ANALYSIS", "provider" to currentProvider.name, "error" to message)

        return ErrorAnalysis(
            category = category,
            message = message,
            userMessage = userMessage,
            canRetry = canRetry,
            suggestedFallback = fallback,
            recoveryAction = recoveryAction
        )
    }

    /**
     * Attempts to recover from an error.
     */
    suspend fun <T> withRecovery(
        error: Throwable,
        currentProvider: AiProvider,
        operation: suspend (AiProvider) -> Result<T>,
        maxRetries: Int = 2
    ): Result<T> {
        var lastError = error
        var currentProviderUsed = currentProvider

        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                Timber.tag("AiErrorHandler").d("Retry attempt $attempt for ${currentProviderUsed.name}")
            }

            val result = operation(currentProviderUsed)

            if (result.isSuccess) {
                return result
            }

            lastError = result.exceptionOrNull() ?: Exception("Unknown error")
            val analysis = analyzeError(lastError, currentProviderUsed)

            when (analysis.recoveryAction) {
                RecoveryAction.RETRY_SAME_PROVIDER -> {
                    // Already retrying, continue to next attempt
                }
                RecoveryAction.SWITCH_PROVIDER -> {
                    analysis.suggestedFallback?.let { fallback ->
                        currentProviderUsed = fallback
                        Timber.tag("AiErrorHandler").d("Switching to fallback provider: ${fallback.name}")
                    }
                }
                RecoveryAction.USE_FALLBACK_RESPONSE -> {
                    return Result.failure(Exception("Using fallback response"))
                }
                else -> {
                    return result
                }
            }
        }

        return Result.failure(lastError)
    }

    private fun findRootCause(error: Throwable): Throwable {
        return generateSequence(error) { it.cause }
            .lastOrNull() ?: error
    }

    private fun categorizeError(message: String, error: Throwable): ErrorCategory {
        val m = message.lowercase()
        if (error is AiProviderException) return when {
            error.isApiKeyIssue() || "api key" in m -> ErrorCategory.API_KEY_ERROR
            error.isBillingIssue() || "quota" in m -> ErrorCategory.RATE_LIMIT_ERROR
            error.isModelUnavailable() -> ErrorCategory.MODEL_UNAVAILABLE_ERROR
            else -> ErrorCategory.UNKNOWN_ERROR
        }
        return when {
            "network" in m || "connect" in m || "no internet" in m -> ErrorCategory.NETWORK_ERROR
            "api key" in m || "unauthorized" in m || "401" in m || "403" in m -> ErrorCategory.API_KEY_ERROR
            "rate limit" in m || "429" in m || "too many requests" in m -> ErrorCategory.RATE_LIMIT_ERROR
            "model" in m && ("not found" in m || "unavailable" in m) -> ErrorCategory.MODEL_UNAVAILABLE_ERROR
            "timeout" in m -> ErrorCategory.TIMEOUT_ERROR
            "parse" in m || "json" in m || "format" in m -> ErrorCategory.PARSING_ERROR
            "empty" in m || "no response" in m -> ErrorCategory.EMPTY_RESPONSE_ERROR
            else -> ErrorCategory.UNKNOWN_ERROR
        }
    }

    private fun getUserMessage(category: ErrorCategory, message: String): String = when (category) {
        ErrorCategory.NETWORK_ERROR -> "No internet connection. Check WiFi or mobile data."
        ErrorCategory.API_KEY_ERROR -> "API key issue. Check AI provider settings."
        ErrorCategory.RATE_LIMIT_ERROR -> "Rate limit reached. Wait and try again."
        ErrorCategory.MODEL_UNAVAILABLE_ERROR -> "AI model unavailable. Try a different model."
        ErrorCategory.TIMEOUT_ERROR -> "Request timed out. The AI service is slow."
        ErrorCategory.PARSING_ERROR -> "Failed to parse AI response. Try again."
        ErrorCategory.EMPTY_RESPONSE_ERROR -> "AI returned an empty response. Try again."
        ErrorCategory.UNKNOWN_ERROR -> "Error: ${message.take(100)}"
    }

    private fun canRetryOperation(category: ErrorCategory): Boolean {
        return when (category) {
            ErrorCategory.NETWORK_ERROR -> true
            ErrorCategory.TIMEOUT_ERROR -> true
            ErrorCategory.RATE_LIMIT_ERROR -> true
            ErrorCategory.PARSING_ERROR -> true
            ErrorCategory.EMPTY_RESPONSE_ERROR -> true
            else -> false
        }
    }

    private suspend fun getFallbackProvider(currentProvider: AiProvider, category: ErrorCategory): AiProvider? {
        // Priority order for fallback: prefer providers with configured API keys
        val priorityProviders = listOf(
            AiProvider.GEMINI,
            AiProvider.OPENAI,
            AiProvider.ANTHROPIC,
            AiProvider.OLLAMA
        )

        // Find first provider that has API key configured and isn't the current one
        for (provider in priorityProviders) {
            if (provider == currentProvider) continue

            // Skip Ollama in fallback - it's for model downloads only
            if (provider == AiProvider.OLLAMA) continue

            // Check if this provider has an API key configured
            val apiKey = try {
                preferencesRepo.getApiKey(provider).first()
            } catch (e: Exception) {
                Timber.w(e, "Failed to check API key for $provider")
                continue
            }

            if (apiKey.isNotBlank()) {
                return provider
            }
        }

        return null // No fallback available
    }

    private fun determineRecoveryAction(
        category: ErrorCategory,
        canRetry: Boolean,
        fallback: AiProvider?
    ): RecoveryAction {
        if (canRetry) {
            return RecoveryAction.RETRY_SAME_PROVIDER
        }

        return when (category) {
            ErrorCategory.API_KEY_ERROR,
            ErrorCategory.MODEL_UNAVAILABLE_ERROR -> {
                if (fallback != null) RecoveryAction.SWITCH_PROVIDER
                else RecoveryAction.SHOW_ERROR
            }
            ErrorCategory.NETWORK_ERROR -> {
                if (fallback != null) RecoveryAction.SWITCH_PROVIDER
                else RecoveryAction.SHOW_ERROR
            }
            ErrorCategory.RATE_LIMIT_ERROR -> {
                if (fallback != null) RecoveryAction.SWITCH_PROVIDER
                else RecoveryAction.SHOW_ERROR
            }
            else -> RecoveryAction.SHOW_ERROR
        }
    }

    /**
     * Gets a user-friendly error message.
     */
    suspend fun getUserFriendlyMessage(error: Throwable): String {
        val analysis = analyzeError(error, AiProvider.GEMINI)
        return analysis.userMessage
    }
}