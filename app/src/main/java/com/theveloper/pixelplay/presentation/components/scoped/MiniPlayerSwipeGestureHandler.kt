package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TRACK_SWIPE_THRESHOLD_DP = 48f
private const val DISMISS_SCREEN_WIDTH_FRACTION = 0.4f

/**
 * Track changes are the default; optional dismiss mode restores swipe-away queue dismissal.
 */
internal class MiniPlayerSwipeGestureHandler(
    private val scope: CoroutineScope,
    density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val dismissEnabled: Boolean,
    private val onSwipeToNext: () -> Unit,
    private val onSwipeToPrevious: () -> Unit,
    private val onDismiss: () -> Unit,
    private val onDismissStarted: () -> Unit
) {
    private val trackSwipeThresholdPx = with(density) { TRACK_SWIPE_THRESHOLD_DP.dp.toPx() }
    private var accumulatedDragX = 0f
    private var trackChanged = false
    private var offsetJob: Job? = null

    fun onDragStart() {
        accumulatedDragX = 0f
        trackChanged = false
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.stop()
        }
    }

    fun onHorizontalDrag(dragAmount: Float) {
        accumulatedDragX += dragAmount
        if (dismissEnabled) {
            offsetJob?.cancel()
            offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.snapTo(accumulatedDragX)
            }
            return
        }

        if (trackChanged || abs(accumulatedDragX) < trackSwipeThresholdPx) return
        trackChanged = true
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
        if (accumulatedDragX < 0f) onSwipeToNext() else onSwipeToPrevious()
    }

    fun onDragEnd() {
        val dismissalThresholdPx = screenWidthPx * DISMISS_SCREEN_WIDTH_FRACTION
        val shouldDismiss = dismissEnabled && abs(accumulatedDragX) > dismissalThresholdPx
        val targetOffset = if (accumulatedDragX < 0f) -screenWidthPx else screenWidthPx
        offsetJob?.cancel()

        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            if (shouldDismiss) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onDismissStarted()
                offsetAnimatable.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = FastOutSlowInEasing
                    )
                )
                onDismiss()
            }
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        accumulatedDragX = 0f
        trackChanged = false
    }

    fun onDragCancel() {
        accumulatedDragX = 0f
        trackChanged = false
        offsetJob?.cancel()
        offsetJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            offsetAnimatable.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }
}

@Composable
internal fun rememberMiniPlayerSwipeGestureHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    dismissEnabled: Boolean,
    onSwipeToNext: () -> Unit,
    onSwipeToPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onDismissStarted: () -> Unit
): MiniPlayerSwipeGestureHandler {
    val onSwipeToNextState = rememberUpdatedState(onSwipeToNext)
    val onSwipeToPreviousState = rememberUpdatedState(onSwipeToPrevious)
    val onDismissState = rememberUpdatedState(onDismiss)
    val onDismissStartedState = rememberUpdatedState(onDismissStarted)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx, dismissEnabled) {
        MiniPlayerSwipeGestureHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            dismissEnabled = dismissEnabled,
            onSwipeToNext = { onSwipeToNextState.value() },
            onSwipeToPrevious = { onSwipeToPreviousState.value() },
            onDismiss = { onDismissState.value() },
            onDismissStarted = { onDismissStartedState.value() }
        )
    }
}

internal fun Modifier.miniPlayerSwipeHorizontalGesture(
    enabled: Boolean,
    handler: MiniPlayerSwipeGestureHandler
): Modifier {
    if (!enabled) return this
    return pointerInput(enabled, handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount)
            },
            onDragEnd = { handler.onDragEnd() },
            onDragCancel = { handler.onDragCancel() }
        )
    }
}
