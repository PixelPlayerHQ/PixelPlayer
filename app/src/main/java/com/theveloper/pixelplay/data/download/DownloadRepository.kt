package com.theveloper.pixelplay.data.download

import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

enum class JobStatus { QUEUED, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

data class DownloadJob(
    val itemId: String = UUID.randomUUID().toString(),
    val track: ExtTrack,
    val quality: String,
    val downloadProvider: String = "",
    val status: JobStatus = JobStatus.QUEUED,
    val progress: Float = 0f,
    val bytesReceived: Long = 0,
    val bytesTotal: Long = 0,
    val error: String? = null,
    val errorType: String? = null,
    val resultFilePath: String? = null,
    val actualExtension: String? = null,
    val published: Boolean = false,
) {
    val title: String get() = track.name
}

@Singleton
class DownloadRepository @Inject constructor() {

    private val _jobs = MutableStateFlow<List<DownloadJob>>(emptyList())
    val jobs: StateFlow<List<DownloadJob>> = _jobs.asStateFlow()

    fun enqueue(track: ExtTrack, quality: String, downloadProvider: String): String {
        val job = DownloadJob(track = track, quality = quality, downloadProvider = downloadProvider)
        _jobs.update { list -> list + job }
        return job.itemId
    }

    fun claimNextQueued(): DownloadJob? =
        _jobs.value.firstOrNull { it.status == JobStatus.QUEUED }?.also { job ->
            _jobs.update { list -> list.map { if (it.itemId == job.itemId) it.copy(status = JobStatus.DOWNLOADING) else it } }
        }

    fun applyDelta(delta: ProgressDelta) {
        _jobs.update { list ->
            list.map { job ->
                val p = delta.items[job.itemId] ?: return@map job
                // Only QUEUED/DOWNLOADING jobs track live status; never downgrade
                // terminal CANCELLED/FAILED/COMPLETED on a late delta.
                val active = job.status == JobStatus.QUEUED || job.status == JobStatus.DOWNLOADING
                job.copy(
                    progress = p.progress.coerceIn(0f, 1f),
                    bytesReceived = p.bytesReceived,
                    bytesTotal = p.bytesTotal,
                    status = if (!active) job.status
                    else when (p.status) {
                        "completed" -> JobStatus.COMPLETED
                        else -> JobStatus.DOWNLOADING
                    },
                )
            }
        }
    }

    fun markCompleted(itemId: String, filePath: String, actualExtension: String?) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.COMPLETED, progress = 1f, resultFilePath = filePath, actualExtension = actualExtension) else it } }
    }

    fun markPublished(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(published = true) else it } }
    }

    fun markFailed(itemId: String, error: String, errorType: String?) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.FAILED, error = error, errorType = errorType) else it } }
    }

    fun markCancelled(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.CANCELLED) else it } }
    }

    fun retry(itemId: String) {
        _jobs.update { list -> list.map { if (it.itemId == itemId) it.copy(status = JobStatus.QUEUED, error = null, errorType = null, progress = 0f) else it } }
    }

    /** Requeues downloads that failed waiting on [extensionId]'s verification. */
    fun retryVerification(extensionId: String) {
        _jobs.value
            .filter {
                it.status == JobStatus.FAILED && it.errorType == "verification_required" &&
                    it.downloadProvider.equals(extensionId, ignoreCase = true)
            }
            .forEach { retry(it.itemId) }
    }

    fun dismiss(itemId: String) {
        _jobs.update { list -> list.filterNot { it.itemId == itemId } }
    }
}
