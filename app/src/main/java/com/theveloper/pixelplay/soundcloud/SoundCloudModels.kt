package com.theveloper.pixelplay.soundcloud

data class SoundCloudSearchHit(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val kind: Kind = Kind.TRACK,
    val streamCount: Long = 0,
    /**
     * Session lists already know a track can stream. NewPipe lists stay [Availability.UNKNOWN]
     * so playability is checked after the list is on screen.
     */
    val availability: Availability = Availability.UNKNOWN,
) {
    enum class Kind { TRACK, PLAYLIST }
    enum class Availability { UNKNOWN, AVAILABLE }
}

data class SoundCloudFileTags(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String?,
    val artworkUrl: String?,
)

internal fun String?.isSoundCloudPlaceholder(): Boolean {
    val value = this?.trim().orEmpty()
    return value.isEmpty() ||
        value.equals("SoundCloud", ignoreCase = true) ||
        value.equals("<unknown>", ignoreCase = true)
}

data class SoundCloudFeedShelf(
    val id: String,
    val title: String,
    val items: List<SoundCloudSearchHit>,
)

data class SoundCloudResolvedTrack(
    val url: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val streamUrl: String,
    val mimeType: String?,
    val artworkUrl: String?,
)

enum class SoundCloudSection {
    FEED,
    DISCOVER,
    SEARCH,
    LIKES,
    TRACKS,
    PLAYLISTS,
    DOWNLOADS,
}
