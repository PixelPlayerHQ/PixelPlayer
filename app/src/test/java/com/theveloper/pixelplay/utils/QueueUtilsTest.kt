package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Song
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QueueUtilsTest {

    @Test
    fun buildAnchoredShuffleQueueSuspending_handles10kSongsWithoutLosingItems() = runBlocking {
        val songs = buildSongs(10_000)
        val anchorIndex = 7_654

        val shuffled = withTimeout(5_000L) {
            QueueUtils.buildAnchoredShuffleQueueSuspending(
                currentQueue = songs,
                anchorIndex = anchorIndex,
                random = Random(42)
            )
        }

        assertEquals("Queue size must stay the same", songs.size, shuffled.size)
        assertEquals(
            "Current song must stay anchored so playback is not redirected",
            songs[anchorIndex].id,
            shuffled[anchorIndex].id
        )

        val originalIds = songs.map { it.id }.toSet()
        val shuffledIds = shuffled.map { it.id }.toSet()
        assertEquals("Shuffled queue must contain the same songs", originalIds, shuffledIds)
    }

    @Test
    fun buildAnchoredShuffleQueueSuspending_yieldsForLargeQueues() = runBlocking {
        val songs = buildSongs(10_000)
        val started = CompletableDeferred<Unit>()
        var heartbeat = 0

        val heartbeatJob = launch {
            started.complete(Unit)
            while (true) {
                heartbeat++
                yield()
            }
        }

        started.await()
        yield() // Let heartbeat run at least once before measuring.
        val beforeShuffleHeartbeat = heartbeat

        withTimeout(5_000L) {
            QueueUtils.buildAnchoredShuffleQueueSuspending(
                currentQueue = songs,
                anchorIndex = 4_321,
                random = Random(7)
            )
        }

        val afterShuffleHeartbeat = heartbeat
        heartbeatJob.cancelAndJoin()

        assertTrue(
            "Shuffle should yield cooperatively so sibling coroutines can run",
            afterShuffleHeartbeat > beforeShuffleHeartbeat
        )
    }

    @Test
    fun buildAnchoredShuffleQueueSuspending_startAtZero_placesAnchorFirst() = runBlocking {
        val songs = buildSongs(32)
        val anchorIndex = 11

        val shuffled = QueueUtils.buildAnchoredShuffleQueueSuspending(
            currentQueue = songs,
            anchorIndex = anchorIndex,
            startAtZero = true,
            random = Random(99)
        )

        assertEquals("Anchor song must become the first item", songs[anchorIndex].id, shuffled.first().id)
        assertEquals("Queue size must stay the same", songs.size, shuffled.size)
        assertEquals(
            "Shuffled queue must contain the same songs",
            songs.map { it.id }.toSet(),
            shuffled.map { it.id }.toSet()
        )
    }

    private fun buildSongs(count: Int): List<Song> = List(count) { index ->
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = "Artist",
            artistId = 1L,
            album = "Album",
            albumId = 1L,
            path = "/tmp/song-$index.mp3",
            contentUriString = "content://pixelplay/song/$index",
            albumArtUriString = null,
            duration = 180_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )
    }
}
