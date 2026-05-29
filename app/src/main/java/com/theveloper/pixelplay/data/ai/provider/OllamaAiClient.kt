package com.theveloper.pixelplay.data.ai.provider

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Ollama AI provider implementation.
 * Supports remote Ollama servers via API key authentication.
 * Note: This provider is for remote servers only - not for Android localhost connections.
 */
class OllamaAiClient(
    private var endpoint: String = DEFAULT_ENDPOINT,
    private var apiKey: String = ""
) : AiClient {

    companion object {
        private const val DEFAULT_MODEL = "llama3"
        private const val DEFAULT_ENDPOINT = "https://ollama.ai/api/v1"
    }

    @Serializable
    private data class ChatMessage(val role: String, val content: String)

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )

    @Serializable
    private data class ChatChoice(val message: ChatMessage)

    @Serializable
    private data class ChatResponse(val choices: List<ChatChoice>)

    @Serializable
    private data class ModelItem(val id: String)

    @Serializable
    private data class ModelsResponse(val data: List<ModelItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Configure the client with a custom endpoint and API key.
     * Used when user specifies a custom Ollama server URL.
     */
    fun configure(customEndpoint: String, customApiKey: String) {
        endpoint = customEndpoint.removeSuffix("/")
        apiKey = customApiKey
    }

    private fun buildRequest(path: String, body: okhttp3.RequestBody): Request {
        val url = "$endpoint$path"
        val builder = Request.Builder()
            .url(url)
            .post(body)

        // Add API key authentication if provided
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        return builder.build()
    }

    private fun buildGetRequest(path: String): Request {
        val url = "$endpoint$path"
        val builder = Request.Builder()
            .url(url)
            .get()

        // Add API key authentication if provided
        if (apiKey.isNotBlank()) {
            builder.addHeader("Authorization", "Bearer $apiKey")
        }

        return builder.build()
    }

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val resolvedModel = model.ifBlank { DEFAULT_MODEL }
            val messagesList = mutableListOf<ChatMessage>()
            if (systemPrompt.isNotBlank()) {
                messagesList.add(ChatMessage(role = "system", content = systemPrompt))
            }
            messagesList.add(ChatMessage(role = "user", content = prompt))

            val requestBody = ChatRequest(
                model = resolvedModel,
                messages = messagesList,
                temperature = temperature.toDouble()
            )

            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())

            val request = buildRequest("/chat/completions", body)

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        throw AiProviderSupport.createException(
                            providerName = "Ollama",
                            statusCode = response.code,
                            transportMessage = response.message,
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                    }

                    val nonEmptyBody = responseBody
                        ?: throw AiProviderSupport.createException(
                            providerName = "Ollama",
                            statusCode = response.code,
                            transportMessage = "Empty response body",
                            responseBody = null,
                            requestedModel = resolvedModel
                        )

                    val chatResponse = json.decodeFromString<ChatResponse>(nonEmptyBody)
                    chatResponse.choices.firstOrNull()?.message?.content
                        ?: throw AiProviderSupport.createException(
                            providerName = "Ollama",
                            statusCode = response.code,
                            transportMessage = "Response had no content",
                            responseBody = nonEmptyBody,
                            requestedModel = resolvedModel
                        )
                }
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable("Ollama", e, resolvedModel)
            }
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return (systemPrompt.length + prompt.length) / 4
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        // Use provided API key for this request if different from stored
        val effectiveApiKey = apiKey.ifBlank { this.apiKey }

        return withContext(Dispatchers.IO) {
            try {
                val requestBuilder = Request.Builder()
                    .url("$endpoint/models")
                    .get()

                if (effectiveApiKey.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $effectiveApiKey")
                }

                val request = requestBuilder.build()
                val response = client.newCall(request).execute()

                if (!response.isSuccessful) {
                    Timber.w("Ollama getAvailableModels failed: ${response.code}")
                    return@withContext listOf(DEFAULT_MODEL)
                }

                val responseBody = response.body?.string() ?: return@withContext listOf(DEFAULT_MODEL)
                val modelsResponse = json.decodeFromString<ModelsResponse>(responseBody)
                modelsResponse.data.map { it.id }
            } catch (e: Exception) {
                Timber.e(e, "Ollama getAvailableModels error")
                listOf(DEFAULT_MODEL)
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        // Test API key by making a request to the models endpoint
        val effectiveApiKey = apiKey.ifBlank { this.apiKey }

        if (effectiveApiKey.isBlank()) {
            return false
        }

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$endpoint/models")
                    .get()
                    .addHeader("Authorization", "Bearer $effectiveApiKey")
                    .build()

                val response = client.newCall(request).execute()
                response.isSuccessful
            } catch (e: Exception) {
                Timber.e(e, "Ollama API key validation failed")
                false
            }
        }
    }

    override fun getDefaultModel(): String = DEFAULT_MODEL
}