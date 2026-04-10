package com.theveloper.pixelplay.data.jellyfin.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class JellyfinArtist(
    val id: String,
    val name: String,
    val albumCount: Int = 0
) : Parcelable {
    companion object {
        fun empty() = JellyfinArtist(
            id = "",
            name = "",
            albumCount = 0
        )
    }
}
