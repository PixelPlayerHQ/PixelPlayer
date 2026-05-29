package com.theveloper.pixelplay.data.ai

import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Enhanced prompt template engine for AI music features.
 * Manages prompt templates with variable substitution.
 */
@Singleton
class AiPromptTemplateEngine @Inject constructor(
    private val aiBehaviorDataCollector: AiBehaviorDataCollector,
    private val aiPreferencesRepository: AiPreferencesRepository
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
            appendLine("You are Vibe-Engine, an expert music curator. Create a personalized playlist based on the user's request.")
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

            if (context.recentlyPlayed.isNotEmpty()) {
                appendLine("## Recently Played")
                appendLine(context.recentlyPlayed)
                appendLine()
            }

            appendLine("## Available Songs for Selection")
            appendLine("Each song has: id|title|artist|album|genre|duration(sec)|year|play_count|favorite")
            appendLine("Select from these songs (provide song IDs):")
            appendLine(context.availableSongs)
            appendLine()

            appendLine("## Curation Strategy")
            appendLine("- Build a cohesive journey: opening (set vibe) → body (narrative arc) → closing (resolve).")
            appendLine("- Mix familiar tracks (higher play count) with discovery (lower play count).")
            appendLine("- Respect the user's request for mood, genre, energy level, or era.")
            appendLine("- Natural energy flow: avoid jarring transitions between vastly different songs.")
            appendLine()

            appendLine("## Output Format")
            appendLine("Return ONLY a JSON array of song IDs — no markdown, no explanation.")
            appendLine("Example: [\"song_id_1\", \"song_id_2\", \"song_id_3\"]")
            appendLine("On error or no match, return: []")
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
    suspend fun generateDailyMixPrompt(
        songs: List<Song>,
        context: TemplateContext,
        maxSongs: Int? = null
    ): String {
        val limit = maxSongs ?: aiPreferencesRepository.maxSongsForContext.first()
        val songList = songs.take(limit).joinToString("\n") { song ->
            "${song.id}|${song.title}|${song.artist}|${song.album}|${song.genre ?: "Unknown"}|${song.duration / 1000}|${song.year ?: "?"}|${if (song.isFavorite) "1" else "0"}"
        }

        return buildString {
            appendLine("Create a 'Daily Mix' playlist — a balanced selection of songs for the user's day.")
            appendLine()
            appendLine("## Selection Criteria")
            appendLine("- Listen to the user's listening phase (Morning/Afternoon/Evening/Night) and match energy.")
            appendLine("- Mix genres for variety — pull from the user's top genres but include adjacent ones.")
            appendLine("- Include some familiar favorites (higher play count) for comfort.")
            appendLine("- Add discoveries (lower play count or unplayed) for exploration.")
            appendLine("- Build a journey: start strong, build energy, peak, recover, resolve.")
            appendLine("- 20-30 tracks total. No duplicate song IDs.")
            appendLine()
            appendLine("## User's Top Genres")
            appendLine(context.topGenres)
            appendLine()
            appendLine("## User's Top Artists")
            appendLine(context.topArtists)
            appendLine()
            appendLine("## Time & Context")
            appendLine("Current time: ${context.timeOfDay}")
            appendLine("Current mood: ${context.currentMood}")
            appendLine()
            appendLine("## Candidate Songs")
            appendLine("Format: id|title|artist|album|genre|duration_sec|year|favorite")
            appendLine(songList)
            appendLine()
            appendLine("## Output Schema")
            appendLine("Return ONLY a JSON array of song IDs (20-30 items). No markdown, no explanation.")
            appendLine("Example: [\"id1\",\"id2\",\"id3\"]")
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
            "${song.id}|${song.title}|${song.artist}|${song.album}|${song.genre ?: "Unknown"}|${song.duration / 1000}|${song.year ?: "?"}"
        }

        return buildString {
            appendLine("Find songs similar to '${seedSong.title}' by ${seedSong.artist}")
            appendLine()
            appendLine("## Seed Song")
            appendLine("Title: ${seedSong.title}")
            appendLine("Artist: ${seedSong.artist}")
            appendLine("Album: ${seedSong.album}")
            appendLine("Genre: ${seedSong.genre ?: "Unknown"}")
            appendLine("Duration: ${seedSong.duration / 1000}s")
            appendLine("Year: ${seedSong.year ?: "Unknown"}")
            appendLine()
            appendLine("## Candidate Songs")
            appendLine("Format: id|title|artist|album|genre|duration_sec|year")
            appendLine(candidateList)
            appendLine()
            appendLine("## Task")
            appendLine("Select up to 10 songs from the candidates that are most similar to the seed song.")
            appendLine("Consider in order: genre > mood/tempo > artist similarity > era > style.")
            appendLine()
            appendLine("## Output Schema")
            appendLine("Return ONLY a JSON array of up to 10 song IDs. No markdown, no explanation.")
            appendLine("Example: [\"id1\",\"id2\",\"id3\"]")
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
                You are Vibe-Engine, an expert music curator for PixelPlayer.
                Analyze the user's listening profile and curate playlists that resonate emotionally.
                Always respond with valid JSON arrays of song IDs.
                No markdown, no explanations — just the JSON.
            """.trimIndent()

            AiProvider.OPENAI -> """
                You are Vibe-Engine, an expert music curator for PixelPlayer.
                Analyze the user's listening profile and curate playlists that resonate emotionally.
                Always respond with valid JSON arrays of song IDs.
                No markdown, no explanations — just the JSON.
            """.trimIndent()

            AiProvider.ANTHROPIC -> """
                You are Claude acting as Vibe-Engine, an expert music curator for PixelPlayer.
                Analyze the user's data deeply and craft playlists with emotional intelligence.
                Always respond with valid JSON arrays of song IDs.
                No markdown, no explanations — just the JSON.
            """.trimIndent()

            AiProvider.OLLAMA -> """
                You are Vibe-Engine, a music curation assistant.
                Create personalized playlists using the user's listening profile and available songs.
                Always respond with valid JSON arrays of song IDs.
                No markdown, no explanations — just the JSON.
            """.trimIndent()

            else -> """
                You are Vibe-Engine, an expert music curator.
                Analyze listening data and curate playlists with valid JSON output.
            """.trimIndent()
        }
    }
}