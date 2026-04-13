package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.toSongs
import com.theveloper.pixelplay.data.worker.AiWorkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class GeminiModel(
    val name: String,
    val displayName: String
)

@Singleton
class GeminiModelService @Inject constructor(
    private val orchestrator: AiOrchestrator,
    private val digestGenerator: UserProfileDigestGenerator,
    private val musicDao: MusicDao,
    private val workerManager: AiWorkerManager
) {

    /**
     * Fetches available Gemini models using the provided API key.
     * Returns a list of model names that are available for the user.
     */
    suspend fun fetchAvailableModels(apiKey: String): Result<List<GeminiModel>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("API Key is required"))
                }

                // Use a lightweight model to test the API key and fetch available models
                // We'll make a request to list models using the Gemini API
                val response = makeModelsListRequest(apiKey)

                Result.success(response)
            } catch (e: Exception) {
                Timber.e(e, "Error fetching Gemini models")
                Result.failure(e)
            }
        }
    }

    private suspend fun makeModelsListRequest(apiKey: String): List<GeminiModel> {
        return withContext(Dispatchers.IO) {
            try {
                // Make HTTP request to Google's Gemini API to list models
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseModelsResponse(response)
                } else {
                    val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Timber.e("Failed to fetch models: $responseCode - $errorMessage")
                    // Return default models if API call fails
                    getDefaultModels()
                }
            } catch (e: Exception) {
                Timber.e(e, "Exception fetching models, returning defaults")
                getDefaultModels()
            }
        }
    }

    private fun parseModelsResponse(jsonResponse: String): List<GeminiModel> {
        try {
            // Parse the JSON response to extract model names
            // Expected format: {"models": [{"name": "models/gemini-...", ...}, ...]}
            val models = mutableListOf<GeminiModel>()

            // Simple JSON parsing - extract model names
            val modelPattern = """"name":\s*"(models/[^"]+)"""".toRegex()
            val matches = modelPattern.findAll(jsonResponse)

            for (match in matches) {
                val fullName = match.groupValues[1]
                val modelName = fullName.removePrefix("models/")

                // Filter for generative models (gemini, gemini-pro, gemini-flash, etc.)
                if (modelName.startsWith("gemini", ignoreCase = true) &&
                    !modelName.contains("embedding", ignoreCase = true)) {
                    models.add(GeminiModel(
                        name = modelName,
                        displayName = formatDisplayName(modelName)
                    ))
                }
            }

            return if (models.isNotEmpty()) {
                models.sortedBy { it.displayName.lowercase() }
            } else {
                getDefaultModels()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing models response")
            return getDefaultModels()
        }
    }

    /**
     * Estimates the token count for a piece of text.
     * Uses a conservative 4 chars per token rule for non-Gemini providers,
     * but we recommend using the specific countTokens method on AiClient for accuracy.
     */
    fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

    /**
     * High-level method to perform an AI operation.
     * Starts a background worker if [runInBackground] is true, 
     * otherwise executes immediately and returns the result.
     */
    suspend fun performAiTask(
        prompt: String,
        type: AiSystemPromptType,
        runInBackground: Boolean = false,
        temperature: Float = 0.7f
    ): String? {
        if (runInBackground) {
            workerManager.enqueueAiTask(prompt, type, temperature)
            return null
        } else {
            val allSongs = musicDao.getAllSongsList().toSongs()
            val context = if (type == AiSystemPromptType.PLAYLIST || 
                            type == AiSystemPromptType.TAGGING || 
                            type == AiSystemPromptType.PERSONA) {
                digestGenerator.generateDigest(allSongs)
            } else ""

            return orchestrator.generateContent(
                prompt = prompt,
                type = type,
                temperature = temperature,
                context = context
            )
        }
    }

    private fun formatDisplayName(modelName: String): String {
        // Convert "gemini-2.5-flash" to "Gemini 2.5 Flash"
        return modelName
            .split("-")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private fun getDefaultModels(): List<GeminiModel> {
        // Full curated list derived from latest supported JSON spec
        return listOf(
            GeminiModel("gemini-3-flash-preview", "Gemini 3.0 Flash (Free Default)"),
            GeminiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview"),
            GeminiModel("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite"),
            GeminiModel("gemini-3-pro-preview", "Gemini 3.0 Pro"),
            GeminiModel("gemini-flash-latest", "Gemini Flash Latest"),
            GeminiModel("gemini-flash-lite-latest", "Gemini Flash-Lite Latest"),
            GeminiModel("gemini-pro-latest", "Gemini Pro Latest"),
            GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash"),
            GeminiModel("gemini-2.5-pro", "Gemini 2.5 Pro"),
            GeminiModel("gemini-2.5-flash-lite", "Gemini 2.5 Flash Lite"),
            GeminiModel("gemini-2.0-flash", "Gemini 2.0 Flash"),
            GeminiModel("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite"),
            GeminiModel("gemma-3-12b-it", "Gemma 3 12B"),
            GeminiModel("gemma-3-27b-it", "Gemma 3 27B")
        )
    }
}
