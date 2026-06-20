package com.theveloper.pixelplay.data.stream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CloudMusicUtilsTest {

    @Test
    fun `jsonToMap parses valid JSON object`() {
        val result = CloudMusicUtils.jsonToMap("""{"key1":"val1","key2":"val2"}""")
        assertEquals(mapOf("key1" to "val1", "key2" to "val2"), result)
    }

    @Test
    fun `jsonToMap handles empty object`() {
        val result = CloudMusicUtils.jsonToMap("{}")
        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `jsonToMap returns empty string for null values`() {
        val result = CloudMusicUtils.jsonToMap("""{"key":null}""")
        assertEquals(mapOf("key" to ""), result)
    }

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

    @Test
    fun `parseArtistNames returns Unknown Artist for blank input`() {
        assertEquals(listOf("Unknown Artist"), CloudMusicUtils.parseArtistNames(""))
        assertEquals(listOf("Unknown Artist"), CloudMusicUtils.parseArtistNames("  "))
    }

    @Test
    fun `parseArtistNames returns single artist for simple name`() {
        val result = CloudMusicUtils.parseArtistNames("Taylor Swift")
        assertEquals(listOf("Taylor Swift"), result)
    }

    @Test
    fun `parseArtistNames trims whitespace around names`() {
        val result = CloudMusicUtils.parseArtistNames("  Artist A ;  Artist B  ")
        assertEquals(listOf("Artist A", "Artist B"), result)
    }
}
