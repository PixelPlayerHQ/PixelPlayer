package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun PlayingEqIcon(
    modifier: Modifier = Modifier,
    color: Color,
    isPlaying: Boolean = true,
    bars: Int = 3,
    minHeightFraction: Float = 0.28f,
    maxHeightFraction: Float = 1.0f,
    // Slower cycles mean fewer animation frames / Canvas redraws per second while the icon
    // is visible. With many current-song indicators potentially on screen (home, queue,
    // lyrics sheet), this noticeably lowers screen-on CPU on weaker devices.
    phaseDurationMillis: Int = 3600,   // ciclo más lento
    wanderDurationMillis: Int = 12000, // patrón más largo
    gapFraction: Float = 0.30f
) {
    val fullRotation = (2f * PI).toFloat()
    val phaseAnim = remember { Animatable(0f) }
    val wanderAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying, phaseDurationMillis) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            val start = (phaseAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
            phaseAnim.snapTo(start)
            phaseAnim.animateTo(
                targetValue = start + fullRotation,
                animationSpec = tween(durationMillis = phaseDurationMillis, easing = LinearEasing)
            )
        }
    }

    LaunchedEffect(isPlaying, wanderDurationMillis) {
        if (!isPlaying) return@LaunchedEffect
        while (isActive) {
            val start = (wanderAnim.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
            wanderAnim.snapTo(start)
            wanderAnim.animateTo(
                targetValue = start + fullRotation,
                animationSpec = tween(durationMillis = wanderDurationMillis, easing = LinearEasing)
            )
        }
    }

    // Factor de actividad: 1 = barras, 0 = puntitos (morph suave)
    val activity by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "activity"
    )

    // Velocidades ENTERAS → continuidad perfecta en wrap 2π
    val speeds = remember(bars) { List(bars) { (it + 1).toFloat() } } // 1f, 2f, 3f
    val shifts = remember(bars) { List(bars) { i -> i * 0.9f } }

    Canvas(modifier = modifier) {
        val phase = phaseAnim.value
        val wander = wanderAnim.value
        val w = size.width
        val h = size.height

        // Layout barras
        val tentativeBarW = w / (bars + (bars - 1) * (1f + gapFraction))
        val gap = tentativeBarW * gapFraction
        val barW = tentativeBarW
        val corner = CornerRadius(barW / 2f, barW / 2f)

        repeat(bars) { i ->
            // 「Respiración」 lenta para que el patrón dure más
            val slowShift = 0.6f * sin(wander + i * 0.4f)
            val slowAmp   = 0.85f + 0.15f * sin(wander * 0.5f + 1.1f + i * 0.3f)

            // Señal principal continua (sin saltos)
            val v = (sin(phase * speeds[i] + shifts[i] + slowShift) * slowAmp + 1f) * 0.5f

            // Suavizado tipo smoothstep
            val eased = v * v * (3 - 2 * v)

            // Altura 「viva」 (modo barras)
            val fracBars = minHeightFraction + (maxHeightFraction - minHeightFraction) * eased
            val barH = h * fracBars

            // Altura 「punto」 (círculo → alto = ancho)
            val dotH = barW

            // Morph: puntito ⇄ barra (sin importar el frame)
            val blendedH = dotH + (barH - dotH) * activity

            val top = (h - blendedH) / 2f
            val left = i * (barW + gap)

            drawRoundRect(
                color = color,
                topLeft = Offset(left, top),
                size = Size(barW, blendedH),
                cornerRadius = corner
            )
        }
    }
}
