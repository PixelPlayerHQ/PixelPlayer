package com.theveloper.pixelplay.data.preferences

enum class AlbumArtPaletteStyle(
    val storageKey: String,
    val label: String
) {
    TONAL_SPOT("tonal_spot", "Tonal Spot"),
    VIBRANT("vibrant", "Vibrant"),
    EXPRESSIVE("expressive", "Expressive"),
    FRUIT_SALAD("fruit_salad", "Fruit Salad");

    companion object {
        val default: AlbumArtPaletteStyle = TONAL_SPOT

        fun fromStorageKey(value: String?): AlbumArtPaletteStyle {
            return entries.firstOrNull { it.storageKey == value } ?: default
        }
    }
}
