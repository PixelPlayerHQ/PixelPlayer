package com.theveloper.pixelplay.data.service

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.theveloper.pixelplay.PixelPlayApplication
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.data.model.PlaybackQueueItemSnapshot
import com.theveloper.pixelplay.data.model.PlaybackQueueSnapshot
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.service.player.TransitionController
import com.theveloper.pixelplay.ui.glancewidget.ControlWidget4x2
import com.theveloper.pixelplay.ui.glancewidget.PixelPlayGlanceWidget
import com.theveloper.pixelplay.ui.glancewidget.PlayerActions
import com.theveloper.pixelplay.ui.glancewidget.PlayerInfoStateDefinition
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.model.WidgetThemeColors
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor
import androidx.compose.ui.graphics.toArgb
import com.theveloper.pixelplay.ui.glancewidget.BarWidget4x1
import com.theveloper.pixelplay.ui.glancewidget.GridWidget2x2
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.service.auto.AutoMediaBrowseTree
import com.theveloper.pixelplay.data.service.wear.buildWearThemePalette
import com.theveloper.pixelplay.data.service.wear.WearStatePublisher
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import com.theveloper.pixelplay.shared.WearIntents
import com.theveloper.pixelplay.utils.MediaItemBuilder
import kotlin.math.abs
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

import javax.inject.Inject
import androidx.core.net.toUri

// Acciones personalizadas para compatibilidad con el widget existente


@UnstableApi
@AndroidEntryPoint
class MusicService : MediaLibraryService() {

    @Inject
    lateinit var engine: DualPlayerEngine
    @Inject
    lateinit var controller: TransitionController
    @Inject
    lateinit var musicRepository: MusicRepository
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository
    @Inject
    lateinit var equalizerPreferencesRepository: EqualizerPreferencesRepository
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    @Inject
    lateinit var equalizerManager: EqualizerManager
    @Inject
    lateinit var colorSchemeProcessor: ColorSchemeProcessor
    @Inject
    lateinit var autoMediaBrowseTree: AutoMediaBrowseTree
    @Inject
    lateinit var wearStatePublisher: WearStatePublisher
    @Inject
    lateinit var replayGainManager: com.theveloper.pixelplay.data.media.ReplayGainManager

    private var replayGainEnabled = false
    private var replayGainUseAlbumGain = false
    private var replayGainJob: Job? = null
    private var replayGainRequestToken = 0L
    private var userSelectedVolume = 1f
    private var expectedReplayGainVolume: Float? = null
    private var pendingReplayGainVolume: Float? = null

    private var favoriteSongIds = emptySet<String>()
    private var mediaSession: MediaLibraryService.MediaLibrarySession? = null
    private val controllerLastBrowsedParent = mutableMapOf<String, String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var keepPlayingInBackground = true
    private var isManualShuffleEnabled = false
    private var persistentShuffleEnabled = false
    // Holds the previous main-thread UncaughtExceptionHandler so we can restore it in onDestroy.
    private var previousMainThreadExceptionHandler: Thread.UncaughtExceptionHandler? = null
    // --- Counted Play State ---
    private var countedPlayActive = false
    private var countedPlayTarget = 0
    private var countedPlayCount = 0
    private var countedOriginalId: String? = null
    private var countedPlayListener: Player.Listener? = null
    private val alarmManager by lazy {
        getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private var endOfTrackTimerSongId: String? = null
    private var castSessionManager: SessionManager? = null
    private var castSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var castRemoteClientCallback: RemoteMediaClient.Callback? = null
    private var observedCastSession: CastSession? = null
    private var playbackSnapshotPersistJob: Job? = null
    private var isRestoringPlaybackSnapshot = false
    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private var headsetReconnectCallback: AudioDeviceCallback? = null
    private var shouldResumeAfterHeadsetReconnect = false
    private var lastNoisyPauseRealtimeMs = 0L
    private var resumeOnHeadsetReconnectEnabled = false
    private var temporaryForegroundStartedInOnCreate = false

    companion object {
        private const val TAG = "MusicService_PixelPlay"
        const val NOTIFICATION_ID = 101
        const val ACTION_SLEEP_TIMER_EXPIRED = "com.theveloper.pixelplay.ACTION_SLEEP_TIMER_EXPIRED"
        const val EXTRA_FORCE_FOREGROUND_ON_START =
            "com.theveloper.pixelplay.extra.FORCE_FOREGROUND_ON_START"
        private const val PLAYBACK_SNAPSHOT_DEBOUNCE_MS = 350L
        private val pendingMediaButtonForegroundStarts = AtomicInteger(0)

        private const val APP_PACKAGE_PREFIX = "com.theveloper.pixelplay"
        private val BLOCKED_WEAR_CONTROLLER_PREFIXES = listOf(
            "android.media.session.MediaController",
            "com.google.android.wearable",
            "com.google.android.clockwork",
            "com.google.android.apps.wearable",
            "com.google.android.apps.wear.companion",
            "com.samsung.android.app.watchmanager",
            "com.mobvoi.wear",
        )
        private val WEAR_HINT_KEY_MARKERS = listOf(
            "wear",
            "clockwork",
            "companion",
            "node",
            "remote_device",
        )
        private const val AUTO_CONTEXT_RECENT = "recent"
        private const val AUTO_CONTEXT_FAVORITES = "favorites"
        private const val AUTO_CONTEXT_ALL_SONGS = "all_songs"
        private const val AUTO_CONTEXT_ALBUM = "album"
        private const val AUTO_CONTEXT_ARTIST = "artist"
        private const val AUTO_CONTEXT_PLAYLIST = "playlist"
        private const val MAX_WIDGET_ARTWORK_BYTES = 2 * 1024 * 1024
        private const val DEFAULT_STREAM_BUFFER_SIZE = 8 * 1024
        private const val WIDGET_ART_FAILURE_RETRY_MS = 30_000L
        private const val HEADSET_RECONNECT_RESUME_WINDOW_MS = 15_000L

        fun markPendingMediaButtonForegroundStart() {
            pendingMediaButtonForegroundStarts.incrementAndGet()
        }

        fun unmarkPendingMediaButtonForegroundStart() {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return
                }
            }
        }

        private fun consumePendingMediaButtonForegroundStart(): Boolean {
            while (true) {
                val currentCount = pendingMediaButtonForegroundStarts.get()
                if (currentCount <= 0) return false
                if (pendingMediaButtonForegroundStarts.compareAndSet(currentCount, currentCount - 1)) {
                    return true
                }
            }
        }
    }

    private val playerSwapListener: (Player) -> Unit = { newPlayer ->
        serviceScope.launch(Dispatchers.Main) {
            val oldPlayer = mediaSession?.player
            oldPlayer?.removeListener(playerListener)

            mediaSession?.player = newPlayer
            newPlayer.addListener(playerListener)

            Timber.tag("MusicService").d("Swapped MediaSession player to new instance.")
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
        }
    }

    private val transitionFinishedListener: () -> Unit = {
        onTransitionFinished()
    }

    override fun onCreate() {
        // Media3's Cast SDK callback path (MediaSessionImpl$$ExternalSyntheticLambda →
        // Util.postOrRun → MediaNotificationManager.updateNotificationInternal) calls
        // Service.startForeground() directly, bypassing onUpdateNotification() entirely.
        // Since startForeground() is final we cannot override it. Instead we intercept
        // ForegroundServiceStartNotAllowedException on the main thread before it reaches
        // ActivityThread and crashes the process.
        val existingHandler = Thread.currentThread().uncaughtExceptionHandler
        previousMainThreadExceptionHandler = existingHandler
        Thread.currentThread().setUncaughtExceptionHandler { thread, throwable ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                throwable is ForegroundServiceStartNotAllowedException
            ) {
                Timber.tag(TAG).w(throwable, "Suppressed ForegroundServiceStartNotAllowedException from Media3/Cast internal path")
            } else {
                existingHandler?.uncaughtException(thread, throwable)
            }
        }

        super.onCreate()

        // A MEDIA_BUTTON broadcast starts the foreground-service timeout before
        // MusicService reaches onStartCommand(). Promote as early as possible so
        // cold-start initialization cannot consume the whole timeout window.
        temporaryForegroundStartedInOnCreate = consumePendingMediaButtonForegroundStart()
        if (temporaryForegroundStartedInOnCreate) {
            startTemporaryForegroundForCommand()
        }
        
        // Ensure engine is ready (re-initialize if service was restarted)
        engine.initialize()
        userSelectedVolume = engine.masterPlayer.volume.coerceIn(0f, 1f)

        engine.masterPlayer.addListener(playerListener)

        // Handle player swaps (crossfade) to keep MediaSession in sync
        engine.addPlayerSwapListener(playerSwapListener)
        engine.addTransitionFinishedListener(transitionFinishedListener)

        controller.initialize()
        initializeCastWearSync()
        registerHeadsetReconnectMonitor()

        // Restore equalizer state from preferences and only attach audio effects when
        // the user actually has at least one effect enabled for the current session.
        serviceScope.launch {
            val eqEnabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
            val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
            val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
            val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
            val bassBoostStrength = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
            val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
            val virtualizerStrength = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
            val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
            val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()

            equalizerManager.restoreState(
                eqEnabled, presetName, customBands,
                bassBoostEnabled, bassBoostStrength,
                virtualizerEnabled, virtualizerStrength,
                loudnessEnabled, loudnessStrength
            )

            val sessionId = engine.getAudioSessionId()
            if (sessionId != 0) {
                equalizerManager.attachToAudioSessionIfNeeded(sessionId)
            }

            // Re-attach equalizer whenever the active audio session changes (e.g. crossfade)
            engine.activeAudioSessionId.collect { newSessionId ->
                if (newSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(newSessionId)
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.keepPlayingInBackgroundFlow.collect { enabled ->
                keepPlayingInBackground = enabled
            }
        }

        serviceScope.launch {
            userPreferencesRepository.resumeOnHeadsetReconnectFlow.collect { enabled ->
                resumeOnHeadsetReconnectEnabled = enabled
                if (!enabled) {
                    clearHeadsetReconnectResume()
                }
            }
        }

        serviceScope.launch {
            userPreferencesRepository.persistentShuffleEnabledFlow.collect { enabled ->
                persistentShuffleEnabled = enabled
            }
        }

        // ReplayGain preference collectors
        serviceScope.launch {
            userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
                replayGainEnabled = enabled
                // Re-apply to current track when toggled
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }
        serviceScope.launch {
            userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
                replayGainUseAlbumGain = useAlbum
                // Re-apply to current track when mode changes
                applyReplayGain(mediaSession?.player?.currentMediaItem)
            }
        }

        // Initialize shuffle state from preferences
        serviceScope.launch {
            val persistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (persistent) {
                isManualShuffleEnabled = userPreferencesRepository.isShuffleOnFlow.first()
                mediaSession?.let { refreshMediaSessionUi(it) }
            }
        }

        val callback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val controllerPackage = controller.packageName
                val hintKeys = controller.connectionHints.keySet().joinToString(",")
                Timber.tag(TAG).d(
                    "onConnect from package=%s uid=%s trusted=%s version=%s hints=[%s]",
                    controllerPackage,
                    controller.uid,
                    controller.isTrusted,
                    controller.controllerVersion,
                    hintKeys
                )
                if (shouldRejectWearController(controller)) {
                    Timber.tag(TAG).i(
                        "Rejecting Wear system controller connection from package=%s",
                        controllerPackage
                    )
                    return MediaSession.ConnectionResult.reject()
                }

                val defaultResult = super.onConnect(session, controller)
                val customCommands = listOf(
                    MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER,
                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE,
                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE,
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON,
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE,
                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE,
                    MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION,
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK,
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER,
                ).map { SessionCommand(it, Bundle.EMPTY) }

                val sessionCommandsBuilder = SessionCommands.Builder()
                    .addSessionCommands(defaultResult.availableSessionCommands.commands)
                customCommands.forEach { sessionCommandsBuilder.add(it) }
                grantArtworkUriPermissions(
                    controller.packageName,
                    listOfNotNull(session.player.currentMediaItem)
                )

                return MediaSession.ConnectionResult.accept(
                    sessionCommandsBuilder.build(),
                    defaultResult.availablePlayerCommands
                )
            }

            override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
                clearLastBrowsedParent(controller)
                super.onDisconnected(session, controller)
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                Timber.tag("MusicService")
                    .d("onCustomCommand received: ${customCommand.customAction}")
                when (customCommand.customAction) {
                    MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER -> {
                        closeNotificationPlayer()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_COUNTED_PLAY -> {
                        val count = args.getInt("count", 1)
                        startCountedPlay(count)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_COUNTED_PLAY -> {
                        stopCountedPlay()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_DURATION -> {
                        val minutes = args.getInt(
                            MusicNotificationProvider.EXTRA_SLEEP_TIMER_MINUTES,
                            0
                        )
                        setDurationSleepTimer(minutes)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SLEEP_TIMER_END_OF_TRACK -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_END_OF_TRACK_ENABLED,
                            true
                        )
                        setEndOfTrackSleepTimer(enabled)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER -> {
                        cancelSleepTimers()
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_TOGGLE_SHUFFLE -> {
                        val enabled = !isManualShuffleEnabled
                        updateManualShuffleState(session, enabled = enabled, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_ON. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        updateManualShuffleState(session, enabled = true, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF -> {
                        Timber.tag("MusicService")
                            .d("Executing SHUFFLE_OFF. Current shuffleMode: ${session.player.shuffleModeEnabled}")
                        updateManualShuffleState(session, enabled = false, broadcast = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                            false
                        )
                        updateManualShuffleState(session, enabled = enabled, broadcast = false)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE -> {
                        val currentMode = session.player.repeatMode
                        val newMode = when (currentMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else -> Player.REPEAT_MODE_OFF
                        }
                        session.player.repeatMode = newMode
                        refreshMediaSessionUi(session)
                        requestWidgetFullUpdate(force = true)
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_LIKE -> {
                        val songId = session.player.currentMediaItem?.mediaId
                            ?: return@onCustomCommand Futures.immediateFuture(
                                SessionResult(SessionError.ERROR_UNKNOWN)
                            )
                        val targetFavoriteState = !favoriteSongIds.contains(songId)
                        return@onCustomCommand setCurrentSongFavoriteState(
                            session = session,
                            targetFavoriteState = targetFavoriteState
                        )
                    }
                    MusicNotificationProvider.CUSTOM_COMMAND_SET_FAVORITE_STATE -> {
                        val enabled = args.getBoolean(
                            MusicNotificationProvider.EXTRA_FAVORITE_ENABLED,
                            false
                        )
                        return@onCustomCommand setCurrentSongFavoriteState(
                            session = session,
                            targetFavoriteState = enabled
                        )
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }

            // --- Android Auto: Media Library Browsing ---

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val rootItem = MediaItem.Builder()
                    .setMediaId(AutoMediaBrowseTree.ROOT_ID)
                    .setMediaMetadata(
                        androidx.media3.common.MediaMetadata.Builder()
                            .setTitle("PixelPlay")
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
                return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    try {
                        rememberLastBrowsedParent(browser, parentId)
                        val children = autoMediaBrowseTree.getChildren(parentId, page, pageSize)
                        grantArtworkUriPermissions(browser.packageName, children)
                        LibraryResult.ofItemList(children, params)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetChildren failed for parentId=$parentId")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                return serviceScope.future {
                    try {
                        val item = autoMediaBrowseTree.getItem(mediaId)
                        if (item != null) {
                            grantArtworkUriPermissions(browser.packageName, listOf(item))
                            LibraryResult.ofItem(item, null)
                        } else {
                            LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetItem failed for mediaId=$mediaId")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onSearch(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<Void>> {
                // Signal that search is supported; results delivered via onGetSearchResult
                return Futures.immediateFuture(LibraryResult.ofVoid())
            }

            override fun onGetSearchResult(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                query: String,
                page: Int,
                pageSize: Int,
                params: MediaLibraryService.LibraryParams?
            ): ListenableFuture<LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
                return serviceScope.future {
                    try {
                        val allResults = autoMediaBrowseTree.search(query)
                        val effectivePage = page.coerceAtLeast(0)
                        val effectivePageSize = if (pageSize > 0) pageSize else Int.MAX_VALUE
                        val offset = (effectivePage.toLong() * effectivePageSize.toLong())
                            .coerceAtMost(Int.MAX_VALUE.toLong())
                            .toInt()
                        val pagedResults = allResults
                            .drop(offset)
                            .take(effectivePageSize)

                        grantArtworkUriPermissions(browser.packageName, pagedResults)
                        LibraryResult.ofItemList(pagedResults, params)
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "onGetSearchResult failed for query=$query")
                        LibraryResult.ofError(SessionError.ERROR_UNKNOWN)
                    }
                }
            }

            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>
            ): ListenableFuture<MutableList<MediaItem>> {
                return serviceScope.future {
                    if (mediaItems.size == 1) {
                        resolveContextQueueForRequestedItem(mediaItems.first(), controller)?.let { queue ->
                            grantArtworkUriPermissions(controller.packageName, queue.mediaItems)
                            return@future queue.mediaItems
                        }
                    }
                    resolveMediaItemsByIds(mediaItems).also { resolvedItems ->
                        grantArtworkUriPermissions(
                            controller.packageName,
                            resolvedItems.trustedArtworkGrantItems
                        )
                    }.mediaItems
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                return serviceScope.future {
                    val requestedIndex = startIndex.coerceIn(0, (mediaItems.size - 1).coerceAtLeast(0))
                    val requestedItem = mediaItems.getOrNull(requestedIndex)

                    val contextQueue = requestedItem?.let {
                        resolveContextQueueForRequestedItem(it, controller)
                    }
                    if (contextQueue != null) {
                        grantArtworkUriPermissions(controller.packageName, contextQueue.mediaItems)
                        return@future MediaSession.MediaItemsWithStartPosition(
                            contextQueue.mediaItems,
                            contextQueue.startIndex,
                            startPositionMs
                        )
                    }

                    val resolvedItems = resolveMediaItemsByIds(mediaItems)
                    grantArtworkUriPermissions(
                        controller.packageName,
                        resolvedItems.trustedArtworkGrantItems
                    )
                    val safeStartIndex = requestedIndex.coerceIn(
                        0,
                        (resolvedItems.mediaItems.size - 1).coerceAtLeast(0)
                    )
                    MediaSession.MediaItemsWithStartPosition(
                        resolvedItems.mediaItems,
                        safeStartIndex,
                        startPositionMs
                    )
                }
            }
        }

        mediaSession = MediaLibrarySession.Builder(this, engine.masterPlayer, callback)
            .setSessionActivity(getOpenAppPendingIntent())
            .setBitmapLoader(CoilBitmapLoader(this, serviceScope))
            .build()

        val localOnlyProvider = LocalOnlyMediaNotificationProvider(this).also {
            it.setSmallIcon(R.drawable.monochrome_player)
        }
        setMediaNotificationProvider(localOnlyProvider)
        serviceScope.launch {
            restorePlaybackQueueSnapshotIfNeeded()
            mediaSession?.let { refreshMediaSessionUi(it) }
            requestWidgetFullUpdate(force = true)
        }

        serviceScope.launch {
            musicRepository.getFavoriteSongIdsFlow().collect { ids ->
                Timber.tag("MusicService")
                    .d("favoriteSongIdsFlow(Room) collected. New ids size: ${ids.size}")
                val oldIds = favoriteSongIds
                favoriteSongIds = ids
                val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
                if (currentSongId != null) {
                    val wasFavorite = oldIds.contains(currentSongId)
                    val isFavorite = ids.contains(currentSongId)
                    if (wasFavorite != isFavorite) {
                        Timber.tag("MusicService")
                            .d("Favorite status changed for current song. Updating notification.")
                        mediaSession?.let { refreshMediaSessionUi(it) }
                        requestWidgetFullUpdate(force = true)
                    }
                }
            }
        }
    }

    private fun shouldRejectWearController(controller: MediaSession.ControllerInfo): Boolean {
        val controllerPackage = controller.packageName
        if (controllerPackage.startsWith(APP_PACKAGE_PREFIX)) {
            return false
        }
        val blockedByPackage = BLOCKED_WEAR_CONTROLLER_PREFIXES.any { prefix ->
            controllerPackage.startsWith(prefix)
        }
        if (blockedByPackage) {
            return true
        }

        val hasWearHints = controller.connectionHints.keySet().any { key ->
            WEAR_HINT_KEY_MARKERS.any { marker ->
                key.contains(marker, ignoreCase = true)
            }
        }
        if (!hasWearHints) {
            return false
        }
        // If hints identify a Wear/remote controller and it's not our app package,
        // reject to avoid the default Wear system media player hijacking the session.
        return true
    }

    private fun createSleepTimerPendingIntent(): PendingIntent {
        val intent = Intent(this, SleepTimerReceiver::class.java)
        return PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelDurationSleepTimerInternal() {
        alarmManager.cancel(createSleepTimerPendingIntent())
    }

    private fun setDurationSleepTimer(minutes: Int) {
        if (minutes <= 0) {
            cancelSleepTimers()
            return
        }
        endOfTrackTimerSongId = null
        val triggerAtMillis = System.currentTimeMillis() + (minutes * 60_000L)
        val pendingIntent = createSleepTimerPendingIntent()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )
                }
            } else
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent,
                )
            Timber.tag(TAG).d("Sleep timer set from Wear for %d minutes", minutes)
        } catch (e: SecurityException) {
            Timber.tag(TAG).w(e, "Exact alarm denied; using inexact sleep timer")
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun setEndOfTrackSleepTimer(enabled: Boolean) {
        if (!enabled) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer disabled from Wear")
            return
        }
        cancelDurationSleepTimerInternal()
        val currentSongId = mediaSession?.player?.currentMediaItem?.mediaId
        if (currentSongId.isNullOrBlank()) {
            endOfTrackTimerSongId = null
            Timber.tag(TAG).d("End-of-track timer ignored: no active song")
            return
        }
        endOfTrackTimerSongId = currentSongId
        Timber.tag(TAG).d("End-of-track timer set from Wear for mediaId=%s", currentSongId)
    }

    private fun cancelSleepTimers() {
        cancelDurationSleepTimerInternal()
        endOfTrackTimerSongId = null
        Timber.tag(TAG).d("Sleep timers cancelled from Wear")
    }

    private fun startTemporaryForegroundForCommand() {
        val notification = NotificationCompat.Builder(
            this,
            PixelPlayApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(R.drawable.monochrome_player)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.service_processing_action))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(getOpenAppPendingIntent())
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to promote service to foreground for external command")
        }
    }

    private fun isServiceAlreadyForeground(): Boolean {
        val player = mediaSession?.player ?: return false
        return player.playWhenReady &&
            player.playbackState != Player.STATE_IDLE &&
            player.playbackState != Player.STATE_ENDED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startedTemporaryForegroundInOnCreate = temporaryForegroundStartedInOnCreate
        temporaryForegroundStartedInOnCreate = false
        val pendingMediaButtonForegroundStart = consumePendingMediaButtonForegroundStart()
        val forcedForegroundStart =
            intent?.getBooleanExtra(EXTRA_FORCE_FOREGROUND_ON_START, false) == true
        val isMediaButtonIntent = intent?.action == Intent.ACTION_MEDIA_BUTTON
        val needsTemporaryForeground = forcedForegroundStart ||
            pendingMediaButtonForegroundStart ||
            (isMediaButtonIntent &&
                !startedTemporaryForegroundInOnCreate &&
                !isServiceAlreadyForeground())
        if (needsTemporaryForeground && !startedTemporaryForegroundInOnCreate) {
            startTemporaryForegroundForCommand()
        }

        intent?.action?.let { action ->
            val player = mediaSession?.player ?: return@let
            when (action) {
                PlayerActions.PLAY_PAUSE -> player.playWhenReady = !player.playWhenReady
                PlayerActions.NEXT -> player.seekToNext()
                PlayerActions.PREVIOUS -> player.seekToPrevious()
                PlayerActions.FAVORITE -> {
                    val songId = player.currentMediaItem?.mediaId
                    if (!songId.isNullOrBlank()) {
                        serviceScope.launch {
                            val updatedFavorite = musicRepository.toggleFavoriteStatus(songId)
                            favoriteSongIds = if (updatedFavorite) {
                                favoriteSongIds + songId
                            } else {
                                favoriteSongIds - songId
                            }
                            mediaSession?.let { refreshMediaSessionUi(it) }
                            requestWidgetFullUpdate(force = true)
                        }
                    }
                }
                PlayerActions.PLAY_FROM_QUEUE -> {
                    val songId = intent.getLongExtra("song_id", -1L)
                    if (songId != -1L) {
                        val timeline = player.currentTimeline
                        if (!timeline.isEmpty) {
                            val window = Timeline.Window()
                            for (i in 0 until timeline.windowCount) {
                                timeline.getWindow(i, window)
                                if (window.mediaItem.mediaId.toLongOrNull() == songId) {
                                    player.seekTo(i, C.TIME_UNSET)
                                    player.play()
                                    break
                                }
                            }
                        }
                    }
                }
                PlayerActions.SHUFFLE -> {
                    val newState = !isManualShuffleEnabled
                    mediaSession?.let { session ->
                        updateManualShuffleState(session, enabled = newState, broadcast = true)
                    }
                }
                PlayerActions.REPEAT -> {
                    val newMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                        else -> Player.REPEAT_MODE_OFF
                    }
                    player.repeatMode = newMode
                    requestWidgetFullUpdate(force = true)
                }
                ACTION_SLEEP_TIMER_EXPIRED -> {
                    Timber.tag(TAG).d("Sleep timer expired action received. Pausing player.")
                    cancelDurationSleepTimerInternal()
                    player.pause()
                }
            }
        }
        val startCommandResult = super.onStartCommand(intent, flags, startId)
        if (needsTemporaryForeground) {
            val player = mediaSession?.player
            val isActivelyPlaying = player?.let {
                it.playWhenReady &&
                    it.playbackState != Player.STATE_IDLE &&
                    it.playbackState != Player.STATE_ENDED
            } == true
            if (!isActivelyPlaying) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return startCommandResult
    }

    private val playerListener = object : Player.Listener {
        override fun onVolumeChanged(volume: Float) {
            if (engine.isTransitionRunning()) {
                return
            }
            val expectedVolume = expectedReplayGainVolume
            if (expectedVolume != null && abs(expectedVolume - volume) < 0.001f) {
                expectedReplayGainVolume = null
                return
            }
            expectedReplayGainVolume = null
            userSelectedVolume = volume.coerceIn(0f, 1f)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val player = engine.masterPlayer
            Timber.tag(TAG).d("onIsPlayingChanged: $isPlaying. Duration: ${player.duration}, Seekable: ${player.isCurrentMediaItemSeekable}")
            // Push state immediately so the watch can foreground PixelPlay before
            // system media surfaces take over.
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            when {
                playWhenReady -> clearHeadsetReconnectResume()
                !resumeOnHeadsetReconnectEnabled -> clearHeadsetReconnectResume()
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> {
                    shouldResumeAfterHeadsetReconnect = true
                    lastNoisyPauseRealtimeMs = SystemClock.elapsedRealtime()
                    Timber.tag(TAG).d("Marked playback for headset reconnect resume")
                }
                else -> clearHeadsetReconnectResume()
            }
        }
        
        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
             val canSeek = availableCommands.contains(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
             val player = engine.masterPlayer
             Timber.tag(TAG).w("onAvailableCommandsChanged. Can Seek Command? $canSeek. IsSeekable? ${player.isCurrentMediaItemSeekable}. Duration: ${player.duration}")
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            Timber.tag(TAG).d("Playback state changed: $playbackState")
            if (playbackState == Player.STATE_ENDED) {
                endOfTrackTimerSongId = null
            }
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist(immediate = playbackState == Player.STATE_IDLE)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            requestWidgetFullUpdate(force = true)
            schedulePlaybackSnapshotPersist(immediate = timeline.isEmpty)
        }

        override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
            val eotTargetSongId = endOfTrackTimerSongId
            if (!eotTargetSongId.isNullOrBlank()) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val previousSongId = engine.masterPlayer.run {
                        if (previousMediaItemIndex != C.INDEX_UNSET) {
                            runCatching { getMediaItemAt(previousMediaItemIndex).mediaId }.getOrNull()
                        } else {
                            null
                        }
                    }
                    if (previousSongId == eotTargetSongId) {
                        endOfTrackTimerSongId = null
                        engine.masterPlayer.seekTo(0L)
                        engine.masterPlayer.pause()
                        Timber.tag(TAG).d("Paused playback at end of track from Wear timer")
                    }
                } else if (item?.mediaId != eotTargetSongId) {
                    endOfTrackTimerSongId = null
                    Timber.tag(TAG).d("Cleared end-of-track timer after manual track change")
                }
            }
            requestWidgetAndWearRefreshWithFollowUp()
            mediaSession?.let { refreshMediaSessionUiWithFollowUp(it) }
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            // Some devices/apps deliver title/artist/art after transition callback.
            // Force an immediate publish for real-time watch metadata.
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUiWithFollowUp(it) }
            // Apply ReplayGain volume adjustment for the new track
            applyReplayGain(mediaSession?.player?.currentMediaItem)
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            Timber.tag("MusicService")
                .d("playerListener.onShuffleModeEnabledChanged: $shuffleModeEnabled")
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            requestWidgetFullUpdate(force = true)
            mediaSession?.let { refreshMediaSessionUi(it) }
            schedulePlaybackSnapshotPersist()
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.tag(TAG).e(error, "Error en el reproductor: ")
        }
    }

    /**
     * Applies ReplayGain volume normalization to the current track.
     * Reads RG tags from the file and adjusts player.volume accordingly.
     */
    private fun applyReplayGain(mediaItem: MediaItem?) {
        val player = engine.masterPlayer
        replayGainJob?.cancel()
        replayGainRequestToken += 1
        val requestToken = replayGainRequestToken

        if (mediaItem == null) {
            return
        }

        if (!replayGainEnabled) {
            pendingReplayGainVolume = null
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(player, userSelectedVolume)
            }
            return
        }

        val mediaId = mediaItem.mediaId
        val filePath = mediaItem.mediaMetadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_FILE_PATH)

        if (filePath.isNullOrBlank()) {
            Timber.tag(TAG).d("ReplayGain: No file path for track, keeping user-selected volume")
            if (!engine.isTransitionRunning()) {
                setPlayerVolume(player, userSelectedVolume)
            }
            return
        }

        val useAlbumGain = replayGainUseAlbumGain
        // Read ReplayGain tags on IO thread to avoid blocking main
        replayGainJob = serviceScope.launch {
            val rgValues = withContext(Dispatchers.IO) {
                replayGainManager.readReplayGain(filePath)
            }

            if (requestToken != replayGainRequestToken) {
                return@launch
            }

            val currentMediaId = mediaSession?.player?.currentMediaItem?.mediaId
            if (currentMediaId != mediaId) {
                Timber.tag(TAG).d("ReplayGain: Ignoring stale result for mediaId=%s", mediaId)
                return@launch
            }

            val volume = replayGainManager.getVolumeMultiplier(
                rgValues,
                useAlbumGain = useAlbumGain
            )

            if (engine.isTransitionRunning()) {
                // Store for application after transition completes
                pendingReplayGainVolume = volume
                Timber.tag(TAG).d("ReplayGain: Stored pending volume=%.2f for %s (transition running)",
                    volume, mediaItem.mediaMetadata.title
                )
            } else {
                pendingReplayGainVolume = null
                setPlayerVolume(player, volume)
                Timber.tag(TAG).d("ReplayGain: Applied volume=%.2f for %s",
                    volume, mediaItem.mediaMetadata.title
                )
            }
        }
    }

    private fun setPlayerVolume(player: Player, volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        expectedReplayGainVolume = clampedVolume
        player.volume = clampedVolume
    }

    private fun onTransitionFinished() {
        val player = engine.masterPlayer
        val pending = pendingReplayGainVolume
        pendingReplayGainVolume = null

        if (!replayGainEnabled) {
            setPlayerVolume(player, userSelectedVolume)
            Timber.tag(TAG).d("ReplayGain: Transition finished, RG disabled — restored userSelectedVolume=%.2f", userSelectedVolume)
            return
        }

        if (pending != null) {
            setPlayerVolume(player, pending)
            Timber.tag(TAG).d("ReplayGain: Transition finished, applied pending volume=%.2f", pending)
        } else {
            // No pending volume was computed during transition, trigger full computation
            applyReplayGain(mediaSession?.player?.currentMediaItem)
            Timber.tag(TAG).d("ReplayGain: Transition finished, no pending volume — triggering full recomputation")
        }
    }

    private fun initializeCastWearSync() {
        val sessionManager = runCatching {
            CastContext.getSharedInstance(this).sessionManager
        }.getOrElse { error ->
            Timber.tag(TAG).w(error, "CastContext unavailable; skipping cast wear sync setup")
            return
        }
        castSessionManager = sessionManager

        val remoteCallback = object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                requestWidgetFullUpdate(force = false)
            }

            override fun onMetadataUpdated() {
                requestWidgetFullUpdate(force = false)
            }

            override fun onQueueStatusUpdated() {
                requestWidgetFullUpdate(force = false)
            }

            override fun onPreloadStatusUpdated() {
                requestWidgetFullUpdate(force = false)
            }
        }
        castRemoteClientCallback = remoteCallback

        val sessionListener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(session: CastSession, sessionId: String) {
                attachCastRemoteClient(session)
            }

            override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
                attachCastRemoteClient(session)
            }

            override fun onSessionEnded(session: CastSession, error: Int) {
                if (observedCastSession === session) {
                    attachCastRemoteClient(null)
                } else {
                    requestWidgetFullUpdate(force = true)
                }
            }

            override fun onSessionStarting(session: CastSession) = Unit
            override fun onSessionStartFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionEnding(session: CastSession) = Unit
            override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
            override fun onSessionResumeFailed(session: CastSession, error: Int) = requestWidgetFullUpdate(force = true)
            override fun onSessionSuspended(session: CastSession, reason: Int) = requestWidgetFullUpdate(force = true)
        }
        castSessionManagerListener = sessionListener
        runCatching {
            sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to register Cast session listener")
        }

        attachCastRemoteClient(sessionManager.currentCastSession)
    }

    private fun attachCastRemoteClient(session: CastSession?) {
        if (observedCastSession === session) return

        observedCastSession?.remoteMediaClient?.let { oldClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { oldClient.unregisterCallback(callback) }
            }
        }

        observedCastSession = session
        session?.remoteMediaClient?.let { remoteClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { remoteClient.registerCallback(callback) }
            }
            remoteClient.requestStatus()
        }
        requestWidgetFullUpdate(force = true)
    }

    private fun stopCastWearSync() {
        observedCastSession?.remoteMediaClient?.let { remoteClient ->
            castRemoteClientCallback?.let { callback ->
                runCatching { remoteClient.unregisterCallback(callback) }
            }
        }
        observedCastSession = null

        val listener = castSessionManagerListener
        val manager = castSessionManager
        if (listener != null && manager != null) {
            runCatching { manager.removeSessionManagerListener(listener, CastSession::class.java) }
                .onFailure { e ->
                    Timber.tag(TAG).w(e, "Failed to remove Cast session listener")
                }
        }
        castSessionManagerListener = null
        castRemoteClientCallback = null
        castSessionManager = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        val allowBackground = keepPlayingInBackground

        if (!allowBackground) {
            stopPlaybackAndUnload(
                reason = "task_removed_background_disabled"
            )
            return
        }

        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopPlaybackAndUnload(
                reason = "task_removed_not_playing"
            )
            return
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onDestroy() {
        playbackSnapshotPersistJob?.cancel()
        stopCastWearSync()
        unregisterHeadsetReconnectMonitor()
        wearStatePublisher.clearState()
        replayGainJob?.cancel()

        engine.removePlayerSwapListener(playerSwapListener)
        engine.removeTransitionFinishedListener(transitionFinishedListener)
        engine.masterPlayer.removeListener(playerListener)

        mediaSession?.run {
            release()
            mediaSession = null
        }
        engine.release()
        controller.release()
        serviceScope.cancel()
        Thread.currentThread().setUncaughtExceptionHandler(previousMainThreadExceptionHandler)
        previousMainThreadExceptionHandler = null
        super.onDestroy()
    }

    private fun registerHeadsetReconnectMonitor() {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (!addedDevices.any(::isReconnectableHeadsetOutput)) return
                maybeResumeAfterHeadsetReconnect()
            }
        }

        audioManager.registerAudioDeviceCallback(callback, null)
        headsetReconnectCallback = callback
    }

    private fun unregisterHeadsetReconnectMonitor() {
        headsetReconnectCallback?.let { callback ->
            runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
        }
        headsetReconnectCallback = null
        clearHeadsetReconnectResume()
    }

    private fun maybeResumeAfterHeadsetReconnect() {
        if (!resumeOnHeadsetReconnectEnabled || !shouldResumeAfterHeadsetReconnect) return

        val elapsedSinceNoisyPause = SystemClock.elapsedRealtime() - lastNoisyPauseRealtimeMs
        if (elapsedSinceNoisyPause > HEADSET_RECONNECT_RESUME_WINDOW_MS) {
            clearHeadsetReconnectResume()
            return
        }

        if (!hasReconnectableHeadsetOutput()) {
            return
        }

        val player = engine.masterPlayer
        if (
            player.currentMediaItem == null ||
            player.playWhenReady ||
            player.playbackState == Player.STATE_IDLE ||
            player.playbackState == Player.STATE_ENDED
        ) {
            clearHeadsetReconnectResume()
            return
        }

        Timber.tag(TAG).d("Resuming playback after headset reconnect")
        clearHeadsetReconnectResume()
        player.play()
    }

    private fun hasReconnectableHeadsetOutput(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .any(::isReconnectableHeadsetOutput)
    }

    private fun isReconnectableHeadsetOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> true
            else -> false
        }
    }

    private fun clearHeadsetReconnectResume() {
        shouldResumeAfterHeadsetReconnect = false
        lastNoisyPauseRealtimeMs = 0L
    }

    private fun schedulePlaybackSnapshotPersist(immediate: Boolean = false) {
        playbackSnapshotPersistJob?.cancel()
        playbackSnapshotPersistJob = serviceScope.launch {
            if (!immediate) {
                delay(PLAYBACK_SNAPSHOT_DEBOUNCE_MS)
            }
            persistPlaybackSnapshot()
        }
    }

    private suspend fun persistPlaybackSnapshot() {
        if (isRestoringPlaybackSnapshot) return
        val snapshot = capturePlaybackSnapshot()
        runCatching {
            userPreferencesRepository.setPlaybackQueueSnapshot(snapshot)
        }.onFailure { e ->
            Timber.tag(TAG).w(e, "Failed to persist playback snapshot")
        }
    }

    private suspend fun capturePlaybackSnapshot(): PlaybackQueueSnapshot? =
        withContext(Dispatchers.Main.immediate) {
            val player = engine.masterPlayer
            val mediaItemCount = player.mediaItemCount
            if (mediaItemCount <= 0) {
                return@withContext null
            }

            val snapshotItems = ArrayList<PlaybackQueueItemSnapshot>(mediaItemCount)
            for (index in 0 until mediaItemCount) {
                val mediaItem = player.getMediaItemAt(index)
                val metadata = mediaItem.mediaMetadata
                val uri = mediaItem.localConfiguration?.uri?.toString()
                    ?: metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI)

                if (mediaItem.mediaId.isBlank() || uri.isNullOrBlank()) {
                    continue
                }

                val durationMs = metadata.extras
                    ?.getLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION)
                    ?.takeIf { it > 0L }

                snapshotItems.add(
                    PlaybackQueueItemSnapshot(
                        mediaId = mediaItem.mediaId,
                        uri = uri,
                        title = metadata.title?.toString(),
                        artist = metadata.artist?.toString(),
                        albumTitle = metadata.albumTitle?.toString(),
                        artworkUri = metadata.artworkUri?.toString()
                            ?: metadata.extras?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART),
                        durationMs = durationMs,
                    )
                )
            }

            if (snapshotItems.isEmpty()) {
                return@withContext null
            }

            val currentMediaId = player.currentMediaItem?.mediaId
            val indexFromMediaId = currentMediaId
                ?.let { id -> snapshotItems.indexOfFirst { it.mediaId == id } }
                ?.takeIf { it >= 0 }

            val safeCurrentIndex = when {
                indexFromMediaId != null -> indexFromMediaId
                player.currentMediaItemIndex in snapshotItems.indices -> player.currentMediaItemIndex
                else -> 0
            }

            val safeRepeatMode = when (player.repeatMode) {
                Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE,
                Player.REPEAT_MODE_ALL -> player.repeatMode
                else -> Player.REPEAT_MODE_OFF
            }

            PlaybackQueueSnapshot(
                items = snapshotItems,
                currentMediaId = currentMediaId,
                currentIndex = safeCurrentIndex,
                currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                playWhenReady = player.playWhenReady,
                repeatMode = safeRepeatMode,
                shuffleEnabled = isManualShuffleEnabled,
            )
        }

    private suspend fun restorePlaybackQueueSnapshotIfNeeded() {
        val alreadyHasQueue = withContext(Dispatchers.Main.immediate) {
            engine.masterPlayer.mediaItemCount > 0
        }
        if (alreadyHasQueue) return

        val snapshot = runCatching {
            userPreferencesRepository.getPlaybackQueueSnapshotOnce()
        }.getOrNull() ?: return

        if (snapshot.items.isEmpty()) {
            return
        }

        val restoredItems = snapshot.items.mapNotNull(::buildMediaItemFromSnapshot)
        if (restoredItems.isEmpty()) {
            userPreferencesRepository.setPlaybackQueueSnapshot(null)
            return
        }

        withContext(Dispatchers.Main.immediate) {
            val player = engine.masterPlayer
            if (player.mediaItemCount > 0) {
                return@withContext
            }

            val safeRepeatMode = when (snapshot.repeatMode) {
                Player.REPEAT_MODE_OFF,
                Player.REPEAT_MODE_ONE,
                Player.REPEAT_MODE_ALL -> snapshot.repeatMode
                else -> Player.REPEAT_MODE_OFF
            }
            val resolvedIndex = when {
                snapshot.currentIndex in restoredItems.indices -> snapshot.currentIndex
                !snapshot.currentMediaId.isNullOrBlank() -> {
                    restoredItems.indexOfFirst { it.mediaId == snapshot.currentMediaId }
                        .takeIf { it >= 0 } ?: 0
                }
                else -> 0
            }

            isRestoringPlaybackSnapshot = true
            try {
                player.setMediaItems(
                    restoredItems,
                    resolvedIndex,
                    snapshot.currentPositionMs.coerceAtLeast(0L)
                )
                // Even paused restores must prepare the timeline so duration/seek state is
                // available immediately when the UI opens after a cold start.
                player.prepare()
                player.repeatMode = safeRepeatMode
                player.shuffleModeEnabled = false
                isManualShuffleEnabled = snapshot.shuffleEnabled
                if (snapshot.playWhenReady) {
                    player.playWhenReady = true
                }
            } finally {
                isRestoringPlaybackSnapshot = false
            }
        }

        Timber.tag(TAG).i(
            "Restored playback snapshot: items=%d index=%d playWhenReady=%s",
            restoredItems.size,
            snapshot.currentIndex,
            snapshot.playWhenReady
        )
        schedulePlaybackSnapshotPersist(immediate = true)
    }

    private fun buildMediaItemFromSnapshot(snapshotItem: PlaybackQueueItemSnapshot): MediaItem? {
        if (snapshotItem.mediaId.isBlank() || snapshotItem.uri.isBlank()) {
            return null
        }

        val metadataBuilder = MediaMetadata.Builder()
        snapshotItem.title?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setTitle(it) }
        snapshotItem.artist?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setArtist(it) }
        snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let { metadataBuilder.setAlbumTitle(it) }
        MediaItemBuilder.externalControllerArtworkUri(this, snapshotItem.artworkUri)
            ?.let { metadataBuilder.setArtworkUri(it) }

        val extras = Bundle().apply {
            putBoolean(
                MediaItemBuilder.EXTERNAL_EXTRA_FLAG,
                snapshotItem.mediaId.startsWith("external:")
            )
            putString(MediaItemBuilder.EXTERNAL_EXTRA_CONTENT_URI, snapshotItem.uri)
            snapshotItem.albumTitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM, it)
            }
            snapshotItem.artworkUri?.takeIf { it.isNotBlank() }?.let {
                putString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART, it)
            }
            snapshotItem.durationMs?.takeIf { it > 0L }?.let {
                putLong(MediaItemBuilder.EXTERNAL_EXTRA_DURATION, it)
            }
        }
        metadataBuilder.setExtras(extras)

        return MediaItem.Builder()
            .setMediaId(snapshotItem.mediaId)
            .setUri(MediaItemBuilder.playbackUri(snapshotItem.uri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun getOpenAppPendingIntent(): PendingIntent {
        val intent = Intent(WearIntents.ACTION_OPEN_PLAYER).apply {
            `package` = packageName
            addCategory(Intent.CATEGORY_DEFAULT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ACTION_SHOW_PLAYER", true) // Signal to MainActivity to show the player
        }
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // --- LÓGICA PARA ACTUALIZACIÓN DE WIDGETS Y DATOS ---
    private var debouncedWidgetUpdateJob: Job? = null
    private var followUpWidgetUpdateJob: Job? = null
    private var followUpMediaSessionUiRefreshJob: Job? = null
    private val WIDGET_STATE_DEBOUNCE_MS = 300L

    private fun requestWidgetFullUpdate(force: Boolean = false) {
        debouncedWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob = serviceScope.launch {
            if (!force) {
                delay(WIDGET_STATE_DEBOUNCE_MS)
            }
            processWidgetUpdateInternal()
        }
    }

    private fun requestWidgetAndWearRefreshWithFollowUp() {
        requestWidgetFullUpdate(force = true)
        followUpWidgetUpdateJob?.cancel()
        followUpWidgetUpdateJob = serviceScope.launch {
            delay(250L)
            requestWidgetFullUpdate(force = true)
        }
    }

    private data class RemotePlaybackSnapshot(
        val songId: String?,
        val title: String,
        val artist: String,
        val artworkUri: Uri?,
        val isPlaying: Boolean,
        val currentPositionMs: Long,
        val totalDurationMs: Long,
        val repeatMode: Int,
        val isShuffleEnabled: Boolean,
    )

    private fun resolveCastRemoteSnapshot(): RemotePlaybackSnapshot? {
        val remoteClient = observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient
            ?: return null

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

        return RemotePlaybackSnapshot(
            songId = songId,
            title = metadata?.getString(CastMediaMetadata.KEY_TITLE).orEmpty(),
            artist = metadata?.getString(CastMediaMetadata.KEY_ARTIST).orEmpty(),
            artworkUri = imageUri,
            isPlaying = mediaStatus.playerState == MediaStatus.PLAYER_STATE_PLAYING,
            currentPositionMs = remoteClient.approximateStreamPosition.coerceAtLeast(0L),
            totalDurationMs = effectiveDurationMs,
            repeatMode = mappedRepeatMode,
            isShuffleEnabled = mediaStatus.queueRepeatMode == MediaStatus.REPEAT_MODE_REPEAT_ALL_AND_SHUFFLE,
        )
    }

    private suspend fun resolveCurrentMediaIdForWear(): String? {
        val remoteSongId = resolveCastRemoteSnapshot()?.songId
        if (!remoteSongId.isNullOrBlank()) {
            return remoteSongId
        }
        val player = engine.masterPlayer
        return withContext(Dispatchers.Main) { player.currentMediaItem?.mediaId }
    }

    private var lastWidgetPlayerInfo: PlayerInfo? = null

    private fun shouldUpdateWidget(old: PlayerInfo, new: PlayerInfo): Boolean {
        if (old.songTitle != new.songTitle) return true
        if (old.artistName != new.artistName) return true
        if (old.isPlaying != new.isPlaying) return true
        if (old.albumArtUri != new.albumArtUri) return true
        if (old.isFavorite != new.isFavorite) return true
        if (old.queue != new.queue) return true
        if (old.themeColors != new.themeColors) return true
        if (old.isShuffleEnabled != new.isShuffleEnabled) return true
        if (old.repeatMode != new.repeatMode) return true
        if (old.totalDurationMs != new.totalDurationMs) return true
        if (old.wearThemePalette != new.wearThemePalette) return true
        
        val drift = kotlin.math.abs(old.currentPositionMs - new.currentPositionMs)
        return drift > 3000L
    }

    private fun shouldPublishWearState(old: PlayerInfo, new: PlayerInfo): Boolean {
        return shouldUpdateWidget(old, new) || old.wearQueueRevision != new.wearQueueRevision
    }

    private suspend fun processWidgetUpdateInternal() {
        val playerInfo = buildPlayerInfo()
        val oldInfo = lastWidgetPlayerInfo

        val shouldUpdateWidgets = oldInfo == null || shouldUpdateWidget(oldInfo, playerInfo)
        val shouldPublishWear = oldInfo == null || shouldPublishWearState(oldInfo, playerInfo)

        if (shouldUpdateWidgets || shouldPublishWear) {
            lastWidgetPlayerInfo = playerInfo
        }

        if (shouldUpdateWidgets) {
            updateGlanceWidgets(playerInfo)
        }

        if (shouldPublishWear) {
            val currentMediaId = resolveCurrentMediaIdForWear()
            // Publish state to Wear OS watch
            wearStatePublisher.publishState(currentMediaId, playerInfo)
        }
    }

    private fun buildWearQueueRevision(
        timeline: Timeline,
        currentIndex: Int,
        currentMediaId: String?,
    ): String {
        val remoteClient = observedCastSession?.remoteMediaClient
            ?: castSessionManager?.currentCastSession?.remoteMediaClient
        val remoteStatus = remoteClient?.mediaStatus
        val remoteQueueItems = remoteStatus?.queueItems.orEmpty()
        if (remoteQueueItems.isNotEmpty()) {
            val remoteCurrentIndex = remoteQueueItems.indexOfFirst {
                it.itemId == remoteStatus?.getCurrentItemId()
            }.takeIf { it >= 0 } ?: 0
            val remoteTokens = remoteQueueItems.map { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
                    ?: item.media?.contentId
                    ?: item.itemId.toString()
            }
            return encodeWearQueueRevision(remoteTokens, remoteCurrentIndex)
        }

        if (timeline.isEmpty) {
            return currentMediaId.orEmpty()
        }

        val window = Timeline.Window()
        val tokens = buildList(timeline.windowCount) {
            for (index in 0 until timeline.windowCount) {
                timeline.getWindow(index, window)
                val mediaItem = window.mediaItem
                add(
                    mediaItem.mediaId.ifBlank {
                        mediaItem.localConfiguration?.uri?.toString()
                            ?: mediaItem.mediaMetadata.title?.toString()
                            ?: index.toString()
                    }
                )
            }
        }
        val safeCurrentIndex = currentIndex.coerceIn(0, (timeline.windowCount - 1).coerceAtLeast(0))
        return encodeWearQueueRevision(tokens, safeCurrentIndex)
    }

    private fun encodeWearQueueRevision(queueTokens: List<String>, currentIndex: Int): String {
        if (queueTokens.isEmpty()) return ""
        return buildString {
            append(currentIndex)
            append('|')
            queueTokens.forEachIndexed { index, token ->
                if (index > 0) append(',')
                append(token)
            }
        }.hashCode().toString()
    }

    private suspend fun buildPlayerInfo(): PlayerInfo {
        val player = engine.masterPlayer
        // Batch all main-thread reads into a single context switch (was 7 separate hops → 1)
        var currentItem: MediaItem? = null
        var isPlaying = false
        var repeatMode = Player.REPEAT_MODE_OFF
        var currentPosition = 0L
        var totalDuration = 0L
        var snapshotWindowIndex = 0
        var snapshotTimeline: Timeline = Timeline.EMPTY

        withContext(Dispatchers.Main) {
            currentItem = player.currentMediaItem
            isPlaying = player.isPlaying
            repeatMode = player.repeatMode
            currentPosition = player.currentPosition
            totalDuration = player.duration.coerceAtLeast(0)
            snapshotWindowIndex = player.currentMediaItemIndex
            snapshotTimeline = player.currentTimeline
        }

        var shuffleEnabled = isManualShuffleEnabled // Manual shuffle for sync with PlayerViewModel

        var title = currentItem?.mediaMetadata?.title?.toString().orEmpty()
        var artist = currentItem?.mediaMetadata?.artist?.toString().orEmpty()
        var mediaId = currentItem?.mediaId
        var artworkUri = resolveArtworkUri(currentItem?.mediaMetadata)
        var artworkData = currentItem?.mediaMetadata?.artworkData

        resolveCastRemoteSnapshot()?.let { remote ->
            if (remote.title.isNotBlank()) {
                title = remote.title
            }
            if (remote.artist.isNotBlank()) {
                artist = remote.artist
            }
            if (!remote.songId.isNullOrBlank()) {
                mediaId = remote.songId
            }
            if (remote.artworkUri != null) {
                artworkUri = remote.artworkUri
                artworkData = null
            }
            isPlaying = remote.isPlaying
            currentPosition = remote.currentPositionMs
            if (remote.totalDurationMs > 0L) {
                totalDuration = remote.totalDurationMs
            }
            repeatMode = remote.repeatMode
            shuffleEnabled = remote.isShuffleEnabled
        }

        val (artBytes, artUriString) = getAlbumArtForWidget(artworkData, artworkUri)

        // Merge two IO preference reads into a single context switch
        val (playerTheme, paletteStyle) = withContext(Dispatchers.IO) {
            Pair(
                themePreferencesRepository.playerThemePreferenceFlow.first(),
                AlbumArtPaletteStyle.fromStorageKey(themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey)
            )
        }

        val schemePair: ColorSchemePair? = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC ->
                ColorSchemePair(
                    light = dynamicLightColorScheme(applicationContext),
                    dark = dynamicDarkColorScheme(applicationContext)
                )
            artUriString != null ->
                // Skip heavy palette recomputation when art + style haven't changed
                if (artUriString == cachedSchemeArtUri && paletteStyle == cachedSchemePaletteStyle) {
                    cachedColorSchemePair
                } else {
                    colorSchemeProcessor.getOrGenerateColorScheme(artUriString, paletteStyle).also {
                        cachedSchemeArtUri = artUriString
                        cachedSchemePaletteStyle = paletteStyle
                        cachedColorSchemePair = it
                    }
                }
            else -> null
        }

        val widgetColors = schemePair?.let {
            WidgetThemeColors(
                lightSurfaceContainer = it.light.surfaceContainer.toArgb(),
                lightSurfaceContainerLowest = it.light.surfaceContainerLowest.toArgb(),
                lightSurfaceContainerLow = it.light.surfaceContainerLow.toArgb(),
                lightSurfaceContainerHigh = it.light.surfaceContainerHigh.toArgb(),
                lightSurfaceContainerHighest = it.light.surfaceContainerHighest.toArgb(),
                lightTitle = it.light.onSurface.toArgb(),
                lightArtist = it.light.onSurfaceVariant.toArgb(),
                lightPlayPauseBackground = it.light.primary.toArgb(),
                lightPlayPauseIcon = it.light.onPrimary.toArgb(),
                lightPrevNextBackground = it.light.onPrimary.toArgb(),
                lightPrevNextIcon = it.light.primary.toArgb(),
                
                darkSurfaceContainer = it.dark.surfaceContainer.toArgb(),
                darkSurfaceContainerLowest = it.dark.surfaceContainerLowest.toArgb(),
                darkSurfaceContainerLow = it.dark.surfaceContainerLow.toArgb(),
                darkSurfaceContainerHigh = it.dark.surfaceContainerHigh.toArgb(),
                darkSurfaceContainerHighest = it.dark.surfaceContainerHighest.toArgb(),
                darkTitle = it.dark.onSurface.toArgb(),
                darkArtist = it.dark.onSurfaceVariant.toArgb(),
                darkPlayPauseBackground = it.dark.primary.toArgb(),
                darkPlayPauseIcon = it.dark.onPrimary.toArgb(),
                darkPrevNextBackground = it.dark.onPrimary.toArgb(),
                darkPrevNextIcon = it.dark.primary.toArgb()
            )
        }
        val wearThemePalette = schemePair?.let { buildWearThemePalette(it.dark) }

        val isFavorite = isSongFavorite(mediaId)
        val wearQueueRevision = buildWearQueueRevision(
            timeline = snapshotTimeline,
            currentIndex = snapshotWindowIndex,
            currentMediaId = mediaId,
        )

        val queueItems = mutableListOf<com.theveloper.pixelplay.data.model.QueueItem>()
        // Reuse snapshotTimeline / snapshotWindowIndex captured at the top — no extra main-thread hop
        if (!snapshotTimeline.isEmpty) {
            val window = Timeline.Window()

            // Empezar desde la siguiente canción en la cola
            val startIndex = if (snapshotWindowIndex + 1 < snapshotTimeline.windowCount) snapshotWindowIndex + 1 else 0

            // Limitar el número de elementos de la cola a 4
            val endIndex = (startIndex + 4).coerceAtMost(snapshotTimeline.windowCount)
            for (i in startIndex until endIndex) {
                snapshotTimeline.getWindow(i, window)
                val mediaItem = window.mediaItem
                val songId = mediaItem.mediaId.toLongOrNull()
                if (songId != null) {
                    queueItems.add(
                        com.theveloper.pixelplay.data.model.QueueItem(
                            id = songId,
                            albumArtBitmapData = null,
                            albumArtUri = resolveArtworkUri(mediaItem.mediaMetadata)?.toString()
                        )
                    )
                }
            }
        }

        return PlayerInfo(
            songTitle = title,
            artistName = artist,
            isPlaying = isPlaying,
            albumArtUri = artUriString,
            albumArtBitmapData = artBytes,
            currentPositionMs = currentPosition,
            totalDurationMs = totalDuration,
            isFavorite = isFavorite,
            queue = queueItems,
            themeColors = widgetColors,
            isShuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            wearThemePalette = wearThemePalette,
            wearQueueRevision = wearQueueRevision,
        )
    }

    // Color scheme cache: skip recomputation when art URI and palette style haven't changed
    private var cachedSchemeArtUri: String? = null
    private var cachedSchemePaletteStyle: AlbumArtPaletteStyle? = null
    private var cachedColorSchemePair: ColorSchemePair? = null
    private var cachedWidgetArtUri: String? = null
    private var cachedWidgetArtBytes: ByteArray? = null
    private var cachedWidgetArtLoadFailureUri: String? = null
    private var cachedWidgetArtLoadFailureAtMs: Long = 0L

    private suspend fun getAlbumArtForWidget(embeddedArt: ByteArray?, artUri: Uri?): Pair<ByteArray?, String?> = withContext(Dispatchers.IO) {
        val artUriString = artUri?.toString()
        embeddedArt?.takeIf { it.isNotEmpty() }?.let { bytes ->
            cachedWidgetArtUri = artUriString
            cachedWidgetArtBytes = bytes
            cachedWidgetArtLoadFailureUri = null
            cachedWidgetArtLoadFailureAtMs = 0L
            return@withContext bytes to artUriString
        }

        if (artUriString.isNullOrBlank()) {
            return@withContext null to artUriString
        }

        if (artUriString == cachedWidgetArtUri && cachedWidgetArtBytes != null) {
            return@withContext cachedWidgetArtBytes to artUriString
        }
        if (artUriString == cachedWidgetArtLoadFailureUri) {
            val failureAgeMs = SystemClock.elapsedRealtime() - cachedWidgetArtLoadFailureAtMs
            if (failureAgeMs < WIDGET_ART_FAILURE_RETRY_MS) {
                return@withContext null to artUriString
            }
        }

        val loadedBytes = loadArtworkBytesForWidget(artUri)
        if (loadedBytes != null) {
            cachedWidgetArtUri = artUriString
            cachedWidgetArtBytes = loadedBytes
            cachedWidgetArtLoadFailureUri = null
            cachedWidgetArtLoadFailureAtMs = 0L
            return@withContext loadedBytes to artUriString
        }

        cachedWidgetArtLoadFailureUri = artUriString
        cachedWidgetArtLoadFailureAtMs = SystemClock.elapsedRealtime()
        return@withContext null to artUriString
    }

    private fun resolveArtworkUri(metadata: MediaMetadata?): Uri? {
        metadata ?: return null
        metadata.artworkUri?.let { return it }
        val extrasUri = metadata.extras
            ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { extrasUri.toUri() }.getOrNull()
    }

    private fun loadArtworkBytesForWidget(uri: Uri): ByteArray? {
        val scheme = uri.scheme?.lowercase()
        return when (scheme) {
            "content", "file", "android.resource", com.theveloper.pixelplay.utils.LocalArtworkUri.SCHEME -> {
                runCatching {
                    AlbumArtUtils.openArtworkInputStream(applicationContext, uri)?.use { input ->
                        readBytesCapped(input, MAX_WIDGET_ARTWORK_BYTES)
                    }
                }.getOrElse { error ->
                    Timber.tag(TAG).w(error, "Widget artwork read failed for local uri=%s", uri)
                    null
                }
            }
            "http", "https" -> {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(uri.toString()).openConnection() as? HttpURLConnection)
                        ?: return null
                    connection.connectTimeout = 4_000
                    connection.readTimeout = 6_000
                    connection.instanceFollowRedirects = true
                    connection.doInput = true
                    connection.inputStream.use { input ->
                        readBytesCapped(input, MAX_WIDGET_ARTWORK_BYTES)
                    }
                } catch (error: Exception) {
                    Timber.tag(TAG).w(error, "Widget artwork read failed for remote uri=%s", uri)
                    null
                } finally {
                    connection?.disconnect()
                }
            }
            else -> null
        }
    }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_STREAM_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalRead += read
            if (totalRead > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
    }

    private suspend fun updateGlanceWidgets(playerInfo: PlayerInfo) = withContext(Dispatchers.IO) {
        try {
            val glanceManager = GlanceAppWidgetManager(applicationContext)

            val glanceIds = glanceManager.getGlanceIds(PixelPlayGlanceWidget::class.java)
            glanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                PixelPlayGlanceWidget().update(applicationContext, id)
            }

            val barGlanceIds = glanceManager.getGlanceIds(BarWidget4x1::class.java)
            barGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                BarWidget4x1().update(applicationContext, id)
            }

            val controlGlanceIds = glanceManager.getGlanceIds(ControlWidget4x2::class.java)
            controlGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                ControlWidget4x2().update(applicationContext, id)
            }

            val gridGlanceIds = glanceManager.getGlanceIds(GridWidget2x2::class.java)
            gridGlanceIds.forEach { id ->
                updateAppWidgetState(applicationContext, PlayerInfoStateDefinition, id) { playerInfo }
                GridWidget2x2().update(applicationContext, id)
            }
            
            if (glanceIds.isNotEmpty() || barGlanceIds.isNotEmpty() || controlGlanceIds.isNotEmpty() || gridGlanceIds.isNotEmpty()) {
                Timber.tag(TAG)
                    .d("Widgets actualizados: ${playerInfo.songTitle} (Original: ${glanceIds.size}, Bar: ${barGlanceIds.size}, Control: ${controlGlanceIds.size})")
            } else {
                Timber.tag(TAG).w("No se encontraron widgets para actualizar")
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error al actualizar el widget")
        }
    }

    fun isSongFavorite(songId: String?): Boolean {
        return songId != null && favoriteSongIds.contains(songId)
    }

    fun isManualShuffleEnabled(): Boolean {
        return isManualShuffleEnabled
    }

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        val playWhenReady = session.player.playWhenReady
        val playbackState = session.player.playbackState

        // Android 12+ (API 31+): Only request foreground when actively playing.
        // This prevents requesting foreground start when player is idle/ended.
        val shouldStartInForeground = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            startInForegroundRequired && playWhenReady
                    && playbackState != Player.STATE_IDLE
                    && playbackState != Player.STATE_ENDED
        } else {
            startInForegroundRequired
        }

        try {
            super.onUpdateNotification(session, shouldStartInForeground)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "onUpdateNotification suppressed: ${e.message}")
        }
    }

    override fun startForegroundService(serviceIntent: Intent?): ComponentName? {
        // Android 12+ (API 31+): Media3 calls startForegroundService asynchronously
        // (e.g. after bitmap loading or Cast SDK callbacks). By that time the app may
        // already be in the background, causing ForegroundServiceStartNotAllowedException.
        // Do not fall back to startService(): on Android 12+ that turns the original
        // foreground-service exception into BackgroundServiceStartNotAllowedException,
        // which Media3 does not handle and crashes the process. If the service is
        // already foreground, Media3's subsequent startForeground() call will simply
        // update the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                super.startForegroundService(serviceIntent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Timber.tag(TAG).w(
                    e,
                    "startForegroundService not allowed; ignoring redundant self-start request"
                )
                serviceIntent?.component ?: ComponentName(this, javaClass)
            }
        }
        return super.startForegroundService(serviceIntent)
    }

    private fun refreshMediaSessionUi(session: MediaSession) {
        val buttons = buildMediaButtonPreferences(session)
        // setMediaButtonPreferences triggers a notification update internally via
        // MediaControllerListener.onMediaButtonPreferencesChanged → onUpdateNotificationInternal,
        // which correctly determines if the service should run in foreground.
        // Do NOT manually call onUpdateNotification(session, false) here — that bypasses
        // Media3's shouldRunInForeground logic and can remove foreground status, leading to
        // ForegroundServiceStartNotAllowedException when async callbacks fire later.
        session.setMediaButtonPreferences(buttons)
    }

    private fun closeNotificationPlayer() {
        stopPlaybackAndUnload(
            reason = "notification_close_button"
        )
    }

    private fun stopPlaybackAndUnload(
        reason: String,
    ) {
        Timber.tag(TAG).d(
            "Stopping playback and unloading service. reason=%s",
            reason
        )
        followUpMediaSessionUiRefreshJob?.cancel()
        followUpWidgetUpdateJob?.cancel()
        debouncedWidgetUpdateJob?.cancel()
        playbackSnapshotPersistJob?.cancel()

        val sessionToRelease = mediaSession
        val player = sessionToRelease?.player ?: engine.masterPlayer

        clearHeadsetReconnectResume()
        cancelDurationSleepTimerInternal()
        endOfTrackTimerSongId = null

        persistPlaybackSnapshotImmediately()

        player.playWhenReady = false
        player.stop()
        player.clearMediaItems()

        requestWidgetFullUpdate(force = true)
        stopForeground(STOP_FOREGROUND_REMOVE)

        stopSelf()
    }

    private fun persistPlaybackSnapshotImmediately() {
        playbackSnapshotPersistJob?.cancel()
        playbackSnapshotPersistJob = serviceScope.launch(start = CoroutineStart.UNDISPATCHED) {
            persistPlaybackSnapshot()
        }
    }

    private fun refreshMediaSessionUiWithFollowUp(
        session: MediaSession,
        delayMs: Long = 250L
    ) {
        refreshMediaSessionUi(session)
        followUpMediaSessionUiRefreshJob?.cancel()
        followUpMediaSessionUiRefreshJob = serviceScope.launch {
            delay(delayMs)
            if (mediaSession === session) {
                refreshMediaSessionUi(session)
            }
        }
    }

    private fun updateManualShuffleState(
        session: MediaSession,
        enabled: Boolean,
        broadcast: Boolean
    ) {
        val changed = isManualShuffleEnabled != enabled
        isManualShuffleEnabled = enabled
        session.player.shuffleModeEnabled = enabled
        
        if (persistentShuffleEnabled) {
            serviceScope.launch {
                userPreferencesRepository.setShuffleOn(enabled)
            }
        }

        if (broadcast && changed) {
            val args = Bundle().apply {
                putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
            }
            session.broadcastCustomCommand(
                SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle.EMPTY),
                args
            )
        }
        refreshMediaSessionUi(session)
        requestWidgetFullUpdate(force = true)
    }

    private fun setCurrentSongFavoriteState(
        session: MediaSession,
        targetFavoriteState: Boolean
    ): ListenableFuture<SessionResult> {
        val songId = session.player.currentMediaItem?.mediaId
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))

        val isCurrentlyFavorite = favoriteSongIds.contains(songId)
        if (isCurrentlyFavorite == targetFavoriteState) {
            refreshMediaSessionUi(session)
            requestWidgetFullUpdate(force = true)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        favoriteSongIds = if (targetFavoriteState) {
            favoriteSongIds + songId
        } else {
            favoriteSongIds - songId
        }

        refreshMediaSessionUi(session)
        requestWidgetFullUpdate(force = true)

        serviceScope.launch {
            Timber.tag("MusicService")
                .d("Applying favorite=$targetFavoriteState for songId: $songId")
            musicRepository.setFavoriteStatus(songId, targetFavoriteState)
            refreshMediaSessionUi(session)
            requestWidgetFullUpdate(force = true)
        }

        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    private data class ContextQueueResolution(
        val mediaItems: MutableList<MediaItem>,
        val startIndex: Int
    )

    private fun controllerKey(controller: MediaSession.ControllerInfo): String {
        return "${controller.packageName}:${controller.uid}"
    }

    private fun rememberLastBrowsedParent(controller: MediaSession.ControllerInfo, parentId: String) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)] = parentId
        }
    }

    private fun getLastBrowsedParent(controller: MediaSession.ControllerInfo): String? {
        return synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent[controllerKey(controller)]
        }
    }

    private fun clearLastBrowsedParent(controller: MediaSession.ControllerInfo) {
        synchronized(controllerLastBrowsedParent) {
            controllerLastBrowsedParent.remove(controllerKey(controller))
        }
    }

    private suspend fun resolveContextQueueForRequestedItem(
        requestedItem: MediaItem,
        controller: MediaSession.ControllerInfo
    ): ContextQueueResolution? {
        var contextType = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_TYPE_EXTRA)
        var contextId = requestedItem.mediaMetadata.extras
            ?.getString(AutoMediaBrowseTree.CONTEXT_ID_EXTRA)

        if (contextType.isNullOrBlank()) {
            val parentId = requestedItem.mediaMetadata.extras
                ?.getString(AutoMediaBrowseTree.CONTEXT_PARENT_ID_EXTRA)
                ?: getLastBrowsedParent(controller)
            val parentContext = parentId?.let { resolveAutoContextFromParentId(it) }
            contextType = parentContext?.first
            contextId = parentContext?.second
        }

        if (contextType.isNullOrBlank()) {
            return null
        }

        val queueSongs = autoMediaBrowseTree.getSongsForContext(contextType, contextId)
        if (queueSongs.isEmpty()) {
            return null
        }

        val startIndex = queueSongs.indexOfFirst { it.id == requestedItem.mediaId }
        if (startIndex < 0) {
            return null
        }

        val queueMediaItems = queueSongs.map { song ->
            MediaItemBuilder.buildForExternalController(this, song)
        }.toMutableList()

        return ContextQueueResolution(
            mediaItems = queueMediaItems,
            startIndex = startIndex
        )
    }

    private suspend fun resolveMediaItemsByIds(
        requestedItems: List<MediaItem>
    ): TrustedMediaItemsResolution {
        val songIds = requestedItems.map { it.mediaId }
        val songs = musicRepository.getSongsByIds(songIds).first()
        val songMap = songs.associateBy { it.id }

        return resolveMediaItemsWithTrustedArtworkGrants(requestedItems) { mediaId ->
            songMap[mediaId]?.let { song ->
                MediaItemBuilder.buildForExternalController(this, song)
            }
        }
    }

    private fun grantArtworkUriPermissions(
        targetPackage: String,
        mediaItems: List<MediaItem>
    ) {
        if (targetPackage.isBlank()) return

        val providerAuthority = "$packageName.provider"
        mediaItems.forEach { mediaItem ->
            val artworkUri = resolveArtworkUri(mediaItem.mediaMetadata) ?: return@forEach
            if (artworkUri.scheme?.lowercase() != "content" || artworkUri.authority != providerAuthority) {
                return@forEach
            }

            runCatching {
                grantUriPermission(targetPackage, artworkUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { error ->
                Timber.tag(TAG).w(
                    error,
                    "Failed to grant artwork URI permission to package=%s uri=%s",
                    targetPackage,
                    artworkUri
                )
            }
        }
    }

    private fun resolveAutoContextFromParentId(parentId: String): Pair<String, String?>? {
        return when {
            parentId == AutoMediaBrowseTree.RECENT_ID -> AUTO_CONTEXT_RECENT to null
            parentId == AutoMediaBrowseTree.FAVORITES_ID -> AUTO_CONTEXT_FAVORITES to null
            parentId == AutoMediaBrowseTree.SONGS_ID -> AUTO_CONTEXT_ALL_SONGS to null
            parentId.startsWith(AutoMediaBrowseTree.ALBUM_PREFIX) -> {
                AUTO_CONTEXT_ALBUM to parentId.removePrefix(AutoMediaBrowseTree.ALBUM_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.ARTIST_PREFIX) -> {
                AUTO_CONTEXT_ARTIST to parentId.removePrefix(AutoMediaBrowseTree.ARTIST_PREFIX)
            }
            parentId.startsWith(AutoMediaBrowseTree.PLAYLIST_PREFIX) -> {
                AUTO_CONTEXT_PLAYLIST to parentId.removePrefix(AutoMediaBrowseTree.PLAYLIST_PREFIX)
            }
            else -> null
        }
    }

    private fun buildMediaButtonPreferences(session: MediaSession): List<CommandButton> {
        val player = session.player
        val songId = player.currentMediaItem?.mediaId
        val isFavorite = isSongFavorite(songId)
        val likeButton = CommandButton.Builder(
            if (isFavorite) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED
        )
            .setDisplayName("Like")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_LIKE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val shuffleOn = isManualShuffleEnabled
        val shuffleCommandAction = if (shuffleOn) {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_OFF
        } else {
            MusicNotificationProvider.CUSTOM_COMMAND_SHUFFLE_ON
        }
        val shuffleButton = CommandButton.Builder(
            if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
        )
            .setDisplayName("Shuffle")
            .setSessionCommand(SessionCommand(shuffleCommandAction, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val repeatButton = CommandButton.Builder(
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                else -> CommandButton.ICON_REPEAT_OFF
            }
        )
            .setDisplayName("Repeat")
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CYCLE_REPEAT_MODE, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        val closeButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(R.drawable.rounded_close_24)
            .setDisplayName(getString(R.string.close_notification_player))
            .setSessionCommand(SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_CLOSE_PLAYER, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build()

        // Let Media3 provide the primary previous/play-next transport buttons from player
        // commands instead of advertising custom back/forward slots here. When custom
        // SLOT_BACK/SLOT_FORWARD buttons are present, Media3 strips the legacy
        // ACTION_SKIP_TO_PREVIOUS/NEXT flags from PlaybackStateCompat, which causes some
        // OEM compact system players (including ColorOS Control Center) to gray out skip.
        return listOf(likeButton, closeButton, shuffleButton, repeatButton)
    }

    // ------------------------
    // Counted Play Controls
    // ------------------------
    fun startCountedPlay(count: Int) {
        val player = engine.masterPlayer
        val currentItem = player.currentMediaItem ?: return

        stopCountedPlay()  // reset previous

        countedPlayTarget = count
        countedPlayCount = 1
        countedOriginalId = currentItem.mediaId
        countedPlayActive = true

        // Force repeat-one
        player.repeatMode = Player.REPEAT_MODE_ONE

        val listener = object : Player.Listener {

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!countedPlayActive) return

                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    countedPlayCount++

                    if (countedPlayCount > countedPlayTarget) {
                        player.pause()
                        stopCountedPlay()
                        return
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!countedPlayActive) return

                // If user manually changes the song -> cancel
                if (mediaItem?.mediaId != countedOriginalId) {
                    stopCountedPlay()
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                // User explicitly changed repeat mode while counted play is active:
                // cancel counted play and accept the new mode instead of fighting back.
                if (countedPlayActive && repeatMode != Player.REPEAT_MODE_ONE) {
                    stopCountedPlay(restoreRepeatMode = false)
                }
            }
        }

        countedPlayListener = listener
        player.addListener(listener)
    }

    fun stopCountedPlay(restoreRepeatMode: Boolean = true) {
        if (!countedPlayActive) return

        countedPlayActive = false
        countedPlayTarget = 0
        countedPlayCount = 0
        countedOriginalId = null

        countedPlayListener?.let {
            engine.masterPlayer.removeListener(it)
        }
        countedPlayListener = null

        // Restore normal repeat mode (OFF) only when not triggered by a user repeat-mode change
        if (restoreRepeatMode) {
            engine.masterPlayer.repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    /**
     * Bridges a suspend block into a [ListenableFuture] for Media3 callback methods.
     */
    private fun <T> CoroutineScope.future(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        launch(Dispatchers.IO) {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

}
