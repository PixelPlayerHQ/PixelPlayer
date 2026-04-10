package com.theveloper.pixelplay.data.jellyfin.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class JellyfinAlbum(
    val id: String,
    val name: String,
    val artist: String,
    val artistId: String? = null,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val year: Int = 0,
    val genre: String? = null
) : Parcelable {
    companion object {
        fun empty() = JellyfinAlbum(
            id = "",
            name = "",
            artist = "",
            artistId = null,
            songCount = 0,
            duration = 0L,
            year = 0,
            genre = null
        )
    }
}
