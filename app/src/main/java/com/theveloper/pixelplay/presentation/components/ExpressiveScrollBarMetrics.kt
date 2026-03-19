package com.theveloper.pixelplay.presentation.components

internal class AxisObservationTracker {
    private var trackedTotalItemsCount = -1
    private var trackedSpacingPx = Int.MIN_VALUE
    private var representativeStridePx = 1f
    private var representativeItemSizePx = 1f
    private var representativeStrideSampleCount = 0
    private var representativeItemSizeSampleCount = 0
    private val observedStridesPx = mutableMapOf<Int, Float>()
    private val observedItemSizesPx = mutableMapOf<Int, Float>()

    fun resetIfNeeded(totalItemsCount: Int, spacingPx: Int) {
        if (trackedTotalItemsCount == totalItemsCount && trackedSpacingPx == spacingPx) {
            return
        }

        trackedTotalItemsCount = totalItemsCount
        trackedSpacingPx = spacingPx
        representativeStridePx = 1f
        representativeItemSizePx = 1f
        representativeStrideSampleCount = 0
        representativeItemSizeSampleCount = 0
        observedStridesPx.clear()
        observedItemSizesPx.clear()
    }

    fun observeRepresentativeSample(
        strideSamplePx: Float?,
        itemSizeSamplePx: Float?
    ) {
        val normalizedStrideSamplePx = strideSamplePx?.coerceAtLeast(1f)
        val normalizedItemSizeSamplePx = itemSizeSamplePx?.coerceAtLeast(1f)

        if (normalizedStrideSamplePx == null && normalizedItemSizeSamplePx == null) {
            return
        }

        normalizedStrideSamplePx?.let { sample ->
            val currentCount = representativeStrideSampleCount
            representativeStrideSampleCount = currentCount + 1
            representativeStridePx =
                if (currentCount == 0) {
                    sample
                } else {
                    ((representativeStridePx * currentCount) + sample) / representativeStrideSampleCount
                }
        }

        normalizedItemSizeSamplePx?.let { sample ->
            val currentCount = representativeItemSizeSampleCount
            representativeItemSizeSampleCount = currentCount + 1
            representativeItemSizePx =
                if (currentCount == 0) {
                    sample
                } else {
                    ((representativeItemSizePx * currentCount) + sample) / representativeItemSizeSampleCount
                }
        }
    }

    fun observeItemSize(index: Int, sizePx: Float) {
        observedItemSizesPx[index] = sizePx.coerceAtLeast(1f)
    }

    fun observeStride(index: Int, stridePx: Float) {
        observedStridesPx[index] = stridePx.coerceAtLeast(1f)
    }

    fun representativeStridePx(fallbackStridePx: Float): Float {
        return if (representativeStrideSampleCount > 0) {
            representativeStridePx
        } else {
            fallbackStridePx.coerceAtLeast(1f)
        }
    }

    fun representativeItemSizePx(fallbackItemSizePx: Float): Float {
        return if (representativeItemSizeSampleCount > 0) {
            representativeItemSizePx
        } else {
            fallbackItemSizePx.coerceAtLeast(1f)
        }
    }

    fun distanceBeforeIndex(index: Int, representativeStridePx: Float): Float {
        if (index <= 0) return 0f

        var correctionPx = 0f
        observedStridesPx.forEach { (observedIndex, observedStridePx) ->
            if (observedIndex < index) {
                correctionPx += observedStridePx - representativeStridePx
            }
        }

        return ((index * representativeStridePx) + correctionPx).coerceAtLeast(0f)
    }

    fun itemSizePx(index: Int, representativeItemSizePx: Float): Float {
        return observedItemSizesPx[index] ?: representativeItemSizePx.coerceAtLeast(1f)
    }
}

internal fun medianOrNull(values: Iterable<Float>): Float? {
    val sorted = values
        .filter { it.isFinite() && it > 0f }
        .sorted()

    if (sorted.isEmpty()) return null

    val middleIndex = sorted.size / 2
    return if (sorted.size % 2 == 0) {
        (sorted[middleIndex - 1] + sorted[middleIndex]) / 2f
    } else {
        sorted[middleIndex]
    }
}
