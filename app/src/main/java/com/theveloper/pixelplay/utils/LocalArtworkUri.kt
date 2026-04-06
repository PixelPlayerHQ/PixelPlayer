package com.theveloper.pixelplay.utils

import android.net.Uri

object LocalArtworkUri {
    const val SCHEME = "pixelplay_local_art"
    private const val HOST_SONG = "song"

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
        return normalized.contains("song_art_") &&
            (
                normalized.startsWith("content://") ||
                    normalized.startsWith("file://") ||
                    normalized.startsWith("/") ||
                    normalized.contains(".provider/")
                )
    }

    fun isLikelyLocalMedia(contentUriString: String): Boolean {
        val normalized = contentUriString.lowercase()
        return !normalized.startsWith("telegram://") &&
            !normalized.startsWith("netease://") &&
            !normalized.startsWith("qqmusic://") &&
            !normalized.startsWith("navidrome://") &&
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

        return if (isLocalArtworkUri(normalizedStoredUri) || looksLikeVolatileArtworkUri(normalizedStoredUri)) {
            if (normalizedStoredUri.contains("?t=")) {
                normalizedStoredUri
            } else {
                buildSongUri(songId)
            }
        } else {
            normalizedStoredUri
        }
    }
}
