package com.theveloper.pixelplay.presentation.viewmodel

import android.content.ComponentCallbacks2
import android.os.Trace
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.toArgb
import android.util.Log
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import com.theveloper.pixelplay.data.model.SourceScope
import javax.inject.Provider

private const val ENABLE_FOLDERS_STORAGE_FILTER = false
private data class GenreSeed(
    val id: String,
    val name: String
)

/**
 * Manages the data state of the music library: Songs, Albums, Artists, Folders.
 * Handles loading from Repository and applying SortOptions.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val extensionRepository: com.theveloper.pixelplay.data.repository.ExtensionRepository
) {

    // --- State ---
    private val _allSongs = MutableStateFlow<ImmutableList<Song>>(persistentListOf())
    val allSongs = _allSongs.asStateFlow()

    private val _allSongsById = MutableStateFlow<Map<String, Song>>(emptyMap())
    val allSongsById = _allSongsById.asStateFlow()

    private val _albums = MutableStateFlow<ImmutableList<Album>>(persistentListOf())
    val albums = _albums.asStateFlow()

    private val _artists = MutableStateFlow<ImmutableList<Artist>>(persistentListOf())
    val artists = _artists.asStateFlow()

    private val _musicFolders = MutableStateFlow<ImmutableList<MusicFolder>>(persistentListOf())
    val musicFolders = _musicFolders.asStateFlow()

    private val _isLoadingLibrary = MutableStateFlow(false)
    val isLoadingLibrary = _isLoadingLibrary.asStateFlow()

    private val _isLoadingCategories = MutableStateFlow(false)
    val isLoadingCategories = _isLoadingCategories.asStateFlow()

    // Sort Options
    private val _currentSongSortOption = MutableStateFlow<SortOption>(SortOption.SongDefaultOrder)
    val currentSongSortOption = _currentSongSortOption.asStateFlow()

    // Filter Options
    private val _currentSourceScope = MutableStateFlow<SourceScope>(SourceScope.All)
    val currentSourceScope = _currentSourceScope.asStateFlow()

    /**
     * Effective source scope. Legacy hideLocalMedia pref is now handled by setting SourceScope to Extension.
     */
    val effectiveSourceScope: kotlinx.coroutines.flow.Flow<SourceScope> = _currentSourceScope.asStateFlow()

    private fun effectiveFoldersSourceScope(
        selectedScope: SourceScope
    ): SourceScope {
        return if (ENABLE_FOLDERS_STORAGE_FILTER) {
            selectedScope
        } else {
            SourceScope.Local
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songsPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Song>> =
        kotlinx.coroutines.flow.combine(_currentSongSortOption, effectiveSourceScope) { sort, scope ->
            sort to scope
        }.flatMapLatest { (sortOption, scope) ->
            musicRepository.getPaginatedSongs(sortOption, scope)
        }
        .flowOn(Dispatchers.IO)

    private val _currentAlbumSortOption = MutableStateFlow<SortOption>(SortOption.AlbumTitleAZ)
    val currentAlbumSortOption = _currentAlbumSortOption.asStateFlow()

    private val _currentArtistSortOption = MutableStateFlow<SortOption>(SortOption.ArtistNameAZ)
    val currentArtistSortOption = _currentArtistSortOption.asStateFlow()

    private val _currentFolderSortOption = MutableStateFlow<SortOption>(SortOption.FolderNameAZ)
    val currentFolderSortOption = _currentFolderSortOption.asStateFlow()

    private val _currentFavoriteSortOption = MutableStateFlow<SortOption>(SortOption.LikedSongDateLiked)
    val currentFavoriteSortOption = _currentFavoriteSortOption.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val albumsPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Album>> =
        kotlinx.coroutines.flow.combine(
            _currentAlbumSortOption,
            effectiveSourceScope,
            userPreferencesRepository.minTracksPerAlbumFlow
        ) { sort, scope, minTracks ->
            Triple(sort, scope, minTracks)
        }.flatMapLatest { (sortOption, scope, minTracks) ->
            musicRepository.getPaginatedAlbums(sortOption, scope, minTracks)
        }
        .flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val artistsPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Artist>> =
        kotlinx.coroutines.flow.combine(_currentArtistSortOption, effectiveSourceScope) { sort, scope ->
            sort to scope
        }.flatMapLatest { (sortOption, scope) ->
            musicRepository.getPaginatedArtists(sortOption, scope)
        }
        .flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoritesPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Song>> =
        kotlinx.coroutines.flow.combine(_currentFavoriteSortOption, effectiveSourceScope) { sort, scope ->
            sort to scope
        }.flatMapLatest { (sortOption, scope) ->
            musicRepository.getPaginatedFavoriteSongs(sortOption, scope)
        }
        .flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoriteSongCountFlow: kotlinx.coroutines.flow.Flow<Int> = effectiveSourceScope
        .flatMapLatest { scope -> musicRepository.getFavoriteSongCountFlow(scope) }
        .flowOn(Dispatchers.IO)

    val genres: kotlinx.coroutines.flow.Flow<ImmutableList<com.theveloper.pixelplay.data.model.Genre>> =
        musicRepository.getGenres()
        .map { genres ->
            genres.map { genre ->
                GenreSeed(
                    id = genre.id,
                    name = genre.name
                )
            }
        }
        .distinctUntilChanged()
        .map { seeds ->
            seeds.map { seed ->
                val lightThemeColor = com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(seed.id, isDark = false)
                val darkThemeColor = com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(seed.id, isDark = true)

                com.theveloper.pixelplay.data.model.Genre(
                    id = seed.id,
                    name = seed.name,
                    lightColorHex = lightThemeColor.container.toHexString(),
                    onLightColorHex = lightThemeColor.onContainer.toHexString(),
                    darkColorHex = darkThemeColor.container.toHexString(),
                    onDarkColorHex = darkThemeColor.onContainer.toHexString()
                )
            }
                .toImmutableList()
        }
        .flowOn(Dispatchers.Default)


    // Internal state
    private var scope: CoroutineScope? = null

    // --- Initialization ---

    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        // Initial load of sort preferences
        scope.launch {
            val songSortKey = userPreferencesRepository.songsSortOptionFlow.first()
            _currentSongSortOption.value = SortOption.SONGS.find { it.storageKey == songSortKey } ?: SortOption.SongDefaultOrder

            val albumSortKey = userPreferencesRepository.albumsSortOptionFlow.first()
            _currentAlbumSortOption.value = SortOption.ALBUMS.find { it.storageKey == albumSortKey } ?: SortOption.AlbumTitleAZ

            val artistSortKey = userPreferencesRepository.artistsSortOptionFlow.first()
            _currentArtistSortOption.value = SortOption.ARTISTS.find { it.storageKey == artistSortKey } ?: SortOption.ArtistNameAZ

            val folderSortKey = userPreferencesRepository.foldersSortOptionFlow.first()
            _currentFolderSortOption.value = SortOption.FOLDERS.find { it.storageKey == folderSortKey } ?: SortOption.FolderNameAZ

            val likedSortKey = userPreferencesRepository.likedSongsSortOptionFlow.first()
            _currentFavoriteSortOption.value = SortOption.LIKED.find { it.storageKey == likedSortKey } ?: SortOption.LikedSongDateLiked

            // Restore last source scope
            _currentSourceScope.value = userPreferencesRepository.lastSourceScopeFlow.first()
        }

        // Reactive Bridge: Observe extensions
        scope.launch {
            extensionRepository.currentMusicExtension.collect { extension ->
                if (extension != null) {
                    // Auto-switch to extension scope if one is selected from Home/Search
                    setSourceScope(SourceScope.Extension(extension.metadata.id))
                }
            }
        }

        // Recovery Logic: Fallback if extension is uninstalled
        scope.launch {
            extensionRepository.installedMusicExtensions.collect { extensions ->
                val current = _currentSourceScope.value
                if (current is SourceScope.Extension) {
                    val stillInstalled = extensions.any { it.metadata.id == current.extensionId }
                    if (!stillInstalled) {
                        Log.w("LibraryStateHolder", "Extension ${current.extensionId} uninstalled. Falling back to All scope.")
                        setSourceScope(SourceScope.All)
                    }
                }
            }
        }
    }

    fun onCleared() {
        scope = null
    }

    // --- Data Loading ---

    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var foldersJob: Job? = null
    @Volatile
    private var needsReloadAfterTrim: Boolean = false

    fun startObservingLibraryData() {
        if (
            songsJob?.isActive == true &&
            albumsJob?.isActive == true &&
            artistsJob?.isActive == true &&
            foldersJob?.isActive == true
        ) {
            return
        }

        Log.d("LibraryStateHolder", "startObservingLibraryData called.")
        needsReloadAfterTrim = false

        songsJob = scope?.launch {
            _isLoadingLibrary.value = true
            musicRepository.getAudioFiles().conflate().collect { songs ->
                val immutableSongs = withContext(Dispatchers.Default) { songs.toImmutableList() }
                val songsMap = withContext(Dispatchers.Default) { songs.associateBy { it.id } }

                _allSongs.value = immutableSongs
                _allSongsById.value = songsMap

                sortSongs(_currentSongSortOption.value, persist = false)
                _isLoadingLibrary.value = false
            }
        }

        albumsJob = scope?.launch {
            _isLoadingCategories.value = true
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            kotlinx.coroutines.flow.combine(
                effectiveSourceScope,
                userPreferencesRepository.minTracksPerAlbumFlow
            ) { scope, minTracks ->
                scope to minTracks
            }.flatMapLatest { (scope, minTracks) ->
                musicRepository.getAlbums(scope, minTracks)
            }.conflate().collect { albums ->
                val sortedAlbums = withContext(Dispatchers.Default) {
                    sortAlbumsList(albums, _currentAlbumSortOption.value).toImmutableList()
                }
                _albums.value = sortedAlbums
                _isLoadingCategories.value = false
            }
        }

        artistsJob = scope?.launch {
            _isLoadingCategories.value = true
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            effectiveSourceScope.flatMapLatest { scope ->
                musicRepository.getArtists(scope)
            }.conflate().collect { artists ->
                val sortedArtists = withContext(Dispatchers.Default) {
                    sortArtistsList(artists, _currentArtistSortOption.value).toImmutableList()
                }
                _artists.value = sortedArtists
                _isLoadingCategories.value = false
            }
        }

        foldersJob = scope?.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            effectiveSourceScope.flatMapLatest { scope ->
                musicRepository.getMusicFolders(effectiveFoldersSourceScope(scope))
            }.conflate().collect { folders ->
                val sortedFolders = withContext(Dispatchers.Default) {
                    sortFoldersList(folders, _currentFolderSortOption.value).toImmutableList()
                }
                _musicFolders.value = sortedFolders
            }
        }
    }

    fun loadSongsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadAlbumsIfNeeded() {
        startObservingLibraryData()
    }

    fun loadArtistsIfNeeded() {
        startObservingLibraryData()
    }

    // --- Sorting ---

    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentSongSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setSongsSortOption(sortOption.storageKey)
            }
            _currentSongSortOption.value = sortOption
        }
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentAlbumSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setAlbumsSortOption(sortOption.storageKey)
            }
            _currentAlbumSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortAlbumsList(_albums.value, sortOption).toImmutableList()
            }
            _albums.value = sorted
        }
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentArtistSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setArtistsSortOption(sortOption.storageKey)
            }
            _currentArtistSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortArtistsList(_artists.value, sortOption).toImmutableList()
            }
            _artists.value = sorted
        }
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentFolderSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setFoldersSortOption(sortOption.storageKey)
            }
            _currentFolderSortOption.value = sortOption

            val sorted = withContext(Dispatchers.Default) {
                sortFoldersList(_musicFolders.value, sortOption).toImmutableList()
            }
            _musicFolders.value = sorted
        }
    }

    private fun sortAlbumsList(albums: Iterable<Album>, sortOption: SortOption): List<Album> {
        return when (sortOption) {
            SortOption.AlbumTitleAZ -> albums.sortedWith(
                compareBy<Album> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumTitleZA -> albums.sortedWith(
                compareByDescending<Album> { it.title.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumArtist -> albums.sortedWith(
                compareBy<Album> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumArtistDesc -> albums.sortedWith(
                compareByDescending<Album> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumReleaseYear -> albums.sortedWith(
                compareByDescending<Album> { it.year }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumReleaseYearAsc -> albums.sortedWith(
                compareBy<Album> { it.year }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumDateAdded -> albums.sortedWith(
                compareByDescending<Album> { it.dateAdded }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumSizeAsc -> albums.sortedWith(
                compareBy<Album> { it.songCount }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.AlbumSizeDesc -> albums.sortedWith(
                compareByDescending<Album> { it.songCount }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.id }
            )
            else -> albums.toList()
        }
    }

    private fun sortArtistsList(artists: Iterable<Artist>, sortOption: SortOption): List<Artist> {
        return when (sortOption) {
            SortOption.ArtistNameAZ -> artists.sortedWith(
                compareBy<Artist> { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNameZA -> artists.sortedWith(
                compareByDescending<Artist> { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNumSongsDesc -> artists.sortedWith(
                compareByDescending<Artist> { it.songCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            SortOption.ArtistNumSongsAsc -> artists.sortedWith(
                compareBy<Artist> { it.songCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            else -> artists.toList()
        }
    }

    private fun sortFoldersList(folders: Iterable<MusicFolder>, sortOption: SortOption): List<MusicFolder> {
        return when (sortOption) {
            SortOption.FolderNameAZ -> folders.sortedWith(
                compareBy<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderNameZA -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSongCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSongCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountAsc -> folders.sortedWith(
                compareBy<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            SortOption.FolderSubdirCountDesc -> folders.sortedWith(
                compareByDescending<MusicFolder> { it.totalSubFolderCount }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.path }
            )
            else -> folders.toList()
        }
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        scope?.launch {
            if (persist && _currentFavoriteSortOption.value.storageKey == sortOption.storageKey) {
                return@launch
            }
            if (persist) {
                userPreferencesRepository.setLikedSongsSortOption(sortOption.storageKey)
            }
            _currentFavoriteSortOption.value = sortOption
        }
    }

    fun updateSong(updatedSong: Song) {
        _allSongs.update { currentList ->
            currentList.map { if (it.id == updatedSong.id) updatedSong else it }.toImmutableList()
        }
    }

    fun removeSong(songId: String) {
        _allSongs.update { currentList ->
            currentList.filter { it.id != songId }.toImmutableList()
        }
    }

    fun setSourceScope(scope: SourceScope) {
        _currentSourceScope.value = scope
        this.scope?.launch {
            userPreferencesRepository.saveLastSourceScope(scope)
        }
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        val shouldReleaseLibraryState =
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        if (!shouldReleaseLibraryState) return

        songsJob?.cancel()
        albumsJob?.cancel()
        artistsJob?.cancel()
        foldersJob?.cancel()
        songsJob = null
        albumsJob = null
        artistsJob = null
        foldersJob = null

        _allSongs.value = persistentListOf()
        _allSongsById.value = emptyMap()
        _albums.value = persistentListOf()
        _artists.value = persistentListOf()
        _musicFolders.value = persistentListOf()
        _isLoadingLibrary.value = false
        _isLoadingCategories.value = false
        needsReloadAfterTrim = true
    }

    fun restoreAfterTrimIfNeeded() {
        if (!needsReloadAfterTrim || scope == null) return
        startObservingLibraryData()
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}
