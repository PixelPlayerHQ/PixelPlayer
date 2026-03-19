package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.repository.NoLyricsFoundException
import com.theveloper.pixelplay.utils.LyricsUtils
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Callback interface for lyrics loading results.
 * Used to update StablePlayerState in PlayerViewModel.
 */
interface LyricsLoadCallback {
    fun onLoadingStarted(songId: String)
    fun onLyricsLoaded(songId: String, lyrics: Lyrics?)
}

/**
 * Manages lyrics loading, search state, and sync offset.
 * Extracted from PlayerViewModel to improve modularity.
 */
@Singleton
class LyricsStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val songMetadataEditor: SongMetadataEditor
) {
    private var scope: CoroutineScope? = null
    private var loadingJob: Job? = null
    private var loadCallback: LyricsLoadCallback? = null

    // Sync offset per song in milliseconds
    private val _currentSongSyncOffset = MutableStateFlow(0)
    val currentSongSyncOffset: StateFlow<Int> = _currentSongSyncOffset.asStateFlow()

    // Lyrics search UI state
    private val _searchUiState = MutableStateFlow<LyricsSearchUiState>(LyricsSearchUiState.Idle)
    val searchUiState: StateFlow<LyricsSearchUiState> = _searchUiState.asStateFlow()

    // Event to notify ViewModel of song updates (e.g. lyrics added)
    private val _songUpdates = kotlinx.coroutines.flow.MutableSharedFlow<Pair<Song, Lyrics?>>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val songUpdates = _songUpdates.asSharedFlow()

    // Event for Toasts
    private val _messageEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    val messageEvents = _messageEvents.asSharedFlow()

    /**
     * Initialize with coroutine scope and callback from ViewModel.
     */
    fun initialize(
        coroutineScope: CoroutineScope,
        callback: LyricsLoadCallback,
        stablePlayerState: StateFlow<com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState>
    ) {
        scope = coroutineScope
        loadCallback = callback

        coroutineScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { songId ->
                    if (songId != null) {
                        updateSyncOffsetForSong(songId)
                    }
                }
        }
    }

    /**
     * Load lyrics for a song.
     * @param song The song to load lyrics for
     * @param sourcePreference The preferred source for lyrics
     */
    fun loadLyricsForSong(song: Song, sourcePreference: LyricsSourcePreference) {
        loadingJob?.cancel()
        val targetSongId = song.id

        loadingJob = scope?.launch {
            loadCallback?.onLoadingStarted(targetSongId)

            val fetchedLyrics = try {
                withContext(Dispatchers.IO) {
                    musicRepository.getLyrics(
                        song = song,
                        sourcePreference = sourcePreference
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }

            loadCallback?.onLyricsLoaded(targetSongId, fetchedLyrics)
        }
    }

    /**
     * Cancel any ongoing lyrics loading.
     */
    fun cancelLoading() {
        loadingJob?.cancel()
    }

    /**
     * Set sync offset for a song.
     */
    fun setSyncOffset(songId: String, offsetMs: Int) {
        scope?.launch {
            userPreferencesRepository.setLyricsSyncOffset(songId, offsetMs)
            _currentSongSyncOffset.value = offsetMs
        }
    }

    /**
     * Update sync offset from song ID (called when song changes).
     */
    suspend fun updateSyncOffsetForSong(songId: String) {
        val offset = userPreferencesRepository.getLyricsSyncOffset(songId)
        _currentSongSyncOffset.value = offset
    }

    /**
     * Set the lyrics search UI state.
     */
    fun setSearchState(state: LyricsSearchUiState) {
        _searchUiState.value = state
    }

    /**
     * Reset the lyrics search state to idle.
     */
    fun resetSearchState() {
        _searchUiState.value = LyricsSearchUiState.Idle
    }

    /**
     * Fetch lyrics for the given song from the remote service.
     */
    fun fetchLyricsForSong(
        song: Song,
        forcePickResults: Boolean,
        contextHelper: (Int) -> String
    ) {
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading

            // Embedded lyrics must be prioritized for manual fetch too.
            val embeddedRaw = withContext(Dispatchers.IO) { readEmbeddedLyricsFromFile(song) }
            if (!embeddedRaw.isNullOrBlank()) {
                val embeddedParsed = LyricsUtils.parseLyrics(embeddedRaw)
                if (hasValidLyrics(embeddedParsed)) {
                    val parsed = embeddedParsed.copy(areFromRemote = false)
                    _searchUiState.value = LyricsSearchUiState.Success(parsed)

                    val songId = song.id.toLongOrNull()
                    if (songId != null) {
                        musicRepository.updateLyrics(songId, embeddedRaw)
                    }

                    _songUpdates.emit(song.copy(lyrics = embeddedRaw) to parsed)
                    _messageEvents.emit(contextHelper(R.string.lyrics_embedded_already_available))
                    return@launch
                }
            }

            if (forcePickResults) {
                musicRepository.searchRemoteLyrics(song)
                    .onSuccess { (query, results) ->
                        _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                    }
                    .onFailure { error ->
                        handleError(error)
                    }
            } else {
                musicRepository.getLyricsFromRemote(song)
                    .onSuccess { (lyrics, rawLyrics) ->
                        _searchUiState.value = LyricsSearchUiState.Success(lyrics)
                        val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(song, rawLyrics)
                        val updatedSong = song.withPersistedLyrics(rawLyrics, refreshedAlbumArtUri)
                        _songUpdates.emit(updatedSong to lyrics)
                    }
                    .onFailure { error ->
                        if (error is NoLyricsFoundException) {
                            // Fallback to search
                            musicRepository.searchRemoteLyrics(song)
                                .onSuccess { (query, results) ->
                                    _searchUiState.value = LyricsSearchUiState.PickResult(query, results)
                                }
                                .onFailure { searchError -> handleError(searchError) }
                        } else {
                            handleError(error)
                        }
                    }
            }
        }
    }

    /**
     * Manual search by query.
     */
    fun searchLyricsManually(title: String, artist: String?) {
        if (title.isBlank()) return
        loadingJob?.cancel()
        loadingJob = scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Loading
            musicRepository.searchRemoteLyricsByQuery(title, artist)
                .onSuccess { (q, results) ->
                    _searchUiState.value = LyricsSearchUiState.PickResult(q, results)
                }
                .onFailure { error -> handleError(error) }
        }
    }

    /**
     * Accept a search result.
     */
    fun acceptLyricsSearchResult(result: LyricsSearchResult, currentSong: Song) {
        scope?.launch {
            _searchUiState.value = LyricsSearchUiState.Success(result.lyrics)

            // 1. Update DB cache
            currentSong.id.toLongOrNull()?.let { songId ->
                musicRepository.updateLyrics(songId, result.rawLyrics)
            }

            // 2. Attempt metadata write-back to the audio file
            val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, result.rawLyrics)
            val updatedSong = currentSong.withPersistedLyrics(result.rawLyrics, refreshedAlbumArtUri)

            // 3. Notify
            _songUpdates.emit(updatedSong to result.lyrics)
        }
    }

    /**
     * Import from file.
     */
    fun importLyricsFromFile(songId: Long, lyricsContent: String, currentSong: Song?) {
        scope?.launch {
            musicRepository.updateLyrics(songId, lyricsContent)

            if (currentSong != null && currentSong.id.toLongOrNull() == songId) {
                val parsedLyrics = LyricsUtils.parseLyrics(lyricsContent)
                val refreshedAlbumArtUri = persistLyricsToFileMetadataIfPossible(currentSong, lyricsContent)
                val updatedSong = currentSong.withPersistedLyrics(lyricsContent, refreshedAlbumArtUri)
                _songUpdates.emit(updatedSong to parsedLyrics.takeIf(::hasValidLyrics))
            }

            _messageEvents.emit("Lyrics imported successfully!")
        }
    }

    fun resetLyrics(songId: Long) {
        resetSearchState()
        scope?.launch {
            musicRepository.resetLyrics(songId)
            _songUpdates.emit(Song.emptySong().copy(id = songId.toString()) to null)
        }
    }

    fun resetAllLyrics() {
        resetSearchState()
        scope?.launch {
            musicRepository.resetAllLyrics()
        }
    }

    private fun handleError(error: Throwable) {
        _searchUiState.value = if (error is NoLyricsFoundException) {
            LyricsSearchUiState.NotFound("Lyrics not found")
        } else {
            LyricsSearchUiState.Error(error.message ?: "Unknown error")
        }
    }

    private fun hasValidLyrics(lyrics: Lyrics?): Boolean {
        if (lyrics == null) return false
        return !lyrics.synced.isNullOrEmpty() || !lyrics.plain.isNullOrEmpty()
    }

    private fun readEmbeddedLyricsFromFile(song: Song): String? {
        return runCatching {
            AudioMetadataReader.read(File(song.path))
                ?.lyrics
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private suspend fun persistLyricsToFileMetadataIfPossible(song: Song, rawLyrics: String): String? {
        val songId = song.id.toLongOrNull() ?: return null
        val normalizedLyrics = rawLyrics.trim()
        if (normalizedLyrics.isBlank()) return null

        return withContext(Dispatchers.IO) {
            val existingArtwork = runCatching {
                AudioMetadataReader.read(File(song.path))?.artwork
            }.getOrNull()

            val coverArtUpdate = existingArtwork?.let { artwork ->
                CoverArtUpdate(
                    bytes = artwork.bytes,
                    mimeType = artwork.mimeType ?: "image/jpeg"
                )
            }

            runCatching {
                songMetadataEditor.editSongMetadata(
                    songId = songId,
                    newTitle = song.title,
                    newArtist = song.artist,
                    newAlbum = song.album,
                    newGenre = song.genre ?: "",
                    newLyrics = normalizedLyrics,
                    newTrackNumber = song.trackNumber,
                    coverArtUpdate = coverArtUpdate
                )
            }.getOrNull()?.updatedAlbumArtUri
        }
    }

    fun onCleared() {
        loadingJob?.cancel()
        scope = null
        loadCallback = null
    }
}

internal fun Song.withPersistedLyrics(rawLyrics: String, refreshedAlbumArtUri: String?): Song {
    return copy(
        lyrics = rawLyrics,
        // Lyrics writes can refresh the cached cover-art file path. Carry it forward immediately
        // so the full player doesn't keep rendering a deleted image URI until the next app reload.
        albumArtUriString = refreshedAlbumArtUri ?: albumArtUriString
    )
}
