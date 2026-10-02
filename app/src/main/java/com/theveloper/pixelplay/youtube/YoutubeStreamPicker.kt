package com.theveloper.pixelplay.youtube

internal data class YoutubeAudioCandidate(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
)

/**
 * Chooses a URL ExoPlayer can open.
 * YouTube Music tracks often expose a SABR or HLS link next to a plain audio file.
 * Playing the manifest first makes that song fail and the playlist skips it.
 */
internal fun pickPlayableAudio(
    candidates: List<YoutubeAudioCandidate>,
    avoidUrl: String? = null,
): YoutubeAudioCandidate? {
    val ranked = candidates
        .filter(::isPlayableAudioCandidate)
        .sortedByDescending(::audioCandidateScore)
    return ranked.firstOrNull { it.url != avoidUrl }
}

private fun isPlayableAudioCandidate(candidate: YoutubeAudioCandidate): Boolean {
    val url = candidate.url.trim()
    if (!url.startsWith("http")) return false
    if (url.contains("encrypted", ignoreCase = true)) return false
    val lower = url.lowercase()
    if ("/sabr" in lower || "source=sabr" in lower) return false
    val mime = candidate.mimeType.lowercase()
    val audio = mime.startsWith("audio/")
    val muxedAudio = mime.startsWith("video/") && (
        "mp4a" in mime || "opus" in mime || "vorbis" in mime
        )
    val hls = "mpegurl" in mime || lower.contains(".m3u8")
    return audio || muxedAudio || hls
}

private fun audioCandidateScore(candidate: YoutubeAudioCandidate): Int {
    val mime = candidate.mimeType.lowercase()
    val kind = when {
        mime.startsWith("audio/mp4") || mime.startsWith("audio/m4a") -> 4_000_000
        mime.startsWith("audio/webm") -> 3_000_000
        mime.startsWith("audio/") -> 2_000_000
        mime.startsWith("video/") -> 1_000_000
        else -> 0
    }
    return kind + candidate.bitrate.coerceAtLeast(0)
}
