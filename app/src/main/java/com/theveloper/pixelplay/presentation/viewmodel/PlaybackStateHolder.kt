package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import androidx.media3.session.MediaController
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.theveloper.pixelplay.data.model.Song
import com.google.android.gms.cast.MediaStatus
import timber.log.Timber
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlin.math.abs

@Singleton
class PlaybackStateHolder @Inject constructor(
    private val dualPlayerEngine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val castStateHolder: CastStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val listeningStatsTracker: ListeningStatsTracker,
    @param:ApplicationContext private val appContext: Context
) {
    companion object {
        private const val TAG = "PlaybackStateHolder"
        private const val DURATION_MISMATCH_TOLERANCE_MS = 1500L
        private const val FOREGROUND_PROGRESS_TICK_MS = 250L
        private const val BACKGROUND_PROGRESS_TICK_MS = 1000L
        /**
         * Threshold above which we skip per-item moveMediaItem calls and use
         * a single setMediaItems call instead. moveMediaItem triggers an IPC
         * round-trip for each call, which freezes the UI on large queues.
         */
        private const val BULK_REPLACE_THRESHOLD = 80
        private const val SHUFFLE_TOGGLE_COOLDOWN_MS = 400L
    }

    private var scope: CoroutineScope? = null
    
    // MediaController
    var mediaController: MediaController? = null
        private set

    // Player State
    private val _stablePlayerState = MutableStateFlow(StablePlayerState())
    val stablePlayerState: StateFlow<StablePlayerState> = _stablePlayerState.asStateFlow()
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    // Internal State
    private var isSeeking = false
    private var remoteSeekUnlockJob: Job? = null
    private var activePositionOccurrenceMediaId: String? = null
    private var activePositionOccurrenceToken: Long = 0L
    private var nextPositionOccurrenceToken: Long = 1L
    private var pausedPositionOverrideMediaId: String? = null
    private var pausedPositionOverrideToken: Long? = null
    private var pausedPositionOverrideMs: Long? = null
    private var coldStartSnapshotMediaId: String? = null
    private var coldStartSnapshotToken: Long? = null
    private var coldStartSnapshotPositionMs: Long? = null
    private var shuffleToggleJob: Job? = null
    private var lastShuffleToggleFinishedAtMs: Long = 0L
    private val powerManager: PowerManager by lazy(LazyThreadSafetyMode.NONE) {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private fun clearColdStartSnapshot() {
        coldStartSnapshotMediaId = null
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = null
    }

    /**
     * Binds a restored snapshot to the active playback occurrence when possible.
     *
     * If the first occurrence was already activated before the snapshot finished loading, we
     * attach the snapshot to that token so resume still works. If playback has already advanced
     * past the first occurrence, the snapshot is stale and must be discarded.
     */
    private fun rememberColdStartSnapshot(mediaId: String, positionMs: Long): Boolean {
        coldStartSnapshotMediaId = mediaId
        coldStartSnapshotToken = null
        coldStartSnapshotPositionMs = positionMs

        if (nextPositionOccurrenceToken == 1L) {
            return true
        }

        if (
            activePositionOccurrenceToken == 1L &&
            nextPositionOccurrenceToken == 2L &&
            activePositionOccurrenceMediaId == mediaId
        ) {
            coldStartSnapshotToken = activePositionOccurrenceToken
            return true
        }

        clearColdStartSnapshot()
        return false
    }

    fun initialize(coroutineScope: CoroutineScope) {
        this.scope = coroutineScope
        scope?.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val snapshotMediaId = snapshot.currentMediaId
                ?: snapshot.items.getOrNull(snapshot.currentIndex)?.mediaId
                ?: return@launch
            val snapshotPositionMs = snapshot.currentPositionMs.coerceAtLeast(0L)
            if (snapshotPositionMs <= 0L) return@launch
            if (!rememberColdStartSnapshot(snapshotMediaId, snapshotPositionMs)) {
                return@launch
            }

            val controller = mediaController
            if (
                controller != null &&
                !controller.isPlaying &&
                controller.currentMediaItem?.mediaId == snapshotMediaId &&
                _currentPosition.value == 0L
            ) {
                _currentPosition.value = snapshotPositionMs
            }
        }
    }

    fun setMediaController(controller: MediaController?) {
        this.mediaController = controller
    }
    
    fun updateStablePlayerState(update: (StablePlayerState) -> StablePlayerState) {
        _stablePlayerState.update(update)
    }

    fun setCurrentPosition(positionMs: Long) {
        _currentPosition.value = positionMs.coerceAtLeast(0L)
    }

    fun syncCurrentPositionFromPlayer(mediaId: String?, reportedPositionMs: Long) {
        _currentPosition.value = resolveUiPosition(mediaId, reportedPositionMs)
    }

    fun ensureCurrentPlaybackOccurrence(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = false)
    }

    fun onPlaybackOccurrenceTransition(mediaId: String?) {
        activatePlaybackOccurrence(mediaId, forceNewOccurrence = true)
    }

    fun rememberPausedPositionOverride(mediaId: String?, positionMs: Long) {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: return
        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false) ?: return
        val safePosition = positionMs.coerceAtLeast(0L)
        pausedPositionOverrideMediaId = safeMediaId
        pausedPositionOverrideToken = activeToken
        pausedPositionOverrideMs = safePosition
        _currentPosition.value = safePosition
    }

    fun clearCurrentPositionHints(mediaId: String? = null) {
        if (mediaId == null || pausedPositionOverrideMediaId == mediaId) {
            pausedPositionOverrideMediaId = null
            pausedPositionOverrideToken = null
            pausedPositionOverrideMs = null
        }
        if (mediaId == null || coldStartSnapshotMediaId == mediaId) {
            clearColdStartSnapshot()
        }
    }

    private fun resolveUiPosition(mediaId: String?, reportedPositionMs: Long): Long {
        val safeReportedPosition = reportedPositionMs.coerceAtLeast(0L)
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() }
        if (safeMediaId == null) {
            return safeReportedPosition
        }

        val activeToken = activatePlaybackOccurrence(safeMediaId, forceNewOccurrence = false)
            ?: return safeReportedPosition

        val pausedOverride = pausedPositionOverrideMs
            ?.takeIf {
                pausedPositionOverrideMediaId == safeMediaId &&
                    pausedPositionOverrideToken == activeToken
            }
        val coldStartSeed = coldStartSnapshotPositionMs
            ?.takeIf {
                coldStartSnapshotMediaId == safeMediaId &&
                    coldStartSnapshotToken == activeToken
            }
        val preferredPosition = pausedOverride ?: coldStartSeed

        if (preferredPosition == null) {
            return safeReportedPosition
        }

        if (safeReportedPosition <= 0L) {
            return preferredPosition
        }

        val drift = abs(safeReportedPosition - preferredPosition)
        if (drift <= DURATION_MISMATCH_TOLERANCE_MS || safeReportedPosition >= preferredPosition) {
            if (pausedPositionOverrideMediaId == safeMediaId && pausedPositionOverrideToken == activeToken) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
            }
            if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotToken == activeToken) {
                clearColdStartSnapshot()
            }
            return safeReportedPosition
        }

        return preferredPosition
    }

    private fun activatePlaybackOccurrence(
        mediaId: String?,
        forceNewOccurrence: Boolean
    ): Long? {
        val safeMediaId = mediaId?.takeIf { it.isNotBlank() } ?: run {
            activePositionOccurrenceMediaId = null
            activePositionOccurrenceToken = 0L
            if (forceNewOccurrence) {
                pausedPositionOverrideMediaId = null
                pausedPositionOverrideToken = null
                pausedPositionOverrideMs = null
            }
            return null
        }

        val shouldAdvance =
            forceNewOccurrence ||
                activePositionOccurrenceToken == 0L ||
                activePositionOccurrenceMediaId != safeMediaId

        if (!shouldAdvance) {
            return activePositionOccurrenceToken
        }

        activePositionOccurrenceMediaId = safeMediaId
        activePositionOccurrenceToken = nextPositionOccurrenceToken++

        pausedPositionOverrideMediaId = null
        pausedPositionOverrideToken = null
        pausedPositionOverrideMs = null

        if (coldStartSnapshotToken != null) {
            clearColdStartSnapshot()
        } else if (coldStartSnapshotMediaId == safeMediaId && coldStartSnapshotPositionMs != null) {
            coldStartSnapshotToken = activePositionOccurrenceToken
        } else if (coldStartSnapshotMediaId != null) {
            clearColdStartSnapshot()
        }

        return activePositionOccurrenceToken
    }
    
    /* -------------------------------------------------------------------------- */
    /*                               Playback Controls                            */
    /* -------------------------------------------------------------------------- */

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                _stablePlayerState.update {
                    it.copy(
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
            } else {
                if (remoteMediaClient.mediaQueue.itemCount > 0) {
                    castStateHolder.castPlayer?.play()
                    _stablePlayerState.update {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else {
                    Timber.w("Remote queue empty, cannot resume.")
                }
            }
        } else {
            val controller = mediaController ?: return
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun seekTo(position: Long) {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val targetPosition = position.coerceAtLeast(0L)
            castStateHolder.setRemotelySeeking(true)
            castStateHolder.setRemotePosition(targetPosition)
            setCurrentPosition(targetPosition)
            castStateHolder.castPlayer?.seek(targetPosition)

            remoteSeekUnlockJob?.cancel()
            remoteSeekUnlockJob = scope?.launch {
                // Fail-safe: never keep remote seeking lock indefinitely.
                delay(1800)
                castStateHolder.setRemotelySeeking(false)
                castSession.remoteMediaClient?.requestStatus()
            }
        } else {
            remoteSeekUnlockJob?.cancel()
            castStateHolder.setRemotelySeeking(false)
            val targetPosition = position.coerceAtLeast(0L)
            val currentMediaId = mediaController?.currentMediaItem?.mediaId
            rememberPausedPositionOverride(currentMediaId, targetPosition)
            mediaController?.seekTo(targetPosition)
        }
    }

    fun previousSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.previous()
        } else {
            val controller = mediaController ?: return
             if (controller.currentPosition > 10000) { // 10 seconds
                 controller.seekTo(0)
            } else {
                 controller.seekToPrevious()
            }
        }
    }

    fun nextSong() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            castStateHolder.castPlayer?.next()
        } else {
             mediaController?.seekToNext()
        }
    }

    fun cycleRepeatMode() {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val currentRepeatMode = remoteMediaClient.mediaStatus?.getQueueRepeatMode() ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
            val newMode = when (currentRepeatMode) {
                MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(newMode)
            
            // Map remote mode back to local constant for persistence/UI
            val mappedLocalMode = when (newMode) {
                MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> Player.REPEAT_MODE_ONE
                MediaStatus.REPEAT_MODE_REPEAT_ALL, MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            scope?.launch { userPreferencesRepository.setRepeatMode(mappedLocalMode) }
            _stablePlayerState.update { it.copy(repeatMode = mappedLocalMode) }
        } else {
            val currentMode = _stablePlayerState.value.repeatMode
            val newMode = when (currentMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
            mediaController?.repeatMode = newMode
            scope?.launch { userPreferencesRepository.setRepeatMode(newMode) }
            _stablePlayerState.update { it.copy(repeatMode = newMode) }
        }
    }

    fun setRepeatMode(mode: Int) {
        val castSession = castStateHolder.castSession.value
        val remoteMediaClient = castSession?.remoteMediaClient

        if (castSession != null && remoteMediaClient != null) {
            val remoteMode = when (mode) {
                Player.REPEAT_MODE_ONE -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                Player.REPEAT_MODE_ALL -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
            }
            castStateHolder.castPlayer?.setRepeatMode(remoteMode)
        } else {
             mediaController?.repeatMode = mode
        }
        
        scope?.launch { userPreferencesRepository.setRepeatMode(mode) }
        _stablePlayerState.update { it.copy(repeatMode = mode) }
    }

    /* -------------------------------------------------------------------------- */
    /*                               Progress Updates                             */
    /* -------------------------------------------------------------------------- */
    
    private var progressJob: kotlinx.coroutines.Job? = null

    /**
     * Reconciles duration reported by the player with the current song metadata duration.
     *
     * Why:
     * - During some transitions (notably crossfade player swaps), the reported duration can lag
     *   behind the currently visible track for a short period.
     * - Relying only on one source can make progress run too slow/fast.
     */
    private fun resolveEffectiveDuration(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long {
        val reported = when {
            reportedDurationMs == C.TIME_UNSET -> 0L
            reportedDurationMs < 0L -> 0L
            else -> reportedDurationMs
        }
        val hint = songDurationHintMs.coerceAtLeast(0L)
        val position = currentPositionMs.coerceAtLeast(0L)

        if (reported <= 0L) return hint
        if (hint <= 0L) return reported

        val diff = abs(reported - hint)
        if (diff <= DURATION_MISMATCH_TOLERANCE_MS) return reported

        // If playback already passed the metadata hint, trust the reported duration to avoid clipping.
        if (position > hint + DURATION_MISMATCH_TOLERANCE_MS && reported >= position) {
            return reported
        }

        // Otherwise prefer the shorter duration to avoid stale longer values after swaps.
        val resolved = minOf(reported, hint)
        if (diff > 10_000L) {
            Timber.tag(TAG).w(
                "Duration mismatch resolved (reported=%dms, hint=%dms, pos=%dms, resolved=%dms)",
                reported, hint, position, resolved
            )
        }
        return resolved
    }

    fun resolveDurationForPlaybackState(
        reportedDurationMs: Long,
        songDurationHintMs: Long,
        currentPositionMs: Long
    ): Long = resolveEffectiveDuration(
        reportedDurationMs = reportedDurationMs,
        songDurationHintMs = songDurationHintMs,
        currentPositionMs = currentPositionMs
    )

    fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope?.launch {
            while (true) {
                val tickMs = currentProgressTickMs()
                val castSession = castStateHolder.castSession.value
                val isRemote = castSession?.remoteMediaClient != null
                
                if (isRemote) {
                    val remoteClient = castSession?.remoteMediaClient
                    if (remoteClient != null) {
                        val isRemotePlaying = remoteClient.isPlaying
                        val currentPosition = remoteClient.approximateStreamPosition.coerceAtLeast(0L)
                        val songDurationHint = _stablePlayerState.value.currentSong?.duration ?: 0L
                        val duration = resolveEffectiveDuration(
                            reportedDurationMs = remoteClient.streamDuration,
                            songDurationHintMs = songDurationHint,
                            currentPositionMs = currentPosition
                        )
                        val isRemotelySeeking = castStateHolder.isRemotelySeeking.value
                        if (!isRemotelySeeking) {
                            castStateHolder.setRemotePosition(currentPosition)
                        }

                        listeningStatsTracker.onProgress(currentPosition, isRemotePlaying)
                        val nextPosition = if (isRemotelySeeking) _currentPosition.value else currentPosition
                        if (_currentPosition.value != nextPosition) {
                            _currentPosition.value = nextPosition
                        }

                        _stablePlayerState.update { state ->
                            if (
                                state.totalDuration == duration &&
                                state.isPlaying == isRemotePlaying &&
                                state.playWhenReady == isRemotePlaying
                            ) {
                                state
                            } else {
                                state.copy(
                                    totalDuration = duration,
                                    isPlaying = isRemotePlaying,
                                    playWhenReady = isRemotePlaying
                                )
                            }
                        }
                    }
                } else {
                     val controller = mediaController
                     // Media3: Check isPlaying or playbackState == READY/BUFFERING
                     if (controller != null && controller.isPlaying && !isSeeking) {
                         val visibleSong = _stablePlayerState.value.currentSong
                         val currentMediaId = controller.currentMediaItem?.mediaId
                         val hasMediaMismatch = visibleSong?.id != null &&
                             currentMediaId != null &&
                             visibleSong.id != currentMediaId

                         if (hasMediaMismatch) {
                            Timber.tag(TAG).v(
                                 "Skipping local progress tick due media mismatch (visible=%s, player=%s)",
                                 visibleSong?.id,
                                 currentMediaId
                             )
                            delay(tickMs)
                            continue
                        }

                         val currentPosition = controller.currentPosition.coerceAtLeast(0L)
                         val songDurationHint = visibleSong?.duration ?: 0L
                         val duration = resolveEffectiveDuration(
                             reportedDurationMs = controller.duration,
                             songDurationHintMs = songDurationHint,
                             currentPositionMs = currentPosition
                         )
                         
                         listeningStatsTracker.onProgress(currentPosition, true)
                         val resolvedPosition = resolveUiPosition(currentMediaId, currentPosition)
                         if (_currentPosition.value != resolvedPosition) {
                             _currentPosition.value = resolvedPosition
                         }
                         
                         _stablePlayerState.update { state ->
                             if (state.totalDuration == duration) {
                                 state
                             } else {
                                 state.copy(totalDuration = duration)
                             }
                        }
                     }
                }
                delay(tickMs)
            }
        }
    }

    private fun currentProgressTickMs(): Long {
        return if (powerManager.isInteractive) {
            FOREGROUND_PROGRESS_TICK_MS
        } else {
            BACKGROUND_PROGRESS_TICK_MS
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /* -------------------------------------------------------------------------- */
    /*                               Shuffle & Repeat                             */
    /* -------------------------------------------------------------------------- */

    private data class PreparedQueueReplacement(
        val mediaItems: List<MediaItem>,
        val targetIndex: Int
    )

    private data class PreparedQueueSegments(
        val beforeCurrent: List<MediaItem>,
        val afterCurrent: List<MediaItem>
    )

    private fun reorderQueueInPlace(player: Player, desiredQueue: List<Song>): Boolean {
        if (desiredQueue.isEmpty()) return false

        val currentCount = player.mediaItemCount
        if (currentCount != desiredQueue.size) {
            Timber.tag(TAG).w(
                "Cannot reorder queue in place: size mismatch (player=%d, desired=%d)",
                currentCount,
                desiredQueue.size
            )
            return false
        }

        val currentIds = MutableList(currentCount) { index ->
            player.getMediaItemAt(index).mediaId
        }
        val desiredIds = desiredQueue.map { it.id }

        val currentCounts = currentIds.groupingBy { it }.eachCount()
        val desiredCounts = desiredIds.groupingBy { it }.eachCount()
        if (currentCounts != desiredCounts) {
            Timber.tag(TAG).w("Cannot reorder queue in place: mediaId mismatch")
            return false
        }

        for (targetIndex in desiredIds.indices) {
            val desiredId = desiredIds[targetIndex]
            if (currentIds[targetIndex] == desiredId) continue

            var fromIndex = -1
            for (searchIndex in targetIndex + 1 until currentIds.size) {
                if (currentIds[searchIndex] == desiredId) {
                    fromIndex = searchIndex
                    break
                }
            }

            if (fromIndex == -1) {
                Timber.tag(TAG).w(
                    "Cannot reorder queue in place: target mediaId '%s' not found",
                    desiredId
                )
                return false
            }

            player.moveMediaItem(fromIndex, targetIndex)
            val movedId = currentIds.removeAt(fromIndex)
            currentIds.add(targetIndex, movedId)
        }

        return true
    }

    /**
     * Replaces the player timeline with [newQueue] in a single setMediaItems call,
     * preserving the currently playing song and its position. This is O(1) IPC calls
     * versus O(n) for reorderQueueInPlace, making it suitable for large queue shuffles.
     */
    private suspend fun buildQueueReplacement(
        newQueue: List<Song>,
        targetIndex: Int,
        currentMediaItem: MediaItem?
    ): PreparedQueueReplacement = withContext(Dispatchers.Default) {
        val safeTargetIndex = targetIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val mediaItems = List(newQueue.size) { index ->
            currentMediaItem
                ?.takeIf { index == safeTargetIndex && it.mediaId == newQueue[safeTargetIndex].id }
                ?: MediaItemBuilder.build(newQueue[index])
        }

        PreparedQueueReplacement(
            mediaItems = mediaItems,
            targetIndex = safeTargetIndex
        )
    }

    private suspend fun buildQueueSegments(
        newQueue: List<Song>,
        currentIndex: Int,
        currentMediaItem: MediaItem?
    ): PreparedQueueSegments? = withContext(Dispatchers.Default) {
        val safeCurrentIndex = currentIndex.coerceIn(0, (newQueue.size - 1).coerceAtLeast(0))
        val currentQueueSong = newQueue.getOrNull(safeCurrentIndex) ?: return@withContext null
        if (currentMediaItem?.mediaId != currentQueueSong.id) {
            return@withContext null
        }

        val beforeCurrent = List(safeCurrentIndex) { index ->
            MediaItemBuilder.build(newQueue[index])
        }
        val afterStartIndex = safeCurrentIndex + 1
        val afterCurrent = List((newQueue.size - afterStartIndex).coerceAtLeast(0)) { offset ->
            MediaItemBuilder.build(newQueue[afterStartIndex + offset])
        }

        PreparedQueueSegments(
            beforeCurrent = beforeCurrent,
            afterCurrent = afterCurrent
        )
    }

    private fun replacePlayerQueuePreservingCurrent(
        currentIndex: Int,
        preparedSegments: PreparedQueueSegments
    ): Boolean {
        val masterPlayer = dualPlayerEngine.masterPlayer
        val mediaItemCount = masterPlayer.mediaItemCount
        if (currentIndex !in 0 until mediaItemCount) {
            return false
        }

        val afterStartIndex = currentIndex + 1
        if (preparedSegments.beforeCurrent.size != currentIndex) {
            return false
        }
        if (preparedSegments.afterCurrent.size != (mediaItemCount - afterStartIndex)) {
            return false
        }

        if (currentIndex > 0) {
            masterPlayer.replaceMediaItems(0, currentIndex, preparedSegments.beforeCurrent)
        }
        masterPlayer.replaceMediaItems(afterStartIndex, mediaItemCount, preparedSegments.afterCurrent)
        return masterPlayer.currentMediaItemIndex == currentIndex
    }

    private fun replacePlayerQueue(
        player: Player,
        preparedQueue: PreparedQueueReplacement,
        currentPosition: Long
    ) {
        val shouldResumePlayback = player.playWhenReady || player.isPlaying
        val masterPlayer = dualPlayerEngine.masterPlayer

        masterPlayer.setMediaItems(
            preparedQueue.mediaItems,
            preparedQueue.targetIndex,
            currentPosition
        )

        if (shouldResumePlayback) {
            masterPlayer.playWhenReady = true
            if (!masterPlayer.isPlaying) {
                masterPlayer.play()
            }
        }
    }

    fun toggleShuffle(
        currentSongs: List<Song>,
        currentSong: Song?,
        currentQueueSourceName: String,
        updateQueueCallback: (List<Song>) -> Unit
    ) {
        val nowMs = SystemClock.elapsedRealtime()
        if (shuffleToggleJob?.isActive == true) return
        if ((nowMs - lastShuffleToggleFinishedAtMs) < SHUFFLE_TOGGLE_COOLDOWN_MS) return

        val coroutineScope = scope ?: return
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            shuffleToggleJob = coroutineScope.launch {
                _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = true) }
                try {
                    val remoteMediaClient = castSession.remoteMediaClient
                    val newRepeatMode = if (remoteMediaClient?.mediaStatus?.getQueueRepeatMode() == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE) {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL
                    } else {
                        MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE
                    }
                    castStateHolder.castPlayer?.setRepeatMode(newRepeatMode)
                } finally {
                    lastShuffleToggleFinishedAtMs = SystemClock.elapsedRealtime()
                    _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = false) }
                    shuffleToggleJob = null
                }
            }
         } else {
            shuffleToggleJob = coroutineScope.launch {
                _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = true) }
                try {
                    val player = mediaController ?: return@launch
                    if (currentSongs.isEmpty()) return@launch

                    val isCurrentlyShuffled = _stablePlayerState.value.isShuffleEnabled

                    if (!isCurrentlyShuffled) {
                        // Enable Shuffle
                        if (!queueStateHolder.hasOriginalQueue()) {
                            queueStateHolder.setOriginalQueueOrder(currentSongs)
                            queueStateHolder.saveOriginalQueueState(currentSongs, currentQueueSourceName)
                        }

                        val currentMediaId = player.currentMediaItem?.mediaId ?: currentSong?.id
                        val playerCurrentIndex = player.currentMediaItemIndex
                            .takeIf { it in currentSongs.indices }
                        val currentIndex = when {
                            playerCurrentIndex != null && currentMediaId != null &&
                                currentSongs.getOrNull(playerCurrentIndex)?.id == currentMediaId -> playerCurrentIndex
                            playerCurrentIndex != null && currentMediaId == null -> playerCurrentIndex
                            currentMediaId != null ->
                                currentSongs.indexOfFirst { it.id == currentMediaId }.takeIf { it >= 0 }
                            else -> null
                        } ?: 0
                        val currentPosition = player.currentPosition
                        val wasPlaying = player.isPlaying
                        val currentMediaItem = player.currentMediaItem

                        // Run heavy shuffle work off main to keep UI and playback responsive.
                        val shuffledQueue = withContext(Dispatchers.Default) {
                            QueueUtils.buildAnchoredShuffleQueueSuspending(currentSongs, currentIndex)
                        }

                        // For large queues, use bulk replace (1 IPC call) instead of
                        // per-item moveMediaItem (n IPC calls) which freezes the UI.
                        if (currentSongs.size > BULK_REPLACE_THRESHOLD) {
                            val preservedReplacement = buildQueueSegments(
                                newQueue = shuffledQueue,
                                currentIndex = currentIndex,
                                currentMediaItem = currentMediaItem
                            )
                            val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                replacePlayerQueuePreservingCurrent(currentIndex, preparedSegments)
                            } == true

                            if (!replacedInPlace) {
                                val preparedQueue = buildQueueReplacement(
                                    newQueue = shuffledQueue,
                                    targetIndex = currentIndex,
                                    currentMediaItem = currentMediaItem
                                )
                                replacePlayerQueue(player, preparedQueue, currentPosition)
                            }
                        } else {
                            val reordered = reorderQueueInPlace(player, shuffledQueue)
                            if (!reordered) {
                                val preservedReplacement = buildQueueSegments(
                                    newQueue = shuffledQueue,
                                    currentIndex = currentIndex,
                                    currentMediaItem = currentMediaItem
                                )
                                val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                    replacePlayerQueuePreservingCurrent(currentIndex, preparedSegments)
                                } == true

                                if (!replacedInPlace) {
                                    val preparedQueue = buildQueueReplacement(
                                        newQueue = shuffledQueue,
                                        targetIndex = currentIndex,
                                        currentMediaItem = currentMediaItem
                                    )
                                    replacePlayerQueue(player, preparedQueue, currentPosition)
                                }
                            }
                        }

                        updateQueueCallback(shuffledQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = true) }
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }

                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(true)
                            }
                        }
                    } else {
                        // Disable Shuffle
                        scope?.launch {
                            if (userPreferencesRepository.persistentShuffleEnabledFlow.first()) {
                                userPreferencesRepository.setShuffleOn(false)
                            }
                        }

                        if (!queueStateHolder.hasOriginalQueue()) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        val originalQueue = queueStateHolder.originalQueueOrder
                        val wasPlaying = player.isPlaying
                        val currentPosition = player.currentPosition
                        val currentSongId = currentSong?.id ?: player.currentMediaItem?.mediaId
                        val currentMediaItem = player.currentMediaItem
                        val originalIndex = originalQueue.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }

                        if (originalIndex == null) {
                            _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                            return@launch
                        }

                        // Use bulk replace for large queues to avoid UI freeze
                        if (originalQueue.size > BULK_REPLACE_THRESHOLD) {
                            val preservedReplacement = buildQueueSegments(
                                newQueue = originalQueue,
                                currentIndex = originalIndex,
                                currentMediaItem = currentMediaItem
                            )
                            val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                replacePlayerQueuePreservingCurrent(originalIndex, preparedSegments)
                            } == true

                            if (!replacedInPlace) {
                                val preparedQueue = buildQueueReplacement(
                                    newQueue = originalQueue,
                                    targetIndex = originalIndex,
                                    currentMediaItem = currentMediaItem
                                )
                                replacePlayerQueue(player, preparedQueue, currentPosition)
                            }
                        } else {
                            val reordered = reorderQueueInPlace(player, originalQueue)
                            if (!reordered) {
                                val preservedReplacement = buildQueueSegments(
                                    newQueue = originalQueue,
                                    currentIndex = originalIndex,
                                    currentMediaItem = currentMediaItem
                                )
                                val replacedInPlace = preservedReplacement?.let { preparedSegments ->
                                    replacePlayerQueuePreservingCurrent(originalIndex, preparedSegments)
                                } == true

                                if (!replacedInPlace) {
                                    val preparedQueue = buildQueueReplacement(
                                        newQueue = originalQueue,
                                        targetIndex = originalIndex,
                                        currentMediaItem = currentMediaItem
                                    )
                                    replacePlayerQueue(player, preparedQueue, currentPosition)
                                }
                            }
                        }

                        updateQueueCallback(originalQueue)
                        _stablePlayerState.update { it.copy(isShuffleEnabled = false) }
                        if (wasPlaying && !player.isPlaying) {
                            player.play()
                        }
                    }
                } finally {
                    lastShuffleToggleFinishedAtMs = SystemClock.elapsedRealtime()
                    _stablePlayerState.update { it.copy(isShuffleTransitionInProgress = false) }
                    shuffleToggleJob = null
                }
            }
        }
    }

}
