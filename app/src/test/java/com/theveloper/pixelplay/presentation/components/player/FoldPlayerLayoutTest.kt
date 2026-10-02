package com.theveloper.pixelplay.presentation.components.player

import org.junit.Assert.assertEquals
import org.junit.Test

class FoldPlayerLayoutTest {

    @Test
    fun noHingeStaysOnThePhonePlayerInEitherOrientation() {
        assertEquals(FoldPlayerLayout.Phone, foldPlayerLayout(null, isLandscape = false))
        assertEquals(FoldPlayerLayout.Phone, foldPlayerLayout(null, isLandscape = true))
    }

    @Test
    fun flatPortraitStaysOnThePhonePlayer() {
        val flat = FoldSignal(halfOpened = false, flat = true, horizontal = true, vertical = false)
        assertEquals(FoldPlayerLayout.Phone, foldPlayerLayout(flat, isLandscape = false))
    }

    @Test
    fun halfOpenHorizontalIsTabletop() {
        val tabletop = FoldSignal(halfOpened = true, flat = false, horizontal = true, vertical = false)
        assertEquals(FoldPlayerLayout.Tabletop, foldPlayerLayout(tabletop, isLandscape = false))
    }

    @Test
    fun halfOpenVerticalIsBook() {
        val book = FoldSignal(halfOpened = true, flat = false, horizontal = false, vertical = true)
        assertEquals(FoldPlayerLayout.Book, foldPlayerLayout(book, isLandscape = true))
    }

    @Test
    fun flatLandscapeUsesTheWideInnerLayout() {
        val flat = FoldSignal(halfOpened = false, flat = true, horizontal = false, vertical = true)
        assertEquals(FoldPlayerLayout.FlatWide, foldPlayerLayout(flat, isLandscape = true))
    }
}
