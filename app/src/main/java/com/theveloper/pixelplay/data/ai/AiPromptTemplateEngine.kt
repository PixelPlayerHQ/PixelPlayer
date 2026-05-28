package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced prompt template engine for AI music features.
 * Manages prompt templates with variable substitution.
 */
@Singleton
class AiPromptTemplateEngine @Inject constructor(
    private val aiBehaviorDataCollector: AiBehaviorDataCollector
) {
    /**
     * Template variables that can be substituted.
     */
    data class TemplateContext(
        val userPrompt: String = "",
        val userHistory: String = "",
        val favoriteSongs: String = "",
        val topGenres: String = "",
        val topArtists: String = "",
        val listeningStats: String = "",
        val currentMood: String = "",
        val timeOfDay: String = "",
        val recentlyPlayed: String = "",
        val availableSongs: String = ""
    )

    /**
     * Generates a playlist creation prompt.
     */
    suspend fun generatePlaylistPrompt(
        userPrompt: String,
        availableSongs: List<Song>,
        context: TemplateContext
    ): String {
        val behaviorSummary = aiBehaviorDataCollector.generateBehaviorSummary()

        return buildString {
            appendLine("You are a music recommendation expert. Create a personalized playlist based on the user's request.")
            appendLine()
            appendLine("## User Request")
            appendLine(userPrompt)
            appendLine()
            appendLine("## Listening Behavior Summary")
            appendLine(behaviorSummary)
            appendLine()

            if (context.topGenres.isNotEmpty()) {
                appendLine("## Top Genres")
                appendLine(context.topGenres)
                appendLine()
            }

            if (context.topArtists.isNotEmpty()) {
                appendLine("## Favorite Artists")
                appendLine(context.topArtists)
                appendLine()
            }

            appendLine("## Available Songs for Selection")
            appendLine("Select from these songs (provide song IDs):")
            appendLine(context.availableSongs)
            appendLine()

            appendLine("## Output Format")
            appendLine("Return a JSON array of song IDs that match the request.")
            appendLine("Example: [\"song_id_1\", \"song_id_2\", \"song_id_3\"]")
            appendLine()
            appendLine("Only return the JSON array, no other text.")
        }
    }

    /**
     * Generates a refine/re-ranking prompt.
     */
    fun generateRerankPrompt(
        currentPlaylist: List<Song>,
        refinementPrompt: String
    ): String {
        val songList = currentPlaylist.mapIndexed { index, song ->
            "${index + 1}. ${song.title} - ${song.artist} (${song.album})"
        }.joinToString("\n")

        return buildString {
            appendLine("The user wants to refine their current playlist.")
            appendLine()
            appendLine("## Current Playlist")
            appendLine(songList)
            appendLine()
            appendLine("## User's Refinement Request")
            appendLine(refinementPrompt)
            appendLine()
            appendLine("## Instructions")
            appendLine("Reorder and/or remove songs from the playlist to better match the user's request.")
            appendLine("Return a JSON array with the refined song IDs in the new order.")
            appendLine("You can remove songs that don't fit and keep songs that do.")
        }
    }

    /**
     * Generates a daily mix generation prompt.
     */
    fun generateDailyMixPrompt(
        songs: List<Song>,
        context: TemplateContext
    ): String {
        val songList = songs.take(100).joinToString("\n") { song ->
            "${song.id}|${song.title}|${song.artist}|${song.album}|${song.genre ?: "Unknown"}|${song.duration}"
        }

        return buildString {
            appendLine("Create a 'Daily Mix' playlist - a balanced selection of songs for the user's day.")
            appendLine()
            appendLine("## Selection Criteria")
            appendLine("- Mix of genres for variety")
            appendLine("- Include some familiar favorites")
            appendLine("- Add some discoveries for exploration")
            appendLine("- Match the general mood based on listening history")
            appendLine()
            appendLine("## User's Top Genres")
            appendLine(context.topGenres)
            appendLine()
            appendLine("## User's Top Artists")
            appendLine(context.topArtists)
            appendLine()
            appendLine("## Candidate Songs")
            appendLine(songList)
            appendLine()
            appendLine("## Output")
            appendLine("Return 20-30 song IDs as a JSON array for the Daily Mix.")
        }
    }

    /**
     * Generates a music analysis/explanation prompt.
     */
    fun generateMusicAnalysisPrompt(song: Song): String {
        return buildString {
            appendLine("Analyze and describe the following song:")
            appendLine()
            appendLine("Title: ${song.title}")
            appendLine("Artist: ${song.artist}")
            appendLine("Album: ${song.album}")
            appendLine("Genre: ${song.genre ?: "Unknown"}")
            appendLine("Duration: ${song.duration / 1000 / 60}:${(song.duration / 1000 % 60).toString().padStart(2, '0')}")
            if (song.year != null) appendLine("Year: ${song.year}")
            appendLine()
            appendLine("Provide a brief description (2-3 sentences) about this song's musical style, mood, and characteristics.")
        }
    }

    /**
     * Generates a similar songs recommendation prompt.
     */
    fun generateSimilarSongsPrompt(
        seedSong: Song,
        candidateSongs: List<Song>
    ): String {
        val candidateList = candidateSongs.take(50).joinToString("\n") { song ->
            "${song.id}|${song.title}|${song.artist}|${song.album}|${song.genre ?: "Unknown"}"
        }

        return buildString {
            appendLine("Find songs similar to '${seedSong.title}' by ${seedSong.artist}")
            appendLine()
            appendLine("## Seed Song")
            appendLine("Title: ${seedSong.title}")
            appendLine("Artist: ${seedSong.artist}")
            appendLine("Album: ${seedSong.album}")
            appendLine("Genre: ${seedSong.genre ?: "Unknown"}")
            appendLine()
            appendLine("## Candidate Songs")
            appendLine(candidateList)
            appendLine()
            appendLine("## Task")
            appendLine("Select up to 10 songs from the candidates that are most similar to the seed song.")
            appendLine("Consider: genre, mood, artist similarity, era, style.")
            appendLine()
            appendLine("Return a JSON array of matching song IDs.")
        }
    }

    /**
     * Generates a lyrics translation prompt.
     */
    fun generateLyricsTranslationPrompt(
        lyrics: String,
        targetLanguage: String
    ): String {
        return buildString {
            appendLine("Translate these song lyrics to $targetLanguage.")
            appendLine()
            appendLine("Original Lyrics:")
            appendLine(lyrics)
            appendLine()
            appendLine("## Requirements")
            appendLine("- Maintain the rhythm and flow where possible")
            appendLine("- Keep any timestamps or line numbers intact")
            appendLine("- Preserve the meaning and emotion of the original")
            appendLine("- If the song is already in $targetLanguage, state that clearly")
            appendLine()
            appendLine("Provide only the translated lyrics, no explanations.")
        }
    }

    /**
     * Generates a chat prompt for music Q&A.
     */
    fun generateChatPrompt(
        userMessage: String,
        conversationHistory: List<Pair<String, String>>,
        context: TemplateContext
    ): String {
        val history = conversationHistory.takeLast(5).joinToString("\n") { (role, msg) ->
            "$role: $msg"
        }

        return buildString {
            appendLine("You are a helpful music assistant. Answer user questions about music, artists, playlists, and recommendations.")
            appendLine()
            if (history.isNotEmpty()) {
                appendLine("## Conversation History")
                appendLine(history)
                appendLine()
            }
            appendLine("## User Question")
            appendLine(userMessage)
            appendLine()
            appendLine("## User's Music Preferences")
            appendLine(context.listeningStats)
            appendLine()
            appendLine("Provide a helpful, friendly response. If you recommend songs, mention their titles and artists.")
        }
    }

    /**
     * Gets default system prompts per provider.
     */
    fun getSystemPrompt(provider: AiProvider): String {
        return when (provider) {
            AiProvider.GEMINI -> """
                You are a music recommendation expert for a personal music player app.
                Be concise, friendly, and focus on creating great playlists.
                Always respond with valid JSON when asked to provide song selections.
            """.trimIndent()

            AiProvider.OPENAI -> """
                You are a music recommendation expert for a personal music player app.
                Be concise, friendly, and focus on creating great playlists.
                Always respond with valid JSON when asked to provide song selections.
            """.trimIndent()

            AiProvider.ANTHROPIC -> """
                You are Claude, a music recommendation expert for a personal music player app.
                Be concise, friendly, and helpful.
                Always respond with valid JSON when asked to provide song selections.
            """.trimIndent()

            AiProvider.OLLAMA -> """
                You are a helpful music recommendation assistant.
                Create personalized playlists based on user preferences and listening history.
                Respond with valid JSON when providing song recommendations.
            """.trimIndent()

            else -> "You are a helpful music assistant."
        }
    }
}