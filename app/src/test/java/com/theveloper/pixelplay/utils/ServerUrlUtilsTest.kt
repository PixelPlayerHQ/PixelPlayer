package com.theveloper.pixelplay.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ServerUrlUtilsTest {

    @Test
    fun `normalizeHttpUrl adds https scheme when absent`() {
        val result = ServerUrlUtils.normalizeHttpUrl("music.example.com")
        assertThat(result).isNotNull()
        assertThat(result!!.scheme).isEqualTo("https")
        assertThat(result.host).isEqualTo("music.example.com")
    }

    @Test
    fun `normalizeHttpUrl preserves explicit http scheme`() {
        val result = ServerUrlUtils.normalizeHttpUrl("http://192.168.1.100:8096")
        assertThat(result).isNotNull()
        assertThat(result!!.scheme).isEqualTo("http")
        assertThat(result.host).isEqualTo("192.168.1.100")
        assertThat(result.port).isEqualTo(8096)
    }

    @Test
    fun `normalizeHttpUrl preserves https scheme`() {
        val result = ServerUrlUtils.normalizeHttpUrl("https://music.example.com")
        assertThat(result).isNotNull()
        assertThat(result!!.scheme).isEqualTo("https")
    }

    @Test
    fun `normalizeHttpUrl trims whitespace and trailing slashes`() {
        val result = ServerUrlUtils.normalizeHttpUrl("  https://music.example.com/  ")
        assertThat(result).isNotNull()
        assertThat(result!!.host).isEqualTo("music.example.com")
    }

    @Test
    fun `normalizeHttpUrl returns null for empty string`() {
        assertThat(ServerUrlUtils.normalizeHttpUrl("")).isNull()
    }

    @Test
    fun `normalizeServerUrl returns clean URL without trailing slash`() {
        val result = ServerUrlUtils.normalizeServerUrl("https://music.example.com/")
        assertThat(result).doesNotEndWith("/")
        assertThat(result).startsWith("https://")
    }

    @Test
    fun `normalizeServerUrl falls back to trimmed input for invalid URLs`() {
        val result = ServerUrlUtils.normalizeServerUrl("")
        assertThat(result).isEmpty()
    }

    @Test
    fun `connectionValidationError returns null for valid https URL`() {
        val error = ServerUrlUtils.connectionValidationError("https://music.example.com")
        assertThat(error).isNull()
    }

    @Test
    fun `connectionValidationError rejects embedded credentials`() {
        val error = ServerUrlUtils.connectionValidationError("https://user:pass@music.example.com")
        assertThat(error).contains("credentials")
    }

    @Test
    fun `connectionValidationError rejects http on public hosts`() {
        val error = ServerUrlUtils.connectionValidationError("http://music.example.com")
        assertThat(error).contains("https")
    }

    @Test
    fun `connectionValidationError allows http on local network`() {
        val error = ServerUrlUtils.connectionValidationError("http://192.168.1.100:8096")
        assertThat(error).isNull()
    }

    @Test
    fun `connectionValidationError allows http on localhost`() {
        val error = ServerUrlUtils.connectionValidationError("http://localhost:8096")
        assertThat(error).isNull()
    }

    @Test
    fun `connectionValidationError allows http on 127-0-0-1`() {
        val error = ServerUrlUtils.connectionValidationError("http://127.0.0.1:4533")
        assertThat(error).isNull()
    }

    @Test
    fun `connectionValidationError uses serverLabel in message`() {
        val error = ServerUrlUtils.connectionValidationError("http://public.example.com", "Jellyfin")
        assertThat(error).contains("Jellyfin")
    }

    @Test
    fun `connectionValidationError rejects invalid URL`() {
        val error = ServerUrlUtils.connectionValidationError("not a url at all ://")
        assertThat(error).contains("Invalid")
    }
}
