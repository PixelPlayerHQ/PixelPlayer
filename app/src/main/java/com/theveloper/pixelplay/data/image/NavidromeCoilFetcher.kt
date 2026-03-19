package com.theveloper.pixelplay.data.image

import android.net.Uri
import coil.ImageLoader
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.theveloper.pixelplay.data.navidrome.NavidromeRepository
import com.theveloper.pixelplay.data.network.navidrome.NavidromeApiService
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Custom Coil Fetcher for Navidrome album art.
 * Handles URIs in format: navidrome_cover://coverArtId
 *
 * Converts the cover art ID to a full HTTP URL using the Navidrome API
 * and downloads the image to a local cache.
 */
class NavidromeCoilFetcher(
    private val uri: Uri,
    private val repository: NavidromeRepository,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File
) : Fetcher {

    companion object {
        private const val TAG = "NavidromeCoilFetcher"
        private val recentlyLoggedFailures = ConcurrentHashMap<String, Long>()
        private const val LOG_FAILURE_COOLDOWN_MS = 60_000L

        private fun shouldLogFailure(key: String): Boolean {
            val now = System.currentTimeMillis()
            val lastLogged = recentlyLoggedFailures[key]
            return if (lastLogged == null || now - lastLogged > LOG_FAILURE_COOLDOWN_MS) {
                recentlyLoggedFailures[key] = now
                if (recentlyLoggedFailures.size > 100) {
                    recentlyLoggedFailures.entries.removeIf { now - it.value > LOG_FAILURE_COOLDOWN_MS }
                }
                true
            } else {
                false
            }
        }
    }

    override suspend fun fetch(): FetchResult? {
        Timber.v("$TAG: Fetching $uri")

        // Parse URI: navidrome_cover://coverArtId
        val coverArtId = uri.host ?: uri.path?.removePrefix("/")
        if (coverArtId.isNullOrBlank()) {
            Timber.w("$TAG: Invalid URI format: $uri")
            return null
        }

        // Check if user is logged in
        if (!repository.isLoggedIn) {
            Timber.v("$TAG: Not logged in, skipping fetch")
            return null
        }

        // Check for size parameter
        val sizeParam = uri.getQueryParameter("size")?.toIntOrNull() ?: 500

        // Check cache first
        val cachedFile = File(cacheDir, "navidrome_cover_${coverArtId}_$sizeParam.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            Timber.v("$TAG: Using cached cover for $coverArtId")
            return SourceResult(
                source = coil.decode.ImageSource(
                    file = cachedFile.absolutePath.toPath(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = "image/jpeg",
                dataSource = coil.decode.DataSource.DISK
            )
        }

        // Get the cover art URL from the repository
        val coverArtUrl = repository.getCoverArtUrl(coverArtId, sizeParam)
        if (coverArtUrl.isNullOrBlank()) {
            if (shouldLogFailure("no_url_$coverArtId")) {
                Timber.w("$TAG: No cover art URL for $coverArtId")
            }
            return null
        }

        // Download the image
        return try {
            downloadImage(coverArtUrl, cachedFile)
        } catch (e: Exception) {
            if (shouldLogFailure("download_$coverArtId")) {
                Timber.w(e, "$TAG: Failed to download cover art for $coverArtId")
            }
            null
        }
    }

    private suspend fun downloadImage(url: String, cacheFile: File): FetchResult? {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("$TAG: HTTP ${response.code} for $url")
                    return null
                }

                val body = response.body ?: return null
                val bytes = body.bytes()

                if (bytes.isEmpty()) {
                    Timber.w("$TAG: Empty response body for $url")
                    return null
                }

                // Save to cache
                FileOutputStream(cacheFile).use { fos ->
                    fos.write(bytes)
                }

                Timber.v("$TAG: Cached cover art (${bytes.size} bytes)")

                SourceResult(
                    source = coil.decode.ImageSource(
                        file = cacheFile.absolutePath.toPath(),
                        fileSystem = okio.FileSystem.SYSTEM
                    ),
                    mimeType = response.header("Content-Type") ?: "image/jpeg",
                    dataSource = coil.decode.DataSource.NETWORK
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to download image from $url")
            null
        }
    }

    /**
     * Factory for creating NavidromeCoilFetcher instances.
     * Registered with Coil's ImageLoader to handle navidrome_cover:// URIs.
     */
    class Factory @Inject constructor(
        private val repository: NavidromeRepository,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        private var cacheDir: File? = null

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "navidrome_cover") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                NavidromeCoilFetcher(data, repository, okHttpClient, cache)
            } else {
                null
            }
        }
    }
}
