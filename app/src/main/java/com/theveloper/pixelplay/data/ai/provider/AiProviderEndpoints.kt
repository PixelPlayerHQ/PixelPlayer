package com.theveloper.pixelplay.data.ai.provider

/**
 * Centralized configuration for all AI provider API endpoints and default models.
 * Every URL, model ID, and provider-specific header value lives here.
 * Nothing in any AiClient or factory should ever hardcode these strings directly.
 */
object AiProviderEndpoints {

    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val DEEPSEEK_BASE_URL = "https://api.deepseek.com/v1"
    const val GROQ_BASE_URL = "https://api.groq.com/openai/v1"
    const val MISTRAL_BASE_URL = "https://api.mistral.ai/v1"
    const val NVIDIA_BASE_URL = "https://integrate.api.nvidia.com/v1"
    const val KIMI_BASE_URL = "https://api.moonshot.cn/v1"
    const val GLM_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
    const val OPENAI_BASE_URL = "https://api.openai.com/v1"
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1"
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1"
    const val OLLAMA_BASE_URL = "https://ollama.ai/api/v1"

    const val GEMINI_DEFAULT_MODEL = "gemini-2.5-flash"
    const val DEEPSEEK_DEFAULT_MODEL = "deepseek-chat"
    const val GROQ_DEFAULT_MODEL = "llama-3.3-70b-versatile"
    const val MISTRAL_DEFAULT_MODEL = "mistral-small-latest"
    const val NVIDIA_DEFAULT_MODEL = "meta/llama-3.1-8b-instruct"
    const val KIMI_DEFAULT_MODEL = "moonshot-v1-8k"
    const val GLM_DEFAULT_MODEL = "glm-4"
    const val OPENAI_DEFAULT_MODEL = "gpt-4o-mini"
    const val OPENROUTER_DEFAULT_MODEL = "google/gemini-2.0-flash-lite-preview-02-05:free"
    const val ANTHROPIC_DEFAULT_MODEL = "claude-3-5-sonnet-20241022"
    const val OLLAMA_DEFAULT_MODEL = "llama3"

    const val ANTHROPIC_API_VERSION = "2023-06-01"
    const val OPENROUTER_SITE_URL = "https://github.com/theovilardo/PixelPlayer"
    const val OPENROUTER_SITE_NAME = "PixelPlayer"
}
