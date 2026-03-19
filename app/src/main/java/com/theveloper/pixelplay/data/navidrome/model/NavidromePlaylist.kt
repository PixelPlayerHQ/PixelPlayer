package com.theveloper.pixelplay.data.navidrome.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents a playlist from a Navidrome/Subsonic server.
 *
 * Based on the Subsonic API "Playlist" entity.
 *
 * @property id The unique identifier of the playlist on the server
 * @property name The playlist name
 * @property comment The playlist description/comment (optional)
 * @property owner The owner username
 * @property songCount The number of songs in the playlist
 * @property duration The total duration in milliseconds
 * @property coverArt The cover art ID (used to construct cover art URL)
 * @property public Whether the playlist is public
 * @property created The creation timestamp (epoch milliseconds)
 * @property changed The last modified timestamp (epoch milliseconds)
 */
@Immutable
@Parcelize
data class NavidromePlaylist(
    val id: String,
    val name: String,
    val comment: String? = null,
    val owner: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L, // milliseconds
    val coverArt: String? = null,
    val public: Boolean = false,
    val created: Long = 0L,
    val changed: Long = 0L
) : Parcelable {
    companion object {
        fun empty() = NavidromePlaylist(
            id = "",
            name = "",
            comment = null,
            owner = null,
            songCount = 0,
            duration = 0L,
            coverArt = null,
            public = false,
            created = 0L,
            changed = 0L
        )
    }
}
