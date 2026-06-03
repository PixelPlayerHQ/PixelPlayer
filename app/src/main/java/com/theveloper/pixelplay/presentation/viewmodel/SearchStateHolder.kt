package com.theveloper.pixelplay.presentation.viewmodel

import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.SearchHistoryItem
import com.theveloper.pixelplay.data.model.SearchResultItem
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.extensions.core.toSong
import com.theveloper.pixelplay.extensions.core.toAppAlbum
import com.theveloper.pixelplay.extensions.core.toAppPlaylist
import dev.brahmkshatriya.echo.common.clients.SearchFeedClient
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import timber.log.Timber
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages search state and operations.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - Search query execution
 * - Search filter management
 * - Search history CRUD operations
 */
@Singleton
class SearchStateHolder @Inject constructor(
    private val musicRepository: MusicRepository,
    private val extensionEngine: dev.brahmkshatriya.echo.extension.loader.ExtensionLoader,
    private val extensionRepository: com.theveloper.pixelplay.data.repository.ExtensionRepository
) {
    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
    }

    private data class SearchRequest(
        val query: String,
        val requestId: Long,
    )

    // Search State
    private val _searchResults = MutableStateFlow<ImmutableList<SearchResultItem>>(persistentListOf())
    val searchResults = _searchResults.asStateFlow()

    private val _selectedSearchFilter = MutableStateFlow(SearchFilterType.ALL)
    val selectedSearchFilter = _selectedSearchFilter.asStateFlow()

    private val _searchHistory = MutableStateFlow<ImmutableList<SearchHistoryItem>>(persistentListOf())
    val searchHistory = _searchHistory.asStateFlow()

    private val searchRequests = MutableSharedFlow<SearchRequest>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val latestSearchRequestId = AtomicLong(0L)

    private var scope: CoroutineScope? = null
    private var searchJob: Job? = null

    /**
     * Initialize with ViewModel scope.
     */
    fun initialize(scope: CoroutineScope) {
        this.scope = scope
        observeSearchRequests()
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchRequests() {
        searchJob?.cancel()
        searchJob = scope?.launch {
            searchRequests
                .debounce(SEARCH_DEBOUNCE_MS)
                .collectLatest { request ->
                    val normalizedQuery = request.query

                    if (normalizedQuery.isBlank()) {
                        if (_searchResults.value.isNotEmpty()) {
                            _searchResults.value = persistentListOf()
                        }
                        return@collectLatest
                    }

                    try {
                        val currentFilter = _selectedSearchFilter.value
                        
                        // Query extensions in parallel
                        val extensionResultsDeferred = extensionEngine.all.value
                            .map { ext ->
                                scope?.async(Dispatchers.IO) {
                                    try {
                                        val client = ext.instance.value().getOrNull()
                                        if (client is SearchFeedClient) {
                                            val extensionId = ext.metadata.id
                                            val feed = client.loadSearchFeed(normalizedQuery)
                                            val shelves = feed.getPagedData(feed.tabs.firstOrNull()).pagedData.loadAll()
                                            shelves.flatMap { shelf ->
                                                when (shelf) {
                                                    is dev.brahmkshatriya.echo.common.models.Shelf.Lists<*> -> {
                                                        shelf.list.mapNotNull { item ->
                                                            when (item) {
                                                                is dev.brahmkshatriya.echo.common.models.Track -> SearchResultItem.SongItem(item.toSong(extensionId))
                                                                is dev.brahmkshatriya.echo.common.models.Album -> SearchResultItem.AlbumItem(item.toAppAlbum(extensionId))
                                                                is dev.brahmkshatriya.echo.common.models.Playlist -> SearchResultItem.PlaylistItem(item.toAppPlaylist(extensionId))
                                                                else -> null
                                                            }
                                                        }
                                                    }
                                                    is dev.brahmkshatriya.echo.common.models.Shelf.Item -> {
                                                        val item = shelf.media
                                                        when (item) {
                                                            is dev.brahmkshatriya.echo.common.models.Track -> listOf(SearchResultItem.SongItem(item.toSong(extensionId)))
                                                            is dev.brahmkshatriya.echo.common.models.Album -> listOf(SearchResultItem.AlbumItem(item.toAppAlbum(extensionId)))
                                                            is dev.brahmkshatriya.echo.common.models.Playlist -> listOf(SearchResultItem.PlaylistItem(item.toAppPlaylist(extensionId)))
                                                            else -> emptyList()
                                                        }
                                                    }
                                                    else -> emptyList()
                                                }
                                            }
                                        } else emptyList()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error searching extension: ${ext.metadata.name}")
                                        emptyList<SearchResultItem>()
                                    }
                                }
                            }

                        musicRepository.searchAll(normalizedQuery, currentFilter).collect { resultsList ->
                            val extensionResults = extensionResultsDeferred.mapNotNull { it?.await() }.flatten()
                            
                            val activeExtensionId = extensionRepository.currentMusicExtension.value?.metadata?.id
                            
                            // Split extension results into active vs others
                            val activeExtResults = extensionResults.filter { 
                                (it is SearchResultItem.SongItem && it.song.extensionId == activeExtensionId) ||
                                (it is SearchResultItem.AlbumItem && it.album.extensionId == activeExtensionId) ||
                                (it is SearchResultItem.PlaylistItem && it.playlist.extensionId == activeExtensionId)
                            }
                            val otherExtResults = extensionResults.filter { it !in activeExtResults }

                            // Combine: 1. Active Extension, 2. Local Library, 3. Other Extensions
                            val combinedResults = activeExtResults + resultsList + otherExtResults

                            // Sort primarily by source group, then by type
                            val sortedResults = combinedResults.sortedWith { a, b ->
                                val scoreA = when {
                                    a in activeExtResults -> 0
                                    a in resultsList -> 1
                                    else -> 2
                                }
                                val scoreB = when {
                                    b in activeExtResults -> 0
                                    b in resultsList -> 1
                                    else -> 2
                                }
                                
                                if (scoreA != scoreB) scoreA.compareTo(scoreB)
                                else {
                                    val typeA = when (a) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                    }
                                    val typeB = when (b) {
                                        is SearchResultItem.SongItem -> 0
                                        is SearchResultItem.AlbumItem -> 1
                                        is SearchResultItem.ArtistItem -> 2
                                        is SearchResultItem.PlaylistItem -> 3
                                    }
                                    typeA.compareTo(typeB)
                                }
                            }

                            if (request.requestId != latestSearchRequestId.get()) {
                                return@collect
                            }

                            val immutableResults = sortedResults.toImmutableList()
                            if (_searchResults.value != immutableResults) {
                                _searchResults.value = immutableResults
                            }
                        }
                    } catch (_: CancellationException) {
                        // Superseded by a newer query; ignore.
                    } catch (e: Exception) {
                        if (request.requestId == latestSearchRequestId.get()) {
                            Timber.e(e, "Error performing search for query: $normalizedQuery")
                            _searchResults.value = persistentListOf()
                        }
                    }
                }
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        _selectedSearchFilter.value = filterType
    }

    fun loadSearchHistory(limit: Int = 15) {
        scope?.launch {
            try {
                val history = withContext(Dispatchers.IO) {
                    musicRepository.getRecentSearchHistory(limit)
                }
                _searchHistory.value = history.toImmutableList()
            } catch (e: Exception) {
                Timber.e(e, "Error loading search history")
            }
        }
    }

    fun onSearchQuerySubmitted(query: String) {
        scope?.launch {
            if (query.isNotBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        musicRepository.addSearchHistoryItem(query)
                    }
                    loadSearchHistory()
                } catch (e: Exception) {
                    Timber.e(e, "Error adding search history item")
                }
            }
        }
    }

    fun performSearch(query: String) {
        val normalizedQuery = query.trim()

        val requestId = latestSearchRequestId.incrementAndGet()

        if (normalizedQuery.isBlank()) {
            if (_searchResults.value.isNotEmpty()) {
                _searchResults.value = persistentListOf()
            }
        }

        searchRequests.tryEmit(SearchRequest(normalizedQuery, requestId))
    }

    fun deleteSearchHistoryItem(query: String) {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.deleteSearchHistoryItemByQuery(query)
                }
                loadSearchHistory()
            } catch (e: Exception) {
                Timber.e(e, "Error deleting search history item")
            }
        }
    }

    fun clearSearchHistory() {
        scope?.launch {
            try {
                withContext(Dispatchers.IO) {
                    musicRepository.clearSearchHistory()
                }
                _searchHistory.value = persistentListOf()
            } catch (e: Exception) {
                Timber.e(e, "Error clearing search history")
            }
        }
    }

    fun onCleared() {
        searchJob?.cancel()
        scope = null
    }
}
