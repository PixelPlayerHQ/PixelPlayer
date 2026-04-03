package com.theveloper.pixelplay.data.service.http

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.security.SecureRandom

internal data class CastAccessPolicy(
    val authToken: String?,
    val allowedSongIds: Set<String>,
    val allowedClientAddresses: Set<String>,
    val enforceClientAddressAllowlist: Boolean
) {
    companion object {
        val EMPTY = CastAccessPolicy(
            authToken = null,
            allowedSongIds = emptySet(),
            allowedClientAddresses = emptySet(),
            enforceClientAddressAllowlist = false
        )
    }
}

internal object CastSessionSecurity {
    const val AUTH_QUERY_PARAMETER = "auth"
    private val secureRandom = SecureRandom()
    private val loopbackCandidates = setOf(
        "127.0.0.1",
        "::1",
        "0:0:0:0:0:0:0:1",
        "::ffff:127.0.0.1"
    )

    fun buildAccessPolicy(
        existingToken: String?,
        allowedSongIds: Collection<String>,
        castDeviceIpHint: String?
    ): CastAccessPolicy {
        val normalizedSongIds = allowedSongIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
        val castAddressVariants = normalizeAddressVariants(castDeviceIpHint)
        val allowedAddresses = buildSet {
            loopbackCandidates.forEach { addAll(normalizeAddressVariants(it)) }
            addAll(castAddressVariants)
        }
        return CastAccessPolicy(
            authToken = existingToken ?: generateAuthToken(),
            allowedSongIds = normalizedSongIds,
            allowedClientAddresses = allowedAddresses,
            enforceClientAddressAllowlist = castAddressVariants.isNotEmpty()
        )
    }

    fun isLoopbackAddress(rawAddress: String?): Boolean {
        val normalized = normalizeAddressVariants(rawAddress)
        return normalized.any { candidate -> candidate in loopbackCandidates }
    }

    fun isAuthorizedClientAddress(remoteAddress: String?, policy: CastAccessPolicy): Boolean {
        if (isLoopbackAddress(remoteAddress)) return true
        if (!policy.enforceClientAddressAllowlist) return true
        if (policy.allowedClientAddresses.isEmpty()) return false
        val normalizedRemote = normalizeAddressVariants(remoteAddress)
        return normalizedRemote.any { candidate -> candidate in policy.allowedClientAddresses }
    }

    fun isAuthorizedSongRequest(
        providedToken: String?,
        requestedSongId: String?,
        policy: CastAccessPolicy
    ): Boolean {
        val normalizedSongId = requestedSongId?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val authToken = policy.authToken ?: return false
        if (providedToken != authToken) return false
        return normalizedSongId in policy.allowedSongIds
    }

    fun buildSongUrl(
        serverAddress: String,
        songId: String,
        streamRevision: String,
        authToken: String?
    ): String {
        return buildProtectedUrl(
            serverAddress = serverAddress,
            endpoint = "song",
            songId = songId,
            streamRevision = streamRevision,
            authToken = authToken
        )
    }

    fun buildArtUrl(
        serverAddress: String,
        songId: String,
        streamRevision: String,
        authToken: String?
    ): String {
        return buildProtectedUrl(
            serverAddress = serverAddress,
            endpoint = "art",
            songId = songId,
            streamRevision = streamRevision,
            authToken = authToken
        )
    }

    fun buildLoopbackSongUrl(
        serverAddress: String,
        songId: String,
        authToken: String?
    ): String? {
        val baseUrl = serverAddress.toHttpUrlOrNull() ?: return null
        return baseUrl
            .newBuilder()
            .host("127.0.0.1")
            .encodedPath("/")
            .addPathSegment("song")
            .addPathSegment(songId)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    addQueryParameter(AUTH_QUERY_PARAMETER, authToken)
                }
            }
            .build()
            .toString()
    }

    fun redactAuthToken(url: String): String {
        return url.replace(Regex("([?&])$AUTH_QUERY_PARAMETER=[^&]+"), "$1$AUTH_QUERY_PARAMETER=<redacted>")
    }

    private fun buildProtectedUrl(
        serverAddress: String,
        endpoint: String,
        songId: String,
        streamRevision: String,
        authToken: String?
    ): String {
        val baseUrl = requireNotNull(serverAddress.toHttpUrlOrNull()) {
            "Invalid cast server address: $serverAddress"
        }
        return baseUrl
            .newBuilder()
            .encodedPath("/")
            .addPathSegment(endpoint)
            .addPathSegment(songId)
            .addQueryParameter("v", streamRevision)
            .apply {
                if (!authToken.isNullOrBlank()) {
                    addQueryParameter(AUTH_QUERY_PARAMETER, authToken)
                }
            }
            .build()
            .toString()
    }

    private fun generateAuthToken(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun normalizeAddressVariants(rawAddress: String?): Set<String> {
        val trimmed = rawAddress?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return emptySet()
        val normalized = linkedSetOf(trimmed)
        val parsed = runCatching { InetAddress.getByName(trimmed) }.getOrNull()
        if (parsed != null) {
            parsed.hostAddress?.lowercase()?.let(normalized::add)
            if (parsed.isLoopbackAddress) {
                normalized += loopbackCandidates
            }
        }
        if (trimmed.startsWith("::ffff:")) {
            normalized += trimmed.removePrefix("::ffff:")
        }
        normalized
            .filter { it.startsWith("::ffff:") }
            .forEach { candidate ->
                normalized += candidate.removePrefix("::ffff:")
            }
        return normalized
    }
}
