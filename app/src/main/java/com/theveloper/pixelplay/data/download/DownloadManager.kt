package com.theveloper.pixelplay.data.download

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.theveloper.pixelplay.data.database.DownloadDao
import com.theveloper.pixelplay.data.database.DownloadEntity
import com.theveloper.pixelplay.data.database.DownloadStatus
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val extensionLoader: ExtensionLoader,
    private val downloadDao: DownloadDao,
    private val workManager: WorkManager
) {

    // Map song ID to progress for UI backwards compatibility
    val downloads: Flow<Map<String, Int>> = downloadDao.getAllDownloads()
        .map { list ->
            list.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING }
                .associate { it.songId to it.progress.toInt() }
        }

    suspend fun downloadSong(song: Song) {
        if (!song.id.startsWith("extension:")) return
        val parts = song.id.split(":")
        if (parts.size < 3) return
        val extId = parts[1]
        val trackId = parts[2]

        // Check if already downloading or downloaded
        val existing = downloadDao.getDownloadBySongId(song.id)
        if (existing != null && (existing.status == DownloadStatus.COMPLETED || existing.status == DownloadStatus.DOWNLOADING)) {
            return
        }

        val download = DownloadEntity(
            songId = song.id,
            title = song.title,
            artist = song.artist ?: "Unknown",
            thumbnailUrl = song.albumArtUriString,
            extensionId = extId,
            status = DownloadStatus.PENDING
        )
        downloadDao.insertDownload(download)

        val data = Data.Builder()
            .putString("songId", song.id)
            .putString("extensionId", extId)
            .putString("trackId", trackId)
            .putString("title", song.title)
            .putString("artist", song.artist)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .addTag("download_${song.id}")
            .build()

        workManager.enqueue(workRequest)
    }

    suspend fun cancelDownload(songId: String) {
        workManager.cancelAllWorkByTag("download_$songId")
        downloadDao.updateProgress(songId, DownloadStatus.CANCELLED, 0f)
    }

    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()
}
