package com.theveloper.pixelplay.presentation.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

const val TRANSITION_DURATION = 350
private val TRANSITION_EASING = FastOutSlowInEasing

// Push: Enter from Right
fun enterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { it }
)

// Push: Exit to Left with Fade
fun exitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { -it / 3 }
) + fadeOut(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING)
)

// Pop: Enter from Left (Parallax, No Fade)
fun popEnterTransition() = slideInHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialOffsetX = { -it / 3 } // Start from Left (parallax)
) + scaleIn(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    initialScale = 0.9f // Slight zoom in for depth
)

// Pop: Exit to Right with Scale Down (No Fade)
fun popExitTransition() = slideOutHorizontally(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetOffsetX = { it }
) + scaleOut(
    animationSpec = tween(TRANSITION_DURATION, easing = TRANSITION_EASING),
    targetScale = 0.75f,
    transformOrigin = TransformOrigin(0.5f, 0.5f)
)
