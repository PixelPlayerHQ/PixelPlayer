package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.surfaceContainerColor

@Composable
fun CurvedVolumeIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    startAngle: Float = 132f,
    sweepAngle: Float = 96f,
    strokeWidth: Dp = 6.dp,
    inset: Dp = 9.dp,
    trackColor: Color = LocalWearPalette.current.surfaceContainerColor().copy(alpha = 0.58f),
    progressColor: Color = LocalWearPalette.current.controlContainer,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(),
        label = "curvedVolumeIndicator",
    )

    Canvas(modifier = modifier) {
        val strokeWidthPx = strokeWidth.toPx()
        val insetPx = strokeWidthPx / 2f + inset.toPx()
        val diameter = size.minDimension - (insetPx * 2f)
        val arcSize = Size(diameter, diameter)
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f,
        )

        drawArc(
            color = trackColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
        drawArc(
            color = progressColor,
            startAngle = startAngle,
            sweepAngle = sweepAngle * animatedProgress,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
        )
    }
}
