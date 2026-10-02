package com.theveloper.pixelplay.data.preferences

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaylistSongIdReplacementTest {

    @Test
    fun replacesHiddenIdWithKeptId() {
        val result = PlaylistPreferencesRepository.replaceSongIds(
            songIds = listOf("local-1", "telegram-9", "local-2"),
            replacements = mapOf("telegram-9" to "local-1")
        )

        assertEquals(listOf("local-1", "local-2"), result)
    }

    @Test
    fun keepsOrderWhenReplacementIsNewToPlaylist() {
        val result = PlaylistPreferencesRepository.replaceSongIds(
            songIds = listOf("a", "hidden", "c"),
            replacements = mapOf("hidden" to "kept")
        )

        assertEquals(listOf("a", "kept", "c"), result)
    }

    @Test
    fun leavesUnrelatedIdsUnchanged() {
        val result = PlaylistPreferencesRepository.replaceSongIds(
            songIds = listOf("a", "b"),
            replacements = mapOf("x" to "y")
        )

        assertEquals(listOf("a", "b"), result)
    }
}
