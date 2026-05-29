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

    private fun jsonArrayOutput(example: String = "[\"id1\",\"id2\",\"id3\"]") =
        "Return ONLY a raw JSON array of song IDs. No markdown, no explanation. Example: $example. On error: []"

    /**
     * Generates a playlist creation prompt.
     */
    suspend fun generatePlaylistPrompt(
        userPrompt: String,
        availableSongs: List<Song>,
        context: TemplateContext
    ): String {
        val behaviorSummary = aiBehaviorDataCollector.generateBehaviorSummary()
        val userContext = aiBehaviorDataCollector.getUserContext()
        return buildString {
            appendLine("You are Vibe-Engine, an expert music curator. Create a personalized playlist.")
            appendLine("# User Request\n$userPrompt\n# Listening Behavior\n$behaviorSummary\n")
            appendLine("# User Context\n$userContext\n")
            if (context.topGenres.isNotEmpty()) appendLine("# Top Genres\n${context.topGenres}\n")
            if (context.topArtists.isNotEmpty()) appendLine("# Favorite Artists\n${context.topArtists}\n")
            if (context.recentlyPlayed.isNotEmpty()) appendLine("# Recently Played\n${context.recentlyPlayed}\n")
            appendLine("# Available Songs\nid|title|artist|album|genre|duration_sec|year|play_count|favorite|skip_est\n${context.availableSongs}\n")
            appendLine("# Curation Strategy")
            appendLine("- Journey: opening (set vibe) -> body (narrative arc) -> closing (resolve)")
            appendLine("- Mix familiar (high play count) with discovery (low play count)")
            appendLine("- Avoid songs with high skip rates unless user explicitly requests them")
            appendLine("- Respect mood, genre, energy, era request. Avoid jarring transitions.\n")
            appendLine(jsonArrayOutput())
        }
    }

    fun generateRerankPrompt(currentPlaylist: List<Song>, refinementPrompt: String): String {
        val songList = currentPlaylist.mapIndexed { i, s -> "${i + 1}. ${s.title} - ${s.artist} (${s.album})" }.joinToString("\n")
        return buildString {
            appendLine("# Current Playlist\n$songList\n# Refinement Request\n$refinementPrompt\n")
            appendLine("Reorder/remove songs to match the request. ${jsonArrayOutput()}")
        }
    }

    suspend fun generateDailyMixPrompt(songs: List<Song>, context: TemplateContext, maxSongs: Int? = null): String {
        val limit = maxSongs ?: aiPreferencesRepository.maxSongsForContext.first()
        val userContext = aiBehaviorDataCollector.getUserContext()
        val songList = songs.take(limit).joinToString("\n") { s ->
            "${s.id}|${s.title}|${s.artist}|${s.album}|${s.genre ?: "Unknown"}|${s.duration / 1000}|${s.year}|${if (s.isFavorite) "1" else "0"}"
        }
        return buildString {
            appendLine("Create a 'Daily Mix' — balanced selection for the user's day.")
            appendLine("# Criteria")
            appendLine("- Match energy to listening phase (Morning/Afternoon/Evening/Night)")
            appendLine("- Mix genres from top affinities; include familiar favorites + discoveries")
            appendLine("- Avoid songs user tends to skip")
            appendLine("- Journey: start strong, build, peak, recover, resolve. 20-30 tracks, no dupes.\n")
            appendLine("# Top Genres\n${context.topGenres}\n# Top Artists\n${context.topArtists}\n")
            appendLine("# User Context\n$userContext\n")
            appendLine("# Context\nTime: ${context.timeOfDay}  Mood: ${context.currentMood}\n")
            appendLine("# Candidates\nid|title|artist|album|genre|duration_sec|year|favorite\n$songList\n")
            appendLine(jsonArrayOutput("[\"id1\",\"id2\",\"id3\"] (20-30 items)"))
        }
    }

    fun generateMusicAnalysisPrompt(song: Song): String {
        val dur = "${song.duration / 1000 / 60}:${(song.duration / 1000 % 60).toString().padStart(2, '0')}"
        return "Analyze: ${song.title} by ${song.artist} (${song.album}, ${song.genre ?: "Unknown"}, $dur${song.year.let { ", $it" }}). Provide 2-3 sentences on musical style, mood, and characteristics."
    }

    fun generateSimilarSongsPrompt(seedSong: Song, candidateSongs: List<Song>): String {
        val candidates = candidateSongs.take(50).joinToString("\n") { s ->
            "${s.id}|${s.title}|${s.artist}|${s.album}|${s.genre ?: "Unknown"}|${s.duration / 1000}|${s.year}"
        }
        return buildString {
            appendLine("Find songs similar to '${seedSong.title}' by ${seedSong.artist}")
            appendLine("# Seed\nTitle: ${seedSong.title}\nArtist: ${seedSong.artist}\nAlbum: ${seedSong.album}\nGenre: ${seedSong.genre ?: "Unknown"}\nDuration: ${seedSong.duration / 1000}s\nYear: ${seedSong.year}\n")
            appendLine("# Candidates\nid|title|artist|album|genre|duration_sec|year\n$candidates\n")
            appendLine("Select up to 10 most similar. Consider: genre > mood/tempo > artist > era > style.")
            appendLine(jsonArrayOutput("[\"id1\",\"id2\",\"id3\"] (up to 10)"))
        }
    }

    fun generateLyricsTranslationPrompt(lyrics: String, targetLanguage: String): String {
        return buildString {
            appendLine("Translate to $targetLanguage. Maintain rhythm/flow. Keep timestamps. Preserve meaning/emotion. If already $targetLanguage, say so.\n$lyrics\n")
            appendLine("Provide only the translated lyrics, no explanations.")
        }
    }

    fun generateChatPrompt(userMessage: String, conversationHistory: List<Pair<String, String>>, context: TemplateContext): String {
        val history = conversationHistory.takeLast(5).joinToString("\n") { (r, m) -> "$r: $m" }
        return buildString {
            appendLine("You are a helpful music assistant.")
            if (history.isNotEmpty()) appendLine("# History\n$history\n")
            appendLine("# Question\n$userMessage\n# Preferences\n${context.listeningStats}\n")
            appendLine("Be friendly. If recommending songs, mention titles and artists.")
        }
    }

    fun getSystemPrompt(provider: AiProvider): String = when (provider) {
        AiProvider.ANTHROPIC -> "You are Claude acting as Vibe-Engine, an expert music curator for PixelPlayer. Analyze deeply, craft emotionally intelligent playlists. Respond with raw JSON arrays of song IDs. No markdown."
        AiProvider.OLLAMA -> "You are Vibe-Engine, a music curation assistant. Create playlists from the user's profile and available songs. Respond with raw JSON arrays of song IDs."
        else -> "You are Vibe-Engine, an expert music curator for PixelPlayer. Analyze listening profiles and curate emotionally resonant playlists. Respond with raw JSON arrays of song IDs. No markdown."
    }
}