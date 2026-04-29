package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AutoScrollingTextOnDemand(
    text: String,
    style: TextStyle,
    gradientEdgeColor: Color,
    expansionFractionProvider: () -> Float,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    startDelayAfterExpandedMillis: Long = 0L
) {
    var overflow by remember { mutableStateOf(false) }
    var expansionReady by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, expansionFractionProvider, startDelayAfterExpandedMillis) {
        if (!enabled) {
            expansionReady = false
            return@LaunchedEffect
        }

        snapshotFlow { expansionFractionProvider() > 0.99f }
            .collectLatest { ready ->
                if (!ready) {
                    expansionReady = false
                } else {
                    if (startDelayAfterExpandedMillis > 0L) {
                        delay(startDelayAfterExpandedMillis)
                    }
                    expansionReady = true
                }
            }
    }

    val canStart by remember {
        derivedStateOf { enabled && expansionReady && overflow }
    }


// Usamos un Text "medidor" sólo la primera composición para detectar overflow.
    if (!canStart) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            onTextLayout = { res: TextLayoutResult -> overflow = res.hasVisualOverflow },
            modifier = modifier
        )
    } else {
        AutoScrollingText(
            text = text,
            style = style,
            textAlign = TextAlign.Start,
            gradientEdgeColor = gradientEdgeColor,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AutoScrollingText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    textAlign: TextAlign? = null,
    gradientEdgeColor: Color,
    gradientWidth: Dp = 24.dp
) {
    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val textPlaceable = subcompose("text") {
            Text(text = text, style = style, maxLines = 1)
        }[0].measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        val isOverflowing = textPlaceable.width > constraints.maxWidth

        val content = @Composable {
            if (isOverflowing) {
                val initialDelayMillis = 1500
                val fadeAnimationDuration = 500

                var isScrolling by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    isScrolling = false // Ensure initial state
                    kotlinx.coroutines.delay(initialDelayMillis.toLong())
                    isScrolling = true
                }

                val animatedLeftGradientStartColor by animateColorAsState(
                    targetValue = if (isScrolling) Color.Transparent else gradientEdgeColor,
                    animationSpec = tween(durationMillis = fadeAnimationDuration),
                    label = "LeftGradientStartColor"
                )

                Box(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            val gradientWidthPx = gradientWidth.toPx()

                            // Left fade-in: Animates its color from opaque to transparent
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(animatedLeftGradientStartColor, gradientEdgeColor),
                                    startX = 0f,
                                    endX = gradientWidthPx
                                ),
                                blendMode = BlendMode.DstIn
                            )
                            // Right fade-out: Always visible for overflow
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(gradientEdgeColor, Color.Transparent),
                                    startX = size.width - gradientWidthPx,
                                    endX = size.width
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                ) {
                    Text(
                        text = text,
                        style = style,
                        textAlign = textAlign,
                        maxLines = 1,
                        modifier = Modifier.basicMarquee(
                            iterations = Int.MAX_VALUE,
                            spacing = MarqueeSpacing(gradientWidth + 6.dp),
                            velocity = 25.dp,
                            initialDelayMillis = initialDelayMillis
                        )
                    )
                }
            } else {
                Text(
                    text = text,
                    style = style,
                    textAlign = textAlign,
                    maxLines = 1,
                )
            }
        }

        val contentPlaceable = subcompose("content", content)[0].measure(constraints)
        val targetWidth = constraints.maxWidth.takeIf { it != Constraints.Infinity } ?: contentPlaceable.width

        layout(targetWidth, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
        }
    }
}
