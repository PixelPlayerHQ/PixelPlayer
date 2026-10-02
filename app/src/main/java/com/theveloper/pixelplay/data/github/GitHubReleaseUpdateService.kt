package com.theveloper.pixelplay.data.github

import android.os.Build
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

data class GitHubLatestRelease(
    val version: String,
    val pageUrl: String,
    val apkUrl: String,
)

@Singleton
class GitHubReleaseUpdateService @Inject constructor() {

    /**
     * Latest GitHub Release for this fork. A push to master publishes that
     * release from CI, so installed builds can notice a newer tag.
     */
    suspend fun fetchLatestRelease(
        owner: String = OWNER,
        repo: String = REPO,
    ): Result<GitHubLatestRelease> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = "https://api.github.com/repos/$owner/$repo/releases/latest"
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PixelPlayer")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val version = json.optString("tag_name").trim().removePrefix("v")
                    val pageUrl = json.optString("html_url").trim()
                    val apkUrl = selectApkUrl(json)
                    if (version.isEmpty() || !isAllowedReleasePage(pageUrl)) {
                        Result.failure(IllegalStateException("Latest release is missing a usable tag or page"))
                    } else {
                        Result.success(
                            GitHubLatestRelease(version = version, pageUrl = pageUrl, apkUrl = apkUrl),
                        )
                    }
                }
                else -> Result.failure(
                    IllegalStateException("Latest release request failed: $code"),
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun downloadApk(url: String, destination: File): Result<File> = withContext(Dispatchers.IO) {
        if (!isAllowedApkDownload(url)) {
            return@withContext Result.failure(IllegalStateException("Refusing APK URL"))
        }
        var current = URL(url)
        var connection: HttpURLConnection? = null
        try {
            repeat(MAX_REDIRECTS) {
                connection?.disconnect()
                connection = (current.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "PixelPlayer")
                    setRequestProperty("Accept", "application/octet-stream, */*")
                }
                val code = connection!!.responseCode
                if (code in 300..399) {
                    val location = connection!!.getHeaderField("Location")
                        ?: return@withContext Result.failure(IllegalStateException("Redirect missing location"))
                    val next = URL(current, location)
                    if (!isAllowedDownloadHop(next)) {
                        return@withContext Result.failure(IllegalStateException("Refusing redirect host"))
                    }
                    current = next
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext Result.failure(IllegalStateException("APK download failed: $code"))
                }
                destination.parentFile?.mkdirs()
                if (destination.exists()) destination.delete()
                connection!!.inputStream.use { input ->
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                if (destination.length() < MIN_APK_BYTES) {
                    destination.delete()
                    return@withContext Result.failure(IllegalStateException("Downloaded file is too small"))
                }
                return@withContext Result.success(destination)
            }
            Result.failure(IllegalStateException("Too many redirects"))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            destination.delete()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun selectApkUrl(json: JSONObject): String {
        val assets = json.optJSONArray("assets") ?: return ""
        val abi = preferredAbi()
        var matched = ""
        var fallback = ""
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || !isAllowedApkDownload(url)) continue
            if (!name.contains("arm64-v8a") && !name.contains("armeabi-v7a")) continue
            if (fallback.isEmpty() && name.contains("armeabi-v7a")) fallback = url
            if (name.contains(abi)) matched = url
        }
        return matched.ifEmpty { fallback }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MIN_APK_BYTES = 1024L
        const val OWNER = "kibetmasi"
        const val REPO = "PixelPlayer"

        fun isAllowedReleasePage(url: String): Boolean {
            val parsed = runCatching { URL(url) }.getOrNull() ?: return false
            if (!parsed.protocol.equals("https", ignoreCase = true)) return false
            if (!parsed.host.equals("github.com", ignoreCase = true)) return false
            val path = parsed.path.orEmpty()
            return path == "/$OWNER/$REPO/releases" ||
                path.startsWith("/$OWNER/$REPO/releases/")
        }

        fun isAllowedApkDownload(url: String): Boolean {
            val parsed = runCatching { URL(url) }.getOrNull() ?: return false
            if (!parsed.protocol.equals("https", ignoreCase = true)) return false
            if (!parsed.host.equals("github.com", ignoreCase = true)) return false
            val path = parsed.path.orEmpty()
            return path.startsWith("/$OWNER/$REPO/releases/download/") &&
                path.endsWith(".apk", ignoreCase = true)
        }

        private fun isAllowedDownloadHop(url: URL): Boolean {
            if (!url.protocol.equals("https", ignoreCase = true)) return false
            val host = url.host.orEmpty().lowercase()
            if (host == "github.com") return isAllowedApkDownload(url.toString())
            return host == "release-assets.githubusercontent.com" ||
                host == "objects.githubusercontent.com" ||
                host == "github-releases.githubusercontent.com"
        }

        private fun preferredAbi(): String {
            val abis = Build.SUPPORTED_ABIS
            return when {
                abis.any { it == "arm64-v8a" } -> "arm64-v8a"
                else -> "armeabi-v7a"
            }
        }
    }
}
