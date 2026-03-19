package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private enum class PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = LocalMaterialTheme.current.secondaryContainer,
    colorPlayPause: Color = LocalMaterialTheme.current.primary,
    tintPlayPauseIcon: Color = LocalMaterialTheme.current.onPrimary,
    tintOtherIcons: Color = LocalMaterialTheme.current.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(lastClicked) {
        if (lastClicked != null) {
            delay(releaseDelay)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.PREVIOUS
                        onPrevious()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Anterior",
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "playCorner"
            )
            val playShape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = playCorner,
                smoothnessAsPercentTR = 60,
                cornerRadiusBL = playCorner,
                smoothnessAsPercentTL = 60,
                cornerRadiusTR = playCorner,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = playCorner,
                smoothnessAsPercentBR = 60
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .clip(playShape)
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = PlaybackButtonType.PLAY_PAUSE
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                MorphingPlayPauseIcon(
                    isPlaying = playPauseVisualState,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        lastClicked = PlaybackButtonType.NEXT
                        onNext()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Siguiente",
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
) {
    Crossfade(
        targetState = isPlaying,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "playPauseCrossfade"
    ) { playing ->
        Icon(
            imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pausar" else "Reproducir",
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
