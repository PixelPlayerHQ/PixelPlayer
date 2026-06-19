package com.theveloper.pixelplay.utils

import android.media.MediaMetadataRetriever
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe helper for MediaMetadataRetriever usage.
 *
 * MediaMetadataRetriever does not expose a reliable reset/clear-data-source API. Reusing an
 * instance across files can leave stale native extractor state on some Android builds, which
 * makes embedded artwork/metadata reads intermittently return null for valid local files. Keep
 * the wrapper API for callers, but use a fresh retriever per operation.
 * 
 * Usage:
 * ```kotlin
 * MediaMetadataRetrieverPool.withRetriever { retriever ->
 *     retriever.setDataSource(filePath)
 *     retriever.embeddedPicture
 * }
 * ```
 */
object MediaMetadataRetrieverPool {
    private val createdCount = AtomicInteger(0)
    
    /**
     * Acquires a MediaMetadataRetriever from the pool, or creates a new one if pool is empty.
     * The caller is responsible for returning it via [release].
     */
    @PublishedApi
    internal fun acquire(): MediaMetadataRetriever {
        createdCount.incrementAndGet()
        return MediaMetadataRetriever()
    }
    
    /**
     * Returns a MediaMetadataRetriever to the pool for reuse.
     * If the pool is full, the retriever is released instead.
     */
    @PublishedApi
    internal fun release(retriever: MediaMetadataRetriever) {
        try {
            retriever.release()
        } catch (e: Exception) {
            Timber.w(e, "Failed to release MediaMetadataRetriever")
        } finally {
            createdCount.decrementAndGet()
        }
    }
    
    /**
     * Executes the given block with a pooled MediaMetadataRetriever.
     * Automatically handles acquiring and returning the retriever.
     * 
     * @param block The operation to perform with the retriever
     * @return The result of the block, or null if an error occurred
     */
    inline fun <T> withRetriever(block: (MediaMetadataRetriever) -> T): T? {
        val retriever = acquire()
        return try {
            block(retriever)
        } catch (e: Exception) {
            null
        } finally {
            release(retriever)
        }
    }
    
    /**
     * Clears all pooled retrievers. Call this when the app is low on memory.
     */
    fun clear() {
        // No-op: retrievers are released immediately after each use.
    }
    
    /**
     * Returns the current number of retrievers held in the pool.
     */
    fun poolSize(): Int = 0
    
    /**
     * Returns the total number of retrievers created (including those in use).
     */
    fun totalCreated(): Int = createdCount.get()
}
