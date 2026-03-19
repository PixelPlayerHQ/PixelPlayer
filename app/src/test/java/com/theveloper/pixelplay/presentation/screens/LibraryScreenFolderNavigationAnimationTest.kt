package com.theveloper.pixelplay.presentation.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LibraryScreenFolderNavigationAnimationTest {

    @Test
    fun `resolveFolderNavigationDirection returns forward when entering a child folder`() {
        assertEquals(
            1,
            resolveFolderNavigationDirection(
                initialPath = "/storage/emulated/0/Music",
                targetPath = "/storage/emulated/0/Music/Rock"
            )
        )
    }

    @Test
    fun `resolveFolderNavigationDirection returns backward when returning to the parent folder`() {
        assertEquals(
            -1,
            resolveFolderNavigationDirection(
                initialPath = "/storage/emulated/0/Music/Rock",
                targetPath = "/storage/emulated/0/Music"
            )
        )
    }

    @Test
    fun `resolveFolderNavigationDirection returns backward when returning to root`() {
        assertEquals(
            -1,
            resolveFolderNavigationDirection(
                initialPath = "/storage/emulated/0/Music",
                targetPath = null
            )
        )
    }

    @Test
    fun `resolveFolderNavigationDirection defaults to forward for unrelated transitions`() {
        assertEquals(
            1,
            resolveFolderNavigationDirection(
                initialPath = "/storage/emulated/0/Music/Rock",
                targetPath = "/storage/emulated/0/Music/Jazz"
            )
        )
    }
}
