package com.theveloper.pixelplay.data.s3

import android.util.Base64
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Minimal AWS Signature V4 signer for S3-compatible object storage
 * (AWS S3, Cloudflare R2, Backblaze B2, MinIO, Wasabi...).
 *
 * Supports the two request styles the app needs:
 *  - [signedHeaders]  : header-based auth for ListObjectsV2
 *  - [presignUrl]     : query-string presigned GET for streaming (no headers needed)
 */
object S3SigV4 {

    private const val ALGORITHM = "AWS4-HMAC-SHA256"
    private const val SERVICE = "s3"

    /** Amazon-style timestamp, e.g. 20130524T000000Z. */
    data class AmzDate(val amzDate: String, val dateStamp: String)

    fun amzDate(epochMillis: Long): AmzDate {
        val utc = java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(java.time.ZoneOffset.UTC)
        val dateStamp = String.format("%04d%02d%02d", utc.year, utc.monthValue, utc.dayOfMonth)
        val amzDate = dateStamp + String.format(
            "T%02d%02d%02dZ", utc.hour, utc.minute, utc.second
        )
        return AmzDate(amzDate, dateStamp)
    }

    /** Builds a full request URL with a presigned GET (default expiry: 1 hour). */
    suspend fun presignGetUrl(
        endpoint: String,
        bucket: String,
        objectKey: String,
        accessKey: String,
        secretKey: String,
        region: String,
        expiresInSeconds: Long = 3600,
        nowMillis: Long = System.currentTimeMillis()
    ): String = withContext(Dispatchers.Default) {
        val host = hostOf(endpoint)
        val date = amzDate(nowMillis)
        val scope = "${date.dateStamp}/$region/$SERVICE/aws4_request"

        val query = sortedMapOf(
            "X-Amz-Algorithm" to ALGORITHM,
            "X-Amz-Credential" to "$accessKey/$scope",
            "X-Amz-Date" to date.amzDate,
            "X-Amz-Expires" to expiresInSeconds.toString(),
            "X-Amz-SignedHeaders" to "host"
        )
        val canonicalQuery = canonicalQueryString(query)
        val canonicalUri = if (bucket.isBlank()) canonicalUri(objectKey)
        else "/$bucket" + canonicalUri(objectKey) // path-style: bucket is part of the signed path
        val canonicalRequest = listOf(
            "GET",
            canonicalUri,
            canonicalQuery,
            "host:$host\n",
            "host",
            "UNSIGNED-PAYLOAD"
        ).joinToString("\n")

        val signature = sign(secretKey, date.amzDate, date.dateStamp, region, canonicalRequest)

        // canonicalUri already includes "/$bucket" (path-style) — don't prepend it again.
        "$endpoint$canonicalUri?$canonicalQuery&X-Amz-Signature=$signature"
    }

    /** Adds header-based SigV4 auth (including the signed x-amz-* headers) to a builder.
     *  [query] entries are URI-encoded and sorted per the SigV4 canonical form.
     *  Request URL becomes "<endpoint>/<bucket>?<canonicalQuery>". */
    suspend fun signedHeaders(
        builder: okhttp3.Request.Builder,
        endpoint: String,
        bucket: String,
        query: Map<String, String>,
        accessKey: String,
        secretKey: String,
        region: String,
        nowMillis: Long = System.currentTimeMillis()
    ): okhttp3.Request.Builder = withContext(Dispatchers.Default) {
        val host = hostOf(endpoint)
        val canonicalUri = if (bucket.isBlank()) "/" else "/$bucket"
        val canonicalQuery = canonicalQueryString(query)
        val date = amzDate(nowMillis)
        val payloadHash = sha256Hex("")
        val amzHeaders = sortedMapOf(
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to date.amzDate
        )

        val canonicalRequest = listOf(
            "GET",
            canonicalUri,
            canonicalQuery,
            "host:$host\n" + amzHeaders.entries.joinToString("") { "${it.key}:${it.value}\n" },
            "host;" + amzHeaders.keys.joinToString(";"),
            payloadHash
        ).joinToString("\n")

        val signature = sign(secretKey, date.amzDate, date.dateStamp, region, canonicalRequest)
        val credential = "$accessKey/${date.dateStamp}/$region/$SERVICE/aws4_request"

        val url = buildString {
            append(endpoint).append(canonicalUri)
            if (canonicalQuery.isNotEmpty()) append('?').append(canonicalQuery)
        }
        builder.url(url)
        builder.header("Host", host)
        builder.header("x-amz-content-sha256", payloadHash)
        builder.header("x-amz-date", date.amzDate)
        builder.header(
            "Authorization",
            "$ALGORITHM Credential=$credential, SignedHeaders=host;${amzHeaders.keys.joinToString(";")}, Signature=$signature"
        )
        builder
    }

    private fun sign(
        secretKey: String,
        amzDate: String,
        dateStamp: String,
        region: String,
        canonicalRequest: String
    ): String {
        val kDate = hmac("AWS4$secretKey".toByteArray(), dateStamp)
        val kRegion = hmac(kDate, region)
        val kService = hmac(kRegion, SERVICE)
        val kSigning = hmac(kService, "aws4_request")
        val stringToSign = listOf(
            ALGORITHM,
            amzDate,
            "$dateStamp/$region/$SERVICE/aws4_request",
            sha256Hex(canonicalRequest)
        ).joinToString("\n")
        return hex(hmac(kSigning, stringToSign))
    }

    internal fun signForTest(
        secretKey: String,
        dateStamp: String,
        region: String,
        canonicalRequest: String,
        amzDate: String = "${dateStamp}T000000Z"
    ): String = sign(secretKey, amzDate, dateStamp, region, canonicalRequest)

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray())
    }

    fun sha256Hex(data: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(data.toByteArray())
        return hex(digest)
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    /** URI-encode each path segment, preserving '/'. */
    fun canonicalUri(objectKey: String): String {
        val path = objectKey.trim('/')
        if (path.isEmpty()) return "/"
        return "/" + path.split("/").joinToString("/") { uriEncode(it, encodeSlash = false) }
    }

    private fun canonicalQueryString(query: Map<String, String>): String =
        query.entries
            .sortedBy { uriEncode(it.key) }
            .joinToString("&") { (k, v) ->
                "${uriEncode(k)}=${uriEncode(v)}"
            }

    /**
     * Strict AWS URI-encoding: unreserved chars kept, everything else percent-encoded
     * (space becomes %20). Base64 flag NO_WRAP | NO_PADDING for hex-safe use.
     */
    fun uriEncode(value: String, encodeSlash: Boolean = true): String {
        val encoded = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt().toChar()
            when {
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' ||
                    c == '-' || c == '.' || c == '_' || c == '~' -> encoded.append(c)
                c == '/' && !encodeSlash -> encoded.append('/')
                else -> encoded.append('%').append("%02X".format(byte.toInt() and 0xFF))
            }
        }
        return encoded.toString()
    }

    // java.net.URI (bukan android.net.Uri) agar signer testable di plain JVM.
    private fun hostOf(endpoint: String): String = runCatching { URI(endpoint).host }.getOrNull() ?: ""
}
