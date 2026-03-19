package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun TabAnimation(
    modifier: Modifier = Modifier,
    index: Int,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedColor: Color = MaterialTheme.colorScheme.surface,
    onUnselectedColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
    title: String,
    selectedIndex: Int,
    onClick: () -> Unit,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val isSelected = index == selectedIndex
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    var hasAnimatedSelectionChange by remember { mutableStateOf(false) }

    val animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) selectedColor else unselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Background Color"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) onSelectedColor else onUnselectedColor,
        animationSpec = tween(durationMillis = 200),
        label = "Tab Content Color"
    )

    // Animate only on actual selection changes, not on the first composition.
    LaunchedEffect(selectedIndex) {
        if (!hasAnimatedSelectionChange) {
            hasAnimatedSelectionChange = true
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }

        if (isSelected) {
            launch {
                scale.animateTo(1.05f, animationSpec = animationSpec)
                scale.animateTo(1f, animationSpec = animationSpec)
            }
        } else {
            scale.snapTo(1f)
        }

        if (!isSelected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) { // Only affect direct neighbors
                val direction = if (distance > 0) 1 else -1
                // Move neighbors slightly
                val offsetValue = 12f * direction
                launch {
                    offsetX.animateTo(offsetValue, animationSpec = animationSpec)
                    offsetX.animateTo(0f, animationSpec = animationSpec)
                }
            } else {
                // Instantly reset offset for non-neighbor tabs
                offsetX.snapTo(0f)
            }
        } else {
            // Ensure the selected tab itself has no offset
            offsetX.snapTo(0f)
        }
    }

    Tab(
        modifier = modifier
            .padding(all = 5.dp)
            .graphicsLayer {
                scaleX = scale.value
                translationX = offsetX.value
                this.transformOrigin = transformOrigin
            }
            .clip(CircleShape)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(50)
            ),
        selected = isSelected,
        text = content,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        selectedContentColor = contentColor,
        unselectedContentColor = contentColor
    )
}
