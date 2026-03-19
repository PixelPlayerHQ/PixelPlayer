package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

/**
 * Room entity for storing song engagement statistics.
 * This replaces the JSON-based storage in DailyMixManager for better performance
 * and structured querying.
 */
@Entity(
    tableName = "song_engagements",
    indices = [
        Index(value = ["play_count"], unique = false)
    ]
)
data class SongEngagementEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    @SerializedName(value = "songId", alternate = ["song_id"])
    val songId: String,
    
    @ColumnInfo(name = "play_count")
    @SerializedName(value = "playCount", alternate = ["play_count", "score", "plays"])
    val playCount: Int = 0,
    
    @ColumnInfo(name = "total_play_duration_ms")
    @SerializedName(
        value = "totalPlayDurationMs",
        alternate = ["total_play_duration_ms", "totalDuration", "total_duration", "durationMs", "duration_ms"]
    )
    val totalPlayDurationMs: Long = 0L,
    
    @ColumnInfo(name = "last_played_timestamp")
    @SerializedName(
        value = "lastPlayedTimestamp",
        alternate = ["last_played_timestamp", "lastPlayedAt", "last_played_at", "timestamp"]
    )
    val lastPlayedTimestamp: Long = 0L
)
