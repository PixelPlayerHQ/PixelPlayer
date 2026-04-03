package com.theveloper.pixelplay.utils

import android.provider.MediaStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaStoreSelectionUtilsTest {

    @Test
    fun `buildLocalAudioSelection does not depend on is music flag`() {
        val (selection, selectionArgs) = buildLocalAudioSelection(10_000)

        assertFalse(selection.contains(MediaStore.Audio.Media.IS_MUSIC))
        assertTrue(selection.contains(MediaStore.Audio.Media.DURATION))
        assertTrue(selection.contains(MediaStore.Audio.Media.TITLE))
        assertArrayEquals(arrayOf("10000"), selectionArgs)
    }

    @Test
    fun `buildLocalAudioSelection clamps negative durations`() {
        val (_, selectionArgs) = buildLocalAudioSelection(-250)

        assertArrayEquals(arrayOf("0"), selectionArgs)
    }
}
