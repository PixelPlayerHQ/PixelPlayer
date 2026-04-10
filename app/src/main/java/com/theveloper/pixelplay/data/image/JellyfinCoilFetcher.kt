package com.theveloper.pixelplay.data.image

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.theveloper.pixelplay.data.jellyfin.JellyfinRepository
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toPath
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class JellyfinCoilFetcher(
    private val uri: Uri,
    private val repository: JellyfinRepository,
    private val okHttpClient: OkHttpClient,
    private val cacheDir: File
) : Fetcher {

    companion object {
        private const val TAG = "JellyfinCoilFetcher"
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

        val itemId = uri.host ?: uri.path?.removePrefix("/")
        if (itemId.isNullOrBlank()) {
            Timber.w("$TAG: Invalid URI format: $uri")
            return null
        }

        if (!repository.isLoggedIn) {
            Timber.v("$TAG: Not logged in, skipping fetch")
            return null
        }

        val sizeParam = uri.getQueryParameter("size")?.toIntOrNull() ?: 500

        val cachedFile = File(cacheDir, "jellyfin_cover_${itemId}_$sizeParam.jpg")
        if (cachedFile.exists() && cachedFile.length() > 0) {
            Timber.v("$TAG: Using cached cover for $itemId")
            return SourceResult(
                source = ImageSource(
                    file = cachedFile.absolutePath.toPath(),
                    fileSystem = FileSystem.SYSTEM
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        val imageUrl = repository.getImageUrl(itemId, sizeParam)
        if (imageUrl.isNullOrBlank()) {
            if (shouldLogFailure("no_url_$itemId")) {
                Timber.w("$TAG: No image URL for $itemId")
            }
            return null
        }

        val authHeader = repository.getAuthorizationHeader()

        return try {
            downloadImage(imageUrl, cachedFile, authHeader)
        } catch (e: Exception) {
            if (shouldLogFailure("download_$itemId")) {
                Timber.w(e, "$TAG: Failed to download cover art for $itemId")
            }
            null
        }
    }

    private fun downloadImage(url: String, cacheFile: File, authHeader: String? = null): FetchResult? {
        val request = Request.Builder()
            .url(url)
            .apply { if (authHeader != null) header("Authorization", authHeader) }
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

                FileOutputStream(cacheFile).use { fos ->
                    fos.write(bytes)
                }

                Timber.v("$TAG: Cached cover art (${bytes.size} bytes)")

                SourceResult(
                    source = ImageSource(
                        file = cacheFile.absolutePath.toPath(),
                        fileSystem = FileSystem.SYSTEM
                    ),
                    mimeType = response.header("Content-Type") ?: "image/jpeg",
                    dataSource = DataSource.NETWORK
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to download image from $url")
            null
        }
    }

    class Factory @Inject constructor(
        private val repository: JellyfinRepository,
        private val okHttpClient: OkHttpClient
    ) : Fetcher.Factory<Uri> {

        private var cacheDir: File? = null

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "jellyfin_cover") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                JellyfinCoilFetcher(data, repository, okHttpClient, cache)
            } else {
                null
            }
        }
    }
}
