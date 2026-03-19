package com.theveloper.pixelplay.data.backup.module

import com.google.gson.GsonBuilder
import com.theveloper.pixelplay.data.database.EngagementDao
import com.theveloper.pixelplay.data.database.SongEngagementEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EngagementStatsModuleHandlerTest {

    private val engagementDao: EngagementDao = mockk(relaxed = true)
    private val handler = EngagementStatsModuleHandler(
        engagementDao = engagementDao,
        gson = GsonBuilder().serializeNulls().create()
    )

    @Test
    fun `export uses stable canonical field names`() = runTest {
        coEvery { engagementDao.getAllEngagements() } returns listOf(
            SongEngagementEntity(
                songId = "song-1",
                playCount = 3,
                totalPlayDurationMs = 1200,
                lastPlayedTimestamp = 100
            )
        )

        val payload = handler.export()

        assertTrue(payload.contains("\"songId\""))
        assertTrue(payload.contains("\"playCount\""))
        assertTrue(payload.contains("\"totalPlayDurationMs\""))
        assertTrue(payload.contains("\"lastPlayedTimestamp\""))
        assertFalse(payload.contains("\"song_id\""))
        assertFalse(payload.contains("\"play_count\""))
    }

    @Test
    fun `restore sanitizes legacy fields skips malformed rows and merges duplicates`() = runTest {
        val payload = """
            [
              {"songId":"song-1","playCount":3,"totalDuration":1200,"lastPlayedAt":100},
              {"song_id":"song-2","play_count":"-4","duration_ms":"500","last_played_timestamp":"250"},
              {"songId":"song-1","playCount":2,"totalPlayDurationMs":4000,"lastPlayedTimestamp":300},
              {"songId":"   ","playCount":8},
              "bad-row"
            ]
        """.trimIndent()

        coEvery { engagementDao.replaceAll(any()) } returns Unit

        handler.restore(payload)

        coVerify(exactly = 1) {
            engagementDao.replaceAll(
                listOf(
                    SongEngagementEntity(
                        songId = "song-1",
                        playCount = 3,
                        totalPlayDurationMs = 4000,
                        lastPlayedTimestamp = 300
                    ),
                    SongEngagementEntity(
                        songId = "song-2",
                        playCount = 0,
                        totalPlayDurationMs = 500,
                        lastPlayedTimestamp = 250
                    )
                )
            )
        }
    }

    @Test
    fun `restore rejects payloads that contain no usable entries`() {
        val payload = """[{"playCount": 3}, null, "bad-row"]"""

        val error = assertThrows(IllegalArgumentException::class.java) {
            runTest {
                handler.restore(payload)
            }
        }

        assertEquals(
            "Engagement stats backup does not contain any valid entries.",
            error.message
        )
        coVerify(exactly = 0) { engagementDao.replaceAll(any()) }
    }
}
