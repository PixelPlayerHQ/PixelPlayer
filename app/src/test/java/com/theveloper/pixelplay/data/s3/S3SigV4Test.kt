package com.theveloper.pixelplay.data.s3

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/**
 * Verifies the SigV4 signer against the official AWS "Authenticating Requests
 * (AWS Signature Version 4)" documentation example (examplebucket / test.txt).
 */
class S3SigV4Test {

    // Values from the AWS docs example.
    private val accessKey = "AKIAIOSFODNN7EXAMPLE"
    private val secretKey = "wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"
    private val region = "us-east-1"
    private val dateStamp = "20130524"

    @Test
    fun `signature matches AWS documentation example`() {
        // Canonical request exactly as documented for the GET Object header example.
        val canonicalRequest = listOf(
            "GET",
            "/test.txt",
            "",
            "host:examplebucket.s3.amazonaws.com\n" +
                "range:bytes=0-9\n" +
                "x-amz-content-sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855\n" +
                "x-amz-date:20130524T000000Z\n",
            "host;range;x-amz-content-sha256;x-amz-date",
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        ).joinToString("\n")

        val signature = S3SigV4.signForTest(secretKey, dateStamp, region, canonicalRequest)

        assertThat(signature)
            .isEqualTo("f0e8bdb87c964420e857bd35b5d6ed310bd44f0170aba48dd91039c6036bdb41")
    }

    @Test
    fun `presigned URL signature matches AWS query-string example`() {
        // Same canonical request shape as the "Using Query Parameters" docs example.
        val canonicalRequest = listOf(
            "GET",
            "/test.txt",
            "X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIOSFODNN7EXAMPLE%2F20130524%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20130524T000000Z" +
                "&X-Amz-Expires=86400" +
                "&X-Amz-SignedHeaders=host",
            "host:examplebucket.s3.amazonaws.com\n",
            "host",
            "UNSIGNED-PAYLOAD"
        ).joinToString("\n")

        val signature = S3SigV4.signForTest(secretKey, dateStamp, region, canonicalRequest)

        assertThat(signature)
            .isEqualTo("aeeed9bbccd4d02ee5c0109b86d86835f995330da4c265957d157751f604d404")
    }

    @Test
    fun `uriEncode follows strict AWS rules`() {
        assertThat(S3SigV4.uriEncode("music/my song+1.mp3")).isEqualTo("music%2Fmy%20song%2B1.mp3")
        assertThat(S3SigV4.uriEncode("music/my song+1.mp3", encodeSlash = false))
            .isEqualTo("music/my%20song%2B1.mp3")
        assertThat(S3SigV4.uriEncode("a~b-c_d.e")).isEqualTo("a~b-c_d.e")
        assertThat(S3SigV4.uriEncode("café")).isEqualTo("caf%C3%A9")
    }

    @Test
    fun `canonicalUri preserves slashes and trims edges`() {
        assertThat(S3SigV4.canonicalUri("/Music/01 song.mp3")).isEqualTo("/Music/01%20song.mp3")
        assertThat(S3SigV4.canonicalUri("")).isEqualTo("/")
        // Empty path segments are legal object keys and must be preserved verbatim.
        assertThat(S3SigV4.canonicalUri("a//b")).isEqualTo("/a//b")
    }

    @Test
    fun `amzDate formats correctly`() {
        val date = S3SigV4.amzDate(1_369_975_200_000L) // 2013-05-31T04:40:00Z
        assertThat(date.dateStamp).isEqualTo("20130531")
        assertThat(date.amzDate).isEqualTo("20130531T044000Z")
    }

    @Test
    fun `presigned URL is path-style with exactly one bucket segment`() = runBlocking {
        val url = S3SigV4.presignGetUrl(
            endpoint = "https://s3.example.com",
            bucket = "mybucket",
            objectKey = "u1/33x - Perunggu.flac",
            accessKey = accessKey,
            secretKey = secretKey,
            region = region,
            expiresInSeconds = 3600,
            nowMillis = 0L
        )
        assertThat(url).startsWith("https://s3.example.com/mybucket/u1/33x%20-%20Perunggu.flac?")
        assertThat(url).doesNotContain("mybucket/mybucket")
    }
}
