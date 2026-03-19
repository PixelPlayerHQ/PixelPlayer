package com.theveloper.pixelplay.data

import com.theveloper.pixelplay.shared.WearLibraryItem

data class WearLocalQueueState(
    val items: List<WearLibraryItem> = emptyList(),
    val currentIndex: Int = -1,
)
