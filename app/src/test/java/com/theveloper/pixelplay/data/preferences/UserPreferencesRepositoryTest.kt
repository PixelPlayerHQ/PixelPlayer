package com.theveloper.pixelplay.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

class UserPreferencesRepositoryTest {

    @Test
    fun `clearPreferencesExceptKeys preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.clearPreferencesExceptKeys(emptySet())

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals(NavBarStyle.DEFAULT, repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `importPreferencesFromBackup clearExisting preserves initial setup completion`() = runTest {
        val tempDir = Files.createTempDirectory("user-preferences-repository-test")
        try {
            val repository = UserPreferencesRepository(
                dataStore = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempDir.resolve("settings.preferences_pb").toFile() }
                ),
                json = Json
            )

            repository.setInitialSetupDone(true)
            repository.setNavBarStyle("compact")

            repository.importPreferencesFromBackup(
                entries = listOf(
                    PreferenceBackupEntry(
                        key = "nav_bar_style",
                        type = "string",
                        stringValue = "restored"
                    )
                ),
                clearExisting = true
            )

            assertTrue(repository.initialSetupDoneFlow.first())
            assertEquals("restored", repository.navBarStyleFlow.first())
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
