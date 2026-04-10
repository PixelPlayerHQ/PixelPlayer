package com.theveloper.pixelplay.data.ai


import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.model.Song
import kotlinx.serialization.json.Json
import javax.inject.Inject
import kotlin.math.max

class AiPlaylistGenerator @Inject constructor(
    private val dailyMixManager: DailyMixManager,
    private val aiOrchestrator: AiOrchestrator,
    private val digestGenerator: UserProfileDigestGenerator,
    private val json: Json
) {

    suspend fun generate(
        userPrompt: String,
        allSongs: List<Song>,
        minLength: Int,
        maxLength: Int,
        candidateSongs: List<Song>? = null
    ): Result<List<Song>> {
        return try {

            // Get offline scored candidates to pass to LLM (much smaller context window than the whole library)
            val samplingPool = when {
                candidateSongs.isNullOrEmpty().not() -> candidateSongs ?: allSongs
                else -> {
                    val rankedForPrompt = dailyMixManager.getTopCandidatesForAi(
                        allSongs = allSongs,
                        favoriteSongIds = emptySet(),
                        limit = 100
                    )
                    if (rankedForPrompt.isNotEmpty()) rankedForPrompt else allSongs
                }
            }

            val sampleSize = max(minLength, 60).coerceAtMost(100)
            val songSample = samplingPool.take(sampleSize)
            
            val songScores = songSample.associate { it.id to dailyMixManager.getScore(it.id) }
            val availableSongsJson = songSample.joinToString(separator = ",\n") { song ->
                val score = songScores[song.id] ?: 0.0
                """
                {
                    "id": "${song.id}",
                    "title": "${song.title.replace("\"", "'")}",
                    "artist": "${song.displayArtist.replace("\"", "'")}",
                    "genre": "${song.genre?.replace("\"", "'") ?: "unknown"}",
                    "relevance_score": $score
                }
                """.trimIndent()
            }

            // Bring in the telemetry digest
            val userDigest = digestGenerator.generateDigest(allSongs)

            val fullPrompt = """
            User Listening Profile Digest:
            $userDigest
            
            ---
            User's explicit request: "$userPrompt"
            Minimum playlist length: $minLength
            Maximum playlist length: $maxLength
            
            Available songs to choose from:
            [
            $availableSongsJson
            ]
            """.trimIndent()

            val responseText = aiOrchestrator.generateContent(fullPrompt, AiSystemPromptType.PLAYLIST)

            val songIds = extractPlaylistSongIds(responseText)

            val songMap = allSongs.associateBy { it.id }
            val generatedPlaylist = songIds.mapNotNull { songMap[it] }

            Result.success(generatedPlaylist)

        } catch (e: IllegalArgumentException) {
            Result.failure(Exception(e.message ?: "AI response did not contain a valid playlist."))
        } catch (e: Exception) {
            val errorDetails = e.message?.takeIf { it.isNotBlank() }
                ?: e.cause?.message?.takeIf { it.isNotBlank() }
                ?: e::class.simpleName
                ?: "Unknown error"
            Result.failure(Exception("AI Error: $errorDetails", e))
        }
    }

    private fun extractPlaylistSongIds(rawResponse: String): List<String> {
        val sanitized = rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()

        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '[') continue

            var depth = 0
            var inString = false
            var isEscaped = false

            for (index in startIndex until sanitized.length) {
                val character = sanitized[index]

                if (inString) {
                    if (isEscaped) {
                        isEscaped = false
                        continue
                    }

                    when (character) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (character) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = sanitized.substring(startIndex, index + 1)
                            val decoded = runCatching { json.decodeFromString<List<String>>(candidate) }
                            if (decoded.isSuccess) {
                                return decoded.getOrThrow()
                            }
                            break
                        }
                    }
                }
            }
        }

        throw IllegalArgumentException("AI response did not contain a valid playlist.")
    }
}
