package com.theveloper.pixelplay.data.worker

import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.SongEntity
import com.theveloper.pixelplay.utils.LocalArtworkUri
import com.theveloper.pixelplay.utils.normalizeMetadataText
import com.theveloper.pixelplay.utils.normalizeMetadataTextOrEmpty
import com.theveloper.pixelplay.utils.splitArtistsByDelimiters

internal data class AlbumGroupingKey(
    val normalizedTitle: String,
    val identity: String
)

internal fun resolveAlbumArtist(
    rawAlbumArtist: String?,
    metadataAlbumArtist: String?
): String? {
    return sequenceOf(metadataAlbumArtist, rawAlbumArtist)
        .mapNotNull { candidate ->
            candidate.normalizeMetadataText()
                ?.takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
        }
        .firstOrNull()
}

internal fun buildAlbumGroupingKey(song: SongEntity): AlbumGroupingKey {
    return buildAlbumGroupingKey(
        albumName = song.albumName,
        albumArtist = song.albumArtist,
        albumArtUriString = song.albumArtUriString,
        parentDirectoryPath = song.parentDirectoryPath,
        fallbackAlbumId = song.albumId,
        preferStableLocalIdentity = LocalArtworkUri.isLikelyLocalMedia(song.contentUriString)
    )
}

internal fun buildAlbumGroupingKeys(album: AlbumEntity): List<AlbumGroupingKey> {
    val albumKeys = mutableListOf<AlbumGroupingKey>()

    albumKeys += buildAlbumGroupingKey(
        albumName = album.title,
        albumArtist = album.artistName,
        albumArtUriString = album.albumArtUriString,
        parentDirectoryPath = null,
        fallbackAlbumId = album.id
    )

    albumKeys += AlbumGroupingKey(
        normalizedTitle = album.title.normalizeMetadataTextOrEmpty()
            .ifBlank { "Unknown Album" }
            .lowercase(),
        identity = "media:${album.id}"
    )

    if (!album.albumArtUriString.isNullOrBlank()) {
        albumKeys += AlbumGroupingKey(
            normalizedTitle = album.title.normalizeMetadataTextOrEmpty()
                .ifBlank { "Unknown Album" }
                .lowercase(),
            identity = "art:${album.albumArtUriString.trim()}"
        )
    }

    return albumKeys.distinct()
}

internal fun chooseAlbumDisplayArtist(
    songs: List<SongEntity>,
    preferAlbumArtist: Boolean
): String {
    if (songs.isEmpty()) return "Unknown Artist"

    val albumArtist = mostCommonValue(
        songs.mapNotNull { song ->
            song.albumArtist.normalizeMetadataText()?.takeIf { it.isNotBlank() }
        }
    )
    val trackArtist = mostCommonValue(
        songs.map { song ->
            song.artistName.normalizeMetadataTextOrEmpty()
        }
    )

    return when {
        preferAlbumArtist && albumArtist != null -> albumArtist
        trackArtist != null -> trackArtist
        albumArtist != null -> albumArtist
        else -> "Unknown Artist"
    }
}

internal fun resolveAlbumDisplayArtistId(
    displayArtist: String,
    songs: List<SongEntity>,
    artistNameToId: Map<String, Long>,
    artistDelimiters: List<String>,
    wordDelimiters: List<String> = emptyList()
): Long {
    artistNameToId[displayArtist.trim()]?.let { return it }

    val primaryArtistName = displayArtist
        .splitArtistsByDelimiters(artistDelimiters, wordDelimiters)
        .firstOrNull()
        ?.trim()
    if (!primaryArtistName.isNullOrEmpty()) {
        artistNameToId[primaryArtistName]?.let { return it }
    }

    return songs.firstOrNull()?.artistId ?: 0L
}

private fun mostCommonValue(values: List<String>): String? {
    return values
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .groupingBy { it }
        .eachCount()
        .maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenByDescending { it.key.length })
        ?.key
}

private fun buildAlbumGroupingKey(
    albumName: String,
    albumArtist: String?,
    albumArtUriString: String?,
    parentDirectoryPath: String?,
    fallbackAlbumId: Long,
    preferStableLocalIdentity: Boolean = false
): AlbumGroupingKey {
    val normalizedTitle = albumName.normalizeMetadataTextOrEmpty()
        .ifBlank { "Unknown Album" }
        .lowercase()
    val stableLocalIdentity = when {
        !parentDirectoryPath.isNullOrBlank() -> {
            "dir:${parentDirectoryPath.trim().lowercase()}"
        }
        else -> {
            "media:$fallbackAlbumId"
        }
    }
    val identity = when {
        !albumArtist.isNullOrBlank() -> {
            "artist:${albumArtist.normalizeMetadataTextOrEmpty().lowercase()}"
        }
        preferStableLocalIdentity -> {
            stableLocalIdentity
        }
        !albumArtUriString.isNullOrBlank() -> {
            "art:${albumArtUriString.trim()}"
        }
        !parentDirectoryPath.isNullOrBlank() -> {
            "dir:${parentDirectoryPath.trim().lowercase()}"
        }
        else -> {
            "media:$fallbackAlbumId"
        }
    }

    return AlbumGroupingKey(
        normalizedTitle = normalizedTitle,
        identity = identity
    )
}
