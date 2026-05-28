package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
 * Client for local Ollama server integration.
 * Supports running local LLMs for playlist generation and chat.
 */
@Singleton
class OllamaClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger
) {
    private var baseUrl: String = "http://localhost:11434"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sets the Ollama server endpoint.
     */
    fun setEndpoint(url: String) {
        baseUrl = url.trimEnd('/')
    }

    /**
     * Checks if Ollama server is available.
     */
    fun checkConnection(): Boolean {
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag("OllamaClient").w(e, "Connection check failed")
            false
        }
    }

    /**
     * Gets available models from Ollama server.
     */
    fun getAvailableModels(): Flow<Result<List<String>>> = flow {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Failed to get models: ${response.code}")))
                return@flow
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val models = json.getJSONArray("models")
            val modelNames = mutableListOf<String>()

            for (i in 0 until models.length()) {
                val model = models.getJSONObject(i)
                modelNames.add(model.getString("name"))
            }

            emit(Result.success(modelNames))
        } catch (e: Exception) {
            Timber.tag("OllamaClient").e(e, "Failed to get models")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates content using Ollama with streaming support.
     */
    fun generate(
        model: String,
        prompt: String,
        systemPrompt: String? = null,
        temperature: Float = 0.7f,
        stream: Boolean = true
    ): Flow<Result<String>> = flow {
        try {
            val requestBody = JSONObject().apply {
                put("model", model)
                put("prompt", prompt)
                put("stream", stream)
                put("temperature", temperature)
                systemPrompt?.let { put("system", it) }
                put("options", JSONObject().apply {
                    put("num_predict", 2048)
                    put("top_p", 0.9)
                })
            }

            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Generation failed: ${response.code}")))
                return@flow
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val content = json.getString("response")

            aiLogger.logOperation(
                operation = "OLLAMA_GENERATE",
                provider = "OLLAMA",
                model = model,
                prompt = prompt,
                response = content,
                success = true,
                durationMs = 0,
                tokensUsed = json.optInt("eval_count", 0)
            )

            emit(Result.success(content))
        } catch (e: Exception) {
            Timber.tag("OllamaClient").e(e, "Generation failed")

            aiLogger.logOperation(
                operation = "OLLAMA_GENERATE",
                provider = "OLLAMA",
                model = model,
                prompt = prompt,
                response = null,
                success = false,
                durationMs = 0,
                error = e.message
            )

            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Chat completion using Ollama.
     */
    fun chat(
        model: String,
        messages: List<ChatMessage>,
        temperature: Float = 0.7f
    ): Flow<Result<String>> = flow {
        try {
            val messagesJson = JSONArray()
            messages.forEach { msg ->
                messagesJson.put(JSONObject().apply {
                    put("role", msg.role)
                    put("content", msg.content)
                })
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesJson)
                put("temperature", temperature)
                put("stream", false)
            }

            val request = Request.Builder()
                .url("$baseUrl/api/chat")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Chat failed: ${response.code}")))
                return@flow
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val content = json.getJSONObject("message").getString("content")

            aiLogger.logOperation(
                operation = "OLLAMA_CHAT",
                provider = "OLLAMA",
                model = model,
                prompt = messages.joinToString("\n") { "${it.role}: ${it.content}" },
                response = content,
                success = true,
                durationMs = 0,
                tokensUsed = json.optInt("eval_count", 0)
            )

            emit(Result.success(content))
        } catch (e: Exception) {
            Timber.tag("OllamaClient").e(e, "Chat failed")

            aiLogger.logOperation(
                operation = "OLLAMA_CHAT",
                provider = "OLLAMA",
                model = model,
                prompt = messages.joinToString("\n") { "${it.role}: ${it.content}" },
                response = null,
                success = false,
                durationMs = 0,
                error = e.message
            )

            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates embedding for a given text.
     */
    suspend fun generateEmbedding(model: String, text: String): Result<List<Float>> =
        withContext(Dispatchers.IO) {
            try {
                val requestBody = JSONObject().apply {
                    put("model", model)
                    put("prompt", text)
                }

                val request = Request.Builder()
                    .url("$baseUrl/api/embeddings")
                    .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Embedding failed: ${response.code}"))
                }

                val body = response.body?.string() ?: ""
                val json = JSONObject(body)
                val embeddingArray = json.getJSONArray("embedding")

                val embedding = mutableListOf<Float>()
                for (i in 0 until embeddingArray.length()) {
                    embedding.add(embeddingArray.getDouble(i).toFloat())
                }

                Result.success(embedding)
            } catch (e: Exception) {
                Timber.tag("OllamaClient").e(e, "Embedding failed")
                Result.failure(e)
            }
        }

    data class ChatMessage(
        val role: String, // "system", "user", "assistant"
        val content: String
    )
}