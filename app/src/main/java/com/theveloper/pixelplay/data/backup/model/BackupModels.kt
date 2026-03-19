package com.theveloper.pixelplay.data.backup.model

enum class BackupOperationType {
    EXPORT,
    IMPORT
}

data class BackupTransferProgressUpdate(
    val operation: BackupOperationType,
    val step: Int,
    val totalSteps: Int,
    val title: String,
    val detail: String,
    val section: BackupSection? = null
) {
    val progress: Float
        get() = if (totalSteps > 0) (step.toFloat() / totalSteps).coerceIn(0f, 1f) else 0f
}

data class PlaybackHistoryBackupEntry(
    val songId: String,
    val timestamp: Long,
    val durationMs: Long,
    val startTimestamp: Long? = null,
    val endTimestamp: Long? = null
)

data class ArtistImageBackupEntry(
    val artistName: String,
    val imageUrl: String,
    val customImageBase64: String? = null
)

data class BackupHistoryEntry(
    val uri: String,
    val displayName: String,
    val createdAt: Long,
    val schemaVersion: Int,
    val modules: Set<String>,
    val sizeBytes: Long,
    val appVersion: String = ""
)

data class RestorePlan(
    val manifest: BackupManifest,
    val backupUri: String,
    val availableModules: Set<BackupSection>,
    val selectedModules: Set<BackupSection>,
    val moduleDetails: Map<BackupSection, ModuleRestoreDetail>,
    val warnings: List<String> = emptyList()
)

data class ModuleRestoreDetail(
    val entryCount: Int,
    val sizeBytes: Long,
    val willOverwrite: Boolean = true
)

sealed class BackupValidationResult {
    data object Valid : BackupValidationResult()
    data class Invalid(val errors: List<ValidationError>) : BackupValidationResult() {
        val fatalErrors: List<ValidationError>
            get() = errors.filter { it.severity == Severity.ERROR }
        val warnings: List<ValidationError>
            get() = errors.filter { it.severity == Severity.WARNING }
    }

    fun isValid(): Boolean = this is Valid || (this is Invalid && fatalErrors.isEmpty())
}

data class ValidationError(
    val code: String,
    val message: String,
    val module: String? = null,
    val severity: Severity = Severity.ERROR
)

enum class Severity { ERROR, WARNING }

sealed class RestoreResult {
    data object Success : RestoreResult()
    data class PartialFailure(
        val succeeded: Set<BackupSection>,
        val failed: Map<BackupSection, String>,
        val rolledBack: Boolean
    ) : RestoreResult()
    data class TotalFailure(val error: String) : RestoreResult()
}
