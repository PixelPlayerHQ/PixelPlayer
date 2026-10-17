package com.theveloper.pixelplay.presentation.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.download.CollectionInfo
import com.theveloper.pixelplay.data.download.DownloadBackendApi
import com.theveloper.pixelplay.data.download.DownloadJob
import com.theveloper.pixelplay.data.download.DownloadJson
import com.theveloper.pixelplay.data.download.DownloadRepository
import com.theveloper.pixelplay.data.download.DownloadSettings
import com.theveloper.pixelplay.data.download.DownloadWorker
import com.theveloper.pixelplay.data.download.ExtTrack
import com.theveloper.pixelplay.data.download.FeedItem
import com.theveloper.pixelplay.data.download.FeedSection
import com.theveloper.pixelplay.data.download.InstalledExtension
import com.theveloper.pixelplay.data.download.JobStatus
import com.theveloper.pixelplay.data.download.collectionTypeOf
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shape of GetExtensionPendingAuthJSON / GetAllPendingAuthRequestsJSON
 * (SpotiFLAC go_backend exports_extensions.go): {"extension_id","auth_url",
 * "callback_url"}. There is no prompt text — the auth_url IS the prompt.
 */
@Serializable
private data class PendingAuth(
    @SerialName("extension_id") val extensionId: String = "",
    @SerialName("auth_url") val authUrl: String = "",
    @SerialName("callback_url") val callbackUrl: String = "",
)

@HiltViewModel
class DownloadViewModel @Inject constructor(
    private val backend: DownloadBackendApi,
    private val repository: DownloadRepository,
    private val settings: DownloadSettings,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val results: List<ExtTrack> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
        val jobs: List<DownloadJob> = emptyList(),
        val safFolderSet: Boolean = false,
        val authRequestId: String? = null,
        val authPrompt: String? = null,
        val downloadProviders: List<InstalledExtension> = emptyList(),
        /** Total installed extensions regardless of type (for sheet messaging). */
        val installedAll: List<InstalledExtension> = emptyList(),
        /** Tracks awaiting the extension/quality picker sheet; empty when hidden. */
        val sheetTracks: List<ExtTrack> = emptyList(),
        /** Sheet subtitle: the track name, or the album/playlist name for "download all". */
        val sheetTitle: String = "",
        /** Last provider/quality the user downloaded with; preselected in the sheet. */
        val lastProvider: String? = null,
        val lastQuality: String? = null,
        /** Catalog shown before the user searches (extension getHomeFeed). */
        val feed: List<FeedSection> = emptyList(),
        val feedProvider: String? = null,
        val feedLoading: Boolean = false,
        /** Extensions with customSearch (manifest searchBehavior); picked one drives search. */
        val searchProviders: List<InstalledExtension> = emptyList(),
        val searchProvider: String? = null,
        val searchFilter: String? = null,
        /** Album/artist/playlist pages opened from results; last one is shown. */
        val details: List<Detail> = emptyList(),
    ) {
        val searchExtension: InstalledExtension? get() = searchProviders.firstOrNull { it.id == searchProvider }
    }

    data class Detail(
        val type: String,
        val id: String,
        val providerId: String,
        val title: String,
        val subtitle: String = "",
        val coverUrl: String? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val tracks: List<ExtTrack> = emptyList(),
        val albums: List<CollectionInfo> = emptyList(),
    )

    private val _ui = MutableStateFlow(
        UiState(
            safFolderSet = settings.safTreeUri != null,
            lastProvider = settings.defaultProvider,
            lastQuality = settings.defaultQuality,
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        loadDownloadProviders()
        loadFeed()
        viewModelScope.launch {
            repository.jobs.collect { jobs ->
                _ui.update { it.copy(jobs = jobs, safFolderSet = settings.safTreeUri != null) }
                // Surface the auth dialog whenever a download fails with
                // errorType == verification_required (e.g. Spotify/Amazon need
                // login verification before downloading).
                checkPendingAuth()
            }
        }
    }

    // Clearing the query brings the catalog back.
    fun onQueryChange(query: String) =
        _ui.update { it.copy(query = query, results = if (query.isBlank()) emptyList() else it.results, error = null) }

    /** Loads the catalog from a homeFeed extension, preferring a search (non-download) one. */
    fun loadFeed() {
        if (_ui.value.feedLoading) return
        _ui.update { it.copy(feedLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val candidates = runCatching { backend.listInstalled() }.getOrDefault(emptyList())
                .filter { it.enabled && it.error == null && it.hasHomeFeed }
                .sortedBy { it.isDownloadProvider }
            for (ext in candidates) {
                val sections = backend.homeFeed(ext.id).sections
                    .map { section -> section.copy(items = section.items.filter { it.name.isNotBlank() }) }
                    .filter { it.items.isNotEmpty() }
                if (sections.isNotEmpty()) {
                    _ui.update { it.copy(feed = sections, feedProvider = ext.id, feedLoading = false) }
                    return@launch
                }
            }
            _ui.update { it.copy(feedLoading = false) }
        }
    }

    /** Tracks open the download sheet; albums/playlists/artists open their page. */
    fun openFeedItem(item: FeedItem) {
        val provider = item.providerId?.takeIf { it.isNotBlank() } ?: _ui.value.feedProvider.orEmpty()
        val type = collectionTypeOf(item.type)
        if (type == null) {
            requestDownload(item.toTrack(provider))
        } else {
            openDetail(Detail(type, item.id, provider, item.name, item.artists, item.coverUrl))
        }
    }

    /** Search result tap: collections open their page, tracks open the download sheet. */
    fun openResult(track: ExtTrack) {
        val type = track.collectionType ?: return requestDownload(track)
        openDetail(Detail(type, track.id, track.providerId, track.name, track.artists, track.coverUrl))
    }

    fun openAlbum(album: CollectionInfo, providerId: String) =
        openDetail(Detail("album", album.id, album.providerId?.takeIf { it.isNotBlank() } ?: providerId, album.name, album.artists, album.coverUrl))

    fun closeDetail() = _ui.update { it.copy(details = it.details.dropLast(1)) }

    private fun openDetail(detail: Detail) {
        _ui.update { it.copy(details = it.details + detail) }
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = runCatching { backend.providerMetadata(detail.providerId, detail.type, detail.id) }
                .map { meta ->
                    val info = meta.albumInfo ?: meta.playlistInfo ?: meta.artistInfo
                    detail.copy(
                        loading = false,
                        title = info?.name?.takeIf { it.isNotBlank() } ?: detail.title,
                        subtitle = info?.artists?.takeIf { it.isNotBlank() } ?: detail.subtitle,
                        coverUrl = info?.coverUrl?.takeIf { it.isNotBlank() } ?: detail.coverUrl,
                        // Artist pages: top tracks, then albums + singles/releases.
                        tracks = (meta.trackList.ifEmpty { meta.topTracks })
                            .map { if (it.providerId.isBlank()) it.copy(providerId = detail.providerId) else it },
                        albums = (meta.albums + meta.releases).distinctBy { it.id },
                    )
                }
                .getOrElse { detail.copy(loading = false, error = it.message ?: context.getString(R.string.download_no_results)) }
            // Replace the matching page only if the user is still on it (or under it).
            _ui.update { state -> state.copy(details = state.details.map { if (it === detail) loaded else it }) }
        }
    }

    private fun loadDownloadProviders() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = runCatching { backend.listInstalled() }.getOrDefault(emptyList())
            val providers = installed.filter { it.isDownloadProvider && it.enabled && it.error == null }
            val searchable = installed.filter { it.hasCustomSearch && it.enabled && it.error == null }
            _ui.update { state ->
                // Keep the saved/selected source while it is still installed; else the primary one.
                val current = state.searchProvider ?: settings.searchProvider
                val provider = searchable.firstOrNull { it.id == current }
                    ?: searchable.firstOrNull { it.searchBehavior?.primary == true }
                    ?: searchable.firstOrNull()
                state.copy(
                    downloadProviders = providers,
                    installedAll = installed,
                    searchProviders = searchable,
                    searchProvider = provider?.id,
                    searchFilter = state.searchFilter?.takeIf { f -> provider?.searchBehavior?.filters?.any { it.id == f } == true }
                        ?: provider?.searchBehavior?.filters?.firstOrNull()?.id,
                )
            }
        }
    }

    fun selectSearchProvider(id: String) {
        val ext = _ui.value.searchProviders.firstOrNull { it.id == id } ?: return
        settings.searchProvider = id
        _ui.update { it.copy(searchProvider = id, searchFilter = ext.searchBehavior?.filters?.firstOrNull()?.id) }
        rerunSearch()
    }

    fun selectSearchFilter(id: String) {
        if (_ui.value.searchFilter == id) return
        _ui.update { it.copy(searchFilter = id) }
        rerunSearch()
    }

    private fun rerunSearch() {
        val query = _ui.value.query
        if (query.isNotBlank()) search(query)
    }

    /** Tap download: refresh provider list, then open the picker sheet. */
    fun requestDownload(track: ExtTrack) = requestDownloadAll(listOf(track), track.name)

    fun requestDownloadAll(tracks: List<ExtTrack>, title: String) {
        if (tracks.isEmpty()) return
        loadDownloadProviders()
        _ui.update { it.copy(sheetTracks = tracks, sheetTitle = title) }
    }

    fun dismissDownloadSheet() = _ui.update { it.copy(sheetTracks = emptyList()) }

    fun enqueue(tracks: List<ExtTrack>, quality: String, providerId: String) {
        _ui.update { it.copy(sheetTracks = emptyList()) }
        if (settings.safTreeUri == null) {
            _ui.update { it.copy(error = context.getString(R.string.download_pick_folder_first)) }
            return
        }
        settings.defaultQuality = quality
        settings.defaultProvider = providerId
        _ui.update { it.copy(lastProvider = providerId, lastQuality = quality) }
        tracks.forEach { repository.enqueue(it, quality, providerId) }
        DownloadWorker.enqueue(context)
    }

    fun search(query: String) = viewModelScope.launch(Dispatchers.IO) {
        if (query.isBlank()) return@launch
        val state = _ui.value
        _ui.update { it.copy(query = query, searching = true, details = emptyList()) }
        // Extension search honours the chosen filter (songs/albums/artists); otherwise the
        // combined metadata search, which only returns tracks.
        val provider = state.searchExtension
        val result = runCatching {
            if (provider != null) backend.customSearch(provider.id, query, state.searchFilter)
            else backend.search(query)
        }
        val items = result.getOrDefault(emptyList())
        _ui.update {
            it.copy(
                results = items,
                searching = false,
                error = when {
                    result.isFailure -> result.exceptionOrNull()?.message ?: context.getString(R.string.download_no_results)
                    items.isEmpty() -> context.getString(R.string.download_no_results)
                    else -> null
                },
            )
        }
    }

    fun onFolderPicked(uri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settings.safTreeUri = uri
        _ui.update { it.copy(safFolderSet = true) }
    }

    /** Called when a FAILED job with errorType "verification_required" appears. */
    fun checkPendingAuth() {
        val state = _ui.value
        if (state.authRequestId != null) return
        val job = state.jobs.firstOrNull {
            it.status == JobStatus.FAILED && it.errorType == "verification_required"
        } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            // Verification belongs to the download extension, not the one the track was found with.
            val extId = job.downloadProvider.ifEmpty { job.track.providerId }
            val pending = if (extId.isNotEmpty()) {
                decodePendingAuth(backend.pendingAuth(extId))
            } else {
                decodeAllPendingAuth().firstOrNull { it.authUrl.isNotEmpty() }
            }
            if (pending != null && pending.authUrl.isNotEmpty()) {
                _ui.update {
                    it.copy(authRequestId = pending.extensionId.ifEmpty { extId }, authPrompt = pending.authUrl)
                }
            }
        }
    }

    private fun decodePendingAuth(json: String): PendingAuth? =
        runCatching { DownloadJson.decodeFromString<PendingAuth>(json.ifEmpty { "{}" }) }.getOrNull()
            ?.takeIf { it.authUrl.isNotEmpty() || it.extensionId.isNotEmpty() }

    /** Fallback when the extension id is unknown: list every pending auth request. */
    private fun decodeAllPendingAuth(): List<PendingAuth> =
        runCatching {
            DownloadJson.decodeFromString<List<PendingAuth>>(gobackend.Gobackend.getAllPendingAuthRequestsJSON())
        }.getOrDefault(emptyList())

    fun submitAuthCode(code: String) {
        val id = _ui.value.authRequestId ?: return
        _ui.update { it.copy(authRequestId = null, authPrompt = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { backend.submitAuthCode(id, code) }
            // Retry the failed downloads now that the extension is authenticated.
            repository.retryVerification(id)
            DownloadWorker.enqueue(context)
        }
    }

    fun cancel(itemId: String) {
        viewModelScope.launch(Dispatchers.IO) { runCatching { backend.cancel(itemId) } }
        repository.markCancelled(itemId)
    }

    fun retry(itemId: String) {
        repository.retry(itemId)
        DownloadWorker.enqueue(context)
    }

    fun dismiss(itemId: String) = repository.dismiss(itemId)

    fun consumeError() = _ui.update { it.copy(error = null) }

    fun dismissAuth() = _ui.update { it.copy(authRequestId = null, authPrompt = null) }
}
