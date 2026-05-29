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
 * Client for HuggingFace Hub - primarily for embeddings and text models.
 * Uses HuggingFace Inference API for cloud inference.
 */
@Singleton
class HuggingFaceClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiLogger: AiLogger
) {
    companion object {
        const val HF_INFERENCE_URL = "https://api-inference.huggingface.co"
        const val HF_HUB_URL = "https://huggingface.co/api"
    }

    private var apiToken: String = ""

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun configure(token: String) {
        this.apiToken = token
    }

    fun getToken(): String = apiToken
    fun hasToken(): Boolean = apiToken.isNotBlank()

    /**
     * Validate API token
     */
    suspend fun validateToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$HF_HUB_URL/whoami-v2")
                .addHeader("Authorization", "Bearer $apiToken")
                .get()
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch popular text embedding models
     */
    suspend fun fetchEmbeddingModels(): List<HFModel> = withContext(Dispatchers.IO) {
        fetchModelsByTask("feature-extraction", 15)
    }

    /**
     * Fetch popular text generation models
     */
    suspend fun fetchGenerationModels(): List<HFModel> = withContext(Dispatchers.IO) {
        fetchModelsByTask("text-generation", 10)
    }

    private suspend fun fetchModelsByTask(task: String, limit: Int): List<HFModel> {
        return try {
            val request = Request.Builder()
                .url("$HF_HUB_URL/models?sort=downloads&direction=-1&limit=$limit&filter=$task")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()

                val body = response.body?.string() ?: return emptyList()
                val jsonArray = JSONArray(body)
                val models = mutableListOf<HFModel>()

                for (i in 0 until jsonArray.length()) {
                    val m = jsonArray.getJSONObject(i)
                    if (!m.optBoolean("private", false)) {
                        models.add(HFModel(
                            id = m.getString("id"),
                            downloads = m.optInt("downloads", 0),
                            task = task
                        ))
                    }
                }
                models.sortedByDescending { it.downloads }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch $task models")
            defaultModels().filter { it.task == task }
        }
    }

    /**
     * Generate embeddings using HF Inference API
     */
    suspend fun generateEmbedding(modelId: String, text: String): List<Float> = withContext(Dispatchers.IO) {
        val body = JSONObject().put("inputs", text).toString()
        val request = Request.Builder()
            .url("$HF_INFERENCE_URL/models/$modelId")
            .addHeader("Authorization", "Bearer $apiToken")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("API error: ${response.code}")
            val responseBody = response.body?.string() ?: throw Exception("Empty response")

            // Handle array response
            val json = JSONArray(responseBody)
            val embedding = json.getJSONArray(0)
            val result = mutableListOf<Float>()
            for (i in 0 until embedding.length()) {
                result.add(embedding.getDouble(i).toFloat())
            }
            result
        }
    }

    /**
     * Default models when API is unavailable
     */
    fun defaultModels(): List<HFModel> = listOf(
        HFModel("sentence-transformers/all-MiniLM-L6-v2", 2_000_000, "feature-extraction"),
        HFModel("BAAI/bge-small-en-v1.5", 1_500_000, "feature-extraction"),
        HFModel("microsoft/Phi-3-mini-4k-instruct", 1_000_000, "text-generation"),
        HFModel("TinyLlama/TinyLlama-1.1B-Chat-v1.0", 500_000, "text-generation"),
        HFModel("google/gemma-2b-it", 800_000, "text-generation")
    )

    data class HFModel(
        val id: String,
        val downloads: Int,
        val task: String
    )
}