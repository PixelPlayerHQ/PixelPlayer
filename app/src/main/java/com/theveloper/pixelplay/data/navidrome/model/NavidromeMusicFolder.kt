package com.theveloper.pixelplay.data.navidrome.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

/**
 * Represents a music folder (library) from a Navidrome/Subsonic server.
 *
 * Based on the Subsonic API "MusicFolder" entity.
 *
 * @property id The unique identifier of the music folder
 * @property name The display name of the music folder
 */
@Immutable
@Parcelize
data class NavidromeMusicFolder(
    val id: String,
    val name: String
) : Parcelable {
    companion object {
        fun empty() = NavidromeMusicFolder(
            id = "",
            name = ""
        )
    }
}
