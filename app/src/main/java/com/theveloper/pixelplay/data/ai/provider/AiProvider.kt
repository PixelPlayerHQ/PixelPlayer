package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(val displayName: String, val requiresApiKey: Boolean, val hasConfigurableUrl: Boolean = false) {
    GEMINI("Google Gemini", requiresApiKey = true),
    DEEPSEEK("DeepSeek", requiresApiKey = true),
    GROQ("Groq", requiresApiKey = true),
    MISTRAL("Mistral", requiresApiKey = true),
    NVIDIA("NVIDIA NIM", requiresApiKey = true),
    KIMI("Kimi (Moonshot)", requiresApiKey = true),
    GLM("Zhipu GLM", requiresApiKey = true),
    OPENAI("OpenAI", requiresApiKey = true),
    OPENROUTER("OpenRouter", requiresApiKey = true),
    OLLAMA("Ollama", requiresApiKey = true),
    CUSTOM("Custom Provider", requiresApiKey = true, hasConfigurableUrl = true);
    
    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: GEMINI
        }
    }
}
