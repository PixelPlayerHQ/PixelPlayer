package com.theveloper.pixelplay.data.repository

import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import com.theveloper.pixelplay.extensions.core.ExtensionStoreRepository
import com.theveloper.pixelplay.extensions.core.toSong
import dev.brahmkshatriya.echo.common.MusicExtension
import dev.brahmkshatriya.echo.common.clients.HomeFeedClient
import dev.brahmkshatriya.echo.common.clients.LibraryFeedClient
import dev.brahmkshatriya.echo.common.clients.LoginClient
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.loadAll
import dev.brahmkshatriya.echo.common.models.Shelf
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.theveloper.pixelplay.data.model.ExtensionCapabilities

@Singleton
class ExtensionRepository @Inject constructor(
    private val extensionEngine: ExtensionLoader,
    private val host: PixelPlayExtensionHost,
    private val storeRepository: ExtensionStoreRepository
) {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val allExtensions = extensionEngine.all
    val installedMusicExtensions = extensionEngine.music
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val storeItems = combine(
        storeRepository.storeItems,
        _searchQuery
    ) { items, query ->
        if (query.isBlank()) items
        else items.filter { 
            it.remote.name.contains(query, ignoreCase = true) || 
            it.remote.subtitle.contains(query, ignoreCase = true) ||
            it.remote.id.contains(query, ignoreCase = true)
        }
    }.stateIn(
        repositoryScope,
        SharingStarted.WhileSubscribed(5000),
        storeRepository.storeItems.value
    )

    private val _currentMusicExtension = MutableStateFlow<MusicExtension?>(null)
    val currentMusicExtension: StateFlow<MusicExtension?> = _currentMusicExtension.asStateFlow()

    private val _homeFeed = MutableStateFlow<Feed<Shelf>?>(null)
    val homeFeed: StateFlow<Feed<Shelf>?> = _homeFeed.asStateFlow()

    private val _shelves = MutableStateFlow<List<Shelf>>(emptyList())
    val shelves: StateFlow<List<Shelf>> = _shelves.asStateFlow()

    private val _isLoadingFeed = MutableStateFlow(false)
    val isLoadingFeed: StateFlow<Boolean> = _isLoadingFeed.asStateFlow()

    private val _yourMixSongsFromExtension = MutableStateFlow<List<com.theveloper.pixelplay.data.model.Song>>(emptyList())
    val yourMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = _yourMixSongsFromExtension.asStateFlow()

    private val _dailyMixSongsFromExtension = MutableStateFlow<List<com.theveloper.pixelplay.data.model.Song>>(emptyList())
    val dailyMixSongsFromExtension: StateFlow<List<com.theveloper.pixelplay.data.model.Song>> = _dailyMixSongsFromExtension.asStateFlow()

    private val _libraryFeed = MutableStateFlow<Feed<Shelf>?>(null)
    val libraryFeed: StateFlow<Feed<Shelf>?> = _libraryFeed.asStateFlow()

    private val _libraryShelves = MutableStateFlow<List<Shelf>>(emptyList())
    val libraryShelves: StateFlow<List<Shelf>> = _libraryShelves.asStateFlow()

    private val _isLoadingLibraryFeed = MutableStateFlow(false)
    val isLoadingLibraryFeed: StateFlow<Boolean> = _isLoadingLibraryFeed.asStateFlow()

    private val _messages = MutableSharedFlow<dev.brahmkshatriya.echo.common.models.Message>()
    val messages = _messages.asSharedFlow()

    private val _errors = MutableSharedFlow<String>()
    val errors = _errors.asSharedFlow()

    private val _extensionCapabilities = MutableStateFlow<Map<String, ExtensionCapabilities>>(emptyMap())
    val extensionCapabilities: StateFlow<Map<String, ExtensionCapabilities>> = _extensionCapabilities

    init {
        repositoryScope.launch {
            host.messageFlow.collect { _messages.emit(it) }
        }

        repositoryScope.launch {
            host.throwFlow.collect { throwable ->
                _errors.emit(throwable.message ?: "Unknown extension error")
            }
        }

        repositoryScope.launch {
            allExtensions.collectLatest { extensions ->
                val caps = mutableMapOf<String, ExtensionCapabilities>()
                extensions.forEach { ext ->
                    val instance = ext.instance.value().getOrNull()
                    caps[ext.metadata.id] = ExtensionCapabilities(
                        isLoginNeeded = instance is LoginClient,
                        canHomeFeed = instance is HomeFeedClient,
                        canLibraryFeed = instance is LibraryFeedClient
                    )
                }
                _extensionCapabilities.value = caps
            }
        }

        fetchStoreExtensions()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.startsWith("http") || (query.contains("/") && !query.contains(" "))) {
            repositoryScope.launch {
                storeRepository.fetchExtensions(query)
            }
        }
    }

    private val _isLoadingStore = MutableStateFlow(false)
    val isLoadingStore: StateFlow<Boolean> = _isLoadingStore.asStateFlow()

    fun fetchStoreExtensions() {
        repositoryScope.launch {
            _isLoadingStore.value = true
            try {
                storeRepository.fetchExtensions()
            } finally {
                _isLoadingStore.value = false
            }
        }
    }

    fun installExtension(item: com.theveloper.pixelplay.extensions.core.ExtensionStoreItem) {
        repositoryScope.launch {
            storeRepository.downloadAndInstall(item)
        }
    }

    fun selectMusicExtension(extension: MusicExtension) {
        _currentMusicExtension.value = extension
        loadHomeFeed()
        loadLibraryFeed()
    }

    fun loadHomeFeed() {
        val extension = _currentMusicExtension.value ?: return
        repositoryScope.launch {
            val client = extension.instance.value().getOrNull()
            if (client is HomeFeedClient) {
                _isLoadingFeed.value = true
                try {
                    val feed = client.loadHomeFeed()
                    _homeFeed.value = feed
                    _shelves.value = feed.loadAll()
                    extractSongsFromShelves(feed.loadAll(), extension.metadata.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoadingFeed.value = false
                }
            } else {
                _homeFeed.value = null
                _shelves.value = emptyList()
                _yourMixSongsFromExtension.value = emptyList()
                _dailyMixSongsFromExtension.value = emptyList()
            }
        }
    }

    private fun extractSongsFromShelves(shelves: List<Shelf>, extensionId: String) {
        val yourMixTracks = mutableListOf<com.theveloper.pixelplay.data.model.Song>()
        val dailyMixTracks = mutableListOf<com.theveloper.pixelplay.data.model.Song>()

        shelves.getOrNull(0)?.let { firstShelf ->
            when (firstShelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                    yourMixTracks.addAll(firstShelf.list.mapNotNull { (it as? dev.brahmkshatriya.echo.common.models.Track)?.toSong(extensionId) })
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    yourMixTracks.addAll(firstShelf.list.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>().mapNotNull { it.toSong(extensionId) })
                }
                else -> {}
            }
        }

        shelves.getOrNull(1)?.let { secondShelf ->
            when (secondShelf) {
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Tracks -> {
                    dailyMixTracks.addAll(secondShelf.list.mapNotNull { (it as? dev.brahmkshatriya.echo.common.models.Track)?.toSong(extensionId) })
                }
                is dev.brahmkshatriya.echo.common.models.Shelf.Lists.Items -> {
                    dailyMixTracks.addAll(secondShelf.list.filterIsInstance<dev.brahmkshatriya.echo.common.models.Track>().mapNotNull { it.toSong(extensionId) })
                }
                else -> {}
            }
        }

        _yourMixSongsFromExtension.value = yourMixTracks.take(60)
        _dailyMixSongsFromExtension.value = dailyMixTracks.take(30)
    }

    fun loadLibraryFeed() {
        val extension = _currentMusicExtension.value ?: return
        repositoryScope.launch {
            val client = extension.instance.value().getOrNull()
            if (client is LibraryFeedClient) {
                _isLoadingLibraryFeed.value = true
                try {
                    val feed = client.loadLibraryFeed()
                    _libraryFeed.value = feed
                    _libraryShelves.value = feed.loadAll()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _isLoadingLibraryFeed.value = false
                }
            } else {
                _libraryFeed.value = null
                _libraryShelves.value = emptyList()
            }
        }
    }
}
