package com.theveloper.pixelplay.presentation.components

import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.map

internal data class SaveQueueOverlayData(
    val songs: List<Song>,
    val defaultName: String,
    val onConfirm: (String, Set<String>) -> Unit
)

@Composable
internal fun UnifiedPlayerQueueLayer(
    shouldRenderLayer: Boolean,
    albumColorScheme: ColorScheme,
    queueScrimAlpha: Float,
    showQueueSheet: Boolean,
    queueHiddenOffsetPx: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueSheetHeightPx: Float,
    onQueueSheetHeightPxChange: (Float) -> Unit,
    configurationResetKey: Any,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    activeTimerValueDisplay: State<String?>,
    playCount: State<Float>,
    isEndOfTrackTimerActive: State<Boolean>,
    onDismissQueue: () -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    onSetPredefinedTimer: (Int) -> Unit,
    onSetEndOfTrackTimer: (Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (Int) -> Unit,
    onRequestSaveAsPlaylist: (List<Song>, String, (String, Set<String>) -> Unit) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit
) {
    if (!shouldRenderLayer) return

    LaunchedEffect(configurationResetKey) {
        onQueueSheetHeightPxChange(0f)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (queueScrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .zIndex(0f)
                    .graphicsLayer { alpha = queueScrimAlpha }
                    .background(MaterialTheme.colorScheme.scrim)
            )
        }

        val shouldRenderQueueSheet = remember(showQueueSheet, queueSheetHeightPx) {
            showQueueSheet || queueSheetHeightPx == 0f
        }

        if (shouldRenderQueueSheet) {
            MaterialTheme(
                colorScheme = albumColorScheme,
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes
            ) {
                QueueBottomSheet(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = queueSheetOffset.value
                            alpha = if (showQueueSheet) 1f else 0f
                        }
                        .onGloballyPositioned { coordinates ->
                            val measuredHeight = coordinates.size.height.toFloat()
                            if (queueSheetHeightPx != measuredHeight) {
                                onQueueSheetHeightPxChange(measuredHeight)
                            }
                        },
                    queue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    currentSongId = infrequentPlayerState.currentSong?.id,
                    onDismiss = onDismissQueue,
                    onSongInfoClick = onSongInfoClick,
                    onPlaySong = onPlaySong,
                    onRemoveSong = onRemoveSong,
                    onReorder = onReorder,
                    repeatMode = infrequentPlayerState.repeatMode,
                    isShuffleOn = infrequentPlayerState.isShuffleEnabled,
                    onToggleRepeat = onToggleRepeat,
                    onToggleShuffle = onToggleShuffle,
                    onClearQueue = onClearQueue,
                    activeTimerValueDisplay = activeTimerValueDisplay,
                    playCount = playCount,
                    isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                    onSetPredefinedTimer = onSetPredefinedTimer,
                    onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                    onOpenCustomTimePicker = onOpenCustomTimePicker,
                    onCancelTimer = onCancelTimer,
                    onCancelCountedPlay = onCancelCountedPlay,
                    onPlayCounter = onPlayCounter,
                    onRequestSaveAsPlaylist = onRequestSaveAsPlaylist,
                    onQueueDragStart = onQueueDragStart,
                    onQueueDrag = onQueueDrag,
                    onQueueRelease = onQueueRelease
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerSongInfoLayer(
    selectedSongForInfo: Song?,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    currentPlaybackQueueProvider: () -> ImmutableList<Song>,
    currentQueueSourceNameProvider: () -> String,
    onDismissSongInfo: () -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onNavigateToGenre: (Song) -> Unit
) {
    selectedSongForInfo?.let { staticSong ->
        val context = LocalContext.current
        var showPlaylistBottomSheet by remember(staticSong.id) { mutableStateOf(false) }
        val playlistViewModel: PlaylistViewModel = hiltViewModel()
        val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()
        val liveSongState by remember(playerViewModel, staticSong.id) {
            playerViewModel.observeSong(staticSong.id).map { it ?: staticSong }
        }.collectAsStateWithLifecycle(initialValue = staticSong)

        val liveSong = liveSongState

        MaterialTheme(
            colorScheme = albumColorScheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            SongInfoBottomSheet(
                song = liveSong,
                isFavorite = liveSong.isFavorite,
                onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(liveSong) },
                onDismiss = {
                    showPlaylistBottomSheet = false
                    onDismissSongInfo()
                },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(
                        song = liveSong,
                        contextSongs = currentPlaybackQueueProvider(),
                        queueName = currentQueueSourceNameProvider()
                    )
                    onDismissSongInfo()
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(liveSong)
                    onDismissSongInfo()
                    Toast.makeText(context, "Added to queue", Toast.LENGTH_SHORT).show()
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(liveSong)
                    onDismissSongInfo()
                    Toast.makeText(context, "Playing next", Toast.LENGTH_SHORT).show()
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = { activity, songToDelete, onResult ->
                    playerViewModel.deleteFromDevice(activity, songToDelete, onResult)
                    onDismissSongInfo()
                },
                onNavigateToAlbum = { onNavigateToAlbum(liveSong) },
                onNavigateToArtist = { onNavigateToArtist(liveSong) },
                onNavigateToGenre = { onNavigateToGenre(liveSong) },
                onEditSong = { title, artist, album, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        liveSong,
                        title,
                        artist,
                        album,
                        genre,
                        lyrics,
                        trackNumber,
                        discNumber,
                        replayGainTrackGainDb,
                        replayGainAlbumGainDb,
                        coverArtUpdate
                    )
                    onDismissSongInfo()
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(liveSong, fields)
                },
                removeFromListTrigger = {
                    playerViewModel.removeSongFromQueue(liveSong.id)
                    onDismissSongInfo()
                }
            )

            if (showPlaylistBottomSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(liveSong),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = 0.dp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerQueueAndSongInfoHost(
    shouldRenderHost: Boolean,
    isQueueTelemetryActive: Boolean,
    albumColorScheme: ColorScheme,
    queueScrimAlpha: Float,
    showQueueSheet: Boolean,
    queueHiddenOffsetPx: Float,
    queueSheetOffset: Animatable<Float, AnimationVector1D>,
    queueSheetHeightPx: Float,
    onQueueSheetHeightPxChange: (Float) -> Unit,
    configurationResetKey: Any,
    currentPlaybackQueue: ImmutableList<Song>,
    currentQueueSourceName: String,
    infrequentPlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    selectedSongForInfo: Song?,
    onSelectedSongForInfoChange: (Song?) -> Unit,
    onAnimateQueueSheet: (Boolean) -> Unit,
    onBeginQueueDrag: () -> Unit,
    onDragQueueBy: (Float) -> Unit,
    onEndQueueDrag: (Float, Float) -> Unit,
    onLaunchSaveQueueOverlay: (List<Song>, String, (String, Set<String>) -> Unit) -> Unit,
    onNavigateToAlbum: (Song) -> Unit,
    onNavigateToArtist: (Song) -> Unit,
    onNavigateToGenre: (Song) -> Unit
) {
    if (!shouldRenderHost) return

    val latestPlaybackQueue = rememberUpdatedState(currentPlaybackQueue)
    val latestQueueSourceName = rememberUpdatedState(currentQueueSourceName)
    val inactiveTimerValueDisplayState = rememberUpdatedState<String?>(null)
    val inactivePlayCountState = rememberUpdatedState(0f)
    val inactiveEndOfTrackTimerActiveState = rememberUpdatedState(false)
    val activeTimerValueDisplay: State<String?> =
        if (isQueueTelemetryActive) {
            playerViewModel.activeTimerValueDisplay.collectAsStateWithLifecycle()
        } else {
            inactiveTimerValueDisplayState
        }
    val playCount: State<Float> =
        if (isQueueTelemetryActive) {
            playerViewModel.playCount.collectAsStateWithLifecycle()
        } else {
            inactivePlayCountState
        }
    val isEndOfTrackTimerActive: State<Boolean> =
        if (isQueueTelemetryActive) {
            playerViewModel.isEndOfTrackTimerActive.collectAsStateWithLifecycle()
        } else {
            inactiveEndOfTrackTimerActiveState
        }

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val onDismissQueueRequest = remember(onAnimateQueueSheet) { { onAnimateQueueSheet(false) } }
            val onQueueSongInfoClick = remember(onSelectedSongForInfoChange) {
                { song: Song -> onSelectedSongForInfoChange(song) }
            }
            val onPlayQueueSong = remember(playerViewModel) {
                { song: Song ->
                    playerViewModel.showAndPlaySong(
                        song = song,
                        contextSongs = latestPlaybackQueue.value,
                        queueName = latestQueueSourceName.value
                    )
                }
            }
            val onRemoveQueueSong = remember(playerViewModel) {
                { id: String -> playerViewModel.removeSongFromQueue(id) }
            }
            val onReorderQueue = remember(playerViewModel) {
                { from: Int, to: Int -> playerViewModel.reorderQueueItem(from, to) }
            }
            val onToggleRepeat = remember(playerViewModel) { { playerViewModel.cycleRepeatMode() } }
            val onToggleShuffle = remember(playerViewModel) { { playerViewModel.toggleShuffle() } }
            val onClearQueue = remember(playerViewModel) { { playerViewModel.clearQueueExceptCurrent() } }
            val onSetPredefinedTimer = remember(playerViewModel) {
                { minutes: Int -> playerViewModel.setSleepTimer(minutes) }
            }
            val onSetEndOfTrackTimer = remember(playerViewModel) {
                { enable: Boolean -> playerViewModel.setEndOfTrackTimer(enable) }
            }
            val onOpenCustomTimePicker: () -> Unit = remember {
                { Log.d("TimerOptions", "OpenCustomTimePicker clicked") }
            }
            val onCancelTimer = remember(playerViewModel) { { playerViewModel.cancelSleepTimer() } }
            val onCancelCountedPlay = remember(playerViewModel) { playerViewModel::cancelCountedPlay }
            val onPlayCounter = remember(playerViewModel) { playerViewModel::playCounted }
            val onRequestSavePlaylist = remember(onLaunchSaveQueueOverlay) {
                { songs: List<Song>, defName: String, onConf: (String, Set<String>) -> Unit ->
                    onLaunchSaveQueueOverlay(songs, defName, onConf)
                }
            }
            val onQueueStartDrag = remember(onBeginQueueDrag) { { onBeginQueueDrag() } }
            val onQueueDrag = remember(onDragQueueBy) { { drag: Float -> onDragQueueBy(drag) } }
            val onQueueRelease = remember(onEndQueueDrag) {
                { drag: Float, vel: Float -> onEndQueueDrag(drag, vel) }
            }
            val playbackQueueProvider = remember {
                { latestPlaybackQueue.value }
            }
            val queueSourceNameProvider = remember {
                { latestQueueSourceName.value }
            }

            UnifiedPlayerQueueLayer(
                shouldRenderLayer = true,
                albumColorScheme = albumColorScheme,
                queueScrimAlpha = queueScrimAlpha,
                showQueueSheet = showQueueSheet,
                queueHiddenOffsetPx = queueHiddenOffsetPx,
                queueSheetOffset = queueSheetOffset,
                queueSheetHeightPx = queueSheetHeightPx,
                onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                configurationResetKey = configurationResetKey,
                currentPlaybackQueue = currentPlaybackQueue,
                currentQueueSourceName = currentQueueSourceName,
                infrequentPlayerState = infrequentPlayerState,
                activeTimerValueDisplay = activeTimerValueDisplay,
                playCount = playCount,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive,
                onDismissQueue = onDismissQueueRequest,
                onSongInfoClick = onQueueSongInfoClick,
                onPlaySong = onPlayQueueSong,
                onRemoveSong = onRemoveQueueSong,
                onReorder = onReorderQueue,
                onToggleRepeat = onToggleRepeat,
                onToggleShuffle = onToggleShuffle,
                onClearQueue = onClearQueue,
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelTimer = onCancelTimer,
                onCancelCountedPlay = onCancelCountedPlay,
                onPlayCounter = onPlayCounter,
                onRequestSaveAsPlaylist = onRequestSavePlaylist,
                onQueueDragStart = onQueueStartDrag,
                onQueueDrag = onQueueDrag,
                onQueueRelease = onQueueRelease
            )

            UnifiedPlayerSongInfoLayer(
                selectedSongForInfo = selectedSongForInfo,
                albumColorScheme = albumColorScheme,
                playerViewModel = playerViewModel,
                currentPlaybackQueueProvider = playbackQueueProvider,
                currentQueueSourceNameProvider = queueSourceNameProvider,
                onDismissSongInfo = { onSelectedSongForInfoChange(null) },
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onNavigateToGenre = onNavigateToGenre
            )
        }
    }
}

@Composable
internal fun UnifiedPlayerSaveQueueLayer(
    pendingOverlay: SaveQueueOverlayData?,
    onDismissOverlay: () -> Unit
) {
    pendingOverlay?.let { overlay ->
        SaveQueueAsPlaylistSheet(
            songs = overlay.songs,
            defaultName = overlay.defaultName,
            onDismiss = onDismissOverlay,
            onConfirm = { name, selectedIds ->
                overlay.onConfirm(name, selectedIds)
                onDismissOverlay()
            }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
internal fun UnifiedPlayerCastLayer(
    showCastSheet: Boolean,
    internalIsKeyboardVisible: Boolean,
    albumColorScheme: ColorScheme,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit
) {
    if (!showCastSheet || internalIsKeyboardVisible) return

    CompositionLocalProvider(
        LocalMaterialTheme provides albumColorScheme
    ) {
        MaterialTheme(
            colorScheme = LocalMaterialTheme.current,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes
        ) {
            CastBottomSheet(
                playerViewModel = playerViewModel,
                onDismiss = onDismiss,
                onExpansionChanged = onExpansionChanged
            )
        }
    }
}
