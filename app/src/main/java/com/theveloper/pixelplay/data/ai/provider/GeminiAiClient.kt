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
 * Gemini AI provider – uses the Google Generative Language REST API via OkHttp.
 * All URLs and default model IDs come from [AiProviderEndpoints].
 */
class GeminiAiClient(private val apiKey: String) : AiClient {

    companion object {
<<<<<<< HEAD
        private val DEFAULT_MODEL get() = AiProviderEndpoints.GEMINI_DEFAULT_MODEL
=======
        private val DEFAULT_GEMINI_MODEL get() = AiProviderEndpoints.GEMINI_DEFAULT_MODEL
>>>>>>> 80a54c1b (refactor(ai): GeminiAiClient – use AiProviderEndpoints, no hardcoded URLs or model IDs)
        private val BASE_URL get() = AiProviderEndpoints.GEMINI_BASE_URL
    }

    @Serializable private data class Part(val text: String)
    @Serializable private data class Content(val parts: List<Part>, val role: String? = null)
    @Serializable private data class SystemInstruction(val parts: List<Part>)
    @Serializable private data class GenerationConfig(
        val temperature: Float = 0.7f,
        val topK: Int = 64,
        val topP: Float = 0.95f
    )
    @Serializable private data class GenerateRequest(
        val contents: List<Content>,
        val systemInstruction: SystemInstruction? = null,
        val generationConfig: GenerationConfig = GenerationConfig()
    )
    @Serializable private data class Candidate(val content: Content)
    @Serializable private data class GenerateResponse(val candidates: List<Candidate>? = null)
    @Serializable private data class ModelItem(val name: String)
    @Serializable private data class ModelsResponse(val models: List<ModelItem>)

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
    ): String = withContext(Dispatchers.IO) {
        val resolvedModel = model.ifBlank { DEFAULT_MODEL }
        val modelPath = if (resolvedModel.startsWith("models/")) resolvedModel else "models/$resolvedModel"

<<<<<<< HEAD
        val systemInstruction = if (systemPrompt.isNotBlank()) {
            SystemInstruction(parts = listOf(Part(systemPrompt)))
        } else null
=======
            val url = "$BASE_URL/$modelPath:generateContent?key=$apiKey"
>>>>>>> 80a54c1b (refactor(ai): GeminiAiClient – use AiProviderEndpoints, no hardcoded URLs or model IDs)

        val requestBody = GenerateRequest(
            contents = listOf(Content(parts = listOf(Part(prompt)))),
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = temperature)
        )

        val body = json.encodeToString(GenerateRequest.serializer(), requestBody)
            .toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$BASE_URL/$modelPath:generateContent?key=$apiKey")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    throw AiProviderSupport.createException(
                        providerName = "Gemini",
                        statusCode = response.code,
                        transportMessage = response.message,
                        responseBody = responseBody,
                        requestedModel = resolvedModel
                    )
                }
                val parsed = json.decodeFromString<GenerateResponse>(
                    responseBody ?: throw AiProviderSupport.createException(
                        providerName = "Gemini",
                        statusCode = response.code,
                        transportMessage = "Empty response body",
                        responseBody = null,
                        requestedModel = resolvedModel
                    )
                )
                parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    ?: throw AiProviderSupport.createException(
                        providerName = "Gemini",
                        statusCode = response.code,
                        transportMessage = "Response had no content",
                        responseBody = responseBody,
                        requestedModel = resolvedModel
                    )
            }
        } catch (e: Exception) {
            throw AiProviderSupport.wrapThrowable("Gemini", e, resolvedModel)
        }
    }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int =
        (systemPrompt.length + prompt.length) / 4

    override suspend fun getAvailableModels(apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models?key=$apiKey")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext getDefaultModels()
                    val body = response.body?.string() ?: return@withContext getDefaultModels()
                    val parsed = json.decodeFromString<ModelsResponse>(body)
                    val models = parsed.models
                        .map { it.name.removePrefix("models/") }
                        .filter {
                            (it.startsWith("gemini", ignoreCase = true) ||
                             it.startsWith("gemma", ignoreCase = true)) &&
                            !it.contains("embedding", ignoreCase = true)
                        }
                    if (models.isNotEmpty()) models else getDefaultModels()
                }
            } catch (e: Exception) {
                getDefaultModels()
            }
        }

    override suspend fun validateApiKey(apiKey: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$BASE_URL/models?key=$apiKey")
                    .get()
                    .build()
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (e: Exception) {
                false
            }
        }

    override fun getDefaultModel(): String = DEFAULT_MODEL

<<<<<<< HEAD
    private fun getDefaultModels(): List<String> = listOf(
        AiProviderEndpoints.GEMINI_DEFAULT_MODEL,
        "gemini-3-flash-preview",
        "gemini-3.1-pro-preview",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-1.5-flash",
        "gemini-1.5-pro"
    ).distinct()
=======
    private fun getDefaultModels(): List<String> {
        return listOf(
            AiProviderEndpoints.GEMINI_DEFAULT_MODEL,
            "gemini-3-flash-preview",
            "gemini-3.1-pro-preview",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        ).distinct()
    }
>>>>>>> 80a54c1b (refactor(ai): GeminiAiClient – use AiProviderEndpoints, no hardcoded URLs or model IDs)
}
