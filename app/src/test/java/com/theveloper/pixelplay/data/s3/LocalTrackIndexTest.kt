package com.theveloper.pixelplay.data.s3

import com.theveloper.pixelplay.data.database.SongMatchKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalTrackIndexTest {

    private val index = LocalTrackIndex(
        listOf(
            SongMatchKey(title = "Gymnopédie No. 1", artistName = "Kevin MacLeod", duration = 182_000),
            SongMatchKey(title = "Local Forecast", artistName = "Kevin MacLeod, Someone", duration = 0),
            SongMatchKey(
                title = "Sneaky Snitch", artistName = "<unknown>", duration = 136_000,
                filePath = "/storage/emulated/0/Music/Kevin MacLeod - Sneaky Snitch.mp3",
            ),
        )
    )

    @Test
    fun `same title and artist within tolerance is a local copy`() {
        assertTrue(index.hasLocalCopy("Gymnopédie No. 1", "Kevin MacLeod", 183_500))
    }

    @Test
    fun `case, punctuation and bracketed suffixes are ignored`() {
        assertTrue(index.hasLocalCopy("GYMNOPÉDIE NO. 1 (Remastered)", "kevin macleod", 182_000))
        assertTrue(index.hasLocalCopy("GYMNOPÉDIE NO. 1 [Live]", "kevin macleod", 182_000))
    }

    @Test
    fun `duration far apart is a different recording`() {
        assertFalse(index.hasLocalCopy("Gymnopédie No. 1", "Kevin MacLeod", 240_000))
    }

    @Test
    fun `unknown durations match on title and primary artist`() {
        assertTrue(index.hasLocalCopy("Gymnopédie No. 1", "Kevin MacLeod", 0))
        assertTrue(index.hasLocalCopy("Local Forecast", "Kevin MacLeod", 150_000))
    }

    @Test
    fun `different artist or title is not a copy`() {
        assertFalse(index.hasLocalCopy("Gymnopédie No. 1", "Erik Satie", 240_000))
        assertFalse(index.hasLocalCopy("Gymnopédie No. 2", "Kevin MacLeod", 182_000))
        assertFalse(index.hasLocalCopy("", "Kevin MacLeod", 182_000))
    }

    @Test
    fun `same title and length match even when the artist differs`() {
        assertTrue(index.hasLocalCopy("Sneaky Snitch", "Kevin MacLeod", 137_000))
    }

    @Test
    fun `same file name is a local copy`() {
        assertTrue(index.hasLocalCopy("01 sneaky", "Unknown Artist", 0, "music/Kevin MacLeod - Sneaky Snitch.flac"))
        assertFalse(index.hasLocalCopy("Other", "Unknown Artist", 0, "music/Other.flac"))
    }
}
