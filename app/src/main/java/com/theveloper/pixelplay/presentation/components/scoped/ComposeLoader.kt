package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow

// ------------------------------------------------------------
// 1) Phase loader: compose a subtree only after a threshold, then keep it alive
// ------------------------------------------------------------
@Composable
fun DeferAt(
    expansionFraction: Float,
    threshold: Float,
    keepAliveKey: Any? = "default",
    content: @Composable () -> Unit
) {
    var ready by rememberSaveable(keepAliveKey) { mutableStateOf(false) }
    LaunchedEffect(expansionFraction) {
        if (!ready && expansionFraction >= threshold) ready = true
    }
    if (ready) content()
}


@Composable
fun DeferUntil(
    condition: Boolean,
    keepAliveKey: Any? = "default",
    content: @Composable () -> Unit
) {
    var ready by rememberSaveable(keepAliveKey) { mutableStateOf(false) }
    LaunchedEffect(condition) { if (condition) ready = true }
    if (ready) content()
}

// ------------------------------------------------------------
// 2) Progress sampler for long-running sliders/meters
// Emits coarse progress snapshots; visual interpolation is handled in the draw phase
// by the specific component that renders the slider/progress.
// ------------------------------------------------------------
@Composable
fun rememberSmoothProgress(
    isPlayingProvider: () -> Boolean,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    sampleWhilePlayingMs: Long = 200L,
    sampleWhilePausedMs: Long = 800L,
    isVisible: Boolean = true
): Pair<androidx.compose.runtime.State<Float>, androidx.compose.runtime.State<Long>> {
    var sampledPosition by remember { mutableLongStateOf(0L) }
    var sampledFraction by remember { mutableFloatStateOf(0f) }

    val latestPositionProvider by rememberUpdatedState(newValue = currentPositionProvider)
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    // Read these inside the loop so the LaunchedEffect doesn't restart every time
    // they change. With the previous keying scheme, crossing the expansion threshold
    // (which flips `isVisible` at 0.01 and the playing sample rate at 0.995) would
    // cancel and relaunch this coroutine mid-gesture — the new fresh coroutine
    // immediately allocated a new Job + had to re-issue its first `sampleNow` and
    // `delay`, which contributed to the post-interaction gesture lag.
    val latestSampleWhilePlayingMs by rememberUpdatedState(sampleWhilePlayingMs)
    val latestSampleWhilePausedMs by rememberUpdatedState(sampleWhilePausedMs)
    val latestIsVisible by rememberUpdatedState(isVisible)

    val safeUpperBound = totalDuration.coerceAtLeast(0L)
    val safeDuration = totalDuration.coerceAtLeast(1L)

    LaunchedEffect(totalDuration) {
        fun sampleNow() {
            val rawPosition = latestPositionProvider()
            val clampedPosition = rawPosition.coerceIn(0L, safeUpperBound)
            sampledPosition = clampedPosition
            sampledFraction = (clampedPosition / safeDuration.toFloat()).coerceIn(0f, 1f)
        }

        sampleNow()

        while (isActive) {
            val isVisible = latestIsVisible
            val isPlaying = latestIsPlayingProvider()

            if (!isVisible || !isPlaying) {
                val initialPos = latestPositionProvider()
                snapshotFlow {
                    latestIsVisible && (latestIsPlayingProvider() || latestPositionProvider() != initialPos)
                }.first { it }

                sampleNow()
                if (!latestIsVisible || !latestIsPlayingProvider()) {
                    continue
                }
            }

            val delayMillis = latestSampleWhilePlayingMs
            delay(delayMillis.coerceAtLeast(1L))
            sampleNow()
        }
    }

    val fractionState = remember {
        derivedStateOf { sampledFraction }
    }

    val displayedPositionState = remember(totalDuration) {
        derivedStateOf {
            sampledPosition.coerceIn(0L, totalDuration.coerceAtLeast(0L))
        }
    }

    return fractionState to displayedPositionState
}
