package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.max

class AiPlaylistGenerator @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val aiHandler: AiHandler,
    private val digestGenerator: UserProfileDigestGenerator,
    private val preferencesRepo: AiPreferencesRepository,
    private val json: Json
) {

    suspend fun generate(
        userPrompt: String,
        allSongs: List<Song>,
        minLength: Int,
        maxLength: Int,
        candidateSongs: List<Song>? = null,
        type: AiSystemPromptType = AiSystemPromptType.PLAYLIST
    ): Result<List<Song>> {
        return try {

            // Get offline scored candidates to pass to LLM (much smaller context window than the whole library)
            val samplingPool = when {
                candidateSongs.isNullOrEmpty().not() -> candidateSongs
                else -> {
                    val rankedForPrompt = dailyMixManager.getTopCandidatesForAi(
                        allSongs = allSongs,
                        favoriteSongIds = emptySet(),
                        limit = 100
                    )
                    if (rankedForPrompt.isNotEmpty()) rankedForPrompt else allSongs
                }
            }

            val isSafe = preferencesRepo.isSafeTokenLimitEnabled.first()
            val prefSampleSize = preferencesRepo.aiSampleSize.first()
            val useExtendedFields = preferencesRepo.aiIncludeExtendedFields.first()
            val sampleCap = if (isSafe) prefSampleSize else prefSampleSize * 2
            val songSample = samplingPool.take(sampleCap)

            val availableSongsJson = buildString {
                songSample.forEachIndexed { index, song ->
                    val score = dailyMixManager.getScore(song.id)
                    val title = song.title.replace("\"", "'").take(40)
                    val artist = song.displayArtist.replace("\"", "'").take(25)
                    val genre = song.genre?.replace("\"", "'")?.take(15) ?: "?"
                    if (index > 0) append(",\n")
                    if (useExtendedFields) {
                        val album = song.album?.replace("\"", "'")?.take(25) ?: "?"
                        val dur = song.duration
                        val fav = if (song.isFavorite) "1" else "0"
                        append("""{"id":"${song.id}","t":"$title","a":"$artist","g":"$genre","al":"$album","d":$dur,"f":$fav,"s":$score}""")
                    } else {
                        append("""{"id":"${song.id}","t":"$title","a":"$artist","g":"$genre","s":$score}""")
                    }
                }
            }

            // Bring in the telemetry digest
            val userDigest = digestGenerator.generateDigest(allSongs, isSafe)

            // Token Optimization: Compact prompt structure with XML data boundaries
            val fullPrompt = """
            $userDigest
            <request>
            <query>$userPrompt</query>
            <target_length>$minLength-$maxLength tracks</target_length>
            </request>
            <candidate_pool>
            [$availableSongsJson]
            </candidate_pool>
            """.trimIndent()

            val responseText = aiHandler.generateContent(fullPrompt, type)

            val songIds = extractPlaylistSongIds(responseText)

            val songMap = allSongs.associateBy { it.id }
            val generatedPlaylist = songIds.mapNotNull { songMap[it] }

            if (generatedPlaylist.isEmpty()) {
                Result.failure(IllegalArgumentException("AI returned song IDs that don't match your library. Try again or adjust your prompt."))
            } else {
                Result.success(generatedPlaylist)
            }

        } catch (e: IllegalArgumentException) {
            Result.failure(Exception(e.message ?: "AI response did not contain a valid playlist."))
        } catch (e: Exception) {
            val errorDetails = buildDetailedErrorMessage(e)
            Result.failure(Exception(errorDetails, e))
        }
    }

    /**
     * Builds a user-friendly error message from the exception chain.
     * Walks the cause chain to find the most specific error detail.
     */
    private fun buildDetailedErrorMessage(e: Exception): String {
        val rootMessage = e.message?.takeIf { it.isNotBlank() }
        val causeMessage = e.cause?.message?.takeIf { it.isNotBlank() }
        val className = e::class.simpleName ?: "Unknown"

        // Check for common error patterns
        val combinedMessages = listOfNotNull(rootMessage, causeMessage).joinToString(" → ")
        
        return when {
            combinedMessages.contains("timeout", ignoreCase = true) ||
            combinedMessages.contains("timed out", ignoreCase = true) ->
                "Request timed out. The AI provider may be slow or overloaded. Try again."
            
            combinedMessages.contains("network", ignoreCase = true) ||
            combinedMessages.contains("connect", ignoreCase = true) ||
            combinedMessages.contains("SocketException", ignoreCase = true) ||
            combinedMessages.contains("no internet", ignoreCase = true) ||
            combinedMessages.contains("offline", ignoreCase = true) ||
            combinedMessages.contains("wifi", ignoreCase = true) ->
                "No Internet Connection. Check your WiFi or mobile data and try again."

            combinedMessages.contains("airplane", ignoreCase = true) ->
                "Airplane mode is active. Please turn it off to use AI."

            combinedMessages.contains("401", ignoreCase = true) ||
            combinedMessages.contains("unauthorized", ignoreCase = true) ->
                "Permission Denied. Your API key might be invalid or restricted."

            combinedMessages.contains("403", ignoreCase = true) ||
            combinedMessages.contains("permission", ignoreCase = true) ||
            combinedMessages.contains("denied", ignoreCase = true) ||
            combinedMessages.contains("forbidden", ignoreCase = true) ->
                "Permission denied by the AI provider. Check that this API key has access to the selected model and that the provider API is enabled."
            
            combinedMessages.contains("safety", ignoreCase = true) ||
            combinedMessages.contains("blocked", ignoreCase = true) ->
                "Content was blocked by safety filters. Try rephrasing your prompt."
            
            combinedMessages.contains("model", ignoreCase = true) &&
            (combinedMessages.contains("not found", ignoreCase = true) ||
             combinedMessages.contains("unavailable", ignoreCase = true)) ->
                "The selected AI model is unavailable. Try selecting a different model in AI Settings."
            
            rootMessage != null -> "AI Error: $rootMessage"
            causeMessage != null -> "AI Error: $causeMessage"
            else -> "AI Error ($className): An unexpected error occurred. Try again."
        }
    }

    private fun extractPlaylistSongIds(rawResponse: String): List<String> {
        val cleaned = AiResponseCleaner.cleanJsonResponse(rawResponse)
        val jsonArray = AiResponseCleaner.extractJsonArray(cleaned)
            ?: throw IllegalArgumentException(
                "AI returned an invalid response format. Expected a JSON array of song IDs but got something else. " +
                "This usually happens with smaller models. Try selecting a more capable model in AI Settings."
            )

        return runCatching { json.decodeFromString<List<String>>(jsonArray) }
            .getOrElse {
                throw IllegalArgumentException(
                    "AI returned malformed JSON. Expected a string array but got: ${jsonArray.take(100)}"
                )
            }
    }
}
