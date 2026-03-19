package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey
    @SerializedName(value = "songId", alternate = ["song_id"])
    val songId: Long,
    @SerializedName("content")
    val content: String,
    @SerializedName(value = "isSynced", alternate = ["is_synced"])
    val isSynced: Boolean = false,
    @SerializedName("source")
    val source: String? = null // "local", "remote", "embedded" - optional
)
