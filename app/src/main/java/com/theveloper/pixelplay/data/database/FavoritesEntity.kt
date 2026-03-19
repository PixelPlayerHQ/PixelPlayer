package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["timestamp"], unique = false)
    ]
)
data class FavoritesEntity(
    @PrimaryKey
    @SerializedName(value = "songId", alternate = ["song_id"])
    val songId: Long,
    @SerializedName(value = "isFavorite", alternate = ["is_favorite"])
    val isFavorite: Boolean = true,
    @SerializedName(value = "timestamp", alternate = ["addedAt", "added_at"])
    val timestamp: Long = System.currentTimeMillis()
)
