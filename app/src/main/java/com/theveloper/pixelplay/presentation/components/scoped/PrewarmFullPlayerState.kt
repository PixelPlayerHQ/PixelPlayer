package com.theveloper.pixelplay.presentation.components.scoped

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

@Composable
internal fun rememberPrewarmFullPlayer(currentSongId: String?): Boolean {
    val context = LocalContext.current

    // OPT #5: Skip prewarm entirely on low-RAM devices. Having two FullPlayerContent
    // instances in the composition tree simultaneously (even with alpha=0) doubles
    // the recomposition cost. On low-end hardware this is not worth the UX benefit.
    val isLowRamDevice = remember(context) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        activityManager.isLowRamDevice
    }

    var prewarmFullPlayer by remember { mutableStateOf(false) }

    if (isLowRamDevice) return false

    LaunchedEffect(currentSongId) {
        if (currentSongId != null) {
            prewarmFullPlayer = true
        }
    }
    LaunchedEffect(currentSongId, prewarmFullPlayer) {
        if (prewarmFullPlayer) {
            delay(32)
            prewarmFullPlayer = false
        }
    }

    return prewarmFullPlayer
}
