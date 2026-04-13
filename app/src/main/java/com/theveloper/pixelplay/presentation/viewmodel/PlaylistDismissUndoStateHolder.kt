package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@ViewModelScoped
class PlaylistDismissUndoStateHolder @Inject constructor() {
    private var dismissUndoTimerJob: Job? = null
    private var undoObserverJob: Job? = null

    fun dismissPlaylistAndShowUndo(
        scope: CoroutineScope,
        currentSong: Song?,
        queue: List<Song>,
        queueName: String,
        position: Long,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        disconnectRemoteIfNeeded: suspend () -> Unit,
        clearPlayback: () -> Unit,
        clearStablePlaybackState: () -> Unit,
        setCurrentPosition: (Long) -> Unit,
        setSheetVisible: (Boolean) -> Unit
    ) {
        scope.launch {
            if (currentSong == null && queue.isEmpty()) return@launch

            updateUiState {
                it.copy(
                    dismissedSong = currentSong,
                    dismissedQueue = queue.toImmutableList(),
                    dismissedQueueName = queueName,
                    dismissedPosition = position,
                    showDismissUndoBar = true
                )
            }

            disconnectRemoteIfNeeded()
            clearPlayback()
            clearStablePlaybackState()

            updateUiState {
                it.copy(
                    currentPlaybackQueue = persistentListOf(),
                    currentQueueSourceName = ""
                )
            }
            setCurrentPosition(0L)
            setSheetVisible(false)

            dismissUndoTimerJob?.cancel()
            dismissUndoTimerJob = scope.launch {
                delay(getUiState().undoBarVisibleDuration)
                if (getUiState().showDismissUndoBar) {
                    updateUiState {
                        it.copy(
                            showDismissUndoBar = false,
                            dismissedSong = null,
                            dismissedQueue = persistentListOf()
                        )
                    }
                }
            }
        }
    }

    fun hideDismissUndoBar(
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit)
    ) {
        dismissUndoTimerJob?.cancel()
        updateUiState {
            it.copy(
                showDismissUndoBar = false,
                dismissedSong = null,
                dismissedQueue = persistentListOf(),
                dismissedQueueName = "",
                dismissedPosition = 0L
            )
        }
    }

    fun observeUndoStateAgainstPlayback(
        scope: CoroutineScope,
        currentSongIdFlow: Flow<String?>,
        getUiState: () -> PlayerUiState,
        onHideDismissUndoBar: () -> Unit
    ) {
        undoObserverJob?.cancel()
        undoObserverJob = scope.launch {
            currentSongIdFlow
                .map { it }
                .distinctUntilChanged()
                .collect { newSongId ->
                    val uiState = getUiState()
                    if (uiState.showDismissUndoBar &&
                        newSongId != null &&
                        newSongId != uiState.dismissedSong?.id
                    ) {
                        onHideDismissUndoBar()
                    }
                }
        }
    }

    fun undoDismissPlaylist(
        scope: CoroutineScope,
        getUiState: () -> PlayerUiState,
        updateUiState: (((PlayerUiState) -> PlayerUiState) -> Unit),
        playSongs: (songs: List<Song>, startSong: Song, queueName: String) -> Unit,
        seekTo: (Long) -> Unit,
        setSheetVisible: (Boolean) -> Unit,
        setSheetCollapsed: () -> Unit,
        emitToast: suspend (String) -> Unit
    ) {
        scope.launch {
            val uiState = getUiState()
            val songToRestore = uiState.dismissedSong
            val queueToRestore = uiState.dismissedQueue
            val queueNameToRestore = uiState.dismissedQueueName
            val positionToRestore = uiState.dismissedPosition

            if (songToRestore != null && queueToRestore.isNotEmpty()) {
                playSongs(queueToRestore.toList(), songToRestore, queueNameToRestore)
                delay(500)
                seekTo(positionToRestore)

                updateUiState {
                    it.copy(
                        showDismissUndoBar = false,
                        dismissedSong = null,
                        dismissedQueue = persistentListOf(),
                        dismissedQueueName = "",
                        dismissedPosition = 0L
                    )
                }
                setSheetVisible(true)
                setSheetCollapsed()
                emitToast("Playlist restored")
            } else {
                updateUiState { it.copy(showDismissUndoBar = false) }
            }
        }
    }

    fun onCleared() {
        dismissUndoTimerJob?.cancel()
        undoObserverJob?.cancel()
    }
}
