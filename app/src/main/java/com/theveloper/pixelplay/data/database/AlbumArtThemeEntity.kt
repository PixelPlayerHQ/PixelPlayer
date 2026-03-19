package com.theveloper.pixelplay.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Para simplificar, almacenaremos los colores como Strings hexadecimales.
// Almacena los valores de color para UN esquema (sea light o dark)
data class StoredColorSchemeValues(
    val primary: String,
    val onPrimary: String,
    val primaryContainer: String,
    val onPrimaryContainer: String,
    val secondary: String,
    val onSecondary: String,
    val secondaryContainer: String,
    val onSecondaryContainer: String,
    val tertiary: String,
    val onTertiary: String,
    val tertiaryContainer: String,
    val onTertiaryContainer: String,
    val background: String,
    val onBackground: String,
    val surface: String,
    val onSurface: String,
    val surfaceVariant: String,
    val onSurfaceVariant: String,
    val error: String,
    val onError: String,
    val outline: String,
    val errorContainer: String,
    val onErrorContainer: String,
    val inversePrimary: String,
    val inverseSurface: String,
    val inverseOnSurface: String,
    val surfaceTint: String,
    val outlineVariant: String,
    val scrim: String,
    val surfaceBright: String,
    val surfaceDim: String,
    val surfaceContainer: String,
    val surfaceContainerHigh: String,
    val surfaceContainerHighest: String,
    val surfaceContainerLow: String,
    val surfaceContainerLowest: String,
    val primaryFixed: String,
    val primaryFixedDim: String,
    val onPrimaryFixed: String,
    val onPrimaryFixedVariant: String,
    val secondaryFixed: String,
    val secondaryFixedDim: String,
    val onSecondaryFixed: String,
    val onSecondaryFixedVariant: String,
    val tertiaryFixed: String,
    val tertiaryFixedDim: String,
    val onTertiaryFixed: String,
    val onTertiaryFixedVariant: String
)

@Entity(
    tableName = "album_art_themes",
    indices = [
        Index(value = ["albumArtUriString", "paletteStyle"])
    ]
)
data class AlbumArtThemeEntity(
    @PrimaryKey val albumArtUriString: String,
    val paletteStyle: String,
    @Embedded(prefix = "light_") val lightThemeValues: StoredColorSchemeValues,
    @Embedded(prefix = "dark_") val darkThemeValues: StoredColorSchemeValues
)
