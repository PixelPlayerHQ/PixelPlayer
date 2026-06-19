package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Shared URL normalization and validation for self-hosted media server credentials
 * (Navidrome/Subsonic, Jellyfin, etc.).
 */
object ServerUrlUtils {

    fun normalizeHttpUrl(serverUrl: String): HttpUrl? {
        val trimmed = serverUrl.trim().trimEnd('/')
        val withScheme = if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            "https://$trimmed"
        } else {
            trimmed
        }
        return withScheme.toHttpUrlOrNull()
    }

    fun normalizeServerUrl(serverUrl: String): String {
        return normalizeHttpUrl(serverUrl)?.toString()?.trimEnd('/')
            ?: serverUrl.trim().trimEnd('/')
    }

    fun connectionValidationError(serverUrl: String, serverLabel: String = "server"): String? {
        val parsed = normalizeHttpUrl(serverUrl)
            ?: return "Invalid server URL format"

        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
            return "Server URL must not contain embedded credentials."
        }

        if (!parsed.isHttps && !CloudStreamSecurity.isLocalOrPrivateHost(parsed.host)) {
            return "Use https:// for remote $serverLabel servers. HTTP is only allowed for local network addresses."
        }

        return null
    }
}
