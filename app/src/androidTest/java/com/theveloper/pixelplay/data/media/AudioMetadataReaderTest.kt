package com.theveloper.pixelplay.data.media

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * `P.6`: `AudioMetadataReader.read(ParcelFileDescriptor)`. Needs a real TagLib call
 * against a real (tiny) tagged audio file, so this runs on-device (D-22: no Robolectric,
 * no TagLib in JVM unit tests — verified nothing else in this repo exercises TagLib there).
 *
 * The asset (`tagged_with_art.mp3`, ~5 KB) is a 1-second silent MP3 with ID3 title/artist/
 * album tags and an embedded cover, generated with ffmpeg for this test.
 */
@RunWith(AndroidJUnit4::class)
class AudioMetadataReaderTest {

    private fun copyAssetToTempFile(): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val tempFile = File.createTempFile("audio_metadata_reader_test", ".mp3", instrumentation.targetContext.cacheDir)
        instrumentation.context.assets.open("tagged_with_art.mp3").use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    @Test
    fun read_pfd_returns_the_same_metadata_as_read_file() {
        val file = copyAssetToTempFile()
        try {
            val fromFile = AudioMetadataReader.read(file)
            val fromPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                AudioMetadataReader.read(pfd)
            }

            assertNotNull(fromFile)
            assertNotNull(fromPfd)
            assertEquals(fromFile!!.title, fromPfd!!.title)
            assertEquals(fromFile.artist, fromPfd.artist)
            assertEquals(fromFile.album, fromPfd.album)
            assertEquals("Test Title", fromPfd.title)
            assertEquals("Test Artist", fromPfd.artist)
            assertEquals("Test Album", fromPfd.album)
            assertNotNull(fromFile.artwork)
            assertNotNull(fromPfd.artwork)
            assertArrayEquals(fromFile.artwork!!.bytes, fromPfd.artwork!!.bytes)
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_pfd_never_consumes_the_callers_descriptor() {
        val file = copyAssetToTempFile()
        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                AudioMetadataReader.read(pfd)

                // NOT a check that the shared read offset is untouched — every TagLib call
                // dup()s pfd, and dup'd descriptors share the same underlying file position
                // (POSIX dup() semantics), so the offset has legitimately moved. What P.6
                // actually guards against is detachFd() called on pfd itself, which would
                // mark its native descriptor invalid (-1) right here.
                assertTrue(
                    "expected the caller's descriptor to still be valid",
                    pfd.fileDescriptor.valid()
                )
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun read_pfd_with_readArtwork_false_skips_the_embedded_cover() {
        val file = copyAssetToTempFile()
        try {
            val result = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                AudioMetadataReader.read(pfd, readArtwork = false)
            }

            assertNotNull(result)
            assertEquals("Test Title", result!!.title)
            assertNull(result.artwork)
        } finally {
            file.delete()
        }
    }
}
