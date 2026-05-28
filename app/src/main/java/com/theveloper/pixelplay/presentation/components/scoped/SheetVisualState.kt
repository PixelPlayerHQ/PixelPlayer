package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
    val baseBottomPadding: Dp,
    /** Draw-phase provider: read this inside graphicsLayer to avoid layout relayout per frame. */
    val playerContentAreaHeightPxProvider: () -> Float,
    /** Layout-phase provider: read inside .offset { } to avoid recomposition per drag frame. */
    val visualSheetTranslationYProvider: () -> Float,
    val overallSheetTopCornerRadiusProvider: () -> Dp,
    val playerContentActualBottomRadiusProvider: () -> Dp,
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
    // Compute in px to be read inside graphicsLayer (draw phase) — zero relayout per drag frame.
    val density = LocalDensity.current
    val baseBottomPadding = remember(containerHeight, sheetCollapsedTargetY, density) {
        val targetYDp = with(density) { sheetCollapsedTargetY.toDp() }
        (containerHeight - com.theveloper.pixelplay.presentation.components.MiniPlayerHeight - targetYDp)
            .coerceAtLeast(0.dp)
    }

    val currentBottomPadding = 0.dp

    val miniHeightPx = remember(density) { with(density) { com.theveloper.pixelplay.presentation.components.MiniPlayerHeight.toPx() } }
    val containerHeightPx = remember(containerHeight, density) { with(density) { containerHeight.toPx() } }
    val baseBottomPaddingPx = remember(baseBottomPadding, density) { with(density) { baseBottomPadding.toPx() } }
    val playerContentAreaHeightPxProvider: () -> Float = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        miniHeightPx,
        containerHeightPx
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                androidx.compose.ui.util.lerp(miniHeightPx, containerHeightPx, effectiveFraction)
            } else {
                0f
            }
        }
    }

    // Lambda provider: read inside .offset { } block (layout phase) — avoids recomposition
    // at ~60fps during drag gestures. The lambda captures Animatable refs and reads them at
    // layout time, same pattern as the horizontal padding providers above.
    val predictiveBackCollapseProgressState = rememberUpdatedState(predictiveBackCollapseProgress)
    val visualSheetTranslationYProvider: () -> Float = remember(
        currentSheetTranslationY,
        sheetCollapsedTargetY
    ) {
        {
            val progress = predictiveBackCollapseProgressState.value
            currentSheetTranslationY.value * (1f - progress) +
                (sheetCollapsedTargetY * progress)
        }
    }

    val overallSheetTopCornerRadiusProvider: () -> Dp = remember(
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        navBarStyle,
        navBarCornerRadiusDp,
        isNavBarHidden,
        swipeDismissProgress,
        currentSheetContentState
    ) {
        {
            val collapsedCornerTarget = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                navBarCornerRadiusDp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val expandedTarget = 0.dp
            val calculatedNormally = if (showPlayerContentArea) {
                lerp(collapsedCornerTarget, expandedTarget, effectiveFraction)
            } else {
                if (navBarStyle == NavBarStyle.DEFAULT) {
                    navBarCornerRadiusDp
                } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                    0.dp
                } else if (isNavBarHidden) {
                    60.dp
                } else {
                    navBarCornerRadiusDp
                }
            }

            calculatedNormally
        }
    }

    // isPlaying and hasCurrentSong are only used in the fallback branch when
    // !showPlayerContentArea. Reading them via rememberUpdatedState keeps the
    // shape provider lambda stable across play/pause toggles — so the
    // PlayerSheetDynamicShape instance (and the modifier chain that consumes it)
    // is not recreated on every isPlaying flip.
    val isPlayingState = rememberUpdatedState(isPlaying)
    val hasCurrentSongState = rememberUpdatedState(hasCurrentSong)
    val playerContentActualBottomRadiusProvider: () -> Dp = remember(
        navBarStyle,
        showPlayerContentArea,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress,
        swipeDismissProgress,
        isNavBarHidden,
        navBarCornerRadiusDp,
        currentSheetContentState
    ) {
        {
            val collapsedRadius = if (isNavBarHidden) {
                32.dp
            } else if (navBarStyle == NavBarStyle.DEFAULT) {
                10.dp
            } else if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                32.dp
            } else {
                navBarCornerRadiusDp
            }

            val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
            val calculatedNormally =
                if (showPlayerContentArea) {
                    val expandedTarget = 0.dp
                    lerp(collapsedRadius, expandedTarget, effectiveFraction)
                } else {
                    if (!isPlayingState.value || !hasCurrentSongState.value) {
                        if (isNavBarHidden) {
                            32.dp
                        } else if (navBarStyle == NavBarStyle.DEFAULT) {
                            10.dp
                        } else {
                            navBarCornerRadiusDp
                        }
                    } else {
                        collapsedRadius
                    }
                }

            if (isNavBarHidden) {
                calculatedNormally
            } else if (currentSheetContentState == PlayerSheetState.COLLAPSED &&
                swipeDismissProgress > 0f &&
                showPlayerContentArea &&
                playerContentExpansionFraction.value < 0.01f
            ) {
                if (navBarStyle == NavBarStyle.DEFAULT) {
                    lerp(10.dp, navBarCornerRadiusDp, swipeDismissProgress)
                } else {
                    val baseCollapsedRadius = if (isNavBarHidden) 32.dp else navBarCornerRadiusDp
                    lerp(baseCollapsedRadius, navBarCornerRadiusDp, swipeDismissProgress)
                }
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
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, effectiveFraction)
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    val currentHorizontalPaddingEndPxProvider: () -> Float = remember(
        showPlayerContentArea,
        collapsedStateHorizontalPaddingPx,
        playerContentExpansionFraction,
        predictiveBackCollapseProgress
    ) {
        {
            if (showPlayerContentArea) {
                val effectiveFraction = playerContentExpansionFraction.value * (1f - predictiveBackCollapseProgress)
                androidx.compose.ui.util.lerp(collapsedStateHorizontalPaddingPx, 0f, effectiveFraction)
            } else {
                collapsedStateHorizontalPaddingPx
            }
        }
    }

    return SheetVisualState(
        currentBottomPadding = currentBottomPadding,
        baseBottomPadding = baseBottomPadding,
        playerContentAreaHeightPxProvider = playerContentAreaHeightPxProvider,
        visualSheetTranslationYProvider = visualSheetTranslationYProvider,
        overallSheetTopCornerRadiusProvider = overallSheetTopCornerRadiusProvider,
        playerContentActualBottomRadiusProvider = playerContentActualBottomRadiusProvider,
        currentHorizontalPaddingStartPxProvider = currentHorizontalPaddingStartPxProvider,
        currentHorizontalPaddingEndPxProvider = currentHorizontalPaddingEndPxProvider
    )
}
