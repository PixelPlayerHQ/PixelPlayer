package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp

@Composable
fun ExpressiveTopBarContent(
    title: String,
    collapseFraction: Float,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    collapsedTitleStartPadding: Dp = 56.dp, // Default safe for standard Nav Icon
    expandedTitleStartPadding: Dp = 16.dp,
    collapsedTitleEndPadding: Dp = 24.dp,
    expandedTitleEndPadding: Dp = 24.dp,
    containerHeightRange: Pair<Dp, Dp> = 88.dp to 56.dp,
    collapsedTitleVerticalBias: Float = -1f,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    titleScaleRange: Pair<Float, Float> = 1.2f to 0.8f,
    titleFontSizeRange: Pair<TextUnit, TextUnit>? = null,
    maxLines: Int = 2,
    collapsedSubtitleMaxLines: Int = 1,
    expandedSubtitleMaxLines: Int = 1,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    subtitleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fadeSubtitleOnCollapse: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null
) {
    val clampedFraction = collapseFraction.coerceIn(0f, 1f)
    val titleScale = lerp(titleScaleRange.first, titleScaleRange.second, clampedFraction)
    val titlePaddingStart = lerp(expandedTitleStartPadding, collapsedTitleStartPadding, clampedFraction)
    val titlePaddingEnd = lerp(expandedTitleEndPadding, collapsedTitleEndPadding, clampedFraction)
    val titleVerticalBias = lerp(1f, collapsedTitleVerticalBias, clampedFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(containerHeightRange.first, containerHeightRange.second, clampedFraction)
    val subtitleAlpha = if (fadeSubtitleOnCollapse) 1f - clampedFraction else 1f
    val subtitleMaxLines = if (clampedFraction < 0.5f) expandedSubtitleMaxLines else collapsedSubtitleMaxLines
    val titleFontSize = (titleFontSizeRange?.let { lerp(it.first, it.second, clampedFraction) } ?: titleStyle.fontSize) * titleScale

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(animatedTitleAlignment)
                .height(titleContainerHeight)
                .fillMaxWidth()
                .padding(start = titlePaddingStart, end = titlePaddingEnd)
        ) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Text(
                    text = title,
                    style = titleStyle.copy(fontSize = titleFontSize),
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    maxLines = maxLines,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    lineHeight = titleFontSize * 1.1f,
                    modifier = Modifier.graphicsLayer {
                        // Removed scaleX/scaleY scaling from graphicsLayer to allow proper ellipsis during layout.
                        // Scaling font size directly ensures Text component is measured with correct constraints.
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) // Scale from left center
                    }
                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = subtitleColor,
                        maxLines = subtitleMaxLines,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.alpha(subtitleAlpha)
                    )
                }
                if (supportingContent != null) {
                    Box(modifier = Modifier.alpha(1f - clampedFraction)) {
                        supportingContent()
                    }
                }
            }
        }
    }
}
