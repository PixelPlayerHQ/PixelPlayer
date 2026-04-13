package com.theveloper.pixelplay.utils
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileDeletionUtils {

    /**
     * Main method to delete a file - handles all Android versions automatically
     */
    suspend fun deleteFile(context: Context, filePath: String): Boolean {
        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    deleteFileAndroid11Plus(context, filePath)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    deleteFileAndroid10(context, filePath)
                }
                else -> {
                    deleteFileLegacy(context, filePath)
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Android 11+ (API 30+) deletion using MediaStore.
     * Without MANAGE_EXTERNAL_STORAGE, direct deletion of files not owned by the app
     * will throw SecurityException. The caller should use [getDeleteRequestIntentSender]
     * to get user confirmation first, or catch the exception and request permission.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun deleteFileAndroid11Plus(context: Context, filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext true

                // Try to get MediaStore URI for the file
                val uri = getMediaStoreUri(context, filePath)
                if (uri != null) {
                    // Use MediaStore for deletion
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    rowsDeleted > 0
                } else {
                    false
                }
            } catch (e: SecurityException) {
                // Without MANAGE_EXTERNAL_STORAGE, this is expected for files not owned by the app.
                // The caller should use MediaStore.createDeleteRequest() instead.
                false
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Creates an IntentSender for Android 11+ that shows a system dialog asking
     * the user to confirm deletion of the file. Returns null if the file is not
     * found in MediaStore or on older Android versions.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun getDeleteRequestIntentSender(context: Context, filePath: String): android.content.IntentSender? {
        val uri = getMediaStoreUri(context, filePath) ?: return null
        return MediaStore.createDeleteRequest(context.contentResolver, listOf(uri)).intentSender
    }

    /**
     * Android 10 (API 29) deletion - Scoped Storage
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun deleteFileAndroid10(context: Context, filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext true

                // For Android 10, we can still use MediaStore for media files
                val uri = getMediaStoreUri(context, filePath)
                return@withContext if (uri != null) {
                    val rowsDeleted = context.contentResolver.delete(uri, null, null)
                    rowsDeleted > 0
                } else {
                    // For non-media files in app-specific directory
                    file.delete()
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Legacy deletion for Android 9 and below
     */
    private suspend fun deleteFileLegacy(context: Context, filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext true

                file.delete()
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get MediaStore URI for a file path
     */
    private fun getMediaStoreUri(context: Context, filePath: String): Uri? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null

            val projection = arrayOf(MediaStore.Files.FileColumns._ID)
            val selection = "${MediaStore.Files.FileColumns.DATA} = ?"
            val selectionArgs = arrayOf(filePath)

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Delete multiple files at once
     */
    suspend fun deleteFiles(context: Context, filePaths: List<String>): List<Boolean> {
        return withContext(Dispatchers.IO) {
            filePaths.map { filePath ->
                deleteFile(context, filePath)
            }
        }
    }

    /**
     * Check if a file can be deleted (exists and is not a directory)
     */
    suspend fun canDeleteFile(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                file.exists() && file.isFile
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Get file information before deletion
     */
    data class FileInfo(
        val exists: Boolean,
        val isFile: Boolean,
        val size: Long,
        val canRead: Boolean,
        val canWrite: Boolean
    )

    suspend fun getFileInfo(filePath: String): FileInfo {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(filePath)
                FileInfo(
                    exists = file.exists(),
                    isFile = file.isFile,
                    size = if (file.exists()) file.length() else 0,
                    canRead = file.canRead(),
                    canWrite = file.canWrite()
                )
            } catch (e: Exception) {
                FileInfo(false, false, 0, false, false)
            }
        }
    }
}