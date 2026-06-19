package com.theveloper.pixelplay.data.stream

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CloudMusicUtilsTest {

    @Test
    fun `jsonToMap parses valid JSON object`() {
        val result = CloudMusicUtils.jsonToMap("""{"key1":"val1","key2":"val2"}""")
        assertThat(result).containsExactly("key1", "val1", "key2", "val2")
    }

    @Test
    fun `jsonToMap handles empty object`() {
        val result = CloudMusicUtils.jsonToMap("{}")
        assertThat(result).isEmpty()
    }

    @Test
    fun `jsonToMap returns empty string for null values`() {
        val result = CloudMusicUtils.jsonToMap("""{"key":null}""")
        assertThat(result).containsEntry("key", "")
    }

    @Test
    fun `parseArtistNames splits on comma`() {
        val result = CloudMusicUtils.parseArtistNames("Artist A, Artist B")
        assertThat(result).containsExactly("Artist A", "Artist B")
    }

    @Test
    fun `parseArtistNames splits on ampersand`() {
        val result = CloudMusicUtils.parseArtistNames("A & B")
        assertThat(result).containsExactly("A", "B")
    }

    @Test
    fun `parseArtistNames splits on slash`() {
        val result = CloudMusicUtils.parseArtistNames("A/B/C")
        assertThat(result).containsExactly("A", "B", "C")
    }

    @Test
    fun `parseArtistNames splits on semicolon`() {
        val result = CloudMusicUtils.parseArtistNames("A;B")
        assertThat(result).containsExactly("A", "B")
    }

    @Test
    fun `parseArtistNames splits on CJK comma`() {
        val result = CloudMusicUtils.parseArtistNames("A、B")
        assertThat(result).containsExactly("A", "B")
    }

    @Test
    fun `parseArtistNames deduplicates names`() {
        val result = CloudMusicUtils.parseArtistNames("A, A, B")
        assertThat(result).containsExactly("A", "B")
    }

    @Test
    fun `parseArtistNames returns Unknown Artist for blank input`() {
        assertThat(CloudMusicUtils.parseArtistNames("")).containsExactly("Unknown Artist")
        assertThat(CloudMusicUtils.parseArtistNames("  ")).containsExactly("Unknown Artist")
    }

    @Test
    fun `parseArtistNames returns single artist for simple name`() {
        val result = CloudMusicUtils.parseArtistNames("Taylor Swift")
        assertThat(result).containsExactly("Taylor Swift")
    }

    @Test
    fun `parseArtistNames handles mixed delimiters`() {
        val result = CloudMusicUtils.parseArtistNames("A, B & C/D")
        assertThat(result).containsExactly("A", "B", "C", "D")
    }

    @Test
    fun `parseArtistNames trims whitespace around names`() {
        val result = CloudMusicUtils.parseArtistNames("  A ,  B  ")
        assertThat(result).containsExactly("A", "B")
    }
}
