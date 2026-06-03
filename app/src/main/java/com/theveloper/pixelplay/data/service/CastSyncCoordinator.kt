package com.theveloper.pixelplay.data.service

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.Player
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.theveloper.pixelplay.data.service.cast.CastRemotePlaybackState
import com.theveloper.pixelplay.presentation.viewmodel.ListeningStatsTracker
import timber.log.Timber

/**
 * Snapshot of the currently casting remote player, projected from the Cast
 * [MediaStatus]. Consumed by both the listening-stats sync and the widget/Wear
 * surfaces, so it lives at file scope rather than nested in [MusicService].
 */
internal data class RemotePlaybackSnapshot(
    val occurrenceId: String,
    val songId: String?,
    val title: String,
    val artist: String,
    val artworkUri: Uri?,
    val isPlaying: Boolean,
    val isActuallyPlaying: Boolean,
    val currentPositionMs: Long,
    val totalDurationMs: Long,
    val repeatMode: Int,
    val isShuffleEnabled: Boolean,
)

/**
 * Owns Cast remote-session synchronization, extracted from [MusicService] during
 * the Pass 5 service decomposition.
 *
 * Responsibilities:
 *  - Registering/unregistering the [SessionManagerListener] and per-session
 *    [RemoteMediaClient.Callback].
 *  - Tracking the observed [CastSession] and mirroring its playback into the
 *    [ListeningStatsTracker].
 *  - Projecting the remote [MediaStatus] into a [RemotePlaybackSnapshot] for the
 *    widget/Wear surfaces.
 *
 * The service supplies [requestWidgetUpdate] so the coordinator can trigger a UI
 * refresh without depending on the widget pipeline directly. All Cast SDK access
 * is wrapped in [runCatching] to tolerate Play Services being unavailable.
 */
internal class CastSyncCoordinator(
    private val context: Context,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val requestWidgetUpdate: (force: Boolean) -> Unit,
) {
    private companion object {
        private const val TAG = "MusicService_PixelPlay"
    }

    private var sessionManager: SessionManager? = null
    private var sessionManagerListener: SessionManagerListener<CastSession>? = null
    private var remoteClientCallback: RemoteMediaClient.Callback? = null
    private var observedSession: CastSession? = null
    private var activeStatsOccurrenceId: String? = null
    private var activePlaybackIntent: Boolean = false

    /** The active remote media client, if any, preferring the observed session. */
    fun currentRemoteMediaClient(): RemoteMediaClient? =
        observedSession?.remoteMediaClient ?: sessionManager?.currentCastSession?.remoteMediaClient

    fun start() {
        val manager = runCatching {
            CastContext.getSharedInstance(context).sessionManager
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "CastContext unavailable; skipping cast wear sync setup")
            return
        }
        sessionManager = manager

        val remoteCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                syncListeningStatsFromRemote()
                requestWidgetUpdate(false)
            }

            override fun onMetadataUpdated() {
                syncListeningStatsFromRemote()
                requestWidgetUpdate(false)
            }

            override fun onQueueStatusUpdated() {
                syncListeningStatsFromRemote()
                requestWidgetUpdate(false)
            }

            override fun onPreloadStatusUpdated() {
                requestWidgetUpdate(false)
            }
        }
        remoteClientCallback = remoteCallback

        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                attachRemoteClient(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                attachRemoteClient(session)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                if (observedSession === session) {
                    attachRemoteClient(null)
                } else {
                    requestWidgetUpdate(true)
                }
            }

            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) = requestWidgetUpdate(true)
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = requestWidgetUpdate(true)
            override fun onSessionSuspended(session: CastSession, reason: Int) = requestWidgetUpdate(true)
        }
        sessionManagerListener = sessionListener
        runCatching {
            manager.addSessionManagerListener(sessionListener, CastSession::class.java)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to register Cast session listener")
        }

        attachRemoteClient(manager.currentCastSession)
    }

    private fun attachRemoteClient(session: CastSession?) {
        if (observedSession === session) return

        observedSession?.remoteMediaClient?.let { oldClient ->
            remoteClientCallback?.let { callback ->
                runCatching { oldClient.unregisterCallback(callback) }
            }
        }

        observedSession = session
        session?.remoteMediaClient?.let { remoteClient ->
            remoteClientCallback?.let { callback ->
                runCatching { remoteClient.registerCallback(callback) }
            }
            remoteClient.requestStatus()
            syncListeningStatsFromRemote()
        } ?: run {
            activeStatsOccurrenceId = null
            activePlaybackIntent = false
            listeningStatsTracker.onPlaybackStopped()
        }
        requestWidgetUpdate(true)
    }

    fun stop() {
        observedSession?.remoteMediaClient?.let { remoteClient ->
            remoteClientCallback?.let { callback ->
                runCatching { remoteClient.unregisterCallback(callback) }
            }
        }
        observedSession = null

        val listener = sessionManagerListener
        val manager = sessionManager
        if (listener != null && manager != null) {
            runCatching { manager.removeSessionManagerListener(listener, CastSession::class.java) }
                .onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to remove Cast session listener")
                }
        }
        sessionManagerListener = null
        remoteClientCallback = null
        sessionManager = null
    }

    fun syncListeningStatsFromRemote() {
        val snapshot = resolveRemoteSnapshot() ?: return
        val songId = snapshot.songId?.takeIf { it.isNotBlank() }
        if (songId == null) {
            activeStatsOccurrenceId = null
            listeningStatsTracker.onPlaybackStopped()
            return
        }

        if (activeStatsOccurrenceId != snapshot.occurrenceId) {
            activeStatsOccurrenceId = snapshot.occurrenceId
            listeningStatsTracker.onTrackChanged(
                songId = songId,
                positionMs = snapshot.currentPositionMs,
                durationMs = snapshot.totalDurationMs,
                isPlaying = snapshot.isActuallyPlaying
            )
            return
        }

        listeningStatsTracker.ensureSession(
            songId = songId,
            positionMs = snapshot.currentPositionMs,
            durationMs = snapshot.totalDurationMs,
            isPlaying = snapshot.isActuallyPlaying
        )
    }

    fun resolveRemoteSnapshot(): RemotePlaybackSnapshot? {
        val remoteClient = currentRemoteMediaClient() ?: return null

        val mediaStatus = remoteClient.mediaStatus ?: return null
        if (mediaStatus.playerState == MediaStatus.PLAYER_STATE_UNKNOWN) {
            return null
        }

        val currentItem = mediaStatus.getQueueItemById(mediaStatus.currentItemId)
        val mediaInfo = currentItem?.media ?: remoteClient.mediaInfo
        val metadata = mediaInfo?.metadata
        if (metadata == null && currentItem == null) {
            return null
        }

        val songId = currentItem
            ?.customData
            ?.optString("songId")
            ?.takeIf { it.isNotBlank() }
        val occurrenceId = currentItem
            ?.itemId
            ?.takeIf { it > 0 }
            ?.toString()
            ?: songId
            ?: mediaInfo?.contentId
            ?: return null

        val durationHintMs = currentItem
            ?.customData
            ?.optLong("durationHintMs", -1L)
            ?.takeIf { it > 0L }

        val streamDurationMs = remoteClient.streamDuration.takeIf { it > 0L }
        val effectiveDurationMs = (streamDurationMs ?: durationHintMs ?: 0L).coerceAtLeast(0L)
        val imageUri = metadata
                ?.images
                ?.firstOrNull()
                ?.url
                ?.toString()
                ?.takeIf { it.isNotBlank() }?.toUri()

        val mappedRepeatMode = when (mediaStatus.queueRepeatMode) {
            MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
            MediaStatus.REPEAT_MODE_REPEAT_ALL,
            MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        val remotePlayback = CastRemotePlaybackState.project(
            mediaStatus = mediaStatus,
            previousPlayIntent = activePlaybackIntent
        )
        activePlaybackIntent = remotePlayback.playWhenReady

        return RemotePlaybackSnapshot(
            occurrenceId = occurrenceId,
            songId = songId,
            title = metadata?.getString(CastMediaMetadata.KEY_TITLE).orEmpty(),
            artist = metadata?.getString(CastMediaMetadata.KEY_ARTIST).orEmpty(),
            artworkUri = imageUri,
            isPlaying = remotePlayback.isPlaying,
            isActuallyPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            currentPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            totalDurationMs = effectiveDurationMs,
            repeatMode = mappedRepeatMode,
            isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
        )
    }
}
