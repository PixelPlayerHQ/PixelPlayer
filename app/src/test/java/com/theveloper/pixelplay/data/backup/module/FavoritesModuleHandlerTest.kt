package com.theveloper.pixelplay.data.backup.module

import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.FavoritesEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesModuleHandlerTest {

    private val favoritesDao: FavoritesDao = mockk(relaxed = true)
    private val handler = FavoritesModuleHandler(
        favoritesDao = favoritesDao,
        gson = GsonBuilder().serializeNulls().create()
    )

    @Test
    fun `export uses stable canonical field names`() = runTest {
        coEvery { favoritesDao.getAllFavoritesOnce() } returns listOf(
            FavoritesEntity(
                songId = 123L,
                isFavorite = true,
                timestamp = 1_700_000_000_000L
            )
        )

        val payload = handler.export()

        assertTrue(payload.contains("\"songId\""))
        assertTrue(payload.contains("\"isFavorite\""))
        assertTrue(payload.contains("\"timestamp\""))
        assertFalse(payload.contains("\"song_id\""))
    }

    @Test
    fun `restore accepts legacy snake case payload`() = runTest {
        val payload = """
            [
              {"song_id": 123, "is_favorite": true, "added_at": 1700000000000}
            ]
        """.trimIndent()

        handler.restore(payload)

        coVerify(exactly = 1) {
            favoritesDao.replaceAll(
                listOf(
                    FavoritesEntity(
                        songId = 123L,
                        isFavorite = true,
                        timestamp = 1_700_000_000_000L
                    )
                )
            )
        }
    }
}
