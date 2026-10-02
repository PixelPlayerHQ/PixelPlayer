package com.theveloper.pixelplay.youtube

enum class YoutubeSection {
    HOME,
    SEARCH,
    LIKED,
    PLAYLISTS,
    RADIO,
}

data class YoutubeShelf(
    val id: String,
    val title: String,
    val items: List<YoutubeHit>,
    val startsRadio: Boolean = false,
)

enum class YoutubeSearchFilter {
    SONGS,
    ALBUMS,
    PLAYLISTS,
}

data class YoutubeHit(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val kind: Kind,
    val album: String = "",
) {
    enum class Kind { TRACK, COLLECTION }
}

/** A page of hits plus the token that fetches the next one, or null at the end. */
data class YoutubePage(
    val hits: List<YoutubeHit>,
    val continuation: String?,
)

data class YoutubeResolvedTrack(
    val watchUrl: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val streamUrl: String,
    val mimeType: String?,
    val artworkUrl: String?,
    val album: String = "",
)

data class YoutubeCredits(
    val artist: String,
    val album: String,
)
