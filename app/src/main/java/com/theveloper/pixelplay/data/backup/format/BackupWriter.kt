package com.theveloper.pixelplay.data.backup.format

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupModuleInfo
import timber.log.Timber
import com.theveloper.pixelplay.di.BackupGson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    @BackupGson private val gson: Gson
) {
    suspend fun write(
        uri: Uri,
        manifest: BackupManifest,
        modulePayloads: Map<String, String>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val totalSteps = modulePayloads.size + 1
            var currentStep = 0

            // Compute checksums and module info
            val modulesInfo = mutableMapOf<String, BackupModuleInfo>()
            val payloadBytes = mutableMapOf<String, ByteArray>()

            modulePayloads.forEach { (key, jsonPayload) ->
                val bytes = jsonPayload.toByteArray(Charsets.UTF_8)
                payloadBytes[key] = bytes
                modulesInfo[key] = BackupModuleInfo(
                    checksum = "sha256:${sha256(bytes)}",
                    entryCount = countJsonArrayEntries(jsonPayload),
                    sizeBytes = bytes.size.toLong()
                )
            }

            val finalManifest = manifest.copy(modules = modulesInfo)
            val manifestJson = gson.toJson(finalManifest)

            context.contentResolver.openOutputStream(uri)?.use { rawOutput ->
                // Write PXPL magic bytes first
                rawOutput.write(BackupFormatDetector.PXPL_MAGIC)

                // Write ZIP archive
                ZipOutputStream(rawOutput).use { zip ->
                    // Write manifest
                    zip.putNextEntry(ZipEntry(BackupManifest.MANIFEST_FILENAME))
                    zip.write(manifestJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    onProgress(++currentStep, totalSteps)

                    // Write each module payload
                    payloadBytes.forEach { (key, bytes) ->
                        zip.putNextEntry(ZipEntry("$key.json"))
                        zip.write(bytes)
                        zip.closeEntry()
                        onProgress(++currentStep, totalSteps)
                    }
                }
            } ?: throw IllegalStateException("Unable to open output stream for backup")
        }
    }

    private fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private fun countJsonArrayEntries(json: String): Int {
        // Quick heuristic: count top-level array elements
        return try {
            val trimmed = json.trim()
            if (trimmed.startsWith("[")) {
                gson.fromJson(trimmed, com.google.gson.JsonArray::class.java)?.size() ?: 0
            } else {
                1
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to count items in backup module JSON")
            0
        }
    }
}
