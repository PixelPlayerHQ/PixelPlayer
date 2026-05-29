package com.theveloper.pixelplay.data.ai.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AiProviderException(
    val providerName: String, val statusCode: Int? = null, val requestedModel: String? = null,
    val providerCode: String? = null, val providerType: String? = null, val rawBody: String? = null,
    message: String, cause: Throwable? = null
) : Exception(message, cause) {

    private val text get() = listOfNotNull(message, rawBody, providerCode, providerType).joinToString(" ").lowercase()

    fun isModelUnavailable() = statusCode == 404 ||
        (text.contains("model") && listOf("not found", "does not exist", "unknown model", "unsupported model", "invalid model", "model_not_found").any { text.contains(it) })

    fun isBillingIssue() = statusCode == 402 || listOf("insufficient_quota", "quota", "credit", "credits", "billing", "payment required", "balance").any { text.contains(it) }

    fun isApiKeyIssue() = statusCode == 401 || listOf("api_key_invalid", "api key not valid", "invalid api key", "invalid key", "incorrect api key", "authentication failed", "unauthorized").any { text.contains(it) }

    fun shouldCooldown() = isBillingIssue() || isApiKeyIssue() ||
        (statusCode != null && statusCode >= 500) ||
        listOf("timeout", "timed out", "unable to resolve host", "failed to connect", "connection reset", "network").any { text.contains(it) }
}

object AiProviderSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildProviderChain(primary: AiProvider) = buildList {
        add(primary)
        addAll(AiProvider.entries.filter { it != primary })
    }

    fun selectRecoveryModel(currentModel: String, defaultModel: String, availableModels: List<String>): String? {
        val avail = availableModels.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (avail.isNotEmpty()) {
            avail.firstOrNull { it == defaultModel }?.takeIf { it != currentModel }?.let { return it }
            avail.firstOrNull { it != currentModel }?.let { return it }
        }
        return defaultModel.takeIf { it.isNotBlank() && it != currentModel }
    }

    fun createException(providerName: String, statusCode: Int?, transportMessage: String?, responseBody: String?, requestedModel: String?, cause: Throwable? = null): AiProviderException {
        val parsed = parseError(responseBody)
        val cleanMessage = parsed.message?.takeIf { it.isNotBlank() } ?: transportMessage?.takeIf { it.isNotBlank() } ?: "Unknown provider error"
        val prefix = "${providerName} API error${if (statusCode != null) " ($statusCode)" else ""}"
        val finalMessage = if (requestedModel.isNullOrBlank()) "$prefix: $cleanMessage" else "$prefix with model '$requestedModel': $cleanMessage"
        return AiProviderException(providerName, statusCode, requestedModel, parsed.code, parsed.type, responseBody, finalMessage, cause)
    }

    fun wrapThrowable(providerName: String, throwable: Throwable, requestedModel: String? = null): AiProviderException = when (throwable) {
        is AiProviderException -> throwable
        else -> {
            val rawMessage = throwable.message.orEmpty()
            val inferredStatus = Regex("""\b([1-5]\d{2})\b""").find(rawMessage)?.groupValues?.getOrNull(1)?.toIntOrNull()
            createException(providerName, inferredStatus, rawMessage.ifBlank { throwable::class.simpleName ?: "Unknown error" }, null, requestedModel, throwable)
        }
    }

    private fun parseError(responseBody: String?): ParsedProviderError {
        if (responseBody.isNullOrBlank()) return ParsedProviderError()
        return runCatching {
            val error = json.parseToJsonElement(responseBody).jsonObject["error"]?.jsonObject ?: json.parseToJsonElement(responseBody).jsonObject
            ParsedProviderError(error["message"]?.jsonPrimitive?.contentOrNull, error["code"]?.jsonPrimitive?.contentOrNull, error["type"]?.jsonPrimitive?.contentOrNull)
        }.getOrDefault(ParsedProviderError(message = responseBody))
    }

    private data class ParsedProviderError(val message: String? = null, val code: String? = null, val type: String? = null)
}
