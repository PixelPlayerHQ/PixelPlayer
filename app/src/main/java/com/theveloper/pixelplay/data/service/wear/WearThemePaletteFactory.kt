package com.theveloper.pixelplay.data.service.wear

import android.graphics.Color as AndroidColor
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.theveloper.pixelplay.shared.WearThemePalette

internal fun buildWearThemePalette(darkScheme: ColorScheme): WearThemePalette {
    val surfaceContainer = darkScheme.surfaceContainer.toArgb()
    val surfaceContainerLowest = darkScheme.surfaceContainerLowest.toArgb()
    val surfaceContainerLow = darkScheme.surfaceContainerLow.toArgb()
    val surfaceContainerHigh = darkScheme.surfaceContainerHigh.toArgb()
    val surfaceContainerHighest = darkScheme.surfaceContainerHighest.toArgb()

    val playContainer = darkScheme.onPrimaryContainer.toArgb()
    val playContent = darkScheme.primaryContainer.toArgb()
    val transportContainer = darkScheme.primary.toArgb()
    val transportContent = darkScheme.onPrimary.toArgb()
    val chipContainer = ColorUtils.blendARGB(
        darkScheme.secondaryContainer.toArgb(),
        surfaceContainerLow,
        0.28f,
    )

    val gradientTop = ColorUtils.blendARGB(surfaceContainerHigh, playContainer, 0.34f)
    val gradientMiddle = ColorUtils.blendARGB(
        ColorUtils.blendARGB(surfaceContainer, transportContainer, 0.12f),
        AndroidColor.BLACK,
        0.34f,
    )
    val gradientBottom = ColorUtils.blendARGB(
        ColorUtils.blendARGB(surfaceContainerLowest, transportContainer, 0.08f),
        AndroidColor.BLACK,
        0.68f,
    )

    return WearThemePalette(
        gradientTopArgb = gradientTop,
        gradientMiddleArgb = gradientMiddle,
        gradientBottomArgb = gradientBottom,
        surfaceContainerLowestArgb = surfaceContainerLowest,
        surfaceContainerLowArgb = surfaceContainerLow,
        surfaceContainerArgb = surfaceContainer,
        surfaceContainerHighArgb = surfaceContainerHigh,
        surfaceContainerHighestArgb = surfaceContainerHighest,
        textPrimaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurface.toArgb(),
            backgroundColor = gradientMiddle,
        ),
        textSecondaryArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = gradientBottom,
        ),
        textErrorArgb = 0xFFFFB8C7.toInt(),
        controlContainerArgb = playContainer,
        controlContentArgb = ensureWearReadable(
            preferredColor = playContent,
            backgroundColor = playContainer,
        ),
        controlDisabledContainerArgb = surfaceContainerHighest,
        controlDisabledContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSurfaceVariant.toArgb(),
            backgroundColor = surfaceContainerHighest,
        ),
        transportContainerArgb = transportContainer,
        transportContentArgb = ensureWearReadable(
            preferredColor = transportContent,
            backgroundColor = transportContainer,
        ),
        chipContainerArgb = chipContainer,
        chipContentArgb = ensureWearReadable(
            preferredColor = darkScheme.onSecondaryContainer.toArgb(),
            backgroundColor = chipContainer,
        ),
        favoriteActiveArgb = shiftWearHue(transportContainer, 34f),
        shuffleActiveArgb = shiftWearHue(transportContainer, -72f),
        repeatActiveArgb = shiftWearHue(transportContainer, -22f),
    )
}

private fun shiftWearHue(color: Int, hueShift: Float): Int {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[0] = (hsl[0] + hueShift + 360f) % 360f
    hsl[1] = (hsl[1] * 1.18f).coerceIn(0.42f, 0.92f)
    hsl[2] = (hsl[2] + 0.08f).coerceIn(0.34f, 0.78f)
    return ColorUtils.HSLToColor(hsl)
}

private fun ensureWearReadable(preferredColor: Int, backgroundColor: Int): Int {
    val opaqueBackground = if (AndroidColor.alpha(backgroundColor) >= 255) {
        backgroundColor
    } else {
        ColorUtils.compositeColors(backgroundColor, AndroidColor.BLACK)
    }
    val preferredContrast = ColorUtils.calculateContrast(preferredColor, opaqueBackground)
    if (preferredContrast >= 3.0) return preferredColor

    val light = 0xFFF6F2FF.toInt()
    val dark = 0xFF17141E.toInt()
    val lightContrast = ColorUtils.calculateContrast(light, opaqueBackground)
    val darkContrast = ColorUtils.calculateContrast(dark, opaqueBackground)
    return if (lightContrast >= darkContrast) light else dark
}
