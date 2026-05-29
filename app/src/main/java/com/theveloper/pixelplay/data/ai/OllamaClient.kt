package com.theveloper.pixelplay.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client for Ollama server integration.
 * Supports connecting to local or remote Ollama servers.
 * Can optionally use API key for protected servers.
 */
@Singleton
class OllamaClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger
) {
    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11434"
    }

    // User-configurable settings (loaded from preferences)
    private var baseUrl: String = DEFAULT_BASE_URL
    private var apiKey: String = ""
    private var model: String = "llama3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // Higher timeout for model generation
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Configure the Ollama server URL
     */
    fun configure(endpoint: String, apiKey: String = "", defaultModel: String = "llama3") {
        this.baseUrl = endpoint.trimEnd('/')
        this.apiKey = apiKey
        this.model = defaultModel
        Timber.d("Ollama configured: $baseUrl, model: $model")
    }

    /**
     * Gets current configuration
     */
    fun getConfiguration(): OllamaConfig = OllamaConfig(baseUrl, apiKey, model)

    /**
     * Gets available models from the configured server
     */
    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .apply { addAuthHeader() }
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Failed to fetch models: ${response.code}")
                    return@withContext listOf(model)
                }

                val body = response.body?.string() ?: return@withContext listOf(model)
                val json = JSONObject(body)
                val models = json.getJSONArray("models")

                val modelNames = mutableListOf<String>()
                for (i in 0 until models.length()) {
                    modelNames.add(models.getJSONObject(i).getString("name"))
                }
                modelNames.ifEmpty { listOf(model) }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch Ollama models")
            listOf(model)
        }
    }

    /**
     * Check if server is reachable
     */
    fun isServerAvailable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .apply { addAuthHeader() }
                .get()
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Timber.w(e, "Ollama server not available")
            false
        }
    }

    /**
     * Generate content using Ollama
     */
    suspend fun generateContent(
        prompt: String,
        systemPrompt: String = "",
        temperature: Float = 0.7f,
        modelName: String = model
    ): String = withContext(Dispatchers.IO) {
        val messages = buildMessages(systemPrompt, prompt)

        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("messages", JSONArray(messages))
            put("temperature", temperature.toDouble())
            put("stream", false)
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .apply { addAuthHeader() }
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string()
                if (!response.isSuccessful) {
                    throw Exception("Ollama error ${response.code}: ${response.message}")
                }

                parseResponse(body ?: throw Exception("Empty response"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Ollama generation failed")
            throw e
        }
    }

    /**
     * Generate embedding using Ollama (for local recommendations)
     */
    suspend fun generateEmbedding(text: String, modelName: String = model): List<Float> = withContext(Dispatchers.IO) {
        val requestBody = JSONObject().apply {
            put("model", modelName)
            put("prompt", text)
        }

        val request = Request.Builder()
            .url("$baseUrl/embeddings")
            .apply { addAuthHeader() }
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string()
            if (!response.isSuccessful) {
                throw Exception("Embedding error: ${response.code}")
            }

            val json = JSONObject(body ?: throw Exception("Empty response"))
            val embedding = json.getJSONArray("embedding")
            val result = mutableListOf<Float>()
            for (i in 0 until embedding.length()) {
                result.add(embedding.getDouble(i).toFloat())
            }
            result
        }
    }

    private fun buildMessages(systemPrompt: String, userPrompt: String): List<JSONObject> {
        val messages = mutableListOf<JSONObject>()
        if (systemPrompt.isNotBlank()) {
            messages.add(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
        }
        messages.add(JSONObject().apply {
            put("role", "user")
            put("content", userPrompt)
        })
        return messages
    }

    private fun parseResponse(body: String): String {
        val json = JSONObject(body)
        val choices = json.getJSONArray("choices")
        if (choices.length() > 0) {
            return choices.getJSONObject(0).getJSONObject("message").getString("content")
        }
        throw Exception("No response content")
    }

    private fun Request.Builder.addAuthHeader(): Request.Builder {
        return if (apiKey.isNotBlank()) {
            addHeader("Authorization", "Bearer $apiKey")
        } else this
    }

    data class OllamaConfig(
        val endpoint: String,
        val apiKey: String,
        val defaultModel: String
    )
}