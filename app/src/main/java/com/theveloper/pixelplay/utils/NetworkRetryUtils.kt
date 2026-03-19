package com.theveloper.pixelplay.utils

import kotlinx.coroutines.delay
import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

object NetworkRetryUtils {
    suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int,
        initialDelayMs: Long,
        shouldRetry: (Throwable) -> Boolean = { it.isRetryableNetworkError() },
        onRetry: (attempt: Int, maxAttempts: Int, throwable: Throwable) -> Unit = { _, _, _ -> },
        block: suspend () -> T
    ): T {
        var delayMs = initialDelayMs
        repeat(maxAttempts) { attempt ->
            try {
                return block()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                val lastAttempt = attempt == maxAttempts - 1
                val retryable = shouldRetry(throwable)
                if (!retryable || lastAttempt) {
                    throw throwable
                }
                onRetry(attempt + 1, maxAttempts, throwable)
                delay(delayMs)
                delayMs *= 2
            }
        }
        error("Unreachable retry state for $operationName")
    }
}

fun Throwable.isRetryableNetworkError(): Boolean {
    return when (this) {
        is IOException -> true
        is HttpException -> code() == 429 || code() >= 500
        else -> false
    }
}
