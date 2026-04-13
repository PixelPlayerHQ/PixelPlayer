package com.theveloper.pixelplay.data.repository

// import kotlinx.coroutines.withContext // May not be needed for Flow transformations

// import kotlinx.coroutines.sync.withLock // May not be needed if directoryScanMutex logic changes

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log

import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.ArtistImageRepository
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.database.FavoritesDao
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SearchHistoryDao
import com.theveloper.pixelplay.data.database.SearchHistoryEntity
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramDao
import com.theveloper.pixelplay.data.database.toAlbum
import com.theveloper.pixelplay.data.database.toArtist
import com.theveloper.pixelplay.data.database.toSearchHistoryItem
import com.theveloper.pixelplay.data.database.toSong
import com.theveloper.pixelplay.data.database.toTelegramEntity
import com.theveloper.pixelplay.data.database.toTelegramEntityWithThread
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.FolderSource
import com.theveloper.pixelplay.data.model.StorageFilter
import com.theveloper.pixelplay.data.preferences.PlaylistPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.utils.DirectoryFilterUtils
import com.theveloper.pixelplay.utils.LogUtils
import com.theveloper.pixelplay.utils.StorageType
import com.theveloper.pixelplay.utils.StorageUtils
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.paging.filter
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MusicRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val playlistPreferencesRepository: PlaylistPreferencesRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val musicDao: MusicDao,
    private val lyricsRepository: LyricsRepository,
    private val telegramDao: TelegramDao,
    private val telegramCacheManagerProvider: Lazy<com.theveloper.pixelplay.data.telegram.TelegramCacheManager>,
    private val telegramRepositoryProvider: Lazy<com.theveloper.pixelplay.data.telegram.TelegramRepository>,
    private val songRepository: SongRepository,
    private val favoritesDao: FavoritesDao,
    private val artistImageRepository: ArtistImageRepository,
    private val folderTreeBuilder: FolderTreeBuilder
) : MusicRepository {

    companion object {
        /** Maximum number of search results to load at once to avoid memory issues with large libraries. */
        private const val SEARCH_RESULTS_LIMIT = 100
        private const val UNKNOWN_GENRE_NAME = "Unknown"
        private const val UNKNOWN_GENRE_ID = "unknown"
    }

    private val directoryScanMutex = Mutex()
    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val defaultLibraryPagingConfig = PagingConfig(
        pageSize = 50,
        enablePlaceholders = true,
        maxSize = 250
    )
    // Tracks the active prefetch job so a new flow emission cancels the previous one.
    @Volatile private var prefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchJob: Job? = null
    @Volatile private var currentSongArtistPrefetchSongId: Long? = null
    @Volatile private var telegramDownloadSyncObserverStarted = false
    private val telegramCacheManager: com.theveloper.pixelplay.data.telegram.TelegramCacheManager
        get() = telegramCacheManagerProvider.get()
    override val telegramRepository: com.theveloper.pixelplay.data.telegram.TelegramRepository
        get() = telegramRepositoryProvider.get()

    private fun normalizePath(path: String): String =
        runCatching { File(path).canonicalPath }.getOrElse { File(path).absolutePath }

    /** Cached directory filter — recomputed only when allowed/blocked dirs preferences change. */
    data class CachedDirFilter(val allowedParentDirs: List<String> = emptyList(), val applyFilter: Boolean = false)

    private val cachedDirFilter: StateFlow<CachedDirFilter> = combine(
        userPreferencesRepository.allowedDirectoriesFlow,
        userPreferencesRepository.blockedDirectoriesFlow
    ) { allowed, blocked ->
        val (dirs, apply) = DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowed,
            blockedDirs = blocked,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
        CachedDirFilter(dirs, apply)
    }.stateIn(repositoryScope, SharingStarted.Eagerly, CachedDirFilter())

    private fun ensureTelegramDownloadSyncObserverStarted() {
        if (telegramDownloadSyncObserverStarted) return
        telegramDownloadSyncObserverStarted = true

        repositoryScope.launch {
            telegramRepository.songFileUpdated.collect {
                androidx.work.WorkManager.getInstance(context).enqueue(
                    com.theveloper.pixelplay.data.worker.SyncWorker.incrementalSyncWork()
                )
            }
        }
    }

    private fun List<Artist>.missingImageCandidates(): List<Pair<Long, String>> =
        asSequence()
            .filter { it.effectiveImageUrl.isNullOrBlank() && it.name.isNotBlank() }
            .map { it.id to it.name }
            .distinctBy { (_, name) -> name.trim().lowercase() }
            .toList()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAudioFiles(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getAllSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedSongs(sortOption: SortOption, storageFilter: com.theveloper.pixelplay.data.model.StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedSongs(sortOption, storageFilter)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedAlbums(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getAlbumsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedArtists(
        sortOption: SortOption,
        storageFilter: StorageFilter
    ): Flow<PagingData<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    Pager(
                        config = defaultLibraryPagingConfig,
                        pagingSourceFactory = {
                            musicDao.getArtistsPaginated(
                                allowedParentDirs = allowedParentDirs,
                                applyDirectoryFilter = applyDirectoryFilter,
                                filterMode = storageFilter.toFilterMode(),
                                sortOrder = sortOption.storageKey
                            )
                        }
                    ).flow
                )
            }.flatMapLatest { it }
        }.map { pagingData ->
            pagingData.map { entity -> entity.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getPaginatedFavoriteSongs(sortOption: SortOption, storageFilter: StorageFilter): Flow<PagingData<Song>> {
        return songRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
    }

    override suspend fun getFavoriteSongsOnce(storageFilter: StorageFilter): List<Song> {
        return songRepository.getFavoriteSongsOnce(storageFilter)
    }

    override fun getFavoriteSongCountFlow(storageFilter: StorageFilter): Flow<Int> {
        return songRepository.getFavoriteSongCountFlow(storageFilter)
    }

    override fun getSongCountFlow(): Flow<Int> {
        return musicDao.getSongCount().distinctUntilChanged()
    }

    override suspend fun getRandomSongs(limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getRandomSongs(limit, filter.allowedParentDirs, filter.applyFilter).map { it.toSong() }
    }

    override suspend fun saveTelegramSongs(songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }
        if (entities.isNotEmpty()) {
            ensureTelegramDownloadSyncObserverStarted()
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
            // Trigger sync to update main DB
            androidx.work.WorkManager.getInstance(context).enqueue(
                com.theveloper.pixelplay.data.worker.SyncWorker.incrementalSyncWork()
            )
        }
    }

    override suspend fun replaceTelegramSongsForChannel(chatId: Long, songs: List<Song>) {
        val entities = songs.mapNotNull { it.toTelegramEntity() }.filter { it.chatId == chatId }
        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByChatId(chatId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }
        // Trigger sync to update main DB (and remove deleted songs)
        androidx.work.WorkManager.getInstance(context).enqueue(
            com.theveloper.pixelplay.data.worker.SyncWorker.incrementalSyncWork()
        )
    }

    /**
     * Compute allowed parent directories by subtracting blocked dirs from all known dirs.
     * Returns Pair(allowedDirs, applyFilter) for use with Room DAO filtered queries.
     */
    private suspend fun computeAllowedDirs(
        allowedDirs: Set<String>,
        blockedDirs: Set<String>
    ): Pair<List<String>, Boolean> {
        return DirectoryFilterUtils.computeAllowedParentDirs(
            allowedDirs = allowedDirs,
            blockedDirs = blockedDirs,
            getAllParentDirs = { musicDao.getDistinctParentDirectories() },
            normalizePath = ::normalizePath
        )
    }

    private fun StorageFilter.toFilterMode(): Int = when (this) {
        StorageFilter.ALL -> 0
        StorageFilter.OFFLINE -> 1
        StorageFilter.ONLINE -> 2
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAlbums(storageFilter: StorageFilter): Flow<List<Album>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getAlbums(allowedParentDirs, applyFilter, storageFilter.toFilterMode())
                .map { entities -> entities.map { it.toAlbum() } }
                .distinctUntilChanged()
        }.flowOn(Dispatchers.IO)
    }

    override fun getAlbumById(id: Long): Flow<Album?> {
        return musicDao.getAlbumById(id).map { it?.toAlbum() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getArtists(storageFilter: StorageFilter): Flow<List<Artist>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            val (allowedParentDirs, applyFilter) = computeAllowedDirs(allowedDirs, blockedDirs)
            musicDao.getArtistsWithSongCountsFiltered(
                allowedParentDirs = allowedParentDirs,
                applyDirectoryFilter = applyFilter,
                filterMode = storageFilter.toFilterMode()
            )
                .distinctUntilChanged()
                .map { entities ->
                    val artists = entities.map { it.toArtist() }
                    // Trigger prefetch for missing images (non-blocking)
                    val missingImages = artists.missingImageCandidates()
                    if (missingImages.isNotEmpty()) {
                        // Cancel any in-flight prefetch before starting a new one — the flow
                        // can emit multiple times during sync, and concurrent launches would
                        // create N × artist-count coroutines simultaneously.
                        prefetchJob?.cancel()
                        prefetchJob = repositoryScope.launch {
                            artistImageRepository.prefetchArtistImages(missingImages)
                        }
                    }
                    artists
                }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsForAlbum(albumId: Long): Flow<List<Song>> {
        return musicDao.getSongsByAlbumId(albumId).map { entities ->
            entities.map { it.toSong() }.sortedBy { it.trackNumber }
        }.flowOn(Dispatchers.IO)
    }

    override fun getArtistById(artistId: Long): Flow<Artist?> {
        return musicDao.getArtistById(artistId).map { it?.toArtist() }
    }

    override fun getArtistsForSong(songId: Long): Flow<List<Artist>> {
        return musicDao.getArtistsForSong(songId)
            .map { entities -> entities.map { it.toArtist() } }
            .distinctUntilChanged()
            .onEach { artists ->
                val missingImages = artists.missingImageCandidates()
                if (missingImages.isNotEmpty()) {
                    val isNewSong = currentSongArtistPrefetchSongId != songId
                    if (isNewSong) {
                        currentSongArtistPrefetchJob?.cancel()
                        currentSongArtistPrefetchSongId = songId
                    } else if (currentSongArtistPrefetchJob?.isActive == true) {
                        // Room re-emits as artist rows are updated; keep the current song batch
                        // alive so one successful image write does not cancel the remaining fetches.
                        return@onEach
                    }

                    currentSongArtistPrefetchJob = repositoryScope.launch {
                        artistImageRepository.prefetchArtistImages(missingImages)
                    }
                }
            }
            .flowOn(Dispatchers.IO)
    }

    override fun getSongsForArtist(artistId: Long): Flow<List<Song>> {
        return musicDao.getSongsForArtist(artistId).map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getAllUniqueAudioDirectories(): Set<String> = withContext(Dispatchers.IO) {
        LogUtils.d(this, "getAllUniqueAudioDirectories")
        directoryScanMutex.withLock {
            val directories = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR ${MediaStore.Audio.Media.DATA} LIKE '%.flac')"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, selection, null, null
            )?.use { c ->
                val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (c.moveToNext()) {
                    File(c.getString(dataColumn)).parent?.let { directories.add(it) }
                }
            }
            LogUtils.i(this, "Found ${directories.size} unique audio directories")
            return@withLock directories
        }
    }

    override fun getAllUniqueAlbumArtUris(): Flow<List<Uri>> {
        return musicDao.getAllUniqueAlbumArtUrisFromSongs().map { uriStrings ->
            uriStrings.mapNotNull { it.toUri() }
        }.flowOn(Dispatchers.IO)
    }

    // --- Métodos de Búsqueda ---

    override fun searchSongs(query: String): Flow<List<Song>> {
        if (query.isBlank()) return flowOf(emptyList())
        // Use limited search to avoid loading thousands of results into memory
        return musicDao.searchSongsLimited(query, emptyList(), false, SEARCH_RESULTS_LIMIT).map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }


    override fun searchAlbums(query: String): Flow<List<Album>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchAlbums(query, emptyList(), false).map { entities ->
            entities.map { it.toAlbum() }
        }.flowOn(Dispatchers.IO)
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        if (query.isBlank()) return flowOf(emptyList())
        return musicDao.searchArtists(query, emptyList(), false).map { entities ->
            entities.map { it.toArtist() }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun searchPlaylists(query: String): List<Playlist> {
        if (query.isBlank()) return emptyList()
        return playlistPreferencesRepository.userPlaylistsFlow.first()
            .filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
    }

    override fun searchAll(query: String, filterType: SearchFilterType): Flow<List<SearchResultItem>> {
        if (query.isBlank()) return flowOf(emptyList())
        val playlistsFlow = flow { emit(searchPlaylists(query)) }

        return when (filterType) {
            SearchFilterType.ALL -> {
                combine(
                    searchSongs(query),
                    searchAlbums(query),
                    searchArtists(query),
                    playlistsFlow
                ) { songs, albums, artists, playlists ->
                    mutableListOf<SearchResultItem>().apply {
                        songs.forEach { add(SearchResultItem.SongItem(it)) }
                        albums.forEach { add(SearchResultItem.AlbumItem(it)) }
                        artists.forEach { add(SearchResultItem.ArtistItem(it)) }
                        playlists.forEach { add(SearchResultItem.PlaylistItem(it)) }
                    }
                }
            }
            SearchFilterType.SONGS -> searchSongs(query).map { songs -> songs.map { SearchResultItem.SongItem(it) } }
            SearchFilterType.ALBUMS -> searchAlbums(query).map { albums -> albums.map { SearchResultItem.AlbumItem(it) } }
            SearchFilterType.ARTISTS -> searchArtists(query).map { artists -> artists.map { SearchResultItem.ArtistItem(it) } }
            SearchFilterType.PLAYLISTS -> playlistsFlow.map { playlists -> playlists.map { SearchResultItem.PlaylistItem(it) } }
        }.flowOn(Dispatchers.Default)
    }

    override suspend fun addSearchHistoryItem(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
            searchHistoryDao.insert(SearchHistoryEntity(query = query, timestamp = System.currentTimeMillis()))
        }
    }

    override suspend fun getRecentSearchHistory(limit: Int): List<SearchHistoryItem> {
        return withContext(Dispatchers.IO) {
            searchHistoryDao.getRecentSearches(limit).map { it.toSearchHistoryItem() }
        }
    }

    override suspend fun deleteSearchHistoryItemByQuery(query: String) {
        withContext(Dispatchers.IO) {
            searchHistoryDao.deleteByQuery(query)
        }
    }

    override suspend fun clearSearchHistory() {
        withContext(Dispatchers.IO) {
            searchHistoryDao.clearAll()
        }
    }

    override fun getMusicByGenre(genreId: String): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.mockGenresEnabledFlow,
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { mockEnabled, allowedDirs, blockedDirs ->
            Triple(mockEnabled, allowedDirs, blockedDirs)
        }.flatMapLatest { (mockEnabled, allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                val genreName = if (mockEnabled) "Mock" else genreId
                emit(
                    if (genreName.equals("unknown", ignoreCase = true)) {
                        musicDao.getSongsWithNullGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    } else {
                        musicDao.getSongsByGenre(
                            genreName = genreName,
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    }
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.flowOn(Dispatchers.IO)
    }

    override fun getSongsByIds(songIds: List<String>): Flow<List<Song>> {
        if (songIds.isEmpty()) return flowOf(emptyList())
        val longIds = songIds.mapNotNull { it.toLongOrNull() }
        if (longIds.isEmpty()) return flowOf(emptyList())
        return musicDao.getSongsByIds(longIds, emptyList(), false).map { entities ->
            val songMap = entities.associate { it.id.toString() to it.toSong() }
            // Preserve the requested order
            songIds.mapNotNull { songMap[it] }
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun getSongByPath(path: String): Song? {
        return withContext(Dispatchers.IO) {
            musicDao.getSongByPath(path)?.toSong()
        }
    }

    override suspend fun invalidateCachesDependentOnAllowedDirectories() {
        Log.i("MusicRepo", "invalidateCachesDependentOnAllowedDirectories called. Reactive flows will update automatically.")
    }

    // Implementación de las nuevas funciones suspend para carga única
    override suspend fun getAllSongsOnce(): List<Song> = withContext(Dispatchers.IO) {
        musicDao.getAllSongsList().map { it.toSong() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDistinctAlbumArtSongs(): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getDistinctAlbumArtSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getHomeMixPreviewSongs(limit: Int): Flow<List<Song>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    musicDao.getHomeMixPreviewSongs(
                        limit = limit,
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter
                    )
                )
            }.flatMapLatest { it }
        }.map { entities ->
            entities.map { it.toSong() }
        }.distinctUntilChanged().flowOn(Dispatchers.IO)
    }

    override suspend fun getAllAlbumsOnce(): List<Album> = withContext(Dispatchers.IO) {
        musicDao.getAllAlbumsList(emptyList(), false).map { it.toAlbum() }
    }

    override suspend fun getAllArtistsOnce(): List<Artist> = withContext(Dispatchers.IO) {
        musicDao.getAllArtistsListRaw().map { it.toArtist() }
    }

    override suspend fun setFavoriteStatus(songId: String, isFavorite: Boolean) = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext
        if (isFavorite) {
            favoritesDao.setFavorite(
                com.theveloper.pixelplay.data.database.FavoritesEntity(
                    songId = id,
                    isFavorite = true
                )
            )
        } else {
            favoritesDao.removeFavorite(id)
        }
    }

    override suspend fun getFavoriteSongIdsOnce(): Set<String> = withContext(Dispatchers.IO) {
        favoritesDao.getFavoriteSongIdsOnce()
            .map { it.toString() }
            .toSet()
    }

    override fun getFavoriteSongIdsFlow(): Flow<Set<String>> {
        return favoritesDao.getFavoriteSongIds()
            .map { ids -> ids.asSequence().map(Long::toString).toSet() }
            .distinctUntilChanged()
    }

    override suspend fun toggleFavoriteStatus(songId: String): Boolean = withContext(Dispatchers.IO) {
        val id = songId.toLongOrNull() ?: return@withContext false
        val isFav = favoritesDao.isFavorite(id) ?: false
        val newFav = !isFav
        setFavoriteStatus(songId, newFav)
        return@withContext newFav
    }

    override fun getSong(songId: String): Flow<Song?> {
        val longId = songId.toLongOrNull()
        return if (longId != null) {
            musicDao.getSongById(longId).map { it?.toSong() }.flowOn(Dispatchers.IO)
        } else {
            combine(
                telegramDao.getSongsByIds(listOf(songId)),
                telegramDao.getAllChannels()
            ) { songs, channels ->
                val channelMap = channels.associateBy { it.chatId }
                songs.firstOrNull()?.let {
                    it.toSong(channelTitle = channelMap[it.chatId]?.title)
                }
            }.flowOn(Dispatchers.IO)
        }
    }

    override fun getGenres(): Flow<List<Genre>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow
        ) { allowedDirs, blockedDirs ->
            allowedDirs to blockedDirs
        }.flatMapLatest { (allowedDirs, blockedDirs) ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) =
                    computeAllowedDirs(allowedDirs, blockedDirs)
                emit(
                    combine(
                        musicDao.getUniqueGenres(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        ),
                        musicDao.hasUnknownGenre(
                            allowedParentDirs = allowedParentDirs,
                            applyDirectoryFilter = applyDirectoryFilter
                        )
                    ) { genreNames, hasUnknown ->
                        val knownGenres = genreNames
                            .asSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .map { buildGenre(it) }
                            .distinctBy { it.id }
                            .sortedBy { it.name.lowercase() }
                            .toList()
                        val unknownAlreadyPresent = knownGenres.any { it.id == UNKNOWN_GENRE_ID }
                        if (hasUnknown && !unknownAlreadyPresent) {
                            knownGenres + buildGenre(UNKNOWN_GENRE_NAME)
                        } else {
                            knownGenres
                        }
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private fun buildGenre(genreName: String): Genre {
        val id = if (genreName.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)) {
            UNKNOWN_GENRE_ID
        } else {
            genreName
                .lowercase()
                .replace(" ", "_")
                .replace("/", "_")
        }
        val colorInt = genreName.hashCode()
        val lightColorHex = "#${(colorInt and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
        val darkColorHex = "#${((colorInt xor 0xFFFFFF) and 0x00FFFFFF).toString(16).padStart(6, '0').uppercase()}"
        return Genre(
            id = id,
            name = genreName,
            lightColorHex = lightColorHex,
            onLightColorHex = "#000000",
            darkColorHex = darkColorHex,
            onDarkColorHex = "#FFFFFF"
        )
    }

    override suspend fun getLyrics(
        song: Song,
        sourcePreference: LyricsSourcePreference,
        forceRefresh: Boolean
    ): Lyrics? {
        return lyricsRepository.getLyrics(song, sourcePreference, forceRefresh)
    }

    /**
     * Obtiene la letra de una canción desde la API de LRCLIB, la persiste en la base de datos
     * y la devuelve como un objeto Lyrics parseado.
     *
     * @param song La canción para la cual se buscará la letra.
     * @return Un objeto Result que contiene el objeto Lyrics si se encontró, o un error.
     */
    override suspend fun getLyricsFromRemote(song: Song): Result<Pair<Lyrics, String>> {
        return lyricsRepository.fetchFromRemote(song)
    }

    override suspend fun searchRemoteLyrics(song: Song): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemote(song)
    }

    override suspend fun searchRemoteLyricsByQuery(title: String, artist: String?): Result<Pair<String, List<LyricsSearchResult>>> {
        return lyricsRepository.searchRemoteByQuery(title, artist)
    }

    override suspend fun updateLyrics(songId: Long, lyrics: String) {
        lyricsRepository.updateLyrics(songId, lyrics)
    }

    override suspend fun resetLyrics(songId: Long) {
        lyricsRepository.resetLyrics(songId)
    }

    override suspend fun resetAllLyrics() {
        lyricsRepository.resetAllLyrics()
    }

    override fun getMusicFolders(storageFilter: StorageFilter): Flow<List<MusicFolder>> {
        return combine(
            userPreferencesRepository.allowedDirectoriesFlow,
            userPreferencesRepository.blockedDirectoriesFlow,
            userPreferencesRepository.isFolderFilterActiveFlow,
            userPreferencesRepository.foldersSourceFlow
        ) { allowedDirs, blockedDirs, isFolderFilterActive, folderSource ->
            FolderFlowConfig(
                allowedDirs = allowedDirs,
                blockedDirs = blockedDirs,
                isFolderFilterActive = isFolderFilterActive,
                folderSource = folderSource
            )
        }.flatMapLatest { config ->
            flow {
                val (allowedParentDirs, applyDirectoryFilter) = computeAllowedDirs(
                    allowedDirs = config.allowedDirs,
                    blockedDirs = config.blockedDirs
                )
                emit(
                    musicDao.getFolderSongs(
                        allowedParentDirs = allowedParentDirs,
                        applyDirectoryFilter = applyDirectoryFilter,
                        filterMode = storageFilter.toFilterMode()
                    ).map { folderSongs ->
                        folderTreeBuilder.buildFolderTree(
                            folderSongs = folderSongs,
                            allowedDirs = config.allowedDirs,
                            blockedDirs = config.blockedDirs,
                            isFolderFilterActive = config.isFolderFilterActive,
                            folderSource = config.folderSource,
                            context = context
                        )
                    }
                )
            }.flatMapLatest { it }
        }.conflate().flowOn(Dispatchers.IO)
    }

    private data class FolderFlowConfig(
        val allowedDirs: Set<String>,
        val blockedDirs: Set<String>,
        val isFolderFilterActive: Boolean,
        val folderSource: FolderSource
    )

    override suspend fun deleteById(id: Long) {
        musicDao.deleteById(id)
    }

    override suspend fun clearTelegramData() {
        // Delete all Telegram playlists from app playlists
        val allChannels = telegramDao.getAllChannels().first()
        allChannels.forEach { channel ->
            telegramRepository.deleteAppPlaylistForTelegramChannel(channel.chatId)
        }

        musicDao.clearAllTelegramSongs()
        telegramDao.clearAll()
        // Clear all Telegram caches (TDLib files, embedded art, memory)
        telegramRepository.clearMemoryCache()
        telegramCacheManager.clearAllCache()
    }

    override suspend fun saveTelegramChannel(channel: TelegramChannelEntity) {
        telegramDao.insertChannel(channel)

        // Create or update the corresponding app playlist.
        // Forum channels use per-topic playlists (managed by replaceTelegramSongsForTopic),
        // so we skip the flat channel-level playlist when topics exist.
        try {
            val topics = withContext(Dispatchers.IO) {
                telegramDao.getTopicsByChannelOnce(channel.chatId)
            }
            if (topics.isEmpty()) {
                // Flat (non-forum) channel: create/update a single channel playlist
                val channelSongs = withContext(Dispatchers.IO) {
                    telegramDao.getSongsByChatId(channel.chatId)
                }
                telegramRepository.updateAppPlaylistForTelegramChannel(
                    channel.chatId,
                    channel.title,
                    channelSongs
                )
            }
            // Forum channels: topic playlists are managed by replaceTelegramSongsForTopic
        } catch (e: Exception) {
            Log.e("MusicRepo", "Failed to update app playlist for Telegram channel ${channel.chatId}", e)
        }
    }

    override fun getAllTelegramChannels(): Flow<List<TelegramChannelEntity>> {
        return telegramDao.getAllChannels()
    }

    override suspend fun deleteTelegramChannel(chatId: Long) {
        musicDao.clearTelegramSongsForChat(chatId)
        telegramDao.deleteSongsByChatId(chatId)
        telegramDao.deleteTopicsByChannel(chatId)     // NEW: remove topics
        telegramDao.deleteChannel(chatId)
        telegramRepository.deleteAppPlaylistForTelegramChannel(chatId)
        telegramRepository.deleteAllTopicPlaylistsForChannel(chatId)  // NEW: remove topic playlists
    }

    override suspend fun saveTelegramTopics(chatId: Long, topics: List<TelegramTopicEntity>) {
        telegramDao.insertTopics(topics)
    }

    override suspend fun replaceTopicsForChannel(
        chatId: Long,
        freshTopics: List<TelegramTopicEntity>
    ) {
        val existingTopics = telegramDao.getTopicsByChannelOnce(chatId)
        val freshThreadIds = freshTopics.map { it.threadId }.toSet()

        // Delete topics that are no longer returned by Telegram
        val removedTopics = existingTopics.filter { it.threadId !in freshThreadIds }
        for (removed in removedTopics) {
            // Delete their songs from the telegram_songs table
            telegramDao.deleteSongsByTopicId(chatId, removed.threadId)
            // Delete their songs from the main music DB
            musicDao.clearTelegramSongsForTopic(chatId, removed.threadId)
            // Delete their playlist from the app playlist store
            telegramRepository.deleteAppPlaylistForTopic(chatId, removed.threadId)
            // Delete the topic row itself
            telegramDao.deleteTopic(removed.id)
        }

        // Insert/update the fresh topic list
        if (freshTopics.isNotEmpty()) {
            telegramDao.insertTopics(freshTopics)
        }
    }

    override suspend fun getTopicsForChannel(chatId: Long): List<TelegramTopicEntity> {
        return telegramDao.getTopicsByChannelOnce(chatId)
    }

    override suspend fun replaceTelegramSongsForTopic(
        chatId: Long,
        threadId: Long,
        topicName: String,
        songs: List<Song>
    ) {
        // Stamp each song entity with the threadId before inserting
        val entities = songs.mapNotNull { it.toTelegramEntityWithThread(threadId) }
            .filter { it.chatId == chatId }

        ensureTelegramDownloadSyncObserverStarted()
        telegramDao.deleteSongsByTopicId(chatId, threadId)
        if (entities.isNotEmpty()) {
            telegramDao.insertSongs(entities)
            telegramRepository.warmUpArtworkForSongs(entities)
        }

        // Create/update the per-topic app playlist
        telegramRepository.updateAppPlaylistForTopic(chatId, threadId, topicName, entities)

        // Sync main music DB
        androidx.work.WorkManager.getInstance(context).enqueue(
            com.theveloper.pixelplay.data.worker.SyncWorker.incrementalSyncWork()
        )
    }

    override suspend fun getSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }

    override suspend fun getFavoriteSongIdsSorted(
        sortOption: SortOption,
        storageFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): List<Long> = withContext(Dispatchers.IO) {
        val filter = cachedDirFilter.value
        musicDao.getFavoriteSongIdsSorted(
            allowedParentDirs = filter.allowedParentDirs,
            applyDirectoryFilter = filter.applyFilter,
            sortOrder = sortOption.storageKey,
            filterMode = storageFilter.toFilterMode()
        )
    }
}
