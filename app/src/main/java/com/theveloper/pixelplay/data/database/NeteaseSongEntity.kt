package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song

@Entity(
    tableName = "netease_songs",
    indices = [
        Index(value = ["netease_id"]),
        Index(value = ["playlist_id"]),
        Index(value = ["playlist_id", "date_added"])
    ]
)
data class NeteaseSongEntity(
    @PrimaryKey val id: String,                          // Netease song ID as string
    @ColumnInfo(name = "netease_id") val neteaseId: Long, // Raw Netease numeric ID
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val duration: Long,                                   // milliseconds
    @ColumnInfo(name = "album_art_url") val albumArtUrl: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val bitrate: Int?,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

/**
 * Convert a [NeteaseSongEntity] to the app's [Song] data model.
 */
fun NeteaseSongEntity.toSong(): Song {
    return Song(
        id = "netease_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = albumId,
        path = "",
        contentUriString = "netease://$neteaseId",
        albumArtUriString = albumArtUrl,
        duration = duration,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 0,
        year = 0,
        trackNumber = 0,
        dateAdded = dateAdded,
        isFavorite = false,
        neteaseId = neteaseId
    )
}

/**
 * Convert a [Song] to a [NeteaseSongEntity] for database storage.
 */
fun Song.toNeteaseEntity(playlistId: Long): NeteaseSongEntity {
    val resolvedNeteaseId = neteaseId ?: 0L
    return NeteaseSongEntity(
        id = "${playlistId}_${resolvedNeteaseId}",
        neteaseId = resolvedNeteaseId,
        playlistId = playlistId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        albumArtUrl = albumArtUriString,
        mimeType = mimeType ?: "audio/mpeg",
        bitrate = bitrate,
        dateAdded = dateAdded
    )
}
