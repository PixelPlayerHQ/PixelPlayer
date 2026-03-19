package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.theveloper.pixelplay.presentation.viewmodel.PlayerSheetState
import kotlinx.coroutines.CoroutineScope
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

internal data class SheetInteractionState(
    val playerShadowShape: Shape,
    val sheetVerticalDragGestureHandler: SheetVerticalDragGestureHandler,
    val canDragSheet: Boolean
)

@Composable
internal fun rememberSheetInteractionState(
    scope: CoroutineScope,
    velocityTracker: VelocityTracker,
    sheetMotionController: SheetMotionController,
    playerContentExpansionFraction: Animatable<Float, AnimationVector1D>,
    currentSheetTranslationY: Animatable<Float, AnimationVector1D>,
    visualOvershootScaleY: Animatable<Float, AnimationVector1D>,
    sheetCollapsedTargetY: Float,
    sheetExpandedTargetY: Float,
    miniPlayerContentHeightPx: Float,
    currentSheetContentState: PlayerSheetState,
    showPlayerContentArea: Boolean,
    overallSheetTopCornerRadius: Dp,
    playerContentActualBottomRadius: Dp,
    useSmoothCorners: Boolean,
    isDragging: Boolean,
    onAnimateSheet: suspend (
        targetExpanded: Boolean,
        animationSpec: AnimationSpec<Float>?,
        initialVelocity: Float
    ) -> Unit,
    onExpandSheetState: () -> Unit,
    onCollapseSheetState: () -> Unit,
    onDraggingChange: (Boolean) -> Unit,
    onDraggingPlayerAreaChange: (Boolean) -> Unit
): SheetInteractionState {
    val useSmoothShape by remember(useSmoothCorners, isDragging, playerContentExpansionFraction.isRunning) {
        derivedStateOf {
            useSmoothCorners && !isDragging && !playerContentExpansionFraction.isRunning
        }
    }

    // ── OPT #3: Freeze shape during animation / drag ─────────────────────────────
    // The radii change continuously at ~60fps during expand/collapse animations,
    // which would cause `remember(...)` to create a new Shape on every frame.
    // Instead, we only let `remember` invalidate when the sheet is *settled*.
    // During motion the previously captured shape is reused — the visual change
    // is imperceptible over the 300ms animation window.
    val isAnimatingOrDragging by remember(playerContentExpansionFraction, isDragging) {
        derivedStateOf { playerContentExpansionFraction.isRunning || isDragging }
    }

    val playerShadowShape = remember(
        overallSheetTopCornerRadius,
        playerContentActualBottomRadius,
        useSmoothShape,
        isAnimatingOrDragging
    ) {
        // Use fast RoundedCornerShape during animation; smooth only when settled.
        val effectiveSmooth = useSmoothShape && !isAnimatingOrDragging
        if (effectiveSmooth) {
            AbsoluteSmoothCornerShape(
                cornerRadiusTL = overallSheetTopCornerRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusTR = overallSheetTopCornerRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = playerContentActualBottomRadius,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = playerContentActualBottomRadius,
                smoothnessAsPercentTR = 60
            )
        } else {
            RoundedCornerShape(
                topStart = overallSheetTopCornerRadius,
                topEnd = overallSheetTopCornerRadius,
                bottomStart = playerContentActualBottomRadius,
                bottomEnd = playerContentActualBottomRadius
            )
        }
    }


    val collapsedYState = rememberUpdatedState(sheetCollapsedTargetY)
    val expandedYState = rememberUpdatedState(sheetExpandedTargetY)
    val miniHeightState = rememberUpdatedState(miniPlayerContentHeightPx)
    val densityState = rememberUpdatedState(LocalDensity.current)
    val currentSheetState = rememberUpdatedState(currentSheetContentState)
    val onAnimateSheetState = rememberUpdatedState(onAnimateSheet)
    val onExpandSheetStateState = rememberUpdatedState(onExpandSheetState)
    val onCollapseSheetStateState = rememberUpdatedState(onCollapseSheetState)
    val onDraggingChangeState = rememberUpdatedState(onDraggingChange)
    val onDraggingPlayerAreaChangeState = rememberUpdatedState(onDraggingPlayerAreaChange)

    val sheetVerticalDragGestureHandler = remember(
        scope,
        velocityTracker,
        sheetMotionController,
        playerContentExpansionFraction,
        currentSheetTranslationY,
        visualOvershootScaleY
    ) {
        SheetVerticalDragGestureHandler(
            scope = scope,
            velocityTracker = velocityTracker,
            densityProvider = { densityState.value },
            sheetMotionController = sheetMotionController,
            playerContentExpansionFraction = playerContentExpansionFraction,
            currentSheetTranslationY = currentSheetTranslationY,
            expandedYProvider = { expandedYState.value },
            collapsedYProvider = { collapsedYState.value },
            miniHeightPxProvider = { miniHeightState.value },
            currentSheetStateProvider = { currentSheetState.value },
            visualOvershootScaleY = visualOvershootScaleY,
            onDraggingChange = { onDraggingChangeState.value(it) },
            onDraggingPlayerAreaChange = { onDraggingPlayerAreaChangeState.value(it) },
            onAnimateSheet = { targetExpanded, animationSpec, initialVelocity ->
                onAnimateSheetState.value(targetExpanded, animationSpec, initialVelocity)
            },
            onExpandSheetState = { onExpandSheetStateState.value() },
            onCollapseSheetState = { onCollapseSheetStateState.value() }
        )
    }

    return SheetInteractionState(
        playerShadowShape = playerShadowShape,
        sheetVerticalDragGestureHandler = sheetVerticalDragGestureHandler,
        canDragSheet = showPlayerContentArea
    )
}
