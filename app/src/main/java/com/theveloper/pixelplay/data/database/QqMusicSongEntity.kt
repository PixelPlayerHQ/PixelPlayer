package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song

@Entity(tableName = "qqmusic_songs")
data class QqMusicSongEntity(
    @PrimaryKey val id: String,                          // QQ song composite ID
    @ColumnInfo(name = "song_mid") val songMid: String,  // QQ song MID (unique identifier)
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val title: String,
    val artist: String,
    val album: String,
    @ColumnInfo(name = "album_mid") val albumMid: String?,
    val duration: Long,                                   // milliseconds
    @ColumnInfo(name = "album_art_url") val albumArtUrl: String?,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    val bitrate: Int?,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

/**
 * Convert a [QqMusicSongEntity] to the app's [Song] data model.
 */
fun QqMusicSongEntity.toSong(): Song {
    return Song(
        id = "qqmusic_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = "",
        contentUriString = "qqmusic://$songMid",
        albumArtUriString = albumArtUrl,
        duration = duration,
        mimeType = mimeType,
        bitrate = bitrate,
        sampleRate = 0,
        year = 0,
        trackNumber = 0,
        dateAdded = dateAdded,
        isFavorite = false,
        qqMusicMid = songMid
    )
}

/**
 * Convert a [Song] to a [QqMusicSongEntity] for database storage.
 */
fun Song.toQqMusicEntity(playlistId: Long): QqMusicSongEntity {
    val resolvedMid = qqMusicMid ?: ""
    return QqMusicSongEntity(
        id = "${playlistId}_${resolvedMid}",
        songMid = resolvedMid,
        playlistId = playlistId,
        title = title,
        artist = artist,
        album = album,
        albumMid = null,
        duration = duration,
        albumArtUrl = albumArtUriString,
        mimeType = mimeType ?: "audio/mpeg",
        bitrate = bitrate,
        dateAdded = dateAdded
    )
}
