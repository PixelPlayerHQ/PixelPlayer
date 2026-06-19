package com.theveloper.pixelplay.data.ai.provider

class GroqAiClient(apiKey: String) : OpenAiCompatibleClient(apiKey) {

    override val providerName = "Groq"
    override val baseUrl = "https://api.groq.com/openai/v1"
    override val defaultModel = "llama-3.1-8b-instant"
    override val defaultModels = listOf(
        "llama-3.1-8b-instant",
        "llama-3.3-70b-versatile",
        "mixtral-8x7b-32768",
        "gemma2-9b-it"
    )

    override fun filterModels(models: List<String>): List<String> =
        models.filter { !it.contains("whisper") }
}
