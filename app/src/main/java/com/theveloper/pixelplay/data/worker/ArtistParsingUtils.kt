package com.theveloper.pixelplay.data.worker

import com.theveloper.pixelplay.utils.extractArtistsFromTitle
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters

internal fun collectArtistNames(
    rawArtistName: String,
    title: String,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList(),
    extractFromTitle: Boolean = true
): List<String> {
    val splitFromArtist = rawArtistName.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
    if (!extractFromTitle) {
        return splitFromArtist
    }

    val (_, titleArtists) = title.extractArtistsFromTitle(artistDelimiters, wordDelimiters)
    if (titleArtists.isEmpty()) {
        return splitFromArtist
    }

    val combined = splitFromArtist.toMutableList()
    titleArtists.forEach { titleArtist ->
        if (combined.none { it.equals(titleArtist, ignoreCase = true) }) {
            combined.add(titleArtist)
        }
    }
    return combined
}

internal fun choosePreferredArtistName(
    localArtistName: String,
    mediaStoreArtistName: String,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList()
): String {
    val localTrimmed = localArtistName.trim()
    val mediaTrimmed = mediaStoreArtistName.trim()

    if (localTrimmed.isBlank()) return mediaStoreArtistName
    if (mediaTrimmed.isBlank()) return localArtistName

    val localArtists = localTrimmed.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
    val mediaArtists = mediaTrimmed.splitArtistsByDelimiters(artistDelimiters, wordDelimiters)

    return when {
        mediaArtists.size > localArtists.size -> mediaStoreArtistName
        localArtists.size > mediaArtists.size -> localArtistName
        mediaTrimmed.length > localTrimmed.length -> mediaStoreArtistName
        localTrimmed.length > mediaTrimmed.length -> localArtistName
        else -> mediaStoreArtistName
    }
}
