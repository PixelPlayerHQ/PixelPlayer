package com.theveloper.pixelplay.data.download

import android.content.Context
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import com.theveloper.pixelplay.data.database.DownloadDao
import com.theveloper.pixelplay.data.database.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.brahmkshatriya.echo.common.clients.DownloadClient
import dev.brahmkshatriya.echo.common.clients.TrackClient
import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Progress
import dev.brahmkshatriya.echo.common.models.Streamable
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val extensionLoader: ExtensionLoader,
    private val downloadDao: DownloadDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString("songId") ?: return Result.failure()
        val extensionId = inputData.getString("extensionId") ?: return Result.failure()
        val trackId = inputData.getString("trackId") ?: return Result.failure()
        val title = inputData.getString("title") ?: "Unknown"
        val artistName = inputData.getString("artist") ?: "Unknown"

        val download = downloadDao.getDownloadBySongId(songId) ?: return Result.failure()
        
        val notifier = DownloadNotificationHelper(context)
        val notifId = kotlin.math.abs(songId.hashCode())

        setForeground(notifier.getForegroundInfo(notifId, "Preparing download: $title"))

        try {
            downloadDao.updateProgress(songId, DownloadStatus.DOWNLOADING, 0f)
            notifier.showProgress(notifId, "Downloading: $title", 0)

            val extension = extensionLoader.all.value.find { it.metadata.id == extensionId } 
                ?: return Result.failure()

            val artist = Artist(id = "artist-$artistName", name = artistName)
            val track = Track(id = trackId, title = title, artists = listOf(artist), isPlayable = Track.Playable.Yes)

            val instance = extension.instance.value().getOrNull() ?: return Result.failure()
            if (instance !is DownloadClient) return Result.failure()

            // Get download contexts
            val contexts = instance.getDownloadTracks(extensionId, track, null)
            val ctx = contexts.firstOrNull() ?: return Result.failure()

            // Select server
            val selectedStreamable = instance.selectServer(ctx)

            // Load media
            if (instance !is TrackClient) return Result.failure()
            val serverMedia = instance.loadStreamableMedia(selectedStreamable, true) as? Streamable.Media.Server 
                ?: return Result.failure()

            // Pick sources
            val sources = instance.selectSources(ctx, serverMedia)

            val downloadedFiles = mutableListOf<File>()
            for (source in sources) {
                val p = MutableStateFlow(Progress())
                coroutineScope {
                    val job = launch {
                        p.collect { prog ->
                            val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                            notifier.showProgress(notifId, "Downloading: $title", pct)
                            downloadDao.updateProgress(songId, DownloadStatus.DOWNLOADING, pct.toFloat())
                        }
                    }
                    val file = instance.download(p, ctx, source)
                    job.cancel()
                    downloadedFiles.add(file)
                }
            }

            // Merge
            val mergeProgress = MutableStateFlow(Progress())
            val merged = coroutineScope {
                val job = launch {
                    mergeProgress.collect { prog ->
                        val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                        notifier.showProgress(notifId, "Merging: $title", pct)
                    }
                }
                val file = instance.merge(mergeProgress, ctx, downloadedFiles)
                job.cancel()
                file
            }

            // Tag
            val tagProgress = MutableStateFlow(Progress())
            val tagged = coroutineScope {
                val job = launch {
                    tagProgress.collect { prog ->
                        val pct = if (prog.size > 0) ((prog.progress * 100 / prog.size).toInt()) else 0
                        notifier.showProgress(notifId, "Tagging: $title", pct)
                    }
                }
                val file = instance.tag(tagProgress, ctx, merged)
                job.cancel()
                file
            }

            // Move to final destination
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            if (!musicDir.exists()) musicDir.mkdirs()
            val dest = File(musicDir, tagged.name)
            tagged.copyTo(dest, overwrite = true)
            if (tagged.absolutePath != dest.absolutePath) tagged.delete()

            downloadDao.updateDownload(download.copy(
                status = DownloadStatus.COMPLETED,
                progress = 100f,
                downloadPath = dest.absolutePath
            ))

            notifier.showComplete(notifId, "Downloaded: $title", dest)
            return Result.success()

        } catch (e: Exception) {
            downloadDao.updateDownload(download.copy(
                status = DownloadStatus.FAILED,
                errorMessage = e.message
            ))
            notifier.showFailed(notifId, "Download failed: $title", e.message)
            return Result.failure()
        }
    }
}
