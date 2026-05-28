package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.ai.provider.AiProviderException
import dagger.hilt.android.qualifiers.ApplicationContext
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
    private val aiLogger: AiLogger
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
    fun analyzeError(error: Throwable, currentProvider: AiProvider): ErrorAnalysis {
        val cause = findRootCause(error)
        val message = cause.message ?: "Unknown error"
        val category = categorizeError(message, error)
        val userMessage = getUserMessage(category, message)
        val canRetry = canRetryOperation(category)
        val fallback = getFallbackProvider(currentProvider, category)
        val recoveryAction = determineRecoveryAction(category, canRetry, fallback)

        aiLogger.logOperation(
            operation = "ERROR_ANALYSIS",
            provider = currentProvider.name,
            model = "",
            prompt = "",
            response = null,
            success = false,
            durationMs = 0,
            error = message
        )

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
        val lowerMessage = message.lowercase()

        // Check for AI-specific exceptions first
        if (error is AiProviderException) {
            return when {
                error.isApiKeyIssue() || lowerMessage.contains("api key") -> ErrorCategory.API_KEY_ERROR
                error.isBillingIssue() || lowerMessage.contains("quota") -> ErrorCategory.RATE_LIMIT_ERROR
                error.isModelUnavailable() -> ErrorCategory.MODEL_UNAVAILABLE_ERROR
                else -> ErrorCategory.UNKNOWN_ERROR
            }
        }

        return when {
            lowerMessage.contains("network") ||
            lowerMessage.contains("connect") ||
            lowerMessage.contains("no internet") ||
            lowerMessage.contains("timeout") -> ErrorCategory.NETWORK_ERROR

            lowerMessage.contains("api key") ||
            lowerMessage.contains("unauthorized") ||
            lowerMessage.contains("401") ||
            lowerMessage.contains("403") -> ErrorCategory.API_KEY_ERROR

            lowerMessage.contains("rate limit") ||
            lowerMessage.contains("429") ||
            lowerMessage.contains("too many requests") -> ErrorCategory.RATE_LIMIT_ERROR

            lowerMessage.contains("model") &&
            (lowerMessage.contains("not found") ||
             lowerMessage.contains("unavailable")) -> ErrorCategory.MODEL_UNAVAILABLE_ERROR

            lowerMessage.contains("timeout") -> ErrorCategory.TIMEOUT_ERROR

            lowerMessage.contains("parse") ||
            lowerMessage.contains("json") ||
            lowerMessage.contains("format") -> ErrorCategory.PARSING_ERROR

            lowerMessage.contains("empty") ||
            lowerMessage.contains("no response") -> ErrorCategory.EMPTY_RESPONSE_ERROR

            else -> ErrorCategory.UNKNOWN_ERROR
        }
    }

    private fun getUserMessage(category: ErrorCategory, message: String): String {
        return when (category) {
            ErrorCategory.NETWORK_ERROR -> {
                "No internet connection. Please check your WiFi or mobile data and try again."
            }
            ErrorCategory.API_KEY_ERROR -> {
                "API key issue. Please check your AI provider settings in Preferences."
            }
            ErrorCategory.RATE_LIMIT_ERROR -> {
                "Rate limit reached. Please wait a moment and try again."
            }
            ErrorCategory.MODEL_UNAVAILABLE_ERROR -> {
                "The selected AI model is unavailable. Try a different model or provider."
            }
            ErrorCategory.TIMEOUT_ERROR -> {
                "Request timed out. The AI service is taking too long to respond."
            }
            ErrorCategory.PARSING_ERROR -> {
                "Failed to parse AI response. Please try again."
            }
            ErrorCategory.EMPTY_RESPONSE_ERROR -> {
                "The AI returned an empty response. Please try again."
            }
            ErrorCategory.UNKNOWN_ERROR -> {
                "An error occurred: ${message.take(100)}"
            }
        }
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

    private fun getFallbackProvider(currentProvider: AiProvider, category: ErrorCategory): AiProvider? {
        val allProviders = listOf(AiProvider.GEMINI, AiProvider.OPENAI, AiProvider.ANTHROPIC, AiProvider.OLLAMA)

        // Remove current provider and return first available
        return allProviders.firstOrNull { it != currentProvider && it != AiProvider.OLLAMA }
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
    fun getUserFriendlyMessage(error: Throwable): String {
        val analysis = analyzeError(error, AiProvider.GEMINI)
        return analysis.userMessage
    }
}