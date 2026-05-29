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
            AiProvider.DEEPSEEK -> GenericOpenAiClient(apiKey, AiProviderEndpoints.DEEPSEEK_BASE_URL, AiProviderEndpoints.DEEPSEEK_DEFAULT_MODEL, "DeepSeek")
            AiProvider.GROQ -> GenericOpenAiClient(apiKey, AiProviderEndpoints.GROQ_BASE_URL, AiProviderEndpoints.GROQ_DEFAULT_MODEL, "Groq")
            AiProvider.MISTRAL -> GenericOpenAiClient(apiKey, AiProviderEndpoints.MISTRAL_BASE_URL, AiProviderEndpoints.MISTRAL_DEFAULT_MODEL, "Mistral")
            AiProvider.NVIDIA -> GenericOpenAiClient(apiKey, AiProviderEndpoints.NVIDIA_BASE_URL, AiProviderEndpoints.NVIDIA_DEFAULT_MODEL, "NVIDIA NIM")
            AiProvider.KIMI -> GenericOpenAiClient(apiKey, AiProviderEndpoints.KIMI_BASE_URL, AiProviderEndpoints.KIMI_DEFAULT_MODEL, "Moonshot Kimi")
            AiProvider.GLM -> GenericOpenAiClient(apiKey, AiProviderEndpoints.GLM_BASE_URL, AiProviderEndpoints.GLM_DEFAULT_MODEL, "Zhipu GLM")
            AiProvider.OPENAI -> GenericOpenAiClient(apiKey, AiProviderEndpoints.OPENAI_BASE_URL, AiProviderEndpoints.OPENAI_DEFAULT_MODEL, "OpenAI")
            AiProvider.OPENROUTER -> GenericOpenAiClient(apiKey, AiProviderEndpoints.OPENROUTER_BASE_URL, AiProviderEndpoints.OPENROUTER_DEFAULT_MODEL, "OpenRouter")
            AiProvider.ANTHROPIC -> AnthropicAiClient(apiKey)
            AiProvider.OLLAMA -> GenericOpenAiClient(apiKey, customEndpoint.ifBlank { AiProviderEndpoints.OLLAMA_BASE_URL }, AiProviderEndpoints.OLLAMA_DEFAULT_MODEL, "Ollama")
            AiProvider.LOCAL -> throw IllegalArgumentException("LOCAL provider does not use AiClient - use LocalModelManager for on-device inference")
        }
    }
}
