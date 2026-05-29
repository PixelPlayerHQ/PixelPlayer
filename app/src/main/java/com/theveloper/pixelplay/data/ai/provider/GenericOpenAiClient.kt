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

class GenericOpenAiClient(
    private val apiKey: String,
    private val baseUrl: String,
    private val defaultModelId: String,
    private val providerName: String = "OpenAI"
) : AiClient {

    @Serializable private data class ChatMessage(val role: String, val content: String)
    @Serializable private data class ChatRequest(val model: String, val messages: List<ChatMessage>, val temperature: Double = 0.7)
    @Serializable private data class ChatChoice(val message: ChatMessage)
    @Serializable private data class ChatResponse(val choices: List<ChatChoice>)
    @Serializable private data class ModelItem(val id: String)
    @Serializable private data class ModelsResponse(val data: List<ModelItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun authenticatedRequest(path: String, key: String = apiKey): Request.Builder =
        Request.Builder().url("${baseUrl.trimEnd('/')}/$path").apply {
            if (key.isNotBlank()) addHeader("Authorization", "Bearer $key")
            if (providerName.equals("OpenRouter", ignoreCase = true)) {
                addHeader("HTTP-Referer", "https://github.com/theovilardo/PixelPlayer")
                addHeader("X-Title", "PixelPlayer")
            }
        }

    override suspend fun generateContent(model: String, systemPrompt: String, prompt: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val m = model.ifBlank { defaultModelId }
            val msgs = buildList {
                if (systemPrompt.isNotBlank()) add(ChatMessage("system", systemPrompt))
                add(ChatMessage("user", prompt))
            }
            val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(m, msgs, temperature.toDouble()))
                .toRequestBody("application/json".toMediaType())
            try {
                client.newCall(authenticatedRequest("chat/completions").post(body).build()).execute().use { response ->
                    val rb = response.body.string()
                    if (!response.isSuccessful) throw AiProviderSupport.createException(providerName, response.code, response.message, rb, m)
                    json.decodeFromString<ChatResponse>(rb).choices.firstOrNull()?.message?.content
                        ?: throw AiProviderSupport.createException(providerName, response.code, "Response had no content", rb, m)
                }
            } catch (e: Exception) { throw AiProviderSupport.wrapThrowable(providerName, e, m) }
        }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int =
        (systemPrompt.length + prompt.length) / 4

    override suspend fun getAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.newCall(authenticatedRequest("models", apiKey).get().build()).execute()
            if (!response.isSuccessful) return@withContext listOf(defaultModelId)
            json.decodeFromString<ModelsResponse>(response.body.string()).data.map { it.id }
                .filter { !it.contains("whisper") && !it.contains("embed") && !it.contains("tts") }
        } catch (_: Exception) { listOf(defaultModelId) }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(ChatRequest.serializer(), ChatRequest(defaultModelId, listOf(ChatMessage("user", "ping")), temperature = 0.0))
            val response = client.newCall(authenticatedRequest("chat/completions", apiKey).post(body.toRequestBody("application/json".toMediaType())).build()).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    override fun getDefaultModel(): String = defaultModelId
}
