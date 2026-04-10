package com.theveloper.pixelplay.data.stream

import io.ktor.http.HttpStatusCode
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Lightweight validation utilities shared by cloud streaming proxies.
 *
 * The checks are intentionally cheap (string/number validation only) so they
 * can run on every request without adding measurable overhead.
 */
object CloudStreamSecurity {
    const val MAX_STREAM_CONTENT_LENGTH_BYTES: Long = 2L * 1024L * 1024L * 1024L

    private const val MAX_RANGE_HEADER_LENGTH = 64
    private const val MAX_RANGE_VALUE_BYTES = 8L * 1024L * 1024L * 1024L

    private val GDRIVE_FILE_ID_REGEX = Regex("^[A-Za-z0-9_-]{10,200}$")
    private val QQMUSIC_SONG_MID_REGEX = Regex("^[A-Za-z0-9_-]{6,50}$")
    private val NAVIDROME_SONG_ID_REGEX = Regex("^[A-Za-z0-9_-]{1,100}$")
    private val JELLYFIN_ITEM_ID_REGEX = Regex("^[A-Za-z0-9]{1,100}$")
    private val FORBIDDEN_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "[::1]")
    private val EXTRA_ALLOWED_AUDIO_TYPES = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/mp4",
        "video/mp4"
    )

    data class RangeHeaderValidation(
        val isValid: Boolean,
        val normalizedHeader: String? = null,
        val startInclusive: Long? = null,
        val endInclusive: Long? = null,
        val isSuffixRange: Boolean = false
    )

    fun validateTelegramFileId(fileId: Int): Boolean = fileId > 0

    fun validateNeteaseSongId(songId: Long): Boolean = songId > 0L

    fun validateGDriveFileId(fileId: String): Boolean = GDRIVE_FILE_ID_REGEX.matches(fileId)

    fun validateQqMusicSongMid(songMid: String): Boolean = QQMUSIC_SONG_MID_REGEX.matches(songMid)

    fun validateNavidromeSongId(songId: String): Boolean = NAVIDROME_SONG_ID_REGEX.matches(songId)

    fun validateJellyfinItemId(itemId: String): Boolean = JELLYFIN_ITEM_ID_REGEX.matches(itemId)

    fun validateRangeHeader(rawHeader: String?): RangeHeaderValidation {
        if (rawHeader.isNullOrBlank()) {
            return RangeHeaderValidation(isValid = true)
        }

        val header = rawHeader.trim()
        if (header.length > MAX_RANGE_HEADER_LENGTH) {
            return RangeHeaderValidation(isValid = false)
        }
        if (!header.startsWith("bytes=") || header.contains(",")) {
            return RangeHeaderValidation(isValid = false)
        }

        val payload = header.removePrefix("bytes=")
        val dashIndex = payload.indexOf('-')
        if (dashIndex <= -1 || payload.indexOf('-', dashIndex + 1) != -1) {
            return RangeHeaderValidation(isValid = false)
        }

        val startPart = payload.substring(0, dashIndex).trim()
        val endPart = payload.substring(dashIndex + 1).trim()

        if (startPart.isEmpty() && endPart.isEmpty()) {
            return RangeHeaderValidation(isValid = false)
        }
        if (startPart.isNotEmpty() && !startPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (endPart.isNotEmpty() && !endPart.all(Char::isDigit)) {
            return RangeHeaderValidation(isValid = false)
        }

        val start = startPart.toLongOrNull()
        val end = endPart.toLongOrNull()

        if (start != null && (start < 0 || start > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (end != null && (end < 0 || end > MAX_RANGE_VALUE_BYTES)) {
            return RangeHeaderValidation(isValid = false)
        }
        if (start != null && end != null && start > end) {
            return RangeHeaderValidation(isValid = false)
        }

        return RangeHeaderValidation(
            isValid = true,
            normalizedHeader = "bytes=$startPart-$endPart",
            startInclusive = start,
            endInclusive = end,
            isSuffixRange = start == null && end != null
        )
    }

    fun isSupportedAudioContentType(contentTypeHeader: String?): Boolean {
        if (contentTypeHeader.isNullOrBlank()) {
            return true
        }
        val normalized = contentTypeHeader.substringBefore(';').trim().lowercase()
        if (normalized.startsWith("audio/")) {
            return true
        }
        return normalized in EXTRA_ALLOWED_AUDIO_TYPES
    }

    fun isAcceptableContentLength(contentLengthHeader: String?): Boolean {
        if (contentLengthHeader.isNullOrBlank()) {
            return true
        }
        val parsed = contentLengthHeader.toLongOrNull() ?: return false
        return parsed in 0..MAX_STREAM_CONTENT_LENGTH_BYTES
    }

    fun isSafeRemoteStreamUrl(
        url: String,
        allowedHostSuffixes: Set<String> = emptySet(),
        allowHttpForAllowedHosts: Boolean = false
    ): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        val host = httpUrl.host.lowercase()

        // Allow private IPs and .local for Subsonic/Navidrome/Jellyfin servers which are often self-hosted
        val isNavidromeStream = httpUrl.pathSegments.contains("stream.view")
        val isJellyfinStream = httpUrl.pathSegments.contains("Audio") && httpUrl.pathSegments.contains("universal")
        
        if (!isNavidromeStream && !isJellyfinStream) {
            if (host in FORBIDDEN_HOSTS) return false
            if (host.endsWith(".local")) return false
            if (isPrivateIpv4Literal(host)) return false
        }
        
        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return false

        val hostAllowed = hostMatchesAllowedSuffix(host, allowedHostSuffixes)
        if (!hostAllowed) return false

        return when (httpUrl.scheme.lowercase()) {
            "https" -> true
            "http" -> allowHttpForAllowedHosts && allowedHostSuffixes.isNotEmpty()
            else -> false
        }
    }

    fun mapUpstreamStatusToProxyStatus(code: Int): HttpStatusCode {
        return when (code) {
            401 -> HttpStatusCode.Unauthorized
            403 -> HttpStatusCode.Forbidden
            404 -> HttpStatusCode.NotFound
            408 -> HttpStatusCode.RequestTimeout
            416 -> HttpStatusCode(416, "Range Not Satisfiable")
            429 -> HttpStatusCode(429, "Too Many Requests")
            in 500..599 -> HttpStatusCode.BadGateway
            else -> HttpStatusCode.BadGateway
        }
    }

    private fun hostMatchesAllowedSuffix(host: String, allowedHostSuffixes: Set<String>): Boolean {
        if (allowedHostSuffixes.isEmpty()) return true
        return allowedHostSuffixes.any { suffix ->
            val normalized = suffix.lowercase()
            host == normalized || host.endsWith(".$normalized")
        }
    }

    internal fun isPrivateIpv4Literal(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false

        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        val first = octets[0]
        val second = octets[1]

        return first == 0 ||
            first == 10 ||
            first == 127 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }
}
