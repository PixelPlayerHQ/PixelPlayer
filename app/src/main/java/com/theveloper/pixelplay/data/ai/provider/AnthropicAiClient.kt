package com.theveloper.pixelplay.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class AnthropicAiClient(private val apiKey: String) : AiClient {

    private companion object {
        const val DEFAULT_MODEL = "claude-3-5-sonnet-20241022"
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val API_VERSION = "2023-06-01"
    }

    @Serializable private data class ChatMessage(val role: String, val content: String)
    @Serializable private data class ChatRequest(val model: String, val max_tokens: Int = 4096, val system: String? = null, val messages: List<ChatMessage>, val temperature: Double = 0.7)
    @Serializable private data class ContentItem(val type: String, val text: String)
    @Serializable private data class ChatResponse(val content: List<ContentItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun request() = Request.Builder().url("$BASE_URL/messages")
        .addHeader("x-api-key", apiKey).addHeader("anthropic-version", API_VERSION).addHeader("content-type", "application/json")

    override suspend fun generateContent(model: String, systemPrompt: String, prompt: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val m = model.ifBlank { DEFAULT_MODEL }
            val req = ChatRequest(m, system = systemPrompt.takeIf { it.isNotBlank() }, messages = listOf(ChatMessage("user", prompt)), temperature = temperature.toDouble())
            val body = json.encodeToString(ChatRequest.serializer(), req).toRequestBody("application/json".toMediaType())
            try {
                client.newCall(request().post(body).build()).execute().use { response ->
                    val rb = response.body?.string()
                    if (!response.isSuccessful) throw AiProviderSupport.createException("Anthropic", response.code, response.message, rb, m)
                    val parsed = json.decodeFromString<ChatResponse>(rb ?: throw AiProviderSupport.createException("Anthropic", response.code, "Empty response body", null, m))
                    parsed.content.firstOrNull { it.type == "text" }?.text
                        ?: throw AiProviderSupport.createException("Anthropic", response.code, "Response had no content", rb, m)
                }
            } catch (e: Exception) { throw AiProviderSupport.wrapThrowable("Anthropic", e, m) }
        }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int =
        (systemPrompt.length + prompt.length) / 4

    override suspend fun getAvailableModels(apiKey: String): List<String> = defaultModels()

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(DEFAULT_MODEL, max_tokens = 1, messages = listOf(ChatMessage("user", "Ping"))))
                .toRequestBody("application/json".toMediaType())
            client.newCall(request().post(body).build()).execute().isSuccessful
        } catch (_: Exception) { false }
    }

    override fun getDefaultModel(): String = DEFAULT_MODEL

    private fun defaultModels() = listOf(
        "claude-3-5-sonnet-20241022", "claude-3-5-haiku-20241022", "claude-3-opus-20240229",
        "claude-3-sonnet-20240229", "claude-3-haiku-20240307"
    )
}
