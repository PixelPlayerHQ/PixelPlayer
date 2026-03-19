package com.theveloper.pixelplay.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.navidrome.model.NavidromeSong

/**
 * Represents a song cached from a Navidrome/Subsonic server.
 *
 * @property id The composite ID (playlistId_songId)
 * @property navidromeId The unique song ID from the Subsonic server
 * @property playlistId The ID of the playlist this song belongs to
 * @property title The song title
 * @property artist The artist name
 * @property artistId The artist ID on the server (optional)
 * @property album The album name
 * @property albumId The album ID on the server (optional)
 * @property coverArtId The cover art ID used to construct the cover art URL
 * @property duration The duration in milliseconds
 * @property trackNumber The track number
 * @property discNumber The disc number
 * @property year The release year
 * @property genre The genre
 * @property bitRate The bitrate in kbps
 * @property mimeType The MIME type
 * @property suffix The file suffix (mp3, flac, etc.)
 * @property path The file path on the server
 * @property dateAdded The timestamp when this record was added
 */
@Entity(
    tableName = "navidrome_songs",
    indices = [
        Index(value = ["navidrome_id"]),
        Index(value = ["playlist_id"]),
        Index(value = ["playlist_id", "date_added"])
    ]
)
data class NavidromeSongEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "navidrome_id") val navidromeId: String,
    @ColumnInfo(name = "playlist_id") val playlistId: String,
    val title: String,
    val artist: String,
    @ColumnInfo(name = "artist_id") val artistId: String?,
    val album: String,
    @ColumnInfo(name = "album_id") val albumId: String?,
    @ColumnInfo(name = "cover_art_id") val coverArtId: String?,
    val duration: Long,
    @ColumnInfo(name = "track_number") val trackNumber: Int,
    @ColumnInfo(name = "disc_number") val discNumber: Int,
    val year: Int,
    val genre: String?,
    val bitRate: Int?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    val suffix: String?,
    val path: String,
    @ColumnInfo(name = "date_added") val dateAdded: Long
)

/**
 * Convert a [NavidromeSongEntity] to the app's [Song] data model.
 */
fun NavidromeSongEntity.toSong(): Song {
    return Song(
        id = "navidrome_$id",
        title = title,
        artist = artist,
        artistId = -1L,
        album = album,
        albumId = -1L,
        path = path,
        contentUriString = "navidrome://$navidromeId",
        albumArtUriString = coverArtId?.let { "navidrome_cover://$it" },
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

/**
 * Convert a [NavidromeSong] to a [NavidromeSongEntity] for database storage.
 */
fun NavidromeSong.toEntity(playlistId: String): NavidromeSongEntity {
    return NavidromeSongEntity(
        id = "${playlistId}_$id",
        navidromeId = id,
        playlistId = playlistId,
        title = title,
        artist = artist,
        artistId = artistId,
        album = album,
        albumId = albumId,
        coverArtId = coverArt,
        duration = duration,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        bitRate = bitRate,
        mimeType = resolvedMimeType,
        suffix = suffix,
        path = path,
        dateAdded = System.currentTimeMillis()
    )
}
