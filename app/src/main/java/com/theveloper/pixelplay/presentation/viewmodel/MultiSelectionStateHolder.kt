package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.ZipShareHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_ALBUM_BATCH_SELECTION = 6

/**
 * Callbacks supplied by [PlayerViewModel] so the batch-selection actions can reach
 * ViewModel-owned playback collaborators (queue dispatch, player sheet, toasts,
 * favorites snapshot) and the ViewModel's [CoroutineScope] without
 * [MultiSelectionStateHolder] depending on the ViewModel.
 * Mirrors the lambda-callback pattern already used by [SongRemovalCallbacks].
 */
class SelectionActionCallbacks(
    val scope: CoroutineScope,
    val playSongs: (songs: List<Song>, startSong: Song, queueName: String) -> Unit,
    val addSongToQueue: (Song) -> Unit,
    val addSongNextToQueue: (Song) -> Unit,
    val showSheet: () -> Unit,
    val emitToast: suspend (String) -> Unit,
    val favoriteSongIds: () -> Set<String>,
)

private data class ResolvedAlbumSelection(
    val albums: List<Album>,
    val songs: List<Song>,
    val wasTrimmed: Boolean
)

/**
 * State holder for multi-selection functionality in LibraryScreen tabs.
 * Manages selection state with order preservation using a LinkedHashSet internally,
 * plus the batch actions performed on the current selection (play/queue/play-next
 * for songs, albums and genres, favorites toggling, and ZIP sharing).
 *
 * Selection order is maintained - the first selected song is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class MultiSelectionStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    @param:ApplicationContext private val context: Context,
) {

    // Internal mutable state - uses List to preserve selection order
    // LinkedHashSet behavior is enforced via toggle logic
    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())
    
    /**
     * Immutable flow of selected songs, preserving selection order.
     */
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs.asStateFlow()
    
    /**
     * Set of selected song IDs for efficient lookup.
     */
    private val _selectedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongIds.asStateFlow()
    
    /**
     * Whether selection mode is currently active (at least one song selected).
     */
    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()
    
    /**
     * Current count of selected songs.
     */
    private val _selectedCount = MutableStateFlow(0)
    val selectedCount: StateFlow<Int> = _selectedCount.asStateFlow()

    /**
     * Toggles the selection state of a song.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param song The song to toggle
     */
    fun toggleSelection(song: Song) {
        val currentList = _selectedSongs.value.toMutableList()
        val currentIds = _selectedSongIds.value.toMutableSet()
        
        if (currentIds.contains(song.id)) {
            // Remove from selection
            currentList.removeAll { it.id == song.id }
            currentIds.remove(song.id)
        } else {
            // Add to selection (preserving order)
            currentList.add(song)
            currentIds.add(song.id)
        }
        
        updateState(currentList, currentIds)
    }

    /**
     * Selects all songs from the provided list.
     * Previously selected songs that are in the new list maintain their position.
     * New songs are appended in their list order.
     *
     * @param songs The complete list of songs to select
     */
    fun selectAll(songs: List<Song>) {
        val currentIds = _selectedSongIds.value
        val currentList = _selectedSongs.value.toMutableList()
        
        // Add songs that aren't already selected
        songs.forEach { song ->
            if (!currentIds.contains(song.id)) {
                currentList.add(song)
            }
        }
        
        val newIds = currentList.map { it.id }.toSet()
        updateState(currentList, newIds)
    }

    /**
     * Clears all selected songs, exiting selection mode.
     */
    fun clearSelection() {
        updateState(emptyList(), emptySet())
    }

    /**
     * Checks if a song is currently selected.
     *
     * @param songId The ID of the song to check
     * @return True if the song is selected, false otherwise
     */
    fun isSelected(songId: String): Boolean {
        return _selectedSongIds.value.contains(songId)
    }

    /**
     * Gets the selection index (1-based) of a song for display purposes.
     * Returns null if the song is not selected.
     *
     * @param songId The ID of the song
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(songId: String): Int? {
        val index = _selectedSongs.value.indexOfFirst { it.id == songId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Removes a specific song from selection if it exists.
     * Useful when a song is deleted from the library.
     *
     * @param songId The ID of the song to remove
     */
    fun removeFromSelection(songId: String) {
        if (!_selectedSongIds.value.contains(songId)) return
        
        val currentList = _selectedSongs.value.filter { it.id != songId }
        val currentIds = _selectedSongIds.value - songId
        updateState(currentList, currentIds)
    }

    /**
     * Updates all state flows atomically.
     */
    private fun updateState(songs: List<Song>, ids: Set<String>) {
        _selectedSongs.value = songs
        _selectedSongIds.value = ids
        _selectedCount.value = songs.size
        _isSelectionMode.value = songs.isNotEmpty()
    }

    // =====================================================
    // Batch actions on the current selection
    // (moved from PlayerViewModel; it only supplies collaborators
    // via SelectionActionCallbacks)
    // =====================================================

    /**
     * Plays all selected songs, preserving their selection order.
     * Clears selection after starting playback.
     */
    fun playSelectedSongs(songs: List<Song>, callbacks: SelectionActionCallbacks) {
        if (songs.isEmpty()) return
        callbacks.playSongs(songs, songs.first(), "Selected Songs")
        clearSelection()
    }

    /**
     * Adds all selected songs to the end of the queue.
     * Clears selection after adding.
     */
    fun addSelectedToQueue(songs: List<Song>, callbacks: SelectionActionCallbacks) {
        songs.forEach(callbacks.addSongToQueue)
        callbacks.scope.launch {
            val n = songs.size
            callbacks.emitToast(
                context.resources.getQuantityString(R.plurals.player_view_model_n_songs_added_to_queue, n, n),
            )
        }
        clearSelection()
    }

    /**
     * Adds all selected songs to play next, preserving selection order.
     * Songs are inserted in reverse order so they play in the correct sequence.
     * Clears selection after adding.
     */
    fun addSelectedAsNext(songs: List<Song>, callbacks: SelectionActionCallbacks) {
        songs.reversed().forEach(callbacks.addSongNextToQueue)
        callbacks.scope.launch {
            val n = songs.size
            callbacks.emitToast(
                context.resources.getQuantityString(R.plurals.player_view_model_n_songs_will_play_next, n, n),
            )
        }
        clearSelection()
    }

    fun playSelectedAlbums(albums: List<Album>, callbacks: SelectionActionCallbacks) =
        launchAlbumSelectionAction(
            albums = albums,
            callbacks = callbacks,
            emptySelectionMessage = { context.getString(R.string.player_view_model_no_playable_songs_in_albums) },
            failureLogMessage = "Error playing selected albums",
            failureMessage = { context.getString(R.string.player_view_model_could_not_queue_albums) },
        ) { selection ->
            val queueName = if (selection.albums.size == 1) {
                selection.albums.first().title
            } else {
                context.getString(R.string.player_view_model_queue_name_selected_albums)
            }

            callbacks.playSongs(selection.songs, selection.songs.first(), queueName)
            callbacks.showSheet()

            if (selection.wasTrimmed) {
                context.getString(R.string.player_view_model_only_first_n_albums_queued, MAX_ALBUM_BATCH_SELECTION)
            } else {
                context.getString(
                    R.string.player_view_model_albums_queued_format,
                    selection.albums.size,
                    selection.songs.size,
                )
            }
        }

    fun addSelectedAlbumsAsNext(albums: List<Album>, callbacks: SelectionActionCallbacks) =
        launchAlbumSelectionAction(
            albums = albums,
            callbacks = callbacks,
            emptySelectionMessage = { "No playable songs found in selected albums" },
            failureLogMessage = "Error adding selected albums as next",
            failureMessage = { "Could not add selected albums as next" },
        ) { selection ->
            selection.songs
                .asReversed()
                .forEach(callbacks.addSongNextToQueue)

            if (selection.wasTrimmed) {
                "Only the first $MAX_ALBUM_BATCH_SELECTION albums were added as next"
            } else {
                "${selection.albums.size} albums will play next"
            }
        }

    fun addSelectedAlbumsToQueue(albums: List<Album>, callbacks: SelectionActionCallbacks) =
        launchAlbumSelectionAction(
            albums = albums,
            callbacks = callbacks,
            emptySelectionMessage = { "No playable songs found in selected albums" },
            failureLogMessage = "Error adding selected albums to queue",
            failureMessage = { "Could not add selected albums to queue" },
        ) { selection ->
            selection.songs.forEach(callbacks.addSongToQueue)

            if (selection.wasTrimmed) {
                "Only the first $MAX_ALBUM_BATCH_SELECTION albums were added to queue"
            } else {
                "${selection.albums.size} albums added to queue"
            }
        }

    /**
     * Shared shape of the album batch actions: resolve the (possibly trimmed)
     * selection on IO, bail out with a toast when nothing is playable, run the
     * action, and toast the message it returns.
     */
    private fun launchAlbumSelectionAction(
        albums: List<Album>,
        callbacks: SelectionActionCallbacks,
        emptySelectionMessage: () -> String,
        failureLogMessage: String,
        failureMessage: () -> String,
        action: suspend (ResolvedAlbumSelection) -> String,
    ) {
        if (albums.isEmpty()) return
        callbacks.scope.launch {
            try {
                val resolvedSelection = resolveSelectedAlbumSongs(albums)
                if (resolvedSelection.songs.isEmpty()) {
                    callbacks.emitToast(emptySelectionMessage())
                    return@launch
                }

                callbacks.emitToast(action(resolvedSelection))
            } catch (e: Exception) {
                Timber.e(e, failureLogMessage)
                callbacks.emitToast(failureMessage())
            }
        }
    }

    /**
     * Adds all selected songs to favorites.
     * Clears selection after liking.
     */
    fun likeSelectedSongs(songs: List<Song>, callbacks: SelectionActionCallbacks) =
        updateFavoritesForSelection(songs, callbacks, makeFavorite = true)

    /**
     * Removes all selected songs from favorites.
     * Clears selection after unliking.
     */
    fun unlikeSelectedSongs(songs: List<Song>, callbacks: SelectionActionCallbacks) =
        updateFavoritesForSelection(songs, callbacks, makeFavorite = false)

    private fun updateFavoritesForSelection(
        songs: List<Song>,
        callbacks: SelectionActionCallbacks,
        makeFavorite: Boolean,
    ) {
        callbacks.scope.launch {
            val favIds = callbacks.favoriteSongIds().toMutableSet()
            var changedCount = 0
            songs.forEach { song ->
                if (favIds.contains(song.id) != makeFavorite) {
                    musicRepository.setFavoriteStatus(song.id, makeFavorite)
                    if (makeFavorite) favIds.add(song.id) else favIds.remove(song.id)
                    changedCount++
                }
            }
            val message = when {
                changedCount > 0 && makeFavorite -> context.resources.getQuantityString(
                    R.plurals.player_view_model_n_songs_added_to_favorites, changedCount, changedCount,
                )
                changedCount > 0 -> context.resources.getQuantityString(
                    R.plurals.player_view_model_n_songs_removed_from_favorites, changedCount, changedCount,
                )
                makeFavorite -> context.getString(R.string.player_view_model_all_songs_already_in_favorites)
                else -> context.getString(R.string.player_view_model_no_songs_were_in_favorites)
            }
            callbacks.emitToast(message)
            clearSelection()
        }
    }

    /**
     * Shares all selected songs as a ZIP file.
     * Clears selection after initiating share.
     */
    fun shareSelectedAsZip(songs: List<Song>, callbacks: SelectionActionCallbacks) {
        callbacks.scope.launch {
            callbacks.emitToast(context.getString(R.string.player_view_model_creating_zip))

            val result = ZipShareHelper.createAndShareZip(context, songs)

            result.onSuccess {
                clearSelection()
            }.onFailure { error ->
                callbacks.emitToast(
                    context.getString(R.string.player_view_model_share_zip_failed_format, error.localizedMessage ?: ""),
                )
                Timber.e(error, "Failed to share selection as ZIP")
            }
        }
    }

    fun playSelectedGenres(genres: List<Genre>, callbacks: SelectionActionCallbacks) =
        launchGenreSelectionAction(
            genres = genres,
            callbacks = callbacks,
            failureLogMessage = "Error playing selected genres",
            failureMessage = "Could not play selected genres",
        ) { songs ->
            callbacks.playSongs(songs, songs.first(), "Selected Genres")
            callbacks.showSheet()
            null
        }

    fun addSelectedGenresToQueue(genres: List<Genre>, callbacks: SelectionActionCallbacks) =
        launchGenreSelectionAction(
            genres = genres,
            callbacks = callbacks,
            failureLogMessage = "Error adding selected genres to queue",
            failureMessage = "Could not add selected genres to queue",
        ) { songs ->
            songs.forEach(callbacks.addSongToQueue)
            val n = songs.size
            context.resources.getQuantityString(R.plurals.player_view_model_n_songs_added_to_queue, n, n)
        }

    fun addSelectedGenresAsNext(genres: List<Genre>, callbacks: SelectionActionCallbacks) =
        launchGenreSelectionAction(
            genres = genres,
            callbacks = callbacks,
            failureLogMessage = "Error adding selected genres as next",
            failureMessage = "Could not add selected genres as next",
        ) { songs ->
            songs.reversed().forEach(callbacks.addSongNextToQueue)
            val n = songs.size
            context.resources.getQuantityString(R.plurals.player_view_model_n_songs_will_play_next, n, n)
        }

    /**
     * Shared shape of the genre batch actions: resolve the songs on IO, bail out
     * with a toast when nothing is playable, run the action, and toast the message
     * it returns (null = no success toast).
     */
    private fun launchGenreSelectionAction(
        genres: List<Genre>,
        callbacks: SelectionActionCallbacks,
        failureLogMessage: String,
        failureMessage: String,
        action: suspend (List<Song>) -> String?,
    ) {
        if (genres.isEmpty()) return
        callbacks.scope.launch {
            try {
                val songs = getSongsForGenres(genres)
                if (songs.isEmpty()) {
                    callbacks.emitToast(context.getString(R.string.player_view_model_no_playable_songs_in_genres))
                    return@launch
                }

                action(songs)?.let { callbacks.emitToast(it) }
            } catch (e: Exception) {
                Timber.e(e, failureLogMessage)
                callbacks.emitToast(failureMessage)
            }
        }
    }

    suspend fun getSongsForGenres(genres: List<Genre>): List<Song> {
        return withContext(Dispatchers.IO) {
            genres.flatMap { genre ->
                musicRepository.getMusicByGenre(genre.name).first()
            }.distinctBy { it.id }
        }
    }

    suspend fun getSongsForAlbums(albums: List<Album>): List<Song> {
        return resolveSelectedAlbumSongs(albums).songs
    }

    private suspend fun resolveSelectedAlbumSongs(albums: List<Album>): ResolvedAlbumSelection {
        val albumsToProcess = albums.take(MAX_ALBUM_BATCH_SELECTION)
        val wasTrimmed = albums.size > albumsToProcess.size

        val songs = withContext(Dispatchers.IO) {
            buildList {
                albumsToProcess.forEach { album ->
                    val albumSongs = musicRepository.getSongsForAlbum(album.id).first()
                    if (albumSongs.isNotEmpty()) {
                        addAll(sortSongsForAlbumSelection(albumSongs))
                    }
                }
            }
        }

        return ResolvedAlbumSelection(
            albums = albumsToProcess,
            songs = songs,
            wasTrimmed = wasTrimmed
        )
    }

    private fun sortSongsForAlbumSelection(songs: List<Song>): List<Song> {
        return songs.sortedWith(
            compareBy<Song> { it.discNumber ?: 1 }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.getDefault()) }
        )
    }
}
