package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import com.theveloper.pixelplay.R
import kotlin.math.roundToInt

private const val DefaultTopBarTitleWidthAxis = 100f
private const val DefaultTopBarTitleCompressedWidthAxis = 78f
private const val TopBarTitleRoundedAxis = 100f
private const val TopBarTitleXtraAxis = 520f
private const val TopBarTitleYopqAxis = 90f
private const val TopBarTitleYtlcAxis = 505f

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
    enableCollapsedTitleWidthCompression: Boolean = true,
    enableExpandedTitleWidthCompression: Boolean = true,
    titleWidthCompressionThreshold: Dp? = null,
    titleMinWidthAxis: Float = DefaultTopBarTitleCompressedWidthAxis,
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
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val sanitizedMinWidthAxis = titleMinWidthAxis.coerceIn(1f, DefaultTopBarTitleWidthAxis)
    val expandedMinWidthAxis = if (enableExpandedTitleWidthCompression) sanitizedMinWidthAxis else DefaultTopBarTitleWidthAxis
    val collapsedMinWidthAxis = if (enableCollapsedTitleWidthCompression) sanitizedMinWidthAxis else DefaultTopBarTitleWidthAxis
    val currentMinWidthAxis = lerp(expandedMinWidthAxis, collapsedMinWidthAxis, clampedFraction)
    val titleFontWeight = FontWeight.Bold

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableTitleWidthPx = with(density) {
            (maxWidth - titlePaddingStart - titlePaddingEnd).coerceAtLeast(0.dp).roundToPx()
        }
        val thresholdWidthPx = with(density) {
            titleWidthCompressionThreshold?.coerceAtLeast(0.dp)?.roundToPx()
        }
        val compressionTargetWidthPx = thresholdWidthPx
            ?.coerceAtMost(availableTitleWidthPx)
            ?: availableTitleWidthPx
        val baseTitleStyle = titleStyle.copy(
            fontSize = titleFontSize,
            lineHeight = titleFontSize * 1.1f,
            fontWeight = titleFontWeight,
            fontFamily = rememberGoogleSansFlexFontFamily(
                fontWeight = titleFontWeight,
                widthAxis = DefaultTopBarTitleWidthAxis
            )
        )
        val naturalTitleWidthPx = remember(title, baseTitleStyle) {
            textMeasurer.measure(
                text = AnnotatedString(title),
                style = baseTitleStyle,
                overflow = TextOverflow.Clip,
                softWrap = false,
                maxLines = 1
            ).size.width
        }
        val shouldCompressTitle = currentMinWidthAxis < DefaultTopBarTitleWidthAxis &&
                availableTitleWidthPx > 0 &&
                naturalTitleWidthPx > compressionTargetWidthPx
        val resolvedWidthAxis = if (shouldCompressTitle && naturalTitleWidthPx > 0) {
            (DefaultTopBarTitleWidthAxis * compressionTargetWidthPx.toFloat() / naturalTitleWidthPx.toFloat())
                .coerceIn(currentMinWidthAxis, DefaultTopBarTitleWidthAxis)
                .roundToInt()
                .toFloat()
        } else {
            DefaultTopBarTitleWidthAxis
        }
        val resolvedTitleStyle = baseTitleStyle.copy(
            fontFamily = rememberGoogleSansFlexFontFamily(
                fontWeight = titleFontWeight,
                widthAxis = resolvedWidthAxis
            )
        )

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
                    style = resolvedTitleStyle,
                    color = contentColor,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
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

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberGoogleSansFlexFontFamily(
    fontWeight: FontWeight,
    widthAxis: Float
): FontFamily {
    val sanitizedWidthAxis = widthAxis.coerceIn(1f, DefaultTopBarTitleWidthAxis).roundToInt().toFloat()
    return remember(fontWeight, sanitizedWidthAxis) {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                weight = fontWeight,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(fontWeight.weight),
                    FontVariation.width(sanitizedWidthAxis),
                    FontVariation.Setting("ROND", TopBarTitleRoundedAxis),
                    FontVariation.Setting("XTRA", TopBarTitleXtraAxis),
                    FontVariation.Setting("YOPQ", TopBarTitleYopqAxis),
                    FontVariation.Setting("YTLC", TopBarTitleYtlcAxis)
                )
            )
        )
    }
}
