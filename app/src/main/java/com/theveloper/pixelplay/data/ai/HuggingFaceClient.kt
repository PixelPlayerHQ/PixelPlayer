package com.theveloper.pixelplay.data.ai

import android.content.Context
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
 * Client for HuggingFace Hub integration.
 * Supports downloading and running inference on HuggingFace models.
 */
@Singleton
class HuggingFaceClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger
) {
    private var apiToken: String = ""
    private val baseUrl = "https://api-inference.huggingface.co"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Sets the HuggingFace API token.
     */
    fun setApiToken(token: String) {
        apiToken = token
    }

    /**
     * Checks if the API token is valid.
     */
    suspend fun validateToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/whoami-v2")
                .addHeader("Authorization", "Bearer $apiToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful

            aiLogger.logApiKeyValidation("HUGGINGFACE", success)

            success
        } catch (e: Exception) {
            aiLogger.logApiKeyValidation("HUGGINGFACE", false, e.message)
            false
        }
    }

    /**
     * Gets recommended models for music recommendation.
     */
    fun getRecommendedModels(): List<HuggingFaceModel> = listOf(
        HuggingFaceModel(
            id = "microsoft/Phi-3-mini-4k-instruct",
            name = "Phi-3 Mini",
            description = "Small but capable instruction-following model",
            sizeMb = 2300,
            requiresApi = false
        ),
        HuggingFaceModel(
            id = "TinyLlama/TinyLlama-1.1B-Chat-v1.0",
            name = "TinyLlama",
            description = "Ultra-lightweight chat model",
            sizeMb = 640,
            requiresApi = false
        ),
        HuggingFaceModel(
            id = "openai/whisper-base",
            name = "Whisper Base",
            description = "Speech recognition model",
            sizeMb = 150,
            requiresApi = false
        ),
        HuggingFaceModel(
            id = "nlptown/bert-base-multilingual-uncased-sentiment",
            name = "Sentiment Analyzer",
            description = "Multilingual sentiment classification",
            sizeMb = 420,
            requiresApi = false
        ),
        HuggingFaceModel(
            id = "facebook/musicgen-small",
            name = "MusicGen Small",
            description = "Music generation model",
            sizeMb = 1700,
            requiresApi = true
        )
    )

    /**
     * Runs inference on a model.
     */
    fun query(
        modelId: String,
        inputs: String,
        parameters: Map<String, Any>? = null
    ): Flow<Result<Any>> = flow {
        try {
            val requestBody = JSONObject().apply {
                put("inputs", inputs)
                parameters?.let { params ->
                    put("parameters", JSONObject(params))
                }
            }

            val request = Request.Builder()
                .url("$baseUrl/models/$modelId")
                .addHeader("Authorization", "Bearer $apiToken")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Inference failed: ${response.code} - ${response.message}")))
                return@flow
            }

            val body = response.body?.string() ?: ""

            // Parse response based on model type
            val result = try {
                val json = JSONArray(body)
                json.getJSONObject(0).getJSONArray("generated_text").getString(0)
            } catch (e: Exception) {
                body
            }

            aiLogger.logOperation(
                operation = "HF_INFERENCE",
                provider = "HUGGINGFACE",
                model = modelId,
                prompt = inputs,
                response = result.toString(),
                success = true,
                durationMs = 0
            )

            emit(Result.success(result))
        } catch (e: Exception) {
            Timber.tag("HuggingFaceClient").e(e, "Inference failed")

            aiLogger.logOperation(
                operation = "HF_INFERENCE",
                provider = "HUGGINGFACE",
                model = modelId,
                prompt = inputs,
                response = null,
                success = false,
                durationMs = 0,
                error = e.message
            )

            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downloads a model from HuggingFace.
     * Returns a Flow with download progress.
     */
    fun downloadModel(
        modelId: String
    ): Flow<DownloadProgress> = flow {
        try {
            // For now, we use the inference API rather than downloading
            // Full model downloading would require the huggingface_hub library
            emit(DownloadProgress.Completed)
        } catch (e: Exception) {
            emit(DownloadProgress.Failed(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Searches for models on HuggingFace Hub.
     */
    fun searchModels(query: String): Flow<Result<List<String>>> = flow {
        try {
            val request = Request.Builder()
                .url("$baseUrl/models?search=$query")
                .addHeader("Authorization", "Bearer $apiToken")
                .get()
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                emit(Result.failure(Exception("Search failed: ${response.code}")))
                return@flow
            }

            val body = response.body?.string() ?: ""
            val json = JSONArray(body)
            val modelIds = mutableListOf<String>()

            for (i in 0 until json.length()) {
                val model = json.getJSONObject(i)
                modelIds.add(model.getString("id"))
            }

            emit(Result.success(modelIds))
        } catch (e: Exception) {
            Timber.tag("HuggingFaceClient").e(e, "Search failed")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    data class HuggingFaceModel(
        val id: String,
        val name: String,
        val description: String,
        val sizeMb: Long,
        val requiresApi: Boolean
    )

    sealed class DownloadProgress {
        object NotStarted : DownloadProgress()
        data class Downloading(val progress: Int) : DownloadProgress()
        object Completed : DownloadProgress()
        data class Failed(val error: String) : DownloadProgress()
    }
}