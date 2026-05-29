package com.theveloper.pixelplay.data.ai.provider

enum class AiProvider(
    val displayName: String,
    val requiresApiKey: Boolean,
    val supportsCustomEndpoint: Boolean = false,
    val defaultEndpoint: String = "",
    val models: List<String> = emptyList()
) {
    GEMINI(
        "Google Gemini", requiresApiKey = true,
        models = listOf(
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-2.0-flash-lite",
            "gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b",
            "gemma-3-27b-it", "gemma-3-12b-it"
        )
    ),
    DEEPSEEK(
        "DeepSeek", requiresApiKey = true,
        models = listOf("deepseek-chat", "deepseek-reasoner", "deepseek-coder", "deepseek-v3")
    ),
    GROQ(
        "Groq", requiresApiKey = true,
        models = listOf(
            "llama-3.3-70b-versatile", "llama-3.2-90b-vision-preview", "llama-3.1-8b-instant",
            "llama-3.1-70b-versatile", "mixtral-8x7b-32768", "gemma2-9b-it",
            "llama-guard-3-8b", "deepseek-r1-distill-llama-70b"
        )
    ),
    MISTRAL(
        "Mistral", requiresApiKey = true,
        models = listOf(
            "mistral-large-latest", "mistral-small-latest", "mistral-medium-latest",
            "open-mistral-nemo", "open-codestral-mamba", "codestral-latest",
            "ministral-8b-latest", "ministral-3b-latest", "pixtral-12b-2409"
        )
    ),
    NVIDIA(
        "NVIDIA NIM", requiresApiKey = true,
        models = listOf(
            "meta/llama-3.1-8b-instruct", "meta/llama-3.1-70b-instruct", "meta/llama-3.1-405b-instruct",
            "mistralai/mistral-7b-instruct-v0.3", "mistralai/mixtral-8x22b-instruct-v0.1",
            "google/gemma-2-2b-it", "google/gemma-2-9b-it", "google/gemma-2-27b-it",
            "nvidia/llama-3.1-nemotron-70b-instruct-hf", "microsoft/phi-3-medium-14b-instruct"
        )
    ),
    KIMI(
        "Kimi (Moonshot)", requiresApiKey = true,
        models = listOf(
            "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k",
            "moonshot-v1-auto"
        )
    ),
    GLM(
        "Zhipu GLM", requiresApiKey = true,
        models = listOf(
            "glm-4", "glm-4v", "glm-4-plus", "glm-4-air",
            "glm-4-airx", "glm-4-long", "glm-4-flash"
        )
    ),
    OPENAI(
        "OpenAI", requiresApiKey = true,
        models = listOf(
            "gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-4", "gpt-3.5-turbo",
            "o1", "o1-mini", "o1-preview", "o3-mini",
            "gpt-4.1", "gpt-4.1-mini", "gpt-4.1-nano",
            "gpt-4.5-preview"
        )
    ),
    OPENROUTER(
        "OpenRouter", requiresApiKey = true, supportsCustomEndpoint = true,
        defaultEndpoint = "https://openrouter.ai/api/v1",
        models = listOf(
            "google/gemini-2.5-flash:free", "google/gemini-2.0-flash-lite-preview-02-05:free",
            "anthropic/claude-3.5-sonnet", "anthropic/claude-3-haiku",
            "openai/gpt-4o-mini", "openai/o3-mini",
            "mistralai/mistral-small", "mistralai/mistral-large",
            "meta-llama/llama-3.3-70b-instruct", "meta-llama/llama-3.1-8b-instruct:free",
            "deepseek/deepseek-chat", "qwen/qwen-2.5-72b-instruct",
            "cohere/command-r-plus", "google/gemma-2-9b-it:free",
            "microsoft/phi-3-medium-14b-instruct:free"
        )
    ),
    ANTHROPIC(
        "Anthropic Claude", requiresApiKey = true,
        models = listOf(
            "claude-sonnet-4-20250514", "claude-4-opus-20250506",
            "claude-3-5-sonnet-20241022", "claude-3-opus-20240229",
            "claude-3-haiku-20240307", "claude-3-5-haiku-20241022"
        )
    ),
    OLLAMA(
        "Ollama Server", requiresApiKey = true, supportsCustomEndpoint = true,
        defaultEndpoint = "https://ollama.ai/api",
        models = listOf(
            "llama3", "llama3.1", "llama3.2", "llama3.3",
            "mistral", "mixtral", "gemma2", "phi3", "phi4",
            "tinyllama", "llama2", "codellama", "neural-chat",
            "starling-lm", "qwen2.5", "deepseek-coder", "command-r",
            "dolphin-mixtral", "yi", "falcon2", "starcoder2"
        )
    ),
    LOCAL("Local Model (Device)", requiresApiKey = false);

    companion object {
        fun fromString(value: String): AiProvider = entries.find { it.name == value } ?: GEMINI
    }
}
