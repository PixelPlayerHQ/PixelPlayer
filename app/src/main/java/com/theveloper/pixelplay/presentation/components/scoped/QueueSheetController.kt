package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Controls queue sheet visibility, drag and snapping decisions.
 * Behavior mirrors the previous inline logic in UnifiedPlayerSheet.
 */
internal class QueueSheetController(
    private val scope: CoroutineScope,
    private val queueSheetOffset: Animatable<Float, AnimationVector1D>,
    private val hiddenOffsetProvider: () -> Float,
    private val allowInteractionProvider: () -> Boolean,
    private val minFlingTravelPxProvider: () -> Float,
    private val dragThresholdPxProvider: () -> Float,
    private val showQueueSheetProvider: () -> Boolean,
    private val onShowQueueSheetChange: (Boolean) -> Unit
) {
    private var dragOffsetCache: Float? = null
    private var pendingDragTarget: Float? = null
    private var dragSnapJob: Job? = null

    private fun resetDragPipeline() {
        dragOffsetCache = null
        pendingDragTarget = null
        dragSnapJob?.cancel()
        dragSnapJob = null
    }

    private fun launchDragSnapLoopIfNeeded() {
        if (dragSnapJob?.isActive == true) return

        dragSnapJob = scope.launch {
            while (isActive) {
                val target = pendingDragTarget ?: break
                pendingDragTarget = null
                queueSheetOffset.snapTo(target)

                // Coalesce high-frequency deltas into frame-paced updates.
                if (coroutineContext[MonotonicFrameClock] != null) {
                    withFrameNanos { }
                } else {
                    yield()
                }
            }
        }
    }

    suspend fun syncOffsetToVisibility() {
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset <= 0f) return
        val targetOffset = if (showQueueSheetProvider()) {
            // If open was requested before we knew the measured height, offset can still be off-range.
            // In that case, honor the open request by snapping to fully expanded.
            if (queueSheetOffset.value > hiddenOffset) 0f
            else queueSheetOffset.value.coerceIn(0f, hiddenOffset)
        } else {
            hiddenOffset
        }
        queueSheetOffset.snapTo(targetOffset)
    }

    suspend fun syncCollapsedWhenHidden() {
        val hiddenOffset = hiddenOffsetProvider()
        if (!showQueueSheetProvider() && hiddenOffset > 0f && queueSheetOffset.value != hiddenOffset) {
            queueSheetOffset.snapTo(hiddenOffset)
        }
    }

    suspend fun forceCollapseIfInteractionDisabled() {
        if (allowInteractionProvider()) return
        onShowQueueSheetChange(false)
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset > 0f) {
            queueSheetOffset.snapTo(hiddenOffset)
        }
    }

    suspend fun animateTo(targetExpanded: Boolean) {
        resetDragPipeline()
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset == 0f) {
            onShowQueueSheetChange(targetExpanded)
            return
        }
        val target = if (targetExpanded) 0f else hiddenOffset
        val shouldPrewarmFirstFrame = targetExpanded && !showQueueSheetProvider()
        onShowQueueSheetChange(true)
        if (shouldPrewarmFirstFrame) {
            queueSheetOffset.snapTo(hiddenOffset)
            if (coroutineContext[MonotonicFrameClock] != null) {
                withFrameNanos { }
            } else {
                yield()
            }
        }
        val travelFraction = if (hiddenOffset > 0f) {
            (abs(queueSheetOffset.value - target) / hiddenOffset).coerceIn(0f, 1f)
        } else {
            1f
        }
        val durationMillis = if (targetExpanded) {
            (220f + (120f * travelFraction)).toInt()
        } else {
            (190f + (110f * travelFraction)).toInt()
        }
        queueSheetOffset.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = durationMillis,
                easing = FastOutSlowInEasing
            )
        )
        onShowQueueSheetChange(targetExpanded)
    }

    fun animate(targetExpanded: Boolean) {
        if (!allowInteractionProvider() && targetExpanded) return
        scope.launch { animateTo(targetExpanded && allowInteractionProvider()) }
    }

    fun beginDrag() {
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset == 0f || !allowInteractionProvider()) return
        resetDragPipeline()
        dragOffsetCache = queueSheetOffset.value
        onShowQueueSheetChange(true)
        scope.launch { queueSheetOffset.stop() }
    }

    fun dragBy(dragAmount: Float) {
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset == 0f || !allowInteractionProvider()) return
        val baseOffset = dragOffsetCache ?: queueSheetOffset.value
        val newOffset = (baseOffset + dragAmount).coerceIn(0f, hiddenOffset)
        dragOffsetCache = newOffset
        pendingDragTarget = newOffset
        launchDragSnapLoopIfNeeded()
    }

    fun endDrag(totalDrag: Float, velocity: Float) {
        val hiddenOffset = hiddenOffsetProvider()
        if (hiddenOffset == 0f || !allowInteractionProvider()) return

        // Freeze pending deltas before deciding snap target.
        val settledOffset = pendingDragTarget ?: dragOffsetCache ?: queueSheetOffset.value
        resetDragPipeline()

        val isFastUpward = velocity < -520f
        val isFastDownward = velocity > 450f
        val minFlingTravelPx = minFlingTravelPxProvider()
        val hasMeaningfulUpwardTravel = totalDrag < -minFlingTravelPx
        // Quick upward flicks on full player can be short in travel but high in intent.
        val hasQuickUpwardTravel = totalDrag < -(minFlingTravelPx * 0.35f)
        val shouldExpandFromQuickFling = isFastUpward && hasQuickUpwardTravel
        val dragThresholdPx = dragThresholdPxProvider()

        // Directional intent: a clear drag past the threshold in either direction
        // commits to that direction. This makes dismissing from the header reliable
        // (downward drag past threshold → close) while still letting upward drags
        // from the closed state open the sheet.
        val hasCommittedDownwardDrag = totalDrag > dragThresholdPx
        val hasCommittedUpwardDrag = totalDrag < -dragThresholdPx

        val shouldExpand = when {
            shouldExpandFromQuickFling -> true
            isFastUpward && hasMeaningfulUpwardTravel -> true
            isFastDownward -> false
            hasCommittedDownwardDrag -> false
            hasCommittedUpwardDrag -> true
            // Tiny residual drag: snap to whichever half the sheet ended in.
            else -> settledOffset < hiddenOffset * 0.5f
        }

        animate(shouldExpand)
    }
}
