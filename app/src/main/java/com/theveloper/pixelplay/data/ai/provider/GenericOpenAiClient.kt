package com.theveloper.pixelplay.data.ai.provider

import okhttp3.Request

/**
 * A generic AI client for OpenAI-compatible APIs (NVIDIA, Kimi, GLM, etc.)
 */
class GenericOpenAiClient(
    apiKey: String,
    override val baseUrl: String,
    private val defaultModelId: String,
    override val providerName: String = "OpenAI"
) : OpenAiCompatibleClient(apiKey) {

    override val defaultModel: String get() = defaultModelId
    override val defaultModels: List<String> get() = listOf(defaultModelId)

    override fun decorateRequest(builder: Request.Builder): Request.Builder {
        if (providerName.equals("OpenRouter", ignoreCase = true)) {
            builder.addHeader("HTTP-Referer", "https://github.com/theovilardo/PixelPlayer")
            builder.addHeader("X-Title", "PixelPlayer")
        }
        return builder
    }

    override fun filterModels(models: List<String>): List<String> =
        models.filter {
            !it.contains("whisper") && !it.contains("embed") && !it.contains("tts")
        }
}
