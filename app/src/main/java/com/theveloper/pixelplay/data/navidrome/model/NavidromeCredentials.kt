package com.theveloper.pixelplay.data.navidrome.model

/**
 * Represents authentication credentials for a Navidrome/Subsonic server.
 *
 * Navidrome supports two authentication methods:
 * 1. Password in URL (p=xxx) - not recommended for security
 * 2. Token-based authentication (t=xxx&s=xxx) - recommended
 *
 * @property serverUrl The base URL of the Navidrome server (e.g., "https://music.example.com")
 * @property username The username for authentication
 * @property password The password (stored securely, used to generate tokens)
 * @property clientId The client identifier sent to the server (default: "PixelPlayer")
 */
data class NavidromeCredentials(
    val serverUrl: String,
    val username: String,
    val password: String,
    val clientId: String = "PixelPlayer"
) {
    companion object {
        /**
         * The Subsonic API version supported by this implementation.
         */
        const val API_VERSION = "1.16.1"

        /**
         * Creates an empty credentials object.
         */
        fun empty() = NavidromeCredentials(
            serverUrl = "",
            username = "",
            password = "",
            clientId = "PixelPlayer"
        )
    }

    /**
     * Returns true if the credentials have all required fields populated.
     */
    val isValid: Boolean
        get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /**
     * Returns the normalized server URL (without trailing slash).
     */
    val normalizedServerUrl: String
        get() = serverUrl.trimEnd('/')
}
