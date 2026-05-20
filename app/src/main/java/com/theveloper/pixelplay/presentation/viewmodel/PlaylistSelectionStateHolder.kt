package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Playlist
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
 * State holder for multi-selection functionality for playlists in LibraryScreen.
 * Manages playlist selection state with order preservation using a
 * list-of-playlists as the single source of truth; ids/count/mode are
 * derived views.
 *
 * Selection order is maintained — the first selected playlist is at index 0,
 * subsequent selections are appended in the order they were selected.
 */
@Singleton
class PlaylistSelectionStateHolder @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
) {

    // The ordered list of selected playlists is the only piece of mutable
    // state. ids/count/mode are projections of this flow, so observers
    // that read any subset of the public flows see values that all
    // originated from the same source emission — no cross-flow tearing is
    // possible. Mutations use StateFlow.update {} for atomic CAS, removing
    // the need for an external synchronized block.
    private val _selectedPlaylists = MutableStateFlow<List<Playlist>>(emptyList())

    /**
     * Immutable flow of selected playlists, preserving selection order.
     */
    val selectedPlaylists: StateFlow<List<Playlist>> = _selectedPlaylists.asStateFlow()

    /**
     * Set of selected playlist IDs for efficient lookup. Derived from
     * [selectedPlaylists] so the two views can never disagree.
     */
    val selectedPlaylistIds: StateFlow<Set<String>> = _selectedPlaylists
        .map { list -> list.mapTo(LinkedHashSet(list.size)) { it.id } }
        .stateIn(appScope, SharingStarted.Eagerly, emptySet())

    /**
     * Whether selection mode is currently active (at least one playlist selected).
     */
    val isSelectionMode: StateFlow<Boolean> = _selectedPlaylists
        .map { it.isNotEmpty() }
        .stateIn(appScope, SharingStarted.Eagerly, false)

    /**
     * Current count of selected playlists.
     */
    val selectedCount: StateFlow<Int> = _selectedPlaylists
        .map { it.size }
        .stateIn(appScope, SharingStarted.Eagerly, 0)

    /**
     * Toggles the selection state of a playlist.
     * If already selected, removes it. If not selected, adds it to the end.
     *
     * @param playlist The playlist to toggle
     */
    fun toggleSelection(playlist: Playlist) {
        _selectedPlaylists.update { current ->
            if (current.any { it.id == playlist.id }) {
                current.filterNot { it.id == playlist.id }
            } else {
                current + playlist
            }
        }
    }

    /**
     * Selects all playlists from the provided list.
     * Previously selected playlists that are in the new list maintain their position.
     * New playlists are appended in their list order.
     *
     * @param playlists The complete list of playlists to select
     */
    fun selectAll(playlists: List<Playlist>) {
        _selectedPlaylists.update { current ->
            val existingIds = current.mapTo(HashSet(current.size)) { it.id }
            val additions = playlists.filter { it.id !in existingIds }
            if (additions.isEmpty()) current else current + additions
        }
    }

    /**
     * Clears all selected playlists, exiting selection mode.
     */
    fun clearSelection() {
        _selectedPlaylists.value = emptyList()
    }

    /**
     * Checks if a playlist is currently selected.
     *
     * @param playlistId The ID of the playlist to check
     * @return True if the playlist is selected, false otherwise
     */
    fun isSelected(playlistId: String): Boolean {
        return selectedPlaylistIds.value.contains(playlistId)
    }

    /**
     * Gets the selection index (1-based) of a playlist for display purposes.
     * Returns null if the playlist is not selected.
     *
     * @param playlistId The ID of the playlist
     * @return 1-based selection index, or null if not selected
     */
    fun getSelectionIndex(playlistId: String): Int? {
        val index = _selectedPlaylists.value.indexOfFirst { it.id == playlistId }
        return if (index >= 0) index + 1 else null
    }

    /**
     * Removes a specific playlist from selection if it exists.
     * Useful when a playlist is deleted.
     *
     * @param playlistId The ID of the playlist to remove
     */
    fun removeFromSelection(playlistId: String) {
        _selectedPlaylists.update { current ->
            if (current.none { it.id == playlistId }) current
            else current.filterNot { it.id == playlistId }
        }
    }
}
