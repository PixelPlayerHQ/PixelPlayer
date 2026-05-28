package com.theveloper.pixelplay.data.preferences

data class FullPlayerLoadingTweaks(
    val delayAll: Boolean = false,
    val delayAlbumCarousel: Boolean = true,
    val delaySongMetadata: Boolean = true,
    val delayProgressBar: Boolean = true,
    val delayControls: Boolean = true,
    val showPlaceholders: Boolean = true,
    val transparentPlaceholders: Boolean = false,
    val applyPlaceholdersOnClose: Boolean = false,
    val switchOnDragRelease: Boolean = true,
    val contentAppearThresholdPercent: Int = 98,
    val contentCloseThresholdPercent: Int = 0
)
