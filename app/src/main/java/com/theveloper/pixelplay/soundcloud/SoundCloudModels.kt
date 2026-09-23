package com.theveloper.pixelplay.soundcloud

data class SoundCloudSearchHit(
    val url: String,
    val title: String,
    val artist: String,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val kind: Kind = Kind.TRACK,
    val streamCount: Long = 0,
) {
    enum class Kind { TRACK, PLAYLIST }
}

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
