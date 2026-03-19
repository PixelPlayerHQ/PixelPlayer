package com.theveloper.pixelplay.shared

import kotlinx.serialization.Serializable

@Serializable
data class WearFavoriteSyncRequest(
    val songIds: List<String>,
)

@Serializable
data class WearFavoriteStateEntry(
    val songId: String,
    val isFavorite: Boolean,
)

@Serializable
data class WearFavoriteSyncResponse(
    val states: List<WearFavoriteStateEntry>,
)
