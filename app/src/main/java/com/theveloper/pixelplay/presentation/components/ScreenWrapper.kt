package com.theveloper.pixelplay.presentation.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.currentBackStackEntryAsState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import androidx.lifecycle.compose.currentStateAsState


@OptIn(UnstableApi::class)
@Composable
fun ScreenWrapper(
    navController: androidx.navigation.NavController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Lifecycle State
    var isResumed by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Initial Check
    val currentState = lifecycleOwner.lifecycle.currentStateAsState().value
    if (currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        isResumed = true
    }

    // Visible entries is the public Navigation API designed for transition-aware stacking.
    // It stays stable while entries are entering / exiting, unlike the restricted currentBackStack.
    val visibleEntries by navController.visibleEntries.collectAsStateWithLifecycle()
    val myEntry = lifecycleOwner as? androidx.navigation.NavBackStackEntry
    val myIndex = visibleEntries.indexOfFirst { it.id == myEntry?.id }
    val topIndex = visibleEntries.indexOfLast {
        it.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }

    // currentBackStackEntry updates synchronously with navigate()/popBackStack(), so it
    // identifies the destination the user is moving TO. The incoming screen during a pop
    // shares STARTED state with the outgoing one for a few frames; without this check the
    // dim overlay would flash onto the screen the user is navigating back to.
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val isNavigationTarget = myEntry != null && currentBackStackEntry?.id == myEntry.id

    // Dim Logic:
    // If I am BACKGROUND (myIndex < topIndex) -> Dim.
    // If I am TOP (myIndex == topIndex) -> Clear.
    // If I am EXITING (myIndex > topIndex, effectively in front during pop) -> Clear.
    // If I am the navigation target (incoming during a pop) -> Clear.
    // Created entries are on their way out, so we keep them clear instead of dimming them for a frame.
    val shouldDim = remember(visibleEntries, myEntry, myIndex, topIndex, isNavigationTarget) {
        !isNavigationTarget &&
            myIndex != -1 &&
            topIndex != -1 &&
            myIndex < topIndex &&
            myEntry?.lifecycle?.currentState != Lifecycle.State.CREATED
    }

    // Declarative Animations
    // Radius: If NOT Resumed -> 32dp. (Background OR Popped)
    val targetRadius = if (isResumed) 0f else 32f
    val cornerRadius by animateFloatAsState(
        targetValue = targetRadius,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "cornerRadius"
    )

    // Dim: If strictly behind Top -> 0.4f. Else -> 0f.
    val targetDim = if (shouldDim) 0.4f else 0f
    val dimAlpha by animateFloatAsState(
        targetValue = targetDim,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "dimAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            // Keep both the graphicsLayer modifier AND its compositingStrategy stable across
            // the full lifecycle of the screen. Toggling the strategy between Auto and
            // Offscreen mid-transition (when cornerRadius crosses the threshold) causes the
            // RenderNode's rendering mode to flip for one frame, producing a subtle flash on
            // the outgoing screen right as the animation starts. Only shape/clip are toggled.
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                if (cornerRadius > 0.5f) {
                    this.shape = RoundedCornerShape(cornerRadius.dp)
                    this.clip = true
                } else {
                    this.clip = false
                }
            }
            .background(MaterialTheme.colorScheme.background)
    ) {
        content()

        // Dim Layer Overlay
        // Always composed with alpha-driven visibility instead of a conditional node.
        // Conditionally adding/removing this Box when dimAlpha crosses 0 added a node to
        // the composition tree mid-transition and contributed to the outgoing-screen flash.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dimAlpha }
                .background(Color.Black)
        )
    }
}
