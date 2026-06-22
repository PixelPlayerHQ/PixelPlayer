package com.theveloper.pixelplay.data.ai.provider

object UnifiedModelFilter {
    private val UNSUITABLE_PATTERNS = listOf(
        "embedding", "embed", "aqa", "imagen", "image-generation",
        "tts", "text-to-speech", "speech", "audio", "whisper",
        "veo", "vision-only", "learnlm-embedding", "moderation",
        "dall-e", "stable-diffusion", "sdxl", "kandinsky",
        "upscale", "background", "remove-background",
        "segment", "detect", "classify", "object-detection"
    )

    fun isModelUsableForChat(modelName: String): Boolean {
        val lower = modelName.lowercase()
        return UNSUITABLE_PATTERNS.none { lower.contains(it) }
    }

    fun filterChatModels(models: List<String>): List<String> {
        return models.filter { isModelUsableForChat(it) }
    }

    fun filterChatModelsWithDefaults(
        apiModels: List<String>,
        defaultModels: List<String>
    ): List<String> {
        return (apiModels.filter { isModelUsableForChat(it) } + defaultModels)
            .distinct()
            .sorted()
    }
}
