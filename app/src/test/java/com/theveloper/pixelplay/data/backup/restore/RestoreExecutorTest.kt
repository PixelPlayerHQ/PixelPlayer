package com.theveloper.pixelplay.data.backup.restore

import android.net.Uri
import com.theveloper.pixelplay.data.backup.format.BackupReader
import com.theveloper.pixelplay.data.backup.model.BackupManifest
import com.theveloper.pixelplay.data.backup.model.BackupModuleInfo
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupValidationResult
import com.theveloper.pixelplay.data.backup.model.DeviceInfo
import com.theveloper.pixelplay.data.backup.model.ModuleRestoreDetail
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.backup.model.RestoreResult
import com.theveloper.pixelplay.data.backup.module.BackupModuleHandler
import com.theveloper.pixelplay.data.backup.validation.ValidationPipeline
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RestoreExecutorTest {

    private val backupReader: BackupReader = mockk()
    private val validationPipeline: ValidationPipeline = mockk()
    private val favoritesHandler: BackupModuleHandler = mockk(relaxed = true)
    private val playbackHistoryHandler: BackupModuleHandler = mockk(relaxed = true)

    private val executor = RestoreExecutor(
        backupReader = backupReader,
        validationPipeline = validationPipeline,
        handlers = mapOf(
            BackupSection.FAVORITES to favoritesHandler,
            BackupSection.PLAYBACK_HISTORY to playbackHistoryHandler
        )
    )

    private val backupUri: Uri = mockk(relaxed = true)

    @Test
    fun `execute rolls back the module that fails during restore`() = runTest {
        val plan = restorePlan(
            selectedModules = setOf(BackupSection.FAVORITES, BackupSection.PLAYBACK_HISTORY)
        )

        coEvery { favoritesHandler.snapshot() } returns "favorites-snapshot"
        coEvery { playbackHistoryHandler.snapshot() } returns "history-snapshot"
        coEvery { backupReader.readModulePayload(backupUri, BackupSection.FAVORITES.key) } returns
            Result.success("favorites-payload")
        coEvery { backupReader.readModulePayload(backupUri, BackupSection.PLAYBACK_HISTORY.key) } returns
            Result.success("history-payload")
        every {
            validationPipeline.validateModulePayload(any(), any(), any())
        } returns BackupValidationResult.Valid
        coEvery { favoritesHandler.restore("favorites-payload") } returns Unit
        coEvery { playbackHistoryHandler.restore("history-payload") } throws IllegalStateException("boom")
        coEvery { favoritesHandler.rollback("favorites-snapshot") } returns Unit
        coEvery { playbackHistoryHandler.rollback("history-snapshot") } returns Unit

        val result = executor.execute(backupUri, plan) { }

        val failure = assertInstanceOf(RestoreResult.TotalFailure::class.java, result)
        assertTrue(failure.error.contains("Playback History"))
        coVerify(exactly = 1) { favoritesHandler.rollback("favorites-snapshot") }
        coVerify(exactly = 1) { playbackHistoryHandler.rollback("history-snapshot") }
    }

    @Test
    fun `execute fails when a selected module payload is missing`() = runTest {
        val plan = restorePlan(selectedModules = setOf(BackupSection.FAVORITES))

        coEvery { favoritesHandler.snapshot() } returns "favorites-snapshot"
        coEvery { backupReader.readModulePayload(backupUri, BackupSection.FAVORITES.key) } returns
            Result.failure(IllegalArgumentException("Module 'favorites' not found in backup"))

        val result = executor.execute(backupUri, plan) { }

        val failure = assertInstanceOf(RestoreResult.TotalFailure::class.java, result)
        assertEquals(
            "Restore failed at Favorites: Module 'favorites' not found in backup. All applied changes were rolled back.",
            failure.error
        )
        coVerify(exactly = 0) { favoritesHandler.restore(any()) }
    }

    @Test
    fun `execute fails before loading oversized module payload`() = runTest {
        val plan = restorePlan(
            selectedModules = setOf(BackupSection.PLAYBACK_HISTORY),
            moduleSizeBytes = BackupReader.MAX_MODULE_PAYLOAD_BYTES + 1L
        )

        coEvery { playbackHistoryHandler.snapshot() } returns "history-snapshot"

        val result = executor.execute(backupUri, plan) { }

        val failure = assertInstanceOf(RestoreResult.TotalFailure::class.java, result)
        assertTrue(failure.error.contains("restore safety limit"))
        coVerify(exactly = 0) { backupReader.readModulePayload(any(), any()) }
        coVerify(exactly = 0) { playbackHistoryHandler.restore(any()) }
    }

    private fun restorePlan(
        selectedModules: Set<BackupSection>,
        moduleSizeBytes: Long = 32
    ): RestorePlan {
        val modules = selectedModules.associate { section ->
            section.key to BackupModuleInfo(
                checksum = "sha256:test",
                entryCount = 1,
                sizeBytes = moduleSizeBytes
            )
        }
        return RestorePlan(
            manifest = BackupManifest(
                schemaVersion = BackupManifest.CURRENT_SCHEMA_VERSION,
                appVersion = "test",
                appVersionCode = 1,
                createdAt = 1_700_000_000_000,
                deviceInfo = DeviceInfo(),
                modules = modules
            ),
            backupUri = backupUri.toString(),
            availableModules = selectedModules,
            selectedModules = selectedModules,
            moduleDetails = selectedModules.associateWith {
                ModuleRestoreDetail(
                    entryCount = 1,
                    sizeBytes = moduleSizeBytes,
                    willOverwrite = true
                )
            }
        )
    }
}
