package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaItemBuilderTest {

    @Test
    fun shouldPreferDirectLocalFileUri_prefersDirectFileUriForLocalM4aMediaStoreItems() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "content://media/external/audio/media/42",
            filePath = "/storage/emulated/0/Music/test-track.m4a",
            mimeType = "audio/mp4"
        )

        assertThat(shouldPreferFile).isTrue()
    }

    @Test
    fun shouldPreferDirectLocalFileUri_keepsContentUriForFormatsThatAlreadySeekCorrectly() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "content://media/external/audio/media/24",
            filePath = "/storage/emulated/0/Music/test-track.flac",
            mimeType = "audio/flac"
        )

        assertThat(shouldPreferFile).isFalse()
    }

    @Test
    fun shouldPreferDirectLocalFileUri_keepsCloudUrisUntouched() {
        val shouldPreferFile = MediaItemBuilder.shouldPreferDirectLocalFileUri(
            contentUriString = "telegram://123/456",
            filePath = "/storage/emulated/0/Download/cached-track.m4a",
            mimeType = "audio/mp4"
        )

        assertThat(shouldPreferFile).isFalse()
    }
}
