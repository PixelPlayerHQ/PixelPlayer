package com.theveloper.pixelplay.data.jellyfin.model

import com.theveloper.pixelplay.utils.ServerUrlUtils
import okhttp3.HttpUrl

data class JellyfinCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val accessToken: String? = null,
    val userId: String? = null
) {
    companion object {
        fun empty() = JellyfinCredentials(
            serverUrl = "",
            username = "",
            password = "",
            accessToken = null,
            userId = null
        )
    }

    val isValid: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() &&
                (password.isNotBlank() || !accessToken.isNullOrBlank())

    val hasToken: Boolean
        get() = !accessToken.isNullOrBlank() && !userId.isNullOrBlank()

    val normalizedHttpUrlOrNull: HttpUrl?
        get() = ServerUrlUtils.normalizeHttpUrl(serverUrl)

    val normalizedServerUrl: String
        get() = ServerUrlUtils.normalizeServerUrl(serverUrl)

    fun connectionValidationError(): String? =
        ServerUrlUtils.connectionValidationError(serverUrl, "Jellyfin")
}
