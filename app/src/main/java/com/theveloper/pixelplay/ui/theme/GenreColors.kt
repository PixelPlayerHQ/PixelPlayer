package com.theveloper.pixelplay.ui.theme

import android.util.LruCache
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import kotlin.math.abs

data class GenreThemeColor(
    val container: Color,
    val onContainer: Color
)

object GenreThemeUtils {
    private val genreColorSchemeCache = LruCache<Int, ColorScheme>(96)
    private val unknownSeedColor = Color(0xFF7C7D84)
    private val unknownLightThemeColor = GenreThemeColor(
        container = Color(0xFFE5E5EA),
        onContainer = Color(0xFF1B1B20)
    )
    private val unknownDarkThemeColor = GenreThemeColor(
        container = Color(0xFF3A3B42),
        onContainer = Color(0xFFF2F1F6)
    )
    
    private val darkColors = listOf(
        GenreThemeColor(Color(0xFF004A77), Color(0xFFC2E7FF)), // Blue
        GenreThemeColor(Color(0xFF7D5260), Color(0xFFFFD8E4)), // Rose
        GenreThemeColor(Color(0xFF633B48), Color(0xFFFFD8EC)), // Pink
        GenreThemeColor(Color(0xFF004F58), Color(0xFF88FAFF)), // Cyan
        GenreThemeColor(Color(0xFF324F34), Color(0xFFCBEFD0)), // Green
        GenreThemeColor(Color(0xFF6E4E13), Color(0xFFFFDEAC)), // Gold/Orange
        GenreThemeColor(Color(0xFF3F474D), Color(0xFFDEE3EB)), // Slate
        GenreThemeColor(Color(0xFF4A4458), Color(0xFFE8DEF8)), // Purple
        GenreThemeColor(Color(0xFF7D2B2B), Color(0xFFFFB4AB)), // Red
        GenreThemeColor(Color(0xFF5B6300), Color(0xFFDDF669)), // Lime
        GenreThemeColor(Color(0xFF005047), Color(0xFF8CF4E6)), // Teal
        GenreThemeColor(Color(0xFF4F378B), Color(0xFFEADDFF)), // Indigo
        GenreThemeColor(Color(0xFF8B4A62), Color(0xFFFFD9E2)), // Maroon
        GenreThemeColor(Color(0xFF725C00), Color(0xFFFFE084)), // Yellow
        GenreThemeColor(Color(0xFF00213B), Color(0xFF99CBFF)), // Navy
        GenreThemeColor(Color(0xFF23507D), Color(0xFFD1E4FF)), // Steel Blue
        GenreThemeColor(Color(0xFF93000A), Color(0xFFFFDAD6)), // Brick Red
        GenreThemeColor(Color(0xFF45464F), Color(0xFFC4C6D0)), // Grey
        GenreThemeColor(Color(0xFF5D3F75), Color(0xFFE8B6FF)), // Violet
        GenreThemeColor(Color(0xFF7A5900), Color(0xFFFFDEA5))  // Amber
    )

    private val lightColors = listOf(
        GenreThemeColor(Color(0xFFD7E3FF), Color(0xFF005AC1)), // Blue
        GenreThemeColor(Color(0xFFFFD8E4), Color(0xFF631835)), // Rose
        GenreThemeColor(Color(0xFFFFD8EC), Color(0xFF631B4B)), // Pink
        GenreThemeColor(Color(0xFFCCE8EA), Color(0xFF004F58)), // Cyan
        GenreThemeColor(Color(0xFFCBEFD0), Color(0xFF042106)), // Green
        GenreThemeColor(Color(0xFFFFDEAC), Color(0xFF281900)), // Gold/Orange
        GenreThemeColor(Color(0xFFEFF1F7), Color(0xFF44474F)), // Slate
        GenreThemeColor(Color(0xFFE8DEF8), Color(0xFF1D192B)), // Purple
        GenreThemeColor(Color(0xFFFFB4AB), Color(0xFF690005)), // Red
        GenreThemeColor(Color(0xFFDDF669), Color(0xFF2F3300)), // Lime
        GenreThemeColor(Color(0xFF8CF4E6), Color(0xFF00201C)), // Teal
        GenreThemeColor(Color(0xFFEADDFF), Color(0xFF21005D)), // Indigo
        GenreThemeColor(Color(0xFFFFD9E2), Color(0xFF3B071D)), // Maroon
        GenreThemeColor(Color(0xFFFFE084), Color(0xFF231B00)), // Yellow
        GenreThemeColor(Color(0xFF99CBFF), Color(0xFF003258)), // Navy
        GenreThemeColor(Color(0xFFD1E4FF), Color(0xFF051C36)), // Steel Blue
        GenreThemeColor(Color(0xFFFFDAD6), Color(0xFF410002)), // Brick Red
        GenreThemeColor(Color(0xFFE2E2E9), Color(0xFF191C20)), // Grey
        GenreThemeColor(Color(0xFFF2DAFF), Color(0xFF2C004F)), // Violet
        GenreThemeColor(Color(0xFFFFDEA5), Color(0xFF261900))  // Amber
    )

    private fun isUnknownGenreId(genreId: String): Boolean {
        return genreId
            .trim()
            .lowercase()
            .let { normalized ->
                normalized == "unknown" ||
                    normalized == "unknown genre" ||
                    normalized == "unknown_genre"
            }
    }

    fun getGenreThemeColor(genreId: String, isDark: Boolean): GenreThemeColor {
        if (isUnknownGenreId(genreId)) {
            return if (isDark) unknownDarkThemeColor else unknownLightThemeColor
        }
        val hash = abs(genreId.hashCode())
        val index = hash % darkColors.size
        return if (isDark) darkColors[index] else lightColors[index]
    }

    fun getGenreThemeColor(
        genre: Genre?,
        isDark: Boolean,
        fallbackGenreId: String = "unknown"
    ): GenreThemeColor {
        val effectiveGenreId = genre?.id?.takeIf { it.isNotBlank() } ?: fallbackGenreId
        if (isUnknownGenreId(effectiveGenreId)) {
            return if (isDark) unknownDarkThemeColor else unknownLightThemeColor
        }
        val seed = resolveSeedColor(genre = genre, isDark = isDark, fallbackGenreId = fallbackGenreId)
        val explicitOnColor = parseHexColor(
            if (isDark) genre?.onDarkColorHex else genre?.onLightColorHex
        )
        return GenreThemeColor(
            container = seed,
            onContainer = explicitOnColor ?: seed.contrastContentColor()
        )
    }

    private fun androidx.compose.ui.graphics.Color.contrastContentColor(): androidx.compose.ui.graphics.Color {
        // Calculate luminance
        val luminance = (0.299 * red + 0.587 * green + 0.114 * blue)
        
        // If background is dark (luminance <= 0.5), we want a light color (Pastel).
        // If background is light (luminance > 0.5), we want a dark color (Deep Tone).
        // mixing 90% white or 90% black retains some hue (10%) while ensuring high contrast.
        return if (luminance <= 0.5) {
             androidx.compose.ui.graphics.lerp(this, androidx.compose.ui.graphics.Color.White, 0.9f)
        } else {
             androidx.compose.ui.graphics.lerp(this, androidx.compose.ui.graphics.Color.Black, 0.9f)
        }
    }

    fun getGenreColorScheme(
        genre: Genre?,
        isDark: Boolean,
        genreIdFallback: String = "unknown",
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.EXPRESSIVE
    ): ColorScheme {
        val effectiveGenreId = genre?.id?.takeIf { it.isNotBlank() } ?: genreIdFallback
        val forceMonochrome = isUnknownGenreId(effectiveGenreId)
        val seed = if (forceMonochrome) {
            unknownSeedColor
        } else {
            resolveSeedColor(
                genre = genre,
                isDark = isDark,
                fallbackGenreId = genreIdFallback
            )
        }
        val cacheKey =
            (((seed.toArgb() * 31) + if (isDark) 1 else 0) * 31 + paletteStyle.ordinal) * 31 +
                if (forceMonochrome) 1 else 0
        genreColorSchemeCache.get(cacheKey)?.let { return it }

        val pair = if (forceMonochrome) {
            generateMonochromeColorSchemeFromSeed(seed)
        } else {
            generateColorSchemeFromSeed(
                seedColor = seed,
                paletteStyle = paletteStyle
            )
        }
        val scheme = if (isDark) pair.dark else pair.light
        genreColorSchemeCache.put(cacheKey, scheme)
        return scheme
    }

    fun getGenreColorScheme(
        genreId: String,
        isDark: Boolean,
        paletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.EXPRESSIVE
    ): ColorScheme {
        return getGenreColorScheme(
            genre = null,
            isDark = isDark,
            genreIdFallback = genreId,
            paletteStyle = paletteStyle
        )
    }

    private fun resolveSeedColor(
        genre: Genre?,
        isDark: Boolean,
        fallbackGenreId: String
    ): Color {
        val explicitSeed = parseHexColor(
            if (isDark) genre?.darkColorHex else genre?.lightColorHex
        )
        if (explicitSeed != null) return explicitSeed

        val fallbackId = genre?.id?.takeIf { it.isNotBlank() } ?: fallbackGenreId
        return getGenreThemeColor(fallbackId, isDark).container
    }

    private fun parseHexColor(hex: String?): Color? {
        if (hex.isNullOrBlank()) return null
        val normalized = if (hex.startsWith("#")) hex else "#$hex"
        return try {
            Color(android.graphics.Color.parseColor(normalized))
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
