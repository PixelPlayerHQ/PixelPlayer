package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val hasConfigurableUrl: Boolean = false,
    val description: String = ""
) {
    GEMINI("Google Gemini", requiresApiKey = true, description = "Google's flagship AI models (Gemini 1.5/2.0)"),
    DEEPSEEK("DeepSeek", requiresApiKey = true, description = "Open-source reasoning models via DeepSeek API"),
    GROQ("Groq", requiresApiKey = true, description = "Ultra-fast inference with Groq LPU hardware"),
    MISTRAL("Mistral", requiresApiKey = true, description = "Mistral AI's efficient and powerful models"),
    NVIDIA("NVIDIA NIM", requiresApiKey = true, description = "NVIDIA's optimized inference microservices"),
    KIMI("Kimi (Moonshot)", requiresApiKey = true, description = "Moonshot AI's long-context Kimi models"),
    GLM("Zhipu GLM", requiresApiKey = true, description = "Zhipu AI's bilingual GLM series"),
    OPENAI("OpenAI", requiresApiKey = true, description = "GPT-4o, GPT-4, and other OpenAI models"),
    OPENROUTER("OpenRouter", requiresApiKey = true, description = "Unified access to 200+ models via OpenRouter"),
    OLLAMA("Ollama", requiresApiKey = true, description = "Local models via Ollama (requires running server)"),
    CUSTOM("Custom Provider", requiresApiKey = true, hasConfigurableUrl = true, description = "Any OpenAI-compatible API endpoint");
    
    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: GEMINI
        }
    }
}
