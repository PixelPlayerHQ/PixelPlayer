package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import kotlin.io.path.createTempDirectory
import org.junit.Test

class AlbumArtUtilsTest {

    @Test
    fun findExternalAlbumArtFile_returnsExplicitCoverFromDedicatedAlbumFolder() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Calvin Harris - Funk Wav Bounces").apply { mkdirs() }
        val songFile = albumDir.resolve("Feels.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val coverFile = albumDir.resolve("cover.jpg").apply { writeBytes(ByteArray(2048) { 7 }) }

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isEqualTo(coverFile)
        root.deleteRecursively()
    }

    @Test
    fun findExternalAlbumArtFile_ignoresLooseArtworkNames() {
        val root = createTempDirectory("album-art-test").toFile()
        val albumDir = root.resolve("Singles").apply { mkdirs() }
        val songFile = albumDir.resolve("Random Song.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        albumDir.resolve("linkin_park_artwork_random.jpg").writeBytes(ByteArray(2048) { 9 })

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }

    @Test
    fun findExternalAlbumArtFile_ignoresGenericDownloadsFolder() {
        val root = createTempDirectory("album-art-test").toFile()
        val downloadsDir = root.resolve("Downloads").apply { mkdirs() }
        val songFile = downloadsDir.resolve("Fresh Track.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        downloadsDir.resolve("cover.jpg").writeBytes(ByteArray(2048) { 5 })

        val resolved = AlbumArtUtils.findExternalAlbumArtFile(songFile.absolutePath)

        assertThat(resolved).isNull()
        root.deleteRecursively()
    }
}
