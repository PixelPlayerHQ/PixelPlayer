package com.theveloper.pixelplay.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ExpressiveScrollBarMetricsTest {

    @Test
    fun distanceBeforeIndex_preservesObservedOutlierStrides() {
        val tracker = AxisObservationTracker()

        tracker.resetIfNeeded(totalItemsCount = 128, spacingPx = 8)
        tracker.observeRepresentativeSample(
            strideSamplePx = 100f,
            itemSizeSamplePx = 92f
        )
        tracker.observeStride(index = 0, stridePx = 14f)
        tracker.observeStride(index = 1, stridePx = 100f)
        tracker.observeStride(index = 2, stridePx = 100f)

        assertEquals(14f, tracker.distanceBeforeIndex(index = 1, representativeStridePx = 100f), 0.001f)
        assertEquals(114f, tracker.distanceBeforeIndex(index = 2, representativeStridePx = 100f), 0.001f)
        assertEquals(214f, tracker.distanceBeforeIndex(index = 3, representativeStridePx = 100f), 0.001f)
    }

    @Test
    fun resetIfNeeded_clearsPreviousContentObservations() {
        val tracker = AxisObservationTracker()

        tracker.resetIfNeeded(totalItemsCount = 32, spacingPx = 8)
        tracker.observeRepresentativeSample(
            strideSamplePx = 100f,
            itemSizeSamplePx = 92f
        )
        tracker.observeStride(index = 0, stridePx = 14f)
        tracker.observeItemSize(index = 0, sizePx = 6f)

        tracker.resetIfNeeded(totalItemsCount = 12, spacingPx = 8)

        assertEquals(100f, tracker.distanceBeforeIndex(index = 1, representativeStridePx = 100f), 0.001f)
        assertEquals(92f, tracker.itemSizePx(index = 0, representativeItemSizePx = 92f), 0.001f)
    }
}
