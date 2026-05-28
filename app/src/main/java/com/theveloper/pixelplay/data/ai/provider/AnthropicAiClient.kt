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

/**
 * Anthropic Claude AI provider implementation
 */
class AnthropicAiClient(private val apiKey: String) : AiClient {

    companion object {
        private const val DEFAULT_MODEL = "claude-3-5-sonnet-20241022"
        private const val BASE_URL = "https://api.anthropic.com/v1"
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val max_tokens: Int = 4096,
        val system: String? = null,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )

    @Serializable
    private data class ContentItem(val type: String, val text: String)

    @Serializable
    private data class ChatResponse(val content: List<ContentItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val resolvedModel = model.ifBlank { DEFAULT_MODEL }
            val messagesList = listOf(ChatMessage(role = "user", content = prompt))

            val requestBody = ChatRequest(
                model = resolvedModel,
                system = systemPrompt.takeIf { it.isNotBlank() },
                messages = messagesList,
                temperature = temperature.toDouble()
            )

            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$BASE_URL/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", ANTHROPIC_VERSION)
                .addHeader("content-type", "application/json")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        throw AiProviderSupport.createException(
                            providerName = "Anthropic",
                            statusCode = response.code,
                            transportMessage = response.message,
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                    }

                    val nonEmptyBody = responseBody
                        ?: throw AiProviderSupport.createException(
                            providerName = "Anthropic",
                            statusCode = response.code,
                            transportMessage = "Empty response body",
                            responseBody = null,
                            requestedModel = resolvedModel
                        )

                    val chatResponse = json.decodeFromString<ChatResponse>(nonEmptyBody)
                    chatResponse.content.firstOrNull { it.type == "text" }?.text
                        ?: throw AiProviderSupport.createException(
                            providerName = "Anthropic",
                            statusCode = response.code,
                            transportMessage = "Response had no content",
                            responseBody = nonEmptyBody,
                            requestedModel = resolvedModel
                        )
                }
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable("Anthropic", e, resolvedModel)
            }
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return (systemPrompt.length + prompt.length) / 4
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        return getDefaultModels()
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val messagesList = listOf(ChatMessage(role = "user", content = "Ping"))
                val requestBody = ChatRequest(
                    model = DEFAULT_MODEL,
                    max_tokens = 1,
                    messages = messagesList
                )
                val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
                val body = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$BASE_URL/messages")
                    .addHeader("x-api-key", apiKey)
                    .addHeader("anthropic-version", ANTHROPIC_VERSION)
                    .addHeader("content-type", "application/json")
                    .post(body)
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun getDefaultModel(): String = DEFAULT_MODEL

    private fun getDefaultModels(): List<String> {
        return listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229",
            "claude-3-sonnet-20240229",
            "claude-3-haiku-20240307"
        )
    }
}
