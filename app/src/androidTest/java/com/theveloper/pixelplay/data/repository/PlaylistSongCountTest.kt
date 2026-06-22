package com.theveloper.pixelplay.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.theveloper.pixelplay.data.database.LocalPlaylistDao
import com.theveloper.pixelplay.data.database.PixelPlayDatabase
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for issue #2391:
 * "Playlist song count doesn't update when removing songs — only when adding."
 *
 * Exercises the real PlaylistPreferencesRepository against an in-memory Room DB to
 * verify that the song count exposed by userPlaylistsFlow (used by the Playlists menu)
 * reflects removals as well as additions.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistSongCountTest {

    private lateinit var db: PixelPlayDatabase
    private lateinit var dao: LocalPlaylistDao
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: PlaylistPreferencesRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PixelPlayDatabase::class.java)
            .addCallback(PixelPlayDatabase.createRuntimeArtifactsCallback())
            .allowMainThreadQueries()
            .build()
        dao = db.localPlaylistDao()
        dataStore = PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("test_settings_${System.nanoTime()}")
        }
        val userPrefs = UserPreferencesRepository(dataStore, Json { ignoreUnknownKeys = true })
        repo = PlaylistPreferencesRepository(dao, userPrefs)
    }

    @After
    fun teardown() {
        db.close()
    }

    private suspend fun countFor(playlistId: String): Int =
        repo.userPlaylistsFlow.first().first { it.id == playlistId }.songIds.size

    @Test
    fun menuSongCount_reflectsAddAndRemove() = runTest {
        val playlist = repo.createPlaylist(name = "J-Pop", songIds = listOf("10", "20", "30"))
        assertEquals("initial count", 3, countFor(playlist.id))

        // Remove a song — the bug report says this does NOT update the count.
        repo.removeSongFromPlaylist(playlist.id, "20")
        assertEquals("after removing one song", 2, countFor(playlist.id))

        // Remove another.
        repo.removeSongFromPlaylist(playlist.id, "30")
        assertEquals("after removing a second song", 1, countFor(playlist.id))

        // Adding works per the report — verify it still does.
        repo.addSongsToPlaylist(playlist.id, listOf("40"))
        assertEquals("after adding one song", 2, countFor(playlist.id))
    }

    /**
     * Reproduces the real-world trigger for issue #2391: removing several songs in
     * quick succession. Each edit does an unsynchronized read-modify-write
     * (userPlaylistsFlow.first() -> modify -> updatePlaylist), so concurrent removals
     * all read the same original list and the last writer wins, silently dropping the
     * other removals. The Playlists-menu count (songIds.size) then stays stuck high.
     */
    @Test
    fun concurrentRemovals_doNotLoseUpdates() = runBlocking {
        val playlist = repo.createPlaylist(
            name = "Race",
            songIds = listOf("1", "2", "3", "4", "5")
        )
        assertEquals(5, countFor(playlist.id))

        // Remove four songs concurrently — "remove one or two of them", fast.
        coroutineScope {
            listOf("1", "2", "3", "4").forEach { id ->
                launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, id) }
            }
        }

        assertEquals("All concurrent removals must persist", 1, countFor(playlist.id))
    }

    /**
     * Walks the exact reproduction from issue #2391, asserting the fixed behaviour:
     * the song count stays accurate after a quick removal of "one or two" songs, and
     * a later addition does not preserve a phantom difference.
     */
    @Test
    fun issue2391_quickRemoveThenAdd_keepsCountAccurate() = runBlocking {
        // Steps 2-3: create a playlist and add a few songs.
        val playlist = repo.createPlaylist(
            name = "J-Pop",
            songIds = listOf("1", "2", "3", "4", "5", "6")
        )
        assertEquals(6, countFor(playlist.id))

        // Step 4: remove one or two of them — quickly, as fast taps do.
        coroutineScope {
            launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, "2") }
            launch(Dispatchers.IO) { repo.removeSongFromPlaylist(playlist.id, "4") }
        }
        // Step 5: the menu count must reflect BOTH removals (the bug left it stuck high).
        assertEquals("count after removing two songs", 4, countFor(playlist.id))

        // Steps 6-7: adding more must not carry over a phantom difference.
        repo.addSongsToPlaylist(playlist.id, listOf("7", "8"))
        assertEquals("count after adding two songs", 6, countFor(playlist.id))
    }
}
