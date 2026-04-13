package com.theveloper.pixelplay.utils

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
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

    /**
     * Returns the MediaStore content URI for a given audio song ID.
     * Returns null for cloud songs (negative IDs).
     */
    fun getMediaStoreUri(songId: Long): Uri? {
        if (songId <= 0) return null
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
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
        if (uris.isEmpty()) return null
        return MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender
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
