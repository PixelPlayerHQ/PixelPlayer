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

    @Test
    fun selectSeedColorArgbFromPixels_keepsDistinctGreenAlbumsFromCollapsingIntoSharedAccent() {
        val sharedAccent = 0xFF39FF88.toInt()

        val warmOliveBase = 0xFF708238.toInt()
        val warmOliveSupport = 0xFF8A9B4F.toInt()
        val coolJadeBase = 0xFF2C7A5F.toInt()
        val coolJadeSupport = 0xFF4C9A7C.toInt()

        val warmSeed = selectSeedColorArgbFromPixels(
            pixels = pixelsOf(
                550 to warmOliveBase,
                300 to warmOliveSupport,
                150 to sharedAccent
            )
        )
        val coolSeed = selectSeedColorArgbFromPixels(
            pixels = pixelsOf(
                550 to coolJadeBase,
                300 to coolJadeSupport,
                150 to sharedAccent
            )
        )

        assertThat(hueDistance(warmSeed, warmOliveBase))
            .isLessThan(hueDistance(warmSeed, sharedAccent))
        assertThat(hueDistance(coolSeed, coolJadeBase))
            .isLessThan(hueDistance(coolSeed, sharedAccent))
        assertThat(hueDistance(warmSeed, coolSeed)).isGreaterThan(12.0)
    }

    @Test
    fun selectSeedColorArgbFromPixels_keepsMostlyNeutralArtworkNearNeutral() {
        val result = selectSeedColorArgbFromPixels(
            pixels = pixelsOf(
                940 to 0xFF7A7A7A.toInt(),
                40 to 0xFF848484.toInt(),
                20 to 0xFF70758A.toInt()
            )
        )

        assertThat(Hct.fromInt(result).chroma).isLessThan(12.0)
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

    private fun pixelsOf(vararg entries: Pair<Int, Int>): IntArray {
        return buildList {
            entries.forEach { (count, color) ->
                repeat(count) { add(color) }
            }
        }.toIntArray()
    }

    private fun hueDistance(firstArgb: Int, secondArgb: Int): Double {
        return com.google.android.material.color.utilities.MathUtils.differenceDegrees(
            Hct.fromInt(firstArgb).hue,
            Hct.fromInt(secondArgb).hue
        )
    }
}
