package com.theveloper.pixelplay.data.github

import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

data class PlayStoreAnnouncementRemoteConfig(
    val enabled: Boolean = false,
    val playStoreUrl: String? = null,
    val title: String? = null,
    val body: String? = null,
    val primaryActionLabel: String? = null,
    val dismissActionLabel: String? = null,
    val linkPendingMessage: String? = null,
)

@Singleton
class GitHubAnnouncementPropertiesService @Inject constructor() {

    /**
     * Reads announcement flags from a raw properties file in GitHub.
     *
     * Expected keys:
     * - play_store_announcement_enabled
     * - play_store_url
     * - play_store_announcement_title
     * - play_store_announcement_body
     * - play_store_primary_action
     * - play_store_dismiss_action
     * - play_store_link_pending_message
     */
    suspend fun fetchPlayStoreAnnouncement(
        owner: String = "PixelPlayerHQ",
        repo: String = "PixelPlayer",
        branch: String = "master",
        configPath: String = "remote-config/app-announcements.properties",
    ): Result<PlayStoreAnnouncementRemoteConfig> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$configPath"
                connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    addRequestProperty("Accept", "text/plain")
                }

                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> {
                        val response = connection.inputStream.bufferedReader().use { it.readText() }
                        val props = Properties().apply { load(StringReader(response)) }
                        val config = PlayStoreAnnouncementRemoteConfig(
                            enabled = props.booleanFlag("play_store_announcement_enabled"),
                            playStoreUrl = props.stringValue("play_store_url"),
                            title = props.stringValue("play_store_announcement_title"),
                            body = props.stringValue("play_store_announcement_body"),
                            primaryActionLabel = props.stringValue("play_store_primary_action"),
                            dismissActionLabel = props.stringValue("play_store_dismiss_action"),
                            linkPendingMessage = props.stringValue("play_store_link_pending_message"),
                        )
                        Result.success(config)
                    }
                    HttpURLConnection.HTTP_NOT_FOUND -> {
                        Timber.i("Remote announcement properties file not found. Keeping announcement disabled.")
                        Result.success(PlayStoreAnnouncementRemoteConfig())
                    }
                    else -> {
                        val errorMessage = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        Result.failure(
                            IllegalStateException("Failed to fetch remote announcement properties: $code - $errorMessage"),
                        )
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}

private fun Properties.stringValue(key: String): String? {
    return getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }
}

private fun Properties.booleanFlag(key: String): Boolean {
    return when (getProperty(key)?.trim()?.lowercase()) {
        "true", "1", "yes", "on" -> true
        else -> false
    }
}
