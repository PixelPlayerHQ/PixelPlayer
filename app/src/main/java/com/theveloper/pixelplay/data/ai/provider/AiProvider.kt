package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val supportsCustomEndpoint: Boolean = false,
    val defaultEndpoint: String = ""
) {
    // Cloud Providers (require internet)
    GEMINI("Google Gemini", requiresApiKey = true),
    DEEPSEEK("DeepSeek", requiresApiKey = true),
    GROQ("Groq", requiresApiKey = true),
    MISTRAL("Mistral", requiresApiKey = true),
    NVIDIA("NVIDIA NIM", requiresApiKey = true),
    KIMI("Kimi (Moonshot)", requiresApiKey = true),
    GLM("Zhipu GLM", requiresApiKey = true),
    OPENAI("OpenAI", requiresApiKey = true),
    OPENROUTER("OpenRouter", requiresApiKey = true, supportsCustomEndpoint = true, defaultEndpoint = "https://openrouter.ai/api/v1"),
    ANTHROPIC("Anthropic Claude", requiresApiKey = true),

    // Ollama: Remote server API (requires API key, used for model downloads only - not Android-native localhost)
    OLLAMA("Ollama Server", requiresApiKey = true, supportsCustomEndpoint = true, defaultEndpoint = "https://ollama.ai/api"),

    // Local Device Models (offline, no API needed)
    LOCAL("Local Model (Device)", requiresApiKey = false);

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: GEMINI
        }

        /** Get all cloud-based providers (require internet) */
        fun cloudProviders(): List<AiProvider> = entries.filter { it != LOCAL }

        /** Get local/offline providers (no internet needed) */
        fun localProviders(): List<AiProvider> = listOf(LOCAL)

        /** Get server-based providers - none currently (Ollama is cloud-only for model downloads) */
        fun serverProviders(): List<AiProvider> = emptyList()

        /** Get all providers that require API key */
        fun providersRequiringApiKey(): List<AiProvider> = entries.filter { it.requiresApiKey }

        /** Get providers that support custom endpoints */
        fun providersWithCustomEndpoint(): List<AiProvider> = entries.filter { it.supportsCustomEndpoint }
    }
}