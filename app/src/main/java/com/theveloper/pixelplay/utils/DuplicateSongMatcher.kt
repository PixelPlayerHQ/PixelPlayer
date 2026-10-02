package com.theveloper.pixelplay.utils

import java.text.Normalizer
import kotlin.math.abs

/**
 * Detects songs that are the same track even though they come from different sources
 * (e.g. a file downloaded from a Telegram channel into local storage and the very same
 * audio message streamed from that channel).
 *
 * Two songs are duplicates when their normalized titles are identical, nothing contradicts
 * the match and at least one more signal confirms it:
 * - contradictions: both durations are known but differ by more than [DURATION_TOLERANCE_MS],
 *   or both artists are known but share no credit;
 * - confirmations: both durations are known and close, or the artists share a credit.
 *
 * Artists are compared credit by credit ("A feat. B" -> {A, B}). Substrings never count, so
 * "Queen" does not match "Queens of the Stone Age".
 *
 * Songs without tags usually get their file name as title (e.g. "Track.mp3"). Their artist
 * is meaningless, so the audio extension is ignored for the title comparison and their
 * artist is treated as unknown.
 */
class DuplicateSongMatcher {

    private class Entry(
        val songId: Long,
        val credits: Set<String>,
        val bracketSegments: Set<String>,
        val durationMs: Long
    )

    private val entriesByTitle = HashMap<String, MutableList<Entry>>()

    fun add(songId: Long, title: String, artist: String, durationMs: Long) {
        val key = titleKey(title)
        if (key.isEmpty()) return
        entriesByTitle.getOrPut(key) { mutableListOf() }
            .add(buildEntry(songId, title, artist, durationMs))
    }

    /** Returns the id of a previously added song that is the same track, or null. */
    fun findDuplicate(title: String, artist: String, durationMs: Long): Long? {
        val key = titleKey(title)
        if (key.isEmpty()) return null
        val candidates = entriesByTitle[key] ?: return null
        val probe = buildEntry(NO_ID, title, artist, durationMs)
        return candidates.firstOrNull { isSameTrack(it, probe) }?.songId
    }

    fun isDuplicate(title: String, artist: String, durationMs: Long): Boolean =
        findDuplicate(title, artist, durationMs) != null

    private fun buildEntry(songId: Long, title: String, artist: String, durationMs: Long) = Entry(
        songId = songId,
        credits = if (hasAudioExtension(title)) emptySet() else splitCredits(artist),
        bracketSegments = bracketSegments(title),
        durationMs = durationMs
    )

    companion object {
        const val DURATION_TOLERANCE_MS = 2_000L
        private const val NO_ID = Long.MIN_VALUE

        private val UNKNOWN_ARTIST_MARKERS = setOf("unknown", "unknownartist", "نامشخص", "ناشناس")

        private val AUDIO_EXTENSION_REGEX = Regex(
            "\\.(mp3|m4a|m4b|aac|flac|ogg|oga|opus|wav|wma|aif|aiff|alac|ape|wv|mka)\\s*$",
            RegexOption.IGNORE_CASE
        )

        private val CREDIT_SEPARATOR_REGEX = Regex(
            "[,;&/|+×،]|\\s+(?:x|and|with|feat\\.?|ft\\.?|featuring)\\s+",
            RegexOption.IGNORE_CASE
        )

        private val BRACKET_SEGMENT_REGEX = Regex("[(\\[{]([^)\\]}]*)[)\\]}]")

        // Words that may follow an artist name inside a bracketed title segment,
        // e.g. "(BLH Remix)" credits the artist "BLH".
        private val SEGMENT_SUFFIXES = listOf("remix", "mix", "edit", "version", "cover")

        internal fun hasAudioExtension(title: String): Boolean =
            AUDIO_EXTENSION_REGEX.containsMatchIn(title)

        private fun titleKey(title: String): String =
            normalizeForMatching(title.replace(AUDIO_EXTENSION_REGEX, ""))

        private fun isSameTrack(a: Entry, b: Entry): Boolean {
            val durationsKnown = a.durationMs > 0L && b.durationMs > 0L
            if (durationsKnown && abs(a.durationMs - b.durationMs) > DURATION_TOLERANCE_MS) {
                return false
            }
            val artistsKnown = !isUnknown(a.credits) && !isUnknown(b.credits)
            if (artistsKnown) return artistsCompatible(a, b)
            // Without usable artists the title alone is too weak; require matching durations.
            return durationsKnown
        }

        private fun splitCredits(artist: String): Set<String> {
            if (artist.isBlank()) return emptySet()
            return artist.split(CREDIT_SEPARATOR_REGEX)
                .map { normalizeForMatching(it) }
                .filterTo(HashSet()) { it.isNotEmpty() }
        }

        private fun bracketSegments(title: String): Set<String> {
            val matches = BRACKET_SEGMENT_REGEX.findAll(title.replace(AUDIO_EXTENSION_REGEX, ""))
            var segments: MutableSet<String>? = null
            matches.forEach { match ->
                val segment = normalizeForMatching(match.groupValues[1])
                if (segment.isEmpty()) return@forEach
                val target = segments ?: HashSet<String>().also { segments = it }
                target.add(segment)
                SEGMENT_SUFFIXES.forEach { suffix ->
                    if (segment.endsWith(suffix) && segment.length > suffix.length) {
                        target.add(segment.removeSuffix(suffix))
                    }
                }
            }
            return segments ?: emptySet()
        }

        private fun isUnknown(credits: Set<String>): Boolean =
            credits.isEmpty() || credits.all { it in UNKNOWN_ARTIST_MARKERS }

        private fun artistsCompatible(a: Entry, b: Entry): Boolean {
            if (a.credits.any { it in b.credits }) return true
            // The artist tag of one song may only appear as a credit in the other's title,
            // e.g. title "Alaki (BLH Remix)" with artist tag "BLH Remix".
            return b.credits.any { it in a.bracketSegments } ||
                a.credits.any { it in b.bracketSegments }
        }

        /**
         * Lowercases, strips diacritics/punctuation/whitespace, maps any decimal digit to ASCII
         * and unifies Arabic/Persian letter variants, so tag differences such as
         * "Song - Name" vs "song name" or "۲" vs "2" still match.
         */
        internal fun normalizeForMatching(value: String): String {
            if (value.isBlank()) return ""
            val decomposed = Normalizer.normalize(value, Normalizer.Form.NFKD)
            val sb = StringBuilder(decomposed.length)
            for (ch in decomposed) {
                when {
                    ch == '\u0640' -> Unit // Arabic tatweel (kashida)
                    Character.isDigit(ch) -> sb.append(Character.digit(ch, 10))
                    Character.isLetter(ch) -> sb.append(
                        when (ch) {
                            'ي', 'ى' -> 'ی'
                            'ك' -> 'ک'
                            'ة', 'ە', 'ہ' -> 'ه'
                            else -> ch.lowercaseChar()
                        }
                    )
                }
            }
            return sb.toString()
        }
    }
}
