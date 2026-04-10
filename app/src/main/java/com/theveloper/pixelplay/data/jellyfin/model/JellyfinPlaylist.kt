package com.theveloper.pixelplay.data.jellyfin.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class JellyfinPlaylist(
    val id: String,
    val name: String,
    val songCount: Int = 0,
    val duration: Long = 0L,
    val created: Long = 0L,
    val changed: Long = 0L
) : Parcelable {
    companion object {
        fun empty() = JellyfinPlaylist(
            id = "",
            name = "",
            songCount = 0,
            duration = 0L,
            created = 0L,
            changed = 0L
        )
    }
}
