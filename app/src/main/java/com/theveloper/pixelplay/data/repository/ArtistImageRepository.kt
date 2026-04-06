package com.theveloper.pixelplay.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.network.deezer.DeezerApiService
import com.theveloper.pixelplay.utils.NetworkRetryUtils
import com.theveloper.pixelplay.utils.isRetryableNetworkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import androidx.core.graphics.scale

/**
 * Repository for fetching and caching artist images from Deezer API.
 * Uses both in-memory LRU cache and Room database for persistent storage.
 */
@Singleton
class ArtistImageRepository @Inject constructor(
    private val deezerApiService: DeezerApiService,
    private val musicDao: MusicDao
) {
    companion object {
        private const val TAG = "ArtistImageRepository"
        private const val CACHE_SIZE = 100 // Number of artist images to cache in memory
        private const val PREFETCH_CONCURRENCY = 3 // Limit parallel API calls
        private val deezerSizeRegex = Regex("/\\d{2,4}x\\d{2,4}([\\-.])")
        private const val NETWORK_RETRY_ATTEMPTS = 3
        private const val NETWORK_RETRY_INITIAL_DELAY_MS = 500L
        private const val MAX_CUSTOM_IMAGE_SOURCE_BYTES = 24L * 1024L * 1024L
        private const val MAX_CUSTOM_IMAGE_DIMENSION_PX = 16_384
        private const val MAX_CUSTOM_IMAGE_TOTAL_PIXELS = 80_000_000L
        private const val TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX = 2_048
        private const val TARGET_CUSTOM_IMAGE_MAX_PIXELS = 4_194_304L // 2048x2048

        internal fun calculateCustomImageSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            while (
                width / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                height / sampleSize > TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX ||
                (width.toLong() / sampleSize) * (height.toLong() / sampleSize) > TARGET_CUSTOM_IMAGE_MAX_PIXELS
            ) {
                sampleSize = sampleSize shl 1
            }
            return sampleSize.coerceAtLeast(1)
        }
    }

    // In-memory LRU cache for quick access
    private val memoryCache = LruCache<String, String>(CACHE_SIZE)
    
    // Mutex to prevent duplicate API calls for the same artist
    private val fetchMutex = Mutex()
    private val pendingFetches = mutableSetOf<String>()
    
    // Semaphore to limit concurrent API calls during prefetch
    private val prefetchSemaphore = Semaphore(PREFETCH_CONCURRENCY)
    
    // Set to track artists for whom image fetching failed (e.g. not found), to avoid retrying in the same session
    private val failedFetches = mutableSetOf<String>()

    /**
     * Get artist image URL, fetching from Deezer if not cached.
     * @param artistName Name of the artist
     * @param artistId Room database ID of the artist (for caching)
     * @return Image URL or null if not found
     */
    suspend fun getArtistImageUrl(artistName: String, artistId: Long): String? {
        if (artistName.isBlank()) return null

        val normalizedName = artistName.trim().lowercase()

        // Check memory cache first
        memoryCache.get(normalizedName)?.let { cachedUrl ->
            return cachedUrl
        }
        
        // Check if previously failed
        if (failedFetches.contains(normalizedName)) {
            return null
        }

        // Resolve canonical DB artist row by name to avoid MediaStore-ID/DB-ID mismatches.
        val (resolvedArtistId, dbCachedUrl) = withContext(Dispatchers.IO) {
            val canonicalArtistId = musicDao.getArtistIdByNormalizedName(artistName) ?: artistId
            val cachedUrl = musicDao.getArtistImageUrl(canonicalArtistId)
                ?: musicDao.getArtistImageUrlByNormalizedName(artistName)
            canonicalArtistId to cachedUrl
        }
        if (!dbCachedUrl.isNullOrEmpty()) {
            val upgradedDbUrl = upgradeToHighResDeezerUrl(dbCachedUrl)
            memoryCache.put(normalizedName, upgradedDbUrl)
            if (upgradedDbUrl != dbCachedUrl) {
                withContext(Dispatchers.IO) {
                    musicDao.updateArtistImageUrl(resolvedArtistId, upgradedDbUrl)
                }
            }
            return upgradedDbUrl
        }

        // Fetch from Deezer API
        return fetchAndCacheArtistImage(artistName, resolvedArtistId, normalizedName)
    }

    /**
     * Prefetch artist images for a list of artists in background.
     * Useful for batch loading when displaying artist lists.
     */
    suspend fun prefetchArtistImages(artists: List<Pair<Long, String>>) = withContext(Dispatchers.IO) {
        // Process in small chunks to avoid creating hundreds of coroutines simultaneously.
        // Without this, a library with 500 artists creates 500 coroutine objects at once, all
        // suspended at the semaphore, exhausting the heap and triggering OOM in coroutine machinery.
        artists.chunked(PREFETCH_CONCURRENCY * 4).forEach { chunk ->
            chunk.map { (artistId, artistName) ->
                async {
                    try {
                        val normalizedName = artistName.trim().lowercase()
                        if (memoryCache.get(normalizedName) == null && !failedFetches.contains(normalizedName)) {
                            prefetchSemaphore.withPermit {
                                getArtistImageUrl(artistName, artistId)
                            }
                        } else {
                            Timber.tag(TAG).d("Skipping prefetch for $artistName") //check
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w("Failed to prefetch image for $artistName: ${e.message}")
                    }
                }
            }.awaitAll()
        }
    }
    
    // ... fetchAndCacheArtistImage method ...
    
    private suspend fun fetchAndCacheArtistImage(
        artistName: String,
        artistId: Long,
        normalizedName: String
    ): String? {
        // Prevent duplicate fetches for the same artist
        fetchMutex.withLock {
            if (pendingFetches.contains(normalizedName)) {
                return null // Already fetching
            }
            pendingFetches.add(normalizedName)
        }

        return try {
            withContext(Dispatchers.IO) {
                val response = withNetworkRetry("deezer_search:$artistName") {
                    deezerApiService.searchArtist(artistName, limit = 1)
                }
                val deezerArtist = response.data.firstOrNull()

                if (deezerArtist != null) {
                    val imageUrl = (
                        deezerArtist.pictureXl
                            ?: deezerArtist.pictureBig
                            ?: deezerArtist.pictureMedium
                            ?: deezerArtist.picture
                        )?.let(::upgradeToHighResDeezerUrl)

                    if (!imageUrl.isNullOrEmpty()) {
                        // Cache in memory
                        memoryCache.put(normalizedName, imageUrl)
                        
                        // Cache in database
                        musicDao.updateArtistImageUrl(artistId, imageUrl)

                        Timber.tag(TAG).d("Fetched and cached image for $artistName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Timber.tag(TAG).d("No Deezer artist found for: $artistName")
                    failedFetches.add(normalizedName) // Mark as failed
                    null
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e("Error fetching artist image for $artistName: ${e.message}")
            // Consider transient errors? For now treating as failed to avoid spam.
            if(e !is java.net.SocketTimeoutException) {
                failedFetches.add(normalizedName)
            }
            null
        } finally {
            fetchMutex.withLock {
                pendingFetches.remove(normalizedName)
            }
        }
    }

    private suspend fun <T> withNetworkRetry(
        operationName: String,
        maxAttempts: Int = NETWORK_RETRY_ATTEMPTS,
        initialDelayMs: Long = NETWORK_RETRY_INITIAL_DELAY_MS,
        block: suspend () -> T
    ): T {
        return NetworkRetryUtils.withNetworkRetry(
            operationName = operationName,
            maxAttempts = maxAttempts,
            initialDelayMs = initialDelayMs,
            shouldRetry = { throwable -> throwable.isRetryableNetworkError() },
            onRetry = { attempt, attempts, throwable ->
                Timber.tag(TAG)
                    .d("Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}")
            },
            block = block
        )
    }
    
    /**
     * Clear all cached images. Useful for debugging or forced refresh.
     */
    fun clearCache() {
        memoryCache.evictAll()
        failedFetches.clear()
    }

    /**
     * Returns the effective image URL for an artist:
     * - If a custom (user-set) image exists in DB → returns that path
     * - Otherwise falls back to the Deezer URL (fetching from API if needed)
     */
    suspend fun getEffectiveArtistImageUrl(artistId: Long, artistName: String): String? {
        val customUri = withContext(Dispatchers.IO) { musicDao.getArtistCustomImage(artistId) }
        if (!customUri.isNullOrBlank()) return customUri
        return getArtistImageUrl(artistName, artistId)
    }

    /**
     * Saves a user-selected image as the artist's custom image.
     *
     * The content URI is resolved immediately and the bitmap is written to
     * internal storage (filesDir/artist_art_<id>.jpg). This avoids depending
     * on a content URI that may expire once the photo-picker dismisses.
     *
     * @param context Application context (used for contentResolver and filesDir)
     * @param artistId The artist's database row ID
     * @param sourceUri URI returned by the system photo-picker
     * @return The internal file path on success, null on failure
     */
    suspend fun setCustomArtistImage(context: Context, artistId: Long, sourceUri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = decodeCustomArtistBitmap(context, sourceUri) ?: return@withContext null
                val scaledBitmap = scaleBitmapIfNeeded(bitmap)
                try {
                    // 2. Write to internal storage as JPEG (compact and predictable for caching)
                    val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                    FileOutputStream(destFile).use { out ->
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                    }

                    val internalPath = destFile.absolutePath

                    // 3. Persist to DB
                    musicDao.updateArtistCustomImage(artistId, internalPath)

                    // 4. The ViewModel reloads the effective image URL on success, so we only need
                    // to persist the internal file path here.
                    Timber.tag(TAG).d("Custom artist image saved: $internalPath")
                    internalPath
                } finally {
                    if (scaledBitmap !== bitmap) {
                        bitmap.recycle()
                    }
                    scaledBitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to save custom artist image for id=$artistId: ${e.message}")
                null
            }
        }
    }

    private fun decodeCustomArtistBitmap(context: Context, sourceUri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(sourceUri)?.lowercase()
        if (mimeType != null && !mimeType.startsWith("image/")) {
            Timber.tag(TAG).w("Rejected custom artist image with unsupported MIME type: $mimeType")
            return null
        }

        runCatching { resolver.openAssetFileDescriptor(sourceUri, "r") }.getOrNull()?.use { descriptor ->
            val declaredLength = descriptor.length
            if (declaredLength > MAX_CUSTOM_IMAGE_SOURCE_BYTES) {
                Timber.tag(TAG)
                    .w("Rejected custom artist image larger than allowed source size: $declaredLength")
                return null
            }
        }

        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        
        // Fetch dimensions without loading the full bitmap into memory.
        // decodeStream returns null when inJustDecodeBounds is true.
        resolver.openInputStream(sourceUri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with invalid bounds: ${width}x${height}")
            return null
        }
        if (width > MAX_CUSTOM_IMAGE_DIMENSION_PX || height > MAX_CUSTOM_IMAGE_DIMENSION_PX) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with oversized bounds: ${width}x${height}")
            return null
        }
        if (width.toLong() * height.toLong() > MAX_CUSTOM_IMAGE_TOTAL_PIXELS) {
            Timber.tag(TAG)
                .w("Rejected custom artist image with excessive pixel count: ${width}x${height}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateCustomImageSampleSize(width, height)
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        return try {
            resolver.openInputStream(sourceUri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, decodeOptions)
            }
        } catch (oom: OutOfMemoryError) {
            Timber.tag(TAG).e(oom, "Failed to decode custom artist image due to OOM")
            null
        }
    }

    private fun scaleBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX) {
            return bitmap
        }

        val scale = TARGET_CUSTOM_IMAGE_MAX_DIMENSION_PX.toFloat() / longestEdge.toFloat()
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return bitmap.scale(scaledWidth, scaledHeight)
    }

    /**
     * Removes the user's custom artist image, reverting to the Deezer URL.
     *
     * @param context Application context
     * @param artistId The artist's database row ID
     */
    suspend fun clearCustomArtistImage(context: Context, artistId: Long) {
        withContext(Dispatchers.IO) {
            try {
                // Delete the internal file if it exists
                val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                if (destFile.exists()) {
                    destFile.delete()
                    Timber.tag(TAG).d("Deleted custom artist image file: ${destFile.absolutePath}")
                }
                // Clear from DB
                musicDao.updateArtistCustomImage(artistId, null)
            } catch (e: Exception) {
                Timber.tag(TAG)
                    .e("Failed to clear custom artist image for id=$artistId: ${e.message}")
            }
        }
    }

    private fun upgradeToHighResDeezerUrl(url: String): String {
        if (!url.contains("dzcdn.net/images/artist")) return url
        return deezerSizeRegex.replace(url, "/1000x1000$1")
    }
}
