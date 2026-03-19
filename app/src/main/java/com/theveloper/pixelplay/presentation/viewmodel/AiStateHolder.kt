package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.DailyMixManager
import com.theveloper.pixelplay.data.ai.AiMetadataGenerator
import com.theveloper.pixelplay.data.ai.AiPlaylistGenerator
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages AI-powered features: AI Playlist Generation and AI Metadata Generation.
 * Extracted from PlayerViewModel.
 */
@Singleton
class AiStateHolder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPlaylistGenerator: AiPlaylistGenerator,
    private val aiMetadataGenerator: AiMetadataGenerator,
    private val dailyMixManager: DailyMixManager,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val dailyMixStateHolder: DailyMixStateHolder
) {
    // State
    private val _showAiPlaylistSheet = MutableStateFlow(false)
    val showAiPlaylistSheet = _showAiPlaylistSheet.asStateFlow()

    private val _isGeneratingAiPlaylist = MutableStateFlow(false)
    val isGeneratingAiPlaylist = _isGeneratingAiPlaylist.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    private val _isGeneratingMetadata = MutableStateFlow(false)
    val isGeneratingMetadata = _isGeneratingMetadata.asStateFlow()

    private var scope: CoroutineScope? = null
    private var allSongsProvider: (() -> List<Song>)? = null
    private var favoriteSongIdsProvider: (() -> Set<String>)? = null
    
    // Callbacks to interact with PlayerViewModel/UI
    private var toastEmitter: ((String) -> Unit)? = null
    private var playSongsCallback: ((List<Song>, Song, String) -> Unit)? = null // songs, startSong, queueName
    private var openPlayerSheetCallback: (() -> Unit)? = null

    private val titleStopWords = setOf(
        "a", "an", "the", "and", "or", "for", "to", "of", "in", "on", "with", "by", "from",
        "de", "la", "el", "los", "las", "y", "o", "para", "con", "por", "del", "al", "un", "una",
        "core", "request", "mood", "target", "activity", "context", "era", "focus", "prioritize",
        "genres", "avoid", "preferred", "language", "energy", "level", "discovery", "where",
        "familiar", "deep", "cuts", "keep", "transitions", "smooth", "repetitive", "artist",
        "clustering", "songs", "listener", "favorites", "explicit", "lyrics", "alternatives",
        "whenever", "possible"
    )

    fun initialize(
        scope: CoroutineScope,
        allSongsProvider: () -> List<Song>,
        favoriteSongIdsProvider: () -> Set<String>,
        toastEmitter: (String) -> Unit,
        playSongsCallback: (List<Song>, Song, String) -> Unit,
        openPlayerSheetCallback: () -> Unit
    ) {
        this.scope = scope
        this.allSongsProvider = allSongsProvider
        this.favoriteSongIdsProvider = favoriteSongIdsProvider
        this.toastEmitter = toastEmitter
        this.playSongsCallback = playSongsCallback
        this.openPlayerSheetCallback = openPlayerSheetCallback
    }

    fun showAiPlaylistSheet() {
        _showAiPlaylistSheet.value = true
    }

    fun dismissAiPlaylistSheet() {
        _showAiPlaylistSheet.value = false
        _aiError.value = null
        _isGeneratingAiPlaylist.value = false
    }

    fun clearAiPlaylistError() {
        _aiError.value = null
    }

    fun generateAiPlaylist(
        prompt: String,
        minLength: Int,
        maxLength: Int,
        saveAsPlaylist: Boolean = false,
        playlistName: String? = null
    ) {
        val scope = this.scope ?: return
        val allSongs = allSongsProvider?.invoke() ?: emptyList()
        val favoriteIds = favoriteSongIdsProvider?.invoke() ?: emptySet()

        scope.launch {
            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                val existingPlaylistNames = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .map { it.name.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()

                // Generate candidate pool using DailyMixManager logic
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongs,
                    favoriteSongIds = favoriteIds,
                    limit = 120
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        if (saveAsPlaylist) {
                            val resolvedPlaylistName = resolveAiPlaylistName(
                                requestedName = playlistName,
                                prompt = prompt,
                                existingNames = existingPlaylistNames
                            )
                            val songIds = generatedSongs.map { it.id }
                            playlistPreferencesRepository.createPlaylist(
                                name = resolvedPlaylistName,
                                songIds = songIds,
                                isAiGenerated = true
                            )
                            toastEmitter?.invoke("AI Playlist '$resolvedPlaylistName' created!")
                            dismissAiPlaylistSheet()
                        } else {
                            // Play immediately logic
                            dailyMixStateHolder.setDailyMixSongs(generatedSongs)
                            playSongsCallback?.invoke(generatedSongs, generatedSongs.first(), "AI: $prompt")
                            openPlayerSheetCallback?.invoke()
                            dismissAiPlaylistSheet()
                        }
                    } else {
                        _aiError.value = context.getString(R.string.ai_no_songs_found)
                    }
                }.onFailure { error ->
                    _aiError.value = resolveAiErrorMessage(error)
                }
            } finally {
                _isGeneratingAiPlaylist.value = false
            }
        }
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        val scope = this.scope ?: return
        val allSongs = allSongsProvider?.invoke() ?: emptyList()
        val favoriteIds = favoriteSongIdsProvider?.invoke() ?: emptySet()
        val currentDailyMixSongs = dailyMixStateHolder.dailyMixSongs.value

        scope.launch {
            if (prompt.isBlank()) {
                toastEmitter?.invoke(context.getString(R.string.ai_prompt_empty))
                return@launch
            }

            _isGeneratingAiPlaylist.value = true
            _aiError.value = null

            try {
                val desiredSize = currentDailyMixSongs.size.takeIf { it > 0 } ?: 25
                val minLength = (desiredSize * 0.6).toInt().coerceAtLeast(10)
                val maxLength = desiredSize.coerceAtLeast(20)
                
                val candidatePool = dailyMixManager.generateDailyMix(
                    allSongs = allSongs,
                    favoriteSongIds = favoriteIds,
                    limit = 100
                )

                val result = aiPlaylistGenerator.generate(
                    userPrompt = prompt,
                    allSongs = allSongs,
                    minLength = minLength,
                    maxLength = maxLength,
                    candidateSongs = candidatePool
                )

                result.onSuccess { generatedSongs ->
                    if (generatedSongs.isNotEmpty()) {
                        dailyMixStateHolder.setDailyMixSongs(generatedSongs)
                        toastEmitter?.invoke(context.getString(R.string.ai_daily_mix_updated))
                    } else {
                        toastEmitter?.invoke(context.getString(R.string.ai_no_songs_for_mix))
                    }
                }.onFailure { error ->
                    val detail = extractAiErrorDetail(error)
                    _aiError.value = resolveAiErrorMessage(error)
                    toastEmitter?.invoke(context.getString(R.string.could_not_update, detail))
                }
            } finally {
                _isGeneratingAiPlaylist.value = false
            }
        }
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        _isGeneratingMetadata.value = true
        return try {
            aiMetadataGenerator.generate(song, fields)
        } finally {
            _isGeneratingMetadata.value = false
        }
    }

    fun onCleared() {
        scope = null
        allSongsProvider = null
        favoriteSongIdsProvider = null
        toastEmitter = null
        playSongsCallback = null
        openPlayerSheetCallback = null
    }

    private fun resolveAiErrorMessage(error: Throwable): String {
        val detail = extractAiErrorDetail(error)
        return if (detail.contains("api key", ignoreCase = true)) {
            context.getString(R.string.ai_error_api_key)
        } else {
            context.getString(R.string.ai_error_generic, detail)
        }
    }

    private fun extractAiErrorDetail(error: Throwable): String {
        return listOf(error.message.orEmpty(), error.cause?.message.orEmpty())
            .map { raw ->
                raw.replace(Regex("^AI\\s*Error:\\s*", RegexOption.IGNORE_CASE), "").trim()
            }
            .firstOrNull { it.isNotBlank() }
            ?: "Unknown error"
    }

    private fun resolveAiPlaylistName(
        requestedName: String?,
        prompt: String,
        existingNames: Set<String>
    ): String {
        val normalizedExisting = existingNames.map { it.lowercase() }.toSet()
        val baseName = requestedName?.trim().takeUnless { it.isNullOrEmpty() }
            ?: generateShortAiTitle(prompt)

        var candidate = baseName.ifBlank { "AI Mix" }
        if (candidate.lowercase() !in normalizedExisting) {
            return candidate
        }

        var counter = 2
        while ("$candidate $counter".lowercase() in normalizedExisting) {
            counter++
        }
        return "$candidate $counter"
    }

    private fun generateShortAiTitle(prompt: String): String {
        val coreRequest = Regex("(?i)core request:\\s*([^.]*)")
            .find(prompt)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            .orEmpty()

        val source = if (coreRequest.isNotBlank()) coreRequest else prompt
        val normalizedText = source
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val tokens = normalizedText
            .split(" ")
            .filter { token ->
                token.length >= 3 && token !in titleStopWords
            }

        val compactTitle = when {
            tokens.size >= 2 -> tokens.take(2).joinToString(" ")
            tokens.size == 1 -> "${tokens.first()} mix"
            else -> fallbackTitleByKeyword(normalizedText)
        }

        return compactTitle
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            .split(" ")
            .joinToString(" ") { part ->
                part.replaceFirstChar { ch -> if (ch.isLowerCase()) ch.titlecase() else ch.toString() }
            }
            .take(26)
            .trim()
            .ifBlank { "AI Mix" }
    }

    private fun fallbackTitleByKeyword(text: String): String {
        return when {
            listOf("workout", "gym", "run", "cardio").any { text.contains(it) } -> "Workout Mix"
            listOf("focus", "study", "work", "productivity").any { text.contains(it) } -> "Focus Flow"
            listOf("chill", "relax", "calm", "lofi").any { text.contains(it) } -> "Chill Vibes"
            listOf("party", "dance", "club").any { text.contains(it) } -> "Party Mix"
            listOf("night", "late", "sleep").any { text.contains(it) } -> "Night Vibes"
            listOf("road", "trip", "drive").any { text.contains(it) } -> "Road Trip"
            listOf("romantic", "love").any { text.contains(it) } -> "Love Notes"
            listOf("sad", "melancholic").any { text.contains(it) } -> "Blue Hour"
            else -> "Fresh Mix"
        }
    }
}
