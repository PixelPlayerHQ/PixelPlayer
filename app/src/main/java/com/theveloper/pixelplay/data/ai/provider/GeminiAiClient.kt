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

class GeminiAiClient(private val apiKey: String) : AiClient {

    private companion object {
        val DEFAULT_MODEL get() = AiProviderEndpoints.GEMINI_DEFAULT_MODEL
        val BASE_URL get() = AiProviderEndpoints.GEMINI_BASE_URL
    }

    @Serializable private data class Part(val text: String)
    @Serializable private data class Content(val parts: List<Part>, val role: String? = null)
    @Serializable private data class SystemInstruction(val parts: List<Part>)
    @Serializable private data class GenerationConfig(val temperature: Float = 0.7f, val topK: Int = 64, val topP: Float = 0.95f)
    @Serializable private data class GenerateRequest(
        val contents: List<Content>, val systemInstruction: SystemInstruction? = null,
        val generationConfig: GenerationConfig = GenerationConfig()
    )
    @Serializable private data class Candidate(val content: Content)
    @Serializable private data class GenerateResponse(val candidates: List<Candidate>? = null)
    @Serializable private data class ModelItem(val name: String)
    @Serializable private data class ModelsResponse(val models: List<ModelItem>)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun request(path: String, key: String = apiKey) =
        Request.Builder().url("$BASE_URL/$path?key=$key")

    override suspend fun generateContent(model: String, systemPrompt: String, prompt: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val m = model.ifBlank { DEFAULT_MODEL }
            val mp = if (m.startsWith("models/")) m else "models/$m"
            val req = GenerateRequest(
                contents = listOf(Content(parts = listOf(Part(prompt)))),
                systemInstruction = if (systemPrompt.isNotBlank()) SystemInstruction(listOf(Part(systemPrompt))) else null,
                generationConfig = GenerationConfig(temperature = temperature)
            )
            val body = json.encodeToString(GenerateRequest.serializer(), req).toRequestBody("application/json".toMediaType())
            try {
                client.newCall(request("$mp:generateContent").post(body).build()).execute().use { response ->
                    val rb = response.body?.string()
                    if (!response.isSuccessful) throw AiProviderSupport.createException("Gemini", response.code, response.message, rb, m)
                    val parsed = json.decodeFromString<GenerateResponse>(rb ?: throw AiProviderSupport.createException("Gemini", response.code, "Empty response body", null, m))
                    parsed.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                        ?: throw AiProviderSupport.createException("Gemini", response.code, "Response had no content", rb, m)
                }
            } catch (e: Exception) { throw AiProviderSupport.wrapThrowable("Gemini", e, m) }
        }

    override suspend fun countTokens(model: String, systemPrompt: String, prompt: String): Int =
        (systemPrompt.length + prompt.length) / 4

    override suspend fun getAvailableModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request("models", apiKey).get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext defaultModels()
                val parsed = json.decodeFromString<ModelsResponse>(response.body?.string() ?: return@withContext defaultModels())
                parsed.models.map { it.name.removePrefix("models/") }
                    .filter { (it.startsWith("gemini", true) || it.startsWith("gemma", true)) && !it.contains("embedding", true) }
                    .ifEmpty { defaultModels() }
            }
        } catch (_: Exception) { defaultModels() }
    }

    override suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = json.encodeToString(GenerateRequest.serializer(), GenerateRequest(contents = listOf(Content(parts = listOf(Part("ping")))), generationConfig = GenerationConfig(temperature = 0f)))
            val response = client.newCall(request("models/${AiProviderEndpoints.GEMINI_DEFAULT_MODEL}:generateContent", apiKey).post(body.toRequestBody("application/json".toMediaType())).build()).execute()
            response.isSuccessful
        } catch (_: Exception) { false }
    }

    override fun getDefaultModel(): String = DEFAULT_MODEL

    private fun defaultModels() = listOf(
        AiProviderEndpoints.GEMINI_DEFAULT_MODEL,
        "gemini-3-flash-preview", "gemini-3.1-pro-preview", "gemini-2.5-pro", "gemini-2.5-flash",
        "gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-flash", "gemini-1.5-pro"
    ).distinct()
}
