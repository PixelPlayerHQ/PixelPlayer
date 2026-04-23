package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.database.LyricsEntity
import com.theveloper.pixelplay.data.database.LyricsDao
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.lyrics.LrcLibApiService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test

class LyricsRepositoryImplTest {

    @Test
    fun getLyrics_returnsSongLyricsBeforeNeedingStorageRead() = runTest {
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = mockk<LrcLibApiService>(relaxed = true),
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "12",
            title = "Track",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = "[00:01.00]Hello again",
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.EMBEDDED_FIRST)

        assertThat(lyrics).isNotNull()
        assertThat(lyrics!!.areFromRemote).isFalse()
        assertThat(lyrics.synced).isNotEmpty()
        assertThat(lyrics.synced!!.first().line).isEqualTo("Hello again")
    }

    @Test
    fun getLyrics_apiFirst_usesStoredLyricsBeforeCallingLrcLib() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = mockk<LyricsDao>(relaxed = true),
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "45",
            title = "Already Here",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = "These lyrics are already saved",
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val lyrics = repository.getLyrics(song, LyricsSourcePreference.API_FIRST)

        assertThat(lyrics).isNotNull()
        assertThat(lyrics!!.plain).containsExactly("These lyrics are already saved")
        assertThat(lyrics.areFromRemote).isFalse()
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getLyrics(any(), any(), any(), any()) }
    }

    @Test
    fun fetchFromRemote_returnsStoredLyricsWithoutCallingApi() = runTest {
        val apiService = mockk<LrcLibApiService>(relaxed = true)
        val lyricsDao = mockk<LyricsDao>(relaxed = true)
        coEvery { lyricsDao.getLyrics(77L) } returns LyricsEntity(
            songId = 77L,
            content = "[00:01.00]Stored line",
            isSynced = true,
            source = "manual"
        )
        val repository = LyricsRepositoryImpl(
            context = mockk<Context>(relaxed = true),
            lrcLibApiService = apiService,
            lyricsDao = lyricsDao,
            okHttpClient = mockk<OkHttpClient>(relaxed = true)
        )
        val song = Song(
            id = "77",
            title = "Stored Track",
            artist = "Artist",
            artistId = 5L,
            album = "Album",
            albumId = 8L,
            path = "",
            contentUriString = "",
            albumArtUriString = null,
            duration = 180_000L,
            lyrics = null,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100
        )

        val result = repository.fetchFromRemote(song)

        assertThat(result.isSuccess).isTrue()
        val (lyrics, rawLyrics) = result.getOrThrow()
        assertThat(rawLyrics).isEqualTo("[00:01.00]Stored line")
        assertThat(lyrics.synced).isNotEmpty()
        assertThat(lyrics.areFromRemote).isFalse()
        coVerify(exactly = 0) { apiService.searchLyrics(any(), any(), any(), any()) }
        coVerify(exactly = 0) { apiService.getLyrics(any(), any(), any(), any()) }
    }
}
