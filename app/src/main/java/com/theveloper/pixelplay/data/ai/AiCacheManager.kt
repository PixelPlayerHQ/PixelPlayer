package com.theveloper.pixelplay.data.ai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI response caching manager for faster repeated queries.
 * Caches AI responses based on prompt hash.
 */
@Singleton
class AiCacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "ai_cache_settings"
        private const val KEY_CACHING_ENABLED = "ai_caching_enabled"
        private const val KEY_DEBUG_MODE = "debug_mode_enabled"
        private const val CACHE_DIR = "ai_cache"
        private const val MAX_CACHE_SIZE_MB = 50
        private const val CACHE_EXPIRY_DAYS = 7
    }

    private val settingsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val cacheDir: File
        get() = File(context.filesDir, CACHE_DIR).also { it.mkdirs() }

    /**
     * Gets a cached response for a given prompt hash.
     */
    suspend fun getCachedResponse(promptHash: String): String? {
        if (!isCachingEnabled()) return null

        val cacheFile = getCacheFile(promptHash)
        if (!cacheFile.exists()) return null

        // Check if cache is expired
        val ageDays = (System.currentTimeMillis() - cacheFile.lastModified()) / (1000 * 60 * 60 * 24)
        if (ageDays > CACHE_EXPIRY_DAYS) {
            cacheFile.delete()
            Timber.tag("AiCache").d("Cache expired for: $promptHash")
            return null
        }

        return try {
            cacheFile.readText()
        } catch (e: Exception) {
            Timber.tag("AiCache").e(e, "Failed to read cache for: $promptHash")
            null
        }
    }

    /**
     * Saves a response to cache.
     */
    suspend fun cacheResponse(promptHash: String, response: String) {
        if (!isCachingEnabled()) return

        try {
            val cacheFile = getCacheFile(promptHash)
            cacheFile.writeText(response)
            Timber.tag("AiCache").d("Cached response for: $promptHash")

            // Clean up if needed
            cleanupOldCache()
        } catch (e: Exception) {
            Timber.tag("AiCache").e(e, "Failed to cache response for: $promptHash")
        }
    }

    /**
     * Invalidates cache for specific prompts.
     */
    fun invalidateCache(promptHash: String) {
        getCacheFile(promptHash).delete()
    }

    /**
     * Clears all cached responses.
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Gets current cache size in MB.
     */
    fun getCacheSizeMb(): Double {
        val size = cacheDir.listFiles()?.sumOf { it.length() } ?: 0
        return size.toDouble() / (1024 * 1024)
    }

    /**
     * Generates a hash for a prompt to use as cache key.
     */
    fun generatePromptHash(prompt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(prompt.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Generates a hash including context variables.
     */
    fun generateContextAwareHash(
        prompt: String,
        provider: String,
        model: String,
        temperature: Float
    ): String {
        val combined = "$prompt|$provider|$model|$temperature"
        return generatePromptHash(combined)
    }

    /**
     * Enables or disables AI caching.
     */
    fun setCachingEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_CACHING_ENABLED, enabled).apply()
        Timber.tag("AiCache").d("Caching enabled: $enabled")
    }

    /**
     * Gets debug mode state from local SharedPreferences.
     * This is used as a proxy for AI features enabled status.
     */
    private suspend fun isDebugModeEnabled(): Boolean {
        return try {
            // Use local SharedPreferences for debug mode
            // This acts as a proxy for AI features being enabled
            settingsPrefs.getBoolean(KEY_DEBUG_MODE, true) // Default to true to enable caching
        } catch (e: Exception) {
            Timber.tag("AiCache").e(e, "Failed to check debug mode")
            false
        }
    }

    private suspend fun isCachingEnabled(): Boolean {
        return try {
            // First check if caching is explicitly enabled in local settings
            val cachingEnabled = settingsPrefs.getBoolean(KEY_CACHING_ENABLED, true)
            if (!cachingEnabled) return false
            
            // Then check debug mode (or use as proxy for AI features)
            isDebugModeEnabled()
        } catch (e: Exception) {
            Timber.tag("AiCache").e(e, "Failed to check if caching enabled")
            false
        }
    }

    private fun getCacheFile(promptHash: String): File {
        return File(cacheDir, "cache_$promptHash.txt")
    }

    private fun cleanupOldCache() {
        val currentSizeMb = getCacheSizeMb()
        if (currentSizeMb > MAX_CACHE_SIZE_MB) {
            // Delete oldest files until under limit
            cacheDir.listFiles()
                ?.sortedBy { it.lastModified() }
                ?.forEach { file ->
                    if (getCacheSizeMb() <= MAX_CACHE_SIZE_MB * 0.8) return
                    file.delete()
                }
        }
    }

    /**
     * Gets cache statistics.
     */
    fun getCacheStats(): CacheStats {
        val files = cacheDir.listFiles() ?: emptyArray()
        val totalSize = files.sumOf { it.length() }
        val oldestTimestamp = files.minOfOrNull { it.lastModified() } ?: System.currentTimeMillis()
        val newestTimestamp = files.maxOfOrNull { it.lastModified() } ?: System.currentTimeMillis()

        return CacheStats(
            entryCount = files.size,
            totalSizeBytes = totalSize,
            oldestEntryAgeDays = ((System.currentTimeMillis() - oldestTimestamp) / (1000 * 60 * 60 * 24)).toInt(),
            newestEntryAgeDays = ((System.currentTimeMillis() - newestTimestamp) / (1000 * 60 * 60 * 24)).toInt()
        )
    }

    data class CacheStats(
        val entryCount: Int,
        val totalSizeBytes: Long,
        val oldestEntryAgeDays: Int,
        val newestEntryAgeDays: Int
    )
}