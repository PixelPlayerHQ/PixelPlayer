package com.theveloper.pixelplay.utils

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

object AlbumArtUtils {

    // P2-1: Dedicated app-level scope to replace GlobalScope.
    // SupervisorJob ensures child failures don't cancel sibling coroutines.
    // Appropriate for fire-and-forget tasks like cache cleanup that outlive any single component.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Main function to get album art - tries multiple methods
     */
    fun getAlbumArtUri(
        appContext: Context,
        path: String,
        songId: Long,
        forceRefresh: Boolean
    ): String? = getEmbeddedAlbumArtUri(appContext, path, songId, forceRefresh)?.toString()

    fun getCachedAlbumArtUri(
        appContext: Context,
        songId: Long
    ): Uri? {
        val cachedFile = albumArtCacheFile(appContext, songId)
        if (!cachedFile.exists()) return null

        cachedFile.setLastModified(System.currentTimeMillis())
        return shareableCacheUri(appContext, cachedFile)
    }

    fun hasCachedAlbumArt(
        appContext: Context,
        songId: Long
    ): Boolean {
        return albumArtCacheFile(appContext, songId).exists()
    }

    /**
     * Enhanced embedded art extraction with better error handling
     */
    fun getEmbeddedAlbumArtUri(
        appContext: Context,
        filePath: String,
        songId: Long,
        deepScan: Boolean
    ): Uri? {
        val audioFile = File(filePath)
        if (!audioFile.exists() || !audioFile.canRead()) {
            return null
        }

        val cachedFile = albumArtCacheFile(appContext, songId)
        val noArtFile = noArtMarkerFile(appContext, songId)

        if (!deepScan) {
            if (noArtFile.exists()) {
                if (cachedFile.exists()) {
                    cachedFile.delete()
                }
                return null
            }

            getCachedAlbumArtUri(appContext, songId)?.let { return it }
        } else {
            noArtFile.delete()
        }

        // Try to extract embedded art using pooled MediaMetadataRetriever.
        return MediaMetadataRetrieverPool.withRetriever { retriever ->
            try {
                retriever.setDataSource(filePath)
            } catch (e: IllegalArgumentException) {
                // FileDescriptor fallback
                try {
                    FileInputStream(filePath).use { fis ->
                        retriever.setDataSource(fis.fd)
                    }
                } catch (e2: Exception) {
                    return@withRetriever null
                }
            }

            val bytes = retriever.embeddedPicture
            if (bytes != null && bytes.isNotEmpty()) {
                saveAlbumArtToCache(appContext, bytes, songId)
            } else {
                cachedFile.delete()
                noArtFile.createNewFile()
                null
            }
        }
    }

    /**
     * Look for external album art files in the same directory
     */
    fun getExternalAlbumArtUri(filePath: String): Uri? {
        return try {
            val audioFile = File(filePath)
            val parentDir = audioFile.parent ?: return null

            // Extended list of common album art file names
            val commonNames = listOf(
                "cover.jpg", "cover.png", "cover.jpeg",
                "folder.jpg", "folder.png", "folder.jpeg",
                "album.jpg", "album.png", "album.jpeg",
                "albumart.jpg", "albumart.png", "albumart.jpeg",
                "artwork.jpg", "artwork.png", "artwork.jpeg",
                "front.jpg", "front.png", "front.jpeg",
                ".folder.jpg", ".albumart.jpg",
                "thumb.jpg", "thumbnail.jpg",
                "scan.jpg", "scanned.jpg"
            )

            // Look for files in the directory
            val dir = File(parentDir)
            if (dir.exists() && dir.isDirectory) {
                // First, check exact common names
                for (name in commonNames) {
                    val artFile = File(parentDir, name)
                    if (artFile.exists() && artFile.isFile && artFile.length() > 1024) { // At least 1KB
                        return Uri.fromFile(artFile)
                    }
                }

                // Then, check any image files that might be album art
                val imageFiles = dir.listFiles { file ->
                    file.isFile && (
                            file.name.contains("cover", ignoreCase = true) ||
                                    file.name.contains("album", ignoreCase = true) ||
                                    file.name.contains("folder", ignoreCase = true) ||
                                    file.name.contains("art", ignoreCase = true) ||
                                    file.name.contains("front", ignoreCase = true)
                            ) && (
                            file.extension.lowercase() in setOf("jpg", "jpeg", "png", "bmp", "webp")
                            )
                }

                imageFiles?.firstOrNull()?.let { Uri.fromFile(it) }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Try MediaStore as last resort
     */
    fun getMediaStoreAlbumArtUri(appContext: Context, albumId: Long): Uri? {
        if (albumId <= 0) return null

        val potentialUri = ContentUris.withAppendedId(
            "content://media/external/audio/albumart".toUri(),
            albumId
        )

        return try {
            appContext.contentResolver.openFileDescriptor(potentialUri, "r")?.use {
                potentialUri // only return if open succeeded
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Save embedded art to cache with unique naming
     */
    fun saveAlbumArtToCache(appContext: Context, bytes: ByteArray, songId: Long): Uri {
        val file = albumArtCacheFile(appContext, songId)

        file.outputStream().use { outputStream ->
            outputStream.write(bytes)
        }
        noArtMarkerFile(appContext, songId).delete()
        
        // Trigger async cache cleanup if needed
        appScope.launch {
            AlbumArtCacheManager.cleanCacheIfNeeded(appContext)
        }

        return shareableCacheUri(appContext, file)
    }

    private fun albumArtCacheFile(appContext: Context, songId: Long): File {
        return File(appContext.cacheDir, "song_art_${songId}.jpg")
    }

    private fun noArtMarkerFile(appContext: Context, songId: Long): File {
        return File(appContext.cacheDir, "song_art_${songId}_no.jpg")
    }

    private fun shareableCacheUri(appContext: Context, file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }
}
