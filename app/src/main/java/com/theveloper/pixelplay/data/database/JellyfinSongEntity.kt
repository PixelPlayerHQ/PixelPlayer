package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.jellyfin.model.JellyfinSong
import com.theveloper.pixelplay.data.model.Song

@Entity(
    tableName = "jellyfin_songs",
    indices = [
        Index(value = ["jellyfin_id"]),
        Index(value = ["playlist_id"]),
        Index(value = ["playlist_id", "date_added"])
    ]
)
data class JellyfinSongEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "jellyfin_id") val jellyfinId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id") val artistId: String?,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: String?,
    val duration: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "disc_number") val discNumber: Int,
    val year: Int,
    val genre: String?,
    val bitRate: Int?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val path: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

fun JellyfinSongEntity.toSong(): Song {
    return Song(
        id = "jellyfin_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = path,
        contentUriString = "jellyfin://$jellyfinId",
        albumArtUriString = "jellyfin_cover://$jellyfinId",
        duration = duration,
        genre = genre,
        mimeType = mimeType,
        bitrate = bitRate,
        sampleRate = null,
        year = year,
        trackNumber = trackNumber,
        dateAdded = dateAdded,
        isFavorite = false
    )
}

fun JellyfinSong.toEntity(playlistId: String): JellyfinSongEntity {
    return JellyfinSongEntity(
        id = "${playlistId}_$id",
        jellyfinId = id,
        playlistId = playlistId,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        duration = duration,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        mimeType = resolvedMimeType,
        path = path,
        dateAdded = System.currentTimeMillis()
    )
}
