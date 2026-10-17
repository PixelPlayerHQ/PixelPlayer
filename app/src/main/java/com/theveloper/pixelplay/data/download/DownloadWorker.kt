package com.theveloper.pixelplay.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.documentfile.provider.DocumentFile
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Serial download queue: claims queued jobs from [DownloadRepository], downloads
 * via gobackend to a cache work file, publishes the result into the user's SAF
 * folder, then media-scans it so the track lands in the local library.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backend: DownloadBackendApi,
    private val repository: DownloadRepository,
    private val settings: DownloadSettings,
    private val finalizer: DownloadFinalizer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        ensureNotificationChannel(applicationContext)
        setForeground(createForegroundInfo(str(R.string.download_notif_starting)))
        while (true) {
            val job = repository.claimNextQueued() ?: break
            val treeUri = settings.safTreeUri ?: run {
                repository.markFailed(job.itemId, str(R.string.download_error_no_folder), "no_folder")
                continue
            }
            process(job, treeUri)
        }
        Result.success()
    }

    private suspend fun process(job: DownloadJob, treeUri: String) {
        // Per-item cache dir: Go writes "<dir>/<artist> - <title><ext>" here and the extension
        // picks the real extension. SAF publishing copies from it. Never output_fd.
        val workDir = File(applicationContext.cacheDir, "downloads/${job.itemId}").apply { deleteRecursively(); mkdirs() }
        try {
            process(job, treeUri, workDir)
        } finally {
            workDir.deleteRecursively()
        }
    }

    private suspend fun process(job: DownloadJob, treeUri: String, workDir: File) {
        val track = job.track
        val provider = job.downloadProvider
        // Mirrors SpotiFLAC's _buildDownloadRequestPayload: service/download_provider = the
        // chosen download extension (Go locks to it), source = the extension the track came from.
        val request = DownloadRequest(
            service = provider,
            source = track.providerId,
            trackName = track.name,
            artistName = track.artists,
            albumName = track.albumName,
            albumArtist = track.albumArtist,
            coverUrl = track.coverUrl.orEmpty(),
            outputDir = workDir.absolutePath,
            quality = job.quality,
            downloadProvider = provider,
            embedMetadata = true,
            itemId = job.itemId,
            durationMs = track.durationMs,
            trackNumber = track.trackNumber,
            discNumber = track.discNumber,
            totalTracks = track.totalTracks,
            totalDiscs = track.totalDiscs,
            releaseDate = track.releaseDate.orEmpty(),
            isrc = track.isrc.orEmpty(),
            spotifyId = track.spotifyId?.takeIf { it.isNotBlank() } ?: track.id,
            providerTrackId = track.providerTrackId?.takeIf { it.isNotBlank() } ?: knownProviderTrackId(track, provider),
            deezerId = track.deezerId.orEmpty(),
            tidalId = track.tidalId.orEmpty(),
            qobuzId = track.qobuzId.orEmpty(),
            genre = track.genre.orEmpty(),
            label = track.label.orEmpty(),
            copyright = track.copyright.orEmpty(),
            composer = track.composer.orEmpty(),
            explicit = track.explicit,
            albumType = track.albumType.orEmpty(),
            upc = track.upc.orEmpty(),
        )
        var seq = 0L
        val result = coroutineScope {
            val poller = launch {
                while (isActive) {
                    val delta = backend.waitProgressDelta(seq, 400)
                    seq = delta.seq
                    repository.applyDelta(delta)
                    updateNotification(job, delta)
                }
            }
            val r = backend.download(request)
            poller.cancel()
            r
        }
        when (result) {
            is BackendResult.Failed ->
                repository.markFailed(job.itemId, result.response.error ?: str(R.string.download_error_failed), result.response.errorType)
            is BackendResult.Ok -> finalizeAndPublish(job, treeUri, result.value)
        }
    }

    private fun finalizeAndPublish(job: DownloadJob, treeUri: String, response: DownloadResponse) {
        val path = response.filePath?.takeIf { it.isNotBlank() && !it.startsWith("content://") }
        var file = path?.let(::File)?.takeIf { it.isFile && it.length() > 0 } ?: run {
            repository.markFailed(job.itemId, str(R.string.download_error_no_file), "no_file")
            return
        }
        try {
            val decryption = finalizer.decryptionFor(response)
            if (decryption != null && !response.alreadyExists) {
                notify(str(R.string.download_notif_decrypting, "${job.track.artists} - ${job.track.name}"))
                file = try {
                    finalizer.decrypt(file, decryption)
                } catch (e: Exception) {
                    Log.e(TAG, "Decrypt failed for ${job.itemId}", e)
                    repository.markFailed(job.itemId, e.message ?: str(R.string.download_error_decrypt), "decrypt_failed")
                    return
                }
            }
            finalizer.writeTags(file, job.track, response)

            val ext = file.extension.lowercase().ifEmpty {
                response.actualExtension?.trimStart('.')?.lowercase().orEmpty().ifEmpty { "flac" }
            }
            val target = publish(file, treeUri, job, ext) ?: return
            repository.markCompleted(job.itemId, target.toString(), ext)
            MediaScannerConnection.scanFile(applicationContext, arrayOf(target.toString()), arrayOf(mimeFor(ext)), null)
            repository.markPublished(job.itemId)
        } finally {
            file.delete() // Go may write outside workDir (extension data dir)
        }
    }

    /** SpotiFLAC _knownProviderTrackId: the track's own id only counts for the provider it came from. */
    private fun knownProviderTrackId(track: ExtTrack, provider: String): String {
        val id = track.id.trim()
        if (id.isEmpty()) return ""
        val sep = id.indexOf(':')
        if (track.providerId.isNotBlank() && track.providerId.equals(provider, ignoreCase = true)) {
            return if (sep >= 0 && sep + 1 < id.length) id.substring(sep + 1) else id
        }
        return if (sep > 0 && id.substring(0, sep).equals(provider, ignoreCase = true)) id.substring(sep + 1) else ""
    }

    /** Copies [src] into the SAF tree as "<artists> - <title>.<ext>" (collision-safe). Returns target uri or null after marking failed. */
    private fun publish(src: File, treeUri: String, job: DownloadJob, ext: String): Uri? {
        val dir = DocumentFile.fromTreeUri(applicationContext, Uri.parse(treeUri))
            ?: run {
                repository.markFailed(job.itemId, str(R.string.download_error_folder_unavailable), "no_folder")
                return null
            }
        val base = sanitize("${job.track.artists.ifEmpty { "Unknown artist" }} - ${job.track.name}")
        var name = "$base.$ext"
        var n = 1
        while (dir.findFile(name) != null) name = "$base (${n++}).$ext"
        val target = dir.createFile(mimeFor(ext), name)
            ?: run {
                repository.markFailed(job.itemId, str(R.string.download_error_create_file), "saf_error")
                return null
            }
        try {
            applicationContext.contentResolver.openOutputStream(target.uri)?.use { out ->
                src.inputStream().use { it.copyTo(out, DEFAULT_BUFFER_SIZE * 8) }
            } ?: run {
                repository.markFailed(job.itemId, str(R.string.download_error_open_file), "saf_error")
                return null
            }
        } catch (e: Exception) {
            repository.markFailed(job.itemId, e.message ?: str(R.string.download_error_publish), "saf_error")
            return null
        }
        return target.uri
    }

    /** applicationContext follows the in-app language (AppCompatDelegate.setApplicationLocales). */
    private fun str(id: Int, vararg args: Any): String = applicationContext.getString(id, *args)

    private fun sanitize(s: String) = s.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150)

    private fun mimeFor(ext: String) = when (ext) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }

    private fun updateNotification(job: DownloadJob, delta: ProgressDelta) {
        val p = delta.items[job.itemId] ?: return
        notify(str(R.string.download_notif_progress, "${job.track.artists} - ${job.track.name}", (p.progress * 100).toInt()))
    }

    private fun notify(text: String) {
        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, buildNotification(text))
    }

    // dataSync type must match the manifest override on androidx.work's
    // SystemForegroundService (required on API 34+; WorkManager ships none).
    private fun createForegroundInfo(text: String) = ForegroundInfo(
        NOTIF_ID,
        buildNotification(text),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    companion object {
        private const val TAG = "DownloadWorker"
        const val CHANNEL_ID = "download_channel"
        const val NOTIF_ID = 4001
        const val UNIQUE_WORK = "external_download"

        fun ensureNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notif_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** Enqueues the serial queue pass. APPEND_OR_REPLACE: appends to a running pass, replaces a dead one. */
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<DownloadWorker>().build(),
            )
        }
    }
}
