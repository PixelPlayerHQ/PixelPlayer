package com.theveloper.pixelplay.presentation.navidrome.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.database.NavidromePlaylistEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.cloud.NavidromeRepository
import com.theveloper.pixelplay.data.worker.NavidromeSyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NavidromeDashboardViewModel @Inject constructor(
    private val repository: NavidromeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val playlists: StateFlow<List<NavidromePlaylistEntity>> = repository.getPlaylists()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncProgress = MutableStateFlow<Float?>(null)
    val syncProgress: StateFlow<Float?> = _syncProgress.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _selectedPlaylistSongs = MutableStateFlow<List<Song>>(emptyList())
    val selectedPlaylistSongs: StateFlow<List<Song>> = _selectedPlaylistSongs.asStateFlow()

    val username: String? get() = repository.username
    val serverUrl: String? get() = repository.serverUrl
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow
    val lastSyncTime: Long get() = repository.lastFullSyncTime

    init {
        observeSyncWorker()
        // Auto sync full library (songs + playlists) if it's been more than 24 hours
        val lastSync = repository.lastFullSyncTime
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSync > NavidromeRepository.SYNC_THRESHOLD_MS) {
            syncAllPlaylistsAndSongs()
        }
    }

    private fun observeSyncWorker() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME_SYNC_ALL).collect { workInfos ->
                val workInfo = workInfos.firstOrNull() ?: return@collect
                
                when (workInfo.state) {
                    WorkInfo.State.RUNNING -> {
                        _isSyncing.value = true
                        val progress = workInfo.progress.getFloat(NavidromeSyncWorker.PROGRESS_VALUE, 0f)
                        _syncProgress.value = if (progress > 0f) progress else null
                        _syncMessage.value = workInfo.progress.getString(NavidromeSyncWorker.PROGRESS_MESSAGE)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                    }
                    WorkInfo.State.FAILED -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                        _syncMessage.value = workInfo.outputData.getString(NavidromeSyncWorker.ERROR_MESSAGE) ?: "Sync failed"
                    }
                    else -> {
                        _isSyncing.value = false
                        _syncProgress.value = null
                    }
                }
            }
        }
    }

    fun syncAllPlaylistsAndSongs() {
        workManager.enqueueUniqueWork(
            WORK_NAME_SYNC_ALL,
            ExistingWorkPolicy.KEEP,
            NavidromeSyncWorker.startAllSync()
        )
    }

    fun syncPlaylistSongs(playlistId: String) {
        workManager.enqueueUniqueWork(
            "navidrome_sync_playlist_$playlistId",
            ExistingWorkPolicy.REPLACE,
            NavidromeSyncWorker.startPlaylistSync(playlistId)
        )
    }

    fun loadPlaylistSongs(playlistId: String) {
        viewModelScope.launch {
            repository.getPlaylistSongs(playlistId).collect { songs ->
                _selectedPlaylistSongs.value = songs
            }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }

    companion object {
        private const val WORK_NAME_SYNC_ALL = "navidrome_sync_all"
    }
}
