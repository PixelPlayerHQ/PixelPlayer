package com.theveloper.pixelplay.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.fetch.DrawableResult
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.theveloper.pixelplay.data.cloud.TelegramRepository
import okio.Path.Companion.toPath
import org.drinkless.tdlib.TdApi
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Custom Coil Fetcher for Telegram album art.
 * Handles URIs in format: telegram_art://chatId/messageId
 * 
 * Priority chain (Nekogram-style):
 * 1. Embedded art from downloaded audio file (MediaMetadataRetriever)
 * 2. TDLib albumCoverThumbnail (from message)
 * 3. TDLib externalAlbumCovers (Spotify/Apple Music metadata)
 * 
 * Log levels:
 * - VERBOSE: Per-request fetch attempts (very frequent, disabled by default)
 * - DEBUG: Success paths, significant state changes
 * - WARN/ERROR: Failures that need attention (logged sparingly to avoid spam)
 */
class TelegramCoilFetcher(
    private val context: Context,
    private val uri: Uri,
    private val telegramRepository: TelegramRepository,
    private val cacheDir: File,
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager?
) : Fetcher {

    companion object {
        // Track recent failures to avoid spamming logs
        private val recentlyLoggedFailures = ConcurrentHashMap<String, Long>()
        private const val LOG_FAILURE_COOLDOWN_MS = 60_000L // Only log same failure once per minute
        
        // Locking mechanism to prevent redundant extractions
        private val extractionMapMutex = Mutex()
        private val extractionLocks = ConcurrentHashMap<String, Mutex>()

        private fun shouldLogFailure(key: String): Boolean {
            val now = System.currentTimeMillis()
            val lastLogged = recentlyLoggedFailures[key]
            return if (lastLogged == null || now - lastLogged > LOG_FAILURE_COOLDOWN_MS) {
                recentlyLoggedFailures[key] = now
                // Cleanup old entries periodically
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
        // Use VERBOSE for per-request logging (can be filtered out in production)
        Timber.v("TelegramCoilFetcher: Fetching $uri")

        // Parse URI: telegram_art://chatId/messageId
        val chatId = uri.host?.toLongOrNull()
        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()

        if (chatId == null || messageId == null) {
            Timber.e("TelegramCoilFetcher: Invalid URI format: $uri")
            return null
        }

        // Check for quality parameter
        val quality = uri.getQueryParameter("quality")
        val isThumbnailRequest = quality == "thumb"

        // Priority 1: Try embedded art from FULLY DOWNLOADED audio files only
        // This avoids IO contention during streaming while still getting high-quality art for buffered files
        val embeddedArtPath = tryExtractEmbeddedArtIfSafe(chatId, messageId)
        if (embeddedArtPath != null) {
            Timber.v("TelegramCoilFetcher: Using embedded art for $uri")
            return SourceResult(
                source = coil.decode.ImageSource(
                    file = embeddedArtPath.toPath(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }
        
        // Fetch message to get thumbnail info and minithumbnail
        val message = telegramRepository.getMessage(chatId, messageId) ?: return null

        // Priority 2: Minithumbnail Fallback (Low Res - embedded in message)
        // For thumbnail requests, we check this early to avoid network calls if possible
        val minithumbnailData = extractMinithumbnail(message.content)
        if (minithumbnailData != null && isThumbnailRequest) {
             Timber.v("TelegramCoilFetcher: Using minithumbnail for fast preview $uri")
             val bitmap = BitmapFactory.decodeByteArray(minithumbnailData, 0, minithumbnailData.size)
             if (bitmap != null) {
                 return DrawableResult(
                     drawable = BitmapDrawable(context.resources, bitmap),
                     isSampled = true, // It's a low-res sample
                     dataSource = DataSource.MEMORY
                 )
             }
        }
        
        // Priority 3: TDLib Thumbnail Download (High Res / Cached)
        // Check if we have the file cached/downloaded already.
        // If it's a thumbnail request, we ONLY use it if it's already on disk.
        val fileId = extractFileIdFromContent(message.content)
        if (fileId != null) {
            val file = telegramRepository.getFile(fileId)
            if (file?.local?.isDownloadingCompleted == true && !file.local.path.isNullOrEmpty()) {
                Timber.v("TelegramCoilFetcher: Using cached thumbnail file for $uri")
                return SourceResult(
                    source = coil.decode.ImageSource(
                        file = file.local.path.toPath(),
                        fileSystem = okio.FileSystem.SYSTEM
                    ),
                    mimeType = null,
                    dataSource = DataSource.DISK
                )
            }
        }

        // If it's a thumbnail request (placeholder) and we don't have:
        // 1. Embedded art
        // 2. Minithumbnail (byte array)
        // 3. Cached thumbnail file
        // Then we give up. We don't want to block or download network data for a placeholder.
        if (isThumbnailRequest) {
            return null
        }
        
        var downloadPath: String? = null
        val isRecentlyFailed = telegramCacheManager?.isArtFailed(chatId, messageId) == true

        // Priority 4: Download if not cached (High Res)
        // Skip if recently failed to avoid network hammer
        if (!isRecentlyFailed && fileId != null) {
             Timber.v("TelegramCoilFetcher: Attempting download fileId: $fileId for $uri")
             downloadPath = downloadWithRetry(fileId, chatId, messageId)
        }
        // Removed verbose "skipping" log - the cache hit is self-explanatory

        if (downloadPath != null) {
            Timber.v("TelegramCoilFetcher: Downloaded thumbnail for $uri")
            return SourceResult(
                source = coil.decode.ImageSource(
                    file = downloadPath.toPath(),
                    fileSystem = okio.FileSystem.SYSTEM
                ),
                mimeType = null,
                dataSource = DataSource.DISK
            )
        }
        
        // Priority 4: Minithumbnail Fallback (Last resort for high-res request)
        if (minithumbnailData != null) {
             Timber.v("TelegramCoilFetcher: Using minithumbnail for $uri")
             val bitmap = BitmapFactory.decodeByteArray(minithumbnailData, 0, minithumbnailData.size)
             if (bitmap != null) {
                 return DrawableResult(
                     drawable = BitmapDrawable(context.resources, bitmap),
                     isSampled = true, // It's a low-res sample
                     dataSource = DataSource.MEMORY
                 )
             }
        }
        
        // Only log "no art found" once per URI per minute to avoid spam
        if (shouldLogFailure("no_art_$uri")) {
            Timber.w("TelegramCoilFetcher: No art available for $uri")
        }
        return null
    }

    private fun extractMinithumbnail(content: TdApi.MessageContent): ByteArray? {
        return when (content) {
            is TdApi.MessageAudio -> content.audio.albumCoverMinithumbnail?.data
            is TdApi.MessageDocument -> content.document.minithumbnail?.data
            else -> null
        }
    }

    /**
     * Tries to extract embedded album art from the downloaded audio file.
     * SAFETY: Only extracts if file is fully downloaded to avoid IO contention during streaming.
     * Returns the path to the cached art file if successful, null otherwise.
     */
    private suspend fun tryExtractEmbeddedArtIfSafe(chatId: Long, messageId: Long): String? {
        val key = "${chatId}_${messageId}"
        val cachedArtFile = File(cacheDir, "telegram_embedded_art_${key}.jpg")
        val noArtMarker = File(cacheDir, "telegram_embedded_art_${key}_none")

        // 1. Fast Check (No Lock): If already cached, return immediately
        if (cachedArtFile.exists() && cachedArtFile.length() > 0) return cachedArtFile.absolutePath
        if (noArtMarker.exists()) return noArtMarker.lastModified().let { 
             // Optional: Cache negative result for a while? For now, trust the marker.
             null 
        }

        // 2. Acquire Lock for this specific art
        val lock = extractionMapMutex.withLock {
            extractionLocks.getOrPut(key) { Mutex() }
        }

        return lock.withLock {
            // 3. Double-Check inside lock (in case another thread finished it while we waited)
            if (cachedArtFile.exists() && cachedArtFile.length() > 0) {
                return cachedArtFile.absolutePath
            }
            if (noArtMarker.exists()) {
                return null
            }
            
            // 4. Proceed with Extraction
            // Get the message to find the audio file ID
            val message = telegramRepository.getMessage(chatId, messageId) ?: return null
            val audioFileId = extractAudioFileId(message.content) ?: return null

            // Check if the audio file is already downloaded
            val audioFile = telegramRepository.getFile(audioFileId)
            if (audioFile?.local?.isDownloadingCompleted != true || audioFile.local.path.isNullOrEmpty()) {
                // Audio not downloaded yet - don't wait
                return null
            }

            val audioFilePath = audioFile.local.path
            Timber.v("TelegramCoilFetcher: Extracting embedded art from: $audioFilePath")

            // Extract embedded art using MediaMetadataRetriever
            val extractedPath = extractAndCacheEmbeddedArt(audioFilePath, cachedArtFile, noArtMarker)
            
            // Notify that embedded art was extracted (for UI color refresh)
            if (extractedPath != null) {
                telegramCacheManager?.notifyEmbeddedArtExtracted(chatId, messageId)
                Timber.d("TelegramCoilFetcher: Cached embedded art for $chatId/$messageId")
            }
            
            extractedPath
        }
    }

    /**
     * Extracts the audio file ID from message content.
     */
    private fun extractAudioFileId(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> content.audio.audio.id
            is TdApi.MessageDocument -> content.document.document.id
            else -> null
        }
    }

    /**
     * Extracts embedded art from an audio file and caches it.
     * Creates a marker file if no art is found to avoid repeated extraction attempts.
     */
    private fun extractAndCacheEmbeddedArt(
        audioFilePath: String,
        cacheFile: File,
        noArtMarker: File
    ): String? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(audioFilePath)
            val embeddedPicture = retriever.embeddedPicture

            if (embeddedPicture != null && embeddedPicture.isNotEmpty()) {
                // Validate that it's actually an image
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(embeddedPicture, 0, embeddedPicture.size, options)
                
                if (options.outWidth > 0 && options.outHeight > 0) {
                    // Valid image - save to cache
                    FileOutputStream(cacheFile).use { fos ->
                        fos.write(embeddedPicture)
                    }
                    Timber.v("TelegramCoilFetcher: Extracted embedded art (${options.outWidth}x${options.outHeight})")
                    cacheFile.absolutePath
                } else {
                    noArtMarker.createNewFile()
                    null
                }
            } else {
                noArtMarker.createNewFile()
                null
            }
        } catch (e: Exception) {
            // Only log extraction failures once per file
            if (shouldLogFailure("extract_$audioFilePath")) {
                Timber.w("TelegramCoilFetcher: Failed to extract embedded art from $audioFilePath: ${e.message}")
            }
            noArtMarker.createNewFile()
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }

    /**
     * Extracts the thumbnail file ID from a message.
     * Supports MessageAudio and MessageDocument content types.
     */
    private suspend fun extractThumbnailFileId(chatId: Long, messageId: Long): Int? {
        val message = telegramRepository.getMessage(chatId, messageId) ?: return null
        return extractFileIdFromContent(message.content)
    }

    /**
     * Extracts the file ID from message content (audio thumbnail or document thumbnail).
     * Priority: albumCoverThumbnail > externalAlbumCovers > document thumbnail
     */
    private fun extractFileIdFromContent(content: TdApi.MessageContent?): Int? {
        return when (content) {
            is TdApi.MessageAudio -> {
                val audio = content.audio
                // Priority 1: Main album cover thumbnail (from TDLib)
                // Priority 2: External album covers (from Spotify/Apple Music metadata)
                val thumbnail = audio.albumCoverThumbnail
                    ?: audio.externalAlbumCovers?.maxByOrNull { it.width * it.height }
                thumbnail?.file?.id
            }
            is TdApi.MessageDocument -> {
                content.document.thumbnail?.file?.id
            }
            else -> null
        }
    }

    /**
     * Downloads a file with automatic retry using refreshed message data.
     * If the first download fails (stale reference), it refreshes the message
     * from the server and retries with the new file ID.
     */
    private suspend fun downloadWithRetry(
        initialFileId: Int,
        chatId: Long,
        messageId: Long
    ): String? {
        // Attempt 1: Download with initial file ID
        try {
            // Priority 1 (Min) to avoid starving audio streaming (Priority 32)
            val path = telegramRepository.downloadFileAwait(initialFileId, 1)
            if (path != null) return path
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Don't log cancellations - they're normal during fast scrolling
            throw e
        } catch (e: Exception) {
            // Only log first attempt failures with sampling
            Timber.v("TelegramCoilFetcher: First download attempt failed for fileId: $initialFileId")
        }

        // Attempt 2: Refresh message and retry with fresh file ID
        val refreshedMessage = telegramRepository.refreshMessage(chatId, messageId)
        val refreshedFileId = extractFileIdFromContent(refreshedMessage?.content)

        if (refreshedFileId == null) {
            // Log refresh failures with sampling to avoid spam
            if (shouldLogFailure("refresh_$chatId/$messageId")) {
                Timber.w("TelegramCoilFetcher: Refresh failed - no thumbnail in message $messageId")
            }
            telegramCacheManager?.markArtFailed(chatId, messageId)
            return null
        }

        // Retry with refreshed file ID
        Timber.v("TelegramCoilFetcher: Retrying with ${if (refreshedFileId == initialFileId) "same" else "new"} fileId")

        return try {
            val result = telegramRepository.downloadFileAwait(refreshedFileId, 1)
            if (result == null) {
                telegramCacheManager?.markArtFailed(chatId, messageId)
            }
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Log retry failures with sampling
            if (shouldLogFailure("retry_$refreshedFileId")) {
                Timber.w("TelegramCoilFetcher: Retry download failed for fileId: $refreshedFileId")
            }
            telegramCacheManager?.markArtFailed(chatId, messageId)
            null
        }
    }

    /**
     * Factory for creating TelegramCoilFetcher instances.
     * Registered with Coil's ImageLoader to handle telegram_art:// URIs.
     */
    class Factory @Inject constructor(
        private val telegramRepository: TelegramRepository,
        private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager
    ) : Fetcher.Factory<Uri> {
        
        private var cacheDir: File? = null
        
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.scheme == "telegram_art") {
                val cache = cacheDir ?: options.context.cacheDir.also { cacheDir = it }
                TelegramCoilFetcher(options.context, data, telegramRepository, cache, telegramCacheManager)
            } else {
                null
            }
        }
    }
}
