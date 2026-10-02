package com.theveloper.pixelplay.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import javax.inject.Inject

data class YoutubeUiState(
    val section: YoutubeSection = YoutubeSection.HOME,
    val query: String = "",
    val filter: YoutubeSearchFilter = YoutubeSearchFilter.SONGS,
    val results: List<YoutubeHit> = emptyList(),
    val shelves: List<YoutubeShelf> = emptyList(),
    val collectionTitle: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class YoutubeViewModel @Inject constructor(
    private val client: YoutubeClient,
    private val musicStore: YoutubeMusicStore,
    private val session: YoutubeSession,
) : ViewModel() {
    private val _uiState = MutableStateFlow(YoutubeUiState())
    val uiState: StateFlow<YoutubeUiState> = _uiState.asStateFlow()
    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    private val _libraryPlaylists = MutableStateFlow<List<YoutubeHit>>(emptyList())
    val libraryPlaylists: StateFlow<List<YoutubeHit>> = _libraryPlaylists.asStateFlow()
    val likedTracks: StateFlow<List<YoutubeHit>> = musicStore.liked
    val recentTracks: StateFlow<List<YoutubeHit>> = musicStore.recent
    val likedSongIds: StateFlow<Set<String>> = musicStore.likedSongIds
    val account: StateFlow<YoutubeAccount> = session.account
    private var searchJob: Job? = null
    private var likedJob: Job? = null
    private var warmUpJob: Job? = null
    private var likedMoreJob: Job? = null
    private var likedContinuation: String? = null
    @Volatile
    private var loadingMoreLiked = false

    init {
        viewModelScope.launch {
            uiState.collect { state ->
                val hits = state.results + state.shelves.flatMap { it.items }
                if (hits.isNotEmpty()) musicStore.rememberHits(hits)
            }
        }
        viewModelScope.launch {
            session.account.collect { account ->
                if (_uiState.value.section == YoutubeSection.LIKED) {
                    loadLiked(refreshing = account.isSignedIn)
                }
            }
        }
    }

    fun openSection(section: YoutubeSection) {
        val current = _uiState.value
        if (current.section == section && current.collectionTitle == null) {
            val hasContent = when (section) {
                YoutubeSection.HOME -> current.shelves.isNotEmpty()
                YoutubeSection.SEARCH -> current.query.trim().length < 2 || current.results.isNotEmpty()
                else -> current.results.isNotEmpty()
            }
            if (hasContent) return
        }
        _uiState.update {
            it.copy(
                section = section,
                collectionTitle = null,
                error = null,
                isLoadingMore = false,
                hasMore = false,
            )
        }
        when (section) {
            YoutubeSection.HOME -> {
                if (client.feedShelves.isNotEmpty()) {
                    _uiState.update { it.copy(shelves = client.feedShelves, isLoading = false) }
                }
                loadFeed(refreshing = client.feedShelves.isNotEmpty())
            }
            YoutubeSection.SEARCH -> {
                if (_uiState.value.query.trim().length >= 2) search(_uiState.value.query)
                else _uiState.update { it.copy(results = emptyList(), shelves = emptyList(), isLoading = false) }
            }
            YoutubeSection.LIKED -> {
                if (client.likedHits.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            results = client.likedHits,
                            isLoading = false,
                            hasMore = likedContinuation != null,
                        )
                    }
                }
                loadLiked(refreshing = client.likedHits.isNotEmpty())
            }
            YoutubeSection.PLAYLISTS -> {
                if (client.playlistHits.isNotEmpty()) {
                    _uiState.update { it.copy(results = client.playlistHits, isLoading = false) }
                }
                loadPlaylists(refreshing = client.playlistHits.isNotEmpty())
            }
            YoutubeSection.RADIO -> {
                if (client.radioHits.isNotEmpty()) {
                    _uiState.update { it.copy(results = client.radioHits, isLoading = false) }
                }
                loadRadioStations(refreshing = client.radioHits.isNotEmpty())
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, error = null, collectionTitle = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            search(query)
        }
    }

    fun setFilter(filter: YoutubeSearchFilter) {
        _uiState.update { it.copy(filter = filter, collectionTitle = null) }
        val query = _uiState.value.query
        if (query.trim().length >= 2) search(query)
    }

    fun refresh() {
        when (_uiState.value.section) {
            YoutubeSection.HOME -> loadFeed(refreshing = true)
            YoutubeSection.SEARCH -> {
                val query = _uiState.value.query
                if (query.trim().length >= 2) search(query, refreshing = true)
            }
            YoutubeSection.LIKED -> loadLiked(refreshing = true)
            YoutubeSection.PLAYLISTS -> loadPlaylists(refreshing = true)
            YoutubeSection.RADIO -> loadRadioStations(refreshing = true)
        }
    }

    fun openCollection(hit: YoutubeHit) {
        if (hit.kind != YoutubeHit.Kind.COLLECTION) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, collectionTitle = hit.title, results = emptyList())
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) { client.loadCollectionTracks(hit.url, limit = 200) }
            }
            loaded.fold(
                onSuccess = { tracks ->
                    _uiState.update { it.copy(isLoading = false, results = tracks) }
                    if (tracks.isEmpty()) {
                        _uiState.update { it.copy(error = "This playlist did not return any tracks.") }
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Couldn't open this playlist",
                        )
                    }
                },
            )
        }
    }

    fun reportError(message: String) {
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    fun closeCollection() {
        val state = _uiState.value
        _uiState.update { it.copy(collectionTitle = null, error = null) }
        if (state.section == YoutubeSection.SEARCH && state.query.trim().length >= 2) {
            search(state.query)
        } else if (state.section == YoutubeSection.HOME) {
            loadFeed()
        } else if (state.section == YoutubeSection.PLAYLISTS) {
            loadPlaylists()
        }
    }

    suspend fun resolvePlayable(hit: YoutubeHit): Song = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(2) {
            val resolved = runCatching {
                val track = client.resolveTrack(hit.url)
                client.toSong(track, hit.url)
            }
            if (resolved.isSuccess) return@withContext resolved.getOrThrow()
            lastError = resolved.exceptionOrNull()
        }
        throw lastError ?: IllegalStateException("Couldn't play this track")
    }

    fun isLiked(hit: YoutubeHit): Boolean {
        val id = YoutubeClient.songIdForUrl(hit.url)
        return id in musicStore.likedSongIds.value || client.likedHits.any { it.url == hit.url }
    }

    fun toggleLike(hit: YoutubeHit) {
        val videoId = YoutubeClient.videoIdFromUrl(hit.url)
        val wasLiked = isLiked(hit)
        val locallyLiked = YoutubeClient.songIdForUrl(hit.url) in musicStore.likedSongIds.value
        if (locallyLiked == wasLiked || !wasLiked) {
            musicStore.toggleLike(hit)
        } else {
            // Liked on the account but not stored locally — drop it from the account list.
            client.likedHits = client.likedHits.filterNot { it.url == hit.url }
            if (_uiState.value.section == YoutubeSection.LIKED) {
                _uiState.update { state ->
                    state.copy(results = state.results.filterNot { it.url == hit.url })
                }
            }
        }
        if (!session.isSignedIn() || videoId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.likeVideo(videoId, like = !wasLiked) }
            }.onFailure { error ->
                // Roll the optimistic change back.
                if (locallyLiked == wasLiked || !wasLiked) musicStore.toggleLike(hit)
                else {
                    client.likedHits = (listOf(hit) + client.likedHits).distinctBy { it.url }
                    if (_uiState.value.section == YoutubeSection.LIKED) {
                        _uiState.update { state ->
                            state.copy(results = (listOf(hit) + state.results).distinctBy { it.url })
                        }
                    }
                }
                _uiState.update {
                    it.copy(error = error.message ?: "Couldn't update the like on YouTube Music")
                }
            }.onSuccess {
                if (wasLiked) {
                    client.likedHits = client.likedHits.filterNot { it.url == hit.url }
                    if (_uiState.value.section == YoutubeSection.LIKED) {
                        _uiState.update { state ->
                            state.copy(results = state.results.filterNot { it.url == hit.url })
                        }
                    }
                } else {
                    client.likedHits = (listOf(hit) + client.likedHits).distinctBy { it.url }
                }
            }
        }
    }

    fun ensureLibraryPlaylists() {
        if (_libraryPlaylists.value.isNotEmpty() || client.playlistHits.isNotEmpty()) {
            if (_libraryPlaylists.value.isEmpty()) _libraryPlaylists.value = client.playlistHits
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.loadLibraryPlaylists() }
            }.onSuccess { hits ->
                client.playlistHits = hits
                _libraryPlaylists.value = hits
            }
        }
    }
    fun addHitsToPlaylist(playlist: YoutubeHit, hits: List<YoutubeHit>) {
        val playlistId = YoutubeClient.playlistIdFromUrl(playlist.url)
        val videoIds = hits.map { YoutubeClient.videoIdFromUrl(it.url) }.filter { it.length == 11 }
        if (playlistId.isBlank() || videoIds.isEmpty()) {
            _uiState.update { it.copy(error = "Couldn't add those songs to the playlist.") }
            return
        }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { client.addVideosToPlaylist(playlistId, videoIds) }
            }.onSuccess {
                _notices.emit("Added ${videoIds.size} to ${playlist.title}")
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = error.message ?: "Couldn't add songs to that playlist")
                }
            }
        }
    }

    fun createPlaylistAndAdd(title: String, hits: List<YoutubeHit>) {
        val videoIds = hits.map { YoutubeClient.videoIdFromUrl(it.url) }.filter { it.length == 11 }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val id = client.createPlaylist(title, videoIds)
                    client.loadLibraryPlaylists()
                    id
                }
            }.onSuccess {
                val refreshed = runCatching {
                    withContext(Dispatchers.IO) { client.loadLibraryPlaylists() }
                }.getOrDefault(client.playlistHits)
                client.playlistHits = refreshed
                _libraryPlaylists.value = refreshed
                _notices.emit("Created $title")
                if (_uiState.value.section == YoutubeSection.PLAYLISTS) loadPlaylists(refreshing = true)
            }.onFailure { error ->
                _uiState.update {
                    it.copy(error = error.message ?: "Couldn't create the playlist")
                }
            }
        }
    }

    fun rememberPlayed(song: Song) {
        musicStore.rememberRecent(song)
    }

    fun importSession(cookieHeader: String, visitorData: String = "", dataSyncId: String = ""): Boolean {
        val normalized = cookieHeader.trim()
        val signedIn = listOf("SAPISID", "__Secure-1PAPISID", "__Secure-3PAPISID")
            .any { key -> normalized.contains("$key=", ignoreCase = true) }
        if (!signedIn) return false
        session.save(normalized, visitorData = visitorData, dataSyncId = dataSyncId)
        clearCaches()
        val epoch = session.generation()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { client.refreshIdentity() }
                val name = runCatching { client.loadAccountName() }.getOrDefault("")
                if (name.isNotBlank() && session.generation() == epoch) session.updateIdentity(displayName = name)
            }
            if (session.generation() != epoch) return@launch
            when (_uiState.value.section) {
                YoutubeSection.LIKED -> loadLiked(refreshing = true)
                YoutubeSection.HOME -> loadFeed(refreshing = true)
                YoutubeSection.PLAYLISTS -> loadPlaylists(refreshing = true)
                YoutubeSection.RADIO -> loadRadioStations(refreshing = true)
                else -> Unit
            }
        }
        return true
    }

    private fun clearCaches() {
        warmUpJob?.cancel()
        likedMoreJob?.cancel()
        likedContinuation = null
        loadingMoreLiked = false
        client.resetIdentity()
        client.likedHits = emptyList()
        client.feedShelves = emptyList()
        client.playlistHits = emptyList()
        _libraryPlaylists.value = emptyList()
        client.radioHits = emptyList()
    }

    fun signOut() {
        likedJob?.cancel()
        session.clear()
        clearCaches()
        _uiState.update {
            it.copy(error = null, results = emptyList(), isLoading = false, isRefreshing = false)
        }
        when (_uiState.value.section) {
            YoutubeSection.LIKED -> loadLiked(refreshing = false)
            YoutubeSection.HOME -> loadFeed(refreshing = true)
            else -> Unit
        }
    }

    private fun loadLiked(refreshing: Boolean = false) {
        likedJob?.cancel()
        likedMoreJob?.cancel()
        likedContinuation = null
        loadingMoreLiked = false
        val epoch = session.generation()
        likedJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.results.isEmpty(),
                    isRefreshing = refreshing,
                    isLoadingMore = false,
                    hasMore = false,
                    error = null,
                )
            }
            val local = musicStore.liked.value
            val remote = if (session.isSignedIn() && session.generation() == epoch) {
                try {
                    Result.success(withContext(Dispatchers.IO) { client.loadLikedPage() })
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            } else {
                Result.success(YoutubePage(emptyList(), null))
            }
            if (session.generation() != epoch) return@launch
            remote.fold(
                onSuccess = { page ->
                    if (session.generation() != epoch) return@launch
                    // Keep YouTube Music's newest-first order. Local likes only fill in
                    // when the account list is empty so they cannot jump the queue.
                    val account = if (session.isSignedIn()) page.hits else emptyList()
                    val rows = account.ifEmpty { local }
                    client.likedHits = account
                    likedContinuation = page.continuation
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            results = rows,
                            hasMore = page.continuation != null,
                        )
                    }
                },
                onFailure = { error ->
                    if (session.generation() != epoch || !session.isSignedIn()) {
                        _uiState.update {
                            it.copy(isLoading = false, isRefreshing = false, error = null, results = local)
                        }
                        return@fold
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            results = local,
                            error = error.message ?: "Couldn't load liked music",
                        )
                    }
                },
            )
        }
    }

    /** Appends the next page of Liked Music; safe to call repeatedly while scrolling. */
    fun loadMoreLiked() {
        val state = _uiState.value
        if (state.section != YoutubeSection.LIKED || state.collectionTitle != null) return
        val token = likedContinuation ?: return
        // Set synchronously: several rows near the end ask for the next page at once.
        if (loadingMoreLiked) return
        loadingMoreLiked = true
        val epoch = session.generation()
        likedMoreJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            val page = try {
                Result.success(withContext(Dispatchers.IO) { client.loadLikedPage(token) })
            } catch (cancelled: CancellationException) {
                loadingMoreLiked = false
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            loadingMoreLiked = false
            if (session.generation() != epoch) return@launch
            page.fold(
                onSuccess = { loaded ->
                    val appended = loaded.hits.filter { hit ->
                        client.likedHits.none { existing -> existing.url == hit.url }
                    }
                    likedContinuation = loaded.continuation.takeIf { appended.isNotEmpty() }
                    client.likedHits = client.likedHits + appended
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            hasMore = likedContinuation != null,
                            results = (it.results + appended).distinctBy { hit -> hit.url },
                        )
                    }
                },
                onFailure = {
                    // Keep what is already on screen; the next scroll can retry.
                    _uiState.update { it.copy(isLoadingMore = false) }
                },
            )
        }
    }

    /**
     * Resolves the first few tracks of a list in the background so tapping one starts
     * playing without waiting for the stream lookup. The first resolve also primes the
     * extractor's player-script cache, which is what makes the very first tap slow.
     */
    fun warmUpTracks(hits: List<YoutubeHit>) {
        val tracks = hits.asSequence()
            .filter { it.kind == YoutubeHit.Kind.TRACK }
            .take(WARM_UP_COUNT)
            .toList()
        if (tracks.isEmpty()) return
        warmUpJob?.cancel()
        warmUpJob = viewModelScope.launch(Dispatchers.IO) {
            tracks.forEach { hit ->
                if (!isActive) return@launch
                runCatching { client.prefetchTrack(hit.url) }
            }
        }
    }

    fun songsFromContext(start: YoutubeHit, source: List<YoutubeHit>): List<Song> {
        return YoutubePlaybackQueue.remainingTracks(start, source).map(client::listSong)
    }

    fun listSong(hit: YoutubeHit): Song = client.listSong(hit)

    fun prefetchAfter(start: YoutubeHit, queue: List<YoutubeHit>, onSong: (Song) -> Unit) {
        val startIndex = queue.indexOfFirst { it.url == start.url }
        if (startIndex < 0) return
        val upcoming = queue.drop(startIndex + 1)
            .filter { it.kind == YoutubeHit.Kind.TRACK }
            .take(4)
        viewModelScope.launch {
            upcoming.forEach { hit ->
                val song = runCatching { resolvePlayable(hit) }.getOrNull() ?: return@forEach
                onSong(song)
            }
        }
    }

    suspend fun radioTracks(seed: YoutubeHit): List<YoutubeHit> = withContext(Dispatchers.IO) {
        client.loadRadio(seed.url)
    }

    private fun loadFeed(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing && it.shelves.isEmpty(),
                    isRefreshing = refreshing,
                    error = null,
                    collectionTitle = null,
                )
            }
            val built = runCatching {
                withContext(Dispatchers.IO) {
                    val signedIn = session.isSignedIn()
                    // Signed in: the real YouTube Music home (Quick picks, Listen again, Mixed for you...).
                    val home = if (signedIn) runCatching { client.loadHome() }.getOrElse { emptyList() } else emptyList()
                    val liked = if (signedIn) {
                        runCatching { client.loadLikedMusic(24) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    val yours = liked.ifEmpty { musicStore.recent.value }
                    val mix = yours.ifEmpty {
                        if (signedIn) emptyList()
                        else runCatching { client.loadTrendingMusic(18) }.getOrDefault(emptyList())
                    }
                    val followed = if (signedIn && home.isEmpty() && yours.isEmpty()) {
                        runCatching { client.loadSubscriptionFeed(12) }.getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }
                    val radioSeeds = (liked.ifEmpty { home.flatMap { it.items } }.filter { it.kind == YoutubeHit.Kind.TRACK })
                        .ifEmpty { musicStore.recent.value }
                    listOfNotNull(
                        mix.takeIf { it.isNotEmpty() && (home.isEmpty() || liked.isNotEmpty()) }?.let {
                            YoutubeShelf(
                                id = "yours",
                                title = when {
                                    liked.isNotEmpty() -> "Your likes"
                                    signedIn -> "For you"
                                    else -> "Trending songs"
                                },
                                items = it,
                            )
                        },
                    ) + home + listOfNotNull(
                        radioSeeds.shuffled().take(12).takeIf { it.isNotEmpty() && signedIn }?.let {
                            YoutubeShelf("radio", "Radio", it, startsRadio = true)
                        },
                        followed.takeIf { it.isNotEmpty() }?.let {
                            YoutubeShelf("followed", "From your feed", it)
                        },
                    )
                }
            }
            built.fold(
                onSuccess = { shelves ->
                    client.feedShelves = shelves
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, shelves = shelves) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Couldn't load the feed",
                        )
                    }
                },
            )
        }
    }

    private fun loadPlaylists(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !refreshing && it.results.isEmpty(), isRefreshing = refreshing, error = null, shelves = emptyList())
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    if (session.isSignedIn()) {
                        // The user's own YouTube Music library, never generic search results.
                        client.loadLibraryPlaylists()
                    } else {
                        listOf("today's hits", "workout", "focus", "party").flatMap { query ->
                            runCatching { client.search(query, YoutubeSearchFilter.PLAYLISTS, 6) }.getOrDefault(emptyList())
                        }.distinctBy { it.url }
                    }
                }
            }
            loaded.fold(
                onSuccess = { hits ->
                    client.playlistHits = hits
                    _libraryPlaylists.value = hits
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message ?: "Couldn't load playlists")
                    }
                },
            )
        }
    }

    private fun loadRadioStations(refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = !refreshing && it.results.isEmpty(), isRefreshing = refreshing, error = null, shelves = emptyList())
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) {
                    val yours = if (session.isSignedIn()) {
                        client.loadLikedMusic(16)
                    } else {
                        emptyList()
                    }.ifEmpty { musicStore.recent.value }.shuffled()
                    yours.ifEmpty {
                        if (session.isSignedIn()) emptyList() else client.loadTrendingMusic(16)
                    }
                }
            }
            loaded.fold(
                onSuccess = { hits ->
                    client.radioHits = hits
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message ?: "Couldn't load radio")
                    }
                },
            )
        }
    }

    private fun search(query: String, refreshing: Boolean = false) {
        val filter = _uiState.value.filter
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    error = null,
                    collectionTitle = null,
                )
            }
            val loaded = runCatching {
                withContext(Dispatchers.IO) { client.search(query, filter) }
            }
            if (_uiState.value.query.trim() != query.trim()) return@launch
            loaded.fold(
                onSuccess = { hits ->
                    _uiState.update { it.copy(isLoading = false, isRefreshing = false, results = hits) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message ?: "Search failed",
                        )
                    }
                },
            )
        }
    }

    private companion object {
        const val WARM_UP_COUNT = 4
    }
}
