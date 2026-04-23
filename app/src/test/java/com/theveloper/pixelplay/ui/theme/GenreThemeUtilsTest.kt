package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import org.junit.Test

class GenreThemeUtilsTest {

    @Test
    fun getGenreDetailColorScheme_usesGenreCardContainerAsSeedInLightTheme() {
        val genre = Genre(id = "rock", name = "Rock")
        val paletteStyle = AlbumArtPaletteStyle.VIBRANT
        val cardColor = GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = false,
            fallbackGenreId = genre.id
        )

        val actual = GenreThemeUtils.getGenreDetailColorScheme(
            genre = genre,
            isDark = false,
            fallbackGenreId = genre.id,
            paletteStyle = paletteStyle
        )
        val expected = generateColorSchemeFromSeed(
            seedColor = cardColor.container,
            paletteStyle = paletteStyle
        ).light

        assertThat(actual.toArgbList()).containsExactlyElementsIn(expected.toArgbList()).inOrder()
    }

    @Test
    fun getGenreDetailColorScheme_usesGenreCardContainerAsSeedInDarkTheme() {
        val genre = Genre(id = "hip_hop", name = "Hip Hop")
        val paletteStyle = AlbumArtPaletteStyle.EXPRESSIVE
        val cardColor = GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = true,
            fallbackGenreId = genre.id
        )

        val actual = GenreThemeUtils.getGenreDetailColorScheme(
            genre = genre,
            isDark = true,
            fallbackGenreId = genre.id,
            paletteStyle = paletteStyle
        )
        val expected = generateColorSchemeFromSeed(
            seedColor = cardColor.container,
            paletteStyle = paletteStyle
        ).dark

        assertThat(actual.toArgbList()).containsExactlyElementsIn(expected.toArgbList()).inOrder()
    }

    @Test
    fun getGenreDetailColorScheme_forUnknownGenre_usesMonochromeScheme() {
        val actual = GenreThemeUtils.getGenreDetailColorScheme(
            genre = null,
            isDark = false,
            fallbackGenreId = "unknown",
            paletteStyle = AlbumArtPaletteStyle.FRUIT_SALAD
        )
        val expected = generateMonochromeColorSchemeFromSeed(Color(0xFF7C7D84)).light

        assertThat(actual.toArgbList()).containsExactlyElementsIn(expected.toArgbList()).inOrder()
    }

    private fun ColorScheme.toArgbList(): List<Int> {
        return listOf(
            primary,
            onPrimary,
            primaryContainer,
            onPrimaryContainer,
            inversePrimary,
            secondary,
            onSecondary,
            secondaryContainer,
            onSecondaryContainer,
            tertiary,
            onTertiary,
            tertiaryContainer,
            onTertiaryContainer,
            background,
            onBackground,
            surface,
            onSurface,
            surfaceVariant,
            onSurfaceVariant,
            surfaceTint,
            inverseSurface,
            inverseOnSurface,
            error,
            onError,
            errorContainer,
            onErrorContainer,
            outline,
            outlineVariant,
            scrim,
            surfaceBright,
            surfaceDim,
            surfaceContainer,
            surfaceContainerHigh,
            surfaceContainerHighest,
            surfaceContainerLow,
            surfaceContainerLowest,
            primaryFixed,
            primaryFixedDim,
            onPrimaryFixed,
            onPrimaryFixedVariant,
            secondaryFixed,
            secondaryFixedDim,
            onSecondaryFixed,
            onSecondaryFixedVariant,
            tertiaryFixed,
            tertiaryFixedDim,
            onTertiaryFixed,
            onTertiaryFixedVariant
        ).map(Color::toArgb)
    }
}
