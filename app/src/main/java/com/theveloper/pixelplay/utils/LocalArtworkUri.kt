package com.theveloper.pixelplay.utils

import android.net.Uri

object LocalArtworkUri {
    const val SCHEME = "pixelplay_local_art"
    private const val HOST_SONG = "song"
    private const val CACHE_BUST_QUERY = "t"

    fun buildSongUri(songId: Long): String = "$SCHEME://$HOST_SONG/$songId"
    fun buildSongUriWithTimestamp(songId: Long): String = buildSongUri(songId) + "?t=${System.currentTimeMillis()}"

    fun isLocalArtworkUri(uriString: String?): Boolean {
        return uriString?.startsWith("$SCHEME://") == true
    }

    fun isLocalArtworkUri(uri: Uri?): Boolean {
        return uri?.toString()?.let(::isLocalArtworkUri) == true
    }

    fun parseSongId(uriString: String): Long? {
        if (!isLocalArtworkUri(uriString)) return null
        val prefix = "$SCHEME://$HOST_SONG/"
        return uriString.removePrefix(prefix)
            .substringBefore('?')
            .toLongOrNull()
    }

    fun looksLikeVolatileArtworkUri(uriString: String?): Boolean {
        if (uriString.isNullOrBlank()) return false
        val normalized = uriString.lowercase()
        val isLegacyCachedFileUri = normalized.contains("song_art_") &&
            (
                normalized.startsWith("content://") ||
                    normalized.startsWith("file://") ||
                    normalized.startsWith("/") ||
                    normalized.contains(".provider/")
                )
        val isSharedArtworkUri = normalized.startsWith("content://") &&
            normalized.contains(".artwork/song/")
        return isLegacyCachedFileUri || isSharedArtworkUri
    }

    fun parseSongIdFromVolatileArtworkUri(uriString: String?): Long? {
        if (uriString.isNullOrBlank()) return null
        if (!looksLikeVolatileArtworkUri(uriString)) return null

        val fileName = uriString.substringAfterLast('/').substringBefore('?')
        if (!fileName.startsWith("song_art_")) {
            return null
        }

        return fileName
            .removePrefix("song_art_")
            .substringBefore('_')
            .substringBefore('.')
            .toLongOrNull()
    }

    fun extractCacheBustToken(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val query = uriString.substringAfter('?', "")
        if (query.isBlank()) return null
        return query
            .split('&')
            .asSequence()
            .mapNotNull { entry ->
                val separatorIndex = entry.indexOf('=')
                if (separatorIndex <= 0) return@mapNotNull null
                val key = entry.substring(0, separatorIndex)
                if (key != CACHE_BUST_QUERY) return@mapNotNull null
                entry.substring(separatorIndex + 1).takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    fun isLikelyLocalMedia(contentUriString: String): Boolean {
        val normalized = contentUriString.lowercase()
        return !normalized.startsWith("telegram://") &&
            !normalized.startsWith("netease://") &&
            !normalized.startsWith("qqmusic://") &&
            !normalized.startsWith("navidrome://") &&
            !normalized.startsWith("jellyfin://") &&
            !normalized.startsWith("gdrive://")
    }

    fun resolveSongArtworkUri(
        storedUri: String?,
        songId: Long,
        contentUriString: String
    ): String? {
        val normalizedStoredUri = storedUri?.takeIf { it.isNotBlank() } ?: return null
        if (!isLikelyLocalMedia(contentUriString)) {
            return normalizedStoredUri
        }

        return when {
            isLocalArtworkUri(normalizedStoredUri) -> {
                if (normalizedStoredUri.contains("?t=")) {
                    normalizedStoredUri
                } else {
                    buildSongUri(songId)
                }
            }
            looksLikeVolatileArtworkUri(normalizedStoredUri) -> buildSongUri(songId)
            else -> normalizedStoredUri
        }
    }
}
