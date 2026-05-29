package com.theveloper.pixelplay.data.ai.provider

/**
 * Enum representing available AI providers
 */
enum class AiProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val supportsCustomEndpoint: Boolean = false,
    val defaultEndpoint: String = "",
    val models: List<String> = emptyList()
) {
    GEMINI(
        "Google Gemini", requiresApiKey = true,
        models = listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-pro", "gemini-1.5-flash")
    ),
    DEEPSEEK(
        "DeepSeek", requiresApiKey = true,
        models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder")
    ),
    GROQ(
        "Groq", requiresApiKey = true,
        models = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768", "gemma2-9b-it", "llama-guard-3-8b")
    ),
    MISTRAL(
        "Mistral", requiresApiKey = true,
        models = listOf("mistral-large-latest", "mistral-small-latest", "open-mistral-nemo", "codestral-latest")
    ),
    NVIDIA(
        "NVIDIA NIM", requiresApiKey = true,
        models = listOf("meta/llama-3.1-8b-instruct", "meta/llama-3.1-70b-instruct", "mistralai/mistral-7b-instruct-v0.3")
    ),
    KIMI(
        "Kimi (Moonshot)", requiresApiKey = true,
        models = listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
    ),
    GLM(
        "Zhipu GLM", requiresApiKey = true,
        models = listOf("glm-4", "glm-4v", "glm-4-plus", "glm-4-air")
    ),
    OPENAI(
        "OpenAI", requiresApiKey = true,
        models = listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo", "o1-mini", "o1-preview")
    ),
    OPENROUTER(
        "OpenRouter", requiresApiKey = true, supportsCustomEndpoint = true,
        defaultEndpoint = "https://openrouter.ai/api/v1",
        models = listOf("google/gemini-2.0-flash-lite-preview-02-05:free", "google/gemini-2.5-flash:free", "anthropic/claude-3.5-sonnet", "openai/gpt-4o-mini", "mistralai/mistral-small", "meta-llama/llama-3.3-70b-instruct")
    ),
    ANTHROPIC(
        "Anthropic Claude", requiresApiKey = true,
        models = listOf("claude-sonnet-4-20250514", "claude-3-5-sonnet-20241022", "claude-3-opus-20240229", "claude-3-haiku-20240307", "claude-3-5-haiku-20241022")
    ),
    OLLAMA(
        "Ollama Server", requiresApiKey = true, supportsCustomEndpoint = true,
        defaultEndpoint = "https://ollama.ai/api",
        models = listOf("llama3", "llama3.1", "mistral", "phi3", "tinyllama", "llama2", "codellama", "neural-chat", "starling-lm")
    ),
    LOCAL("Local Model (Device)", requiresApiKey = false);

    companion object {
        fun fromString(value: String): AiProvider {
            return entries.find { it.name == value } ?: GEMINI
        }
    }
}