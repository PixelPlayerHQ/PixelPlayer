package com.theveloper.pixelplay.youtube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YoutubeStreamPickerTest {

    @Test
    fun pickPlayableAudio_prefersDirectAudioOverHlsAndVideo() {
        val picked = pickPlayableAudio(
            listOf(
                candidate("https://cdn.example/playlist.m3u8", "application/vnd.apple.mpegurl", 1_000_000),
                candidate("https://cdn.example/video.mp4", "video/mp4; codecs=\"avc1, mp4a.40.2\"", 800_000),
                candidate("https://cdn.example/audio.webm", "audio/webm; codecs=\"opus\"", 160_000),
                candidate("https://cdn.example/audio.m4a", "audio/mp4; codecs=\"mp4a.40.2\"", 128_000),
            ),
        )

        assertThat(picked?.url).isEqualTo("https://cdn.example/audio.m4a")
    }

    @Test
    fun pickPlayableAudio_skipsSabreAndTheUrlThatJustFailed() {
        val picked = pickPlayableAudio(
            candidates = listOf(
                candidate("https://cdn.example/sabr/audio", "audio/mp4", 200_000),
                candidate("https://cdn.example/failed.m4a", "audio/mp4", 160_000),
                candidate("https://cdn.example/backup.m4a", "audio/webm", 96_000),
            ),
            avoidUrl = "https://cdn.example/failed.m4a",
        )

        assertThat(picked?.url).isEqualTo("https://cdn.example/backup.m4a")
    }

    @Test
    fun pickPlayableAudio_returnsNullWhenEveryUrlAlreadyFailed() {
        val picked = pickPlayableAudio(
            candidates = listOf(candidate("https://cdn.example/failed.m4a", "audio/mp4", 128_000)),
            avoidUrl = "https://cdn.example/failed.m4a",
        )

        assertThat(picked).isNull()
    }

    private fun candidate(url: String, mimeType: String, bitrate: Int) = YoutubeAudioCandidate(
        url = url,
        mimeType = mimeType,
        bitrate = bitrate,
    )
}
