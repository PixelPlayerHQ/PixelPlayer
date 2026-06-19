package com.theveloper.pixelplay.data.ai.provider

class DeepSeekAiClient(apiKey: String) : OpenAiCompatibleClient(apiKey) {

    override val providerName = "DeepSeek"
    override val baseUrl = "https://api.deepseek.com"
    override val defaultModel = "deepseek-chat"
    override val defaultModels = listOf("deepseek-chat", "deepseek-reasoner")
}
