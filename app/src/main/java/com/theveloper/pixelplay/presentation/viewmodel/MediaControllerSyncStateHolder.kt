package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Trace
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.EotStateHolder
import com.theveloper.pixelplay.data.media.MediaMapper
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Inject
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class PlaybackAudioMetadata(
    val mediaId: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null
)

private data class QueueTimelineSignature(
    val count: Int,
    val orderHash: Long,
    val firstMediaId: String?,
    val lastMediaId: String?
)

/**
 * Callbacks supplied by [PlayerViewModel] so the controller-sync cluster can reach
 * ViewModel-owned state (the media controller, UI state, the player sheet, track
 * volume, toasts/dialog events, lyrics loading, the EOT sleep-timer cancel, and the
 * manual shuffle toggle) without [MediaControllerSyncStateHolder] depending on the
 * ViewModel. Stored once via [MediaControllerSyncStateHolder.initialize].
 */
class ControllerSyncCallbacks(
    val scope: CoroutineScope,
    val getController: () -> MediaController?,
    val getUiState: () -> PlayerUiState,
    val updateUiState: ((PlayerUiState) -> PlayerUiState) -> Unit,
    val showSheet: () -> Unit,
    val setTrackVolume: (Float) -> Unit,
    val emitToast: suspend (String) -> Unit,
    val showNoInternetDialog: suspend () -> Unit,
    val ensureTelegramObservers: () -> Unit,
    val cancelSleepTimerForEot: () -> Unit,
    val resetLyricsSearchState: () -> Unit,
    val loadLyricsForCurrentSong: () -> Unit,
    val toggleShuffle: () -> Unit,
)

/**
 * Owns the MediaController-to-UI synchronization extracted from [PlayerViewModel]:
 * the decomposed Player.Listener registrations (volume, playback state, media-item
 * and timeline transitions, tracks/metadata/shuffle/repeat), the queue snapshot
 * rebuild with its timeline-signature dedupe, repeat-mode restore, and the playback
 * audio-metadata (format/bitrate/sample-rate) probing shown in the player file info.
 */
@OptIn(UnstableApi::class)
@ViewModelScoped
class MediaControllerSyncStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    private val mediaMapper: MediaMapper,
    private val playbackStateHolder: PlaybackStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val castStateHolder: CastStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val themeStateHolder: ThemeStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val playbackDispatchStateHolder: PlaybackDispatchStateHolder,
    @param:ApplicationContext private val context: Context,
) {

    private lateinit var cb: ControllerSyncCallbacks

    fun initialize(callbacks: ControllerSyncCallbacks) {
        cb = callbacks
    }

    // All Player.Listener instances registered by the decomposed setup*Listeners()
    // helpers. Tracked together so they can be removed in one pass on re-setup and
    // in onCleared().
    private val mediaControllerPlaybackListeners = mutableListOf<Player.Listener>()

    private var pendingRepeatMode: Int? = null
    private var bufferingDebounceJob: Job? = null
    private var transitionSchedulerJob: Job? = null

    private var metadataProbeJob: Job? = null
    private var metadataProbeMediaId: String? = null

    private val _playbackAudioMetadata = MutableStateFlow(PlaybackAudioMetadata())
    val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata> = _playbackAudioMetadata.asStateFlow()

    private var lastQueueUpdateRequestId = 0L
    private var lastQueueSignature: QueueTimelineSignature? = null
    private var lastQueueUpdateJob: Job? = null

    /** Cancels the in-flight media-item-transition handler (used by playback dispatch). */
    fun cancelTransitionScheduler() {
        transitionSchedulerJob?.cancel()
    }

    fun resolveSongFromMediaItem(
        mediaItem: MediaItem,
        allSongsById: Map<String, Song>? = null
    ): Song? {
        val resolvedSong =
            allSongsById?.get(mediaItem.mediaId)
                ?: libraryStateHolder.allSongsById.value[mediaItem.mediaId]
                ?: cb.getUiState().currentPlaybackQueue.find { it.id == mediaItem.mediaId }
                ?: mediaMapper.resolveSongFromMediaItem(mediaItem)

        return resolvedSong?.let { normalizeArtworkForResolvedSong(it, mediaItem) }
    }

    private fun normalizeArtworkForResolvedSong(song: Song, mediaItem: MediaItem): Song {
        val metadataArtwork =
            mediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }
                ?: mediaItem.mediaMetadata.extras
                    ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
                    ?.takeIf { it.isNotBlank() }

        return when {
            metadataArtwork == null && song.albumArtUriString != null -> song.copy(albumArtUriString = null)
            metadataArtwork != null && song.albumArtUriString != metadataArtwork ->
                song.copy(albumArtUriString = metadataArtwork)
            else -> song
        }
    }

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: cb.getController() ?: return
        val requestId = ++lastQueueUpdateRequestId
        lastQueueUpdateJob?.cancel()
        lastQueueUpdateJob = cb.scope.launch {
            // Debounce slightly to handle rapid-fire timeline events
            delay(100)

            val isWindowed = dualPlayerEngine.isUsingWindowedQueue()
            val mediaItems = if (isWindowed) {
                dualPlayerEngine.getFullQueue()
            } else {
                val timeline = currentMediaController.currentTimeline
                val windowCount = timeline.windowCount
                val list = ArrayList<MediaItem>(windowCount)
                val window = Timeline.Window()
                for (i in 0 until windowCount) {
                    list.add(timeline.getWindow(i, window).mediaItem)
                }
                list
            }

            val count = mediaItems.size
            if (count == 0) {
                if (requestId != lastQueueUpdateRequestId) return@launch
                val emptySignature = QueueTimelineSignature(
                    count = 0,
                    orderHash = 0L,
                    firstMediaId = null,
                    lastMediaId = null
                )
                if (lastQueueSignature != emptySignature) {
                    lastQueueSignature = emptySignature
                    cb.updateUiState { it.copy(currentPlaybackQueue = persistentListOf()) }
                }
                return@launch
            }

            var orderHash = 1125899906842597L
            var firstMediaId: String? = null
            var lastMediaId: String? = null

            for (i in 0 until count) {
                val mediaItem = mediaItems[i]
                val mediaId = mediaItem.mediaId
                if (i == 0) firstMediaId = mediaId
                if (i == count - 1) lastMediaId = mediaId
                orderHash = (orderHash * 31) + mediaId.hashCode()
                if (i % 500 == 0) kotlinx.coroutines.yield()
            }

            val signature = QueueTimelineSignature(
                count = count,
                orderHash = orderHash,
                firstMediaId = firstMediaId,
                lastMediaId = lastMediaId
            )
            if (requestId != lastQueueUpdateRequestId) return@launch
            if (signature == lastQueueSignature) return@launch

            val allSongsById = libraryStateHolder.allSongsById.value

            val queue = withContext(Dispatchers.Default) {
                mediaItems.mapNotNull { mediaItem ->
                    resolveSongFromMediaItem(mediaItem, allSongsById)
                }
            }

            if (requestId != lastQueueUpdateRequestId) return@launch

            lastQueueSignature = signature
            cb.updateUiState { it.copy(currentPlaybackQueue = queue.toPlaybackQueue()) }
            if (queue.isNotEmpty()) {
                cb.showSheet()
            }
        }
    }

    fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        val controller = cb.getController()
        if (controller == null) {
            pendingRepeatMode = mode
            return
        }

        if (controller.repeatMode != mode) {
            controller.repeatMode = mode
        }
        pendingRepeatMode = null
    }

    fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    private fun resetPlaybackAudioMetadata() {
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        _playbackAudioMetadata.value = PlaybackAudioMetadata()
    }

    private fun preparePlaybackAudioMetadataForMedia(mediaId: String?) {
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        _playbackAudioMetadata.value = PlaybackAudioMetadata(mediaId = mediaId)
    }

    private fun extractBitDepthFromPcmEncoding(pcmEncoding: Int): Int? {
        return when (pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
    }

    private fun refreshPlaybackAudioMetadata(player: Player, tracks: Tracks = player.currentTracks) {
        runCatching {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId == null) {
                resetPlaybackAudioMetadata()
                return@runCatching
            }

            val selectedAudioFormat = tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter { index -> group.isTrackSelected(index) }
                        .map { index -> group.getTrackFormat(index) }
                }
                .firstOrNull()

            val current = _playbackAudioMetadata.value.takeIf { it.mediaId == mediaId }
            val metadata = PlaybackAudioMetadata(
                mediaId = mediaId,
                mimeType = selectedAudioFormat?.sampleMimeType
                    ?: selectedAudioFormat?.containerMimeType
                    ?: current?.mimeType,
                bitrate = selectedAudioFormat?.bitrate?.takeIf { it > 0 }
                    ?: current?.bitrate,
                sampleRate = selectedAudioFormat?.sampleRate?.takeIf { it > 0 }
                    ?: current?.sampleRate,
                channelCount = selectedAudioFormat?.channelCount?.takeIf { it > 0 } ?: current?.channelCount,
                bitDepth = selectedAudioFormat?.pcmEncoding?.let(::extractBitDepthFromPcmEncoding) ?: current?.bitDepth
            )

            _playbackAudioMetadata.value = metadata
            maybeProbeMissingPlaybackAudioMetadata(player, metadata)
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to refresh playback audio metadata")
        }
    }

    private fun maybeProbeMissingPlaybackAudioMetadata(
        player: Player,
        metadata: PlaybackAudioMetadata
    ) {
        val shouldProbe = metadata.mimeType.isNullOrBlank() || metadata.bitrate == null || metadata.sampleRate == null
        if (!shouldProbe) return

        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val uri = mediaItem.localConfiguration?.uri ?: return

        if (metadataProbeMediaId == mediaId && metadataProbeJob?.isActive == true) return

        metadataProbeJob?.cancel()
        metadataProbeMediaId = mediaId
        metadataProbeJob = cb.scope.launch(Dispatchers.IO) {
            val probedMetadata = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val mimeType = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.isNotBlank() }
                        ?: context.contentResolver.getType(uri)
                    val bitrate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    val sampleRate = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                            ?.toIntOrNull()
                            ?.takeIf { it > 0 }
                    } else null
                    PlaybackAudioMetadata(
                        mediaId = mediaId,
                        mimeType = mimeType,
                        bitrate = bitrate,
                        sampleRate = sampleRate
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull() ?: return@launch

            _playbackAudioMetadata.update { current ->
                val isSameMediaItem = current.mediaId == mediaId
                if (!isSameMediaItem) return@update current
                current.copy(
                    mimeType = current.mimeType ?: probedMetadata.mimeType,
                    bitrate = current.bitrate ?: probedMetadata.bitrate,
                    sampleRate = current.sampleRate ?: probedMetadata.sampleRate
                )
            }
        }
    }

    private fun isRemoteSessionControllingPlayback(): Boolean {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
        return remoteClient != null &&
                (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value)
    }

    private fun syncPlaybackPositionFromPlayer(
        mediaId: String?,
        reportedPositionMs: Long
    ): Long {
        playbackStateHolder.syncCurrentPositionFromPlayer(mediaId, reportedPositionMs)
        return playbackStateHolder.currentPosition.value
    }

    private fun syncDisplayedMediaItemIfChanged(player: Player) {
        if (isRemoteSessionControllingPlayback()) return

        val mediaItem = player.currentMediaItem ?: return
        val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
        val currentIndex = playbackStateHolder.stablePlayerState.value.currentMediaItemIndex
        val expectedIndex = if (dualPlayerEngine.isUsingWindowedQueue()) {
            dualPlayerEngine.getCurrentAbsoluteIndex()
        } else {
            player.currentMediaItemIndex
        }
        if (currentSongId == mediaItem.mediaId && currentIndex == expectedIndex) return

        playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem.mediaId)
        preparePlaybackAudioMetadataForMedia(mediaItem.mediaId)
        transitionSchedulerJob?.cancel()
        lyricsStateHolder.cancelLoading()
        cb.resetLyricsSearchState()

        val song = resolveSongFromMediaItem(mediaItem)
        val currentPosition = player.currentPosition.coerceAtLeast(0L)
        val resolvedDuration = if (song != null) {
            playbackStateHolder.resolveDurationForPlaybackState(
                reportedDurationMs = player.duration,
                songDurationHintMs = song.duration.coerceAtLeast(0L),
                currentPositionMs = currentPosition
            )
        } else {
            0L
        }

        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                currentMediaItemIndex = expectedIndex,
                totalDuration = resolvedDuration,
                lyrics = null,
                isLoadingLyrics = song != null,
                isPlaying = player.isPlaying,
                playWhenReady = player.playWhenReady
            )
        }
        syncPlaybackPositionFromPlayer(mediaItem.mediaId, currentPosition)

        song?.let { currentSongValue ->
            cb.scope.launch {
                val uri = currentSongValue.albumArtUriString?.toUri()
                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
            }
            cb.loadLyricsForCurrentSong()
        }
    }

    /**
     * Wires the [MediaController] into the playback UI state. Decomposed from a single
     * ~300-line block into a one-time state sync plus a set of focused, structured
     * sub-listener registrations so each playback concern reads in isolation:
     *  - [applyInitialControllerState]: snapshot the controller's current state on attach
     *  - [setupVolumeListeners]: track volume changes
     *  - [setupPlaybackListeners]: play/pause, playWhenReady, playback-state transitions
     *  - [setupTransitionListeners]: media-item and timeline transitions
     *  - [setupMetadataListeners]: tracks, metadata, shuffle and repeat mode
     */
    fun setupMediaControllerListeners(playerCtrl: MediaController?) {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        if (playerCtrl == null) return Trace.endSection()
        applyInitialControllerState(playerCtrl)
        clearMediaControllerPlaybackListeners(playerCtrl)
        setupVolumeListeners(playerCtrl)
        setupPlaybackListeners(playerCtrl)
        setupTransitionListeners(playerCtrl)
        setupMetadataListeners(playerCtrl)
        Trace.endSection()
    }

    /** Registers [listener] on [playerCtrl] and tracks it for later removal. */
    private fun registerMediaControllerListener(playerCtrl: MediaController, listener: Player.Listener) {
        mediaControllerPlaybackListeners.add(listener)
        playerCtrl.addListener(listener)
    }

    /** Removes and forgets every listener registered via [registerMediaControllerListener]. */
    fun clearMediaControllerPlaybackListeners(controller: MediaController?) {
        mediaControllerPlaybackListeners.forEach { listener ->
            controller?.removeListener(listener)
        }
        mediaControllerPlaybackListeners.clear()
    }

    /** One-time snapshot of the controller's current state when it first attaches. */
    private fun applyInitialControllerState(playerCtrl: MediaController) {
        cb.setTrackVolume(playerCtrl.volume)
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying,
                playWhenReady = playerCtrl.playWhenReady
            )
        }
        preparePlaybackAudioMetadataForMedia(playerCtrl.currentMediaItem?.mediaId)
        refreshPlaybackAudioMetadata(playerCtrl)

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            playbackStateHolder.ensureCurrentPlaybackOccurrence(mediaItem.mediaId)
            val song = resolveSongFromMediaItem(mediaItem)

            if (song != null) {
                val initialPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                    reportedDurationMs = playerCtrl.duration,
                    songDurationHintMs = song.duration.coerceAtLeast(0L),
                    currentPositionMs = initialPosition
                )
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        totalDuration = resolvedDuration
                    )
                }
                syncPlaybackPositionFromPlayer(mediaItem.mediaId, initialPosition)
                cb.scope.launch {
                    val uri = song.albumArtUriString?.toUri()
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                }
                cb.loadLyricsForCurrentSong()
                if (playerCtrl.isPlaying) {
                    cb.showSheet()
                    playbackStateHolder.startProgressUpdates()
                }
            } else {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
                playbackStateHolder.clearCurrentPositionHints()
                playbackStateHolder.setCurrentPosition(0L)
                resetPlaybackAudioMetadata()
            }
        }
    }

    /** Volume changes coming back from the player/session. */
    private fun setupVolumeListeners(playerCtrl: MediaController) {
        registerMediaControllerListener(playerCtrl, object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                cb.setTrackVolume(volume)
            }
        })
    }

    /** Play/pause, playWhenReady and playback-state lifecycle. */
    private fun setupPlaybackListeners(playerCtrl: MediaController) {
        registerMediaControllerListener(playerCtrl, object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = isPlaying,
                        playWhenReady = playerCtrl.playWhenReady
                    )
                }
                val shouldKeepSampling = playerCtrl.playWhenReady &&
                    playerCtrl.playbackState != Player.STATE_IDLE &&
                    playerCtrl.playbackState != Player.STATE_ENDED
                if (isPlaying || shouldKeepSampling) {
                    cb.showSheet()
                    if (isPlaying) {
                        playbackDispatchStateHolder.clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    }
                    playbackStateHolder.startProgressUpdates()
                } else {
                    playbackStateHolder.stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, pausedPosition)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState { it.copy(playWhenReady = playWhenReady) }
                if (
                    playWhenReady &&
                    playerCtrl.playbackState != Player.STATE_IDLE &&
                    playerCtrl.playbackState != Player.STATE_ENDED
                ) {
                    playbackStateHolder.startProgressUpdates()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl)
                syncDisplayedMediaItemIfChanged(playerCtrl)

                // Debounce buffering state to avoid flickering
                bufferingDebounceJob?.cancel()
                if (playbackState == Player.STATE_BUFFERING) {
                    bufferingDebounceJob = cb.scope.launch {
                        delay(500) // Wait 500ms before showing buffering indicator
                        playbackStateHolder.updateStablePlayerState { state ->
                            state.copy(isBuffering = true)
                        }
                    }
                } else {
                    // Immediately hide buffering when not buffering
                    playbackStateHolder.updateStablePlayerState { state ->
                        state.copy(isBuffering = false)
                    }
                }

                if (playbackState == Player.STATE_READY) {
                    playbackDispatchStateHolder.clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    val readyPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    val songDurationHint = playbackStateHolder.stablePlayerState.value.currentSong?.duration ?: 0L
                    val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                        reportedDurationMs = playerCtrl.duration,
                        songDurationHintMs = songDurationHint,
                        currentPositionMs = readyPosition
                    )
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, readyPosition)
                    playbackStateHolder.updateStablePlayerState { it.copy(totalDuration = resolvedDuration) }
                    playbackStateHolder.startProgressUpdates()
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    playbackDispatchStateHolder.clearPreparingSongIfMatching()
                    if (!castStateHolder.isCastConnecting.value && !castStateHolder.isRemotePlaybackActive.value) {
                        lyricsStateHolder.cancelLoading()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = null,
                                isPlaying = false,
                                playWhenReady = false,
                                lyrics = null,
                                isLoadingLyrics = false,
                                totalDuration = 0L
                            )
                        }
                        playbackStateHolder.clearCurrentPositionHints()
                        playbackStateHolder.setCurrentPosition(0L)
                        resetPlaybackAudioMetadata()
                    }
                }
            }
        })
    }

    /** Media-item and timeline transitions (incl. EOT timer + Telegram offline guard). */
    private fun setupTransitionListeners(playerCtrl: MediaController) {
        registerMediaControllerListener(playerCtrl, object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem?.mediaId)
                preparePlaybackAudioMetadataForMedia(mediaItem?.mediaId)
                transitionSchedulerJob?.cancel()
                lyricsStateHolder.cancelLoading()
                transitionSchedulerJob = cb.scope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (sleepTimerStateHolder.isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = libraryStateHolder.allSongsById.value[previousSongId]?.title
                                ?: context.getString(R.string.player_view_model_default_track_title)

                            cb.scope.launch {
                                cb.emitToast(
                                    context.getString(R.string.player_view_model_playback_stopped_eot, finishedSongTitle),
                                )
                            }
                            cb.cancelSleepTimerForEot()
                        }
                    }

                    mediaItem?.let { transitionedItem ->
                        val song = resolveSongFromMediaItem(transitionedItem)

                        // Offline check for Telegram songs
                        if (song?.contentUriString?.startsWith("telegram:") == true) {
                            cb.ensureTelegramObservers()
                            val isOnline = connectivityStateHolder.isOnline.value
                            if (!isOnline) {
                                val fileId = song.telegramFileId
                                if (fileId != null) {
                                    val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                                    if (!isCached) {
                                        playerCtrl.pause()
                                        cb.showNoInternetDialog()
                                    }
                                }
                            }
                        }

                        val resolvedDuration = if (song != null) {
                            playbackStateHolder.resolveDurationForPlaybackState(
                                reportedDurationMs = playerCtrl.duration,
                                songDurationHintMs = song.duration.coerceAtLeast(0L),
                                currentPositionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                            )
                        } else {
                            0L
                        }
                        cb.resetLyricsSearchState()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = song,
                                currentMediaItemIndex = if (dualPlayerEngine.isUsingWindowedQueue()) {
                                    dualPlayerEngine.getCurrentAbsoluteIndex()
                                } else {
                                    playerCtrl.currentMediaItemIndex
                                },
                                totalDuration = resolvedDuration,
                                lyrics = null,
                                isLoadingLyrics = song != null,
                                playWhenReady = playerCtrl.playWhenReady
                            )
                        }
                        val transitionPosition = syncPlaybackPositionFromPlayer(
                            transitionedItem.mediaId,
                            playerCtrl.currentPosition.coerceAtLeast(0L)
                        )

                        song?.let { currentSongValue ->
                            launch {
                                val uri = currentSongValue.albumArtUriString?.toUri()
                                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                            }
                            cb.loadLyricsForCurrentSong()
                        }
                    } ?: run {
                        if (!castStateHolder.isCastConnecting.value && !castStateHolder.isRemotePlaybackActive.value) {
                            lyricsStateHolder.cancelLoading()
                            playbackStateHolder.updateStablePlayerState {
                                it.copy(
                                    currentSong = null,
                                    isPlaying = false,
                                    playWhenReady = false,
                                    lyrics = null,
                                    isLoadingLyrics = false,
                                    totalDuration = 0L
                                )
                            }
                            playbackStateHolder.clearCurrentPositionHints()
                            resetPlaybackAudioMetadata()
                        }
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                syncDisplayedMediaItemIfChanged(playerCtrl)
                // Skip updates during crossfade transitions to prevent UI freeze and jumpy state.
                if (dualPlayerEngine.isTransitionRunning()) return

                transitionSchedulerJob?.cancel()

                // Only refresh full queue on structural changes or source updates (metadata)
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED ||
                    reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) {
                    updateCurrentPlaybackQueueFromPlayer(cb.getController())
                    dualPlayerEngine.triggerAdjacentPreResolution()
                }
            }
        })
    }

    /** Track/metadata changes plus shuffle and repeat-mode reconciliation. */
    private fun setupMetadataListeners(playerCtrl: MediaController) {
        registerMediaControllerListener(playerCtrl, object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl, tracks)
            }
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                syncDisplayedMediaItemIfChanged(playerCtrl)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // IMPORTANT: We don't use ExoPlayer's shuffle mode anymore
                // Instead, we manually shuffle the queue to fix crossfade issues
                // If ExoPlayer's shuffle gets enabled (e.g., from media button), turn it off and use our toggle
                if (shuffleModeEnabled) {
                    playerCtrl.shuffleModeEnabled = false
                    // Trigger our manual shuffle instead
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        cb.toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                cb.scope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
        })
    }
}
