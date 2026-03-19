package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.WearLibraryRepository
import com.theveloper.pixelplay.data.WearLocalPlayerRepository
import com.theveloper.pixelplay.data.WearOutputTarget
import com.theveloper.pixelplay.data.WearPlaybackController
import com.theveloper.pixelplay.data.WearStateRepository
import com.theveloper.pixelplay.shared.WearLibraryItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for library browse screens on the watch.
 * Manages loading state, browse results, and playback-from-context actions.
 */
@HiltViewModel
class WearBrowseViewModel @Inject constructor(
    private val libraryRepository: WearLibraryRepository,
    private val playbackController: WearPlaybackController,
    private val stateRepository: WearStateRepository,
    private val localPlayerRepository: WearLocalPlayerRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val uiState: StateFlow<BrowseUiState> = _uiState.asStateFlow()

    /** Track current params for refresh */
    private var currentBrowseType: String? = null
    private var currentContextId: String? = null

    /**
     * Load items from the phone for the given browse type and optional context.
     */
    fun loadItems(browseType: String, contextId: String? = null) {
        currentBrowseType = browseType
        currentContextId = contextId
        viewModelScope.launch {
            _uiState.value = BrowseUiState.Loading
            try {
                val response = libraryRepository.browse(browseType, contextId)
                val errorMsg = response.error
                if (errorMsg != null) {
                    _uiState.value = BrowseUiState.Error(errorMsg)
                } else {
                    _uiState.value = BrowseUiState.Success(response.items)
                }
            } catch (e: Exception) {
                _uiState.value = BrowseUiState.Error(e.message ?: "Connection error")
            }
        }
    }

    /**
     * Play a song within its context (album, artist, playlist, etc.).
     * Sets up the full queue on the phone so next/previous work correctly.
     */
    fun playFromContext(songId: String, contextType: String, contextId: String?) {
        prepareForPhonePlayback()
        playbackController.playFromContext(songId, contextType, contextId)
    }

    /**
     * Insert song as next item in current queue.
     */
    fun playNextFromContext(songId: String, contextType: String, contextId: String?) {
        playbackController.playNextFromContext(songId, contextType, contextId)
    }

    /**
     * Append song to queue end.
     */
    fun addToQueueFromContext(songId: String, contextType: String, contextId: String?) {
        playbackController.addToQueueFromContext(songId, contextType, contextId)
    }

    fun playQueueIndex(index: Int) {
        prepareForPhonePlayback()
        playbackController.playQueueIndex(index)
    }

    /**
     * Refresh current browse data by invalidating cache and reloading.
     */
    fun refresh() {
        val type = currentBrowseType ?: return
        libraryRepository.invalidateCache()
        loadItems(type, currentContextId)
    }

    private fun prepareForPhonePlayback() {
        stateRepository.setOutputTarget(WearOutputTarget.PHONE)
        if (localPlayerRepository.localPlayerState.value.isPlaying) {
            localPlayerRepository.pause()
        }
        playbackController.requestPhoneVolumeState()
    }
}

/**
 * UI state for browse screens.
 */
sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Success(val items: List<WearLibraryItem>) : BrowseUiState
    data class Error(val message: String) : BrowseUiState
}
