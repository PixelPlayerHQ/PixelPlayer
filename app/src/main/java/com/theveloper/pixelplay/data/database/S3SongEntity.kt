package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.s3.S3Repository

@Entity(
    tableName = "s3_songs",
    indices = [
        Index(value = ["object_key"]),
        Index(value = ["folder_id"]),
        Index(value = ["folder_id", "date_added"])
    ]
)
data class S3SongEntity(
    @PrimaryKey val id: String,                                    // "{folderId}_{objectKey}"
    @ColumnInfo(name = "object_key") val objectKey: String,        // full S3 object key
    @ColumnInfo(name = "folder_id") val folderId: String,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: Long,
    val duration: Long,                                             // milliseconds (0 = unknown)
    val mimeType: String,
    val bitrate: Int?,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    @ColumnInfo(name = "date_added") val dateAdded: Long,
    @ColumnInfo(name = "date_modified") val dateModified: Long,
    @ColumnInfo(name = "album_art_uri") val albumArtUri: String? = null,
    @ColumnInfo(name = "metadata_probed", defaultValue = "0") val metadataProbed: Boolean = false
)

fun S3SongEntity.toSong(): Song {
    return Song(
        id = "s3_$objectKey",
        title = title,
        artist = artist,
        artistId = artistIdFor(artist),
        album = album,
        albumId = albumIdFor(album),
        path = "",
        contentUriString = "s3://$objectKey",
        albumArtUriString = albumArtUri,
        duration = duration,
        genre = S3Repository.S3_GENRE,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 0,
        year = 0,
        trackNumber = 0,
        dateAdded = dateAdded,
        isFavorite = false,
        s3ObjectKey = objectKey
    )
}

fun Song.toS3SongEntity(folderId: String): S3SongEntity {
    val resolvedKey = s3ObjectKey ?: ""
    return S3SongEntity(
        id = "${folderId}_${resolvedKey}",
        objectKey = resolvedKey,
        folderId = folderId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        mimeType = mimeType ?: "audio/mpeg",
        bitrate = bitrate,
        fileSize = 0L,
        dateAdded = dateAdded,
        dateModified = dateAdded
    )
}

internal fun artistIdFor(artist: String): Long =
    -(S3Repository.S3_ARTIST_ID_OFFSET + artist.lowercase().hashCode().toLong().let { if (it < 0) -it else it })

internal fun albumIdFor(album: String): Long =
    -(S3Repository.S3_ALBUM_ID_OFFSET + album.lowercase().hashCode().toLong().let { if (it < 0) -it else it })
