package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState

private const val PREDICTIVE_BACK_SWIPE_EDGE_LEFT = 0
private const val PREDICTIVE_BACK_SWIPE_EDGE_RIGHT = 1

internal data class SheetVisualState(
    val currentBottomPadding: Dp,
    /** Draw-phase provider: read this inside graphicsLayer to avoid layout relayout per frame. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Layout-phase provider: read inside .offset { } to avoid recomposition per drag frame. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadius: Dp,
    val playerContentActualBottomRadius: Dp,
    /** Draw-phase providers: read inside graphicsLayer to avoid layout relayout per frame. */
    val currentHorizontalPaddingStartPxProvider: () -> Float,
    val currentHorizontalPaddingEndPxProvider: () -> Float
)

@Composable
internal fun rememberSheetVisualState(
    showPlayerContentArea: Boolean,
    collapsedStateHorizontalPadding: Dp,
    predictiveBackCollapseProgress: Float,
    predictiveBackSwipeEdge: Int?,
    currentSheetContentState: PlayerSheetState,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    containerHeight: Dp,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    navBarStyle: String,
    navBarCornerRadiusDp: Dp,
    isNavBarHidden: Boolean,
    isPlaying: Boolean,
    hasCurrentSong: Boolean,
    swipeDismissProgress: Float
): SheetVisualState {
    val currentBottomPadding by remember(
        showPlayerContentArea,
        collapsedStateHorizontalPadding,
        predictiveBackCollapseProgress,
        currentSheetContentState
    ) {
        derivedStateOf {
            if (predictiveBackCollapseProgress > 0f &&
                showPlayerContentArea &&
                currentSheetContentState == PlayerSheetState.EXPANDED
            ) {
                lerp(0.dp, collapsedStateHorizontalPadding, predictiveBackCollapseProgress)
            } else {
                0.dp
            }
        }
    }

    // Compute in px to be read inside graphicsLayer (draw phase) — zero relayout per drag frame.
    val density = LocalDensity.current
    val miniHeightPx = remember(density) { with(density) { com.theveloper.pixelplay.presentation.components.MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        miniHeightPx,
        containerHeightPx
    ) {
        {
            if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, playerContentExpansionFraction.value)
            } else {
                0f
            }
        }
    }

    // Lambda provider: read inside .offset { } block (layout phase) — avoids recomposition
    // at ~60fps during drag gestures. The lambda captures Animatable refs and reads them at
    // layout time, same pattern as the horizontal padding providers above.
    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY
    ) {
        {
            currentSheetTranslationY.value * (1f - predictiveBackCollapseProgress) +
                (sheetCollapsedTargetY * predictiveBackCollapseProgress)
        }
    }

    val overallSheetTopCornerRadius by remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        currentSheetContentState,
        navBarStyle,
        navBarCornerRadiusDp,
        isNavBarHidden
    ) {
        derivedStateOf {
            if (showPlayerContentArea) {
                val collapsedCornerTarget = if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    32.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }

                if (predictiveBackCollapseProgress > 0f &&
                    currentSheetContentState == PlayerSheetState.EXPANDED
                ) {
                    val expandedCorner = 0.dp
                    lerp(expandedCorner, collapsedCornerTarget, predictiveBackCollapseProgress)
                } else {
                    val fraction = playerContentExpansionFraction.value
                    val expandedTarget = 0.dp
                    lerp(collapsedCornerTarget, expandedTarget, fraction)
                }
            } else {
                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }
            }
        }
    }

    val playerContentActualBottomRadius by remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        isPlaying,
        hasCurrentSong,
        predictiveBackCollapseProgress,
        currentSheetContentState,
        swipeDismissProgress,
        isNavBarHidden,
        navBarCornerRadiusDp
    ) {
        derivedStateOf {
            if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                val fraction = playerContentExpansionFraction.value
                return@derivedStateOf lerp(32.dp, 0.dp, fraction)
            }

            val calculatedNormally =
                if (predictiveBackCollapseProgress > 0f &&
                    showPlayerContentArea &&
                    currentSheetContentState == PlayerSheetState.EXPANDED
                ) {
                    val expandedRadius = 0.dp
                    val collapsedRadiusTarget = if (isNavBarHidden) 60.dp else 12.dp
                    lerp(expandedRadius, collapsedRadiusTarget, predictiveBackCollapseProgress)
                } else {
                    if (showPlayerContentArea) {
                        val fraction = playerContentExpansionFraction.value
                        val collapsedRadius = if (isNavBarHidden) 60.dp else 12.dp
                        if (fraction < 0.2f) {
                            lerp(collapsedRadius, 26.dp, (fraction / 0.2f).coerceIn(0f, 1f))
                        } else {
                            lerp(26.dp, 0.dp, ((fraction - 0.2f) / 0.8f).coerceIn(0f, 1f))
                        }
                    } else {
                        if (!isPlaying || !hasCurrentSong) {
                            if (isNavBarHidden) 32.dp else navBarCornerRadiusDp
                        } else {
                            if (isNavBarHidden) 32.dp else 12.dp
                        }
                    }
                }

            if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                val baseCollapsedRadius = if (isNavBarHidden) 32.dp else 12.dp
                lerp(baseCollapsedRadius, navBarCornerRadiusDp, swipeDismissProgress)
            } else {
                calculatedNormally
            }
        }
    }

    val actualCollapsedStateHorizontalPadding =
        if (navBarStyle == NavBarStyle.FULL_WIDTH) 14.dp else collapsedStateHorizontalPadding
    val collapsedStateHorizontalPaddingPx = remember(actualCollapsedStateHorizontalPadding, density) {
        with(density) { actualCollapsedStateHorizontalPadding.toPx() }
    }

    // Draw-phase lambda providers for horizontal padding — read inside graphicsLayer to avoid
    // per-frame relayout. The lambda captures Animatable/Float refs and reads them at draw time.
    val currentHorizontalPaddingStartPxProvider: () -> Float = remember(
        showPlayerContentArea,
        currentSheetContentState,
        predictiveBackCollapseProgress,
        predictiveBackSwipeEdge,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction
    ) {
        {
            val currentPadding = if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
            } else {
                collapsedStateHorizontalPaddingPx
            }
            if (predictiveBackCollapseProgress > 0f &&
                showPlayerContentArea &&
                currentSheetContentState == PlayerSheetState.EXPANDED
            ) {
                val gestureSidePaddingPx = androidx.compose.ui.util.lerp(0f, collapsedStateHorizontalPaddingPx, predictiveBackCollapseProgress)
                when (predictiveBackSwipeEdge) {
                    PREDICTIVE_BACK_SWIPE_EDGE_LEFT -> gestureSidePaddingPx
                    PREDICTIVE_BACK_SWIPE_EDGE_RIGHT -> 0f
                    else -> currentPadding
                }
            } else {
                currentPadding
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        currentSheetContentState,
        predictiveBackCollapseProgress,
        predictiveBackSwipeEdge,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction
    ) {
        {
            val currentPadding = if (showPlayerContentArea) {
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, playerContentExpansionFraction.value)
            } else {
                collapsedStateHorizontalPaddingPx
            }
            if (predictiveBackCollapseProgress > 0f &&
                showPlayerContentArea &&
                currentSheetContentState == PlayerSheetState.EXPANDED
            ) {
                val gestureSidePaddingPx = androidx.compose.ui.util.lerp(0f, collapsedStateHorizontalPaddingPx, predictiveBackCollapseProgress)
                when (predictiveBackSwipeEdge) {
                    PREDICTIVE_BACK_SWIPE_EDGE_LEFT -> 0f
                    PREDICTIVE_BACK_SWIPE_EDGE_RIGHT -> gestureSidePaddingPx
                    else -> currentPadding
                }
            } else {
                currentPadding
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadius = overallSheetTopCornerRadius,
        playerContentActualBottomRadius = playerContentActualBottomRadius,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}
