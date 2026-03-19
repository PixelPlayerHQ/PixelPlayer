package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SheetThemeStateTest {

    private val systemScheme = lightColorScheme(primary = Color(0xFF336699))
    private val activeAlbumScheme = lightColorScheme(primary = Color(0xFFAA2244))
    private val previousAlbumScheme = lightColorScheme(primary = Color(0xFF228855))

    @Test
    fun resolvePlayerSheetTargetScheme_withoutAlbumArt_usesSystemScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = false,
            currentSongActiveScheme = null,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertThat(resolved).isSameInstanceAs(systemScheme)
    }

    @Test
    fun resolvePlayerSheetTargetScheme_withPendingAlbumPalette_reusesPreviousAlbumScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = true,
            currentSongActiveScheme = null,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertThat(resolved).isSameInstanceAs(previousAlbumScheme)
    }

    @Test
    fun resolvePlayerSheetTargetScheme_withReadyAlbumPalette_usesCurrentAlbumScheme() {
        val resolved = resolvePlayerSheetTargetScheme(
            isAlbumArtTheme = true,
            hasAlbumArt = true,
            currentSongActiveScheme = activeAlbumScheme,
            lastAlbumScheme = previousAlbumScheme,
            systemColorScheme = systemScheme
        )

        assertThat(resolved).isSameInstanceAs(activeAlbumScheme)
    }
}
