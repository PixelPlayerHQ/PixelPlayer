package com.theveloper.pixelplay.data.ai.provider

class DeepSeekAiClient(apiKey: String) : OpenAiCompatibleClient(apiKey) {

    override val providerName = "DeepSeek"
    override val baseUrl = "https://api.deepseek.com"
    override val providerDefaultModel = "deepseek-chat"
    override val providerDefaultModels = listOf("deepseek-chat", "deepseek-reasoner")
}
