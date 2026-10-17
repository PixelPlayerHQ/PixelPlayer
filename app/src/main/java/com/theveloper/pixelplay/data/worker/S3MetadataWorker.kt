package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.theveloper.pixelplay.data.s3.S3Repository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * Background queue that reads embedded tags (title/artist/album/duration/artwork)
 * for S3 songs whose metadata still comes from the filename. Processes batches
 * until the `metadata_probed = 0` backlog is empty, then refreshes the unified library.
 */
@HiltWorker
class S3MetadataWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: S3Repository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = try {
        var probed = 0
        while (repository.probeUnprobedBatch()) {
            probed += 8
            setProgress(androidx.work.workDataOf(PROGRESS_PROBED to probed))
        }
        if (probed > 0) repository.refreshUnifiedLibrary()
        Timber.tag("S3MetadataWorker").d("Tag probe finished (%d songs)", probed)
        Result.success()
    } catch (e: Exception) {
        Timber.tag("S3MetadataWorker").w(e, "Tag probe failed")
        Result.retry()
    }

    companion object {
        const val PROGRESS_PROBED = "probed"
    }
}
