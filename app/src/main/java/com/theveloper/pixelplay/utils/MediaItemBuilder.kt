package com.theveloper.pixelplay.utils

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.theveloper.pixelplay.data.model.Song
import java.io.File

object MediaItemBuilder {
    private const val EXTERNAL_MEDIA_ID_PREFIX = "external:"
    private const val EXTERNAL_EXTRA_PREFIX = "com.theveloper.pixelplay.external."
    private val DIRECT_FILE_URI_MIME_TYPES = setOf(
        "audio/mp4",
        "audio/m4a",
        "audio/x-m4a",
        "audio/mp4a-latm",
        "audio/aac",
        "audio/x-aac",
        "audio/3gp",
        "audio/3gpp",
        "audio/3gpp2",
    )
    private val DIRECT_FILE_URI_EXTENSIONS = setOf(
        "m4a",
        "m4b",
        "m4p",
        "mp4",
        "aac",
        "3ga",
        "3gp",
        "3gpp",
        "alac",
    )
    private val SUPPORTED_ARTWORK_SCHEMES = setOf(
        "content",
        "file",
        "android.resource",
        "http",
        "https",
    )
    const val EXTERNAL_EXTRA_FLAG = EXTERNAL_EXTRA_PREFIX + "FLAG"
    const val EXTERNAL_EXTRA_ALBUM = EXTERNAL_EXTRA_PREFIX + "ALBUM"
    const val EXTERNAL_EXTRA_DURATION = EXTERNAL_EXTRA_PREFIX + "DURATION"
    const val EXTERNAL_EXTRA_CONTENT_URI = EXTERNAL_EXTRA_PREFIX + "CONTENT_URI"
    const val EXTERNAL_EXTRA_ALBUM_ART = EXTERNAL_EXTRA_PREFIX + "ALBUM_ART"
    const val EXTERNAL_EXTRA_GENRE = EXTERNAL_EXTRA_PREFIX + "GENRE"
    const val EXTERNAL_EXTRA_TRACK = EXTERNAL_EXTRA_PREFIX + "TRACK"
    const val EXTERNAL_EXTRA_YEAR = EXTERNAL_EXTRA_PREFIX + "YEAR"
    const val EXTERNAL_EXTRA_DATE_ADDED = EXTERNAL_EXTRA_PREFIX + "DATE_ADDED"
    const val EXTERNAL_EXTRA_MIME_TYPE = EXTERNAL_EXTRA_PREFIX + "MIME_TYPE"
    const val EXTERNAL_EXTRA_BITRATE = EXTERNAL_EXTRA_PREFIX + "BITRATE"
    const val EXTERNAL_EXTRA_SAMPLE_RATE = EXTERNAL_EXTRA_PREFIX + "SAMPLE_RATE"
    const val EXTERNAL_EXTRA_FILE_PATH = EXTERNAL_EXTRA_PREFIX + "FILE_PATH"

    fun build(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playbackUri(song))
            .setMimeType(song.mimeType)
            .setMediaMetadata(buildMediaMetadataForSong(song))
            .build()
    }

    fun buildForExternalController(context: Context, song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(playbackUri(song))
            .setMimeType(song.mimeType)
            .setMediaMetadata(
                buildMediaMetadataForSong(
                    song = song,
                    exposedArtworkUri = externalControllerArtworkUri(context, song.albumArtUriString)
                )
            )
            .build()
    }

    fun playbackUri(song: Song): Uri = playbackUri(
        contentUriString = song.contentUriString,
        filePath = song.path,
        mimeType = song.mimeType
    )

    fun playbackUri(
        contentUriString: String,
        filePath: String? = null,
        mimeType: String? = null
    ): Uri {
        directLocalFileUri(contentUriString, filePath, mimeType)?.let { return it }
        val uri = runCatching { Uri.parse(contentUriString) }.getOrNull()
            ?: return Uri.fromFile(File(contentUriString))
        // Telegram downloaded files can be stored as absolute paths (without file://).
        // Normalize them so ExoPlayer always gets a canonical local-file URI.
        return if (uri.scheme.isNullOrBlank() && contentUriString.startsWith("/")) {
            Uri.fromFile(File(contentUriString))
        } else {
            uri
        }
    }

    private fun directLocalFileUri(
        contentUriString: String,
        filePath: String?,
        mimeType: String?
    ): Uri? {
        val normalizedPath = filePath?.takeIf { it.startsWith("/") } ?: return null
        if (!shouldPreferDirectLocalFileUri(contentUriString, normalizedPath, mimeType)) {
            return null
        }

        return Uri.fromFile(File(normalizedPath))
    }

    internal fun shouldPreferDirectLocalFileUri(
        contentUriString: String,
        filePath: String?,
        mimeType: String?
    ): Boolean {
        val normalizedPath = filePath?.takeIf { it.startsWith("/") } ?: return false
        if (!LocalArtworkUri.isLikelyLocalMedia(contentUriString)) {
            return false
        }

        if (!contentUriString.startsWith("content://")) {
            return false
        }

        return shouldPreferDirectFileUri(normalizedPath, mimeType)
    }

    private fun shouldPreferDirectFileUri(
        filePath: String,
        mimeType: String?
    ): Boolean {
        val normalizedMimeType = mimeType?.lowercase()
        if (normalizedMimeType != null && normalizedMimeType in DIRECT_FILE_URI_MIME_TYPES) {
            return true
        }

        val extension = filePath.substringAfterLast('.', "").lowercase()
        return extension in DIRECT_FILE_URI_EXTENSIONS
    }

    /**
     * Artwork URIs are surfaced to external controllers (Android Auto, widgets, etc.).
     * Keep only schemes that these surfaces can usually resolve, and normalize raw paths.
     */
    fun artworkUri(rawArtworkUri: String?): Uri? {
        if (rawArtworkUri.isNullOrBlank()) {
            return null
        }

        if (rawArtworkUri.startsWith("/")) {
            return Uri.fromFile(File(rawArtworkUri))
        }

        val uri = rawArtworkUri.toUri()
        val scheme = uri.scheme?.lowercase()
        return if (scheme != null && scheme in SUPPORTED_ARTWORK_SCHEMES) {
            uri
        } else {
            null
        }
    }

    fun externalControllerArtworkUri(context: Context, rawArtworkUri: String?): Uri? {
        val normalizedUri = artworkUri(rawArtworkUri) ?: return null
        return when (normalizedUri.scheme?.lowercase()) {
            "file" -> normalizedUri.path
                ?.takeIf { it.isNotBlank() }
                ?.let(::File)
                ?.let { file -> providerBackedArtworkUri(context, file) ?: normalizedUri }
                ?: normalizedUri
            null -> null
            else -> normalizedUri
        }
    }

    private fun buildMediaMetadataForSong(
        song: Song,
        exposedArtworkUri: Uri? = artworkUri(song.albumArtUriString)
    ): MediaMetadata {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.displayArtist)
            .setAlbumTitle(song.album)

        exposedArtworkUri?.let { artworkUri ->
            metadataBuilder.setArtworkUri(artworkUri)
        }

        val extras = Bundle().apply {
            putBoolean(EXTERNAL_EXTRA_FLAG, song.id.startsWith(EXTERNAL_MEDIA_ID_PREFIX))
            putString(EXTERNAL_EXTRA_ALBUM, song.album)
            putLong(EXTERNAL_EXTRA_DURATION, song.duration)
            putString(EXTERNAL_EXTRA_CONTENT_URI, song.contentUriString)
            (exposedArtworkUri?.toString() ?: song.albumArtUriString)?.let {
                putString(EXTERNAL_EXTRA_ALBUM_ART, it)
            }
            song.genre?.let { putString(EXTERNAL_EXTRA_GENRE, it) }
            putInt(EXTERNAL_EXTRA_TRACK, song.trackNumber)
            putInt(EXTERNAL_EXTRA_YEAR, song.year)
            putLong(EXTERNAL_EXTRA_DATE_ADDED, song.dateAdded)
            putString(EXTERNAL_EXTRA_MIME_TYPE, song.mimeType)
            putInt(EXTERNAL_EXTRA_BITRATE, song.bitrate ?: 0)
            putInt(EXTERNAL_EXTRA_SAMPLE_RATE, song.sampleRate ?: 0)
            putString(EXTERNAL_EXTRA_FILE_PATH, song.path)
        }

        metadataBuilder.setExtras(extras)
        return metadataBuilder.build()
    }

    private fun providerBackedArtworkUri(context: Context, file: File): Uri? {
        val canonicalFile = runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
        if (!isInsideAppStorage(context, canonicalFile)) {
            return null
        }

        return runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.provider", canonicalFile)
        }.getOrNull()
    }

    private fun isInsideAppStorage(context: Context, file: File): Boolean {
        val storageRoots = listOf(context.cacheDir, context.filesDir)
            .mapNotNull { root -> runCatching { root.canonicalFile }.getOrNull() }

        return storageRoots.any { root ->
            file.path == root.path || file.path.startsWith(root.path + File.separator)
        }
    }
}
