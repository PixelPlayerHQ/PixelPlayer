package com.theveloper.pixelplay.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateSongMatcherTest {

    private fun DuplicateSongMatcher.remember(
        title: String,
        artist: String,
        durationMs: Long,
        songId: Long = 1L
    ) {
        add(songId, title, artist, durationMs)
    }

    @Test
    fun sameTrackWithDifferentFormatting_isDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Song - Name", "The Artist", 200_000)

        assertTrue(matcher.isDuplicate("song name", "the artist", 200_000))
    }

    @Test
    fun durationWithinTolerance_isDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Track", "Artist", 200_000)

        assertTrue(matcher.isDuplicate("Track", "Artist", 201_500))
        assertFalse(matcher.isDuplicate("Track", "Artist", 215_000))
    }

    @Test
    fun differentArtistSameTitle_isNotDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Intro", "Artist A", 60_000)

        assertFalse(matcher.isDuplicate("Intro", "Artist B", 60_000))
    }

    @Test
    fun unknownArtist_matchesOnTitleAndDuration() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Some Title", "<unknown>", 180_000)

        assertTrue(matcher.isDuplicate("Some Title", "Real Artist", 180_000))
    }

    @Test
    fun unknownArtistWithoutDuration_isNotDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Some Title", "<unknown>", 0)

        assertFalse(matcher.isDuplicate("Some Title", "Real Artist", 0))
    }

    @Test
    fun persianTitlesWithArabicLetterVariants_match() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("دلتنگي", "ابی", 240_000)

        assertTrue(matcher.isDuplicate("دلتنگی", "ابی", 240_000))
    }

    @Test
    fun persianDigitsInTitle_matchAsciiDigits() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Track ۲", "Artist", 180_000)

        assertTrue(matcher.isDuplicate("Track 2", "Artist", 180_000))
    }

    @Test
    fun fileNameTitleWithExtensionAndDifferentArtist_isDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Alaki (BLH Remix)", "Poori x Hiphopologist x Sha", 180_000)

        assertTrue(matcher.isDuplicate("Alaki (BLH Remix).mp3", "BLH Remix", 180_400))
    }

    @Test
    fun fileNameTitleMatchesInEitherDirection() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Alaki (BLH Remix).mp3", "BLH Remix", 180_000)

        assertTrue(matcher.isDuplicate("Alaki (BLH Remix)", "Poori x Hiphopologist x Sha", 180_000))
    }

    @Test
    fun fileNameTitleWithDifferentDuration_isNotDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Alaki (BLH Remix)", "Poori", 180_000)

        assertFalse(matcher.isDuplicate("Alaki (BLH Remix).mp3", "BLH Remix", 240_000))
    }

    @Test
    fun artistNamedInTitle_isCompatible() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Alaki (BLH Remix)", "Poori", 180_000)

        assertTrue(matcher.isDuplicate("Alaki (BLH Remix)", "BLH Remix", 180_000))
    }

    @Test
    fun artistThatIsOnlyASubstringOfAnother_isNotDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("No One Knows", "Queens of the Stone Age", 255_000)

        assertFalse(matcher.isDuplicate("No One Knows", "Queen", 255_000))
    }

    @Test
    fun artistNameInsideTitleOutsideBrackets_isNotCompatible() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Love Me Do", "The Beatles", 140_000)

        assertFalse(matcher.isDuplicate("Love Me Do", "Do", 140_000))
    }

    @Test
    fun sharedCreditInFeaturingList_isDuplicate() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Track", "Main Artist feat. Guest", 200_000)

        assertTrue(matcher.isDuplicate("Track", "Main Artist", 200_000))
        assertTrue(matcher.isDuplicate("Track", "Guest & Someone Else", 200_000))
    }

    @Test
    fun separateCreditLists_withoutSharedCredit_areNotDuplicates() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("Track", "Alpha x Beta", 200_000)

        assertFalse(matcher.isDuplicate("Track", "Gamma, Delta", 200_000))
    }

    @Test
    fun blankTitle_neverMatches() {
        val matcher = DuplicateSongMatcher()
        matcher.remember("", "Artist", 100_000)

        assertFalse(matcher.isDuplicate("", "Artist", 100_000))
    }
}
