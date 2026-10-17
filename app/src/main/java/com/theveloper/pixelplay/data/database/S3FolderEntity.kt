package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "s3_folders")
data class S3FolderEntity(
    @PrimaryKey val id: String,                                    // S3 prefix, e.g. "Music/"
    val name: String,                                              // display name (last segment)
    @ColumnInfo(name = "song_count") val songCount: Int = 0,
    @ColumnInfo(name = "last_sync_time") val lastSyncTime: Long = 0
)
