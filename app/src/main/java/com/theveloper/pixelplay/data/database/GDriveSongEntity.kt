package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song
import kotlin.math.absoluteValue

@Entity(
    tableName = "gdrive_songs",
    indices = [
        Index(value = ["drive_file_id"]),
        Index(value = ["folder_id"]),
        Index(value = ["folder_id", "date_added"])
    ]
)
data class GDriveSongEntity(
    @PrimaryKey val id: String,                                    // "{folderId}_{driveFileId}"
    @ColumnInfo(name = "drive_file_id") val driveFileId: String,   // Google Drive file ID
    @ColumnInfo(name = "folder_id") val folderId: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val duration: Long,                                             // milliseconds
    @ColumnInfo(name = "album_art_url") val albumArtUrl: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val bitrate: Int?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long
)

fun GDriveSongEntity.toSong(): Song {
    val syntheticArtistId = -(artist.hashCode().toLong().absoluteValue)
    val syntheticAlbumId = -(album.hashCode().toLong().absoluteValue)

    return Song(
        id = "gdrive_$driveFileId",
        title = title,
        artist = artist,
        artistId = syntheticArtistId,
        album = album,
        albumId = syntheticAlbumId,
        path = "",
        contentUriString = "gdrive://$driveFileId",
        albumArtUriString = albumArtUrl,
        duration = duration,
        genre = "Google Drive",
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 0,
        year = 0,
        trackNumber = 0,
        dateAdded = dateAdded,
        isFavorite = false,
        gdriveFileId = driveFileId
    )
}

fun Song.toGDriveEntity(folderId: String): GDriveSongEntity {
    val resolvedDriveFileId = gdriveFileId ?: ""
    return GDriveSongEntity(
        id = "${folderId}_${resolvedDriveFileId}",
        driveFileId = resolvedDriveFileId,
        folderId = folderId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        albumArtUrl = albumArtUriString,
        mimeType = mimeType ?: "audio/mpeg",
        bitrate = bitrate,
        fileSize = 0L,
        dateAdded = dateAdded,
        dateModified = dateModified
    )
}
