package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.graphics.TransformOrigin

// MD3 Expressive – Emphasized easing (matches Material Motion spec)
// cubic-bezier(0.2, 0, 0, 1.0) — fast start, smooth settle
private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// Decelerate for elements entering the screen — mirror of EmphasizedAccelerateEasing
// so the entry feels as weighty/grounded as the pop-exit
private val EmphasizedDecelerateEasing = CubicBezierEasing(0.2f, 0.85f, 0.7f, 1f)

// Accelerate for elements leaving the screen
private val EmphasizedAccelerateEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Base duration designed for 1x animation scale — looks good at full speed,
// still smooth at 0.5x (system halves it to ~225 ms).
const val TRANSITION_DURATION = 450

// Push: Enter from Right — slides in 50% of screen width + slight scale up (mirrors popExit's weight)
// Fade uses an accelerate-style curve so alpha stays low while slide does its work,
// then catches up at the end — prevents the "fade arrives before slide" perceptual mismatch.
fun enterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { (it * 0.5f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing)
)

// Push: Exit to Left — recedes 25% (parallax, barely moves)
fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { -(it * 0.25f).toInt() }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)

// Pop: Enter from Left — parallax slide-in 25% + subtle scale up
fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialOffsetX = { -(it * 0.25f).toInt() }
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedDecelerateEasing),
    initialScale = 0.95f
) + fadeIn(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedDecelerateEasing)
)

// Pop: Exit to Right — slides out 50% + slight scale down
fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetOffsetX = { (it * 0.5f).toInt() }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = EmphasizedAccelerateEasing),
    targetScale = 0.92f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION / 2, easing = EmphasizedAccelerateEasing)
)
