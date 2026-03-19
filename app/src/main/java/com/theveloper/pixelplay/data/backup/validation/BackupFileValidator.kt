package com.theveloper.pixelplay.data.backup.validation

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.theveloper.pixelplay.data.backup.format.BackupReader
import com.theveloper.pixelplay.data.backup.format.BackupFormatDetector
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupValidationResult
import com.theveloper.pixelplay.data.backup.model.Severity
import com.theveloper.pixelplay.data.backup.model.ValidationError
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupFileValidator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatDetector: BackupFormatDetector
) {
    companion object {
        const val MAX_BACKUP_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB
        const val MAX_ZIP_RATIO = 100 // max decompressed/compressed ratio
        private const val MAX_TOTAL_DECOMPRESSED_BYTES = 256L * 1024 * 1024
    }

    fun validate(uri: Uri): BackupValidationResult {
        val errors = mutableListOf<ValidationError>()
        val docFile = DocumentFile.fromSingleUri(context, uri)
        val fileName = docFile?.name
        val fileSize = docFile?.length()?.takeIf { it >= 0L }

        // Check URI accessibility
        val format = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = formatDetector.readHeader(input)
                if (header.isEmpty()) {
                    errors.add(ValidationError("FILE_EMPTY", "Backup file is empty or inaccessible."))
                    return BackupValidationResult.Invalid(errors)
                }
                formatDetector.detect(header)
            }
        } catch (e: Exception) {
            errors.add(ValidationError("FILE_ACCESS", "Cannot open backup file: ${e.message}"))
            return BackupValidationResult.Invalid(errors)
        }

        if (format == null) {
            errors.add(ValidationError("FILE_EMPTY", "Backup file is empty or inaccessible."))
            return BackupValidationResult.Invalid(errors)
        }

        // Check file size
        if (fileSize != null && fileSize > MAX_BACKUP_SIZE_BYTES) {
            errors.add(ValidationError("FILE_TOO_LARGE", "Backup file exceeds the ${MAX_BACKUP_SIZE_BYTES / (1024 * 1024)}MB limit."))
            return BackupValidationResult.Invalid(errors)
        }

        // Check file name extension (if available)
        if (fileName != null && !fileName.endsWith(".pxpl", ignoreCase = true) &&
            !fileName.endsWith(".gz", ignoreCase = true)) {
            errors.add(ValidationError("FILE_EXTENSION", "File extension is not .pxpl. The file may not be a valid backup.", severity = Severity.WARNING))
        }

        if (format == BackupFormatDetector.Format.UNKNOWN) {
            errors.add(ValidationError("FORMAT_UNKNOWN", "File is not a recognized PixelPlay backup format."))
            return BackupValidationResult.Invalid(errors)
        }

        // For ZIP format: validate zip structure safety
        if (format == BackupFormatDetector.Format.PXPL_V3_ZIP) {
            validateZipSafety(uri, fileSize, BackupFormatDetector.PXPL_MAGIC_SIZE, errors)
        }

        return if (errors.any { it.severity == Severity.ERROR }) {
            BackupValidationResult.Invalid(errors)
        } else if (errors.isNotEmpty()) {
            BackupValidationResult.Invalid(errors)
        } else {
            BackupValidationResult.Valid
        }
    }

    private fun validateZipSafety(
        uri: Uri,
        fileSize: Long?,
        offset: Int,
        errors: MutableList<ValidationError>
    ) {
        try {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                skipFully(raw, offset)
                val compressedZipBytes = fileSize?.minus(offset)?.coerceAtLeast(0L)

                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    var totalDecompressed = 0L
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (entry != null) {
                        val name = entry.name

                        // Path traversal check
                        if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                            errors.add(ValidationError("ZIP_PATH_TRAVERSAL", "Suspicious zip entry path: $name"))
                            return
                        }

                        // Only allow .json files and manifest
                        if (!name.endsWith(".json")) {
                            errors.add(ValidationError("ZIP_UNEXPECTED_ENTRY", "Unexpected file in backup: $name", severity = Severity.WARNING))
                        }

                        val perEntryLimit = if (name == BackupManifest.MANIFEST_FILENAME) {
                            BackupReader.MAX_MANIFEST_BYTES.toLong()
                        } else {
                            BackupReader.MAX_MODULE_PAYLOAD_BYTES.toLong()
                        }

                        var entryBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read == -1) break

                            entryBytes += read
                            totalDecompressed += read

                            if (entryBytes > perEntryLimit) {
                                errors.add(
                                    ValidationError(
                                        "ZIP_ENTRY_TOO_LARGE",
                                        "Backup entry '$name' exceeds the ${perEntryLimit / (1024 * 1024)}MB in-memory safety limit."
                                    )
                                )
                                return
                            }

                            if (totalDecompressed > MAX_TOTAL_DECOMPRESSED_BYTES) {
                                errors.add(
                                    ValidationError(
                                        "ZIP_TOO_LARGE",
                                        "Backup file expands beyond the ${MAX_TOTAL_DECOMPRESSED_BYTES / (1024 * 1024)}MB safety limit."
                                    )
                                )
                                return
                            }

                            if (compressedZipBytes != null &&
                                compressedZipBytes > 0 &&
                                totalDecompressed > compressedZipBytes * MAX_ZIP_RATIO
                            ) {
                                errors.add(ValidationError("ZIP_BOMB", "Backup file has suspicious compression ratio."))
                                return
                            }
                        }

                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: errors.add(ValidationError("FILE_ACCESS", "Cannot open backup file."))
        } catch (e: Exception) {
            errors.add(ValidationError("ZIP_CORRUPT", "Backup ZIP archive is corrupted: ${e.message}"))
        }
    }

    private fun skipFully(input: InputStream, byteCount: Int) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = input.skip(remaining.toLong())
            if (skipped > 0) {
                remaining -= skipped.toInt()
                continue
            }

            if (input.read() == -1) {
                throw IllegalArgumentException("Backup file is truncated.")
            }
            remaining--
        }
    }
}
