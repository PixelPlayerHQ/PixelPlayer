package com.theveloper.pixelplay.presentation.viewmodel

import com.google.common.truth.Truth.assertThat
import com.theveloper.pixelplay.data.model.Song
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
}
