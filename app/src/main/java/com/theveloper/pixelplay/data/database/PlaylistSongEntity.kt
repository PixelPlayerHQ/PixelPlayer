package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    indices = [
        Index(value = ["playlist_id", "sort_order"]),
        Index(value = ["song_id"])
    ]
)
data class PlaylistSongEntity(
    @ColumnInfo(name = "playlist_id")
    val playlistId: String,
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int,
)
