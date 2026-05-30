package com.theveloper.pixelplay.presentation.viewmodel

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

class FileExplorerDirectoryMergeTest {

    @Test
    fun `merge preserves filesystem directories missing from MediaStore index`() {
        val unindexedAlbum = RawDirectoryEntry(
            file = File("/storage/emulated/0/Music/New Album"),
            directAudioCount = -1,
            totalAudioCount = -1,
            canonicalPath = "/storage/emulated/0/Music/New Album"
        )
        val indexedAlbum = RawDirectoryEntry(
            file = File("/storage/emulated/0/Music/Indexed Album"),
            directAudioCount = -1,
            totalAudioCount = -1,
            canonicalPath = "/storage/emulated/0/Music/Indexed Album"
        )
        val indexedAlbumCounts = indexedAlbum.copy(
            directAudioCount = 3,
            totalAudioCount = 3
        )

        val merged = mergeDirectoryEntryLists(
            filesystemEntries = listOf(unindexedAlbum, indexedAlbum),
            mediaStoreEntries = listOf(indexedAlbumCounts)
        )

        assertEquals(
            listOf(
                "/storage/emulated/0/Music/Indexed Album",
                "/storage/emulated/0/Music/New Album"
            ),
            merged.map { it.canonicalPath }
        )
        assertEquals(3, merged.first { it.canonicalPath.endsWith("Indexed Album") }.totalAudioCount)
        assertEquals(-1, merged.first { it.canonicalPath.endsWith("New Album") }.totalAudioCount)
    }
}
