package com.theveloper.pixelplay.data.backup.restore

import android.net.Uri
import android.util.Log
import com.theveloper.pixelplay.data.backup.format.BackupReader
import com.theveloper.pixelplay.data.backup.model.BackupOperationType
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupTransferProgressUpdate
import com.theveloper.pixelplay.data.backup.model.BackupValidationResult
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.backup.model.RestoreResult
import com.theveloper.pixelplay.data.backup.module.BackupModuleHandler
import com.theveloper.pixelplay.data.backup.validation.ValidationPipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RestoreExecutor @Inject constructor(
    private val backupReader: BackupReader,
    private val validationPipeline: ValidationPipeline,
    private val handlers: Map<BackupSection, @JvmSuppressWildcards BackupModuleHandler>
) {
    companion object {
        private const val TAG = "RestoreExecutor"
    }

    suspend fun execute(
        uri: Uri,
        plan: RestorePlan,
        onProgress: (BackupTransferProgressUpdate) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {
        val selectedModules = plan.selectedModules.toList().sortedBy { it.key }
        val totalSteps = selectedModules.size * 2 + 3
        var step = 0

        // ---- PHASE 1: SNAPSHOT ----
        reportProgress(onProgress, ++step, totalSteps, "Creating safety snapshots", "Capturing current state for rollback.")

        val snapshots = mutableMapOf<BackupSection, String>()
        try {
            selectedModules.forEach { section ->
                val handler = handlers[section]
                if (handler != null) {
                    snapshots[section] = handler.snapshot()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Snapshot phase failed", e)
            return@withContext RestoreResult.TotalFailure("Failed to capture current state: ${e.message}")
        }

        // ---- PHASE 2: READ, VALIDATE, RESTORE ----
        reportProgress(onProgress, ++step, totalSteps, "Preparing restore", "Selected modules will be processed one at a time.")

        val restoredModules = mutableListOf<BackupSection>()
        var currentSection: BackupSection? = null
        try {
            selectedModules.forEach { section ->
                currentSection = section
                val moduleInfo = plan.manifest.modules[section.key]
                if (moduleInfo != null &&
                    moduleInfo.sizeBytes > BackupReader.MAX_MODULE_PAYLOAD_BYTES
                ) {
                    throw IllegalStateException(
                        "Backup payload for ${section.label} is ${moduleInfo.sizeBytes / (1024 * 1024)}MB, " +
                            "which exceeds the ${BackupReader.MAX_MODULE_PAYLOAD_BYTES / (1024 * 1024)}MB restore safety limit."
                    )
                }

                val payload = backupReader.readModulePayload(uri, section.key).getOrThrow()

                // Validate each module payload
                val validationResult = validationPipeline.validateModulePayload(
                    section, payload, plan.manifest
                )
                if (validationResult is BackupValidationResult.Invalid && validationResult.fatalErrors.isNotEmpty()) {
                    throw IllegalStateException(
                        "Validation failed for ${section.label}: ${validationResult.fatalErrors.first().message}"
                    )
                }

                reportProgress(
                    onProgress, ++step, totalSteps,
                    "Validated ${section.label}",
                    section.description,
                    section
                )

                reportProgress(
                    onProgress, ++step, totalSteps,
                    "Restoring ${section.label}",
                    section.description,
                    section
                )

                val handler = handlers[section]
                    ?: throw IllegalStateException("No handler for module ${section.key}")
                handler.restore(payload)
                restoredModules.add(section)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed while processing backup module, rolling back", e)
            // Roll back in reverse restore order, including the module that failed mid-restore.
            var rollbackSuccess = true
            val rollbackOrder = (restoredModules + listOfNotNull(currentSection)).distinct().asReversed()
            rollbackOrder.forEach { section ->
                try {
                    val snapshot = snapshots[section]
                    if (snapshot != null) {
                        handlers[section]?.rollback(snapshot)
                    }
                } catch (rollbackError: Exception) {
                    Log.e(TAG, "Rollback failed for ${section.key}", rollbackError)
                    rollbackSuccess = false
                }
            }

            val failedReason = e.message ?: "Unknown error"
            val failedSection = currentSection

            if (rollbackSuccess) {
                val failedLabel = failedSection?.label ?: "unknown module"
                return@withContext RestoreResult.TotalFailure(
                    "Restore failed at $failedLabel: $failedReason. All applied changes were rolled back."
                )
            }

            return@withContext RestoreResult.PartialFailure(
                succeeded = restoredModules.toSet(),
                failed = failedSection?.let { mapOf(it to failedReason) }.orEmpty(),
                rolledBack = false
            )
        }

        // ---- PHASE 3: FINALIZE ----
        reportProgress(onProgress, ++step, totalSteps, "Restore complete", "All selected modules were restored successfully.")

        RestoreResult.Success
    }

    private fun reportProgress(
        onProgress: (BackupTransferProgressUpdate) -> Unit,
        step: Int,
        totalSteps: Int,
        title: String,
        detail: String,
        section: BackupSection? = null
    ) {
        onProgress(
            BackupTransferProgressUpdate(
                operation = BackupOperationType.IMPORT,
                step = step,
                totalSteps = totalSteps,
                title = title,
                detail = detail,
                section = section
            )
        )
    }
}
