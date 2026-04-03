package com.theveloper.pixelplay.presentation.viewmodel

import android.os.Trace
import kotlinx.coroutines.flow.flowOn
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

private const val ENABLE_FOLDERS_STORAGE_FILTER = false

/**
 * Manages the data state of the music library: Songs, Albums, Artists, Folders.
 * Handles loading from Repository and applying SortOptions.
 */
@Singleton
class LibraryStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository
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
    private val _currentStorageFilter = MutableStateFlow(com.theveloper.pixelplay.data.model.StorageFilter.ALL)
    val currentStorageFilter = _currentStorageFilter.asStateFlow()

    private fun effectiveFoldersStorageFilter(
        selectedFilter: com.theveloper.pixelplay.data.model.StorageFilter
    ): com.theveloper.pixelplay.data.model.StorageFilter {
        return if (ENABLE_FOLDERS_STORAGE_FILTER) {
            selectedFilter
        } else {
            com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val songsPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Song>> =
        kotlinx.coroutines.flow.combine(_currentSongSortOption, _currentStorageFilter) { sort, filter ->
            sort to filter
        }.flatMapLatest { (sortOption, filter) ->
            musicRepository.getPaginatedSongs(sortOption, filter)
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
    val favoritesPagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<Song>> =
        kotlinx.coroutines.flow.combine(_currentFavoriteSortOption, _currentStorageFilter) { sort, filter ->
            sort to filter
        }.flatMapLatest { (sortOption, storageFilter) ->
            musicRepository.getPaginatedFavoriteSongs(sortOption, storageFilter)
        }
        .flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val favoriteSongCountFlow: kotlinx.coroutines.flow.Flow<Int> = _currentStorageFilter
        .flatMapLatest { filter -> musicRepository.getFavoriteSongCountFlow(filter) }
        .flowOn(Dispatchers.IO)

    @OptIn(ExperimentalStdlibApi::class)
    val genres: kotlinx.coroutines.flow.Flow<ImmutableList<com.theveloper.pixelplay.data.model.Genre>> = _allSongs
        .map { songs ->
            val genreMap = mutableMapOf<String, MutableList<Song>>()
            val unknownGenreName = "Unknown Genre"

            songs.forEach { song ->
                val genreName = song.genre?.trim()
                if (genreName.isNullOrBlank()) {
                    genreMap.getOrPut(unknownGenreName) { mutableListOf() }.add(song)
                } else {
                    genreMap.getOrPut(genreName) { mutableListOf() }.add(song)
                }
            }

            genreMap.toList().mapIndexedNotNull { index, (genreName, songs) ->
                if (songs.isNotEmpty()) {
                    val id = if (genreName.equals(unknownGenreName, ignoreCase = true)) {
                        "unknown"
                    } else {
                        genreName.lowercase().replace(" ", "_").replace("/", "_")
                    }
                    val lightThemeColor = com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(id, isDark = false)
                    val darkThemeColor = com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(id, isDark = true)

                    com.theveloper.pixelplay.data.model.Genre(
                        id = id,
                        name = genreName,
                        lightColorHex = lightThemeColor.container.toHexString(),
                        onLightColorHex = lightThemeColor.onContainer.toHexString(),
                        darkColorHex = darkThemeColor.container.toHexString(),
                        onDarkColorHex = darkThemeColor.onContainer.toHexString()
                    )
                } else {
                    null
                }
            }
                .distinctBy { it.id }
                .sortedBy { it.name.lowercase() }
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

            // Restore last storage filter (All / Cloud / Local)
            _currentStorageFilter.value = userPreferencesRepository.lastStorageFilterFlow.first()
        }
    }

    fun onCleared() {
        scope = null
    }

    // --- Data Loading ---

    // We observe the repository flows permanently in initialize(), or we start collecting here?
    // Better to start collecting in initialize() or have these functions just be "ensure active".
    // Actually, explicit "load" functions are legacy imperative style.
    // We should launch collectors in initialize() that update the state.

    private var songsJob: Job? = null
    private var albumsJob: Job? = null
    private var artistsJob: Job? = null
    private var foldersJob: Job? = null

    fun startObservingLibraryData() {
        if (songsJob?.isActive == true) return

        Log.d("LibraryStateHolder", "startObservingLibraryData called.")

        songsJob = scope?.launch {
            _isLoadingLibrary.value = true
            musicRepository.getAudioFiles().collect { songs ->
                // Process heavy list conversions on Default dispatcher to avoid blocking UI
                val immutableSongs = withContext(Dispatchers.Default) { songs.toImmutableList() }
                val songsMap = withContext(Dispatchers.Default) { songs.associateBy { it.id } }

                _allSongs.value = immutableSongs
                _allSongsById.value = songsMap

                // When the repository emits a new list (triggered by directory changes),
                // we update our state and re-apply current sorting.
                // Apply sort to the new data
                sortSongs(_currentSongSortOption.value, persist = false)
                _isLoadingLibrary.value = false
            }
        }

        albumsJob = scope?.launch {
            _isLoadingCategories.value = true
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _currentStorageFilter.flatMapLatest { filter ->
                musicRepository.getAlbums(filter)
            }.collect { albums ->
                _albums.value = albums.toImmutableList()
                sortAlbums(_currentAlbumSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }

        artistsJob = scope?.launch {
            _isLoadingCategories.value = true
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _currentStorageFilter.flatMapLatest { filter ->
                musicRepository.getArtists(filter)
            }.collect { artists ->
                _artists.value = artists.toImmutableList()
                sortArtists(_currentArtistSortOption.value, persist = false)
                _isLoadingCategories.value = false
            }
        }

        foldersJob = scope?.launch {
            @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
            _currentStorageFilter.flatMapLatest { filter ->
                musicRepository.getMusicFolders(effectiveFoldersStorageFilter(filter))
            }.collect { folders ->
                _musicFolders.value = folders.toImmutableList()
                sortFolders(_currentFolderSortOption.value, persist = false)
            }
        }
    }

    // Deprecated imperative loaders - redirected to observer start
    fun loadSongsFromRepository() {
        startObservingLibraryData()
    }

    fun loadAlbumsFromRepository() {
        startObservingLibraryData()
    }

    fun loadArtistsFromRepository() {
        startObservingLibraryData()
    }

    fun loadFoldersFromRepository() {
        startObservingLibraryData()
    }

    // --- Lazy Loading Checks ---

    // --- Lazy Loading Checks ---
    // We replace conditional "check if empty" with "ensure observing".
    // If we are already observing, startObservingLibraryData returns early.
    // If we are not (e.g. process death recovery?), it restarts.

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
            // Updating the sort option triggers songsPagingFlow to reload with new sort at DB level
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

            val sorted = when (sortOption) {
                SortOption.AlbumTitleAZ -> _albums.value.sortedWith(
                    compareBy<Album> { it.title.lowercase() }
                        .thenBy { it.artist.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumTitleZA -> _albums.value.sortedWith(
                    compareByDescending<Album> { it.title.lowercase() }
                        .thenBy { it.artist.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumArtist -> _albums.value.sortedWith(
                    compareBy<Album> { it.artist.lowercase() }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumArtistDesc -> _albums.value.sortedWith(
                    compareByDescending<Album> { it.artist.lowercase() }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumReleaseYear -> _albums.value.sortedWith(
                    compareByDescending<Album> { it.year }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumReleaseYearAsc -> _albums.value.sortedWith(
                    compareBy<Album> { it.year }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumDateAdded -> _albums.value.sortedWith(
                    compareByDescending<Album> { it.dateAdded }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumSizeAsc -> _albums.value.sortedWith(
                    compareBy<Album> { it.songCount }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.AlbumSizeDesc -> _albums.value.sortedWith(
                    compareByDescending<Album> { it.songCount }
                        .thenBy { it.title.lowercase() }
                        .thenBy { it.id }
                )
                else -> _albums.value
            }
            _albums.value = sorted.toImmutableList()
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

            val sorted = when (sortOption) {
                SortOption.ArtistNameAZ -> _artists.value.sortedWith(
                    compareBy<Artist> { it.name.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.ArtistNameZA -> _artists.value.sortedWith(
                    compareByDescending<Artist> { it.name.lowercase() }
                        .thenBy { it.id }
                )
                SortOption.ArtistNumSongs -> _artists.value.sortedWith(
                    compareByDescending<Artist> { it.songCount }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.id }
                )
                else -> _artists.value
            }
            _artists.value = sorted.toImmutableList()
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

            val sorted = when (sortOption) {
                SortOption.FolderNameAZ -> _musicFolders.value.sortedWith(
                    compareBy<MusicFolder> { it.name.lowercase() }
                        .thenBy { it.path }
                )
                SortOption.FolderNameZA -> _musicFolders.value.sortedWith(
                    compareByDescending<MusicFolder> { it.name.lowercase() }
                        .thenBy { it.path }
                )
                SortOption.FolderSongCountAsc -> _musicFolders.value.sortedWith(
                    compareBy<MusicFolder> { it.totalSongCount }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.path }
                )
                SortOption.FolderSongCountDesc -> _musicFolders.value.sortedWith(
                    compareByDescending<MusicFolder> { it.totalSongCount }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.path }
                )
                SortOption.FolderSubdirCountAsc -> _musicFolders.value.sortedWith(
                    compareBy<MusicFolder> { it.totalSubFolderCount }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.path }
                )
                SortOption.FolderSubdirCountDesc -> _musicFolders.value.sortedWith(
                    compareByDescending<MusicFolder> { it.totalSubFolderCount }
                        .thenBy { it.name.lowercase() }
                        .thenBy { it.path }
                )
                else -> _musicFolders.value
            }
            _musicFolders.value = sorted.toImmutableList()
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
            // The actual filtering/sorting of favorites happens in ViewModel using this flow
        }
    }

    /**
     * Updates a single song in the in-memory list.
     * Used effectively after metadata edits to reflect changes immediately.
     */
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

    fun setStorageFilter(filter: com.theveloper.pixelplay.data.model.StorageFilter) {
        _currentStorageFilter.value = filter
        scope?.launch {
            userPreferencesRepository.saveLastStorageFilter(filter)
        }
    }
}

private fun androidx.compose.ui.graphics.Color.toHexString(): String {
    return String.format("#%08X", this.toArgb())
}
