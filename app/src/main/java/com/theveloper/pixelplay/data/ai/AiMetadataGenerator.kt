package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.SerializationException
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
    private val aiOrchestrator: AiOrchestrator,
    private val json: Json
) {
    private fun cleanJson(jsonString: String): String {
        return jsonString.replace("```json", "").replace("```", "").trim()
    }

    suspend fun generate(
        song: Song,
        fieldsToComplete: List<String>
    ): Result<SongMetadata> {
        return try {
            val fieldsJson = fieldsToComplete.joinToString(separator = ", ") { "\"$it\"" }

            val albumInfo = if (song.album.isNotBlank()) "<album>${song.album}</album>" else ""

            val fullPrompt = """
            <target_song>
            <title>${song.title}</title>
            <artist>${song.displayArtist}</artist>
            $albumInfo
            </target_song>
            <task>
            Complete the following fields using your music knowledge:
            <fields_to_complete>[$fieldsJson]</fields_to_complete>
            </task>
            """.trimIndent()

            val responseText = aiOrchestrator.generateContent(fullPrompt, AiSystemPromptType.METADATA)
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
            Result.failure(Exception("Failed to parse AI response: ${e.message}", e))
        } catch (e: Exception) {
            Timber.e(e, "Generic error in AiMetadataGenerator.")
            Result.failure(Exception("AI Error: ${e.message}", e))
        }
    }
}
