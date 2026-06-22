package com.theveloper.pixelplay.data.stream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CloudMusicUtilsTest {

    @Test
    fun `parseArtistNames preserves common punctuation in artist names by default`() {
        assertEquals(listOf("W&W"), CloudMusicUtils.parseArtistNames("W&W"))
        assertEquals(listOf("AC/DC"), CloudMusicUtils.parseArtistNames("AC/DC"))
        assertEquals(listOf("Lost & Found"), CloudMusicUtils.parseArtistNames("Lost & Found"))
        assertEquals(listOf("Black Country, New Road"), CloudMusicUtils.parseArtistNames("Black Country, New Road"))
    }

    @Test
    fun `parseArtistNames still splits explicit semicolon artists`() {
        assertEquals(
            listOf("Artist One", "Artist Two"),
            CloudMusicUtils.parseArtistNames("Artist One; Artist Two")
        )
    }
}
