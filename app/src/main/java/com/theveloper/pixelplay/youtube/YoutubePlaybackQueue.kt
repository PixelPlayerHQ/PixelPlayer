package com.theveloper.pixelplay.youtube

/**
 * A playlist keeps every track, including ones already played. Playback still
 * starts at the song you tapped. [remainingTracks] is only the upcoming suffix,
 * used to warm streams ahead of the current song.
 */
object YoutubePlaybackQueue {
    fun playlistTracks(start: YoutubeHit, source: List<YoutubeHit>): List<YoutubeHit> {
        if (start.kind != YoutubeHit.Kind.TRACK) return emptyList()
        val tracks = source.filter { it.kind == YoutubeHit.Kind.TRACK }
        return if (tracks.any { it.url == start.url }) tracks else listOf(start)
    }

    fun remainingTracks(start: YoutubeHit, source: List<YoutubeHit>): List<YoutubeHit> {
        if (start.kind != YoutubeHit.Kind.TRACK) return emptyList()
        val tracks = source.filter { it.kind == YoutubeHit.Kind.TRACK }
        val index = tracks.indexOfFirst { it.url == start.url }
        return if (index >= 0) tracks.drop(index) else listOf(start)
    }
}
