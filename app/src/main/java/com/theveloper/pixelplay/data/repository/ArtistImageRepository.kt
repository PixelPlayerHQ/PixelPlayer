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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to prefetch image for $artistName: ${e.message}")
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
                        
                        Log.d(TAG, "Fetched and cached image for $artistName: $imageUrl")
                        imageUrl
                    } else {
                        null
                    }
                } else {
                    Log.d(TAG, "No Deezer artist found for: $artistName")
                    failedFetches.add(normalizedName) // Mark as failed
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching artist image for $artistName: ${e.message}")
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
                Log.d(
                    TAG,
                    "Retrying $operationName after failure ($attempt/$attempts): ${throwable.message}"
                )
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
                // 1. Open and decode the bitmap from the content URI
                val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return@withContext null
                val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                // 2. Write to internal storage as JPEG (lossless enough, small file)
                val destFile = File(context.filesDir, "artist_art_${artistId}.jpg")
                FileOutputStream(destFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                bitmap.recycle()

                val internalPath = destFile.absolutePath

                // 3. Persist to DB
                musicDao.updateArtistCustomImage(artistId, internalPath)

                // 4. Bust the memory cache so next call picks up the new image
                val normalizedName = withContext(Dispatchers.IO) {
                    // We can't easily reverse artistId → name here, so just evict via ID prefix if cached
                    // The ViewModel will reload effectively from getEffectiveArtistImageUrl
                    null
                }

                Log.d(TAG, "Custom artist image saved: $internalPath")
                internalPath
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save custom artist image for id=$artistId: ${e.message}")
                null
            }
        }
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
                    Log.d(TAG, "Deleted custom artist image file: ${destFile.absolutePath}")
                }
                // Clear from DB
                musicDao.updateArtistCustomImage(artistId, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear custom artist image for id=$artistId: ${e.message}")
            }
        }
    }

    private fun upgradeToHighResDeezerUrl(url: String): String {
        if (!url.contains("dzcdn.net/images/artist")) return url
        return deezerSizeRegex.replace(url, "/1000x1000$1")
    }
}
