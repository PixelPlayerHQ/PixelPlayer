package com.theveloper.pixelplay.data.preferences

object AlbumArtColorAccuracy {
    const val MIN = 0
    const val MAX = 10
    const val DEFAULT = MIN
    const val STEPS = MAX - MIN - 1

    fun clamp(value: Int): Int = value.coerceIn(MIN, MAX)
}
