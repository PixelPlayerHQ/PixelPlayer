package com.theveloper.pixelplay.data.github

import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
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
     * Reads a raw `.properties` file from a GitHub repo over its raw-content CDN. Generic on
     * purpose (`GEN-DES-01`): this class has no idea what a "Play Store announcement" or a
     * "downloads feature flag" is — each caller maps the result to its own type, the way
     * [fetchPlayStoreAnnouncement] does below.
     *
     * A 404 is **not** a failure: it means the file doesn't exist (yet, or was deliberately
     * removed), and the caller gets an empty [Properties] back to interpret however its own
     * defaults say to — this is what makes a remote kill switch fail safe instead of fail
     * enabled. Any other non-2xx status, a network error, or an unreadable response is a
     * [Result.failure]. Cancelling the calling coroutine while this suspends propagates as
     * [CancellationException], never as a [Result.failure] (`AND-CONC-04`).
     */
    suspend fun fetchProperties(
        owner: String,
        repo: String,
        branch: String,
        configPath: String,
    ): Result<Properties> = fetchRawProperties(buildRawContentUrl(owner, repo, branch, configPath))

    /**
     * Reads announcement flags from a raw properties file in GitHub. A mapping over
     * [fetchProperties]; the network call and its error handling live there now.
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
        owner: String = "theovilardo",
        repo: String = "PixelPlay",
        branch: String = "master",
        configPath: String = "remote-config/app-announcements.properties",
    ): Result<PlayStoreAnnouncementRemoteConfig> {
        return fetchProperties(owner, repo, branch, configPath)
            .mapCatching { it.toPlayStoreAnnouncementRemoteConfig() }
    }
}

/**
 * The mapping [fetchPlayStoreAnnouncement] applies to whatever [fetchProperties] returns.
 * `internal` and pulled out on its own so it's testable directly with a hand-built
 * [Properties] — no network involved — which is most of what "same behavior as before"
 * actually means here.
 */
internal fun Properties.toPlayStoreAnnouncementRemoteConfig(): PlayStoreAnnouncementRemoteConfig =
    PlayStoreAnnouncementRemoteConfig(
        enabled = booleanFlag("play_store_announcement_enabled"),
        playStoreUrl = stringValue("play_store_url"),
        title = stringValue("play_store_announcement_title"),
        body = stringValue("play_store_announcement_body"),
        primaryActionLabel = stringValue("play_store_primary_action"),
        dismissActionLabel = stringValue("play_store_dismiss_action"),
        linkPendingMessage = stringValue("play_store_link_pending_message"),
    )

/** Where [fetchProperties] actually points. Pure string-building, no I/O — `internal` so a
 * test can verify it without touching the network. */
internal fun buildRawContentUrl(owner: String, repo: String, branch: String, configPath: String): String =
    "https://raw.githubusercontent.com/$owner/$repo/$branch/$configPath"

/**
 * Does the actual HTTP GET against [rawUrl] and parses the response as [Properties]. `internal`
 * so a test can point it at a local server instead of `raw.githubusercontent.com` — the public
 * [fetchProperties] always builds a real GitHub URL, so this is the only seam that makes the
 * 404 / error behaviors verifiable without a live network call.
 *
 * The `catch (e: CancellationException) { throw e }` below is the actual fix this task exists
 * for (`AND-CONC-04`): the original code caught a broad `Exception`, which would have silently
 * turned a coroutine cancellation into an ordinary [Result.failure] instead of letting it
 * propagate. It's worth being honest about what this does and doesn't guarantee:
 * `HttpURLConnection` is a classic blocking-socket API that does **not** react to
 * `Thread.interrupt()`, so cancelling the caller while this is blocked in
 * [HttpURLConnection.getResponseCode] does not interrupt that call — it still runs to
 * completion (or its own timeout) before this function gets a chance to notice anything. What
 * this fix guarantees is narrower but real: cancellation is never *swallowed* once it does
 * reach this code, at whichever point that happens to be. Making the blocking call itself
 * promptly interruptible would mean replacing `HttpURLConnection` with something that supports
 * it (OkHttp, e.g.) — out of scope for a "fix the catch" task; noted for whoever touches this
 * next.
 */
internal suspend fun fetchRawProperties(rawUrl: String): Result<Properties> {
    return withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val conn = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                addRequestProperty("Accept", "text/plain")
            }
            connection = conn

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    Result.success(Properties().apply { load(StringReader(response)) })
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    Timber.i("Remote properties file not found at $rawUrl. Returning empty properties.")
                    Result.success(Properties())
                }
                else -> {
                    val errorMessage = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    Result.failure(
                        IllegalStateException("Failed to fetch remote properties: $code - $errorMessage"),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
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
