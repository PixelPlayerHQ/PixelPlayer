package com.theveloper.pixelplay.data.model

data class ExtensionCapabilities(
    val isLoginNeeded: Boolean = false,
    val canHomeFeed: Boolean = false,
    val canLibraryFeed: Boolean = false
)
