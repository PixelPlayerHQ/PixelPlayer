package com.theveloper.pixelplay.data.service.wear

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionCommand
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor
import com.theveloper.pixelplay.shared.WearBrowseRequest
import com.theveloper.pixelplay.shared.WearBrowseResponse
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearFavoriteStateEntry
import com.theveloper.pixelplay.shared.WearFavoriteSyncRequest
import com.theveloper.pixelplay.shared.WearFavoriteSyncResponse
import com.theveloper.pixelplay.shared.WearLibraryItem
import com.theveloper.pixelplay.shared.WearLibraryState
import com.theveloper.pixelplay.shared.WearPlaybackCommand
import com.theveloper.pixelplay.shared.WearPlaybackResult
import com.theveloper.pixelplay.shared.WearThemePalette
import com.theveloper.pixelplay.shared.WearTransferMetadata
import com.theveloper.pixelplay.shared.WearTransferProgress
import com.theveloper.pixelplay.shared.WearTransferRequest
import com.theveloper.pixelplay.shared.WearVolumeCommand
import com.theveloper.pixelplay.shared.WearVolumeState
import com.theveloper.pixelplay.utils.MediaItemBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * WearableListenerService that receives commands from the Wear OS watch app.
 * Handles playback commands (play, pause, next, prev, etc.), volume commands,
 * and library browse requests.
 *
 * Commands are received via the Wear Data Layer MessageClient and forwarded
 * to the MusicService via MediaController or processed directly.
 */
@AndroidEntryPoint
class WearCommandReceiver : WearableListenerService() {

    @Inject lateinit var musicRepository: MusicRepository
    @Inject lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject lateinit var playlistPreferencesRepository: PlaylistPreferencesRepository
    @Inject lateinit var dualPlayerEngine: DualPlayerEngine
    @Inject lateinit var colorSchemeProcessor: ColorSchemeProcessor
    @Inject lateinit var transferStateStore: PhoneWatchTransferStateStore
    @Inject lateinit var transferCancellationStore: PhoneWatchTransferCancellationStore

    private val json = Json { ignoreUnknownKeys = true }
    private var mediaController: MediaController? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var cachedCastPlayer: CastPlayer? = null
    private var cachedCastSession: CastSession? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val albumPaletteSeedCache = ConcurrentHashMap<Long, Int>()
    private val albumArtworkTransferCache = ConcurrentHashMap<Long, ByteArray>()

    companion object {
        private const val TAG = "WearCommandReceiver"
        private const val MAX_BROWSE_SONGS = 500
        private const val MAX_ALBUMS = 200
        private const val MAX_ARTISTS = 200
        private val EXPLICIT_PLAY_RETRY_DELAYS_MS = longArrayOf(180L, 700L)
        private const val TRANSFER_CHUNK_SIZE = 8192
        private const val PROGRESS_UPDATE_INTERVAL_BYTES = 65536L
        private const val TRANSFER_ARTWORK_MAX_DIMENSION = 1024
        private const val TRANSFER_ARTWORK_QUALITY = 95
        private const val TRANSFER_ARTWORK_MAX_BYTES = 1_500_000
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Timber.tag(TAG).d(
            "Received message path=%s sourceNodeId=%s bytes=%d",
            messageEvent.path,
            messageEvent.sourceNodeId,
            messageEvent.data.size
        )

        when (messageEvent.path) {
            WearDataPaths.PLAYBACK_COMMAND -> handlePlaybackCommand(messageEvent)
            WearDataPaths.VOLUME_COMMAND -> handleVolumeCommand(messageEvent)
            WearDataPaths.BROWSE_REQUEST -> handleBrowseRequest(messageEvent)
            WearDataPaths.TRANSFER_REQUEST -> runBlocking(Dispatchers.IO) {
                handleTransferRequest(messageEvent)
            }
            WearDataPaths.TRANSFER_PROGRESS -> handleWatchTransferProgress(messageEvent)
            WearDataPaths.TRANSFER_CANCEL -> handleTransferCancel(messageEvent)
            WearDataPaths.WATCH_LIBRARY_STATE -> handleWatchLibraryState(messageEvent)
            WearDataPaths.FAVORITES_SYNC_REQUEST -> handleFavoriteSyncRequest(messageEvent)
            else -> Timber.tag(TAG).w("Unknown message path: ${messageEvent.path}")
        }
    }

    private fun handleWatchLibraryState(messageEvent: MessageEvent) {
        val libraryStateJson = String(messageEvent.data, Charsets.UTF_8)
        runCatching {
            json.decodeFromString<WearLibraryState>(libraryStateJson)
        }.onSuccess { libraryState ->
            transferStateStore.updateWatchSongIds(
                nodeId = messageEvent.sourceNodeId,
                songIds = libraryState.songIds.toSet(),
            )
        }.onFailure { error ->
            Timber.tag(TAG).e(error, "Failed to parse watch library state")
        }
    }

    private fun handleWatchTransferProgress(messageEvent: MessageEvent) {
        val progressJson = String(messageEvent.data, Charsets.UTF_8)
        runCatching {
            json.decodeFromString<WearTransferProgress>(progressJson)
        }.onSuccess { progress ->
            transferStateStore.markProgress(
                requestId = progress.requestId,
                songId = progress.songId,
                bytesTransferred = progress.bytesTransferred,
                totalBytes = progress.totalBytes,
                status = progress.status,
                error = progress.error,
            )
            if (
                progress.status == WearTransferProgress.STATUS_FAILED &&
                progress.error == WearTransferProgress.ERROR_ALREADY_ON_WATCH
            ) {
                transferStateStore.markSongPresentOnWatch(
                    nodeId = messageEvent.sourceNodeId,
                    songId = progress.songId,
                )
            }
        }.onFailure { error ->
            Timber.tag(TAG).e(error, "Failed to parse watch transfer progress")
        }
    }

    private fun handlePlaybackCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearPlaybackCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse playback command")
            return
        }

        Timber.tag(TAG).d("Playback command: ${command.action}")

        if (
            command.action == WearPlaybackCommand.TOGGLE_FAVORITE &&
            !command.songId.isNullOrBlank()
        ) {
            handleSongFavoriteCommand(command, messageEvent.sourceNodeId)
            return
        }

        when (command.action) {
            WearPlaybackCommand.PLAY_ITEM -> {
                handlePlayItem(command, messageEvent.sourceNodeId)
            }
            WearPlaybackCommand.PLAY_FROM_CONTEXT -> {
                handlePlayFromContext(command)
            }
            WearPlaybackCommand.PLAY_NEXT_FROM_CONTEXT -> {
                handleInsertIntoQueue(command, playNext = true)
            }
            WearPlaybackCommand.ADD_TO_QUEUE_FROM_CONTEXT -> {
                handleInsertIntoQueue(command, playNext = false)
            }
            WearPlaybackCommand.PLAY_QUEUE_INDEX -> {
                runOnMainThread {
                    if (!handlePlaybackCommandViaCast(command)) {
                        handlePlayQueueIndex(command)
                    }
                }
            }
            WearPlaybackCommand.SET_SLEEP_TIMER_DURATION,
            WearPlaybackCommand.SET_SLEEP_TIMER_END_OF_TRACK,
            WearPlaybackCommand.CANCEL_SLEEP_TIMER -> {
                handleSleepTimerCommand(command)
            }
            else -> {
                runOnMainThread {
                    if (handlePlaybackCommandViaCast(command)) {
                        return@runOnMainThread
                    }
                    getOrBuildMediaController { controller ->
                        when (command.action) {
                            WearPlaybackCommand.PLAY -> controller.play()
                            WearPlaybackCommand.PAUSE -> controller.pause()
                            WearPlaybackCommand.TOGGLE_PLAY_PAUSE -> {
                                if (controller.isPlaying) controller.pause() else controller.play()
                            }
                            WearPlaybackCommand.NEXT -> controller.seekToNext()
                            WearPlaybackCommand.PREVIOUS -> controller.seekToPrevious()
                            WearPlaybackCommand.TOGGLE_SHUFFLE -> {
                                controller.sendCustomCommand(
                                    SessionCommand(
                                        MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
                                        Bundle.EMPTY
                                    ),
                                    Bundle.EMPTY
                                )
                            }
                            WearPlaybackCommand.CYCLE_REPEAT -> {
                                controller.sendCustomCommand(
                                    SessionCommand(
                                        MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
                                        Bundle.EMPTY
                                    ),
                                    Bundle.EMPTY
                                )
                            }
                            WearPlaybackCommand.TOGGLE_FAVORITE -> {
                                val targetEnabled = command.targetEnabled
                                if (targetEnabled == null) {
                                    val sessionCommand = SessionCommand(
                                        MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
                                        Bundle.EMPTY
                                    )
                                    controller.sendCustomCommand(sessionCommand, Bundle.EMPTY)
                                } else {
                                    val args = Bundle().apply {
                                        putBoolean(MusicNotificationProvider.EXTRA_FAVORITE_ENABLED, targetEnabled)
                                    }
                                    val sessionCommand = SessionCommand(
                                        MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
                                        Bundle.EMPTY
                                    )
                                    controller.sendCustomCommand(sessionCommand, args)
                                }
                            }
                            else -> Timber.tag(TAG).w("Unknown playback action: ${command.action}")
                        }
                    }
                }
            }
        }
    }

    private fun handleSongFavoriteCommand(command: WearPlaybackCommand, targetNodeId: String) {
        val songId = command.songId
        if (songId.isNullOrBlank()) {
            scope.launch {
                sendPlaybackResult(
                    nodeId = targetNodeId,
                    requestId = command.requestId,
                    action = command.action,
                    songId = null,
                    success = false,
                    error = "Missing song id",
                )
            }
            return
        }

        scope.launch {
            runCatching {
                val targetFavoriteState = command.targetEnabled
                    ?: !musicRepository.getFavoriteSongIdsOnce().contains(songId)
                musicRepository.setFavoriteStatus(songId, targetFavoriteState)
                sendPlaybackResult(
                    nodeId = targetNodeId,
                    requestId = command.requestId,
                    action = command.action,
                    songId = songId,
                    success = true,
                )
            }.onFailure { error ->
                sendPlaybackResult(
                    nodeId = targetNodeId,
                    requestId = command.requestId,
                    action = command.action,
                    songId = songId,
                    success = false,
                    error = error.message ?: "Failed to update favorite",
                )
            }
        }
    }

    private fun handlePlayItem(command: WearPlaybackCommand, targetNodeId: String) {
        val songId = command.songId
        if (songId.isNullOrBlank()) {
            scope.launch {
                sendPlaybackResult(
                    nodeId = targetNodeId,
                    requestId = command.requestId,
                    action = command.action,
                    songId = null,
                    success = false,
                    error = "Missing song id",
                )
            }
            return
        }

        scope.launch {
            try {
                val song = resolveSongById(songId)
                if (song == null) {
                    sendPlaybackResult(
                        nodeId = targetNodeId,
                        requestId = command.requestId,
                        action = command.action,
                        songId = songId,
                        success = false,
                        error = "This song is no longer available on phone",
                    )
                    return@launch
                }

                val cloudReady = ensureStartSongCloudUriResolved(song)
                if (!cloudReady) {
                    sendPlaybackResult(
                        nodeId = targetNodeId,
                        requestId = command.requestId,
                        action = command.action,
                        songId = song.id,
                        success = false,
                        error = "This song could not be opened on phone",
                    )
                    return@launch
                }

                val mediaItem = MediaItemBuilder.build(song)
                getOrBuildMediaController { controller ->
                    startExplicitPlayback(controller, song.id) {
                        controller.setMediaItem(mediaItem)
                        controller.prepare()
                    }
                    scope.launch {
                        sendPlaybackResult(
                            nodeId = targetNodeId,
                            requestId = command.requestId,
                            action = command.action,
                            songId = song.id,
                            success = true,
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to handle PLAY_ITEM")
                sendPlaybackResult(
                    nodeId = targetNodeId,
                    requestId = command.requestId,
                    action = command.action,
                    songId = songId,
                    success = false,
                    error = e.message ?: "Failed to start playback on phone",
                )
            }
        }
    }

    private fun handlePlaybackCommandViaCast(command: WearPlaybackCommand): Boolean {
        val castSession = resolveActiveCastSession() ?: return false
        val remoteClient = castSession.remoteMediaClient ?: return false
        val remotePlayerState = remoteClient.playerState
        val hasRemotePlaybackState =
            remoteClient.mediaStatus != null &&
                remotePlayerState != MediaStatus.PLAYER_STATE_IDLE &&
                remotePlayerState != MediaStatus.PLAYER_STATE_UNKNOWN
        if (!hasRemotePlaybackState) {
            return false
        }

        val castPlayer = getOrCreateCastPlayer(castSession)
        val handled = when (command.action) {
            WearPlaybackCommand.PLAY -> {
                castPlayer.play()
                true
            }

            WearPlaybackCommand.PAUSE -> {
                castPlayer.pause()
                true
            }

            WearPlaybackCommand.TOGGLE_PLAY_PAUSE -> {
                if (remoteClient.isPlaying) castPlayer.pause() else castPlayer.play()
                true
            }

            WearPlaybackCommand.NEXT -> {
                castPlayer.next()
                true
            }

            WearPlaybackCommand.PREVIOUS -> {
                castPlayer.previous()
                true
            }

            WearPlaybackCommand.CYCLE_REPEAT -> {
                val currentRepeatMode = remoteClient.mediaStatus?.queueRepeatMode ?: MediaStatus.REPEAT_MODE_REPEAT_OFF
                val nextRepeatMode = when (currentRepeatMode) {
                    MediaStatus.REPEAT_MODE_REPEAT_OFF -> MediaStatus.REPEAT_MODE_REPEAT_ALL
                    MediaStatus.REPEAT_MODE_REPEAT_ALL -> MediaStatus.REPEAT_MODE_REPEAT_SINGLE
                    MediaStatus.REPEAT_MODE_REPEAT_SINGLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                    MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                    else -> MediaStatus.REPEAT_MODE_REPEAT_OFF
                }
                castPlayer.setRepeatMode(nextRepeatMode)
                true
            }

            WearPlaybackCommand.PLAY_QUEUE_INDEX -> {
                val requestedIndex = command.queueIndex
                if (requestedIndex == null || requestedIndex < 0) {
                    Timber.tag(TAG).w("PLAY_QUEUE_INDEX missing/invalid index for cast: ${command.queueIndex}")
                } else {
                    val queueItems = remoteClient.mediaStatus?.queueItems.orEmpty()
                    val queueItem = queueItems.getOrNull(requestedIndex)
                    if (queueItem != null) {
                        castPlayer.jumpToItem(queueItem.itemId, 0L)
                    } else {
                        Timber.tag(TAG).w(
                            "PLAY_QUEUE_INDEX out of bounds for cast: index=%d count=%d",
                            requestedIndex,
                            queueItems.size
                        )
                    }
                }
                true
            }

            else -> false
        }

        if (handled) {
            Timber.tag(TAG).d("Handled wear command via cast: ${command.action}")
        }
        return handled
    }

    private fun resolveActiveCastSession(): CastSession? {
        return try {
            CastContext.getSharedInstance(this).sessionManager.currentCastSession
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to get active Cast session")
            null
        }
    }

    private fun getOrCreateCastPlayer(session: CastSession): CastPlayer {
        val existingPlayer = cachedCastPlayer
        if (existingPlayer != null && cachedCastSession === session) {
            return existingPlayer
        }

        cachedCastPlayer?.release()
        return CastPlayer(session, contentResolver).also { createdPlayer ->
            cachedCastPlayer = createdPlayer
            cachedCastSession = session
        }
    }

    private fun handlePlayQueueIndex(command: WearPlaybackCommand) {
        val index = command.queueIndex
        if (index == null || index < 0) {
            Timber.tag(TAG).w("PLAY_QUEUE_INDEX missing/invalid index: ${command.queueIndex}")
            return
        }
        getOrBuildMediaController { controller ->
            val itemCount = controller.mediaItemCount
            if (index >= itemCount) {
                Timber.tag(TAG).w("PLAY_QUEUE_INDEX out of bounds: index=$index count=$itemCount")
                return@getOrBuildMediaController
            }
            val targetMediaId = controller.getMediaItemAt(index).mediaId
            controller.seekTo(index, C.TIME_UNSET)
            startExplicitPlayback(controller, targetMediaId)
            Timber.tag(TAG).d("Jumped to queue index=$index from wear")
        }
    }

    private fun handleSleepTimerCommand(command: WearPlaybackCommand) {
        getOrBuildMediaController { controller ->
            when (command.action) {
                WearPlaybackCommand.SET_SLEEP_TIMER_DURATION -> {
                    val minutes = command.durationMinutes ?: 0
                    if (minutes <= 0) {
                        Timber.tag(TAG).w("SET_SLEEP_TIMER_DURATION requires positive minutes")
                        return@getOrBuildMediaController
                    }
                    val args = Bundle().apply {
                        putInt(MusicNotificationProvider.EXTRA_SLEEP_TIMER_MINUTES, minutes)
                    }
                    controller.sendCustomCommand(
                        SessionCommand(
                            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
                            Bundle.EMPTY
                        ),
                        args
                    )
                }

                WearPlaybackCommand.SET_SLEEP_TIMER_END_OF_TRACK -> {
                    val enabled = command.targetEnabled ?: true
                    val args = Bundle().apply {
                        putBoolean(MusicNotificationProvider.EXTRA_END_OF_TRACK_ENABLED, enabled)
                    }
                    controller.sendCustomCommand(
                        SessionCommand(
                            MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
                            Bundle.EMPTY
                        ),
                        args
                    )
                }

                WearPlaybackCommand.CANCEL_SLEEP_TIMER -> {
                    controller.sendCustomCommand(
                        SessionCommand(
                            MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
                            Bundle.EMPTY
                        ),
                        Bundle.EMPTY
                    )
                }

                else -> Unit
            }
        }
    }

    /**
     * Resolve the song from browse context and insert it into queue.
     * - playNext = true: insert right after current item
     * - playNext = false: append to queue end
     */
    private fun handleInsertIntoQueue(command: WearPlaybackCommand, playNext: Boolean) {
        val songId = command.songId
        if (songId.isNullOrBlank()) {
            Timber.tag(TAG).w("Queue insert missing songId")
            return
        }

        scope.launch {
            try {
                val song = resolveSongById(songId)
                if (song == null) {
                    Timber.tag(TAG).w("Cannot resolve song for queue insert: songId=$songId")
                    return@launch
                }
                val mediaItem = MediaItemBuilder.build(song)

                getOrBuildMediaController { controller ->
                    if (playNext) {
                        val currentIndex = controller.currentMediaItemIndex
                        val insertionIndex = if (currentIndex == C.INDEX_UNSET) {
                            controller.mediaItemCount
                        } else {
                            (currentIndex + 1).coerceAtMost(controller.mediaItemCount)
                        }
                        controller.addMediaItem(insertionIndex, mediaItem)
                        Timber.tag(TAG).d("Inserted as next: ${song.title} at index=$insertionIndex")
                    } else {
                        controller.addMediaItem(mediaItem)
                        Timber.tag(TAG).d("Appended to queue: ${song.title}")
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to insert song into queue")
            }
        }
    }

    /**
     * Handle PLAY_FROM_CONTEXT: load songs for the given context, build a queue,
     * find the start index, and start playback.
     */
    private fun handlePlayFromContext(command: WearPlaybackCommand) {
        val songId = command.songId
        val contextType = command.contextType
        if (songId == null || contextType == null) {
            Timber.tag(TAG).w("PLAY_FROM_CONTEXT missing songId or contextType")
            return
        }

        scope.launch {
            try {
                val songs = getSongsForContext(contextType, command.contextId)
                if (songs.isEmpty()) {
                    Timber.tag(TAG).w("No songs found for context: $contextType / ${command.contextId}")
                    return@launch
                }

                val startIndex = songs.indexOfFirst { it.id == songId }
                if (startIndex < 0) {
                    Timber.tag(TAG).w(
                        "PLAY_FROM_CONTEXT song not found inside context: type=%s contextId=%s songId=%s",
                        contextType,
                        command.contextId,
                        songId,
                    )
                    return@launch
                }
                val startSong = songs[startIndex]
                val cloudReady = ensureStartSongCloudUriResolved(startSong)
                if (!cloudReady) {
                    Timber.tag(TAG).w(
                        "Aborting PLAY_FROM_CONTEXT: unresolved cloud URI for songId=%s",
                        startSong.id
                    )
                    return@launch
                }
                val mediaItems = buildPlaybackQueueMediaItems(songs)

                getOrBuildMediaController { controller ->
                    // Large watch-initiated queues can exceed Binder limits if we send the
                    // whole timeline through MediaController, so write directly to the player.
                    val enginePlayer = dualPlayerEngine.masterPlayer
                    startExplicitPlayback(controller, startSong.id) {
                        enginePlayer.setMediaItems(mediaItems, startIndex, 0L)
                        enginePlayer.prepare()
                    }
                    Timber.tag(TAG).d(
                        "Playing from context: $contextType, song=${songs[startIndex].title}, " +
                            "queue size=${songs.size}"
                    )
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to handle PLAY_FROM_CONTEXT")
            }
        }
    }

    /**
     * Mirrors phone-side playback behavior: resolve cloud URIs before ExoPlayer touches them.
     * This warms DualPlayerEngine.resolvedUriCache so resolveDataSpec can swap URI instantly.
     *
     * @return true if URI is ready (or not cloud), false when cloud resolution failed.
     */
    private suspend fun ensureStartSongCloudUriResolved(song: Song): Boolean {
        val originalUri = runCatching { song.contentUriString.toUri() }.getOrNull() ?: return true
        val scheme = originalUri.scheme?.lowercase()
        if (scheme != "telegram" && scheme != "netease" && scheme != "qqmusic") return true

        return runCatching {
            val resolvedUri = dualPlayerEngine.resolveCloudUri(originalUri)
            if (resolvedUri == originalUri) {
                Timber.tag(TAG).w(
                    "Cloud resolve returned original URI for songId=%s (%s)",
                    song.id,
                    scheme
                )
                false
            } else {
                Timber.tag(TAG).d(
                    "Cloud URI pre-resolved for songId=%s (%s)",
                    song.id,
                    scheme
                )
                true
            }
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "Failed to pre-resolve cloud URI for songId=${song.id}")
            false
        }
    }

    private fun startExplicitPlayback(
        controller: MediaController,
        targetMediaId: String,
        preparePlayback: (() -> Unit)? = null,
    ) {
        preparePlayback?.invoke()
        forceExplicitPlayback(controller, targetMediaId)
        EXPLICIT_PLAY_RETRY_DELAYS_MS.forEach { delayMs ->
            mainHandler.postDelayed(
                {
                    if (!controller.isConnected) return@postDelayed
                    forceExplicitPlayback(controller, targetMediaId)
                },
                delayMs,
            )
        }
    }

    private fun forceExplicitPlayback(
        controller: MediaController,
        targetMediaId: String,
    ) {
        val currentMediaId = controller.currentMediaItem?.mediaId.orEmpty()
        val targetMatchesCurrent = currentMediaId.isBlank() ||
            targetMediaId.isBlank() ||
            currentMediaId == targetMediaId
        if (!targetMatchesCurrent || controller.isPlaying) {
            return
        }
        if (controller.playbackState == Player.STATE_IDLE) {
            controller.prepare()
        }
        controller.playWhenReady = true
        controller.play()
    }

    /**
     * Get songs for a given context type and optional context ID.
     */
    private suspend fun getSongsForContext(contextType: String, contextId: String?): List<Song> {
        return when (contextType) {
            "album" -> {
                val albumId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForAlbum(albumId).first()
            }
            "artist" -> {
                val artistId = contextId?.toLongOrNull() ?: return emptyList()
                musicRepository.getSongsForArtist(artistId).first()
            }
            "playlist" -> {
                val playlistId = contextId ?: return emptyList()
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId } ?: return emptyList()
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds.mapNotNull { id -> songsById[id] }
            }
            "favorites" -> {
                musicRepository.getFavoriteSongsOnce()
            }
            "all_songs" -> {
                // Playback queues must include the full phone library, even if watch browsing is capped.
                musicRepository.getAllSongsOnce()
            }
            else -> {
                Timber.tag(TAG).w("Unknown context type: $contextType")
                emptyList()
            }
        }
    }

    private suspend fun resolveSongById(songId: String): Song? {
        return musicRepository.getSongsByIds(listOf(songId)).first().firstOrNull()
    }

    private suspend fun buildPlaybackQueueMediaItems(songs: List<Song>): List<MediaItem> {
        return withContext(Dispatchers.Default) {
            songs.map { MediaItemBuilder.build(it) }
        }
    }

    private suspend fun sendPlaybackResult(
        nodeId: String,
        requestId: String?,
        action: String,
        songId: String?,
        success: Boolean,
        error: String? = null,
    ) {
        if (requestId.isNullOrBlank() || nodeId.isBlank()) return

        val payload = json.encodeToString(
            WearPlaybackResult(
                requestId = requestId,
                action = action,
                songId = songId,
                success = success,
                error = error,
            )
        ).toByteArray(Charsets.UTF_8)

        runCatching {
            Wearable.getMessageClient(this@WearCommandReceiver)
                .sendMessage(nodeId, WearDataPaths.PLAYBACK_RESULT, payload)
                .await()
        }.onFailure { sendError ->
            Timber.tag(TAG).w(sendError, "Failed to send playback result to watch")
        }
    }

    // ---- Browse request handling ----

    private fun handleBrowseRequest(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearBrowseRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse browse request")
            return
        }

        Timber.tag(TAG).d("Browse request: type=${request.browseType}, contextId=${request.contextId}")

        scope.launch {
            val response = try {
                val items = getBrowseItems(request.browseType, request.contextId)
                WearBrowseResponse(requestId = request.requestId, items = items)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to process browse request")
                WearBrowseResponse(
                    requestId = request.requestId,
                    error = e.message ?: "Unknown error"
                )
            }

            // Send response back to the watch
            try {
                val responseBytes = json.encodeToString(response).toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(this@WearCommandReceiver)
                messageClient.sendMessage(
                    messageEvent.sourceNodeId,
                    WearDataPaths.BROWSE_RESPONSE,
                    responseBytes
                ).await()
                Timber.tag(TAG).d(
                    "Sent browse response: ${response.items.size} items for ${request.browseType}"
                )
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to send browse response")
            }
        }
    }

    private fun handleFavoriteSyncRequest(messageEvent: MessageEvent) {
        scope.launch {
            runCatching {
                val request = json.decodeFromString<WearFavoriteSyncRequest>(
                    String(messageEvent.data, Charsets.UTF_8)
                )
                val favoriteIds = musicRepository.getFavoriteSongIdsOnce()
                val response = WearFavoriteSyncResponse(
                    states = request.songIds
                        .filter { it.isNotBlank() }
                        .distinct()
                        .map { songId ->
                            WearFavoriteStateEntry(
                                songId = songId,
                                isFavorite = favoriteIds.contains(songId),
                            )
                        }
                )
                Wearable.getMessageClient(this@WearCommandReceiver)
                    .sendMessage(
                        messageEvent.sourceNodeId,
                        WearDataPaths.FAVORITES_SYNC_STATE,
                        json.encodeToString(response).toByteArray(Charsets.UTF_8),
                    )
                    .await()
            }.onFailure { error ->
                Timber.tag(TAG).e(error, "Failed to handle favorite sync request")
            }
        }
    }

    /**
     * Get browse items based on the browse type, following the same pattern
     * as AutoMediaBrowseTree.
     */
    private suspend fun getBrowseItems(browseType: String, contextId: String?): List<WearLibraryItem> {
        return when (browseType) {
            WearBrowseRequest.ROOT -> listOf(
                WearLibraryItem("favorites", "Favorites", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("playlists", "Playlists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("albums", "Albums", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("artists", "Artists", "", WearLibraryItem.TYPE_CATEGORY),
                WearLibraryItem("all_songs", "All Songs", "", WearLibraryItem.TYPE_CATEGORY),
            )

            WearBrowseRequest.ALBUMS -> {
                musicRepository.getAllAlbumsOnce()
                    .take(MAX_ALBUMS)
                    .map { album ->
                        WearLibraryItem(
                            id = album.id.toString(),
                            title = album.title,
                            subtitle = "${album.artist} · ${album.songCount} songs",
                            type = WearLibraryItem.TYPE_ALBUM,
                        )
                    }
            }

            WearBrowseRequest.ARTISTS -> {
                musicRepository.getAllArtistsOnce()
                    .take(MAX_ARTISTS)
                    .map { artist ->
                        WearLibraryItem(
                            id = artist.id.toString(),
                            title = artist.name,
                            subtitle = "${artist.songCount} songs",
                            type = WearLibraryItem.TYPE_ARTIST,
                        )
                    }
            }

            WearBrowseRequest.PLAYLISTS -> {
                playlistPreferencesRepository.userPlaylistsFlow.first()
                    .map { playlist ->
                        WearLibraryItem(
                            id = playlist.id,
                            title = playlist.name,
                            subtitle = "${playlist.songIds.size} songs",
                            type = WearLibraryItem.TYPE_PLAYLIST,
                        )
                    }
            }

            WearBrowseRequest.FAVORITES -> {
                musicRepository.getFavoriteSongsOnce()
                    .take(MAX_BROWSE_SONGS)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ALL_SONGS -> {
                musicRepository.getAllSongsOnce()
                    .take(MAX_BROWSE_SONGS)
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.QUEUE -> {
                getQueueItems()
            }

            WearBrowseRequest.ALBUM_SONGS -> {
                val albumId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing albumId for ALBUM_SONGS")
                musicRepository.getSongsForAlbum(albumId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.ARTIST_SONGS -> {
                val artistId = contextId?.toLongOrNull()
                    ?: throw IllegalArgumentException("Missing artistId for ARTIST_SONGS")
                musicRepository.getSongsForArtist(artistId).first()
                    .map { song -> song.toWearLibraryItem() }
            }

            WearBrowseRequest.PLAYLIST_SONGS -> {
                val playlistId = contextId
                    ?: throw IllegalArgumentException("Missing playlistId for PLAYLIST_SONGS")
                val playlist = playlistPreferencesRepository.userPlaylistsFlow.first()
                    .find { it.id == playlistId }
                    ?: throw IllegalArgumentException("Playlist not found: $playlistId")
                val songs = musicRepository.getSongsByIds(playlist.songIds).first()
                // Maintain playlist order
                val songsById = songs.associateBy { it.id }
                playlist.songIds
                    .mapNotNull { id -> songsById[id] }
                    .map { song -> song.toWearLibraryItem() }
            }

            else -> {
                Timber.tag(TAG).w("Unknown browse type: $browseType")
                emptyList()
            }
        }
    }

    private suspend fun getQueueItems(): List<WearLibraryItem> = suspendCancellableCoroutine { continuation ->
        getOrBuildMediaController { controller ->
            try {
                val currentIndex = controller.currentMediaItemIndex
                val count = controller.mediaItemCount
                val startIndex = if (currentIndex in 0 until count) currentIndex else 0
                val items = buildList {
                    for (index in startIndex until count) {
                        val mediaItem = controller.getMediaItemAt(index)
                        val metadata = mediaItem.mediaMetadata
                        val title = metadata.title?.toString()?.takeIf { it.isNotBlank() }
                            ?: mediaItem.mediaId.takeIf { it.isNotBlank() }
                            ?: "Track ${index + 1}"
                        val artist = metadata.artist?.toString().orEmpty()
                            .ifBlank { metadata.albumArtist?.toString().orEmpty() }
                        val album = metadata.albumTitle?.toString().orEmpty()
                        val info = when {
                            artist.isNotBlank() && album.isNotBlank() -> "$artist · $album"
                            artist.isNotBlank() -> artist
                            album.isNotBlank() -> album
                            else -> ""
                        }
                        val subtitle = if (index == currentIndex) {
                            if (info.isBlank()) "Playing" else "Playing · $info"
                        } else {
                            info
                        }
                        add(
                            WearLibraryItem(
                                id = index.toString(),
                                title = title,
                                subtitle = subtitle,
                                type = WearLibraryItem.TYPE_SONG,
                                canSaveToWatch = false,
                            )
                        )
                    }
                }
                if (continuation.isActive) {
                    continuation.resume(items)
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to build queue browse items")
                if (continuation.isActive) {
                    continuation.resume(emptyList())
                }
            }
        }
    }

    private fun Song.toWearLibraryItem(): WearLibraryItem {
        return WearLibraryItem(
            id = id,
            title = title,
            subtitle = displayArtist,
            type = WearLibraryItem.TYPE_SONG,
            canSaveToWatch = isSongLikelyTransferableForWatch(this),
        )
    }

    private fun isSongLikelyTransferableForWatch(song: Song): Boolean {
        val contentUri = song.contentUriString
        if (
            contentUri.startsWith("telegram://") ||
            contentUri.startsWith("netease://") ||
            contentUri.startsWith("qqmusic://") ||
            contentUri.startsWith("gdrive://")
        ) {
            return false
        }

        val localFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localFile != null && localFile.isFile && localFile.canRead() && localFile.length() > 0L) {
            return true
        }

        val scheme = runCatching { contentUri.toUri().scheme?.lowercase() }.getOrNull()
        return scheme == ContentResolver.SCHEME_CONTENT || scheme == ContentResolver.SCHEME_FILE
    }

    private fun isSongTransferEligible(song: Song): Boolean {
        if (!isSongLikelyTransferableForWatch(song)) return false

        val localFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localFile != null && localFile.isFile && localFile.canRead() && localFile.length() > 0L) {
            return true
        }

        val uri = runCatching { song.contentUriString.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme == ContentResolver.SCHEME_FILE) {
            val uriFile = uri.path?.let { File(it) }
            return uriFile != null && uriFile.isFile && uriFile.canRead() && uriFile.length() > 0L
        }
        if (scheme != ContentResolver.SCHEME_CONTENT) return false

        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    // ---- Volume handling ----

    private fun handleVolumeCommand(messageEvent: MessageEvent) {
        val commandJson = String(messageEvent.data, Charsets.UTF_8)
        val command = try {
            json.decodeFromString<WearVolumeCommand>(commandJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse volume command")
            return
        }

        Timber.tag(TAG).d("Volume command: direction=${command.direction}, value=${command.value}")

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        val absoluteValue = command.value
        if (absoluteValue != null) {
            // Set absolute volume (scaled from 0-100 to device range)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVolume = (absoluteValue * maxVolume / 100).coerceIn(0, maxVolume)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        } else {
            when (command.direction) {
                WearVolumeCommand.UP -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_RAISE,
                    0
                )
                WearVolumeCommand.DOWN -> audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    AudioManager.ADJUST_LOWER,
                    0
                )
                WearVolumeCommand.QUERY -> Unit
            }
        }

        val currentLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxLevel = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        sendVolumeState(
            nodeId = messageEvent.sourceNodeId,
            level = currentLevel,
            max = maxLevel,
        )
    }

    private fun sendVolumeState(nodeId: String, level: Int, max: Int) {
        scope.launch {
            runCatching {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val activeRoute = resolveActiveOutputRoute(audioManager)
                val payload = json.encodeToString(
                    WearVolumeState(
                        level = level,
                        max = max,
                        routeType = activeRoute.type,
                        routeName = activeRoute.name,
                    )
                )
                    .toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(this@WearCommandReceiver)
                messageClient.sendMessage(nodeId, WearDataPaths.VOLUME_STATE, payload).await()
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to send volume state update to watch")
            }
        }
    }

    private data class ActiveOutputRoute(
        val type: String,
        val name: String,
    )

    private suspend fun resolveActiveOutputRoute(audioManager: AudioManager): ActiveOutputRoute {
        if (withContext(Dispatchers.Main.immediate) { isCastingMediaPlayback() }) {
            return ActiveOutputRoute(
                type = WearVolumeState.ROUTE_TYPE_CAST,
                name = "Cast",
            )
        }

        val outputs = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrDefault(emptyList())

        val bluetoothDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BLE_BROADCAST
        }
        if (bluetoothDevice != null || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn) {
            val bluetoothName = bluetoothDevice?.productName?.toString().orEmpty().ifBlank { "Bluetooth" }
            return ActiveOutputRoute(
                type = resolveBluetoothRouteType(
                    audioDevice = bluetoothDevice,
                    deviceName = bluetoothName,
                ),
                name = bluetoothName,
            )
        }

        val wiredDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (wiredDevice != null || audioManager.isWiredHeadsetOn) {
            return ActiveOutputRoute(
                type = WearVolumeState.ROUTE_TYPE_HEADPHONES,
                name = wiredDevice?.productName?.toString().orEmpty().ifBlank { "Headphones" },
            )
        }

        val speakerDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
        }
        if (speakerDevice != null) {
            return ActiveOutputRoute(
                type = WearVolumeState.ROUTE_TYPE_SPEAKER,
                name = "Phone speaker",
            )
        }

        return ActiveOutputRoute(
            type = WearVolumeState.ROUTE_TYPE_PHONE,
            name = "Phone",
        )
    }

    private fun resolveBluetoothRouteType(
        audioDevice: AudioDeviceInfo?,
        deviceName: String,
    ): String {
        return when (audioDevice?.type) {
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            -> WearVolumeState.ROUTE_TYPE_HEADPHONES

            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> WearVolumeState.ROUTE_TYPE_BLUETOOTH

            else -> {
                if (looksLikeBluetoothHeadphones(deviceName)) {
                    WearVolumeState.ROUTE_TYPE_HEADPHONES
                } else {
                    WearVolumeState.ROUTE_TYPE_BLUETOOTH
                }
            }
        }
    }

    private fun looksLikeBluetoothHeadphones(deviceName: String): Boolean {
        val normalizedName = deviceName.trim().lowercase()
        if (normalizedName.isBlank()) return false

        val headphoneHints = listOf(
            "airpods",
            "buds",
            "buds+",
            "bud",
            "earbud",
            "earbuds",
            "earphones",
            "earphone",
            "headphone",
            "headphones",
            "headset",
            "pods",
            "xm3",
            "xm4",
            "xm5",
            "wh-",
            "wf-",
            "qc ultra",
            "quietcomfort",
            "freebuds",
            "bean",
            "auricular",
            "auriculares",
            "audifono",
            "audifonos",
        )
        val speakerHints = listOf(
            "speaker",
            "soundlink",
            "boombox",
            "flip",
            "charge",
            "xtreme",
            "soundcore",
            "home",
            "nest",
            "roam",
            "move",
            "parlante",
            "bocina",
            "altavoz",
        )

        if (speakerHints.any { normalizedName.contains(it) }) return false
        return headphoneHints.any { normalizedName.contains(it) }
    }

    private fun isCastingMediaPlayback(): Boolean {
        val castSession = resolveActiveCastSession() ?: return false
        val remoteClient = castSession.remoteMediaClient ?: return false
        val playerState = remoteClient.playerState
        return remoteClient.mediaStatus != null &&
            playerState != MediaStatus.PLAYER_STATE_IDLE &&
            playerState != MediaStatus.PLAYER_STATE_UNKNOWN
    }

    // ---- Transfer handling ----

    private data class PreparedTransferRequest(
        val nodeId: String,
        val requestId: String,
        val songId: String,
        val fileSize: Long,
        val inputStream: InputStream,
        val artworkBytes: ByteArray?,
    )

    private suspend fun handleTransferRequest(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearTransferRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse transfer request")
            return
        }
        var openedInputStream: InputStream? = null

        Timber.tag(TAG).d("Transfer request: songId=${request.songId}, requestId=${request.requestId}")

        try {
            // 1. Find the song
            val songs = musicRepository.getSongsByIds(listOf(request.songId)).first()
            val song = songs.firstOrNull()

            if (song == null) {
                sendTransferMetadataError(
                    messageEvent.sourceNodeId, request.requestId, request.songId,
                    "Song not found"
                )
                return
            }
            transferStateStore.markRequested(
                requestId = request.requestId,
                songId = song.id,
                songTitle = song.title,
            )

            // 2. Verify this song is truly available offline on the phone.
            if (!isSongTransferEligible(song)) {
                sendTransferMetadataError(
                    messageEvent.sourceNodeId, request.requestId, request.songId,
                    "Song must be downloaded locally on phone before saving to watch"
                )
                return
            }

            // 3. Open the file and get its size
            val fileInputStream = openSongFile(song)
            if (fileInputStream == null) {
                sendTransferMetadataError(
                    messageEvent.sourceNodeId, request.requestId, request.songId,
                    "Cannot read audio file"
                )
                return
            }
            openedInputStream = fileInputStream

            val fileSize = getSongFileSize(song)
            val paletteSeedArgb = resolvePaletteSeedArgb(song)
            val transferThemePalette = resolveTransferThemePalette(song)
            val transferArtworkBytes = resolveTransferArtworkBytes(song)

            // 4. Send metadata to watch
            val metadata = WearTransferMetadata(
                requestId = request.requestId,
                songId = song.id,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                albumId = song.albumId,
                duration = song.duration,
                mimeType = song.mimeType ?: "audio/mpeg",
                fileSize = fileSize,
                bitrate = song.bitrate ?: 0,
                sampleRate = song.sampleRate ?: 0,
                isFavorite = song.isFavorite,
                paletteSeedArgb = paletteSeedArgb,
                themePalette = transferThemePalette,
            )
            transferStateStore.markMetadata(
                requestId = request.requestId,
                songId = song.id,
                songTitle = song.title,
                totalBytes = fileSize,
            )
            if (transferCancellationStore.consumeCancellation(request.requestId)) {
                sendTransferProgress(
                    nodeId = messageEvent.sourceNodeId,
                    requestId = request.requestId,
                    songId = song.id,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }
            val metadataBytes = json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(this@WearCommandReceiver)
            msgClient.sendMessage(
                messageEvent.sourceNodeId,
                WearDataPaths.TRANSFER_METADATA,
                metadataBytes,
            ).await()

            Timber.tag(TAG).d("Sent transfer metadata: ${song.title} ($fileSize bytes)")

            val preparedTransfer = PreparedTransferRequest(
                nodeId = messageEvent.sourceNodeId,
                requestId = request.requestId,
                songId = song.id,
                fileSize = fileSize,
                inputStream = fileInputStream,
                artworkBytes = transferArtworkBytes,
            )

            scope.launch {
                continueTransferRequest(preparedTransfer)
            }
            openedInputStream = null
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to handle transfer request")
            sendTransferProgress(
                messageEvent.sourceNodeId, request.requestId, request.songId,
                0, 0, WearTransferProgress.STATUS_FAILED, e.message,
            )
            runCatching { openedInputStream?.close() }
        }
    }

    private suspend fun continueTransferRequest(transfer: PreparedTransferRequest) {
        var audioStreamingStarted = false
        try {
            if (transfer.artworkBytes != null) {
                runCatching {
                    streamArtworkToWatch(
                        nodeId = transfer.nodeId,
                        requestId = transfer.requestId,
                        songId = transfer.songId,
                        artworkBytes = transfer.artworkBytes,
                    )
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Artwork transfer failed for songId=${transfer.songId}")
                }
            }

            if (transferCancellationStore.consumeCancellation(transfer.requestId)) {
                sendTransferProgress(
                    nodeId = transfer.nodeId,
                    requestId = transfer.requestId,
                    songId = transfer.songId,
                    bytesTransferred = 0L,
                    totalBytes = transfer.fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }

            audioStreamingStarted = true
            streamFileToWatch(
                nodeId = transfer.nodeId,
                requestId = transfer.requestId,
                songId = transfer.songId,
                inputStream = transfer.inputStream,
                fileSize = transfer.fileSize,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to continue transfer request")
            sendTransferProgress(
                nodeId = transfer.nodeId,
                requestId = transfer.requestId,
                songId = transfer.songId,
                bytesTransferred = 0L,
                totalBytes = transfer.fileSize,
                status = WearTransferProgress.STATUS_FAILED,
                error = e.message,
            )
        } finally {
            if (!audioStreamingStarted) {
                runCatching { transfer.inputStream.close() }
            }
        }
    }

    private fun handleTransferCancel(messageEvent: MessageEvent) {
        val requestJson = String(messageEvent.data, Charsets.UTF_8)
        val request = try {
            json.decodeFromString<WearTransferRequest>(requestJson)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to parse transfer cancel")
            return
        }
        transferCancellationStore.markCancelled(request.requestId)
        transferStateStore.markCancelled(request.requestId)
        Timber.tag(TAG).d("Transfer cancelled: requestId=${request.requestId}")
    }

    /**
     * Open a song's audio file, trying direct File access first,
     * then falling back to ContentResolver.
     */
    private fun openSongFile(song: Song): InputStream? {
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                file.inputStream()
            } else {
                contentResolver.openInputStream(song.contentUriString.toUri())
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to open song file: ${song.path}")
            try {
                contentResolver.openInputStream(song.contentUriString.toUri())
            } catch (e2: Exception) {
                Timber.tag(TAG).e(e2, "ContentResolver fallback also failed")
                null
            }
        }
    }

    /**
     * Get the file size of a song, trying File.length() first,
     * then ContentResolver.
     */
    private fun getSongFileSize(song: Song): Long {
        val file = File(song.path)
        if (file.exists()) return file.length()

        // Fallback: query ContentResolver for size
        return try {
            contentResolver.openAssetFileDescriptor(song.contentUriString.toUri(), "r")?.use {
                it.length
            } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Resolve palette seed from album art with album-level cache to avoid repeated extraction work
     * across transfers of songs in the same album.
     */
    private fun resolvePaletteSeedArgb(song: Song): Int? {
        if (song.albumId > 0L) {
            albumPaletteSeedCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmap(song) ?: return null
        return try {
            extractSeedColorArgb(bitmap)?.also { seed ->
                if (song.albumId > 0L) {
                    albumPaletteSeedCache[song.albumId] = seed
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolveTransferArtworkBytes(song: Song): ByteArray? {
        if (song.albumId > 0L) {
            albumArtworkTransferCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmapForTransfer(song) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, TRANSFER_ARTWORK_QUALITY, stream)
            val bytes = stream.toByteArray()
            if (bytes.isEmpty() || bytes.size > TRANSFER_ARTWORK_MAX_BYTES) {
                null
            } else {
                if (song.albumId > 0L) {
                    albumArtworkTransferCache[song.albumId] = bytes
                }
                bytes
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to encode transfer artwork for songId=${song.id}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun resolveTransferThemePalette(song: Song): WearThemePalette? {
        val playerTheme = themePreferencesRepository.playerThemePreferenceFlow.first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC) {
            return buildWearThemePalette(dynamicDarkColorScheme(this))
        }

        val artUriString = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return null
        val paletteStyle = AlbumArtPaletteStyle.fromStorageKey(
            themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey
        )
        val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(artUriString, paletteStyle)
            ?: return null
        return buildWearThemePalette(schemePair.dark)
    }

    private fun loadSongAlbumArtBitmapForTransfer(song: Song): Bitmap? {
        val fromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString -> decodeBoundedBitmapFromUri(uriString, TRANSFER_ARTWORK_MAX_DIMENSION) }
        if (fromUri != null) return fromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(this, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmap(embedded, TRANSFER_ARTWORK_MAX_DIMENSION)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to load transfer artwork for songId=${song.id}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromUri(uriString: String, maxDimension: Int): Bitmap? {
        val uri = uriString.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }

        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun decodeBoundedBitmap(data: ByteArray, maxDimension: Int): Bitmap? {
        if (data.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun loadSongAlbumArtBitmap(song: Song): Bitmap? {
        val artFromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString ->
                runCatching {
                    contentResolver.openInputStream(uriString.toUri())?.use { input ->
                        BitmapFactory.decodeStream(
                            input,
                            null,
                            BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = 4
                            },
                        )
                    }
                }.getOrNull()
            }
        if (artFromUri != null) return artFromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(this, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            )
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to extract album art for palette seed: songId=${song.id}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val step = (minOf(bitmap.width, bitmap.height) / 24).coerceAtLeast(1)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 28) {
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    if (red + green + blue > 36) {
                        redSum += red
                        greenSum += green
                        blueSum += blue
                        count++
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return null
        return Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    }

    /**
     * Stream an audio file to the watch via ChannelClient.
     * The stream format is: [4 bytes requestId length][requestId bytes][audio data]
     */
    private suspend fun streamFileToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        inputStream: InputStream,
        fileSize: Long,
    ) {
        if (transferCancellationStore.consumeCancellation(requestId)) {
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_CANCELLED,
            )
            return
        }
        val channelClient = Wearable.getChannelClient(this@WearCommandReceiver)
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_CHANNEL).await()

        try {
            val outputStream = channelClient.getOutputStream(channel).await()

            // Write header: requestId length (4 bytes big-endian) + requestId bytes
            val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
            val lengthBytes = ByteBuffer.allocate(4).putInt(requestIdBytes.size).array()
            outputStream.write(lengthBytes)
            outputStream.write(requestIdBytes)

            // Stream audio data in chunks
            val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
            var totalSent = 0L
            var lastProgressUpdate = 0L

            inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Check for cancellation
                    if (transferCancellationStore.consumeCancellation(requestId)) {
                        Timber.tag(TAG).d("Transfer cancelled during streaming: $requestId")
                        sendTransferProgress(
                            nodeId, requestId, songId, totalSent, fileSize,
                            WearTransferProgress.STATUS_CANCELLED,
                        )
                        return
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalSent += bytesRead

                    // Send progress updates periodically
                    if (totalSent - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                        sendTransferProgress(
                            nodeId, requestId, songId, totalSent, fileSize,
                            WearTransferProgress.STATUS_TRANSFERRING,
                        )
                        lastProgressUpdate = totalSent
                    }
                }
            }

            outputStream.flush()
            outputStream.close()

            // Send completion status
            sendTransferProgress(
                nodeId, requestId, songId, fileSize, fileSize,
                WearTransferProgress.STATUS_COMPLETED,
            )
            Timber.tag(TAG).d("Transfer complete: songId=$songId, $totalSent bytes sent")

        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to stream file to watch")
            sendTransferProgress(
                nodeId, requestId, songId, 0, fileSize,
                WearTransferProgress.STATUS_FAILED, e.message,
            )
        } finally {
            try {
                channelClient.close(channel).await()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to close channel")
            }
            transferCancellationStore.clear(requestId)
        }
    }

    /**
     * Stream album artwork bytes to the watch via a dedicated ChannelClient path.
     * Stream format: [requestId length][requestId][songId length][songId][artwork bytes]
     */
    private suspend fun streamArtworkToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        artworkBytes: ByteArray,
    ) {
        if (artworkBytes.isEmpty()) return
        val channelClient = Wearable.getChannelClient(this@WearCommandReceiver)
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_ARTWORK_CHANNEL).await()
        try {
            val outputStream = channelClient.getOutputStream(channel).await()
            val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
            val songIdBytes = songId.toByteArray(Charsets.UTF_8)

            outputStream.write(ByteBuffer.allocate(4).putInt(requestIdBytes.size).array())
            outputStream.write(requestIdBytes)
            outputStream.write(ByteBuffer.allocate(4).putInt(songIdBytes.size).array())
            outputStream.write(songIdBytes)
            outputStream.write(artworkBytes)
            outputStream.flush()
            outputStream.close()

            Timber.tag(TAG).d(
                "Artwork transfer complete: songId=$songId, bytes=${artworkBytes.size}"
            )
        } finally {
            runCatching { channelClient.close(channel).await() }
                .onFailure { error -> Timber.tag(TAG).w(error, "Failed to close artwork channel") }
        }
    }

    private suspend fun sendTransferMetadataError(
        nodeId: String,
        requestId: String,
        songId: String,
        errorMessage: String,
    ) {
        val metadata = WearTransferMetadata(
            requestId = requestId,
            songId = songId,
            title = "",
            artist = "",
            album = "",
            albumId = 0L,
            duration = 0L,
            mimeType = "",
            fileSize = 0L,
            bitrate = 0,
            sampleRate = 0,
            isFavorite = false,
            error = errorMessage,
        )
        try {
            val metadataBytes = json.encodeToString(metadata).toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(this@WearCommandReceiver)
            msgClient.sendMessage(nodeId, WearDataPaths.TRANSFER_METADATA, metadataBytes).await()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to send transfer error metadata")
        }

        // Secondary signal so watch can clear pending transfer even if metadata handling is delayed.
        sendTransferProgress(
            nodeId = nodeId,
            requestId = requestId,
            songId = songId,
            bytesTransferred = 0L,
            totalBytes = 0L,
            status = WearTransferProgress.STATUS_FAILED,
            error = errorMessage,
        )
    }

    private suspend fun sendTransferProgress(
        nodeId: String,
        requestId: String,
        songId: String,
        bytesTransferred: Long,
        totalBytes: Long,
        status: String,
        error: String? = null,
    ) {
        transferStateStore.markProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        if (status == WearTransferProgress.STATUS_COMPLETED) {
            transferStateStore.markSongPresentOnWatch(
                nodeId = nodeId,
                songId = songId,
            )
        }
        val progress = WearTransferProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        try {
            val progressBytes = json.encodeToString(progress).toByteArray(Charsets.UTF_8)
            val msgClient = Wearable.getMessageClient(this@WearCommandReceiver)
            msgClient.sendMessage(nodeId, WearDataPaths.TRANSFER_PROGRESS, progressBytes).await()
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to send transfer progress")
        }
    }

    // ---- MediaController management ----

    /**
     * Get existing MediaController or build a new one, then execute the action.
     */
    private fun getOrBuildMediaController(action: (MediaController) -> Unit) {
        runOnMainThread {
            val existing = mediaController
            if (existing != null && existing.isConnected) {
                action(existing)
                return@runOnMainThread
            }

            val inFlight = mediaControllerFuture
            if (inFlight != null && !inFlight.isDone) {
                inFlight.addListener(
                    {
                        try {
                            action(inFlight.get())
                        } catch (e: Exception) {
                            Timber.tag(TAG).e(e, "Failed to reuse pending MediaController")
                        }
                    },
                    ContextCompat.getMainExecutor(this)
                )
                return@runOnMainThread
            }

            val sessionToken = SessionToken(
                this,
                ComponentName(this, MusicService::class.java)
            )
            val future = MediaController.Builder(this, sessionToken)
                .setApplicationLooper(Looper.getMainLooper())
                .buildAsync()
            mediaControllerFuture = future
            future.addListener(
                {
                    try {
                        val controller = future.get()
                        mediaController = controller
                        action(controller)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Failed to build MediaController")
                    }
                },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun releaseController() {
        val controller = mediaController
        mediaController = null
        mediaControllerFuture = null
        if (controller != null) {
            try {
                controller.release()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to release MediaController")
            }
        }
    }

    private fun releaseCastPlayer() {
        cachedCastPlayer?.release()
        cachedCastPlayer = null
        cachedCastSession = null
    }

    override fun onDestroy() {
        scope.cancel()
        runOnMainThread {
            releaseController()
            releaseCastPlayer()
        }
        super.onDestroy()
    }
}
