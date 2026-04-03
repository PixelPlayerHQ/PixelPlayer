package com.theveloper.pixelplay.data.model

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize

@Immutable
@Parcelize
data class Album(
    val id: Long, // MediaStore.Audio.Albums._ID
    val title: String,
    val artist: String,
    val year: Int,
    val dateAdded: Long,
    val albumArtUriString: String?,
    val songCount: Int
) : Parcelable {
    companion object {
        fun empty() = Album(
            id = -1,
            title = "",
            artist = "",
            dateAdded = 0,
            year = 0,
            albumArtUriString = null,
            songCount = 0
        )
    }
}

@Immutable
@Parcelize
data class Artist(
    val id: Long, // MediaStore.Audio.Artists._ID
    val name: String,
    val songCount: Int,
    val imageUrl: String? = null, // Deezer artist image URL (from API)
    val customImageUri: String? = null // User-defined custom artist image (local file path)
) : Parcelable {
    companion object {
        fun empty() = Artist(
            id = -1,
            name = "",
            songCount = 0,
            imageUrl = null,
            customImageUri = null
        )
    }

    /** Returns the image URL/path to use, preferring the user's custom image. */
    val effectiveImageUrl: String?
        get() = customImageUri?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() }
}

/**
 * Represents a simplified artist reference for multi-artist support.
 * Used when displaying multiple artists for a song.
 */
@Immutable
@Parcelize
data class ArtistRef(
    val id: Long,
    val name: String,
    val isPrimary: Boolean = false
) : Parcelable