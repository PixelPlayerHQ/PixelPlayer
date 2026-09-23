package com.theveloper.pixelplay.soundcloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import javax.inject.Inject

data class SoundCloudUiState(
    val section: SoundCloudSection = SoundCloudSection.FEED,
    val query: String = "",
    val clientId: String = "",
    val username: String = "",
    val isSignedIn: Boolean = false,
    val displayName: String = "",
    val isLoading: Boolean = false,
    val results: List<SoundCloudSearchHit> = emptyList(),
    val downloads: List<Song> = emptyList(),
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val browsingPlaylistTitle: String? = null,
    val browsingPlaylistUrl: String? = null,
    /** null = follow device default (tiles on wide/foldable, list on phone). */
    val tilesOverride: Boolean? = null,
    val resolvingKey: String? = null,
    /** True while a playlist (or nested) browse can be popped with system Back. */
    val canNavigateBack: Boolean = false,
    val isRefreshing: Boolean = false,
)

private data class SoundCloudNavFrame(
    val section: SoundCloudSection,
    val results: List<SoundCloudSearchHit>,
    val browsingPlaylistTitle: String?,
    val browsingPlaylistUrl: String?,
    val query: String,
)

@HiltViewModel
class SoundCloudViewModel @Inject constructor(
    private val client: SoundCloudClient,
    private val settings: SoundCloudSettings,
    private val downloader: SoundCloudDownloadService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SoundCloudUiState())
    val uiState: StateFlow<SoundCloudUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadJob: Job? = null
    private val navStack = ArrayDeque<SoundCloudNavFrame>()

    init {
        viewModelScope.launch {
            settings.clientId.collect { id ->
                _uiState.update { it.copy(clientId = id) }
                client.setClientIdOverride(id.ifBlank { null })
            }
        }
        viewModelScope.launch {
            settings.username.collect { user ->
                _uiState.update { it.copy(username = user) }
            }
        }
        viewModelScope.launch {
            settings.session.collect { session ->
                client.setSession(session.oauthToken, session.cookieHeader)
                _uiState.update {
                    it.copy(
                        isSignedIn = session.isSignedIn,
                        displayName = session.displayName.ifBlank { session.permalink },
                    )
                }
            }
        }
        viewModelScope.launch {
            delay(350)
            val state = _uiState.value
            if (state.clientId.isNotBlank()) {
                selectSection(
                    if (state.isSignedIn) SoundCloudSection.FEED else SoundCloudSection.DISCOVER,
                )
            }
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value, errorMessage = null) }
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            if (_uiState.value.section != SoundCloudSection.SEARCH) {
                searchJob?.cancel()
                loadJob?.cancel()
                _uiState.update {
                    it.copy(
                        section = SoundCloudSection.SEARCH,
                        browsingPlaylistTitle = null,
                        browsingPlaylistUrl = null,
                        statusMessage = null,
                    )
                }
            }
            scheduleSearch(value)
            return
        }
        // Cleared / too short while searching — leave search mode empty (don't reload other tabs).
        if (_uiState.value.section == SoundCloudSection.SEARCH) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    statusMessage = null,
                )
            }
        }
    }

    fun setUseTiles(useTiles: Boolean) {
        _uiState.update { it.copy(tilesOverride = useTiles) }
    }

    fun selectSection(section: SoundCloudSection) {
        searchJob?.cancel()
        clearNavStack()
        _uiState.update {
            it.copy(
                section = section,
                browsingPlaylistTitle = null,
                browsingPlaylistUrl = null,
                errorMessage = null,
                statusMessage = null,
                canNavigateBack = false,
                isRefreshing = false,
            )
        }
        when (section) {
            SoundCloudSection.FEED -> loadFeed()
            SoundCloudSection.DISCOVER -> loadDiscover()
            SoundCloudSection.SEARCH -> {
                val q = _uiState.value.query
                if (q.trim().length >= 2) {
                    scheduleSearch(q)
                } else {
                    _uiState.update {
                        it.copy(results = emptyList(), statusMessage = null)
                    }
                }
            }
            SoundCloudSection.LIKES -> loadLikes()
            SoundCloudSection.TRACKS -> loadTracks()
            SoundCloudSection.PLAYLISTS -> loadPlaylists()
            SoundCloudSection.DOWNLOADS -> refreshDownloads()
        }
    }

    fun openPlaylist(hit: SoundCloudSearchHit) {
        if (hit.kind != SoundCloudSearchHit.Kind.PLAYLIST) return
        pushNavFrame()
        runLoad("Opening playlist…") {
            _uiState.update {
                it.copy(
                    browsingPlaylistTitle = hit.title,
                    browsingPlaylistUrl = hit.url,
                    canNavigateBack = navStack.isNotEmpty(),
                )
            }
            client.loadPlaylistTracks(hit.url)
        }
    }

    /** Pull-to-refresh: reload current section / open playlist without leaving the tab. */
    fun refresh() {
        val state = _uiState.value
        if (state.isRefreshing || state.isLoading) return
        val playlistUrl = state.browsingPlaylistUrl
        if (!playlistUrl.isNullOrBlank()) {
            runLoad("Refreshing playlist…", clearResults = false, refreshing = true) {
                client.loadPlaylistTracks(playlistUrl)
            }
            return
        }
        when (state.section) {
            SoundCloudSection.FEED -> runLoad("Refreshing feed…", clearResults = false, refreshing = true) {
                if (!client.hasSession) {
                    throw IllegalStateException("Sign in to SoundCloud to see your personal feed")
                }
                client.loadFeed()
            }
            SoundCloudSection.DISCOVER ->
                runLoad("Refreshing discover…", clearResults = false, refreshing = true) { client.loadDiscover() }
            SoundCloudSection.SEARCH -> {
                val q = state.query.trim()
                if (q.length >= 2) scheduleSearch(q, refreshing = true)
            }
            SoundCloudSection.LIKES -> loadLikes(refreshing = true)
            SoundCloudSection.TRACKS -> loadTracks(refreshing = true)
            SoundCloudSection.PLAYLISTS -> loadPlaylists(refreshing = true)
            SoundCloudSection.DOWNLOADS -> {
                _uiState.update { it.copy(isRefreshing = true) }
                refreshDownloads()
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Pops playlist browse (or nested) without leaving the SoundCloud tab. */
    fun navigateBack(): Boolean {
        val frame = navStack.removeLastOrNull() ?: return false
        loadJob?.cancel()
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                section = frame.section,
                results = frame.results,
                browsingPlaylistTitle = frame.browsingPlaylistTitle,
                browsingPlaylistUrl = frame.browsingPlaylistUrl,
                query = frame.query,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
                statusMessage = null,
                canNavigateBack = navStack.isNotEmpty(),
            )
        }
        return true
    }

    private fun pushNavFrame() {
        val state = _uiState.value
        navStack.addLast(
            SoundCloudNavFrame(
                section = state.section,
                results = state.results,
                browsingPlaylistTitle = state.browsingPlaylistTitle,
                browsingPlaylistUrl = state.browsingPlaylistUrl,
                query = state.query,
            ),
        )
        _uiState.update { it.copy(canNavigateBack = true) }
    }

    private fun clearNavStack() {
        navStack.clear()
    }

    suspend fun resolveToSong(hit: SoundCloudSearchHit) = withContext(Dispatchers.IO) {
        ensureClient()
        client.toSong(client.resolveTrack(hit.url))
    }

    /**
     * Resolves playable tracks from the current results list for shuffle/queue playback.
     * Skips playlists and tracks that fail to resolve (e.g. DRM).
     */
    suspend fun resolveTracksForPlayback(limit: Int = SHUFFLE_TRACK_LIMIT): List<Song> {
        val tracks = _uiState.value.results
            .asSequence()
            .filter { it.kind == SoundCloudSearchHit.Kind.TRACK }
            .take(limit)
            .toList()
        if (tracks.isEmpty()) {
            throw IllegalStateException("No tracks to play — open a playlist or pick a feed with songs")
        }
        setBusy()
        return withContext(Dispatchers.IO) {
            ensureClient()
            val gate = Semaphore(RESOLVE_PARALLELISM)
            coroutineScope {
                tracks.map { hit ->
                    async {
                        gate.withPermit {
                            try {
                                client.toSong(client.resolveTrack(hit.url))
                            } catch (t: Throwable) {
                                Timber.w(t, "Skip unplayable SoundCloud track: %s", hit.url)
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }
        }.also { songs ->
            if (songs.isEmpty()) {
                setIdle(error = "No playable streams in this list")
            }
        }
    }

    fun download(hit: SoundCloudSearchHit) {
        if (hit.kind != SoundCloudSearchHit.Kind.TRACK) {
            _uiState.update { it.copy(errorMessage = "Open the playlist, then download a track") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null, errorMessage = null) }
            try {
                ensureClient()
                val file = withContext(Dispatchers.IO) {
                    val track = client.resolveTrack(hit.url)
                    downloader.downloadToMusicFolder(track)
                }
                refreshDownloads()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = null,
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "SoundCloud download failed")
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = t.message ?: t::class.java.simpleName)
                }
            }
        }
    }

    fun setBusy(key: String? = null) {
        _uiState.update {
            it.copy(isLoading = true, statusMessage = null, errorMessage = null, resolvingKey = key)
        }
    }

    fun setIdle(error: String? = null) {
        _uiState.update {
            it.copy(isLoading = false, statusMessage = null, errorMessage = error, resolvingKey = null)
        }
    }

    private fun refreshDownloads() {
        val songs = downloader.listDownloadedSongs()
        _uiState.update {
            it.copy(
                downloads = songs,
                results = emptyList(),
                isLoading = false,
                statusMessage = null,
            )
        }
    }

    private fun scheduleSearch(rawQuery: String, refreshing: Boolean = false) {
        searchJob?.cancel()
        val query = rawQuery.trim()
        if (query.length < 2) {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    isLoading = false,
                    isRefreshing = false,
                    statusMessage = null,
                    errorMessage = null,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            if (!refreshing) delay(SEARCH_DEBOUNCE_MS)
            runLoad("Searching…", cancelPrevious = false, clearResults = !refreshing, refreshing = refreshing) {
                if (query.contains("soundcloud.com/", ignoreCase = true) &&
                    !query.contains("/sets/", ignoreCase = true) &&
                    !query.contains("/likes", ignoreCase = true)
                ) {
                    val track = client.resolveTrack(query)
                    listOf(
                        SoundCloudSearchHit(
                            url = track.url,
                            title = track.title,
                            artist = track.artist,
                            durationSec = track.durationMs / 1000L,
                            thumbnailUrl = track.artworkUrl,
                        ),
                    )
                } else {
                    client.search(query)
                }
            }
        }
    }

    private fun loadFeed() = runLoad("Loading feed…") {
        if (!client.hasSession) {
            throw IllegalStateException("Sign in to SoundCloud to see your personal feed")
        }
        client.loadFeed()
    }

    private fun loadDiscover() = runLoad("Loading discover…") { client.loadDiscover() }

    private fun loadLikes(refreshing: Boolean = false) = runLoad(
        "Loading likes…",
        clearResults = !refreshing,
        refreshing = refreshing,
    ) {
        if (client.hasSession) {
            client.loadMyLikes()
        } else {
            client.loadUserLikes(_uiState.value.username)
        }
    }

    private fun loadTracks(refreshing: Boolean = false) = runLoad(
        "Loading tracks…",
        clearResults = !refreshing,
        refreshing = refreshing,
    ) {
        if (client.hasSession) {
            client.loadMyTracks()
        } else {
            client.loadUserTracks(_uiState.value.username)
        }
    }

    private fun loadPlaylists(refreshing: Boolean = false) = runLoad(
        "Loading playlists…",
        clearResults = !refreshing,
        refreshing = refreshing,
    ) {
        if (client.hasSession) {
            client.loadMyPlaylists()
        } else {
            client.loadUserPlaylists(_uiState.value.username)
        }
    }

    private fun runLoad(
        @Suppress("UNUSED_PARAMETER") status: String,
        cancelPrevious: Boolean = true,
        clearResults: Boolean = true,
        refreshing: Boolean = false,
        block: suspend () -> List<SoundCloudSearchHit>,
    ) {
        if (cancelPrevious) {
            loadJob?.cancel()
        }
        val job = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    errorMessage = null,
                    statusMessage = null,
                    results = if (clearResults) emptyList() else it.results,
                )
            }
            try {
                ensureClient()
                val hits = withContext(Dispatchers.IO) { block() }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        results = hits,
                        statusMessage = null,
                    )
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.w(t, "SoundCloud load failed")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = t.message ?: t::class.java.simpleName,
                        statusMessage = null,
                    )
                }
            }
        }
        if (cancelPrevious) {
            loadJob = job
        }
    }

    private fun ensureClient() {
        val id = _uiState.value.clientId
        if (id.isBlank()) throw IllegalStateException("Set client_id in Settings → Experimental")
        client.setClientIdOverride(id)
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 350L
        private const val SHUFFLE_TRACK_LIMIT = 30
        private const val RESOLVE_PARALLELISM = 4
    }
}
