package com.theveloper.pixelplay.presentation.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.presentation.components.brickbreaker.BrickBreakerOverlay
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

@Composable
fun EasterEggScreen(
    viewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
) {
    val context = LocalContext.current
    var isVisible by rememberSaveable { mutableStateOf(false) }
    var hasShownFanToast by rememberSaveable { mutableStateOf(false) }

    val stablePlayerState by viewModel.stablePlayerState.collectAsStateWithLifecycle()
    val currentSong = stablePlayerState.currentSong

    LaunchedEffect(Unit) {
        isVisible = true
    }

    LaunchedEffect(hasShownFanToast) {
        if (hasShownFanToast) return@LaunchedEffect
        Toast.makeText(context, "Thank you for using PixelPlayer!", Toast.LENGTH_SHORT).show()
        hasShownFanToast = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(durationMillis = 260)) +
            scaleIn(
                initialScale = 0.97f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            ) +
            slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight / 10 },
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            ),
    ) {
        BrickBreakerOverlay(
            isMiniPlayerVisible = currentSong != null,
            onPlayRandom = { viewModel.playRandomSong() },
            onClose = {
                isVisible = false
                onNavigationIconClick()
            },
        )
    }
}
