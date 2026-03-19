package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qqmusic_playlists")
data class QqMusicPlaylistEntity(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "song_count") val songCount: Int,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long
)
