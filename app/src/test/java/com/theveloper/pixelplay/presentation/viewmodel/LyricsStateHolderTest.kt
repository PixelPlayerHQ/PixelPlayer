package com.theveloper.pixelplay.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.media.SongMetadataEditor
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.Test

class LyricsStateHolderTest {

    @Test
    fun withPersistedLyrics_replacesAlbumArtUriWhenMetadataWriteRefreshesArtworkPath() {
        val originalSong = testSong(albumArtUriString = "file:///cache/song_art_1_old.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "New lyrics",
            refreshedAlbumArtUri = "file:///cache/song_art_1_new.jpg"
        )

        assertThat(updatedSong.lyrics).isEqualTo("New lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("file:///cache/song_art_1_new.jpg")
    }

    @Test
    fun withPersistedLyrics_keepsExistingAlbumArtUriWhenMetadataWriteDoesNotReturnOne() {
        val originalSong = testSong(albumArtUriString = "content://art/song_art_1.jpg")

        val updatedSong = originalSong.withPersistedLyrics(
            rawLyrics = "Imported lyrics",
            refreshedAlbumArtUri = null
        )

        assertThat(updatedSong.lyrics).isEqualTo("Imported lyrics")
        assertThat(updatedSong.albumArtUriString).isEqualTo("content://art/song_art_1.jpg")
    }

    @Test
    fun fetchLyricsForSong_usesStoredLyricsWithoutRemoteFetch() {
        val musicRepository = mockk<MusicRepository>(relaxed = true)
        val userPreferencesRepository = mockk<UserPreferencesRepository>(relaxed = true)
        val songMetadataEditor = mockk<SongMetadataEditor>(relaxed = true)
        val holder = LyricsStateHolder(
            musicRepository = musicRepository,
            userPreferencesRepository = userPreferencesRepository,
            songMetadataEditor = songMetadataEditor
        )
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Unconfined)
        val callback = RecordingLyricsLoadCallback()
        val state = MutableStateFlow(StablePlayerState())
        val song = testSong(albumArtUriString = "content://art/song_art_1.jpg").copy(
            lyrics = "Stored lyrics"
        )
        val storedLyrics = Lyrics(plain = listOf("Stored lyrics"), areFromRemote = false)

        try {
            coEvery { userPreferencesRepository.getLyricsSyncOffset(any()) } returns 0
            coEvery { musicRepository.getStoredLyrics(song) } returns (storedLyrics to "Stored lyrics")
            holder.initialize(scope, callback, state)

            holder.fetchLyricsForSong(
                song = song,
                forcePickResults = false,
                sourcePreference = com.theveloper.pixelplay.data.model.LyricsSourcePreference.API_FIRST
            ) { "Lyrics already available" }
            runBlocking {
                withTimeout(5_000) {
                    while (holder.searchUiState.value !is LyricsSearchUiState.Success) {
                        delay(10)
                    }
                }
            }

            assertThat(holder.searchUiState.value).isEqualTo(LyricsSearchUiState.Success(storedLyrics))
            coVerify(exactly = 1) { musicRepository.getStoredLyrics(song) }
            coVerify(exactly = 0) { musicRepository.getLyricsFromRemote(any()) }
            coVerify(exactly = 0) { musicRepository.searchRemoteLyrics(any()) }
        } finally {
            job.cancel()
        }
    }

    private fun testSong(albumArtUriString: String?): Song {
        return Song(
            id = "1",
            title = "Indian Summer",
            artist = "Blood Cultures",
            album = "Happy Birthday",
            path = "/music/indian-summer.mp3",
            contentUriString = "content://media/external/audio/media/1",
            albumArtUriString = albumArtUriString,
            duration = 295_000L,
            mimeType = "audio/mpeg",
            bitrate = 320_000,
            sampleRate = 44_100,
            artistId = 1L,
            albumId = 1L
        )
    }

    private class RecordingLyricsLoadCallback : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) = Unit

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) = Unit
    }
}
