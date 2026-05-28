package com.theveloper.pixelplay.utils

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.BaseColumns
import android.provider.MediaStore
import androidx.annotation.RequiresApi

/**
 * Helper for requesting MediaStore write/delete permissions on Android 11+
 * without needing MANAGE_EXTERNAL_STORAGE.
 *
 * After the user approves the system dialog, both ContentResolver-based and
 * raw file-path operations are allowed (thanks to the FUSE virtual filesystem).
 */
object MediaStorePermissionHelper {
    private const val MEDIASTORE_AUTHORITY = "media"

    data class DeleteRequest(
        val intentSender: IntentSender,
        val acceptedUris: List<Uri>,
        val rejectedUris: List<Uri>
    )

    /**
     * Returns the MediaStore content URI for a given audio song ID.
     * Returns null for cloud songs (negative IDs).
     */
    fun getMediaStoreUri(songId: Long): Uri? {
        if (songId <= 0) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL, songId)
        } else {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
        }
    }

    fun getAudioMediaStoreUris(context: Context, songId: Long): List<Uri> {
        if (songId <= 0) return emptyList()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return listOf(ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId))
        }

        val specificVolumes = runCatching {
            MediaStore.getExternalVolumeNames(context)
        }.getOrDefault(emptySet())
            .filterNot { it == MediaStore.VOLUME_EXTERNAL }
            .sortedWith(compareBy<String> { it != MediaStore.VOLUME_EXTERNAL_PRIMARY }.thenBy { it })

        return buildList {
            specificVolumes.forEach { volumeName ->
                add(MediaStore.Audio.Media.getContentUri(volumeName, songId))
            }
            add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY, songId))
            add(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL, songId))
        }.distinctBy { it.toString() }
    }

    fun isMediaStoreItemUriString(contentUriString: String): Boolean {
        val normalized = contentUriString.trim().lowercase()
        if (!normalized.startsWith("content://$MEDIASTORE_AUTHORITY/")) return false
        return normalized.substringAfterLast('/').toLongOrNull()?.let { it > 0 } == true
    }

    fun canUseSongIdForMediaStoreRequest(contentUriString: String): Boolean {
        val normalized = contentUriString.trim().lowercase()
        return normalized.isBlank() ||
            normalized.startsWith("/") ||
            normalized.startsWith("file://") ||
            isMediaStoreItemUriString(normalized)
    }

    fun resolveDeleteRequestUri(
        context: Context,
        songId: Long?,
        contentUriString: String,
        filePath: String
    ): Uri? {
        val parsedMediaStoreUri = parseMediaStoreItemUri(contentUriString)
        val parsedSongId = parsedMediaStoreUri?.lastPathSegment?.toLongOrNull()
        val candidates = buildList {
            if (songId != null && canUseSongIdForMediaStoreRequest(contentUriString)) {
                addAll(getAudioMediaStoreUris(context, songId))
            }
            if (parsedSongId != null) {
                addAll(getAudioMediaStoreUris(context, parsedSongId))
            }
            parsedMediaStoreUri?.let(::add)
            if (filePath.isNotBlank()) {
                getMediaStoreUri(context, filePath)?.let(::add)
            }
        }.distinctBy { it.toString() }

        return candidates.firstOrNull { uri ->
            runCatching {
                context.contentResolver.query(uri, arrayOf(BaseColumns._ID), null, null, null)?.use { cursor ->
                    cursor.moveToFirst()
                } == true
            }.getOrDefault(false)
        }
    }

    private fun parseMediaStoreItemUri(contentUriString: String): Uri? {
        if (!isMediaStoreItemUriString(contentUriString)) return null
        return runCatching { Uri.parse(contentUriString.trim()) }.getOrNull()
    }

    /**
     * Returns the MediaStore content URI for a given file path.
     * Useful for non-audio files like .lrc that are indexed by MediaStore.
     */
    fun getMediaStoreUri(context: Context, filePath: String): Uri? {
        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
        val selectionArgs = arrayOf(filePath)
        val queryUri = MediaStore.Files.getContentUri("external")

        return context.contentResolver.query(queryUri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                ContentUris.withAppendedId(queryUri, id)
            } else {
                null
            }
        }
    }

    /**
     * Creates an IntentSender that, when launched, asks the user to grant
     * write access to the given MediaStore URIs.
     *
     * Returns null on Android < 11 or if [uris] is empty.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createWriteRequestIntentSender(
        context: Context,
        uris: Collection<Uri>
    ): IntentSender? {
        if (uris.isEmpty()) return null
        return MediaStore.createWriteRequest(context.contentResolver, uris).intentSender
    }

    /**
     * Creates an IntentSender that, when launched, asks the user to confirm
     * deletion of the given MediaStore URIs.
     *
     * Returns null on Android < 11 or if [uris] is empty.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteRequestIntentSender(
        context: Context,
        uris: Collection<Uri>
    ): IntentSender? {
        return createDeleteRequest(context, uris)?.intentSender
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteRequest(
        context: Context,
        uris: Collection<Uri>
    ): DeleteRequest? {
        val distinctUris = uris.distinctBy { it.toString() }
        if (distinctUris.isEmpty()) return null

        createPlatformDeleteRequest(context, distinctUris)?.let { intentSender ->
            return DeleteRequest(
                intentSender = intentSender,
                acceptedUris = distinctUris,
                rejectedUris = emptyList()
            )
        }

        val acceptedUris = distinctUris.filter { uri ->
            createPlatformDeleteRequest(context, listOf(uri)) != null
        }
        if (acceptedUris.isEmpty()) return null

        val intentSender = createPlatformDeleteRequest(context, acceptedUris) ?: return null
        val acceptedUriStrings = acceptedUris.mapTo(mutableSetOf()) { it.toString() }
        return DeleteRequest(
            intentSender = intentSender,
            acceptedUris = acceptedUris,
            rejectedUris = distinctUris.filterNot { it.toString() in acceptedUriStrings }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun createPlatformDeleteRequest(
        context: Context,
        uris: Collection<Uri>
    ): IntentSender? {
        return try {
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Convenience: creates a write-request IntentSender for a single song ID.
     * Returns null for cloud songs or Android < 11.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createWriteRequestForSong(context: Context, songId: Long): IntentSender? {
        val uri = getMediaStoreUri(songId) ?: return null
        return createWriteRequestIntentSender(context, listOf(uri))
    }

    /**
     * Convenience: creates a delete-request IntentSender for a single song ID.
     * Returns null for cloud songs or Android < 11.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun createDeleteRequestForSong(context: Context, songId: Long): IntentSender? {
        val uri = getMediaStoreUri(songId) ?: return null
        return createDeleteRequestIntentSender(context, listOf(uri))
    }
}
