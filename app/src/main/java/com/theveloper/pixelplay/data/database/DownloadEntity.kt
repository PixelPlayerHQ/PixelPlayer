package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object DownloadStatus {
    const val PENDING = 0
    const val DOWNLOADING = 1
    const val COMPLETED = 2
    const val FAILED = 3
    const val PAUSED = 4
    const val CANCELLED = 5
}

@Entity(
    tableName = "downloads",
    indices = [Index(value = ["song_id"], unique = true)]
)
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "song_id") val songId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "status") val status: Int = DownloadStatus.PENDING,
    @ColumnInfo(name = "progress") val progress: Float = 0f,
    @ColumnInfo(name = "total_size") val totalSize: Long = 0L,
    @ColumnInfo(name = "download_path") val downloadPath: String? = null,
    @ColumnInfo(name = "extension_id") val extensionId: String,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "error_message") val errorMessage: String? = null
)
