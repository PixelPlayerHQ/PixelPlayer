package com.theveloper.pixelplay.data.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository

class ArtistParsingUtilsTest {

    @Test
    fun `default delimiters preserve ampersand slash comma and plus inside artist names`() {
        assertEquals(
            listOf("W&W"),
            collectArtistNames(
                rawArtistName = "W&W",
                title = "Rave Culture",
                artistDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
                wordDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS
            )
        )
        assertEquals(
            listOf("AC/DC"),
            collectArtistNames(
                rawArtistName = "AC/DC",
                title = "Back In Black",
                artistDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
                wordDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS
            )
        )
        assertEquals(
            listOf("Lost & Found"),
            collectArtistNames(
                rawArtistName = "Lost & Found",
                title = "Found",
                artistDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
                wordDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS
            )
        )
        assertEquals(
            listOf("Black Country, New Road"),
            collectArtistNames(
                rawArtistName = "Black Country, New Road",
                title = "Track X",
                artistDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_DELIMITERS,
                wordDelimiters = UserPreferencesRepository.DEFAULT_ARTIST_WORD_DELIMITERS
            )
        )
    }

    @Test
    fun `choosePreferredArtistName prefers media store when it contains more artists`() {
        val result =
            choosePreferredArtistName(
                localArtistName = "Calvin Harris",
                mediaStoreArtistName = "Calvin Harris, Pharrell Williams, Katy Perry, Big Sean, Funk Wav",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = emptyList()
            )

        assertEquals(
            "Calvin Harris, Pharrell Williams, Katy Perry, Big Sean, Funk Wav",
            result
        )
    }

    @Test
    fun `choosePreferredArtistName preserves richer local metadata when media store is reduced to primary`() {
        val result =
            choosePreferredArtistName(
                localArtistName = "Calvin Harris, Pharrell Williams, Katy Perry",
                mediaStoreArtistName = "Calvin Harris",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = emptyList()
            )

        assertEquals("Calvin Harris, Pharrell Williams, Katy Perry", result)
    }

    @Test
    fun `collectArtistNames merges title features without duplicating existing artists`() {
        val result =
            collectArtistNames(
                rawArtistName = "Calvin Harris, Pharrell Williams",
                title = "Feels (feat. Katy Perry & Big Sean)",
                artistDelimiters = listOf(",", "&"),
                wordDelimiters = listOf("feat."),
                extractFromTitle = true
            )

        assertEquals(
            listOf("Calvin Harris", "Pharrell Williams", "Katy Perry", "Big Sean"),
            result
        )
    }
}
