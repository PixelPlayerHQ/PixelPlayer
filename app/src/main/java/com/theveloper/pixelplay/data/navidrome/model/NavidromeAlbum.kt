package com.theveloper.pixelplay.data.navidrome.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents an album from a Navidrome/Subsonic server.
 *
 * Based on the Subsonic API "AlbumID3" entity.
 *
 * @property id The unique identifier of the album on the server
 * @property name The album name
 * @property artist The artist name
 * @property artistId The artist ID on the server (optional)
 * @property coverArt The cover art ID (used to construct cover art URL)
 * @property songCount The number of songs in the album
 * @property duration The total duration in milliseconds
 * @property playCount The play count (optional)
 * @property year The release year (optional)
 * @property genre The genre (optional)
 */
@Immutable
@Parcelize
data class NavidromeAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L, // milliseconds
    val playCount: Int = 0,
    val year: Int = 0,
    val genre: String? = null
) : Parcelable {
    companion object {
        fun empty() = NavidromeAlbum(
            id = "",
            name = "",
            artist = "",
            artistId = null,
            coverArt = null,
            songCount = 0,
            duration = 0L,
            playCount = 0,
            year = 0,
            genre = null
        )
    }
}
