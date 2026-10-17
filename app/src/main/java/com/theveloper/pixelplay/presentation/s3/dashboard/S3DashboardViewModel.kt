package com.theveloper.pixelplay.presentation.s3.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.S3FolderEntity
import com.theveloper.pixelplay.data.s3.S3Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class S3DashboardViewModel @Inject constructor(
    private val repository: S3Repository
) : ViewModel() {

    val folders: StateFlow<List<S3FolderEntity>> = repository.getFolders()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    val probeQueue: StateFlow<S3Repository.ProbeQueueState> = repository.probeQueueFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, S3Repository.ProbeQueueState(isRunning = false, remaining = 0))

    val bucketLabel: String? get() = repository.userBucketLabel()
    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedInFlow

    init {
        // Auto-sync folders when dashboard opens
        syncAllFoldersAndSongs()
    }

    fun syncAllFoldersAndSongs() {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing all folders..."
            val result = repository.syncAllFoldersAndSongs()
            _syncMessage.value = when {
                result.folderCount == 0 -> "No folders to sync"
                result.failedFolderCount > 0 ->
                    "Synced ${result.folderCount - result.failedFolderCount}/${result.folderCount} folders (some failed)"
                else -> "Synced ${result.folderCount} folders"
            }
            _isSyncing.value = false
        }
    }

    fun syncFolder(folderId: String) {
        if (_isSyncing.value) return
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Syncing folder..."
            repository.syncFolder(folderId).fold(
                onSuccess = { count -> _syncMessage.value = "Synced $count songs" },
                onFailure = { _syncMessage.value = "Sync failed: ${it.message}" }
            )
            _isSyncing.value = false
        }
    }

    fun removeFolder(folderId: String) {
        viewModelScope.launch {
            repository.removeFolder(folderId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
        }
    }
}
