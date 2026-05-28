package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.network.NetworkTimeouts
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
    private val orchestrator: AiHandler,
    private val digestGenerator: UserProfileDigestGenerator,
    private val musicRepository: MusicRepository,
    private val workerManager: AiWorkerManager
) {

    suspend fun fetchAvailableModels(apiKey: String): Result<List<GeminiModel>> {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext Result.failure(Exception("API Key is required"))
                }
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
                val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "GET"
                connection.connectTimeout = NetworkTimeouts.GITHUB_CONNECT_MS
                connection.readTimeout = NetworkTimeouts.GITHUB_READ_MS

                val responseCode = connection.responseCode
                val apiModels = if (responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    parseModelsResponse(response)
                } else emptyList()

                val defaults = getDefaultModels()
                (apiModels + defaults).distinctBy { it.name }.sortedWith(
                    compareBy<GeminiModel> { model ->
                        val preferred = defaults.map { it.name }
                        preferred.indexOf(model.name).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    }.thenBy { it.displayName.lowercase() }
                )
            } catch (e: Exception) {
                getDefaultModels()
            }
        }
    }

    private fun parseModelsResponse(jsonResponse: String): List<GeminiModel> {
        try {
            val models = mutableListOf<GeminiModel>()
            val modelPattern = """"name":\s*"(models/[^"]+)"""".toRegex()
            val matches = modelPattern.findAll(jsonResponse)

            val blacklist = listOf("-2.0", "-2.5", "-preview", "customtools", "search", "tuning", "-001", "-002")
            val whitelist = listOf("gemini-3.1-pro-preview")

            for (match in matches) {
                val fullName = match.groupValues[1]
                val modelName = fullName.removePrefix("models/")

                val isWhitelisted = whitelist.any { modelName == it }
                val hasForbiddenSuffix = blacklist.any { modelName.contains(it) }
                val isBlacklisted = hasForbiddenSuffix && !isWhitelisted

                if (!isBlacklisted && 
                    (modelName.startsWith("gemini", ignoreCase = true) || 
                     modelName.startsWith("gemma", ignoreCase = true)) &&
                    !modelName.contains("embedding", ignoreCase = true)) {
                    models.add(GeminiModel(
                        name = modelName,
                        displayName = formatDisplayName(modelName)
                    ))
                }
            }
            return models
        } catch (e: Exception) {
            return emptyList()
        }
    }

    fun estimateTokens(text: String): Int {
        return (text.length / 4).coerceAtLeast(1)
    }

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
            val allSongs = musicRepository.getAllSongsOnce()
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
        return modelName
            .split("-")
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.uppercase() }
            }
    }

    private fun getDefaultModels(): List<GeminiModel> {
        return listOf(
            GeminiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite (Recommended Default)"),
            GeminiModel("gemini-3.5-flash", "Gemini 3.5 Flash"),
            GeminiModel("gemini-3.1-pro-preview", "Gemini 3.1 Pro (Preview)"),
            GeminiModel("gemini-flash-lite-latest", "Gemini Flash Lite Latest"),
            GeminiModel("gemini-flash-latest", "Gemini Flash Latest"),
            GeminiModel("gemma-4-31b-it", "Gemma 4 31B IT"),
            GeminiModel("gemma-4-26b-a4b-it", "Gemma 4 26B MoE")
        )
    }
}
