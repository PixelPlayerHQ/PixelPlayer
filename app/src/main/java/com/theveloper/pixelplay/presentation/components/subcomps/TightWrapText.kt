package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import kotlin.math.ceil

@Composable
fun TightWrapText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    style: TextStyle = LocalTextStyle.current,
) {
    val textMeasurer = rememberTextMeasurer()
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }

    val mergedStyle = remember(
        style, textColor, fontSize, fontStyle, fontWeight, fontFamily,
        letterSpacing, textDecoration, textAlign, lineHeight
    ) {
        style.merge(
            TextStyle(
                color = textColor,
                fontSize = fontSize,
                fontStyle = fontStyle,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                letterSpacing = letterSpacing,
                textDecoration = textDecoration,
                textAlign = textAlign ?: TextAlign.Unspecified,
                lineHeight = lineHeight
            )
        )
    }

    val layoutContainer = remember { TextLayoutContainer() }

    Layout(
        modifier = modifier.drawBehind {
            layoutContainer.result?.let { result ->
                val minX = if (result.lineCount > 0) {
                    (0 until result.lineCount).minOf { result.getLineLeft(it) }
                } else 0f
                drawText(result, topLeft = Offset(-minX, 0f))
            }
        },
        content = {}
    ) { _, constraints ->
        val result = textMeasurer.measure(
            text = text,
            style = mergedStyle,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            constraints = constraints
        )
        layoutContainer.result = result

        val minX = if (result.lineCount > 0) {
            (0 until result.lineCount).minOf { result.getLineLeft(it) }
        } else 0f

        val maxX = if (result.lineCount > 0) {
            (0 until result.lineCount).maxOf { result.getLineRight(it) }
        } else 0f

        val tightWidth = ceil(maxX - minX).toInt()

        layout(tightWidth, result.size.height) {
            // No children to place
        }
    }
}

private class TextLayoutContainer {
    var result: TextLayoutResult? = null
}