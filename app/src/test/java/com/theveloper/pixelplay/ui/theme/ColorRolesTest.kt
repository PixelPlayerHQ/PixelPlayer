package com.theveloper.pixelplay.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeFruitSalad
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import org.junit.Test

class ColorRolesTest {

    @Test
    fun generateColorSchemeFromSeed_autoNeutralOutputIsPureGrayscale() {
        val seed = Color(0xFF7F7F7F)

        AlbumArtPaletteStyle.entries.forEach { style ->
            val actual = generateColorSchemeFromSeed(seed, style)
            val nonGrayscaleLight = actual.light.toArgbList().filterNot(::isGrayscaleArgb)
            val nonGrayscaleDark = actual.dark.toArgbList().filterNot(::isGrayscaleArgb)

            assertThat(nonGrayscaleLight).isEmpty()
            assertThat(nonGrayscaleDark).isEmpty()
        }
    }

    @Test
    fun generateColorSchemeFromSeed_keepsStyleSpecificSchemeForMutedGreenSeeds() {
        val seed = Color(0xFF556B2F)
        val sourceHct = Hct.fromInt(seed.toArgb())

        AlbumArtPaletteStyle.entries.forEach { style ->
            val actual = generateColorSchemeFromSeed(seed, style)

            assertThat(actual.light.toArgbList())
                .containsExactlyElementsIn(
                    expectedScheme(style, sourceHct, isDark = false).toArgbList()
                )
                .inOrder()
            assertThat(actual.dark.toArgbList())
                .containsExactlyElementsIn(
                    expectedScheme(style, sourceHct, isDark = true).toArgbList()
                )
                .inOrder()
        }
    }

    private fun expectedScheme(
        style: AlbumArtPaletteStyle,
        sourceHct: Hct,
        isDark: Boolean
    ): DynamicScheme {
        return when (style) {
            AlbumArtPaletteStyle.TONAL_SPOT -> SchemeTonalSpot(sourceHct, isDark, 0.0)
            AlbumArtPaletteStyle.VIBRANT -> SchemeVibrant(sourceHct, isDark, 0.0)
            AlbumArtPaletteStyle.EXPRESSIVE -> SchemeExpressive(sourceHct, isDark, 0.0)
            AlbumArtPaletteStyle.FRUIT_SALAD -> SchemeFruitSalad(sourceHct, isDark, 0.0)
        }
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

    private fun DynamicScheme.toArgbList(): List<Int> {
        return listOf(
            getPrimary(),
            getOnPrimary(),
            getPrimaryContainer(),
            getOnPrimaryContainer(),
            getInversePrimary(),
            getSecondary(),
            getOnSecondary(),
            getSecondaryContainer(),
            getOnSecondaryContainer(),
            getTertiary(),
            getOnTertiary(),
            getTertiaryContainer(),
            getOnTertiaryContainer(),
            getBackground(),
            getOnBackground(),
            getSurface(),
            getOnSurface(),
            getSurfaceVariant(),
            getOnSurfaceVariant(),
            getSurfaceTint(),
            getInverseSurface(),
            getInverseOnSurface(),
            getError(),
            getOnError(),
            getErrorContainer(),
            getOnErrorContainer(),
            getOutline(),
            getOutlineVariant(),
            getScrim(),
            getSurfaceBright(),
            getSurfaceDim(),
            getSurfaceContainer(),
            getSurfaceContainerHigh(),
            getSurfaceContainerHighest(),
            getSurfaceContainerLow(),
            getSurfaceContainerLowest(),
            getPrimaryFixed(),
            getPrimaryFixedDim(),
            getOnPrimaryFixed(),
            getOnPrimaryFixedVariant(),
            getSecondaryFixed(),
            getSecondaryFixedDim(),
            getOnSecondaryFixed(),
            getOnSecondaryFixedVariant(),
            getTertiaryFixed(),
            getTertiaryFixedDim(),
            getOnTertiaryFixed(),
            getOnTertiaryFixedVariant()
        )
    }

    private fun isGrayscaleArgb(argb: Int): Boolean {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(argb, hsl)
        return hsl[1] == 0f
    }
}
