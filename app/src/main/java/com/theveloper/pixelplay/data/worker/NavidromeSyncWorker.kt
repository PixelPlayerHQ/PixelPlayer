package com.theveloper.pixelplay.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.theveloper.pixelplay.data.cloud.NavidromeRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class NavidromeSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: NavidromeRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncType = inputData.getString(KEY_SYNC_TYPE) ?: SYNC_TYPE_ALL
        val playlistId = inputData.getString(KEY_PLAYLIST_ID)

        Timber.d("NavidromeSyncWorker: Starting sync (type=$syncType, playlistId=$playlistId)")

        return try {
            when (syncType) {
                SYNC_TYPE_ALL -> {
                    repository.syncAllPlaylistsAndSongs { progress, message ->
                        setProgressAsync(
                            workDataOf(
                                PROGRESS_VALUE to progress,
                                PROGRESS_MESSAGE to message
                            )
                        )
                    }
                }
                SYNC_TYPE_PLAYLISTS -> {
                    repository.syncPlaylists()
                }
                SYNC_TYPE_PLAYLIST_SONGS -> {
                    if (playlistId != null) {
                        repository.syncPlaylistSongs(playlistId)
                        repository.syncUnifiedLibrarySongsFromNavidrome()
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "NavidromeSyncWorker: Sync failed")
            Result.failure(workDataOf(ERROR_MESSAGE to e.message))
        }
    }

    companion object {
        const val KEY_SYNC_TYPE = "sync_type"
        const val KEY_PLAYLIST_ID = "playlist_id"
        
        const val SYNC_TYPE_ALL = "all"
        const val SYNC_TYPE_PLAYLISTS = "playlists"
        const val SYNC_TYPE_PLAYLIST_SONGS = "playlist_songs"

        const val PROGRESS_VALUE = "progress_value"
        const val PROGRESS_MESSAGE = "progress_message"
        const val ERROR_MESSAGE = "error_message"

        fun startAllSync() = OneTimeWorkRequestBuilder<NavidromeSyncWorker>()
            .setInputData(workDataOf(KEY_SYNC_TYPE to SYNC_TYPE_ALL))
            .build()
            
        fun startPlaylistSync(playlistId: String) = OneTimeWorkRequestBuilder<NavidromeSyncWorker>()
            .setInputData(
                workDataOf(
                    KEY_SYNC_TYPE to SYNC_TYPE_PLAYLIST_SONGS,
                    KEY_PLAYLIST_ID to playlistId
                )
            )
            .build()
    }
}
