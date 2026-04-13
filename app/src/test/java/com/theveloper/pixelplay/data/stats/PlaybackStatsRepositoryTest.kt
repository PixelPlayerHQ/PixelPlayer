package com.theveloper.pixelplay.data.stats

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import io.mockk.every
import io.mockk.mockk
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class PlaybackStatsRepositoryTest {

    @Test
    fun `loadSummary excludes event that only touches the start boundary`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val now = LocalDate.of(2026, 4, 10)
            .atStartOfDay(zoneId)
            .toInstant()
            .toEpochMilli()

        val boundaryTouchingEvent = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = now,
            durationMs = 10_000L,
            startTimestamp = now - 10_000L,
            endTimestamp = now
        )

        repository.importEventsFromBackup(listOf(boundaryTouchingEvent))

        val summary = repository.loadSummary(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1")),
            nowMillis = now
        )

        assertThat(summary.totalDurationMs).isEqualTo(0L)
        assertThat(summary.totalPlayCount).isEqualTo(0)
    }

    private fun createRepository(): PlaybackStatsRepository {
        val uniqueDir = createTempDirectory(
            "playback-stats-test-${Instant.now().toEpochMilli()}-"
        ).toFile()
        val testContext = mockk<android.content.Context>(relaxed = true)
        every { testContext.filesDir } returns uniqueDir
        return PlaybackStatsRepository(testContext)
    }

    private fun song(songId: String): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = "Artist",
        artistId = 1L,
        album = "Album",
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = 5 * 60 * 1000L,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
