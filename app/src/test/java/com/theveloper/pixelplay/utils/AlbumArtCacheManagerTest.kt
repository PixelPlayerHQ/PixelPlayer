package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File

class AlbumArtCacheManagerTest {

    @Test
    fun snapshotFilesForCleanup_usesStableLastModifiedSnapshots() {
        val oldest = FlakyLastModifiedFile(
            path = "/tmp/song_art_oldest.jpg",
            timestamps = longArrayOf(10L, 10_000L, 10_000L)
        )
        val middle = FlakyLastModifiedFile(
            path = "/tmp/song_art_middle.jpg",
            timestamps = longArrayOf(20L, 0L, 0L)
        )
        val newest = FlakyLastModifiedFile(
            path = "/tmp/song_art_newest.jpg",
            timestamps = longArrayOf(30L, -1L, -1L)
        )

        val sorted = AlbumArtCacheManager.snapshotFilesForCleanup(
            artFiles = listOf(newest, middle, oldest),
            cleanupPercentage = 1.0
        )

        assertThat(sorted.map(File::getAbsolutePath)).containsExactly(
            oldest.absolutePath,
            middle.absolutePath,
            newest.absolutePath
        ).inOrder()
    }

    @Test
    fun snapshotFilesForCleanup_breaksTimestampTiesByPath() {
        val zFile = FlakyLastModifiedFile(
            path = "/tmp/song_art_z.jpg",
            timestamps = longArrayOf(50L)
        )
        val aFile = FlakyLastModifiedFile(
            path = "/tmp/song_art_a.jpg",
            timestamps = longArrayOf(50L)
        )

        val sorted = AlbumArtCacheManager.snapshotFilesForCleanup(
            artFiles = listOf(zFile, aFile),
            cleanupPercentage = 1.0
        )

        assertThat(sorted.map(File::getAbsolutePath)).containsExactly(
            aFile.absolutePath,
            zFile.absolutePath
        ).inOrder()
    }

    private class FlakyLastModifiedFile(
        path: String,
        private val timestamps: LongArray
    ) : File(path) {
        private var index = 0

        override fun lastModified(): Long {
            val safeIndex = index.coerceAtMost(timestamps.lastIndex)
            index++
            return timestamps[safeIndex]
        }
    }
}
