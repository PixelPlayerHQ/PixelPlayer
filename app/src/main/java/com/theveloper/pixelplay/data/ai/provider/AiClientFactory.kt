package com.theveloper.pixelplay.data.ai.provider

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating AI client instances based on provider type
 */
@Singleton
class AiClientFactory @Inject constructor() {
    
    /**
     * Create an AI client for the specified provider
     * @param provider The AI provider type
     * @param apiKey The API key for the provider
     * @param customEndpoint Optional custom endpoint override
     * @return AiClient instance
     */
    fun createClient(provider: AiProvider, apiKey: String, customEndpoint: String = ""): AiClient {
        if (apiKey.isBlank() && provider.requiresApiKey) {
            throw IllegalArgumentException("API Key cannot be blank for ${provider.displayName}")
        }

        return when (provider) {
            AiProvider.GEMINI -> GeminiAiClient(apiKey)
            AiProvider.DEEPSEEK -> DeepSeekAiClient(apiKey)
            AiProvider.GROQ -> GroqAiClient(apiKey)
            AiProvider.MISTRAL -> MistralAiClient(apiKey)
            AiProvider.NVIDIA -> GenericOpenAiClient(
                apiKey = apiKey,
                baseUrl = "https://integrate.api.nvidia.com/v1",
                defaultModelId = "meta/llama-3.1-8b-instruct",
                providerName = "NVIDIA NIM"
            )
            AiProvider.KIMI -> GenericOpenAiClient(
                apiKey = apiKey,
                baseUrl = "https://api.moonshot.cn/v1",
                defaultModelId = "moonshot-v1-8k",
                providerName = "Moonshot Kimi"
            )
            AiProvider.GLM -> GenericOpenAiClient(
                apiKey = apiKey,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                defaultModelId = "glm-4",
                providerName = "Zhipu GLM"
            )
            AiProvider.OPENAI -> GenericOpenAiClient(
                apiKey = apiKey,
                baseUrl = "https://api.openai.com/v1",
                defaultModelId = "gpt-4o-mini",
                providerName = "OpenAI"
            )
            AiProvider.OPENROUTER -> GenericOpenAiClient(
                apiKey = apiKey,
                baseUrl = "https://openrouter.ai/api/v1",
                defaultModelId = "google/gemini-2.0-flash-lite-preview-02-05:free",
                providerName = "OpenRouter"
            )
            AiProvider.ANTHROPIC -> AnthropicAiClient(apiKey)
            AiProvider.OLLAMA -> {
                // Use custom endpoint if provided, otherwise use provider default
                val endpoint = customEndpoint.ifBlank { provider.defaultEndpoint }
                OllamaAiClient(endpoint, apiKey)
            }
            AiProvider.LOCAL -> throw IllegalArgumentException("LOCAL provider does not use AiClient - use LocalModelManager for on-device inference")
        }
    }
}
