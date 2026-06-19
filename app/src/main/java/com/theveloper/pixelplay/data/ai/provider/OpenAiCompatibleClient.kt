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
 * Base class for AI providers that expose an OpenAI-compatible chat/completions API.
 *
 * Subclasses only need to supply [providerName], [baseUrl], [defaultModel],
 * [defaultModels], and optionally [modelFilter] or [decorateRequest].
 */
abstract class OpenAiCompatibleClient(private val apiKey: String) : AiClient {

    protected abstract val providerName: String
    protected abstract val baseUrl: String
    protected abstract val providerDefaultModel: String
    protected abstract val providerDefaultModels: List<String>

    @Serializable
    protected data class ChatMessage(val role: String, val content: String)

    @Serializable
    protected data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )

    @Serializable
    protected data class ChatChoice(val message: ChatMessage)

    @Serializable
    protected data class ChatResponse(val choices: List<ChatChoice>)

    @Serializable
    protected data class ModelItem(val id: String)

    @Serializable
    protected data class ModelsResponse(val data: List<ModelItem>)

    protected val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    protected val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    protected open fun decorateRequest(builder: Request.Builder): Request.Builder = builder

    internal open fun filterModels(models: List<String>): List<String> = models

    override suspend fun generateContent(
        model: String,
        systemPrompt: String,
        prompt: String,
        temperature: Float
    ): String {
        return withContext(Dispatchers.IO) {
            val resolvedModel = model.ifBlank { providerDefaultModel }
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

            val builder = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")

            val request = decorateRequest(builder).post(body).build()

            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()

                    if (!response.isSuccessful) {
                        throw AiProviderSupport.createException(
                            providerName = providerName,
                            statusCode = response.code,
                            transportMessage = response.message,
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                    }

                    val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                    chatResponse.choices.firstOrNull()?.message?.content
                        ?: throw AiProviderSupport.createException(
                            providerName = providerName,
                            statusCode = response.code,
                            transportMessage = "Response had no content",
                            responseBody = responseBody,
                            requestedModel = resolvedModel
                        )
                }
            } catch (e: Exception) {
                throw AiProviderSupport.wrapThrowable(providerName, e, resolvedModel)
            }
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int {
        return (systemPrompt.length + prompt.length) / 4
    }

    override suspend fun getAvailableModels(apiKey: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext providerDefaultModels
                    }

                    val responseBody = response.body.string()
                    val modelsResponse = json.decodeFromString<ModelsResponse>(responseBody)
                    filterModels(modelsResponse.data.map { it.id })
                }
            } catch (e: Exception) {
                providerDefaultModels
            }
        }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("${baseUrl.trimEnd('/')}/models")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }
    }

    override fun getDefaultModel(): String = providerDefaultModel
}
