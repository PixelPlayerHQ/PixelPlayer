package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.SerializationException
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.ai.provider.AiClientFactory
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

@Serializable
data class SongMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null
)

class AiMetadataGenerator @Inject constructor(
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val aiClientFactory: AiClientFactory,
    private val json: Json
) {
    companion object {
        // Removed DEFAULT_GEMINI_MODEL - now handled by provider implementations
    }

    private fun cleanJson(jsonString: String): String {
        return jsonString.replace("```json", "").replace("```", "").trim()
    }

    suspend fun generate(
        song: Song,
        fieldsToComplete: List<String>
    ): Result<SongMetadata> {
        return try {
            // Get AI provider and create client
            val providerName = aiPreferencesRepository.aiProvider.first()
            val provider = AiProvider.fromString(providerName)
            
            // Get API key based on provider
            val apiKey = when (provider) {
                AiProvider.GEMINI -> aiPreferencesRepository.geminiApiKey.first()
                AiProvider.DEEPSEEK -> aiPreferencesRepository.deepseekApiKey.first()
            }
            
            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key not configured for ${provider.displayName}."))
            }
            
            // Create AI client
            val aiClient = aiClientFactory.createClient(provider, apiKey)
            
            // Get model based on provider
            val selectedModel = when (provider) {
                AiProvider.GEMINI -> aiPreferencesRepository.geminiModel.first()
                AiProvider.DEEPSEEK -> aiPreferencesRepository.deepseekModel.first()
            }
            val modelName = selectedModel.ifBlank { aiClient.getDefaultModel() }

            val customSystemPrompt = when (provider) {
                AiProvider.GEMINI -> aiPreferencesRepository.geminiSystemPrompt.first()
                AiProvider.DEEPSEEK -> aiPreferencesRepository.deepseekSystemPrompt.first()
            }

            val fieldsJson = fieldsToComplete.joinToString(separator = ", ") { "\"$it\"" }

            val systemPrompt = """
            You are a music metadata expert. Your task is to find and complete missing metadata for a given song.
            You will be given the song's title and artist, and a list of fields to complete.
            Your response MUST be a raw JSON object, without any markdown, backticks or other formatting.
            The JSON keys MUST be lowercase and match the requested fields (e.g., "title", "artist", "album", "genre").
            For the genre, you must provide only one, the most accurate, single genre for the song.
            If you cannot find a specific piece of information, you should return an empty string for that field.

            Example response for a request to complete "album" and "genre":
            {
                "album": "Some Album",
                "genre": "Indie Pop"
            }
            """.trimIndent()

            val albumInfo = if (song.album.isNotBlank()) "Album: \"${song.album}\"" else ""

            val fullPrompt = """
            $systemPrompt
            Additional guidance:
            $customSystemPrompt

            Song title: "${song.title}"
            Song artist: "${song.displayArtist}"
            $albumInfo
            Fields to complete: [$fieldsJson]
            """.trimIndent()

            val responseText = aiClient.generateContent(modelName, fullPrompt)
            if (responseText.isBlank()) {
                Timber.e("AI returned an empty or null response.")
                return Result.failure(Exception("AI returned an empty response."))
            }

            Timber.d("AI Response: $responseText")
            val cleanedJson = cleanJson(responseText)
            val metadata = json.decodeFromString<SongMetadata>(cleanedJson)

            Result.success(metadata)
        } catch (e: SerializationException) {
            Timber.e(e, "Error deserializing AI response.")
            Result.failure(Exception("Failed to parse AI response: ${e.message}"))
        } catch (e: Exception) {
            Timber.e(e, "Generic error in AiMetadataGenerator.")
            Result.failure(Exception("AI Error: ${e.message}"))
        }
    }
}
