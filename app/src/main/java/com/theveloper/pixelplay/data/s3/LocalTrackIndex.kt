package com.theveloper.pixelplay.data.s3

import com.theveloper.pixelplay.data.database.SongMatchKey
import com.theveloper.pixelplay.data.stream.CloudMusicUtils
import kotlin.math.abs

/**
 * Index of on-device tracks. The S3 mirror skips objects that have a local copy, so the
 * library shows the local file instead of a duplicate stream.
 */
class LocalTrackIndex(localSongs: List<SongMatchKey>) {

    private class Local(val artist: String, val duration: Long)

    private val byTitle: Map<String, List<Local>> =
        localSongs.groupBy({ norm(it.title) }, { Local(primaryArtist(it.artistName), it.duration) })

    private val fileNames: Set<String> =
        localSongs.mapNotNullTo(HashSet()) { baseName(it.filePath).takeIf(String::isNotEmpty) }

    /**
     * A local copy is the same file name, or the same title with either the same primary artist
     * or (when one side's artist is missing or spelled differently) the same length.
     */
    fun hasLocalCopy(title: String, artist: String, durationMs: Long, objectKey: String = ""): Boolean {
        if (objectKey.isNotEmpty() && baseName(objectKey) in fileNames) return true
        val candidates = byTitle[norm(title).ifEmpty { return false }] ?: return false
        val s3Artist = primaryArtist(artist)
        return candidates.any { local ->
            val closeLength = local.duration > 0 && durationMs > 0 &&
                abs(local.duration - durationMs) <= DURATION_TOLERANCE_MS
            val sameArtist = local.artist.isNotEmpty() && local.artist == s3Artist
            // Unknown durations (S3 tags not probed yet) match on title + artist alone.
            closeLength || (sameArtist && (local.duration <= 0 || durationMs <= 0))
        }
    }

    companion object {
        const val DURATION_TOLERANCE_MS = 3_000L
        private val ARTIST_SEPARATORS = Regex("[,;/&]")
        private val BRACKETED_SUFFIX = Regex("""\s*[(\[][^)\]]*[)\]]""")

        /** Lowercased, without "(Remastered)"-style suffixes or punctuation. */
        private fun norm(value: String) = value.lowercase()
            .replace(BRACKETED_SUFFIX, "")
            .filter { it.isLetterOrDigit() }

        // Tags differ in how they join artists ("A, B" vs "A; B"), so also cut at common separators.
        private fun primaryArtist(artist: String) = norm(
            CloudMusicUtils.parseArtistNames(artist).firstOrNull().orEmpty().split(ARTIST_SEPARATORS).first()
        )

        /** "Music/Artist - Song.flac" -> "artistsong". */
        private fun baseName(path: String) = norm(path.substringAfterLast('/').substringBeforeLast('.'))
    }
}
