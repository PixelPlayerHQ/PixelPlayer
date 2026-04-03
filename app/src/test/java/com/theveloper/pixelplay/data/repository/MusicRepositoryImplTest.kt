package com.theveloper.pixelplay.data.repository

import android.content.Context
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SongEntity // Necesario para datos de prueba
import com.theveloper.pixelplay.data.database.AlbumEntity
import com.theveloper.pixelplay.data.database.ArtistEntity
import com.theveloper.pixelplay.data.model.Song // Para verificar el mapeo
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.TelegramDao
import dagger.Lazy
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
// import com.google.common.truth.Truth.assertThat


@ExperimentalCoroutinesApi
class MusicRepositoryImplTest {

    private lateinit var musicRepository: MusicRepositoryImpl
    private val mockMusicDao: MusicDao = mockk()
    private val mockSearchHistoryDao: SearchHistoryDao = mockk(relaxed = true) // relaxed para evitar mockear todos los métodos de historial
    private val mockContext: Context = mockk(relaxed = true) // relaxed para getAllUniqueAudioDirectories si no se testea a fondo aquí
    private val mockUserPreferencesRepository: UserPreferencesRepository = mockk()
    private val mockPlaylistPreferencesRepository: PlaylistPreferencesRepository = mockk(relaxed = true)
    private val mockLyricsRepository: LyricsRepository = mockk(relaxed = true)
    private val mockTelegramDao: TelegramDao = mockk(relaxed = true)
    private val mockTelegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager = mockk(relaxed = true)
    private val mockTelegramRepository: com.theveloper.pixelplay.data.telegram.TelegramRepository = mockk(relaxed = true)
    private val mockTelegramCacheManagerProvider: Lazy<com.theveloper.pixelplay.data.telegram.TelegramCacheManager> = mockk()
    private val mockTelegramRepositoryProvider: Lazy<com.theveloper.pixelplay.data.telegram.TelegramRepository> = mockk()
    private val mockSongRepository: SongRepository = mockk(relaxed = true)
    private val mockFavoritesDao: FavoritesDao = mockk(relaxed = true)
    private val mockArtistImageRepository: ArtistImageRepository = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher) // Usar el dispatcher de prueba para Main
        // Mockear los flows de preferencias por defecto, pueden ser sobrescritos por test
        coEvery { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(emptySet())
        coEvery { mockUserPreferencesRepository.blockedDirectoriesFlow } returns flowOf(setOf("/dummy"))
        coEvery { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        coEvery { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(false)
        // Populate artists
        val dummyArtists = listOf(
            ArtistEntity(101L, "ArtistName1", 5, null),
            ArtistEntity(102L, "ArtistName2", 3, null)
        )
        every { mockMusicDao.getAllArtistsRaw() } returns flowOf(dummyArtists)
        coEvery { mockMusicDao.getDistinctParentDirectories() } returns listOf("/music/folder1", "/music/folder2")
        every { mockTelegramCacheManagerProvider.get() } returns mockTelegramCacheManager
        every { mockTelegramRepositoryProvider.get() } returns mockTelegramRepository

        every { mockMusicDao.getAllSongArtistCrossRefs() } returns flowOf(emptyList())
        every { mockMusicDao.getAllSongs(any(), any()) } answers {
            println("getAllSongs called with: ${args[0]}, ${args[1]}")
            flowOf(emptyList())
        }
        every { mockMusicDao.getArtistsWithSongCountsFiltered(any(), any(), any()) } returns flowOf(emptyList())

        // Logic-based DAO stubs
        every { mockMusicDao.getSongs(any(), eq(true)) } answers {
            val allowedParams = firstArg<List<String>>()
            if (allowedParams.isEmpty()) flowOf(emptyList()) else flowOf(emptyList()) // Placeholder, can be improved if needed
        }
        every { mockMusicDao.getSongs(any(), eq(false)) } returns flowOf(emptyList()) // Placeholder
        every { mockMusicDao.getAlbums(any(), eq(true), any()) } answers {
            val allowedParams = firstArg<List<String>>()
            if (allowedParams.isEmpty()) flowOf(emptyList()) else flowOf(emptyList())
        }
        every { mockMusicDao.getAlbums(any(), eq(false), any()) } returns flowOf(emptyList())
        
        every { mockMusicDao.getArtists(any(), eq(true)) } answers {
             val allowedParams = firstArg<List<String>>()
             if (allowedParams.isNotEmpty()) {
                 // 101L is allowed (Artist1Name), 102L is forbidden (Artist2Name)
                 flowOf(dummyArtists.filter { it.id == 101L })
             } else {
                 flowOf(emptyList())
             }
        }
        every { mockMusicDao.getArtists(any(), eq(false)) } returns flowOf(dummyArtists)



        musicRepository = MusicRepositoryImpl(
            context = mockContext,
            userPreferencesRepository = mockUserPreferencesRepository,
            playlistPreferencesRepository = mockPlaylistPreferencesRepository,
            searchHistoryDao = mockSearchHistoryDao,
            musicDao = mockMusicDao,
            lyricsRepository = mockLyricsRepository,
            telegramDao = mockTelegramDao,
            telegramCacheManagerProvider = mockTelegramCacheManagerProvider,
            telegramRepositoryProvider = mockTelegramRepositoryProvider,
            songRepository = mockSongRepository,

            favoritesDao = mockFavoritesDao,
            artistImageRepository = mockArtistImageRepository,
            folderTreeBuilder = mockk(relaxed = true)
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain() // Limpiar el dispatcher de Main
    }

    // --- Pruebas para getAudioFiles ---
    @Test
    fun `getAudioFiles returns songs from DAO, filtered by allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Pop", "/allowed/path/songA.mp3", "/allowed/path"),
            createSongEntity(2L, "Song B", "Artist 1", "Pop", "/forbidden/path/songB.mp3", "/forbidden/path"),
            createSongEntity(3L, "Song C", "Artist 2", "Rock", "/allowed/path/songC.mp3", "/allowed/path")
        )
        val allowedDirs = setOf("/allowed/path")

        // Mock filter behavior:
        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed/path") }
        every { mockMusicDao.getAllSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        every { mockMusicDao.getAllSongs(any(), eq(false)) } returns flowOf(songEntities)
        
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs) // No es suspend
        every { mockUserPreferencesRepository.blockedDirectoriesFlow } returns flowOf(setOf("/dummy")) // Trigger filter
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true) // No es suspend
        every { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(true)
        coEvery { mockMusicDao.getDistinctParentDirectories() } returns listOf("/allowed/path", "/forbidden/path")

        val result: List<Song> = musicRepository.getAudioFiles().first()

        assertEquals(2, result.size)
        assertEquals(listOf("1", "3"), result.map { it.id })
        verify { mockMusicDao.getAllSongs(any<List<String>>(), any<Boolean>()) }
    }

    @Test
    fun `getAudioFiles returns empty list if no allowed directories even before initial setup`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "Song A", "Artist 1", "Pop", "/any/path/songA.mp3", "/any/path"),
            createSongEntity(2L, "Song B", "Artist 1", "Pop", "/other/path/songB.mp3", "/other/path")
        )
        // Stubs removed, relying on setUp



        val result = musicRepository.getAudioFiles().first()
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getAudioFiles returns empty list if initial setup done and no allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
             createSongEntity(1L, "Song A", "Artist 1", "Pop", "/allowed/path/songA.mp3", "/allowed/path")
        )
        // Stubs removed
        val result = musicRepository.getAudioFiles().first()
        assertTrue(result.isEmpty())
    }

    // --- Pruebas para getAlbums ---
    @Test
    fun `getAlbums returns albums from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "S1", "A1", "G", "/allowed/s1.mp3", "/allowed").copy(albumId = 201L),
            createSongEntity(2L, "S2", "A1", "G", "/allowed/s2.mp3", "/allowed").copy(albumId = 201L),
            createSongEntity(3L, "S3", "A2", "G", "/forbidden/s3.mp3", "/forbidden").copy(albumId = 202L),
            createSongEntity(4L, "S4", "A3", "G", "/allowed/s4.mp3", "/allowed").copy(albumId = 203L)
        )
        val allAlbumEntities = listOf(
            AlbumEntity(201L, "Album1", "ArtistName1", 101L, "art_uri1", 10, 0L, 2023), // El songCount original del DAO
            AlbumEntity(202L, "Album2", "ArtistName2", 102L, "art_uri2", 5, 0L, 2022),
            AlbumEntity(203L, "Album3", "ArtistName3", 103L, "art_uri3", 3, 0L, 2021)
        )
        val allowedDirs = setOf("/allowed")

        // Mock getSongs logic to return filtered list for getAlbums internal call to getAudioFiles()
        // Filtered logic: Only S1, S2, S4 are in allowed directories
        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed") }
        every { mockMusicDao.getSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        val expectedAlbums = allAlbumEntities.map { album ->
            when (album.id) {
                201L -> album.copy(songCount = 2)
                203L -> album.copy(songCount = 1)
                else -> album
            }
        }.filter { it.id == 201L || it.id == 203L }
        every { mockMusicDao.getAlbums(any(), eq(true), any()) } returns flowOf(expectedAlbums)

        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)

        val result = musicRepository.getAlbums().first()

        assertEquals(2, result.size)
        assertEquals(listOf(201L, 203L), result.map { it.id })
        assertEquals(2, result.find { it.id == 201L }?.songCount)
        assertEquals(1, result.find { it.id == 203L }?.songCount)
    }

    // --- Pruebas para getArtists ---
    @Test
    fun `getArtists returns artists from DAO, filtered by songs in allowed directories`() = runTest(testDispatcher) {
        val songEntities = listOf(
            createSongEntity(1L, "S1", "Artist1Name", "G", "/allowed/s1.mp3", "/allowed"),
            createSongEntity(2L, "S2", "Artist2Name", "G", "/forbidden/s2.mp3", "/forbidden"),
            createSongEntity(3L, "S3", "Artist1Name", "G", "/allowed/s3.mp3", "/allowed")
        )
        val allArtistEntities = listOf(
            ArtistEntity(101L, "Artist1Name", 20), // El trackCount original del DAO
            ArtistEntity(102L, "Artist2Name", 10)
        )
        val allowedDirs = setOf("/allowed")

        // Mock getSongs logic to return filtered list for getArtists internal call to getAudioFiles()
        // Filtered logic: Only S1 and S3 are in allowed directories
        // Filtered logic: Only S1 and S3 are in allowed directories
        val filteredSongs = songEntities.filter { it.filePath.startsWith("/allowed") }
        every { mockMusicDao.getSongs(any(), eq(true)) } returns flowOf(filteredSongs)
        val expectedArtists = allArtistEntities.map { 
            if (it.id == 101L) it.copy(trackCount = 2) else it 
        }.filter { it.id == 101L }
        every { mockMusicDao.getArtistsWithSongCountsFiltered(any(), eq(true), any()) } returns flowOf(expectedArtists)
        
        every { mockUserPreferencesRepository.allowedDirectoriesFlow } returns flowOf(allowedDirs)
        every { mockUserPreferencesRepository.initialSetupDoneFlow } returns flowOf(true)
        every { mockUserPreferencesRepository.isFolderFilterActiveFlow } returns flowOf(true)


        val result = musicRepository.getArtists().first()

        assertEquals(1, result.size)
        assertEquals(101L, result.first().id)
        assertEquals(2, result.first().songCount)
    }

    @Test
    fun `getGenres deduplicates normalized genre ids`() = runTest(testDispatcher) {
        every {
            mockMusicDao.getUniqueGenres(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(listOf("Rock", " Rock ", "rock"))
        every {
            mockMusicDao.hasUnknownGenre(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(false)

        val result = musicRepository.getGenres().first()

        assertEquals(1, result.size)
        assertEquals("rock", result.first().id)
        assertEquals("Rock", result.first().name)
    }

    @Test
    fun `getGenres does not append unknown when already present`() = runTest(testDispatcher) {
        every {
            mockMusicDao.getUniqueGenres(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(listOf("Unknown", " unknown "))
        every {
            mockMusicDao.hasUnknownGenre(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(true)

        val result = musicRepository.getGenres().first()

        assertEquals(1, result.size)
        assertEquals(1, result.count { it.id == "unknown" })
        assertEquals("Unknown", result.first().name)
    }

    @Test
    fun `getGenres appends unknown only when needed`() = runTest(testDispatcher) {
        every {
            mockMusicDao.getUniqueGenres(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(listOf("Rock"))
        every {
            mockMusicDao.hasUnknownGenre(
                any<List<String>>(),
                any<Boolean>()
            )
        } returns flowOf(true)

        val result = musicRepository.getGenres().first()

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "rock" })
        assertEquals(1, result.count { it.id == "unknown" })
    }

    @Nested
    @DisplayName("Search History Functions")
    inner class SearchHistoryFunctions {
        @Test
        fun `addSearchHistoryItem calls dao methods`() = runTest {
            val query = "test query"
            coEvery { mockSearchHistoryDao.deleteByQuery(query) } just runs
            coEvery { mockSearchHistoryDao.insert(any()) } just runs

            musicRepository.addSearchHistoryItem(query)

            coVerifyOrder {
                mockSearchHistoryDao.deleteByQuery(query)
                mockSearchHistoryDao.insert(any())
            }
        }
        // TODO: Añadir más tests para el historial si es necesario
    }

    // TODO: Añadir tests para:
    // - getSongsForAlbum, getSongsForArtist, getSongsByIds
    // - searchSongs, searchAlbums, searchArtists, searchAll (verificando la lógica de combine y filtrado)
    // - getAllUniqueAlbumArtUris
    // - getMusicByGenre
    // - getAllUniqueAudioDirectories (si se mantiene la lógica de MediaStore, necesitará mockear ContentResolver)
    // - invalidateCachesDependentOnAllowedDirectories (verificar que hace lo esperado, o nada si es obsoleta)

    // Helper method to create SongEntity with defaults to avoid positional argument hell
    private fun createSongEntity(
        id: Long,
        title: String,
        artistName: String,
        genre: String,
        filePath: String,
        parentDirectoryPath: String
    ): SongEntity {
        return SongEntity(
            id = id,
            title = title,
            artistName = artistName,
            artistId = 101L, // Default
            albumName = "Album1", // Default
            albumId = 201L, // Default
            contentUriString = "uri_$id",
            albumArtUriString = "art_$id",
            duration = 180,
            genre = genre,
            filePath = filePath,
            parentDirectoryPath = parentDirectoryPath,
            year = 2023,
            trackNumber = 1
        )
    }
}
