package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCompactLayoutTest {

    @Test
    fun portraitFixedHeight_isDerivedFromExistingMinima() {
        // 182 (controls) + 70 (metadata) + 70 (progress) + 4 (spacing) + 16 (album padding)
        assertEquals(342.dp, estimatePlayerPortraitFixedHeight())
    }

    @Test
    fun landscapeFixedHeight_isDerivedFromExistingMinima() {
        assertEquals(322.dp, estimatePlayerLandscapeFixedHeight())
    }

    @Test
    fun estimateCarouselHeight_mirrorsExistingStyles() {
        assertEquals(400f, estimateCarouselHeight(400.dp, CarouselStyle.NO_PEEK).value, 0.01f)
        assertEquals(320f, estimateCarouselHeight(400.dp, CarouselStyle.ONE_PEEK).value, 0.5f)
        assertEquals(240f, estimateCarouselHeight(400.dp, CarouselStyle.TWO_PEEK).value, 0.5f)
        // Unknown style keeps the existing default (0.8f).
        assertEquals(320f, estimateCarouselHeight(400.dp, "unknown").value, 0.5f)
    }

    @Test
    fun portrait_normalPhone_keepsRegularLayout() {
        // Typical fullscreen portrait: 400dp wide, ~780dp content height.
        // 400 (carousel NO_PEEK) + 342 (fixed) = 742 < 780 -> fits, no compact branch.
        assertFalse(
            shouldUseCompactPlayerLayout(
                availableHeight = 780.dp,
                contentMaxWidth = 400.dp,
                carouselStyle = CarouselStyle.NO_PEEK
            )
        )
    }

    @Test
    fun portrait_splitScreen_usesCompactLayout() {
        // Short split-screen window: same width, only 400dp height.
        // 400 + 342 = 742 > 400 -> compact, prevents SpaceAround slack / overflow.
        assertTrue(
            shouldUseCompactPlayerLayout(
                availableHeight = 400.dp,
                contentMaxWidth = 400.dp,
                carouselStyle = CarouselStyle.NO_PEEK
            )
        )
    }

    @Test
    fun portrait_popupView_usesCompactLayout() {
        // Narrow pop-up: ONE_PEEK carousel 240dp + 342 = 582 > 350 -> compact.
        assertTrue(
            shouldUseCompactPlayerLayout(
                availableHeight = 350.dp,
                contentMaxWidth = 300.dp,
                carouselStyle = CarouselStyle.ONE_PEEK
            )
        )
    }

    @Test
    fun portrait_compact_neverTriggersOnInvalidConstraints() {
        assertFalse(shouldUseCompactPlayerLayout(0.dp, 400.dp, CarouselStyle.NO_PEEK))
        assertFalse(shouldUseCompactPlayerLayout(400.dp, 0.dp, CarouselStyle.NO_PEEK))
    }

    @Test
    fun resolveCarouselHeight_neverExceedsItsConstraints() {
        // Normal window: width-based height fits, unchanged.
        assertEquals(
            400.dp,
            resolveCarouselHeight(400.dp, 800.dp, CarouselStyle.NO_PEEK)
        )
        // Short window: capped by maxHeight minus the section's own 16dp padding.
        assertEquals(
            334.dp,
            resolveCarouselHeight(400.dp, 350.dp, CarouselStyle.NO_PEEK)
        )
    }

    @Test
    fun landscape_normalHeight_keepsRegularLayout() {
        assertFalse(shouldUseCompactLandscapeLayout(600.dp))
    }

    @Test
    fun landscape_shortSplitHeight_usesCompactLayout() {
        // Right column minima 322dp do not fit into 300dp -> compact Top + scroll.
        assertTrue(shouldUseCompactLandscapeLayout(300.dp))
        assertFalse(shouldUseCompactLandscapeLayout(0.dp))
    }
}
