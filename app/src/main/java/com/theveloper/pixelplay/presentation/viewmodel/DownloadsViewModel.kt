package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.database.DownloadEntity
import com.theveloper.pixelplay.data.download.DownloadManager
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadManager: DownloadManager
) : ViewModel() {

    val downloads: StateFlow<List<DownloadEntity>> = downloadManager.getAllDownloads()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun cancelDownload(songId: String) {
        viewModelScope.launch {
            downloadManager.cancelDownload(songId)
        }
    }

    fun retryDownload(download: DownloadEntity) {
        viewModelScope.launch {
            // Re-create Song object from Entity using emptySong as template
            val song = Song.emptySong().copy(
                id = download.songId,
                title = download.title,
                artist = download.artist,
                albumArtUriString = download.thumbnailUrl
            )
            downloadManager.downloadSong(song)
        }
    }

    fun removeDownload(songId: String) {
        viewModelScope.launch {
            // We need a remove method in DownloadManager
            // For now, let's just cancel it
            downloadManager.cancelDownload(songId)
        }
    }
}
