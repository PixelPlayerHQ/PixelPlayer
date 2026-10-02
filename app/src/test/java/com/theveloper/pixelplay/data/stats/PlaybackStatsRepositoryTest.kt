package com.theveloper.pixelplay.data.stats

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.ArtistRef
import com.theveloper.pixelplay.data.model.Song
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
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

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1")),
            allEvents = listOf(boundaryTouchingEvent),
            nowMillis = now
        )

        assertThat(summary.totalDurationMs).isEqualTo(0L)
        assertThat(summary.totalPlayCount).isEqualTo(0)
    }

    @Test
    fun `loadSummary preserves playback longer than track duration`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(10, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val listenedMs = TimeUnit.MINUTES.toMillis(15)
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + listenedMs,
            durationMs = listenedMs,
            startTimestamp = start,
            endTimestamp = start + listenedMs
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1", durationMs = TimeUnit.MINUTES.toMillis(3))),
            allEvents = listOf(event),
            nowMillis = start + listenedMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(listenedMs)
        assertThat(summary.totalPlayCount).isEqualTo(1)
        assertThat(summary.songs.single().totalDurationMs).isEqualTo(listenedMs)
    }

    @Test
    fun `loadSummary does not count short gaps between spans as listened time`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(12, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val firstDurationMs = 10_000L
        val secondDurationMs = 10_000L
        val gapMs = 1_000L
        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-1",
                timestamp = start + firstDurationMs,
                durationMs = firstDurationMs,
                startTimestamp = start,
                endTimestamp = start + firstDurationMs
            ),
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-2",
                timestamp = start + firstDurationMs + gapMs + secondDurationMs,
                durationMs = secondDurationMs,
                startTimestamp = start + firstDurationMs + gapMs,
                endTimestamp = start + firstDurationMs + gapMs + secondDurationMs
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1"), song("song-2")),
            allEvents = events,
            nowMillis = start + firstDurationMs + gapMs + secondDurationMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(firstDurationMs + secondDurationMs)
        assertThat(summary.totalPlayCount).isEqualTo(2)
    }

    @Test
    fun `buildSummaryFromEvents uses event spans without filesystem persistence`() = runTest {
        val repository = createRepository()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(9, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val durationMs = 30_000L
        val events = listOf(
            PlaybackStatsRepository.PlaybackEvent(
                songId = "song-1",
                timestamp = start + durationMs,
                durationMs = durationMs,
                startTimestamp = start,
                endTimestamp = start + durationMs
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(song("song-1")),
            allEvents = events,
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.totalDurationMs).isEqualTo(durationMs)
        assertThat(summary.uniqueSongs).isEqualTo(1)
    }

    @Test
    fun `loadSummary separates multi artist playback in top artists`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(14, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val durationMs = 60_000L
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + durationMs,
            durationMs = durationMs,
            startTimestamp = start,
            endTimestamp = start + durationMs
        )
        val collaboration = song(
            songId = "song-1",
            artist = "Artist A",
            artists = listOf(
                ArtistRef(id = 1L, name = "Artist A", isPrimary = true),
                ArtistRef(id = 2L, name = "Artist B", isPrimary = false)
            )
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(collaboration),
            allEvents = listOf(event),
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.topArtists.map { it.artist })
            .containsExactly("Artist A", "Artist B")
            .inOrder()
        summary.topArtists.forEach { artist ->
            assertThat(artist.totalDurationMs).isEqualTo(durationMs)
            assertThat(artist.playCount).isEqualTo(1)
            assertThat(artist.uniqueSongs).isEqualTo(1)
        }
    }

    @Test
    fun `loadSummary counts separated artists in genre uniqueness`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(15, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val durationMs = 30_000L
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "song-1",
            timestamp = start + durationMs,
            durationMs = durationMs,
            startTimestamp = start,
            endTimestamp = start + durationMs
        )
        val collaboration = song(
            songId = "song-1",
            artist = "Artist A",
            artists = listOf(
                ArtistRef(id = 1L, name = "Artist A", isPrimary = true),
                ArtistRef(id = 2L, name = "Artist B", isPrimary = false)
            ),
            genre = "Pop"
        )

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = listOf(collaboration),
            allEvents = listOf(event),
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.topGenres.single().uniqueArtists).isEqualTo(2)
    }

    @Test
    fun `streamed songs supply artist album and title when missing from the library`() = runTest {
        val repository = createRepository()
        val zoneId = ZoneId.systemDefault()
        val start = LocalDate.of(2026, 4, 10)
            .atTime(16, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
        val durationMs = 90_000L
        val event = PlaybackStatsRepository.PlaybackEvent(
            songId = "yt_super_shy",
            timestamp = start + durationMs,
            durationMs = durationMs,
            startTimestamp = start,
            endTimestamp = start + durationMs
        )
        val streamed = song(
            songId = "yt_super_shy",
            artist = "NewJeans",
            album = "NewJeans"
        ).copy(title = "Super Shy")

        val summary = repository.buildSummaryFromEvents(
            range = StatsTimeRange.DAY,
            songs = mergeStatsSongs(emptyList(), listOf(streamed)),
            allEvents = listOf(event),
            nowMillis = start + durationMs + 1_000L
        )

        assertThat(summary.topArtists.map { it.artist }).containsExactly("NewJeans")
        assertThat(summary.topAlbums.map { it.album }).containsExactly("NewJeans")
        assertThat(summary.songs.map { it.title }).containsExactly("Super Shy")
    }

    @Test
    fun `mergeStatsSongs keeps the library copy when ids collide`() {
        val library = song(songId = "yt_1", artist = "Library Artist")
        val streamed = song(songId = "yt_1", artist = "Stream Artist")
        val merged = mergeStatsSongs(listOf(library), listOf(streamed))
        assertThat(merged).containsExactly(library)
    }

    private fun createRepository(): PlaybackStatsRepository {
        val uniqueDir = createTempDirectory(
            "playback-stats-test-${Instant.now().toEpochMilli()}-"
        ).toFile()
        val testContext = mockk<android.content.Context>(relaxed = true)
        every { testContext.filesDir } returns uniqueDir
        return PlaybackStatsRepository(testContext)
    }

    private fun song(
        songId: String,
        durationMs: Long = 5 * 60 * 1000L,
        artist: String = "Artist",
        artists: List<ArtistRef> = emptyList(),
        genre: String? = null,
        album: String = "Album",
    ): Song = Song(
        id = songId,
        title = "Song $songId",
        artist = artist,
        artistId = 1L,
        artists = artists,
        album = album,
        albumId = 1L,
        path = "/music/$songId.mp3",
        contentUriString = "content://media/external/audio/media/$songId",
        albumArtUriString = null,
        duration = durationMs,
        genre = genre,
        mimeType = "audio/mpeg",
        bitrate = 320_000,
        sampleRate = 44_100
    )
}
