package com.theveloper.pixelplay.data.navidrome.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents an artist from a Navidrome/Subsonic server.
 *
 * Based on the Subsonic API "ArtistID3" entity.
 *
 * @property id The unique identifier of the artist on the server
 * @property name The artist name
 * @property coverArt The cover art ID (used to construct artist image URL)
 * @property albumCount The number of albums by this artist
 * @property artistImageUrl Direct URL to the artist image (optional)
 */
@Immutable
@Parcelize
data class NavidromeArtist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int = 0,
    val artistImageUrl: String? = null
) : Parcelable {
    companion object {
        fun empty() = NavidromeArtist(
            id = "",
            name = "",
            coverArt = null,
            albumCount = 0,
            artistImageUrl = null
        )
    }
}
