package com.theveloper.pixelplay.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import com.theveloper.pixelplay.data.model.TransitionSettings

@Entity(
    tableName = "transition_rules",
    indices = [Index(value = ["playlistId", "fromTrackId", "toTrackId"], unique = true)]
)
data class TransitionRuleEntity(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    val id: Long = 0,
    @SerializedName("playlistId")
    val playlistId: String,
    @SerializedName(value = "fromTrackId", alternate = ["fromSongId", "from_song_id"])
    val fromTrackId: String?,
    @SerializedName(value = "toTrackId", alternate = ["toSongId", "to_song_id"])
    val toTrackId: String?,
    @Embedded
    @SerializedName("settings")
    val settings: TransitionSettings
)
