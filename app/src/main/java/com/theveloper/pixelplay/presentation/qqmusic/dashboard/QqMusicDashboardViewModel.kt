package com.theveloper.pixelplay.presentation.qqmusic.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.QqMusicPlaylistEntity
import com.theveloper.pixelplay.data.qqmusic.QqMusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

@HiltViewModel
class QqMusicDashboardViewModel @Inject constructor(
    private val repository: QqMusicRepository
) : ViewModel() {

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    val playlists: StateFlow<List<QqMusicPlaylistEntity>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _showSyncTypeDialog = MutableStateFlow(false)
    val showSyncTypeDialog: StateFlow<Boolean> = _showSyncTypeDialog.asStateFlow()

    val nickname: String?
        get() = repository.userNickname

    val avatarUrl: String?
        get() = repository.userAvatarUrl

    fun showSyncTypeDialog() {
        _showSyncTypeDialog.value = true
    }

    fun hideSyncTypeDialog() {
        _showSyncTypeDialog.value = false
    }

    fun syncAll(syncType: QqMusicRepository.PlaylistSyncType = QqMusicRepository.PlaylistSyncType.ALL) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            _showSyncTypeDialog.value = false
            repository.syncAllPlaylistsAndSongs(syncType).fold(
                onSuccess = { result ->
                    val msg = "Synced ${result.playlistCount} playlists, ${result.syncedSongCount} songs"
                    Timber.d("QQ Music sync success: $msg")
                    _syncState.value = SyncState.Success(msg)
                },
                onFailure = { error ->
                    Timber.e(error, "QQ Music sync failed")
                    _syncState.value = SyncState.Error(error.message ?: "Sync failed")
                }
            )
        }
    }

    fun syncPlaylist(playlistId: Long) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            repository.syncPlaylistSongs(playlistId).fold(
                onSuccess = { count ->
                    _syncState.value = SyncState.Success("Synced $count songs")
                },
                onFailure = { error ->
                    Timber.e(error, "QQ Music playlist sync failed")
                    _syncState.value = SyncState.Error(error.message ?: "Sync failed")
                }
            )
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylistById(playlistId)
        }
    }

    fun clearSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _loggedOut.value = true
        }
    }
}
