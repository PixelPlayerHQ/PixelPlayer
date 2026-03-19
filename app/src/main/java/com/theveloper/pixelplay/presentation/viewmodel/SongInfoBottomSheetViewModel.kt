package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.wear.PhoneWatchTransferState
import com.theveloper.pixelplay.data.service.wear.PhoneWatchTransferStateStore
import com.theveloper.pixelplay.data.service.wear.WearPhoneTransferSender
import com.theveloper.pixelplay.shared.WearTransferProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SongInfoBottomSheetViewModel @Inject constructor(
    private val wearPhoneTransferSender: WearPhoneTransferSender,
    private val transferStateStore: PhoneWatchTransferStateStore,
) : ViewModel() {

    private val _isPixelPlayWatchAvailable = MutableStateFlow(false)
    val isPixelPlayWatchAvailable: StateFlow<Boolean> = _isPixelPlayWatchAvailable.asStateFlow()
    private val _isWatchAvailabilityResolved = MutableStateFlow(false)
    val isWatchAvailabilityResolved: StateFlow<Boolean> = _isWatchAvailabilityResolved.asStateFlow()
    private val _isRefreshingWatchAvailability = MutableStateFlow(false)

    private val _isRequestingToWatch = MutableStateFlow(false)
    val watchTransfers: StateFlow<Map<String, PhoneWatchTransferState>> = transferStateStore.transfers
    val watchSongIds: StateFlow<Set<String>> = transferStateStore.watchSongIds
    val reachableWatchNodeIds: StateFlow<Set<String>> = transferStateStore.reachableWatchNodeIds
    val isWatchLibraryResolved: StateFlow<Boolean> = transferStateStore.isWatchLibraryResolved
    val activeWatchTransfer: StateFlow<PhoneWatchTransferState?> = watchTransfers
        .map { transfers ->
            transfers.values
                .asSequence()
                .filter { it.status == WearTransferProgress.STATUS_TRANSFERRING }
                .maxByOrNull { it.updatedAtMillis }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null,
        )
    val isSendingToWatch: StateFlow<Boolean> = combine(
        _isRequestingToWatch,
        activeWatchTransfer
    ) { isRequesting, activeTransfer ->
        isRequesting || activeTransfer != null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = false,
    )

    fun refreshWatchAvailability() {
        if (_isRefreshingWatchAvailability.value) return

        viewModelScope.launch {
            _isRefreshingWatchAvailability.value = true
            val available = wearPhoneTransferSender.isPixelPlayWatchAvailable()
            _isPixelPlayWatchAvailable.value = available
            _isWatchAvailabilityResolved.value = true
            _isRefreshingWatchAvailability.value = false
            if (available) {
                viewModelScope.launch {
                    wearPhoneTransferSender.refreshWatchLibraryState()
                }
            }
        }
    }

    fun isLocalSongForWatchTransfer(song: Song): Boolean {
        val uri = song.contentUriString
        val isCloudSong = uri.startsWith("telegram://") ||
            uri.startsWith("netease://") ||
               uri.startsWith("qqmusic://") ||
            uri.startsWith("gdrive://")
        if (isCloudSong) return false

        if (song.path.isNotBlank()) {
            return File(song.path).exists()
        }

        return uri.startsWith("content://") || uri.startsWith("file://")
    }

    fun sendSongToWatch(song: Song, onComplete: (String) -> Unit) {
        if (_isRequestingToWatch.value) return

        viewModelScope.launch {
            if (!isLocalSongForWatchTransfer(song)) {
                onComplete("Only local songs can be sent to watch")
                return@launch
            }
            if (!_isPixelPlayWatchAvailable.value) {
                onComplete("No reachable watch with PixelPlay")
                refreshWatchAvailability()
                return@launch
            }
            if (transferStateStore.isSongSavedOnAllReachableWatches(song.id)) {
                onComplete(WearTransferProgress.ERROR_ALREADY_ON_WATCH)
                return@launch
            }

            _isRequestingToWatch.update { true }
            val result = wearPhoneTransferSender.requestSongTransfer(song.id, song.title)
            _isRequestingToWatch.update { false }

            if (result.isSuccess) {
                val nodeCount = result.getOrNull() ?: 1
                onComplete(
                    if (nodeCount > 1) {
                        "Transfer requested on $nodeCount watches"
                    } else {
                        "Transfer requested on watch"
                    }
                )
            } else {
                onComplete(result.exceptionOrNull()?.message ?: "Failed to request transfer")
                refreshWatchAvailability()
            }
        }
    }

    fun cancelWatchTransfer(requestId: String) {
        if (requestId.isBlank()) return
        viewModelScope.launch {
            wearPhoneTransferSender.cancelTransfer(requestId)
        }
    }

    fun isSongSavedOnAllReachableWatches(songId: String): Boolean {
        return transferStateStore.isSongSavedOnAllReachableWatches(songId)
    }
}
