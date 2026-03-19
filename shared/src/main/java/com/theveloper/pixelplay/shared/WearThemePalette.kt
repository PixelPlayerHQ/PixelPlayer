package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

/**
 * Compact palette snapshot sent from phone to watch so Wear UI can render
 * with the same color intent used on phone.
 *
 * Values are ARGB ints (some may include alpha).
 */
@Serializable
data class WearThemePalette(
    val gradientTopArgb: Int,
    val gradientMiddleArgb: Int,
    val gradientBottomArgb: Int,
    val surfaceContainerLowestArgb: Int = 0,
    val surfaceContainerLowArgb: Int = 0,
    val surfaceContainerArgb: Int = 0,
    val surfaceContainerHighArgb: Int = 0,
    val surfaceContainerHighestArgb: Int = 0,
    val textPrimaryArgb: Int,
    val textSecondaryArgb: Int,
    val textErrorArgb: Int,
    val controlContainerArgb: Int,
    val controlContentArgb: Int,
    val controlDisabledContainerArgb: Int,
    val controlDisabledContentArgb: Int,
    val transportContainerArgb: Int = 0,
    val transportContentArgb: Int = 0,
    val chipContainerArgb: Int,
    val chipContentArgb: Int,
    val favoriteActiveArgb: Int,
    val shuffleActiveArgb: Int,
    val repeatActiveArgb: Int,
)
