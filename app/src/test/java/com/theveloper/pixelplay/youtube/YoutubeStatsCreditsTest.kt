package com.theveloper.pixelplay.youtube

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class YoutubeStatsCreditsTest {

    @Test
    fun parseStatsCredits_readsArtistAndAlbumAndDropsPlaceholders() {
        val raw = """
            Here you go:
            [
              {"id":"yt_1","artist":"NewJeans","album":"Get Up"},
              {"id":"yt_2","artist":"Unknown Artist","album":"YouTube Music"}
            ]
        """.trimIndent()

        val guesses = parseStatsCredits(raw)

        assertThat(guesses).containsExactly(
            StatsCreditGuess("yt_1", "NewJeans", "Get Up"),
            StatsCreditGuess("yt_2", "", ""),
        ).inOrder()
    }

    @Test
    fun statsCreditPrompt_includesTheTitleTheModelHasToIdentify() {
        val prompt = statsCreditPrompt(
            listOf(
                CatalogCreditGap(
                    songId = "yt_1",
                    title = "Super Shy",
                    artist = "",
                    album = "",
                    artworkUrl = null,
                    videoId = "abcdefghijk",
                ),
            ),
        )

        assertThat(prompt).contains("Super Shy")
        assertThat(prompt).contains("yt_1")
    }
}
