package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.di.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State holder for multi-selection functionality in LibraryScreen tabs.
 * Manages selection state with order preservation using a list-of-songs as
 * the single source of truth; ids/count/mode are derived views.
 *
 * Selection order is maintained — the first selected song is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class MultiSelectionStateHolder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
) {

    // The ordered list of selected songs is the only piece of mutable state.
    // ids/count/mode are projections of this flow, so observers that read
    // any subset of the public flows see values that all originated from
    // the same source emission — no cross-flow tearing is possible.
    // Mutations use StateFlow.update {} for atomic CAS, removing the need
    // for an external synchronized block.
    private val _selectedSongs = MutableStateFlow<List<Song>>(emptyList())

    /**
     * Immutable flow of selected songs, preserving selection order.
     */
    val selectedSongs: StateFlow<List<Song>> = _selectedSongs.asStateFlow()

    /**
     * Set of selected song IDs for efficient lookup. Derived from
     * [selectedSongs] so the two views can never disagree.
     */
    val selectedSongIds: StateFlow<Set<String>> = _selectedSongs
        .map { songs -> songs.mapTo(LinkedHashSet(songs.size)) { it.id } }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    /**
     * Whether selection mode is currently active (at least one song selected).
     */
    val isSelectionMode: StateFlow<Boolean> = _selectedSongs
        .map { it.isNotEmpty() }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    /**
     * Current count of selected songs.
     */
    val selectedCount: StateFlow<Int> = _selectedSongs
        .map { it.size }
        .stateIn(appScope, SharingStarted.Eagerly, 0)

    /**
     * Toggles the selection state of a song.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param song The song to toggle
     */
    fun toggleSelection(song: Song) {
        _selectedSongs.update { current ->
            if (current.any { it.id == song.id }) {
                current.filterNot { it.id == song.id }
            } else {
                current + song
            }
        }
    }

    /**
     * Selects all songs from the provided list.
     * Previously selected songs that are in the new list maintain their position.
     * New songs are appended in their list order.
     *
     * @param songs The complete list of songs to select
     */
    fun selectAll(songs: List<Song>) {
        _selectedSongs.update { current ->
            val existingIds = current.mapTo(HashSet(current.size)) { it.id }
            val additions = songs.filter { it.id !in existingIds }
            if (additions.isEmpty()) current else current + additions
        }
    }

    /**
     * Clears all selected songs, exiting selection mode.
     */
    fun clearSelection() {
        _selectedSongs.value = emptyList()
    }

    /**
     * Checks if a song is currently selected.
     *
     * @param songId The ID of the song to check
     * @return True if the song is selected, false otherwise
     */
    fun isSelected(songId: String): Boolean {
        return _selectedSongs.value.any { it.id == songId }
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
        _selectedSongs.update { current ->
            if (current.none { it.id == songId }) current
            else current.filterNot { it.id == songId }
        }
    }
}
