package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalArtworkUriTest {

    @Test
    fun resolveSongArtworkUri_convertsLegacyLocalCacheUriToStableUri() {
        val resolved = LocalArtworkUri.resolveSongArtworkUri(
            storedUri = "content://com.theveloper.pixelplay.provider/cache/song_art_42.jpg",
            songId = 42L,
            contentUriString = "content://media/external/audio/media/42"
        )

        assertThat(resolved).isEqualTo(LocalArtworkUri.buildSongUri(42L))
    }

    @Test
    fun resolveSongArtworkUri_convertsSharedArtworkUriToStableUri() {
        val resolved = LocalArtworkUri.resolveSongArtworkUri(
            storedUri = "content://com.theveloper.pixelplay.artwork/song/42?t=1234",
            songId = 42L,
            contentUriString = "content://media/external/audio/media/42"
        )

        assertThat(resolved).isEqualTo(LocalArtworkUri.buildSongUri(42L))
    }

    @Test
    fun resolveSongArtworkUri_keepsRemoteArtworkUriUntouched() {
        val resolved = LocalArtworkUri.resolveSongArtworkUri(
            storedUri = "https://example.com/cover.jpg",
            songId = 42L,
            contentUriString = "content://media/external/audio/media/42"
        )

        assertThat(resolved).isEqualTo("https://example.com/cover.jpg")
    }

    @Test
    fun resolveSongArtworkUri_keepsCloudSourceArtworkUntouched() {
        val resolved = LocalArtworkUri.resolveSongArtworkUri(
            storedUri = "telegram_art://123/456",
            songId = 42L,
            contentUriString = "telegram://123/456"
        )

        assertThat(resolved).isEqualTo("telegram_art://123/456")
    }

    @Test
    fun parseSongId_readsStableSongUri() {
        val songId = LocalArtworkUri.parseSongId(LocalArtworkUri.buildSongUri(99L))

        assertThat(songId).isEqualTo(99L)
    }

    @Test
    fun parseSongIdFromVolatileArtworkUri_readsLegacyCacheFileName() {
        val songId = LocalArtworkUri.parseSongIdFromVolatileArtworkUri(
            "content://com.theveloper.pixelplay.provider/cache/song_art_77_v2.jpg"
        )

        assertThat(songId).isEqualTo(77L)
    }

    @Test
    fun extractCacheBustToken_readsTimestampQuery() {
        val cacheBustToken = LocalArtworkUri.extractCacheBustToken(
            "pixelplay_local_art://song/99?t=456"
        )

        assertThat(cacheBustToken).isEqualTo("456")
    }
}
