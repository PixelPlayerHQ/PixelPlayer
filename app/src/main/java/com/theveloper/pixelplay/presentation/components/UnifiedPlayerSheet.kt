@file:kotlin.OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.scoped.PlayerAlbumNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerArtistNavigationEffect
import com.theveloper.pixelplay.presentation.components.scoped.PlayerSheetPredictiveBackHandler
import com.theveloper.pixelplay.presentation.components.scoped.QueueSheetRuntimeEffects
import com.theveloper.pixelplay.presentation.components.scoped.miniPlayerDismissHorizontalGesture
import com.theveloper.pixelplay.presentation.components.scoped.playerSheetVerticalDragGesture
import com.theveloper.pixelplay.presentation.components.scoped.rememberCastSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberFullPlayerVisualState
import com.theveloper.pixelplay.presentation.components.scoped.rememberMiniPlayerDismissGestureHandler
import com.theveloper.pixelplay.presentation.components.scoped.rememberPrewarmFullPlayer
import com.theveloper.pixelplay.presentation.components.scoped.rememberQueueSheetState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetBackAndDragState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetInteractionState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetModalOverlayController
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetOverlayState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetActionHandlers
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetThemeState
import com.theveloper.pixelplay.presentation.components.scoped.rememberSheetVisualState
import com.theveloper.pixelplay.presentation.components.scoped.SheetMotionController
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StablePlayerState
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.abs

internal val LocalMaterialTheme = staticCompositionLocalOf<ColorScheme> { error("No ColorScheme provided") }

val MiniPlayerHeight = 64.dp
const val ANIMATION_DURATION_MS = 255

private data class PlayerUiSheetSlice(
    val currentPlaybackQueue: kotlinx.collections.immutable.ImmutableList<Song> = persistentListOf(),
    val currentQueueSourceName: String = "",
    val preparingSongId: String? = null
)

val MiniPlayerBottomSpacer = 8.dp

@OptIn(UnstableApi::class)
@Composable
fun UnifiedPlayerSheet(
    playerViewModel: PlayerViewModel,
    sheetCollapsedTargetY: Float,
    containerHeight: Dp,
    collapsedStateHorizontalPadding: Dp = 12.dp,
    navController: NavHostController,
    hideMiniPlayer: Boolean = false,
    isNavBarHidden: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestContext by rememberUpdatedState(context)
    LaunchedEffect(playerViewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            playerViewModel.toastEvents.collect { message ->
                Toast.makeText(latestContext, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val currentBackStackEntry by navController.currentBackStackEntryAsState()

    val infrequentPlayerStateReference = playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val infrequentPlayerState = infrequentPlayerStateReference.value

    val currentPositionState = playerViewModel.currentPlaybackPosition.collectAsStateWithLifecycle()

    val remotePositionState = playerViewModel.remotePosition.collectAsStateWithLifecycle()
    // We observe isRemotePlaybackActive directly as switching modes is a major event
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    
    // Position Provider: Reads state inside the lambda to prevent recomposition of UnifiedPlayerSheet
    val positionToDisplayProvider = remember(isRemotePlaybackActive) {
        {
            if (isRemotePlaybackActive) remotePositionState.value
            else currentPositionState.value
        }
    }
    
    val isFavorite by playerViewModel.isCurrentSongFavorite.collectAsStateWithLifecycle()
    val playerUiSheetSlice by remember {
        playerViewModel.playerUiState
            .map { state ->
                PlayerUiSheetSlice(
                    currentPlaybackQueue = state.currentPlaybackQueue,
                    currentQueueSourceName = state.currentQueueSourceName,
                    preparingSongId = state.preparingSongId
                )
            }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = PlayerUiSheetSlice())

    val currentPlaybackQueue = playerUiSheetSlice.currentPlaybackQueue
    val currentQueueSourceName = playerUiSheetSlice.currentQueueSourceName
    val preparingSongId = playerUiSheetSlice.preparingSongId

    val currentSheetContentState by playerViewModel.sheetState.collectAsStateWithLifecycle()
    val predictiveBackCollapseProgress by playerViewModel.predictiveBackCollapseFraction.collectAsStateWithLifecycle()
    val predictiveBackSwipeEdge by playerViewModel.predictiveBackSwipeEdge.collectAsStateWithLifecycle()
    val prewarmFullPlayer = rememberPrewarmFullPlayer(infrequentPlayerState.currentSong?.id)

    val playerConfig by playerViewModel.playerConfigSlice.collectAsStateWithLifecycle()
    val navBarCornerRadius = playerConfig.navBarCornerRadius
    val navBarStyle = playerConfig.navBarStyle
    val carouselStyle = playerConfig.carouselStyle
    val fullPlayerLoadingTweaks = playerConfig.fullPlayerLoadingTweaks
    val tapBackgroundClosesPlayer = playerConfig.tapBackgroundClosesPlayer
    val useSmoothCorners = playerConfig.useSmoothCorners
    val playerThemePreference = playerConfig.playerThemePreference

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()

    val offsetAnimatable = remember { Animatable(0f) }

    val screenWidthPx =
        remember(configuration, density) { with(density) { configuration.screenWidthDp.dp.toPx() } }
    val dismissThresholdPx = remember(screenWidthPx) { screenWidthPx * 0.4f }

    val swipeDismissProgress by remember(dismissThresholdPx) {
        derivedStateOf {
            if (dismissThresholdPx == 0f) 0f
            else (abs(offsetAnimatable.value) / dismissThresholdPx).coerceIn(0f, 1f)
        }
    }

    val screenHeightPx = remember(
        configuration,
        density
    ) { with(density) { configuration.screenHeightDp.dp.toPx() } }
    val miniPlayerContentHeightPx = remember { with(density) { MiniPlayerHeight.toPx() } }

    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()

    val showPlayerContentArea by remember(infrequentPlayerState.currentSong, isCastConnecting) {
        derivedStateOf { infrequentPlayerState.currentSong != null || isCastConnecting }
    }

    val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction
    val visualOvershootScaleY = remember { Animatable(1f) }
    val initialFullPlayerOffsetY = remember(density) { with(density) { 24.dp.toPx() } }
    val sheetAnimationSpec = remember {
        tween<Float>(
            durationMillis = ANIMATION_DURATION_MS,
            easing = FastOutSlowInEasing
        )
    }
    val sheetAnimationMutex = remember { MutatorMutex() }

    val sheetExpandedTargetY = 0f

    val initialY =
        if (currentSheetContentState == PlayerSheetState.COLLAPSED) sheetCollapsedTargetY else sheetExpandedTargetY
    val currentSheetTranslationY = remember { Animatable(initialY) }
    val sheetMotionController = remember(
        currentSheetTranslationY,
        playerContentExpansionFraction,
        sheetAnimationMutex,
        sheetAnimationSpec
    ) {
        SheetMotionController(
            translationY = currentSheetTranslationY,
            expansionFraction = playerContentExpansionFraction,
            mutex = sheetAnimationMutex,
            defaultAnimationSpec = sheetAnimationSpec,
            expandedY = sheetExpandedTargetY
        )
    }

    PlayerArtistNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )
    PlayerAlbumNavigationEffect(
        navController = navController,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetMotionController = sheetMotionController,
        playerViewModel = playerViewModel
    )

    val fullPlayerVisualState = rememberFullPlayerVisualState(
        expansionFraction = playerContentExpansionFraction,
        initialOffsetY = initialFullPlayerOffsetY
    )

    suspend fun animatePlayerSheet(
        targetExpanded: Boolean,
        animationSpec: androidx.compose.animation.core.AnimationSpec<Float> = sheetAnimationSpec,
        initialVelocity: Float = 0f
    ) {
        sheetMotionController.animateTo(
            targetExpanded = targetExpanded,
            canExpand = showPlayerContentArea,
            collapsedY = sheetCollapsedTargetY,
            animationSpec = animationSpec,
            initialVelocity = initialVelocity
        )
    }

    val sheetCollapsedTargetYState = rememberUpdatedState(sheetCollapsedTargetY)
    LaunchedEffect(sheetMotionController) {
        snapshotFlow { sheetCollapsedTargetYState.value }
            .collect { collapsedTargetY ->
                sheetMotionController.syncToExpansion(collapsedTargetY)
            }
    }

    var previousSheetState by remember { mutableStateOf(currentSheetContentState) }
    LaunchedEffect(showPlayerContentArea, currentSheetContentState) {
        val targetExpanded = showPlayerContentArea && currentSheetContentState == PlayerSheetState.EXPANDED
        val shouldBounceCollapse =
            showPlayerContentArea &&
                previousSheetState == PlayerSheetState.EXPANDED &&
                currentSheetContentState == PlayerSheetState.COLLAPSED

        previousSheetState = currentSheetContentState

        animatePlayerSheet(targetExpanded = targetExpanded)

        if (showPlayerContentArea) {
            scope.launch {
                visualOvershootScaleY.snapTo(1f)
                if (targetExpanded) {
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = 50
                            1.0f at 0
                            1.05f at 125
                            1.0f at 250
                        }
                    )
                } else if (shouldBounceCollapse) {
                    visualOvershootScaleY.snapTo(0.96f)
                    visualOvershootScaleY.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                } else {
                    visualOvershootScaleY.snapTo(1f)
                }
            }
        } else {
            scope.launch { visualOvershootScaleY.snapTo(1f) }
        }
    }

    val sheetVisualState = rememberSheetVisualState(
        showPlayerContentArea = showPlayerContentArea,
        collapsedStateHorizontalPadding = collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress = predictiveBackCollapseProgress,
        predictiveBackSwipeEdge = predictiveBackSwipeEdge,
        currentSheetContentState = currentSheetContentState,
        playerContentExpansionFraction = playerContentExpansionFraction,
        containerHeight = containerHeight,
        currentSheetTranslationY = currentSheetTranslationY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        navBarStyle = navBarStyle,
        navBarCornerRadiusDp = navBarCornerRadius.dp,
        isNavBarHidden = isNavBarHidden,
        isPlaying = infrequentPlayerState.isPlaying,
        hasCurrentSong = infrequentPlayerState.currentSong != null,
        swipeDismissProgress = swipeDismissProgress
    )
    val currentBottomPadding = sheetVisualState.currentBottomPadding
    val playerContentAreaHeightPxProvider = sheetVisualState.playerContentAreaHeightPxProvider
    val visualSheetTranslationYProvider = sheetVisualState.visualSheetTranslationYProvider
    val overallSheetTopCornerRadius = sheetVisualState.overallSheetTopCornerRadius
    val playerContentActualBottomRadius = sheetVisualState.playerContentActualBottomRadius
    val currentHorizontalPaddingStartPxProvider = sheetVisualState.currentHorizontalPaddingStartPxProvider
    val currentHorizontalPaddingEndPxProvider = sheetVisualState.currentHorizontalPaddingEndPxProvider

    val queueSheetState = rememberQueueSheetState(
        scope = scope,
        screenHeightPx = screenHeightPx,
        density = density,
        currentBottomPadding = currentBottomPadding,
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val showQueueSheet = queueSheetState.showQueueSheet
    val allowQueueSheetInteraction = queueSheetState.allowQueueSheetInteraction
    val queueSheetOffset = queueSheetState.queueSheetOffset
    val queueSheetHeightPx = queueSheetState.queueSheetHeightPx
    val queueHiddenOffsetPx = queueSheetState.queueHiddenOffsetPx
    val queueSheetController = queueSheetState.queueSheetController
    val onQueueSheetHeightPxChange = queueSheetState.onQueueSheetHeightPxChange

    val castSheetState = rememberCastSheetState()
    val sheetBackAndDragState = rememberSheetBackAndDragState(
        showPlayerContentArea = showPlayerContentArea,
        currentSheetContentState = currentSheetContentState
    )
    val canHandlePlayerBack by remember(
        sheetBackAndDragState.predictiveBackEnabled,
        showQueueSheet,
        castSheetState.showCastSheet
    ) {
        derivedStateOf {
            sheetBackAndDragState.predictiveBackEnabled &&
                !showQueueSheet &&
                !castSheetState.showCastSheet
        }
    }
    val velocityTracker = remember { VelocityTracker() }
    val sheetModalOverlayController = rememberSheetModalOverlayController(
        scope = scope,
        queueSheetController = queueSheetController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onCollapsePlayerSheet = { playerViewModel.collapsePlayerSheet() }
    )
    val pendingSaveQueueOverlay = sheetModalOverlayController.pendingSaveQueueOverlay
    val selectedSongForInfo = sheetModalOverlayController.selectedSongForInfo
    val sheetActionHandlers = rememberSheetActionHandlers(
        scope = scope,
        navController = navController,
        playerViewModel = playerViewModel,
        sheetMotionController = sheetMotionController,
        queueSheetController = queueSheetController,
        sheetModalOverlayController = sheetModalOverlayController,
        sheetCollapsedTargetY = sheetCollapsedTargetY
    )

    val hapticFeedback = LocalHapticFeedback.current
    val miniDismissGestureHandler = rememberMiniPlayerDismissGestureHandler(
        scope = scope,
        density = density,
        hapticFeedback = hapticFeedback,
        offsetAnimatable = offsetAnimatable,
        screenWidthPx = screenWidthPx,
        onDismissPlaylistAndShowUndo = { playerViewModel.dismissPlaylistAndShowUndo() }
    )

    QueueSheetRuntimeEffects(
        queueSheetController = queueSheetController,
        queueSheetOffset = queueSheetOffset,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        showQueueSheet = showQueueSheet,
        allowQueueSheetInteraction = allowQueueSheetInteraction,
        onTopEdgeReached = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    )

    PlayerSheetPredictiveBackHandler(
        enabled = canHandlePlayerBack,
        playerViewModel = playerViewModel,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        sheetMotionController = sheetMotionController,
        animationDurationMs = ANIMATION_DURATION_MS,
        onSwipeEdgeChanged = { playerViewModel.updatePredictiveBackSwipeEdge(it) },
        registrationKey = currentBackStackEntry?.id
    )

    val sheetOverlayState = rememberSheetOverlayState(
        density = density,
        showPlayerContentArea = showPlayerContentArea,
        hideMiniPlayer = hideMiniPlayer,
        showQueueSheet = showQueueSheet,
        queueHiddenOffsetPx = queueHiddenOffsetPx,
        screenHeightPx = screenHeightPx,
        castSheetOpenFraction = castSheetState.castSheetOpenFraction
    )
    val internalIsKeyboardVisible = sheetOverlayState.internalIsKeyboardVisible
    val actuallyShowSheetContent = sheetOverlayState.actuallyShowSheetContent
    val isQueueVisible = sheetOverlayState.isQueueVisible
    val bottomSheetOpenFraction = sheetOverlayState.bottomSheetOpenFraction
    val queueScrimAlpha = sheetOverlayState.queueScrimAlpha

    LaunchedEffect(showQueueSheet) {
        playerViewModel.updateQueueSheetVisibility(showQueueSheet)
    }
    LaunchedEffect(castSheetState.showCastSheet) {
        playerViewModel.updateCastSheetVisibility(castSheetState.showCastSheet)
    }
    DisposableEffect(Unit) {
        onDispose {
            playerViewModel.updateQueueSheetVisibility(false)
            playerViewModel.updateCastSheetVisibility(false)
        }
    }

    val activePlayerSchemePair by playerViewModel.activePlayerColorSchemePair.collectAsStateWithLifecycle()
    val themedAlbumArtUri by playerViewModel.currentThemedAlbumArtUri.collectAsStateWithLifecycle()
    val isDarkTheme = LocalPixelPlayDarkTheme.current
    val currentSong = infrequentPlayerState.currentSong
    val sheetThemeState = rememberSheetThemeState(
        activePlayerSchemePair = activePlayerSchemePair,
        isDarkTheme = isDarkTheme,
        playerThemePreference = playerThemePreference,
        currentSong = currentSong,
        themedAlbumArtUri = themedAlbumArtUri,
        preparingSongId = preparingSongId,
        systemColorScheme = MaterialTheme.colorScheme
    )
    val albumColorScheme = sheetThemeState.albumColorScheme
    val miniPlayerScheme = sheetThemeState.miniPlayerScheme
    val isPreparingPlayback = sheetThemeState.isPreparingPlayback
    val miniReadyAlpha = sheetThemeState.miniReadyAlpha
    val miniAppearScale = sheetThemeState.miniAppearScale
    val playerAreaBackground = sheetThemeState.playerAreaBackground
    val visualCardShadowElevation by remember(showQueueSheet, miniReadyAlpha) {
        derivedStateOf {
            if (showQueueSheet || playerContentExpansionFraction.value > 0.18f) 0.dp
            else (3f * miniReadyAlpha).dp
        }
    }

    val sheetInteractionState = rememberSheetInteractionState(
        scope = scope,
        velocityTracker = velocityTracker,
        sheetMotionController = sheetMotionController,
        playerContentExpansionFraction = playerContentExpansionFraction,
        currentSheetTranslationY = currentSheetTranslationY,
        visualOvershootScaleY = visualOvershootScaleY,
        sheetCollapsedTargetY = sheetCollapsedTargetY,
        sheetExpandedTargetY = sheetExpandedTargetY,
        miniPlayerContentHeightPx = miniPlayerContentHeightPx,
        currentSheetContentState = currentSheetContentState,
        showPlayerContentArea = showPlayerContentArea,
        overallSheetTopCornerRadius = overallSheetTopCornerRadius,
        playerContentActualBottomRadius = playerContentActualBottomRadius,
        useSmoothCorners = useSmoothCorners,
        isDragging = sheetBackAndDragState.isDragging,
        onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
            if (animationSpec == null) {
                animatePlayerSheet(targetExpanded = targetExpanded)
            } else {
                animatePlayerSheet(
                    targetExpanded = targetExpanded,
                    animationSpec = animationSpec,
                    initialVelocity = initialVelocity
                )
            }
        },
        onExpandSheetState = { playerViewModel.expandPlayerSheet() },
        onCollapseSheetState = { playerViewModel.collapsePlayerSheet() },
        onDraggingChange = sheetBackAndDragState.onDraggingChange,
        onDraggingPlayerAreaChange = sheetBackAndDragState.onDraggingPlayerAreaChange
    )

    if (actuallyShowSheetContent) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, visualSheetTranslationYProvider().roundToInt()) }
                .height(containerHeight),
            shadowElevation = 0.dp,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = currentBottomPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Use granular showDismissUndoBar and undoBarVisibleDuration
                    if (showPlayerContentArea) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val targetHeightPx = playerContentAreaHeightPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val startPaddingPx = currentHorizontalPaddingStartPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val endPaddingPx = currentHorizontalPaddingEndPxProvider()
                                        .toInt().coerceAtLeast(0)
                                    val innerWidth = (constraints.maxWidth - startPaddingPx - endPaddingPx)
                                        .coerceAtLeast(0)
                                    val placeable = measurable.measure(
                                        constraints.copy(
                                            minWidth = innerWidth,
                                            maxWidth = innerWidth,
                                            minHeight = targetHeightPx,
                                            maxHeight = targetHeightPx
                                        )
                                    )
                                    layout(constraints.maxWidth, targetHeightPx) {
                                        placeable.placeRelative(startPaddingPx, 0)
                                    }
                                }
                                .miniPlayerDismissHorizontalGesture(
                                    enabled = currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    handler = miniDismissGestureHandler
                                )
                                .graphicsLayer {
                                    translationX = offsetAnimatable.value
                                    scaleX = miniAppearScale
                                    scaleY = visualOvershootScaleY.value * miniAppearScale
                                    alpha = miniReadyAlpha
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .then(
                                    if (visualCardShadowElevation > 0.dp) {
                                        Modifier.shadow(
                                            elevation = visualCardShadowElevation,
                                            shape = sheetInteractionState.playerShadowShape,
                                            clip = false
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .background(
                                    color = playerAreaBackground,
                                    shape = sheetInteractionState.playerShadowShape
                                )
                                .clipToBounds()
                                .playerSheetVerticalDragGesture(
                                    enabled = sheetInteractionState.canDragSheet,
                                    handler = sheetInteractionState.sheetVerticalDragGestureHandler
                                )
                                .clickable(
                                    enabled = tapBackgroundClosesPlayer || currentSheetContentState == PlayerSheetState.COLLAPSED,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    playerViewModel.togglePlayerSheetState()
                                }
                        ) {
                            UnifiedPlayerMiniAndFullLayers(
                                currentSong = infrequentPlayerState.currentSong,
                                miniPlayerScheme = miniPlayerScheme,
                                overallSheetTopCornerRadius = overallSheetTopCornerRadius,
                                infrequentPlayerState = infrequentPlayerState,
                                isCastConnecting = isCastConnecting,
                                isPreparingPlayback = isPreparingPlayback,
                                playerContentExpansionFraction = playerContentExpansionFraction,
                                albumColorScheme = albumColorScheme,
                                bottomSheetOpenFraction = bottomSheetOpenFraction,
                                fullPlayerVisualState = fullPlayerVisualState,
                                currentPlaybackQueue = currentPlaybackQueue,
                                currentQueueSourceName = currentQueueSourceName,
                                currentSheetContentState = currentSheetContentState,
                                carouselStyle = carouselStyle,
                                fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                                isSheetDragGestureActive = sheetBackAndDragState.isDraggingPlayerArea,
                                playerViewModel = playerViewModel,
                                currentPositionProvider = positionToDisplayProvider,
                                isFavorite = isFavorite,
                                onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                                onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                                onQueueDrag = sheetActionHandlers.dragQueueBy,
                                onQueueRelease = sheetActionHandlers.endQueueDrag,
                                onShowCastClicked = castSheetState.openCastSheet
                            )
                        }
                    }

                    // Prewarm full player once per track to reduce first-open jank.
                    UnifiedPlayerPrewarmLayer(
                        prewarmFullPlayer = prewarmFullPlayer,
                        currentSong = infrequentPlayerState.currentSong,
                        containerHeight = containerHeight,
                        albumColorScheme = albumColorScheme,
                        currentPlaybackQueue = currentPlaybackQueue,
                        currentQueueSourceName = currentQueueSourceName,
                        infrequentPlayerState = infrequentPlayerState,
                        carouselStyle = carouselStyle,
                        fullPlayerLoadingTweaks = fullPlayerLoadingTweaks,
                        playerViewModel = playerViewModel,
                        currentPositionProvider = positionToDisplayProvider,
                        isCastConnecting = isCastConnecting,
                        isFavorite = isFavorite,
                        onShowQueueClicked = sheetActionHandlers.openQueueSheet,
                        onQueueDragStart = sheetActionHandlers.beginQueueDrag,
                        onQueueDrag = sheetActionHandlers.dragQueueBy,
                        onQueueRelease = sheetActionHandlers.endQueueDrag
                    )
                }

                BackHandler(enabled = isQueueVisible && !internalIsKeyboardVisible) {
                    sheetActionHandlers.animateQueueSheet(false)
                }


                UnifiedPlayerQueueAndSongInfoHost(
                    shouldRenderHost = !internalIsKeyboardVisible || selectedSongForInfo != null,
                    isQueueTelemetryActive = showQueueSheet,
                    albumColorScheme = albumColorScheme,
                    queueScrimAlpha = queueScrimAlpha,
                    showQueueSheet = showQueueSheet,
                    queueHiddenOffsetPx = queueHiddenOffsetPx,
                    queueSheetOffset = queueSheetOffset,
                    queueSheetHeightPx = queueSheetHeightPx,
                    onQueueSheetHeightPxChange = onQueueSheetHeightPxChange,
                    configurationResetKey = configuration,
                    currentPlaybackQueue = currentPlaybackQueue,
                    currentQueueSourceName = currentQueueSourceName,
                    infrequentPlayerState = infrequentPlayerState,
                    playerViewModel = playerViewModel,
                    selectedSongForInfo = selectedSongForInfo,
                    onSelectedSongForInfoChange = sheetActionHandlers.onSelectedSongForInfoChange,
                    onAnimateQueueSheet = sheetActionHandlers.animateQueueSheet,
                    onBeginQueueDrag = sheetActionHandlers.beginQueueDrag,
                    onDragQueueBy = sheetActionHandlers.dragQueueBy,
                    onEndQueueDrag = sheetActionHandlers.endQueueDrag,
                    onLaunchSaveQueueOverlay = sheetActionHandlers.onLaunchSaveQueueOverlay,
                    onNavigateToAlbum = sheetActionHandlers.onNavigateToAlbum,
                    onNavigateToArtist = sheetActionHandlers.onNavigateToArtist,
                    onNavigateToGenre = sheetActionHandlers.onNavigateToGenre
                )

            }
        }

        UnifiedPlayerCastLayer(
            showCastSheet = castSheetState.showCastSheet,
            internalIsKeyboardVisible = internalIsKeyboardVisible,
            albumColorScheme = albumColorScheme,
            playerViewModel = playerViewModel,
            onDismiss = castSheetState.dismissCastSheet,
            onExpansionChanged = castSheetState.onCastExpansionChanged
        )

        UnifiedPlayerSaveQueueLayer(
            pendingOverlay = pendingSaveQueueOverlay,
            onDismissOverlay = { sheetModalOverlayController.dismissSaveQueueOverlay() }
        )
    }
}

@Composable
private fun CastConnectingDialog() {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 24.dp)
                    .widthIn(min = 220.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Mantén la app abierta",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Estamos transfiriendo la reproducción. Puede tardar unos segundos en desconectarse o reconectarse.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun getNavigationBarHeight(): Dp {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    return insets.calculateBottomPadding()
}

@Composable
internal fun MiniPlayerContentInternal(
    song: Song,
    isPlaying: Boolean,
    isCastConnecting: Boolean,
    isPreparingPlayback: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    cornerRadiusAlb: Dp,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hapticFeedback = LocalHapticFeedback.current
    val controlsEnabled = !isCastConnecting && !isPreparingPlayback

    val previousInteraction = remember { MutableInteractionSource() }
    val playPauseInteraction = remember { MutableInteractionSource() }
    val nextInteraction = remember { MutableInteractionSource() }
    val miniPlayerIndication = remember { ripple(bounded = false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(start = 10.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val albumArtModel = song.albumArtUriString?.takeIf { it.isNotBlank() }
        Box(contentAlignment = Alignment.Center) {
            key(song.id) {
                SmartImage(
                    model = albumArtModel,
                    contentDescription = "Carátula de ${song.title}",
                    shape = CircleShape,
                    targetSize = Size(150, 150),
                    modifier = Modifier.size(44.dp),
                    placeholderModel = if (albumArtModel?.startsWith("telegram_art") == true) {
                        "$albumArtModel?quality=thumb"
                    } else null
                )
            }
            if (isCastConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = LocalMaterialTheme.current.onPrimaryContainer
                )
            } else if (isPreparingPlayback) {
                CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            val titleStyle = MaterialTheme.typography.titleSmall.copy(
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer
            )
            val artistStyle = MaterialTheme.typography.bodySmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontFamily = GoogleSansRounded,
                color = LocalMaterialTheme.current.onPrimaryContainer.copy(alpha = 0.7f)
            )

            AutoScrollingText(
                text = when {
                    isCastConnecting -> "Connecting to device…"
                    isPreparingPlayback -> "Preparing playback…"
                    else -> song.title
                },
                style = titleStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
            )
            AutoScrollingText(
                text = if (isPreparingPlayback) "Loading audio…" else song.displayArtist,
                style = artistStyle,
                gradientEdgeColor = LocalMaterialTheme.current.primaryContainer
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.onPrimary)
                .clickable(
                    interactionSource = previousInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPrevious()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipPrevious,
                contentDescription = "Anterior",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.primary)
                .clickable(
                    interactionSource = playPauseInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onPlayPause()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                tint = LocalMaterialTheme.current.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(LocalMaterialTheme.current.onPrimary)
                .clickable(
                    interactionSource = nextInteraction,
                    indication = miniPlayerIndication,
                    enabled = controlsEnabled
                ) { onNext() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.SkipNext,
                contentDescription = "Siguiente",
                tint = LocalMaterialTheme.current.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
