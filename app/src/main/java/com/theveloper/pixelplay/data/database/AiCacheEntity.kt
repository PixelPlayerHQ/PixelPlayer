package com.theveloper.pixelplay.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey
    val promptHash: String, // SHA-256 hash
    val responseJson: String,
    val timestamp: Long
)
