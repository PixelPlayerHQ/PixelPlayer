package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Checkbox
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RemoveDone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.view.HapticFeedbackConstantsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingText
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.components.player.AnimatedPlaybackControls
import com.theveloper.pixelplay.presentation.viewmodel.PlayerUiState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.layout.onGloballyPositioned
import com.theveloper.pixelplay.presentation.components.scoped.QueueItemDismissGestureHandler
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import coil.size.Size
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.RandomAccess

private data class QueueUndoBarProjection(
    val isVisible: Boolean = false,
    val removedSongTitle: String = ""
)

private fun PlayerUiState.toQueueUndoBarProjection(): QueueUndoBarProjection =
    QueueUndoBarProjection(
        isVisible = showQueueItemUndoBar,
        removedSongTitle = lastRemovedQueueSong?.title.orEmpty()
    )

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun QueueBottomSheet(
    viewModel: PlayerViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    queue: List<Song>,
    currentQueueSourceName: String,
    currentSongId: String?,
    repeatMode: Int,
    isShuffleOn: Boolean,
    onDismiss: () -> Unit,
    onSongInfoClick: (Song) -> Unit,
    onPlaySong: (Song) -> Unit,
    onRemoveSong: (String) -> Unit,
    onReorder: (from: Int, to: Int) -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClearQueue: () -> Unit,
    activeTimerValueDisplay: androidx.compose.runtime.State<String?>,
    playCount: androidx.compose.runtime.State<Float>,
    isEndOfTrackTimerActive: androidx.compose.runtime.State<Boolean>,
    onSetPredefinedTimer: (minutes: Int) -> Unit,
    onSetEndOfTrackTimer: (enable: Boolean) -> Unit,
    onOpenCustomTimePicker: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelCountedPlay: () -> Unit,
    onPlayCounter: (count: Int) -> Unit,
    onRequestSaveAsPlaylist: (
        songs: List<Song>,
        defaultName: String,
        onConfirm: (String, Set<String>) -> Unit
    ) -> Unit,
    onQueueDragStart: () -> Unit,
    onQueueDrag: (Float) -> Unit,
    onQueueRelease: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 10.dp,
    shape: RoundedCornerShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
    val colors = MaterialTheme.colorScheme
    var showTimerOptions by rememberSaveable { mutableStateOf(false) }
    var showClearQueueDialog by remember { mutableStateOf(false) }
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }

    val infrequentPlayerState by viewModel.stablePlayerState.collectAsStateWithLifecycle()

    val albumColorSchemePair by viewModel.currentAlbumArtColorSchemePair.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val albumColorScheme = remember(albumColorSchemePair, isDark) {
        albumColorSchemePair?.let { pair -> if (isDark) pair.dark else pair.light }
    }

    val isPlaying = infrequentPlayerState.isPlaying

    val currentSongIndex = remember(queue, currentSongId) {
        queue.indexOfFirst { it.id == currentSongId }
    }

    // Read show queue history preference
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val showQueueHistory = settingsState.showQueueHistory

    // Offset to convert display indices to queue indices when history is hidden.
    val queueIndexOffset = if (showQueueHistory || currentSongIndex < 0) 0 else currentSongIndex

    // Show full queue including history (Apple Music style) OR only from current song.
    // Use subList when possible to avoid copying large queues.
    val displaySongs = remember(queue, queueIndexOffset) {
        if (queueIndexOffset == 0) {
            queue
        } else if (queue is RandomAccess) {
            queue.subList(queueIndexOffset, queue.size)
        } else {
            queue.drop(queueIndexOffset)
        }
    }

    // Calculate the display index of the current song (depends on whether we show history or not).
    val currentSongDisplayIndex = remember(currentSongIndex, queueIndexOffset) {
        if (currentSongIndex < 0) -1 else currentSongIndex - queueIndexOffset
    }

    val listState = rememberLazyListState()
    val displaySongCount = displaySongs.size

    // Local order used only while previewing a drag reorder.
    var reorderPreviewOrder by remember { mutableStateOf<List<Int>?>(null) }
    var reorderPreviewKeys by remember { mutableStateOf<List<Long>?>(null) }
    var reorderPreviewBaseQueue by remember { mutableStateOf<List<Song>?>(null) }
    var pendingReorderExpectedIds by remember { mutableStateOf<List<String>?>(null) }
    var pendingReorderGraceUpdates by remember { mutableIntStateOf(0) }

    // Stable keys for queue rows to prevent state recycling glitches on remove/reorder.
    var committedDisplaySongIds by remember { mutableStateOf(displaySongs.map { it.id }) }
    var committedDisplayKeys by remember { mutableStateOf(List(displaySongCount) { it.toLong() }) }
    var nextStableQueueItemKey by remember { mutableLongStateOf(displaySongCount.toLong()) }

    // Track queue order by content (not list identity) to avoid clearing preview
    // when upstream emits equivalent list instances during drag.
    var reorderPreviewQueueSignature by remember { mutableStateOf<Int?>(null) }
    val displaySongsSignature = remember(displaySongs, queueIndexOffset) {
        (queueIndexOffset * 31) + System.identityHashCode(displaySongs)
    }
    val defaultDisplayOrder = remember(displaySongCount, queueIndexOffset) {
        List(displaySongCount) { queueIndexOffset + it }
    }
    val defaultDisplayKeys = remember(displaySongCount, queueIndexOffset) {
        List(displaySongCount) { (queueIndexOffset + it).toLong() }
    }
    val activeOrder = reorderPreviewOrder ?: defaultDisplayOrder
    val activeKeys = reorderPreviewKeys
        ?: committedDisplayKeys.takeIf { it.size == displaySongCount }
        ?: defaultDisplayKeys
    val activeSongSource = reorderPreviewBaseQueue ?: queue
    val activeKeyToLocalIndex = remember(activeKeys) {
        HashMap<Long, Int>(activeKeys.size).apply {
            activeKeys.forEachIndexed { index, stableKey ->
                put(stableKey, index)
            }
        }
    }

    fun remapCommittedKeysForDisplay(newSongs: List<Song>) {
        // Fast path: common queue-skip case where display list is just a suffix of previous display list.
        if (committedDisplaySongIds.isNotEmpty() && newSongs.isNotEmpty()) {
            val firstNewId = newSongs.first().id
            val startIndex = committedDisplaySongIds.indexOf(firstNewId)
            if (startIndex >= 0 && startIndex + newSongs.size <= committedDisplaySongIds.size) {
                var suffixMatches = true
                for (i in newSongs.indices) {
                    if (committedDisplaySongIds[startIndex + i] != newSongs[i].id) {
                        suffixMatches = false
                        break
                    }
                }
                if (suffixMatches) {
                    committedDisplaySongIds = committedDisplaySongIds.subList(startIndex, startIndex + newSongs.size).toList()
                    committedDisplayKeys = committedDisplayKeys.subList(startIndex, startIndex + newSongs.size).toList()
                    return
                }
            }
        }

        val reusableKeysBySongId = mutableMapOf<String, ArrayDeque<Long>>()
        committedDisplaySongIds.forEachIndexed { index, songId ->
            val key = committedDisplayKeys.getOrNull(index) ?: return@forEachIndexed
            reusableKeysBySongId.getOrPut(songId) { ArrayDeque() }.addLast(key)
        }

        var nextKey = nextStableQueueItemKey
        val newKeys = ArrayList<Long>(newSongs.size)
        newSongs.forEach { song ->
            val bucket = reusableKeysBySongId[song.id]
            val reusedKey = if (bucket != null && bucket.isNotEmpty()) bucket.removeFirst() else null
            if (reusedKey != null) {
                newKeys.add(reusedKey)
            } else {
                newKeys.add(nextKey)
                nextKey++
            }
        }

        committedDisplaySongIds = newSongs.map { it.id }
        committedDisplayKeys = newKeys
        nextStableQueueItemKey = nextKey
    }

    // Reset local reorder preview only when the queue truly changes to something new.
    LaunchedEffect(displaySongsSignature, queueIndexOffset) {
        val expectedIds = pendingReorderExpectedIds

        if (expectedIds != null) {
            val currentDisplayIds = displaySongs.map { it.id }
            if (currentDisplayIds == expectedIds) {
                reorderPreviewKeys
                    ?.takeIf { it.size == displaySongs.size }
                    ?.let { previewKeys ->
                        committedDisplaySongIds = currentDisplayIds
                        committedDisplayKeys = previewKeys
                    }
                reorderPreviewOrder = null
                reorderPreviewKeys = null
                reorderPreviewBaseQueue = null
                pendingReorderExpectedIds = null
                pendingReorderGraceUpdates = 0
                remapCommittedKeysForDisplay(displaySongs)
                reorderPreviewQueueSignature = displaySongsSignature
                return@LaunchedEffect
            }

            if (reorderPreviewOrder != null && pendingReorderGraceUpdates > 0) {
                pendingReorderGraceUpdates -= 1
                reorderPreviewQueueSignature = displaySongsSignature
                return@LaunchedEffect
            }

            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
        }

        if (reorderPreviewQueueSignature != null && reorderPreviewQueueSignature != displaySongsSignature) {
            // Queue data changed from external source - safe to clear preview
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
        }
        remapCommittedKeysForDisplay(displaySongs)
        reorderPreviewQueueSignature = displaySongsSignature
    }

    // Jump directly to current song when it changes. Avoid a long animated scroll on large queues.
    LaunchedEffect(currentSongDisplayIndex, displaySongCount) {
        if (currentSongDisplayIndex >= 0 && currentSongDisplayIndex < displaySongCount) {
            listState.scrollToItem(currentSongDisplayIndex)
        }
    }

    val canDragSheetFromList by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }
    val updatedCanDragSheet by rememberUpdatedState(canDragSheetFromList)
    var draggingSheetFromList by remember { mutableStateOf(false) }
    var listDragAccumulated by remember { mutableStateOf(0f) }
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    var lastMovedFrom by remember { mutableStateOf<Int?>(null) }
    var lastMovedTo by remember { mutableStateOf<Int?>(null) }
    var reorderHandleInUse by remember { mutableStateOf(false) }
    val updatedReorderHandleInUse by rememberUpdatedState(reorderHandleInUse)

    fun mapKeyToLocalIndex(key: Any?, keyToLocalIndex: Map<Long, Int>): Int? {
        val stableKey = key as? Long ?: return null
        return keyToLocalIndex[stableKey]
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (reorderPreviewOrder == null) {
                reorderPreviewBaseQueue = queue
            }
            val currentOrder = activeOrder
            val currentKeys = activeKeys

            val fromLocalIndex = mapKeyToLocalIndex(from.key, activeKeyToLocalIndex) ?: return@rememberReorderableLazyListState
            val toLocalIndex = mapKeyToLocalIndex(to.key, activeKeyToLocalIndex) ?: return@rememberReorderableLazyListState
            if (fromLocalIndex == toLocalIndex) return@rememberReorderableLazyListState

            reorderPreviewOrder = currentOrder.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            reorderPreviewKeys = currentKeys.toMutableList().apply {
                add(toLocalIndex, removeAt(fromLocalIndex))
            }
            if (lastMovedFrom == null) {
                lastMovedFrom = fromLocalIndex
            }
            lastMovedTo = toLocalIndex
        },
    )
    val isReordering by remember {
        derivedStateOf { reorderableState.isAnyItemDragging }
    }
    val updatedIsReordering by rememberUpdatedState(isReordering)

    val updatedOnQueueDragStart by rememberUpdatedState(onQueueDragStart)
    val updatedOnQueueDrag by rememberUpdatedState(onQueueDrag)
    val updatedOnQueueRelease by rememberUpdatedState(onQueueRelease)

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            val fromIndex = lastMovedFrom
            val toIndex = lastMovedTo

            lastMovedFrom = null
            lastMovedTo = null

            if (fromIndex != null && toIndex != null) {
                // Convert display indices to queue indices by adding the offset
                val fromQueueIndex = fromIndex + queueIndexOffset
                val toQueueIndex = toIndex + queueIndexOffset

                val fromWithinQueue = fromQueueIndex in queue.indices
                val toWithinQueue = toQueueIndex in queue.indices

                if (fromWithinQueue && toWithinQueue && fromQueueIndex != toQueueIndex) {
                    val previewBase = reorderPreviewBaseQueue ?: queue
                    val expectedIds = reorderPreviewOrder
                        ?.mapNotNull { previewBase.getOrNull(it)?.id }
                        ?.takeIf { it.size == displaySongCount }
                    pendingReorderExpectedIds = expectedIds
                    pendingReorderGraceUpdates = if (expectedIds != null) 6 else 0
                    // Keep reorderPreviewOrder alive so items don't snap back
                    // while we wait for the new queue data to propagate.
                    onReorder(fromQueueIndex, toQueueIndex)
                    return@LaunchedEffect
                }
            }

            // Only clear preview if no valid reorder was dispatched
            reorderPreviewOrder = null
            reorderPreviewKeys = null
            reorderPreviewBaseQueue = null
            pendingReorderExpectedIds = null
            pendingReorderGraceUpdates = 0
        }
    }

    val useLightweightQueueListShape by remember {
        derivedStateOf {
            listState.isScrollInProgress ||
                draggingSheetFromList ||
                isReordering ||
                reorderHandleInUse
        }
    }
    val queueListShape = remember(useLightweightQueueListShape) {
        if (useLightweightQueueListShape) {
            RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
        } else {
            AbsoluteSmoothCornerShape(
                cornerRadiusTR = 26.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = 26.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 60
            )
        }
    }

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    fun finalizeListDrag(velocity: Float = 0f) {
        if (draggingSheetFromList) {
            updatedOnQueueRelease(listDragAccumulated, velocity)
            draggingSheetFromList = false
            listDragAccumulated = 0f
        }
    }

    val listDragConnection = remember(updatedCanDragSheet) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && available.y < 0f) {
                    finalizeListDrag()
                    return Offset.Zero
                }

                if (draggingSheetFromList) {
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return available
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList && available.y < 0f) {
                    finalizeListDrag(available.y)
                    return Velocity.Zero
                }

                if (available.y > 0 && updatedCanDragSheet) {
                    if (!draggingSheetFromList) {
                        draggingSheetFromList = true
                        listDragAccumulated = 0f
                        updatedOnQueueDragStart()
                    }
                    updatedOnQueueRelease(listDragAccumulated, available.y)
                    draggingSheetFromList = false
                    listDragAccumulated = 0f
                    return available
                }
                return Velocity.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (updatedIsReordering || updatedReorderHandleInUse) return Offset.Zero

                if (draggingSheetFromList && source == NestedScrollSource.UserInput && available.y != 0f) {
                    listDragAccumulated += available.y
                    updatedOnQueueDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (updatedIsReordering || updatedReorderHandleInUse) return Velocity.Zero

                if (draggingSheetFromList) return available.also { finalizeListDrag(available.y) }
                return Velocity.Zero
            }
        }
    }

    val directSheetDragModifier =
        if (updatedIsReordering || updatedReorderHandleInUse) {
            Modifier
        } else {
            Modifier.pointerInput(updatedOnQueueDragStart, updatedOnQueueDrag, updatedOnQueueRelease) {
                var dragTotal = 0f
                val dragVelocityTracker = VelocityTracker()
                detectVerticalDragGestures(
                    onDragStart = {
                        dragTotal = 0f
                        dragVelocityTracker.resetTracking()
                        updatedOnQueueDragStart()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragTotal += dragAmount
                        dragVelocityTracker.addPosition(change.uptimeMillis, change.position)
                        updatedOnQueueDrag(dragAmount)
                    },
                    onDragEnd = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    },
                    onDragCancel = {
                        val velocity = dragVelocityTracker.calculateVelocity().y
                        updatedOnQueueRelease(dragTotal, velocity)
                    }
                )
            }
        }

    Surface(
        modifier = modifier,
        shape = shape,
        tonalElevation = tonalElevation,
        color = colors.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column {
                val headerTopPadding = WindowInsets.statusBars
                    .asPaddingValues()
                    .calculateTopPadding() + 10.dp

                QueueHeaderSection(
                    isPlaying = isPlaying,
                    queueSourceName = currentQueueSourceName,
                    queueCount = displaySongCount,
                    topPadding = headerTopPadding,
                    onPrevious = { viewModel.previousSong() },
                    onPlayPause = { viewModel.playPause() },
                    onNext = { viewModel.nextSong() },
                    colorScheme = albumColorScheme,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(directSheetDragModifier)
                )

                if (displaySongCount == 0) {
                    Box(
                        modifier         = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Queue is empty.", color = colors.onSurface)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(shape = queueListShape)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = queueListShape
                                )
                                .then(
                                    if (isReordering || reorderHandleInUse) {
                                        Modifier
                                    } else {
                                        Modifier.nestedScroll(listDragConnection)
                                    }
                                ),
                            userScrollEnabled = !(isReordering || reorderHandleInUse),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(
                                start = 0.dp, // Reduced start padding by half (12dp -> 6dp)
                                // Reduced end padding: 16.dp when scrollable (was 22.dp), 6dp otherwise to match start
                                end = if (listState.canScrollForward || listState.canScrollBackward) 26.dp else 0.dp,
                                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp
                            )
                        ) {
                            item("queue_top_spacer") {
                                Spacer(modifier = Modifier.height(6.dp))
                            }

                            items(
                                count = displaySongCount,
                                key = { index -> activeKeys[index] },
                                contentType = { "queue_song" }
                            ) { index ->
                                if (index >= activeOrder.size || index >= activeKeys.size) return@items
                                val queueIndex = activeOrder[index]
                                if (queueIndex !in activeSongSource.indices) return@items
                                val itemStableKey = activeKeys[index]
                                val song = activeSongSource[queueIndex]
                                // Use currentSongDisplayIndex for comparison since index is in displayQueue
                                val canReorder = index > currentSongDisplayIndex
                                ReorderableItem(
                                    state = reorderableState,
                                    key = itemStableKey,
                                    enabled = canReorder,
                                    animateItemModifier = when {
                                        isReordering || reorderHandleInUse || reorderPreviewOrder != null -> Modifier.animateItem(
                                            placementSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                        else -> Modifier.animateItem(
                                            fadeInSpec = tween(durationMillis = 140),
                                            fadeOutSpec = tween(durationMillis = 120),
                                            placementSpec = tween(durationMillis = 180)
                                        )
                                    }
                                ) { isDragging ->
                                    val scale by animateFloatAsState(
                                        targetValue = if (isDragging) 1.015f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "scaleAnimation"
                                    )

                                    QueuePlaylistSongItem(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 0.dp)
                                            .graphicsLayer {
                                                scaleX = scale
                                                scaleY = scale
                                            }
                                        ,
                                        onClick = { onPlaySong(song) },
                                        song = song,
                                        // Use index comparison to correctly highlight only the current song
                                        // even when the same song appears multiple times in the queue
                                        isCurrentSong = index == currentSongDisplayIndex,
                                        isPlaying = isPlaying,
                                        isDragging = isDragging,
                                        onRemoveClick = { onRemoveSong(song.id) },
                                        isReorderModeEnabled = false,
                                        isDragHandleVisible = canReorder,
                                        isRemoveButtonVisible = false,
                                        enableSwipeToDismiss = canReorder,
                                        swipeStateIdentity = (itemStableKey shl 32) xor queueIndex.toLong(),
                                        onDismissSong = { onRemoveSong(song.id) },
                                        isFromPlaylist = true,
                                        onMoreOptionsClick = { onSongInfoClick(song) },
                                        dragHandle = {
                                            IconButton(
                                                onClick = {},
                                                modifier = Modifier
                                                    .draggableHandle(
                                                        onDragStarted = {
                                                            draggingSheetFromList = false
                                                            reorderHandleInUse = true
                                                            performAppCompatHapticFeedback(
                                                                view,
                                                                appHapticsConfig,
                                                                HapticFeedbackConstantsCompat.GESTURE_START
                                                            )
                                                        },
                                                        onDragStopped = {
                                                            reorderHandleInUse = false
                                                            performAppCompatHapticFeedback(
                                                                view,
                                                                appHapticsConfig,
                                                                HapticFeedbackConstantsCompat.GESTURE_END
                                                            )
                                                        }
                                                    )
                                                    .size(40.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.DragIndicator,
                                                    contentDescription = "Reorder song",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        ExpressiveScrollBar(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    top = 24.dp,
                                    end = 14.dp,
                                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp
                                )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                val fabSpacing = 16.dp
                val menuSpacing = 20.dp
                val fabRotation by animateFloatAsState(
                    targetValue = if (isFabExpanded) 45f else 0f,
                    label = "fabRotation"
                )

                val navigationBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = fabSpacing + navigationBarHeight)
                        // Usamos IntrinsicSize.Min o una altura fija para asegurar igualdad
                        .height(70.dp)
                        .then(directSheetDragModifier),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically // Alinea FAB y Toolbar al centro verticalmente
                ) {
                    val isTimerActiveDerived = remember {
                        derivedStateOf { activeTimerValueDisplay.value != null }
                    }
                    QueueControlsToolbar(
                        isShuffleOn = isShuffleOn,
                        repeatMode = repeatMode,
                        isTimerActive = isTimerActiveDerived,
                        onToggleShuffle = onToggleShuffle,
                        onToggleRepeat = onToggleRepeat,
                        onTimerClick = { showTimerOptions = true }
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    MediumFloatingActionButton(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f),
                        onClick = { isFabExpanded = !isFabExpanded },
                        shape = AbsoluteSmoothCornerShape(
                            cornerRadiusTR = 50.dp,
                            smoothnessAsPercentTR = 60,
                            cornerRadiusTL = 8.dp,
                            smoothnessAsPercentTL = 60,
                            cornerRadiusBR = 50.dp,
                            smoothnessAsPercentBR = 60,
                            cornerRadiusBL = 8.dp,
                            smoothnessAsPercentBL = 60
                        ),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        elevation = FloatingActionButtonDefaults.elevation(0.dp) // Opcional: para igualar elevación flat
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Queue actions",
                            //modifier = Modifier.rotate(fabRotation)
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .zIndex(20f)
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                isFabExpanded = false
                            }
                    )
                }

                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 3 }),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surfaceContainerLowest
                                    )
                                )
                            )
                            .clickable {
                                isFabExpanded = !isFabExpanded
                            }
                            .zIndex(30f),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            modifier = Modifier
                                .wrapContentWidth(Alignment.CenterHorizontally)
                                .padding(bottom = fabSpacing + menuSpacing),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            QueueToolbarMenuButton(
                                text = "Clear Queue",
                                icon = Icons.Filled.ClearAll,
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                onClick = {
                                    isFabExpanded = false
                                    showClearQueueDialog = true
                                }
                            )
                            QueueToolbarMenuButton(
                                text = "Save as Playlist",
                                icon = Icons.Filled.LibraryAdd,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    isFabExpanded = false
                                    val defaultName = if (currentQueueSourceName.isNotBlank()) {
                                        "${currentQueueSourceName} Queue"
                                    } else {
                                        "Current Queue"
                                    }
                                    onRequestSaveAsPlaylist(
                                        queue,
                                        defaultName
                                    ) { name, selectedIds ->
                                        val orderedSelection = queue
                                            .filter { selectedIds.contains(it.id) }
                                            .map { it.id }
                                        if (orderedSelection.isNotEmpty()) {
                                            playlistViewModel.createPlaylist(
                                                name = name,
                                                songIds = orderedSelection,
                                                isQueueGenerated = true
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Undo bar for queue item removal
            val queueUndoBarState by remember(viewModel) {
                viewModel.playerUiState
                    .map { it.toQueueUndoBarProjection() }
                    .distinctUntilChanged()
            }.collectAsStateWithLifecycle(initialValue = QueueUndoBarProjection())
            AnimatedVisibility(
                visible = queueUndoBarState.isVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp
                    )
                    .zIndex(50f),
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.inverseSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = queueUndoBarState.removedSongTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inverseOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "removed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.inverseOnSurface.copy(alpha = 0.7f),
                        )
                        TextButton(
                            onClick = { viewModel.undoRemoveSongFromQueue() }
                        ) {
                            Text(
                                text = "Undo",
                                color = colors.inversePrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        if (showTimerOptions) {
            TimerOptionsBottomSheet(
                onPlayCounter = onPlayCounter,
                activeTimerValueDisplay = activeTimerValueDisplay.value,
                playCount = playCount.value,
                isEndOfTrackTimerActive = isEndOfTrackTimerActive.value,
                onDismiss = { showTimerOptions = false },
                onSetPredefinedTimer = onSetPredefinedTimer,
                onSetEndOfTrackTimer = onSetEndOfTrackTimer,
                onOpenCustomTimePicker = onOpenCustomTimePicker,
                onCancelCountedPlay = onCancelCountedPlay,
                onCancelTimer = onCancelTimer
            )
        }

        if (showClearQueueDialog) {
            AlertDialog(
                onDismissRequest = { showClearQueueDialog = false },
                title = { Text("Clear Queue") },
                text = { Text("Are you sure you want to clear all songs from the queue except the current one?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onClearQueue()
                            showClearQueueDialog = false
                        }
                    ) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearQueueDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueToolbarMenuButton(
    text: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .widthIn(min = 184.dp, max = 260.dp)
            .heightIn(min = 48.dp)
            .wrapContentWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor
            )
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor
            )
        }
    }
}

/**
 * Composed queue header that merges the miniplayer, section title and source badge
 * into a single expressive surface so the sheet opens with one clear visual idea.
 * Separating this prevents recomposition when unrelated state changes.
 */
@Composable
private fun QueueHeaderSection(
    isPlaying: Boolean,
    queueSourceName: String,
    queueCount: Int,
    topPadding: Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    colorScheme: ColorScheme? = null,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = topPadding, bottom = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 14.dp)
                .width(42.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.14f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            QueueHeader(
                queueSourceName = queueSourceName,
                queueCount = queueCount,
                modifier = Modifier.fillMaxWidth()
            )

            QueueHeaderTransportPanel(
                isPlaying = isPlaying,
                onPrevious = onPrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                colorScheme = colorScheme,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun QueueHeader(
    queueSourceName: String,
    queueCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Next Up",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = when {
                    queueCount <= 0 -> "Queue is empty for now."
                    queueCount == 1 -> "1 track lined up."
                    else -> "$queueCount tracks lined up."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        QueueSourceBadge(
            queueSourceName = queueSourceName,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun QueueSourceBadge(
    queueSourceName: String,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 190.dp),
        shape = CircleShape,
        color = colors.surfaceContainerHighest.copy(alpha = 0.88f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant
            )
            Text(
                text = queueSourceName.ifBlank { "Queue" },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = colors.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1
            )
        }
    }
}

/**
 * Extracted toolbar composable for queue controls (shuffle, repeat, timer).
 * Separating this reduces recompositions when only these states change.
 */
@Composable
private fun QueueControlsToolbar(
    isShuffleOn: Boolean,
    repeatMode: Int,
    isTimerActive: androidx.compose.runtime.State<Boolean>,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
    val inactiveColors = IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 8.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTL = 50.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = 8.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 50.dp,
            smoothnessAsPercentBL = 60
        ),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FilledTonalIconButton(
                onClick = onToggleShuffle,
                colors = if (isShuffleOn) activeColors else inactiveColors,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Toggle Shuffle",
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = onToggleRepeat,
                colors = if (repeatMode != Player.REPEAT_MODE_OFF) activeColors else inactiveColors,
                modifier = Modifier.size(48.dp)
            ) {
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Toggle Repeat",
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalIconButton(
                onClick = onTimerClick,
                colors = if (isTimerActive.value) activeColors else inactiveColors,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = "Sleep Timer",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveQueueAsPlaylistSheet(
    songs: List<Song>,
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Set<String>) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val animatedAlbumCornerRadius = 60.dp
    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    var playlistName by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(defaultName, selection = TextRange(defaultName.length)))
    }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val selectedSongIds = remember(songs) {
        mutableStateMapOf<String, Boolean>().apply {
            songs.forEach { put(it.id, true) }
        }
    }

    val filteredSongs = remember(searchQuery, songs) {
        if (searchQuery.isBlank()) songs
        else songs.filter {
            it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true)
        }
    }

    val hasSelection by remember {
        derivedStateOf { selectedSongIds.any { it.value } }
    }
    val allSelected by remember {
        derivedStateOf { selectedSongIds.isNotEmpty() && selectedSongIds.all { it.value } }
    }

    LaunchedEffect(Unit) {
        // Give the dialog a moment to settle before requesting focus so the IME opens once
        delay(250)
        focusRequester.requestFocus()
    }

    // Override back handler to dismiss the dialog directly
    BackHandler(onBack = { onDismiss() })

    Dialog(
        onDismissRequest = { onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

        Scaffold(
                modifier = Modifier
                    .fillMaxSize(),
                    //.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.surface,
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    Column {
                        MediumTopAppBar(
                            title = {
                                Text(
                                    modifier = Modifier.padding(start = 4.dp),
                                    text = "Save as playlist",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    modifier = Modifier.padding(start = 8.dp),
                                    onClick = { onDismiss() },
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                                }
                            },
                            actions = {
                                val animatedContainerColor by animateColorAsState(
                                    targetValue = if (allSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                    label = "selectBtnContainer"
                                )
                                val animatedContentColor by animateColorAsState(
                                    targetValue = if (allSelected) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurface,
                                    label = "selectBtnContent"
                                )
                                val animatedCornerPercent by animateIntAsState(
                                    targetValue = if (allSelected) 50 else 15,
                                    label = "selectBtnShape"
                                )

                                Surface(
                                    modifier = Modifier
                                        .padding(end = 12.dp)
                                        .height(40.dp)
                                        .clickable {
                                            if (allSelected) {
                                                selectedSongIds.keys.forEach {
                                                    selectedSongIds[it] = false
                                                }
                                            } else {
                                                selectedSongIds.keys.forEach {
                                                    selectedSongIds[it] = true
                                                }
                                            }
                                        },
                                    shape = RoundedCornerShape(animatedCornerPercent),
                                    color = animatedContainerColor,
                                    contentColor = animatedContentColor
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (allSelected) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = if (allSelected) "Deselect All" else "Select All",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface
                            ),
                            //scrollBehavior = scrollBehavior
                        )
                        // Input section pinned to the top
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = playlistName,
                                onValueChange = { playlistName = it },
                                label = { Text("Playlist Name") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )

                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search songs to include...") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(
                                                Icons.Filled.Clear,
                                                contentDescription = "Clear search"
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = CircleShape,
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                )
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                },
                bottomBar = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.ime) // Push up with keyboard
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            tonalElevation = 6.dp,
                            shadowElevation = 4.dp,
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 12.dp)
                                ) {
                                    Text(
                                        text = "${selectedSongIds.count { it.value }} songs selected",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = if (playlistName.text.isNotBlank()) "Save as: ${playlistName.text}" else "Enter a playlist name",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                            alpha = 0.8f
                                        )
                                    )
                                }

                                Button(
                                    onClick = {
                                        if (hasSelection) {
                                            val finalName =
                                                playlistName.text.ifBlank { defaultName }
                                            val chosenIds = selectedSongIds
                                                .filterValues { it }
                                                .keys
                                            onConfirm(finalName, chosenIds)
                                            onDismiss()
                                        }
                                    },
                                    enabled = hasSelection,
                                    modifier = Modifier.height(48.dp),
                                    shape = CircleShape,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Save")
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = innerPadding.calculateTopPadding(),
                            bottom = innerPadding.calculateBottomPadding()
                        )
                        .consumeWindowInsets(innerPadding)
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (filteredSongs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "No songs match \"$searchQuery\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(filteredSongs, key = { it.id }) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .clickable {
                                        val currentSelection = selectedSongIds[song.id] ?: false
                                        selectedSongIds[song.id] = !currentSelection
                                    }
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                        shape = CircleShape
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedSongIds[song.id] ?: false,
                                    onCheckedChange = { isChecked ->
                                        selectedSongIds[song.id] = isChecked
                                    }
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                ) {
                                    SmartImage(
                                        model = song.albumArtUriString,
                                        contentDescription = song.title,
                                        shape = albumShape,
                                        targetSize = Size(168, 168),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        song.displayArtist,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

private data class QueueHeaderTransportColors(
    val playPauseContainer: Color,
    val playPauseContent: Color,
    val skipContainer: Color,
    val skipContent: Color
)

@Composable
private fun QueueHeaderTransportPanel(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    colorScheme: ColorScheme? = null,
    modifier: Modifier = Modifier
) {
    val colors = colorScheme ?: MaterialTheme.colorScheme
    val transportColors = remember(colors) {
        QueueHeaderTransportColors(
            playPauseContainer = colors.tertiaryFixedDim,
            playPauseContent = colors.onTertiaryFixed,
            skipContainer = colors.secondaryFixedDim,
            skipContent = colors.onSecondaryFixed
        )
    }
    val stableControlAnimationSpec = remember {
        tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)
    }

    AnimatedPlaybackControls(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        isPlayingProvider = { isPlaying },
        onPrevious = onPrevious,
        onPlayPause = onPlayPause,
        onNext = onNext,
        height = 74.dp,
        pressAnimationSpec = stableControlAnimationSpec,
        releaseDelay = 220L,
        colorOtherButtons = transportColors.skipContainer,
        colorPlayPause = transportColors.playPauseContainer,
        tintPlayPauseIcon = transportColors.playPauseContent,
        tintOtherIcons = transportColors.skipContent,
        colorPreviousButton = transportColors.skipContainer,
        colorNextButton = transportColors.skipContainer,
        tintPreviousIcon = transportColors.skipContent,
        tintNextIcon = transportColors.skipContent,
        playPauseIconSize = 34.dp,
        iconSize = 30.dp
    )
}

@Composable
fun QueuePlaylistSongItem(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    song: Song,
    isCurrentSong: Boolean,
    isPlaying: Boolean? = null,
    isDragging: Boolean,
    onRemoveClick: () -> Unit,
    dragHandle: @Composable () -> Unit,
    isReorderModeEnabled: Boolean,
    onMoreOptionsClick: (song: Song) -> Unit,
    isDragHandleVisible: Boolean,
    isRemoveButtonVisible: Boolean,
    enableSwipeToDismiss: Boolean = false,
    swipeStateIdentity: Long = 0L,
    onDismissSong: () -> Unit = {},
    isFromPlaylist: Boolean
) {
    val colors = MaterialTheme.colorScheme

    val cornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 22.dp,
        label = "cornerRadiusAnimation"
    )

    val itemShape = RoundedCornerShape(cornerRadius)
//        AbsoluteSmoothCornerShape(
//            cornerRadiusTR = cornerRadius,
//            smoothnessAsPercentTL = 60,
//            cornerRadiusTL = cornerRadius,
//            smoothnessAsPercentTR = 60,
//            cornerRadiusBR = cornerRadius,
//            smoothnessAsPercentBL = 60,
//            cornerRadiusBL = cornerRadius,
//            smoothnessAsPercentBR = 60
//        )

    val albumCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong) 60.dp else 8.dp,
        label = "cornerRadiusAnimation"
    )

    val albumShape = RoundedCornerShape(albumCornerRadius)
//        AbsoluteSmoothCornerShape(
//            cornerRadiusTR = albumCornerRadius,
//            smoothnessAsPercentTL = 60,
//            cornerRadiusTL = albumCornerRadius,
//            smoothnessAsPercentTR = 60,
//            cornerRadiusBR = albumCornerRadius,
//            smoothnessAsPercentBL = 60,
//            cornerRadiusBL = albumCornerRadius,
//            smoothnessAsPercentBR = 60
//        )

    val elevation by animateDpAsState(
        targetValue = if (isDragging) 4.dp else 1.dp,
        label = "elevationAnimation"
    )

    val backgroundColor = colors.surfaceContainerLowest
    val mvContainerColor = if (isCurrentSong) colors.tertiaryContainer else colors.surfaceContainerHigh
    val mvContentColor = if (isCurrentSong) colors.onTertiaryContainer else colors.onSurface
    val hapticView = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current
    val dismissScope = rememberCoroutineScope()
    val dismissEnabled = enableSwipeToDismiss && !isDragging
    val density = LocalDensity.current

    // Custom gesture-based dismiss (tension → snap → free-drag → dismiss/spring-back)
    val dismissOffsetAnimatable = remember(swipeStateIdentity) { Animatable(0f) }
    var itemWidthPx by remember { mutableStateOf(0f) }

    val dismissHandler = remember(swipeStateIdentity, dismissEnabled, itemWidthPx) {
        if (dismissEnabled && itemWidthPx > 0f) {
            QueueItemDismissGestureHandler(
                scope = dismissScope,
                density = density,
                hapticView = hapticView,
                appHapticsConfig = appHapticsConfig,
                offsetAnimatable = dismissOffsetAnimatable,
                itemWidthPx = itemWidthPx,
                onDismiss = onDismissSong
            )
        } else null
    }

    val isSwipeTargeted = dismissHandler?.isInDismissZone == true
    val currentOffsetPx = dismissOffsetAnimatable.value
    val revealWidthPx = (-currentOffsetPx).coerceAtLeast(0f)
    val revealProgress = if (density.density > 0f) {
        (revealWidthPx / (56.dp.value * density.density)).coerceIn(0f, 1f)
    } else 0f

    val dismissBackgroundColor by animateColorAsState(
        targetValue = if (isSwipeTargeted) colors.errorContainer else colors.errorContainer.copy(alpha = 0.82f),
        animationSpec = tween(durationMillis = 150),
        label = "dismissBackgroundColor"
    )
    val dismissIconAlpha by animateFloatAsState(
        targetValue = revealProgress * if (isSwipeTargeted) 1f else 0.88f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconAlpha"
    )
    val dismissIconScale by animateFloatAsState(
        targetValue = if (isSwipeTargeted) 1.08f else 0.95f,
        animationSpec = tween(durationMillis = 120),
        label = "dismissIconScale"
    )
    val dismissIconRotation = 0f

    // Track the actual rendered height of the Surface (foreground item) to size the background exactly.
    var surfaceHeightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val measuredWidth = coordinates.size.width.toFloat()
                if (measuredWidth != itemWidthPx) itemWidthPx = measuredWidth
            }
    ) {
        // Background reveal: stretches horizontally like before, height matches Surface exactly,
        // clipped to CircleShape for fully-rounded ends.
        if (revealWidthPx > 0f && surfaceHeightPx > 0f) {
            val revealWidthDp = with(density) { revealWidthPx.toDp() }
            val surfaceHeightDp = with(density) { surfaceHeightPx.toDp() }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .height(surfaceHeightDp)
                    .width(revealWidthDp)
                    .clip(CircleShape)
                    .background(dismissBackgroundColor),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = "Dismiss song",
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .graphicsLayer {
                            alpha = dismissIconAlpha
                            scaleX = dismissIconScale
                            scaleY = dismissIconScale
                            rotationZ = dismissIconRotation
                        },
                    tint = colors.onErrorContainer
                )
            }
        }

        // Foreground content with horizontal offset
        Surface(
            modifier = Modifier
                .graphicsLayer { translationX = currentOffsetPx }
                .onGloballyPositioned { coordinates ->
                    val h = coordinates.size.height.toFloat()
                    if (h != surfaceHeightPx) surfaceHeightPx = h
                }
                .padding(horizontal = 12.dp)
                .clip(itemShape)
                .clickable(
                    enabled = currentOffsetPx == 0f
                ) {
                    onClick()
                },
            shape = itemShape,
            color = backgroundColor,
            tonalElevation = elevation,
            shadowElevation = elevation
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Drag handle is a DIRECT child of the Row, NOT inside the dismiss
                // gesture area. This prevents detectHorizontalDragGestures from
                // interfering with the reorderable library's drag detection.
                AnimatedVisibility(visible = isDragHandleVisible) {
                    dragHandle()
                }

                // All remaining content is wrapped in a Row that carries the dismiss
                // gesture. Because it is a SIBLING of the drag handle (not an ancestor),
                // pointer events on the drag handle never reach this gesture detector.
                val dismissGestureModifier = if (dismissEnabled && dismissHandler != null) {
                    Modifier.pointerInput(swipeStateIdentity, dismissHandler) {
                        detectHorizontalDragGestures(
                            onDragStart = { dismissHandler.onDragStart() },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                dismissHandler.onHorizontalDrag(dragAmount)
                            },
                            onDragEnd = { dismissHandler.onDragEnd() },
                            onDragCancel = { dismissHandler.onDragCancel() }
                        )
                    }
                } else Modifier

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .then(dismissGestureModifier),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val albumArtPadding by animateDpAsState(
                        targetValue = if (isDragHandleVisible) 6.dp else 12.dp,
                        label = "albumArtPadding"
                    )
                    Spacer(Modifier.width(albumArtPadding))

                    SmartImage(
                        model = song.albumArtUriString,
                        shape = albumShape,
                        contentDescription = "Carátula",
                        modifier = Modifier
                            .size(42.dp)
                            .clip(albumShape),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.width(16.dp))

                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentSong) colors.primary else colors.onSurface,
                            fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentSong) colors.primary.copy(alpha = 0.8f) else colors.onSurfaceVariant
                        )
                    }

                    if (isCurrentSong) {
                        if (isPlaying != null) {
                            PlayingEqIcon(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .size(width = 18.dp, height = 16.dp),
                                color = colors.secondary,
                                isPlaying = isPlaying
                            )
                            Spacer(Modifier.width(4.dp))
                            if (!isRemoveButtonVisible) {
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    } else {
                        Spacer(Modifier.width(8.dp))
                    }

                    if (isFromPlaylist) {
                        FilledIconButton(
                            onClick = { onMoreOptionsClick(song) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = mvContainerColor,
                                contentColor = mvContentColor
                            ),
                            modifier = Modifier
                                .size(36.dp)
                                .padding(end = 14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options for ${song.title}",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    AnimatedVisibility(visible = isRemoveButtonVisible && !enableSwipeToDismiss) {
                        FilledIconButton(
                            onClick = onRemoveClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = colors.surfaceContainer,
                                contentColor = colors.onSurface
                            ),
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp)
                                .padding(start = 4.dp, end = 8.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(18.dp),
                                painter = painterResource(R.drawable.rounded_close_24),
                                contentDescription = "Remove from playlist",
                            )
                        }
                    }
                }
            }
        }
    }
}
