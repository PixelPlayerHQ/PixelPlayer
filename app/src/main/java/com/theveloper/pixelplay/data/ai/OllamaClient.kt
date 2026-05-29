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

@Singleton
class OllamaClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger
) {
    companion object { const val DEFAULT_BASE_URL = "http://localhost:11434" }

    private var baseUrl: String = DEFAULT_BASE_URL
    private var apiKey: String = ""
    private var model: String = "llama3"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).build()

    fun configure(endpoint: String, apiKey: String = "", defaultModel: String = "llama3") {
        this.baseUrl = endpoint.trimEnd('/'); this.apiKey = apiKey; this.model = defaultModel
        Timber.d("Ollama configured: $baseUrl, model: $model")
    }

    fun getConfiguration() = OllamaConfig(baseUrl, apiKey, model)

    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request("/api/tags").get().build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext listOf(model)
                val json = JSONObject(response.body?.string() ?: return@withContext listOf(model))
                val models = json.getJSONArray("models")
                (0 until models.length()).map { models.getJSONObject(it).getString("name") }.ifEmpty { listOf(model) }
            }
        } catch (_: Exception) { listOf(model) }
    }

    fun isServerAvailable(): Boolean = try {
        client.newCall(request("/api/tags").get().build()).execute().use { it.isSuccessful }
    } catch (_: Exception) { false }

    suspend fun generateContent(prompt: String, systemPrompt: String = "", temperature: Float = 0.7f, modelName: String = model): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("model", modelName)
                put("messages", JSONArray(buildList {
                    if (systemPrompt.isNotBlank()) add(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    add(JSONObject().apply { put("role", "user"); put("content", prompt) })
                }))
                put("temperature", temperature.toDouble())
                put("stream", false)
            }.toString().toRequestBody("application/json".toMediaType())
            try {
                client.newCall(request("/chat/completions").post(body).build()).execute().use { response ->
                    val rb = response.body?.string() ?: throw Exception("Empty response")
                    if (!response.isSuccessful) throw Exception("Ollama error ${response.code}: ${response.message}")
                    JSONObject(rb).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                }
            } catch (e: Exception) { Timber.e(e, "Ollama generation failed"); throw e }
        }

    suspend fun generateEmbedding(text: String, modelName: String = model): List<Float> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply { put("model", modelName); put("prompt", text) }
            .toString().toRequestBody("application/json".toMediaType())
        client.newCall(request("/embeddings").post(body).build()).execute().use { response ->
            val rb = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) throw Exception("Embedding error: ${response.code}")
            val arr = JSONObject(rb).getJSONArray("embedding")
            (0 until arr.length()).map { arr.getDouble(it).toFloat() }
        }
    }

    private fun request(path: String) = Request.Builder().url("$baseUrl$path").apply {
        if (apiKey.isNotBlank()) addHeader("Authorization", "Bearer $apiKey")
    }

    data class OllamaConfig(val endpoint: String, val apiKey: String, val defaultModel: String)
}
