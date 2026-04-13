package com.theveloper.pixelplay.data.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class AiProviderException(
    val providerName: String,
    val statusCode: Int? = null,
    val requestedModel: String? = null,
    val providerCode: String? = null,
    val providerType: String? = null,
    val rawBody: String? = null,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {

    fun isModelUnavailable(): Boolean {
        val text = buildSearchText()
        val mentionsMissingModel = text.contains("model") &&
            (
                text.contains("not found") ||
                    text.contains("does not exist") ||
                    text.contains("unknown model") ||
                    text.contains("unsupported model") ||
                    text.contains("invalid model") ||
                    text.contains("model_not_found")
                )

        return statusCode == 404 || mentionsMissingModel
    }

    fun isBillingIssue(): Boolean {
        val text = buildSearchText()
        return statusCode == 402 ||
            text.contains("insufficient_quota") ||
            text.contains("quota") ||
            text.contains("credit") ||
            text.contains("credits") ||
            text.contains("billing") ||
            text.contains("payment required") ||
            text.contains("balance")
    }

    fun isApiKeyIssue(): Boolean {
        val text = buildSearchText()
        return statusCode == 401 ||
            statusCode == 403 ||
            text.contains("api key") ||
            text.contains("invalid key") ||
            text.contains("unauthorized") ||
            text.contains("forbidden")
    }

    fun shouldCooldown(): Boolean {
        val text = buildSearchText()
        return isBillingIssue() ||
            isApiKeyIssue() ||
            (statusCode != null && statusCode >= 500) ||
            text.contains("timeout") ||
            text.contains("timed out") ||
            text.contains("unable to resolve host") ||
            text.contains("failed to connect") ||
            text.contains("connection reset") ||
            text.contains("network")
    }

    private fun buildSearchText(): String {
        return listOfNotNull(message, rawBody, providerCode, providerType)
            .joinToString(" ")
            .lowercase()
    }
}

internal object AiProviderSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildProviderChain(primary: AiProvider): List<AiProvider> {
        val preferredFallbacks = listOf(
            AiProvider.GROQ,
            AiProvider.GEMINI,
            AiProvider.DEEPSEEK,
            AiProvider.MISTRAL,
            AiProvider.OPENAI,
            AiProvider.NVIDIA,
            AiProvider.KIMI,
            AiProvider.GLM
        )

        return buildList {
            add(primary)
            addAll(preferredFallbacks.filter { it != primary })
            addAll(AiProvider.entries.filter { it != primary && it !in preferredFallbacks })
        }.distinct()
    }

    fun selectRecoveryModel(
        currentModel: String,
        defaultModel: String,
        availableModels: List<String>
    ): String? {
        val normalizedCurrent = currentModel.trim()
        val normalizedDefault = defaultModel.trim()
        val normalizedAvailable = availableModels
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalizedAvailable.isNotEmpty()) {
            val preferredDefault = normalizedAvailable.firstOrNull { it == normalizedDefault }
            if (preferredDefault != null && preferredDefault != normalizedCurrent) {
                return preferredDefault
            }

            val firstAlternative = normalizedAvailable.firstOrNull { it != normalizedCurrent }
            if (firstAlternative != null) {
                return firstAlternative
            }
        }

        return normalizedDefault.takeIf { it.isNotBlank() && it != normalizedCurrent }
    }

    fun createException(
        providerName: String,
        statusCode: Int?,
        transportMessage: String?,
        responseBody: String?,
        requestedModel: String?,
        cause: Throwable? = null
    ): AiProviderException {
        val parsed = parseError(responseBody)
        val cleanMessage = parsed.message
            ?.takeIf { it.isNotBlank() }
            ?: transportMessage?.takeIf { it.isNotBlank() }
            ?: "Unknown provider error"
        val prefix = buildString {
            append(providerName)
            append(" API error")
            if (statusCode != null) {
                append(" (")
                append(statusCode)
                append(")")
            }
        }
        val finalMessage = if (requestedModel.isNullOrBlank()) {
            "$prefix: $cleanMessage"
        } else {
            "$prefix with model '$requestedModel': $cleanMessage"
        }

        return AiProviderException(
            providerName = providerName,
            statusCode = statusCode,
            requestedModel = requestedModel,
            providerCode = parsed.code,
            providerType = parsed.type,
            rawBody = responseBody,
            message = finalMessage,
            cause = cause
        )
    }

    fun wrapThrowable(
        providerName: String,
        throwable: Throwable,
        requestedModel: String? = null
    ): AiProviderException {
        return when (throwable) {
            is AiProviderException -> throwable
            else -> {
                val rawMessage = throwable.message.orEmpty()
                val inferredStatus = Regex("""\b([1-5]\d{2})\b""")
                    .find(rawMessage)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toIntOrNull()

                createException(
                    providerName = providerName,
                    statusCode = inferredStatus,
                    transportMessage = rawMessage.ifBlank { throwable::class.simpleName ?: "Unknown error" },
                    responseBody = null,
                    requestedModel = requestedModel,
                    cause = throwable
                )
            }
        }
    }

    private fun parseError(responseBody: String?): ParsedProviderError {
        if (responseBody.isNullOrBlank()) return ParsedProviderError()

        return runCatching {
            val root = json.parseToJsonElement(responseBody).jsonObject
            val errorObject = root["error"]?.jsonObject ?: root

            ParsedProviderError(
                message = errorObject["message"]?.jsonPrimitive?.contentOrNull,
                code = errorObject["code"]?.jsonPrimitive?.contentOrNull,
                type = errorObject["type"]?.jsonPrimitive?.contentOrNull
            )
        }.getOrDefault(ParsedProviderError(message = responseBody))
    }

    private data class ParsedProviderError(
        val message: String? = null,
        val code: String? = null,
        val type: String? = null
    )
}
