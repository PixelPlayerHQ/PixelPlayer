package com.theveloper.pixelplay.data.ai.provider

class MistralAiClient(apiKey: String) : OpenAiCompatibleClient(apiKey) {

    override val providerName = "Mistral"
    override val baseUrl = "https://api.mistral.ai/v1"
    override val defaultModel = "mistral-large-latest"
    override val defaultModels = listOf(
        "mistral-large-latest",
        "mistral-small-latest",
        "open-mixtral-8x22b",
        "open-mixtral-8x7b"
    )
}
