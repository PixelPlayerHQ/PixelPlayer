package com.theveloper.pixelplay.presentation.viewmodel

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.net.Uri
import android.os.Trace
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.core.content.ContextCompat
import com.theveloper.pixelplay.data.model.LibraryTabId
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.Timeline
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.EotStateHolder
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.database.AlbumArtThemeDao
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.model.Album
import com.theveloper.pixelplay.data.model.Artist
import com.theveloper.pixelplay.data.model.FolderSource
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.model.SearchFilterType
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SortOption
import com.theveloper.pixelplay.data.model.toLibraryTabIdOrNull
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.repository.LyricsSearchResult
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.service.MusicNotificationProvider
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.service.player.CastPlayer
import com.theveloper.pixelplay.data.service.http.MediaFileHttpServerService
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.utils.AppShortcutManager
import com.theveloper.pixelplay.utils.ValidatedLyricsImport
import com.theveloper.pixelplay.utils.QueueUtils
import com.theveloper.pixelplay.utils.MediaItemBuilder
import com.theveloper.pixelplay.utils.LyricsUtils
import com.theveloper.pixelplay.utils.StorageType
import com.theveloper.pixelplay.utils.StorageUtils
import com.theveloper.pixelplay.utils.ZipShareHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil.imageLoader
import coil.memory.MemoryCache
import dagger.Lazy

private const val CAST_LOG_TAG = "PlayerCastTransfer"
private const val ENABLE_FOLDERS_SOURCE_SWITCHING = false
private const val MAX_ALBUM_BATCH_SELECTION = 6
private const val SONG_ID_QUERY_CHUNK_SIZE = 900
private const val HOME_MIX_PREVIEW_LIMIT = 48

private fun List<Song>.toPlaybackQueue(): ImmutableList<Song> = when (this) {
    is PersistentList<Song> -> this
    is ImmutableList<Song> -> this
    else -> this.toPersistentList()
}

private fun ImmutableList<Song>.asPersistentPlaybackQueue(): PersistentList<Song> =
    this as? PersistentList<Song> ?: this.toPersistentList()

private fun ImmutableList<Song>.replaceSong(updatedSong: Song): ImmutableList<Song> {
    val index = indexOfFirst { it.id == updatedSong.id }
    if (index == -1) return this
    return asPersistentPlaybackQueue().set(index, updatedSong)
}

private fun ImmutableList<Song>.removeSongById(songId: String): ImmutableList<Song> {
    val index = indexOfFirst { it.id == songId }
    if (index == -1) return this
    return asPersistentPlaybackQueue().removeAt(index)
}

data class PlaybackAudioMetadata(
    val mediaId: String? = null,
    val mimeType: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channelCount: Int? = null,
    val bitDepth: Int? = null
)

private data class SortOptionsSnapshot(
    val songSort: SortOption,
    val albumSort: SortOption,
    val artistSort: SortOption,
    val folderSort: SortOption,
    val favoriteSort: SortOption,
)

private data class AiUiSnapshot(
    val showAiPlaylistSheet: Boolean,
    val isGeneratingAiPlaylist: Boolean,
    val aiStatus: String?,
    val aiError: String?,
    val isGeneratingAiMetadata: Boolean,
)

private data class PreparedPlaybackQueue(
    val mediaItems: List<MediaItem>,
    val startIndex: Int
)

private data class PendingMetadataEdit(
    val song: com.theveloper.pixelplay.data.model.Song,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val lyrics: String,
    val trackNumber: Int,
    val discNumber: Int?,
    val replayGainTrackGainDb: String?,
    val replayGainAlbumGainDb: String?,
    val coverArtUpdate: CoverArtUpdate?
)

@UnstableApi
@SuppressLint("LogNotTimber")
@OptIn(coil.annotation.ExperimentalCoilApi::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val albumArtThemeDao: AlbumArtThemeDao,
    val syncManager: SyncManager, // Inyectar SyncManager

    private val dualPlayerEngine: DualPlayerEngine,
    private val appShortcutManager: AppShortcutManager,
    private val telegramCacheManagerProvider: Lazy<com.theveloper.pixelplay.data.telegram.TelegramCacheManager>,
    private val listeningStatsTracker: ListeningStatsTracker,
    private val dailyMixStateHolder: DailyMixStateHolder,
    private val lyricsStateHolder: LyricsStateHolder,
    private val castStateHolder: CastStateHolder,
    private val castRouteStateHolder: CastRouteStateHolder,
    private val queueStateHolder: QueueStateHolder,
    private val queueUndoStateHolder: QueueUndoStateHolder,
    private val playlistDismissUndoStateHolder: PlaylistDismissUndoStateHolder,
    private val playbackStateHolder: PlaybackStateHolder,
    private val connectivityStateHolder: ConnectivityStateHolder,
    private val sleepTimerStateHolder: SleepTimerStateHolder,
    private val searchStateHolder: SearchStateHolder,
    private val aiStateHolder: AiStateHolder,
    private val libraryStateHolder: LibraryStateHolder,
    private val folderNavigationStateHolder: FolderNavigationStateHolder,
    private val libraryTabsStateHolder: LibraryTabsStateHolder,
    private val castTransferStateHolder: CastTransferStateHolder,
    private val metadataEditStateHolder: MetadataEditStateHolder,
    private val songRemovalStateHolder: SongRemovalStateHolder,
    private val externalMediaStateHolder: ExternalMediaStateHolder,
    val themeStateHolder: ThemeStateHolder,
    val multiSelectionStateHolder: MultiSelectionStateHolder,
    val playlistSelectionStateHolder: PlaylistSelectionStateHolder,
    private val sessionToken: SessionToken,
    private val mediaControllerFactory: com.theveloper.pixelplay.data.media.MediaControllerFactory
) : ViewModel() {

    private val _playerUiState = MutableStateFlow(PlayerUiState())
    val playerUiState: StateFlow<PlayerUiState> = _playerUiState.asStateFlow()
    
    private val _showNoInternetDialog = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val showNoInternetDialog: SharedFlow<Unit> = _showNoInternetDialog.asSharedFlow()

    val stablePlayerState: StateFlow<StablePlayerState> = playbackStateHolder.stablePlayerState
    /**
     * High-frequency playback position should not force global UI recomposition.
     * Keep a dedicated position flow for real-time UI elements (seek bars, lyrics timing).
     */
    val currentPlaybackPosition: StateFlow<Long> = playbackStateHolder.currentPosition
    val playbackHistory = listeningStatsTracker.playbackHistory

    // Removed: _masterAllSongs was a duplicate of libraryStateHolder.allSongs
    // All reads now delegate to libraryStateHolder.allSongs

    // Lyrics load callback for LyricsStateHolder
    private val lyricsLoadCallback = object : LyricsLoadCallback {
        override fun onLoadingStarted(songId: String) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = true, lyrics = null)
            }
        }

        override fun onLyricsLoaded(songId: String, lyrics: Lyrics?) {
            playbackStateHolder.updateStablePlayerState { state ->
                if (state.currentSong?.id != songId) state
                else state.copy(isLoadingLyrics = false, lyrics = lyrics)
            }
        }
    }



    /**
     * Paginated songs for efficient display in LibraryScreen.
     * Uses Paging 3 for memory-efficient loading of large libraries.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val paginatedSongs: Flow<PagingData<Song>> = libraryStateHolder.songsPagingFlow
        .cachedIn(viewModelScope)

    private val offlinePlaybackObserverJob = viewModelScope.launch {
        connectivityStateHolder.offlinePlaybackBlocked.collect {
            Timber.w("Received offline blocked event. Showing dialog.")
            _showNoInternetDialog.emit(Unit)
        }
    }

    private var telegramPlaybackObserversStarted = false

    private fun ensureTelegramPlaybackObserversStarted() {
        if (telegramPlaybackObserversStarted) return
        telegramPlaybackObserversStarted = true

        val telegramCacheManager = telegramCacheManagerProvider.get()
        val telegramRepository = musicRepository.telegramRepository

        viewModelScope.launch {
            launch {
                telegramCacheManager.embeddedArtUpdated.collect { updatedArtUri ->
                    refreshArtwork(updatedArtUri)
                }
            }

            launch {
                telegramRepository.downloadCompleted.collect {
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    if (currentSong != null && currentSong.contentUriString.startsWith("telegram:")) {
                        val uri = Uri.parse(currentSong.contentUriString)
                        val chatId = uri.host?.toLongOrNull()
                        val messageId = uri.pathSegments.firstOrNull()?.toLongOrNull()

                        if (chatId != null && messageId != null) {
                            refreshArtwork("telegram_art://$chatId/$messageId")
                        }
                    }
                }
            }
        }
    }

    private suspend fun refreshArtwork(updatedArtUri: String) {
        val currentState = playbackStateHolder.stablePlayerState.value
        val currentSong = currentState.currentSong
        // Check if it matches, ignoring query params for comparison
        val currentUriClean = currentSong?.albumArtUriString?.substringBefore('?')
        val updatedUriClean = updatedArtUri.substringBefore('?')
        
        if (currentUriClean == updatedUriClean) {
            Timber.d("PlayerViewModel: Embedded art updated for current song, forcing refresh")
            
            // 1. Invalidate Coil cache for the BASE uri (without params)
            // This ensures next time we load it without params, it's fresh too.
            val baseUri = currentUriClean ?: updatedUriClean
            
            // Remove from Memory Cache
            context.imageLoader.memoryCache?.keys?.forEach { key ->
                if (key.toString().contains(baseUri)) {
                    context.imageLoader.memoryCache?.remove(key)
                }
            }
            // Remove from Disk Cache
            context.imageLoader.diskCache?.remove(baseUri)

            // 2. Extract Colors (using base URI)
            themeStateHolder.extractAndGenerateColorScheme(updatedArtUri.toUri(), updatedArtUri, isPreload = false)
            
            // 3. FORCE UI REFRESH by updating the URI with a version timestamp
            // This forces SmartImage to see a "new" model and reload.
            // We keep the quality param if it exists, or add a version param.
            val newUri = if (updatedArtUri.contains("?")) {
                "$updatedArtUri&v=${System.currentTimeMillis()}"
            } else {
                "$updatedArtUri?v=${System.currentTimeMillis()}"
            }
            
            val updatedSong = currentSong!!.copy(albumArtUriString = newUri)
            
            // Update State
            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(currentSong = updatedSong)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentSongArtists: StateFlow<List<Artist>> = stablePlayerState
        .map { it.currentSong?.id }
        .distinctUntilChanged()
        .flatMapLatest { songId ->
            val idLong = songId?.toLongOrNull()
            if (idLong == null) flowOf(emptyList())
            else musicRepository.getArtistsForSong(idLong)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _sheetState = MutableStateFlow(PlayerSheetState.COLLAPSED)
    val sheetState: StateFlow<PlayerSheetState> = _sheetState.asStateFlow()
    private val _isSheetVisible = MutableStateFlow(false)
    private val _bottomBarHeight = MutableStateFlow(0)
    val bottomBarHeight: StateFlow<Int> = _bottomBarHeight.asStateFlow()
    private val _predictiveBackCollapseFraction = MutableStateFlow(0f)
    val predictiveBackCollapseFraction: StateFlow<Float> = _predictiveBackCollapseFraction.asStateFlow()
    private val _predictiveBackSwipeEdge = MutableStateFlow<Int?>(null)
    val predictiveBackSwipeEdge: StateFlow<Int?> = _predictiveBackSwipeEdge.asStateFlow()
    private val _isQueueSheetVisible = MutableStateFlow(false)
    val isQueueSheetVisible: StateFlow<Boolean> = _isQueueSheetVisible.asStateFlow()
    private val _isCastSheetVisible = MutableStateFlow(false)
    val isCastSheetVisible: StateFlow<Boolean> = _isCastSheetVisible.asStateFlow()

    val playerContentExpansionFraction = Animatable(0f)

    // AI Ecosystem: States delegated to AiStateHolder for centralized management
    val showAiPlaylistSheet: StateFlow<Boolean> = aiStateHolder.showAiPlaylistSheet
    val isGeneratingAiPlaylist: StateFlow<Boolean> = aiStateHolder.isGeneratingAiPlaylist
    val aiSuccess: StateFlow<Boolean> = aiStateHolder.aiSuccess
    val aiStatus: StateFlow<String?> = aiStateHolder.aiStatus
    val aiError: StateFlow<String?> = aiStateHolder.aiError

    // AI Metadata Generation States
    val isGeneratingAiMetadata: StateFlow<Boolean> = aiStateHolder.isGeneratingMetadata
    val aiMetadataSuccess: StateFlow<Boolean> = aiStateHolder.aiMetadataSuccess

    private val _selectedSongForInfo = MutableStateFlow<Song?>(null)
    val selectedSongForInfo: StateFlow<Song?> = _selectedSongForInfo.asStateFlow()

    // Theme & Colors - delegated to ThemeStateHolder
    val currentAlbumArtColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.currentAlbumArtColorSchemePair
    val activePlayerColorSchemePair: StateFlow<ColorSchemePair?> = themeStateHolder.activePlayerColorSchemePair
    val currentThemedAlbumArtUri: StateFlow<String?> = themeStateHolder.currentAlbumArtUri

    val playerThemePreference: StateFlow<String> = themePreferencesRepository.playerThemePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ThemePreference.ALBUM_ART
        )

    val navBarCornerRadius: StateFlow<Int> = userPreferencesRepository.navBarCornerRadiusFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 32)

    val navBarStyle: StateFlow<String> = userPreferencesRepository.navBarStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = NavBarStyle.DEFAULT
        )

    val navBarCompactMode: StateFlow<Boolean> = userPreferencesRepository.navBarCompactModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val libraryNavigationMode: StateFlow<String> = userPreferencesRepository.libraryNavigationModeFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LibraryNavigationMode.TAB_ROW
        )

    val carouselStyle: StateFlow<String> = userPreferencesRepository.carouselStyleFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CarouselStyle.NO_PEEK
        )

    val hasActiveAiProviderApiKey: StateFlow<Boolean> = combine(
        aiPreferencesRepository.aiProvider,
        aiPreferencesRepository.geminiApiKey,
        aiPreferencesRepository.deepseekApiKey,
        aiPreferencesRepository.groqApiKey,
        aiPreferencesRepository.mistralApiKey,
        aiPreferencesRepository.nvidiaApiKey,
        aiPreferencesRepository.kimiApiKey,
        aiPreferencesRepository.glmApiKey,
        aiPreferencesRepository.openaiApiKey
    ) { values ->
        val provider = values[0]
        val gemini = values[1]
        val deepseek = values[2]
        val groq = values[3]
        val mistral = values[4]
        val nvidia = values[5]
        val kimi = values[6]
        val glm = values[7]
        val openai = values[8]
        when (provider) {
            "DEEPSEEK" -> deepseek.isNotBlank()
            "GROQ" -> groq.isNotBlank()
            "MISTRAL" -> mistral.isNotBlank()
            "NVIDIA" -> nvidia.isNotBlank()
            "KIMI" -> kimi.isNotBlank()
            "GLM" -> glm.isNotBlank()
            "OPENAI" -> openai.isNotBlank()
            else -> gemini.isNotBlank()
        }
    }.distinctUntilChanged()
        .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val hasGeminiApiKey: StateFlow<Boolean> = aiPreferencesRepository.geminiApiKey
        .map { it.isNotBlank() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val fullPlayerLoadingTweaks: StateFlow<FullPlayerLoadingTweaks> = userPreferencesRepository.fullPlayerLoadingTweaksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FullPlayerLoadingTweaks()
        )

    val showPlayerFileInfo: StateFlow<Boolean> = userPreferencesRepository.showPlayerFileInfoFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    /**
     * Whether tapping the background of the player sheet toggles its state.
     * When disabled, users must use gestures or buttons to expand/collapse.
     */
    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val hapticsEnabled: StateFlow<Boolean> = userPreferencesRepository.hapticsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    // Lyrics sync offset - now managed by LyricsStateHolder
    val currentSongLyricsSyncOffset: StateFlow<Int> = lyricsStateHolder.currentSongSyncOffset

    // Lyrics source preference (API_FIRST, EMBEDDED_FIRST, LOCAL_FIRST)
    val lyricsSourcePreference: StateFlow<LyricsSourcePreference> = userPreferencesRepository.lyricsSourcePreferenceFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LyricsSourcePreference.EMBEDDED_FIRST
        )

    val immersiveLyricsEnabled: StateFlow<Boolean> = userPreferencesRepository.immersiveLyricsEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val immersiveLyricsTimeout: StateFlow<Long> = userPreferencesRepository.immersiveLyricsTimeoutFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 4000L
        )

    private val _isImmersiveTemporarilyDisabled = MutableStateFlow(false)
    val isImmersiveTemporarilyDisabled: StateFlow<Boolean> = _isImmersiveTemporarilyDisabled.asStateFlow()

    fun setImmersiveTemporarilyDisabled(disabled: Boolean) {
        _isImmersiveTemporarilyDisabled.value = disabled
    }

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    fun setLyricsSyncOffset(songId: String, offsetMs: Int) {
        lyricsStateHolder.setSyncOffset(songId, offsetMs)
    }

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )



    private val _isInitialThemePreloadComplete = MutableStateFlow(false)

    val isEndOfTrackTimerActive: StateFlow<Boolean> = sleepTimerStateHolder.isEndOfTrackTimerActive
    val activeTimerValueDisplay: StateFlow<String?> = sleepTimerStateHolder.activeTimerValueDisplay
    val playCount: StateFlow<Float> = sleepTimerStateHolder.playCount

    // Lyrics search UI state - managed by LyricsStateHolder
    val lyricsSearchUiState: StateFlow<LyricsSearchUiState> = lyricsStateHolder.searchUiState

    private var bufferingDebounceJob: Job? = null



    // Toast Events
    private val _toastEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val toastEvents = _toastEvents.asSharedFlow()

    // MediaStore write-permission request (needed for metadata editing without MANAGE_EXTERNAL_STORAGE)
    private val _writePermissionRequest = MutableSharedFlow<android.content.IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val writePermissionRequest: SharedFlow<android.content.IntentSender> = _writePermissionRequest.asSharedFlow()

    // MediaStore delete-permission request (for deletion without MANAGE_EXTERNAL_STORAGE)
    private val _deletePermissionRequest = MutableSharedFlow<android.content.IntentSender>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val deletePermissionRequest: SharedFlow<android.content.IntentSender> = _deletePermissionRequest.asSharedFlow()

    private var pendingMetadataEdit: PendingMetadataEdit? = null
    private var pendingDeleteSong: Song? = null
    private var pendingDeleteCallback: ((Boolean) -> Unit)? = null

    private val _albumNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val albumNavigationRequests = _albumNavigationRequests.asSharedFlow()
    private val _artistNavigationRequests = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val artistNavigationRequests = _artistNavigationRequests.asSharedFlow()
    private val _searchNavDoubleTapEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val searchNavDoubleTapEvents = _searchNavDoubleTapEvents.asSharedFlow()
    
    // New event for scrolling to a specific index in the songs list
    private val _scrollToIndexEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val scrollToIndexEvent = _scrollToIndexEvent.asSharedFlow()
    
    private var albumNavigationJob: Job? = null
    private var artistNavigationJob: Job? = null
    private var fullQueuePlaybackJob: Job? = null
    private var fullQueuePlaybackToken: Long = 0L

    fun requestLocateCurrentSong() {
        val currentSongId = stablePlayerState.value.currentSong?.id ?: return
        val currentIdLong = currentSongId.toLongOrNull() ?: return // Telegram songs with negative IDs are also Longs
        
        viewModelScope.launch {
            try {
                // Get current sort option and filter from UI state
                val sortOption = playerUiState.value.currentSongSortOption
                val storageFilter = playerUiState.value.currentStorageFilter
                
                // Fetch sorted IDs from DB
                val sortedIds = musicRepository.getSongIdsSorted(sortOption, storageFilter)
                
                // Find index
                val index = sortedIds.indexOf(currentIdLong)
                
                if (index != -1) {
                    _scrollToIndexEvent.emit(index)
                } else {
                    sendToast("Song not found in current list")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to locate current song")
                sendToast("Could not locate song")
            }
        }
    }

    fun showAndPlaySongFromLibrary(
        song: Song,
        queueName: String = "Library",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build full library queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentSongSortOption
            val storageFilter = playerUiState.value.currentStorageFilter
            musicRepository.getSongIdsSorted(sortOption, storageFilter)
        }
    }

    fun showAndPlaySongFromFavorites(
        song: Song,
        queueName: String = "Liked Songs",
        isVoluntaryPlay: Boolean = true
    ) {
        launchLatestFullQueuePlayback(
            song = song,
            queueName = queueName,
            isVoluntaryPlay = isVoluntaryPlay,
            failureMessage = "Failed to build favorites queue for songId=%s"
        ) {
            val sortOption = playerUiState.value.currentFavoriteSortOption
            val storageFilter = playerUiState.value.currentStorageFilter
            musicRepository.getFavoriteSongIdsSorted(sortOption, storageFilter)
        }
    }

    private fun launchLatestFullQueuePlayback(
        song: Song,
        queueName: String,
        isVoluntaryPlay: Boolean,
        failureMessage: String,
        sortedIdsProvider: suspend () -> List<Long>
    ) {
        cancelPendingFullQueuePlayback()
        val requestToken = fullQueuePlaybackToken

        fullQueuePlaybackJob = viewModelScope.launch {
            try {
                val sortedIds = sortedIdsProvider()
                throwIfFullQueuePlaybackRequestIsStale(requestToken)

                val fullQueue = resolvePlaybackQueueFromSortedIds(sortedIds)
                throwIfFullQueuePlaybackRequestIsStale(requestToken)

                showAndPlaySong(
                    song = song,
                    contextSongs = fullQueue.ifEmpty { listOf(song) },
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (requestToken != fullQueuePlaybackToken) {
                    return@launch
                }

                Timber.e(error, failureMessage, song.id)
                showAndPlaySong(
                    song = song,
                    contextSongs = listOf(song),
                    queueName = queueName,
                    isVoluntaryPlay = isVoluntaryPlay,
                    cancelPendingQueueBuild = false
                )
            }
        }
    }

    private fun cancelPendingFullQueuePlayback() {
        fullQueuePlaybackToken += 1L
        fullQueuePlaybackJob?.cancel()
        fullQueuePlaybackJob = null
    }

    private fun throwIfFullQueuePlaybackRequestIsStale(requestToken: Long) {
        if (requestToken != fullQueuePlaybackToken) {
            throw CancellationException("Stale full-queue playback request")
        }
    }

    private suspend fun resolvePlaybackQueueFromSortedIds(sortedIds: List<Long>): List<Song> {
        if (sortedIds.isEmpty()) return emptyList()

        val orderedIds = sortedIds.map(Long::toString)
        val cachedSongsById = libraryStateHolder.allSongsById.value
        val missingIds = ArrayList<String>()
        val cachedQueue = ArrayList<Song>(orderedIds.size)

        withContext(Dispatchers.Default) {
            orderedIds.forEach { songId ->
                val cachedSong = cachedSongsById[songId]
                if (cachedSong != null) {
                    cachedQueue.add(cachedSong)
                } else {
                    missingIds.add(songId)
                }
            }
        }

        if (missingIds.isEmpty()) {
            return cachedQueue
        }

        val missingSongsById = getSongsByIdsChunked(missingIds).associateBy { it.id }
        return withContext(Dispatchers.Default) {
            val finalQueue = ArrayList<Song>(orderedIds.size)
            orderedIds.forEach { songId ->
                val resolvedSong = cachedSongsById[songId] ?: missingSongsById[songId]
                if (resolvedSong != null) {
                    finalQueue.add(resolvedSong)
                }
            }
            finalQueue
        }
    }

    private suspend fun getSongsByIdsChunked(songIds: List<String>): List<Song> {
        if (songIds.isEmpty()) return emptyList()
        if (songIds.size <= SONG_ID_QUERY_CHUNK_SIZE) {
            return musicRepository.getSongsByIds(songIds).first()
        }

        return withContext(Dispatchers.IO) {
            buildList(songIds.size) {
                songIds.chunked(SONG_ID_QUERY_CHUNK_SIZE).forEach { chunk ->
                    addAll(musicRepository.getSongsByIds(chunk).first())
                }
            }
        }
    }

    val castRoutes: StateFlow<List<MediaRouter.RouteInfo>> = castStateHolder.castRoutes
    val selectedRoute: StateFlow<MediaRouter.RouteInfo?> = castStateHolder.selectedRoute
    /** Pre-mapped so UI composables don't create a new Flow on every recomposition. */
    val selectedRouteName: StateFlow<String?> = castStateHolder.selectedRoute
        .map { it?.name }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val routeVolume: StateFlow<Int> = castStateHolder.routeVolume
    val isRefreshingRoutes: StateFlow<Boolean> = castStateHolder.isRefreshingRoutes

    // Connectivity state delegated to ConnectivityStateHolder
    val isWifiEnabled: StateFlow<Boolean> = connectivityStateHolder.isWifiEnabled
    val isWifiRadioOn: StateFlow<Boolean> = connectivityStateHolder.isWifiRadioOn
    val wifiName: StateFlow<String?> = connectivityStateHolder.wifiName
    val isBluetoothEnabled: StateFlow<Boolean> = connectivityStateHolder.isBluetoothEnabled
    val bluetoothName: StateFlow<String?> = connectivityStateHolder.bluetoothName
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = connectivityStateHolder.bluetoothAudioDeviceStates
    val bluetoothAudioDevices: StateFlow<List<String>> = connectivityStateHolder.bluetoothAudioDevices



    // Connectivity is now managed by ConnectivityStateHolder

    // Cast state is now managed by CastStateHolder
    private val sessionManager: SessionManager? get() = castStateHolder.sessionManager

    val isRemotePlaybackActive: StateFlow<Boolean> = castStateHolder.isRemotePlaybackActive
    val isCastConnecting: StateFlow<Boolean> = castStateHolder.isCastConnecting
    val remotePosition: StateFlow<Long> = castStateHolder.remotePosition

    private val _trackVolume = MutableStateFlow(1.0f)
    val trackVolume: StateFlow<Float> = _trackVolume.asStateFlow()


    @Inject
    lateinit var mediaMapper: com.theveloper.pixelplay.data.media.MediaMapper

    @Inject
    lateinit var imageCacheManager: com.theveloper.pixelplay.data.media.ImageCacheManager

    init {
        // Initialize helper classes with our coroutine scope
        listeningStatsTracker.initialize(viewModelScope)
        dailyMixStateHolder.initialize(viewModelScope)
        lyricsStateHolder.initialize(viewModelScope, lyricsLoadCallback, playbackStateHolder.stablePlayerState)
        playbackStateHolder.initialize(viewModelScope)
        themeStateHolder.initialize(viewModelScope)

        // On cold start, the MediaController connects asynchronously, leaving stablePlayerState.currentSong
        // null until that happens. Pre-load the palette from the persisted snapshot so the mini player
        // has the correct colors immediately on first render, before the controller is ready.
        viewModelScope.launch {
            val snapshot = runCatching {
                userPreferencesRepository.getPlaybackQueueSnapshotOnce()
            }.getOrNull() ?: return@launch

            val currentItem = if (snapshot.currentMediaId != null) {
                snapshot.items.find { it.mediaId == snapshot.currentMediaId }
            } else {
                snapshot.items.getOrNull(snapshot.currentIndex)
            } ?: return@launch

            val artworkUri = currentItem.artworkUri?.takeIf { it.isNotBlank() } ?: return@launch

            themeStateHolder.extractAndGenerateColorScheme(
                albumArtUriAsUri = artworkUri.toUri(),
                currentSongUriString = artworkUri,
                isPreload = false
            )
        }

        stablePlayerState
            .map { it.currentSong?.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
            .distinctUntilChanged()
            .onEach { artworkUri ->
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = artworkUri?.toUri(),
                    currentSongUriString = artworkUri,
                    isPreload = false
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            lyricsStateHolder.songUpdates.collect { update: Pair<com.theveloper.pixelplay.data.model.Song, com.theveloper.pixelplay.data.model.Lyrics?> ->
                val song = update.first
                val lyrics = update.second
                // Check if this update is relevant to the currently playing song OR the selected song
                if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                    // MERGE FIX: if song comes back empty (e.g. from reset), preserve current metadata
                    val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                    val safeSong = if (song.title.isEmpty() && currentSong != null) {
                        currentSong.copy(lyrics = "")
                    } else {
                        song
                    }
                    updateSongInStates(safeSong, lyrics)
                }
                if (_selectedSongForInfo.value?.id == song.id) {
                    val currentSelected = _selectedSongForInfo.value
                    if (song.title.isEmpty() && currentSelected != null) {
                        _selectedSongForInfo.value = currentSelected.copy(lyrics = "")
                    } else {
                        _selectedSongForInfo.value = song
                    }
                }
            }
        }

        lyricsStateHolder.messageEvents
            .onEach { msg: String -> _toastEvents.emit(msg) }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            stablePlayerState
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .flatMapLatest { songId ->
                    if (songId.isNullOrBlank()) flowOf(null)
                    else musicRepository.getSong(songId)
                }
                .collect { repositorySong ->
                    val currentState = playbackStateHolder.stablePlayerState.value
                    val currentSong = currentState.currentSong ?: return@collect
                    if (repositorySong == null || repositorySong.id != currentSong.id) {
                        return@collect
                    }

                    val hydratedSong = currentSong.withRepositoryHydration(repositorySong)
                    val persistedLyrics = parsePersistedLyrics(hydratedSong.lyrics)
                    val shouldApplyPersistedLyrics = currentState.lyrics == null && persistedLyrics != null
                    val shouldRefreshSong = hydratedSong != currentSong
                    val shouldReloadLyrics =
                        !shouldApplyPersistedLyrics &&
                            currentState.lyrics == null &&
                            hydratedSong.improvesLyricsLookupComparedTo(currentSong)

                    if (shouldApplyPersistedLyrics || shouldReloadLyrics) {
                        lyricsStateHolder.cancelLoading()
                    }

                    if (shouldRefreshSong || shouldApplyPersistedLyrics) {
                        updateSongInStates(
                            updatedSong = hydratedSong,
                            newLyrics = if (shouldApplyPersistedLyrics) persistedLyrics else null,
                            isLoadingLyrics = if (shouldApplyPersistedLyrics) false else null
                        )

                        if (_selectedSongForInfo.value?.id == hydratedSong.id) {
                            _selectedSongForInfo.value = hydratedSong
                        }
                    }

                    if (shouldReloadLyrics) {
                        lyricsStateHolder.loadLyricsForSong(hydratedSong, lyricsSourcePreference.value)
                    }
                }
        }
    }

    fun setTrackVolume(volume: Float) {
        mediaController?.let {
            val clampedVolume = volume.coerceIn(0f, 1f)
            it.volume = clampedVolume
            _trackVolume.value = clampedVolume
        }
    }

    fun sendToast(message: String) {
        viewModelScope.launch {
            _toastEvents.emit(message)
        }
    }

    fun onSearchNavIconDoubleTapped() {
        _searchNavDoubleTapEvents.tryEmit(Unit)
    }


    // Last Library Tab Index
    val lastLibraryTabIndexFlow: StateFlow<Int> =
        userPreferencesRepository.lastLibraryTabIndexFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0 // Default to Songs tab
        )

    val libraryTabsFlow: StateFlow<List<String>> = userPreferencesRepository.libraryTabsOrderFlow
        .map { orderJson ->
            if (orderJson != null) {
                try {
                    Json.decodeFromString<List<String>>(orderJson)
                } catch (e: Exception) {
                    listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
                }
            } else {
                listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED")
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("SONGS", "ALBUMS", "ARTIST", "PLAYLISTS", "FOLDERS", "LIKED"))

    private val _loadedTabs = MutableStateFlow(emptySet<String>())
    private var lastBlockedDirectories: Set<String>? = null

    private val _currentLibraryTabId = MutableStateFlow(LibraryTabId.SONGS)
    val currentLibraryTabId: StateFlow<LibraryTabId> = _currentLibraryTabId.asStateFlow()

    private val _isSortingSheetVisible = MutableStateFlow(false)
    val isSortingSheetVisible: StateFlow<Boolean> = _isSortingSheetVisible.asStateFlow()

    val availableSortOptions: StateFlow<List<SortOption>> =
        currentLibraryTabId.map { tabId ->
            Trace.beginSection("PlayerViewModel.availableSortOptionsMapping")
            val options = when (tabId) {
                LibraryTabId.SONGS -> SortOption.SONGS
                LibraryTabId.ALBUMS -> SortOption.ALBUMS
                LibraryTabId.ARTISTS -> SortOption.ARTISTS
                LibraryTabId.PLAYLISTS -> SortOption.PLAYLISTS
                LibraryTabId.FOLDERS -> SortOption.FOLDERS
                LibraryTabId.LIKED -> SortOption.LIKED
            }
            Trace.endSection()
            options
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SortOption.SONGS
        )

    val isSyncingStateFlow: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    private val _isInitialDataLoaded = MutableStateFlow(false)

    // Public read-only access to all songs (using _masterAllSongs declared at class level)
    // Library State - delegated to LibraryStateHolder
    val allSongsFlow: StateFlow<ImmutableList<Song>> = libraryStateHolder.allSongs

    // Genres StateFlow - delegated to LibraryStateHolder
    val genres: StateFlow<ImmutableList<Genre>> = libraryStateHolder.genres
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val paletteRegenerationTargets: StateFlow<List<Song>> = musicRepository.getDistinctAlbumArtSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val homeMixPreviewSongs: StateFlow<ImmutableList<Song>> = musicRepository.getHomeMixPreviewSongs(
        limit = HOME_MIX_PREVIEW_LIMIT
    ).map { it.toImmutableList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    val albumsFlow: StateFlow<ImmutableList<Album>> = libraryStateHolder.albums
    val artistsFlow: StateFlow<ImmutableList<Artist>> = libraryStateHolder.artists

    var searchQuery by mutableStateOf("")
        private set

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    private var mediaController: MediaController? = null
    private var mediaControllerPlaybackListener: Player.Listener? = null
    private val _isMediaControllerReady = MutableStateFlow(false)
    val isMediaControllerReady: StateFlow<Boolean> = _isMediaControllerReady.asStateFlow()
    // SessionToken injected via constructor
    private val mediaControllerListener = object : MediaController.Listener, Player.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command.customAction == MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE) {
                val enabled = args.getBoolean(
                    MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED,
                    false
                )
                viewModelScope.launch {
                    if (enabled != playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            
            // Debounce buffering state to avoid flickering
            bufferingDebounceJob?.cancel()
            
            if (playbackState == Player.STATE_BUFFERING) {
                bufferingDebounceJob = viewModelScope.launch {
                    delay(150) // Wait 150ms before showing buffering indicator
                    playbackStateHolder.updateStablePlayerState { state ->
                        state.copy(isBuffering = true)
                    }
                }
            } else {
                // Immediately hide buffering when not buffering
                playbackStateHolder.updateStablePlayerState { state ->
                    state.copy(isBuffering = false)
                }
            }
        }
    }
    private val mediaControllerFuture: ListenableFuture<MediaController> =
        mediaControllerFactory.create(context, sessionToken, mediaControllerListener)
    private var pendingRepeatMode: Int? = null

    private var pendingPlaybackAction: (() -> Unit)? = null
    private var metadataProbeJob: Job? = null
    private var metadataProbeMediaId: String? = null

    private val _playbackAudioMetadata = MutableStateFlow(PlaybackAudioMetadata())
    val playbackAudioMetadata: StateFlow<PlaybackAudioMetadata> = _playbackAudioMetadata.asStateFlow()

    val favoriteSongIds: StateFlow<Set<String>> = musicRepository
        .getFavoriteSongIdsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged(),
        favoriteSongIds
    ) { songId, ids ->
        songId?.let { ids.contains(it) } ?: false
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ---------------------------------------------------------------------------
    // FullPlayerSlice — consolidates 11 independent flows into ONE subscription.
    // Previously FullPlayerContent had ~13 separate collectAsStateWithLifecycle()
    // calls. Each emission from any of them caused a recompose of the entire 2k-line
    // composable. Now a single collect + distinctUntilChanged batches all settings.
    // ---------------------------------------------------------------------------
    data class FullPlayerSlice(
        val currentSongArtists: List<Artist> = emptyList(),
        val lyricsSyncOffset: Int = 0,
        val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
        val audioMetadata: PlaybackAudioMetadata = PlaybackAudioMetadata(),
        val showPlayerFileInfo: Boolean = true,
        val immersiveLyricsEnabled: Boolean = false,
        val immersiveLyricsTimeout: Long = 4000L,
        val isImmersiveTemporarilyDisabled: Boolean = false,
        val isRemotePlaybackActive: Boolean = false,
        val selectedRouteName: String? = null,
        val isBluetoothEnabled: Boolean = false,
        val bluetoothName: String? = null
    )

    // Intermediate combine #1: 5 settings flows
    private val fullPlayerSlicePart1 = combine(
        currentSongArtists,
        currentSongLyricsSyncOffset,
        albumArtQuality,
        playbackAudioMetadata,
        showPlayerFileInfo
    ) { artists: List<Artist>, syncOffset: Int, artQuality: AlbumArtQuality,
        audioMeta: PlaybackAudioMetadata, showFileInfo: Boolean ->
        FullPlayerSlicePart1(artists, syncOffset, artQuality, audioMeta, showFileInfo)
    }

    private data class BluetoothSlice(val enabled: Boolean, val name: String?)

    private val bluetoothSlice = combine(isBluetoothEnabled, bluetoothName) { bt, btName ->
        BluetoothSlice(bt, btName)
    }

    // Intermediate combine #2: remaining flows (≤5 for Kotlin type inference)
    private val fullPlayerSlicePart2 = combine(
        immersiveLyricsEnabled,
        immersiveLyricsTimeout,
        isImmersiveTemporarilyDisabled,
        isRemotePlaybackActive,
        combine(selectedRouteName, bluetoothSlice) { route, bt -> route to bt }
    ) { immersive: Boolean, immersiveTimeout: Long, immersiveDisabled: Boolean,
        remotePb: Boolean, routeAndBt: Pair<String?, BluetoothSlice> ->
        val (routeName, bt) = routeAndBt
        FullPlayerSlicePart2(immersive, immersiveTimeout, immersiveDisabled, remotePb, routeName, bt.enabled, bt.name)
    }

    private data class FullPlayerSlicePart1(
        val currentSongArtists: List<Artist>,
        val lyricsSyncOffset: Int,
        val albumArtQuality: AlbumArtQuality,
        val audioMetadata: PlaybackAudioMetadata,
        val showPlayerFileInfo: Boolean
    )

    private data class FullPlayerSlicePart2(
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val isImmersiveTemporarilyDisabled: Boolean,
        val isRemotePlaybackActive: Boolean,
        val selectedRouteName: String?,
        val isBluetoothEnabled: Boolean,
        val bluetoothName: String?
    )

    val fullPlayerSlice: StateFlow<FullPlayerSlice> = combine(
        fullPlayerSlicePart1,
        fullPlayerSlicePart2
    ) { p1, p2 ->
        FullPlayerSlice(
            currentSongArtists = p1.currentSongArtists,
            lyricsSyncOffset = p1.lyricsSyncOffset,
            albumArtQuality = p1.albumArtQuality,
            audioMetadata = p1.audioMetadata,
            showPlayerFileInfo = p1.showPlayerFileInfo,
            immersiveLyricsEnabled = p2.immersiveLyricsEnabled,
            immersiveLyricsTimeout = p2.immersiveLyricsTimeout,
            isImmersiveTemporarilyDisabled = p2.isImmersiveTemporarilyDisabled,
            isRemotePlaybackActive = p2.isRemotePlaybackActive,
            selectedRouteName = p2.selectedRouteName,
            isBluetoothEnabled = p2.isBluetoothEnabled,
            bluetoothName = p2.bluetoothName
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FullPlayerSlice())

    // ---------------------------------------------------------------------------
    // PlayerConfigSlice — consolidates 7 infrequently-changing preference flows
    // into ONE subscription. Previously the player sheet had 7 separate
    // collectAsStateWithLifecycle() calls for config values, each causing a full
    // sheet recomposition when any preference changed.
    // ---------------------------------------------------------------------------
    data class PlayerConfigSlice(
        val navBarCornerRadius: Int = 32,
        val navBarStyle: String = NavBarStyle.DEFAULT,
        val carouselStyle: String = CarouselStyle.NO_PEEK,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
        val tapBackgroundClosesPlayer: Boolean = false,
        val useSmoothCorners: Boolean = true,
        val playerThemePreference: String = ThemePreference.ALBUM_ART
    )

    private val playerConfigSlicePart1 = combine(
        navBarCornerRadius,
        navBarStyle,
        carouselStyle,
        fullPlayerLoadingTweaks,
        tapBackgroundClosesPlayer
    ) { radius, style, carousel, tweaks, tapClose ->
        PlayerConfigSlicePart1(radius, style, carousel, tweaks, tapClose)
    }

    private data class PlayerConfigSlicePart1(
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val carouselStyle: String,
        val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks,
        val tapBackgroundClosesPlayer: Boolean
    )

    val playerConfigSlice: StateFlow<PlayerConfigSlice> = combine(
        playerConfigSlicePart1,
        useSmoothCorners,
        playerThemePreference
    ) { p1, smoothCorners, themePref ->
        PlayerConfigSlice(
            navBarCornerRadius = p1.navBarCornerRadius,
            navBarStyle = p1.navBarStyle,
            carouselStyle = p1.carouselStyle,
            fullPlayerLoadingTweaks = p1.fullPlayerLoadingTweaks,
            tapBackgroundClosesPlayer = p1.tapBackgroundClosesPlayer,
            useSmoothCorners = smoothCorners,
            playerThemePreference = themePref
        )
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlayerConfigSlice())

    // Library State - delegated to LibraryStateHolder
    // Favorites now use paginated flow from LibraryStateHolder (DB-level sort & filter)
    val favoritesPagingFlow = libraryStateHolder.favoritesPagingFlow

    // Daily mix state is now managed by DailyMixStateHolder
    val dailyMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.dailyMixSongs
    val yourMixSongs: StateFlow<ImmutableList<Song>> = dailyMixStateHolder.yourMixSongs

    fun removeFromDailyMix(songId: String) {
        dailyMixStateHolder.removeFromDailyMix(songId)
    }

    /**
     * Observes a song by ID from Room DB, combined with the latest favorite status.
     * Uses direct Room query instead of scanning the full in-memory list.
     */
    fun observeSong(songId: String?): Flow<Song?> {
        if (songId == null) return flowOf(null)
        return combine(
            musicRepository.getSong(songId),
            favoriteSongIds
        ) { song, favorites ->
            song?.copy(isFavorite = favorites.contains(songId))
        }.distinctUntilChanged()
    }



    private fun updateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.updateDailyMix(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    fun shuffleAllSongs(queueName: String = "All Songs (Shuffled)") {
        Log.d("ShuffleDebug", "shuffleAllSongs called.")
        
        // Load random songs from DB instead of materializing the entire library
        viewModelScope.launch {
            val randomSongs = musicRepository.getRandomSongs(limit = 500)
            if (randomSongs.isNotEmpty()) {
                playSongsShuffled(randomSongs, queueName, startAtZero = true)
            }
        }
    }

    /**
     * Called from Quick Settings tile. Unlike shuffleAllSongs(), this always starts
     * fresh playback regardless of current state, and correctly handles the case
     * where the MediaController isn't ready yet (cold start from tile).
     *
     * Uses allSongsFlow (StateFlow populated after resetAndLoadInitialData) instead
     * of querying the DB directly, which can be empty on cold start before sync runs.
     */
    fun triggerShuffleAllFromTile() {
        Timber.d("[TileDebug] triggerShuffleAllFromTile called. mediaController=${mediaController != null}")
        val action: () -> Unit = {
            Timber.d("[TileDebug] action() invoked")
            viewModelScope.launch {
                // If the in-memory library is already loaded, use it immediately
                var songs = allSongsFlow.value
                Timber.d("[TileDebug] allSongsFlow has ${songs.size} songs immediately")

                if (songs.isEmpty()) {
                    // Library not loaded yet — trigger a sync and wait up to 30s
                    Timber.d("[TileDebug] Library empty, triggering sync and waiting for allSongsFlow")
                    syncManager.sync()
                    val result = withTimeoutOrNull(30_000L) {
                        allSongsFlow.first { it.isNotEmpty() }
                    }
                    songs = result ?: persistentListOf()
                    Timber.d("[TileDebug] After wait, allSongsFlow has ${songs.size} songs")
                }

                if (songs.isNotEmpty()) {
                    // Shuffle a random subset (up to 500) to avoid loading entire library
                    val subset = if (songs.size > 500) songs.shuffled().take(500) else songs.toList()
                    Timber.d("[TileDebug] Calling playSongsShuffled with ${subset.size} songs")
                    playSongsShuffled(subset, "All Songs (Shuffled)", startAtZero = true)
                } else {
                    Timber.w("[TileDebug] No songs found even after sync - library may be empty")
                    sendToast("No songs found in library")
                }
            }
        }

        if (mediaController == null) {
            Timber.d("[TileDebug] mediaController null, queuing as pendingPlaybackAction")
            pendingPlaybackAction = action
        } else {
            Timber.d("[TileDebug] mediaController ready, calling action immediately")
            action()
        }
    }

    fun playRandomSong() {
        viewModelScope.launch {
            val randomSongs = musicRepository.getRandomSongs(limit = 500)
            if (randomSongs.isNotEmpty()) {
                playSongsShuffled(randomSongs, "All Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleFavoriteSongs() {
        Log.d("ShuffleDebug", "shuffleFavoriteSongs called.")

        // Load favorite songs from DB on-demand instead of holding them in memory
        viewModelScope.launch {
            val favSongs = musicRepository.getFavoriteSongsOnce(playerUiState.value.currentStorageFilter)
            if (favSongs.isNotEmpty()) {
                playSongsShuffled(favSongs, "Liked Songs (Shuffled)", startAtZero = true)
            }
        }
    }

    fun shuffleRandomAlbum() {
        viewModelScope.launch {
            val allAlbums = libraryStateHolder.albums.value
            if (allAlbums.isNotEmpty()) {
                val randomAlbum = allAlbums.random()
                val albumSongs = musicRepository.getSongsForAlbum(randomAlbum.id).first()
                if (albumSongs.isNotEmpty()) {
                    playSongsShuffled(albumSongs, randomAlbum.title, startAtZero = true)
                }
            }
        }
    }

    fun shuffleRandomArtist() {
        viewModelScope.launch {
            val allArtists = libraryStateHolder.artists.value
            if (allArtists.isNotEmpty()) {
                val randomArtist = allArtists.random()
                val artistSongs = musicRepository.getSongsForArtist(randomArtist.id).first()
                if (artistSongs.isNotEmpty()) {
                    playSongsShuffled(artistSongs, randomArtist.name, startAtZero = true)
                }
            }
        }
    }


    private fun loadPersistedDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.loadPersistedDailyMix(allSongsFlow)
    }

    fun forceUpdateDailyMix() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.forceUpdate(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private var transitionSchedulerJob: Job? = null
    private var remoteQueueLoadJob: Job? = null
    private var castSongUiSyncJob: Job? = null
    private var lastCastSongUiSyncedId: String? = null

    private fun incrementSongScore(song: Song) {
        listeningStatsTracker.onVoluntarySelection(song.id)
    }

    // MIN_SESSION_LISTEN_MS, currentSession, and ListeningStatsTracker class
    // have been moved to ListeningStatsTracker.kt for better modularity


    fun updatePredictiveBackCollapseFraction(fraction: Float) {
        _predictiveBackCollapseFraction.value = fraction.coerceIn(0f, 1f)
    }

    fun updatePredictiveBackSwipeEdge(edge: Int?) {
        _predictiveBackSwipeEdge.value = edge
    }

    fun resetPredictiveBackState() {
        _predictiveBackCollapseFraction.value = 0f
        _predictiveBackSwipeEdge.value = null
    }

    fun updateQueueSheetVisibility(visible: Boolean) {
        _isQueueSheetVisible.value = visible
    }

    fun updateCastSheetVisibility(visible: Boolean) {
        _isCastSheetVisible.value = visible
    }

    // Helper to resolve stored sort keys against the allowed group
    private fun resolveSortOption(
        optionKey: String?,
        allowed: Collection<SortOption>,
        fallback: SortOption
    ): SortOption {
        return SortOption.fromStorageKey(optionKey, allowed, fallback)
    }

    private data class FolderSourceState(
        val source: FolderSource,
        val rootPath: String,
        val isSdCardAvailable: Boolean
    )

    private fun resolveFolderSourceState(preferredSource: FolderSource): FolderSourceState {
        val storages = StorageUtils.getAvailableStorages(context)
        val internalPath = storages
            .firstOrNull { it.storageType == StorageType.INTERNAL }
            ?.path
            ?.path
            ?: android.os.Environment.getExternalStorageDirectory().path
        val sdPath = storages
            .firstOrNull { it.storageType == StorageType.SD_CARD }
            ?.path
            ?.path

        val effectiveSource = if (!ENABLE_FOLDERS_SOURCE_SWITCHING) {
            FolderSource.INTERNAL
        } else if (preferredSource == FolderSource.SD_CARD && sdPath == null) {
            FolderSource.INTERNAL
        } else {
            preferredSource
        }

        val resolvedRootPath = if (effectiveSource == FolderSource.SD_CARD) sdPath!! else internalPath
        return FolderSourceState(
            source = effectiveSource,
            rootPath = resolvedRootPath,
            isSdCardAvailable = sdPath != null
        )
    }

    // Connectivity refresh delegated to ConnectivityStateHolder
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        connectivityStateHolder.refreshLocalConnectionInfo(refreshBluetoothDevices)
    }

    init {
        Log.i("PlayerViewModel", "init started.")

        // Cast initialization if already connected
        val currentSession = sessionManager?.currentCastSession
        if (currentSession != null) {
            castStateHolder.setCastPlayer(CastPlayer(currentSession, context.contentResolver))
            castStateHolder.setRemotePlaybackActive(true)
        }



        viewModelScope.launch {
            userPreferencesRepository.migrateTabOrder()
        }

        viewModelScope.launch {
            userPreferencesRepository.ensureLibrarySortDefaults()
        }

        viewModelScope.launch {
            val legacyFavoriteIds = userPreferencesRepository.favoriteSongIdsFlow.first()
            if (legacyFavoriteIds.isNotEmpty()) {
                val roomFavoriteIds = musicRepository.getFavoriteSongIdsOnce()
                if (roomFavoriteIds.isEmpty()) {
                    legacyFavoriteIds.forEach { songId ->
                        musicRepository.setFavoriteStatus(songId, true)
                    }
                }
                userPreferencesRepository.clearFavoriteSongIds()
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.isFoldersPlaylistViewFlow.collect { isPlaylistView ->
                folderNavigationStateHolder.setFoldersPlaylistViewState(
                    isPlaylistView = isPlaylistView,
                    updateUiState = { mutation -> _playerUiState.update(mutation) }
                )
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.foldersSourceFlow.collect { preferredSource ->
                val resolved = resolveFolderSourceState(preferredSource)
                if (resolved.source != preferredSource) {
                    userPreferencesRepository.setFoldersSource(resolved.source)
                }

                _playerUiState.update { currentState ->
                    val sourceChanged = currentState.folderSource != resolved.source ||
                            currentState.folderSourceRootPath != resolved.rootPath
                    currentState.copy(
                        folderSource = resolved.source,
                        folderSourceRootPath = resolved.rootPath,
                        isSdCardAvailable = resolved.isSdCardAvailable,
                        currentFolderPath = if (sourceChanged) null else currentState.currentFolderPath,
                        currentFolder = if (sourceChanged) null else currentState.currentFolder
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.isAlbumsListViewFlow,
            ) { gestureNav, albumsList ->
                Pair(gestureNav, albumsList)
            }.collect { (gestureNav, albumsList) ->
                _playerUiState.update {
                    it.copy(
                        folderBackGestureNavigationEnabled = gestureNav,
                        isAlbumsListView = albumsList,
                    )
                }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.blockedDirectoriesFlow
                .distinctUntilChanged()
                .collect { blocked ->
                    if (lastBlockedDirectories == null) {
                        lastBlockedDirectories = blocked
                        return@collect
                    }

                    if (blocked != lastBlockedDirectories) {
                        lastBlockedDirectories = blocked
                        onBlockedDirectoriesChanged()
                    }
                }
        }

        viewModelScope.launch {
            combine(libraryTabsFlow, lastLibraryTabIndexFlow) { tabs, index ->
                tabs.getOrNull(index)?.toLibraryTabIdOrNull() ?: LibraryTabId.SONGS
            }.collect { tabId ->
                _currentLibraryTabId.value = tabId
            }
        }

        // Load initial sort options ONCE at startup.
        viewModelScope.launch {
            val initialSongSort = resolveSortOption(
                userPreferencesRepository.songsSortOptionFlow.first(),
                SortOption.SONGS,
                SortOption.SongTitleAZ
            )
            val initialAlbumSort = resolveSortOption(
                userPreferencesRepository.albumsSortOptionFlow.first(),
                SortOption.ALBUMS,
                SortOption.AlbumTitleAZ
            )
            val initialArtistSort = resolveSortOption(
                userPreferencesRepository.artistsSortOptionFlow.first(),
                SortOption.ARTISTS,
                SortOption.ArtistNameAZ
            )
            val initialFolderSort = resolveSortOption(
                userPreferencesRepository.foldersSortOptionFlow.first(),
                SortOption.FOLDERS,
                SortOption.FolderNameAZ
            )
            val initialLikedSort = resolveSortOption(
                userPreferencesRepository.likedSongsSortOptionFlow.first(),
                SortOption.LIKED,
                SortOption.LikedSongDateLiked
            )

            _playerUiState.update {
                it.copy(
                    currentSongSortOption = initialSongSort,
                    currentAlbumSortOption = initialAlbumSort,
                    currentArtistSortOption = initialArtistSort,
                    currentFolderSortOption = initialFolderSort,
                    currentFavoriteSortOption = initialLikedSort
                )
            }
            // Also update the dedicated flow for favorites to ensure consistency
            // _currentFavoriteSortOptionStateFlow.value = initialLikedSort // Delegated to LibraryStateHolder

            sortSongs(initialSongSort, persist = false)
            sortAlbums(initialAlbumSort, persist = false)
            sortArtists(initialArtistSort, persist = false)
            sortFolders(initialFolderSort, persist = false)
            sortFavoriteSongs(initialLikedSort, persist = false)
        }

        viewModelScope.launch {
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            if (isPersistent) {
                // If persistent shuffle is on, read the last used shuffle state (On/Off)
                val savedShuffle = userPreferencesRepository.isShuffleOnFlow.first()
                // Update the UI state so the shuffle button reflects the saved setting immediately
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = savedShuffle) }
            }
        }

        // launchColorSchemeProcessor() - Handled by ThemeStateHolder and on-demand calls

        loadPersistedDailyMix()
        loadSearchHistory()

        viewModelScope.launch {
            isSyncingStateFlow.collect { isSyncing ->
                val oldSyncingLibraryState = _playerUiState.value.isSyncingLibrary
                _playerUiState.update { it.copy(isSyncingLibrary = isSyncing) }

                if (oldSyncingLibraryState && !isSyncing) {
                    Log.i("PlayerViewModel", "Sync completed. Calling resetAndLoadInitialData from isSyncingStateFlow observer.")
                    resetAndLoadInitialData("isSyncingStateFlow observer")
                }
            }
        }

        viewModelScope.launch {
            if (!isSyncingStateFlow.value && !_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                Log.i("PlayerViewModel", "Initial check: Sync not active and initial data not loaded. Calling resetAndLoadInitialData.")
                resetAndLoadInitialData("Initial Check")
            }
        }

        mediaControllerFuture.addListener({
            try {
                mediaController = mediaControllerFuture.get()
                // Pass controller to PlaybackStateHolder
                playbackStateHolder.setMediaController(mediaController)
                _isMediaControllerReady.value = true


                setupMediaControllerListeners()
                flushPendingRepeatMode()
                syncShuffleStateWithSession(playbackStateHolder.stablePlayerState.value.isShuffleEnabled)
                // Execute any pending action that was queued while the controller was connecting
                pendingPlaybackAction?.invoke()
                pendingPlaybackAction = null
            } catch (e: Exception) {
                _playerUiState.update { it.copy(isLoadingInitialSongs = false, isLoadingLibraryCategories = false) }
                Log.e("PlayerViewModel", "Error setting up MediaController", e)
            }
        }, ContextCompat.getMainExecutor(context))


        // Start Cast discovery
        castStateHolder.startDiscovery()

        // Observe selection for HTTP server management
        viewModelScope.launch {
            castStateHolder.selectedRoute.collect { route ->
                if (route != null && !route.isDefault && route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                    castTransferStateHolder.primeHttpServerStart()
                } else if (route?.isDefault == true) {
                    val hasActiveRemoteSession = castStateHolder.castSession.value?.remoteMediaClient != null ||
                            castStateHolder.isRemotePlaybackActive.value ||
                            castStateHolder.isCastConnecting.value
                    if (hasActiveRemoteSession) {
                        return@collect
                    }
                    context.stopService(Intent(context, MediaFileHttpServerService::class.java))
                }
            }
        }

        // Initialize connectivity monitoring (WiFi/Bluetooth)
        connectivityStateHolder.initialize()

        // Initialize sleep timer state holder
        sleepTimerStateHolder.initialize(
            scope = viewModelScope,
            toastEmitter = { msg -> _toastEvents.emit(msg) },
            mediaControllerProvider = { mediaController },
            currentSongIdProvider = { stablePlayerState.map { it.currentSong?.id }.stateIn(viewModelScope, SharingStarted.Eagerly, null) },
            songTitleResolver = { songId -> libraryStateHolder.allSongs.value.find { it.id == songId }?.title ?: "Unknown" }
        )

        // Initialize SearchStateHolder
        searchStateHolder.initialize(viewModelScope)

        // Collect SearchStateHolder flows
        viewModelScope.launch {
            combine(
                searchStateHolder.searchResults,
                searchStateHolder.selectedSearchFilter,
                searchStateHolder.searchHistory,
            ) { results, filter, history ->
                Triple(results, filter, history)
            }.collect { (results, filter, history) ->
                _playerUiState.update {
                    it.copy(
                        searchResults = results,
                        selectedSearchFilter = filter,
                        searchHistory = history,
                    )
                }
            }
        }

        // Initialize AiStateHolder
        aiStateHolder.initialize(
            scope = viewModelScope,
            allSongsProvider = { libraryStateHolder.allSongs.value },
            favoriteSongIdsProvider = { favoriteSongIds.value },
            toastEmitter = { msg -> viewModelScope.launch { _toastEvents.emit(msg) } },
            playSongsCallback = { songs, startSong, queueName -> playSongs(songs, startSong, queueName) },
            openPlayerSheetCallback = { _isSheetVisible.value = true }
        )

        // Collect AiStateHolder flows
        viewModelScope.launch {
            combine(
                aiStateHolder.showAiPlaylistSheet,
                aiStateHolder.isGeneratingAiPlaylist,
                aiStateHolder.aiStatus,
                aiStateHolder.aiError,
                aiStateHolder.isGeneratingMetadata,
            ) { show, generating, status, error, generatingMetadata ->
                AiUiSnapshot(
                    showAiPlaylistSheet = show,
                    isGeneratingAiPlaylist = generating,
                    aiStatus = status,
                    aiError = error,
                    isGeneratingAiMetadata = generatingMetadata
                )
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(isGeneratingAiMetadata = snapshot.isGeneratingAiMetadata)
                }
            }
        }

        // Initialize LibraryStateHolder
        libraryStateHolder.initialize(viewModelScope)

        // Sync library folders and loading states
        viewModelScope.launch {
            combine(
                libraryStateHolder.musicFolders,
                libraryStateHolder.isLoadingLibrary,
                libraryStateHolder.isLoadingCategories,
            ) { folders, loadingLibrary, loadingCategories ->
                Triple(folders, loadingLibrary, loadingCategories)
            }.collect { (folders, loadingLibrary, loadingCategories) ->
                _playerUiState.update {
                    it.copy(
                        musicFolders = folders,
                        isLoadingInitialSongs = loadingLibrary,
                        isLoadingLibraryCategories = loadingCategories,
                    )
                }
            }
        }

        // Sync sort options and storage filter
        viewModelScope.launch {
            combine(
                libraryStateHolder.currentSongSortOption,
                libraryStateHolder.currentAlbumSortOption,
                libraryStateHolder.currentArtistSortOption,
                libraryStateHolder.currentFolderSortOption,
                libraryStateHolder.currentFavoriteSortOption,
            ) { songSort, albumSort, artistSort, folderSort, favoriteSort ->
                SortOptionsSnapshot(songSort, albumSort, artistSort, folderSort, favoriteSort)
            }.collect { snapshot ->
                _playerUiState.update {
                    it.copy(
                        currentSongSortOption = snapshot.songSort,
                        currentAlbumSortOption = snapshot.albumSort,
                        currentArtistSortOption = snapshot.artistSort,
                        currentFolderSortOption = snapshot.folderSort,
                        currentFavoriteSortOption = snapshot.favoriteSort,
                    )
                }
            }
        }
        viewModelScope.launch {
            libraryStateHolder.currentStorageFilter.collect { filter ->
                _playerUiState.update { it.copy(currentStorageFilter = filter) }
            }
        }
        viewModelScope.launch {
            userPreferencesRepository.hideLocalMediaFlow.collect { hide ->
                _playerUiState.update { it.copy(hideLocalMedia = hide) }
            }
        }


        castTransferStateHolder.initialize(
            scope = viewModelScope,
            getCurrentQueue = { _playerUiState.value.currentPlaybackQueue },
            updateQueue = { newQueue ->
                _playerUiState.update {
                    it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue())
                }
            },
            getMasterAllSongs = { libraryStateHolder.allSongs.value },
            onTransferBackComplete = { startProgressUpdates() },
            onSheetVisible = { _isSheetVisible.value = true },
            onDisconnect = { disconnect() },
            onCastError = { message ->
                viewModelScope.launch { _toastEvents.emit(message) }
            },
            onSongChanged = { uriString ->
                castSongUiSyncJob?.cancel()
                castSongUiSyncJob = viewModelScope.launch {
                    delay(220)
                    val currentSongId = stablePlayerState.value.currentSong?.id
                    if (currentSongId != null && currentSongId == lastCastSongUiSyncedId) {
                        return@launch
                    }
                    loadLyricsForCurrentSong()
                    uriString?.toUri()?.let { uri ->
                        themeStateHolder.extractAndGenerateColorScheme(uri, uriString)
                    }
                    if (currentSongId != null) {
                        lastCastSongUiSyncedId = currentSongId
                    }
                }
            }
        )



        viewModelScope.launch {
            // Repeat preference is only a startup restore value.
            // Keeping a live collector here creates a feedback path:
            // player -> DataStore -> collector -> player, which can cause
            // repeat mode oscillation if a transient player state is persisted.
            val savedRepeatMode = userPreferencesRepository.repeatModeFlow.first()
            applyPreferredRepeatMode(savedRepeatMode)
        }

        viewModelScope.launch {
            stablePlayerState
                .map { it.isShuffleEnabled }
                .distinctUntilChanged()
                .collect { enabled ->
                    syncShuffleStateWithSession(enabled)
                }
        }

        // Auto-hide undo bar when a new song starts playing
        playlistDismissUndoStateHolder.observeUndoStateAgainstPlayback(
            scope = viewModelScope,
            currentSongIdFlow = stablePlayerState.map { it.currentSong?.id },
            getUiState = { _playerUiState.value },
            onHideDismissUndoBar = { hideDismissUndoBar() }
        )

        Trace.endSection() // End PlayerViewModel.init
    }

    fun onMainActivityStart() {
        Trace.beginSection("PlayerViewModel.onMainActivityStart")
        preloadThemesAndInitialData()
        checkAndUpdateDailyMixIfNeeded()
        Trace.endSection()
    }


    private fun checkAndUpdateDailyMixIfNeeded() {
        // Delegate to DailyMixStateHolder
        dailyMixStateHolder.checkAndUpdateIfNeeded(
            allSongsFlow = allSongsFlow,
            favoriteSongIdsFlow = favoriteSongIds
        )
    }

    private fun preloadThemesAndInitialData() {
        Trace.beginSection("PlayerViewModel.preloadThemesAndInitialData")
        viewModelScope.launch {
            _isInitialThemePreloadComplete.value = false
            if (isSyncingStateFlow.value && !_isInitialDataLoaded.value) {
                // Sync is active - defer to sync completion handler
            } else if (!_isInitialDataLoaded.value && libraryStateHolder.allSongs.value.isEmpty()) {
                resetAndLoadInitialData("preloadThemesAndInitialData")
            }
            _isInitialThemePreloadComplete.value = true
        }
        Trace.endSection()
    }

    private fun loadInitialLibraryDataParallel() {
        libraryStateHolder.loadSongsFromRepository()
        libraryStateHolder.loadAlbumsFromRepository()
        libraryStateHolder.loadArtistsFromRepository()
        libraryStateHolder.loadFoldersFromRepository()
    }

    private fun resetAndLoadInitialData(caller: String = "Unknown") {
        Trace.beginSection("PlayerViewModel.resetAndLoadInitialData")
        loadInitialLibraryDataParallel()
        updateDailyMix()
        Trace.endSection()
    }

    fun loadSongsIfNeeded() = libraryStateHolder.loadSongsIfNeeded()
    fun loadAlbumsIfNeeded() = libraryStateHolder.loadAlbumsIfNeeded()
    fun loadArtistsIfNeeded() = libraryStateHolder.loadArtistsIfNeeded()
    fun loadFoldersFromRepository() = libraryStateHolder.loadFoldersFromRepository()

    fun setStorageFilter(filter: com.theveloper.pixelplay.data.model.StorageFilter) {
        libraryStateHolder.setStorageFilter(filter)
    }

    fun setHideLocalMedia(hide: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideLocalMedia(hide)
        }
    }

    fun toggleStorageFilter() {
        val current = _playerUiState.value.currentStorageFilter
        val next = when (current) {
            com.theveloper.pixelplay.data.model.StorageFilter.ALL -> com.theveloper.pixelplay.data.model.StorageFilter.ONLINE
            com.theveloper.pixelplay.data.model.StorageFilter.ONLINE -> com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE
            com.theveloper.pixelplay.data.model.StorageFilter.OFFLINE -> com.theveloper.pixelplay.data.model.StorageFilter.ALL
        }
        setStorageFilter(next)
    }

    fun showAndPlaySong(
        song: Song,
        contextSongs: List<Song>,
        queueName: String = "Current Context",
        isVoluntaryPlay: Boolean = true,
        cancelPendingQueueBuild: Boolean = true
    ) {
        if (cancelPendingQueueBuild) {
            cancelPendingFullQueuePlayback()
        }
        val playbackContext =
            if (contextSongs.any { it.id == song.id }) contextSongs else listOf(song)
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            val mediaStatus = remoteMediaClient.mediaStatus
            val desiredQueue = playbackContext
            val lastRemoteQueue = castTransferStateHolder.lastRemoteQueue
            val contextMatchesRemoteSnapshot = lastRemoteQueue.matchesSongOrder(desiredQueue)
            val targetIndexInDesiredQueue = desiredQueue.indexOfFirst { it.id == song.id }

            val currentRemoteId = mediaStatus
                ?.let { status ->
                    status.getQueueItemById(status.getCurrentItemId())
                        ?.customData?.optString("songId")
                        ?.takeIf { it.isNotBlank() }
                } ?: castTransferStateHolder.lastRemoteSongId

            val itemIdFromStatus = mediaStatus
                ?.queueItems
                ?.firstOrNull { it.customData?.optString("songId") == song.id }
                ?.itemId

            val targetItemId = itemIdFromStatus?.takeIf { it > 0 }
            val canJumpInCurrentRemoteQueue = contextMatchesRemoteSnapshot && targetIndexInDesiredQueue >= 0 && targetItemId != null

            when {
                canJumpInCurrentRemoteQueue -> {
                    // Same queue context: jump directly for immediate, deterministic song changes.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                    val itemId = requireNotNull(targetItemId)
                    castStateHolder.castPlayer?.jumpToItem(itemId, 0L)
                }
                contextMatchesRemoteSnapshot && currentRemoteId == song.id -> {
                    // Already on target.
                    remoteQueueLoadJob?.cancel()
                    castTransferStateHolder.markPendingRemoteSong(song)
                }
                else -> {
                    // Queue context changed: perform a single remote queue load.
                    remoteQueueLoadJob?.cancel()
                    remoteQueueLoadJob = viewModelScope.launch {
                        val hydratedQueue = hydrateSongsIfNeeded(desiredQueue)
                        if (hydratedQueue.isEmpty()) return@launch
                        val hydratedStartSong =
                            hydratedQueue.firstOrNull { it.id == song.id } ?: hydratedQueue.first()
                        val loaded = castTransferStateHolder.playRemoteQueue(
                            songsToPlay = hydratedQueue,
                            startSong = hydratedStartSong,
                            isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
                        )
                        if (!loaded) {
                            Timber.tag(CAST_LOG_TAG).w(
                                "Failed to load requested remote queue (songId=%s size=%d).",
                                song.id,
                                desiredQueue.size
                            )
                        }
                    }
                }
            }

            if (isVoluntaryPlay) incrementSongScore(song)
            return
        }    // Local playback logic
        mediaController?.let { controller ->
            val currentQueue = _playerUiState.value.currentPlaybackQueue
            val songIndexInQueue = currentQueue.indexOfFirst { it.id == song.id }
            val queueMatchesContext = currentQueue.matchesSongOrder(playbackContext)

            if (songIndexInQueue != -1 && queueMatchesContext) {
                if (controller.currentMediaItemIndex == songIndexInQueue) {
                    if (!controller.isPlaying) controller.play()
                } else {
                    controller.seekTo(songIndexInQueue, 0L)
                    controller.play()
                }
                if (isVoluntaryPlay) incrementSongScore(song)
            } else {
                if (isVoluntaryPlay) incrementSongScore(song)
                playSongs(playbackContext, song, queueName, null)
            }
        }
        resetPredictiveBackState()
    }

    fun showAndPlaySong(song: Song) {
        Log.d("ShuffleDebug", "showAndPlaySong (single song overload) called for '${song.title}'")
        // Use the song directly without scanning allSongs — the caller provides up-to-date data
        showAndPlaySong(song, listOf(song), "Library")
    }

    private fun List<Song>.matchesSongOrder(contextSongs: List<Song>): Boolean {
        if (size != contextSongs.size) return false
        return indices.all { this[it].id == contextSongs[it].id }
    }

    private fun Song.requiresHydration(): Boolean {
        return contentUriString.isBlank()
    }

    private suspend fun hydrateSongsIfNeeded(songs: List<Song>): List<Song> {
        if (songs.isEmpty() || songs.none { it.requiresHydration() }) return songs
        val hydratedSongs = getSongsByIdsChunked(songs.map { it.id })
        if (hydratedSongs.isEmpty()) return songs
        val hydratedById = hydratedSongs.associateBy { it.id }
        return songs.mapNotNull { original ->
            hydratedById[original.id] ?: original.takeIf { !original.requiresHydration() }
        }
    }

    fun playAlbum(album: Album) {
        Log.d("ShuffleDebug", "playAlbum called for album: ${album.title}")
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForAlbum(album.id).first()
                }

                if (songsList.isNotEmpty()) {
                    val sortedSongs = songsList.sortedWith(
                        compareBy<Song> { it.discNumber }
                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                            .thenBy { it.title.lowercase() }
                    )

                    playSongs(sortedSongs, sortedSongs.first(), album.title, null)
                    _isSheetVisible.value = true // Mostrar reproductor
                } else {
                    Log.w("PlayerViewModel", "Album '${album.title}' has no playable songs.")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing album ${album.title}", e)
            }
        }
    }

    fun playArtist(artist: Artist) {
        Log.d("ShuffleDebug", "playArtist called for artist: ${artist.name}")
        viewModelScope.launch {
            try {
                val songsList: List<Song> = withContext(Dispatchers.IO) {
                    musicRepository.getSongsForArtist(artist.id).first()
                }

                if (songsList.isNotEmpty()) {
                    playSongs(songsList, songsList.first(), artist.name, null)
                    _isSheetVisible.value = true
                } else {
                    Log.w("PlayerViewModel", "Artist '${artist.name}' has no playable songs.")
                    // podrías emitir un evento Toast
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error playing artist ${artist.name}", e)
            }
        }
    }

    fun removeSongFromQueue(songId: String) {
        queueUndoStateHolder.removeSongFromQueue(
            scope = viewModelScope,
            mediaController = mediaController,
            songId = songId,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun undoRemoveSongFromQueue() {
        queueUndoStateHolder.undoRemoveSongFromQueue(
            mediaController = mediaController,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) }
        )
    }

    fun hideQueueItemUndoBar() {
        queueUndoStateHolder.hideQueueItemUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun reorderQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            if (fromIndex >= 0 && fromIndex < controller.mediaItemCount &&
                toIndex >= 0 && toIndex < controller.mediaItemCount) {

                // Move the item in the MediaController's timeline.
                // This is the source of truth for playback.
                controller.moveMediaItem(fromIndex, toIndex)

            }
        }
    }

    fun togglePlayerSheetState(resetPredictiveState: Boolean = true) {
        _sheetState.value = if (_sheetState.value == PlayerSheetState.COLLAPSED) {
            PlayerSheetState.EXPANDED
        } else {
            PlayerSheetState.COLLAPSED
        }
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun expandPlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.EXPANDED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun collapsePlayerSheet(resetPredictiveState: Boolean = true) {
        _sheetState.value = PlayerSheetState.COLLAPSED
        if (resetPredictiveState) {
            resetPredictiveBackState()
        }
    }

    fun triggerAlbumNavigationFromPlayer(albumId: Long) {
        if (albumId == -1L) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored invalid albumId=$albumId")
            return
        }

        val existingJob = albumNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("AlbumDebug", "triggerAlbumNavigationFromPlayer ignored; navigation already in progress for albumId=$albumId")
            return
        }

        albumNavigationJob?.cancel()
        albumNavigationJob = viewModelScope.launch {
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            Log.d(
                "AlbumDebug",
                "triggerAlbumNavigationFromPlayer: albumId=$albumId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _albumNavigationRequests.emit(albumId)
        }
    }

    fun triggerArtistNavigationFromPlayer(artistId: Long) {
        if (artistId <= 0) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored invalid artistId=$artistId")
            return
        }

        val existingJob = artistNavigationJob
        if (existingJob != null && existingJob.isActive) {
            Log.d("ArtistDebug", "triggerArtistNavigationFromPlayer ignored; navigation already in progress for artistId=$artistId")
            return
        }

        artistNavigationJob?.cancel()
        artistNavigationJob = viewModelScope.launch {
            val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
            Log.d(
                "ArtistDebug",
                "triggerArtistNavigationFromPlayer: artistId=$artistId, songId=${currentSong?.id}, title=${currentSong?.title}"
            )
            collapsePlayerSheet()

            withTimeoutOrNull(900) {
                awaitSheetState(PlayerSheetState.COLLAPSED)
                awaitPlayerCollapse()
            }

            _artistNavigationRequests.emit(artistId)
        }
    }

    suspend fun awaitSheetState(target: PlayerSheetState) {
        sheetState.first { it == target }
    }

    suspend fun awaitPlayerCollapse(threshold: Float = 0.1f, timeoutMillis: Long = 800L) {
        withTimeoutOrNull(timeoutMillis) {
            snapshotFlow { playerContentExpansionFraction.value }
                .first { it <= threshold }
        }
    }

    private fun resolveSongFromMediaItem(
        mediaItem: MediaItem,
        allSongsById: Map<String, Song>? = null
    ): Song? {
        val resolvedSong =
            allSongsById?.get(mediaItem.mediaId)
                ?: libraryStateHolder.allSongsById.value[mediaItem.mediaId]
                ?: _playerUiState.value.currentPlaybackQueue.find { it.id == mediaItem.mediaId }
                ?: mediaMapper.resolveSongFromMediaItem(mediaItem)

        return resolvedSong?.let { normalizeArtworkForResolvedSong(it, mediaItem) }
    }

    private fun normalizeArtworkForResolvedSong(song: Song, mediaItem: MediaItem): Song {
        val metadataArtwork =
            mediaItem.mediaMetadata.artworkUri?.toString()?.takeIf { it.isNotBlank() }
                ?: mediaItem.mediaMetadata.extras
                    ?.getString(MediaItemBuilder.EXTERNAL_EXTRA_ALBUM_ART)
                    ?.takeIf { it.isNotBlank() }

        return when {
            metadataArtwork == null && song.albumArtUriString != null -> song.copy(albumArtUriString = null)
            metadataArtwork != null && song.albumArtUriString != metadataArtwork ->
                song.copy(albumArtUriString = metadataArtwork)
            else -> song
        }
    }

    private fun updateCurrentPlaybackQueueFromPlayer(playerCtrl: MediaController?) {
        val currentMediaController = playerCtrl ?: mediaController ?: return
        val count = currentMediaController.mediaItemCount

        if (count == 0) {
            _playerUiState.update { it.copy(currentPlaybackQueue = persistentListOf()) }
            return
        }

        // To avoid ANRs with very large queues (e.g. 5000+ songs after a long background stay),
        // we capture the lightweight MediaItem references on the Main thread, but process 
        // the heavy resolving and Song object creation on a background thread.
        val mediaItems = mutableListOf<MediaItem>()
        for (i in 0 until count) {
            mediaItems.add(currentMediaController.getMediaItemAt(i))
        }

        viewModelScope.launch {
            val allSongsById = libraryStateHolder.allSongsById.value
            
            val queue = withContext(Dispatchers.Default) {
                mediaItems.mapNotNull { mediaItem ->
                    resolveSongFromMediaItem(mediaItem, allSongsById)
                }
            }

            _playerUiState.update { it.copy(currentPlaybackQueue = queue.toPlaybackQueue()) }
            if (queue.isNotEmpty()) {
                _isSheetVisible.value = true
            }
        }
    }

    private fun applyPreferredRepeatMode(@Player.RepeatMode mode: Int) {
        playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = mode) }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            pendingRepeatMode = mode
            return
        }

        val controller = mediaController
        if (controller == null) {
            pendingRepeatMode = mode
            return
        }

        if (controller.repeatMode != mode) {
            controller.repeatMode = mode
        }
        pendingRepeatMode = null
    }

    private fun flushPendingRepeatMode() {
        pendingRepeatMode?.let { applyPreferredRepeatMode(it) }
    }

    private fun resetPlaybackAudioMetadata() {
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        _playbackAudioMetadata.value = PlaybackAudioMetadata()
    }

    private fun preparePlaybackAudioMetadataForMedia(mediaId: String?) {
        metadataProbeJob?.cancel()
        metadataProbeJob = null
        metadataProbeMediaId = null
        _playbackAudioMetadata.value = PlaybackAudioMetadata(mediaId = mediaId)
    }

    private fun extractBitDepthFromPcmEncoding(pcmEncoding: Int): Int? {
        return when (pcmEncoding) {
            C.ENCODING_PCM_8BIT -> 8
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 32
            else -> null
        }
    }

    private fun refreshPlaybackAudioMetadata(player: Player, tracks: Tracks = player.currentTracks) {
        runCatching {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId == null) {
                resetPlaybackAudioMetadata()
                return@runCatching
            }

            val selectedAudioFormat = tracks.groups
                .asSequence()
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .flatMap { group ->
                    (0 until group.length)
                        .asSequence()
                        .filter { index -> group.isTrackSelected(index) }
                        .map { index -> group.getTrackFormat(index) }
                }
                .firstOrNull()

            val current = _playbackAudioMetadata.value.takeIf { it.mediaId == mediaId }
            val metadata = PlaybackAudioMetadata(
                mediaId = mediaId,
                mimeType = selectedAudioFormat?.sampleMimeType
                    ?: selectedAudioFormat?.containerMimeType
                    ?: current?.mimeType,
                bitrate = selectedAudioFormat?.bitrate?.takeIf { it > 0 }
                    ?: current?.bitrate,
                sampleRate = selectedAudioFormat?.sampleRate?.takeIf { it > 0 }
                    ?: current?.sampleRate,
                channelCount = selectedAudioFormat?.channelCount?.takeIf { it > 0 } ?: current?.channelCount,
                bitDepth = selectedAudioFormat?.pcmEncoding?.let(::extractBitDepthFromPcmEncoding) ?: current?.bitDepth
            )

            _playbackAudioMetadata.value = metadata
            maybeProbeMissingPlaybackAudioMetadata(player, metadata)
        }.onFailure { throwable ->
            Timber.w(throwable, "Failed to refresh playback audio metadata")
        }
    }

    private fun maybeProbeMissingPlaybackAudioMetadata(
        player: Player,
        metadata: PlaybackAudioMetadata
    ) {
        val shouldProbe = metadata.mimeType.isNullOrBlank() || metadata.bitrate == null || metadata.sampleRate == null
        if (!shouldProbe) return

        val mediaItem = player.currentMediaItem ?: return
        val mediaId = mediaItem.mediaId
        val uri = mediaItem.localConfiguration?.uri ?: return

        if (metadataProbeMediaId == mediaId && metadataProbeJob?.isActive == true) return

        metadataProbeJob?.cancel()
        metadataProbeMediaId = mediaId
        metadataProbeJob = viewModelScope.launch(Dispatchers.IO) {
            val probedMetadata = runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    val mimeType = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                        ?.takeIf { it.isNotBlank() }
                        ?: context.contentResolver.getType(uri)
                    val bitrate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    val sampleRate = retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                        ?.toIntOrNull()
                        ?.takeIf { it > 0 }
                    PlaybackAudioMetadata(
                        mediaId = mediaId,
                        mimeType = mimeType,
                        bitrate = bitrate,
                        sampleRate = sampleRate
                    )
                } finally {
                    retriever.release()
                }
            }.getOrNull() ?: return@launch

            _playbackAudioMetadata.update { current ->
                val isSameMediaItem = current.mediaId == mediaId
                if (!isSameMediaItem) return@update current
                current.copy(
                    mimeType = current.mimeType ?: probedMetadata.mimeType,
                    bitrate = current.bitrate ?: probedMetadata.bitrate,
                    sampleRate = current.sampleRate ?: probedMetadata.sampleRate
                )
            }
        }
    }

    private fun isRemoteSessionControllingPlayback(): Boolean {
        val remoteClient = castStateHolder.castSession.value?.remoteMediaClient
        return remoteClient != null &&
                (castStateHolder.isRemotePlaybackActive.value || castStateHolder.isCastConnecting.value)
    }

    private fun syncPlaybackPositionFromPlayer(
        mediaId: String?,
        reportedPositionMs: Long
    ): Long {
        playbackStateHolder.syncCurrentPositionFromPlayer(mediaId, reportedPositionMs)
        return playbackStateHolder.currentPosition.value
    }

    private fun setupMediaControllerListeners() {
        Trace.beginSection("PlayerViewModel.setupMediaControllerListeners")
        val playerCtrl = mediaController ?: return Trace.endSection()
        _trackVolume.value = playerCtrl.volume
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                isShuffleEnabled = it.isShuffleEnabled,
                repeatMode = playerCtrl.repeatMode,
                isPlaying = playerCtrl.isPlaying,
                playWhenReady = playerCtrl.playWhenReady
            )
        }
        preparePlaybackAudioMetadataForMedia(playerCtrl.currentMediaItem?.mediaId)
        refreshPlaybackAudioMetadata(playerCtrl)

        updateCurrentPlaybackQueueFromPlayer(playerCtrl)

        playerCtrl.currentMediaItem?.let { mediaItem ->
            playbackStateHolder.ensureCurrentPlaybackOccurrence(mediaItem.mediaId)
            val song = resolveSongFromMediaItem(mediaItem)

            if (song != null) {
                val initialPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                    reportedDurationMs = playerCtrl.duration,
                    songDurationHintMs = song.duration.coerceAtLeast(0L),
                    currentPositionMs = initialPosition
                )
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = song,
                        totalDuration = resolvedDuration
                    )
                }
                syncPlaybackPositionFromPlayer(mediaItem.mediaId, initialPosition)
                viewModelScope.launch {
                    val uri = song.albumArtUriString?.toUri()
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                }
                listeningStatsTracker.onSongChanged(
                    song = song,
                    positionMs = initialPosition,
                    durationMs = resolvedDuration,
                    isPlaying = playerCtrl.isPlaying
                )
                loadLyricsForCurrentSong()
                if (playerCtrl.isPlaying) {
                    _isSheetVisible.value = true
                    startProgressUpdates()
                }
            } else {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
                playbackStateHolder.clearCurrentPositionHints()
                playbackStateHolder.setCurrentPosition(0L)
                resetPlaybackAudioMetadata()
            }
        }

        mediaControllerPlaybackListener?.let(playerCtrl::removeListener)
        mediaControllerPlaybackListener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                _trackVolume.value = volume
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = isPlaying,
                        playWhenReady = playerCtrl.playWhenReady
                    )
                }
                listeningStatsTracker.onPlayStateChanged(
                    isPlaying = isPlaying,
                    positionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                )
                if (isPlaying) {
                    _isSheetVisible.value = true
                    clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    startProgressUpdates()
                } else {
                    stopProgressUpdates()
                    val pausedPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, pausedPosition)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.updateStablePlayerState { it.copy(playWhenReady = playWhenReady) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                playbackStateHolder.onPlaybackOccurrenceTransition(mediaItem?.mediaId)
                preparePlaybackAudioMetadataForMedia(mediaItem?.mediaId)
                transitionSchedulerJob?.cancel()
                lyricsStateHolder.cancelLoading()
                transitionSchedulerJob = viewModelScope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                        val activeEotSongId = EotStateHolder.eotTargetSongId.value
                        val previousSongId = playerCtrl.run { if (previousMediaItemIndex != C.INDEX_UNSET) getMediaItemAt(previousMediaItemIndex).mediaId else null }

                        if (isEndOfTrackTimerActive.value && activeEotSongId != null && previousSongId != null && previousSongId == activeEotSongId) {
                            playerCtrl.seekTo(0L)
                            playerCtrl.pause()

                            val finishedSongTitle = libraryStateHolder.allSongs.value.find { it.id == previousSongId }?.title
                                ?: "Track"

                            viewModelScope.launch {
                                _toastEvents.emit("Playback stopped: $finishedSongTitle finished (End of Track).")
                            }
                            cancelSleepTimer(suppressDefaultToast = true)
                        }
                    }

                    mediaItem?.let { transitionedItem ->
                        listeningStatsTracker.finalizeCurrentSession()
                        val song = resolveSongFromMediaItem(transitionedItem)
                        
                        // Offline check for Telegram songs
                        if (song?.contentUriString?.startsWith("telegram:") == true) {
                            ensureTelegramPlaybackObserversStarted()
                            val isOnline = connectivityStateHolder.isOnline.value
                            if (!isOnline) {
                                val fileId = song.telegramFileId
                                if (fileId != null) {
                                    val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                                    if (!isCached) {
                                        playerCtrl.pause()
                                        _showNoInternetDialog.emit(Unit)
                                    }
                                }
                            }
                        }

                        val resolvedDuration = if (song != null) {
                            playbackStateHolder.resolveDurationForPlaybackState(
                                reportedDurationMs = playerCtrl.duration,
                                songDurationHintMs = song.duration.coerceAtLeast(0L),
                                currentPositionMs = playerCtrl.currentPosition.coerceAtLeast(0L)
                            )
                        } else {
                            0L
                        }
                        resetLyricsSearchState()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = song,
                                totalDuration = resolvedDuration,
                                lyrics = null,
                                isLoadingLyrics = song != null,
                                playWhenReady = playerCtrl.playWhenReady
                            )
                        }
                        val transitionPosition = syncPlaybackPositionFromPlayer(
                            transitionedItem.mediaId,
                            playerCtrl.currentPosition.coerceAtLeast(0L)
                        )

                        song?.let { currentSongValue ->
                            listeningStatsTracker.onSongChanged(
                                song = currentSongValue,
                                positionMs = transitionPosition,
                                durationMs = resolvedDuration,
                                isPlaying = playerCtrl.isPlaying
                            )
                            viewModelScope.launch {
                                val uri = currentSongValue.albumArtUriString?.toUri()
                                val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                                themeStateHolder.extractAndGenerateColorScheme(uri, currentUri)
                            }
                            loadLyricsForCurrentSong()
                        }
                    } ?: run {
                        if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                            lyricsStateHolder.cancelLoading()
                            playbackStateHolder.updateStablePlayerState {
                                it.copy(
                                    currentSong = null,
                                    isPlaying = false,
                                    playWhenReady = false,
                                    lyrics = null,
                                    isLoadingLyrics = false,
                                    totalDuration = 0L
                                )
                            }
                            playbackStateHolder.clearCurrentPositionHints()
                            resetPlaybackAudioMetadata()
                        }
                    }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl)
                if (playbackState == Player.STATE_READY) {
                    clearPreparingSongIfMatching(playerCtrl.currentMediaItem?.mediaId)
                    val readyPosition = playerCtrl.currentPosition.coerceAtLeast(0L)
                    val songDurationHint = playbackStateHolder.stablePlayerState.value.currentSong?.duration ?: 0L
                    val resolvedDuration = playbackStateHolder.resolveDurationForPlaybackState(
                        reportedDurationMs = playerCtrl.duration,
                        songDurationHintMs = songDurationHint,
                        currentPositionMs = readyPosition
                    )
                    syncPlaybackPositionFromPlayer(playerCtrl.currentMediaItem?.mediaId, readyPosition)
                    playbackStateHolder.updateStablePlayerState { it.copy(totalDuration = resolvedDuration) }
                    listeningStatsTracker.updateDuration(resolvedDuration)
                    startProgressUpdates()
                }
                if (playbackState == Player.STATE_ENDED) {
                    listeningStatsTracker.finalizeCurrentSession()
                }
                if (playbackState == Player.STATE_IDLE && playerCtrl.mediaItemCount == 0) {
                    clearPreparingSongIfMatching()
                    if (!isCastConnecting.value && !isRemotePlaybackActive.value) {
                        listeningStatsTracker.onPlaybackStopped()
                        lyricsStateHolder.cancelLoading()
                        playbackStateHolder.updateStablePlayerState {
                            it.copy(
                                currentSong = null,
                                isPlaying = false,
                                playWhenReady = false,
                                lyrics = null,
                                isLoadingLyrics = false,
                                totalDuration = 0L
                            )
                        }
                        playbackStateHolder.clearCurrentPositionHints()
                        playbackStateHolder.setCurrentPosition(0L)
                        resetPlaybackAudioMetadata()
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                if (isRemoteSessionControllingPlayback()) return
                refreshPlaybackAudioMetadata(playerCtrl, tracks)
            }
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                // IMPORTANT: We don't use ExoPlayer's shuffle mode anymore
                // Instead, we manually shuffle the queue to fix crossfade issues
                // If ExoPlayer's shuffle gets enabled (e.g., from media button), turn it off and use our toggle
                if (shuffleModeEnabled) {
                    playerCtrl.shuffleModeEnabled = false
                    // Trigger our manual shuffle instead
                    if (!playbackStateHolder.stablePlayerState.value.isShuffleEnabled) {
                        toggleShuffle()
                    }
                }
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                playbackStateHolder.updateStablePlayerState { it.copy(repeatMode = repeatMode) }
                viewModelScope.launch { userPreferencesRepository.setRepeatMode(repeatMode) }
            }
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (isRemoteSessionControllingPlayback()) return
                transitionSchedulerJob?.cancel()
                updateCurrentPlaybackQueueFromPlayer(mediaController)
            }
        }
        playerCtrl.addListener(checkNotNull(mediaControllerPlaybackListener))
        Trace.endSection()
    }


    // rebuildPlayerQueue functionality moved to PlaybackStateHolder (simplified)
    fun playSongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        cancelPendingFullQueuePlayback()
        viewModelScope.launch {
            transitionSchedulerJob?.cancel()

            val validSongs = hydrateSongsIfNeeded(songsToPlay)

            if (validSongs.isEmpty()) {
                _toastEvents.emit(context.getString(R.string.no_valid_songs))
                return@launch
            }

            // Adjust startSong if it was filtered out
            val validStartSong =
                validSongs.firstOrNull { it.id == startSong.id } ?: validSongs.first()

            // Offline check for the starting song if it is a Telegram song
            if (validStartSong.contentUriString.startsWith("telegram:")) {
                ensureTelegramPlaybackObserversStarted()
                val isOnline = connectivityStateHolder.isOnline.value
                val fileId = validStartSong.telegramFileId
                
                Timber.d("Offline Check: fileId=$fileId, contentUri=${validStartSong.contentUriString}, isOnline=$isOnline")

                if (!isOnline) {
                     if (fileId != null) {
                         val isCached = musicRepository.telegramRepository.isFileCached(fileId)
                         Timber.d("Offline Check: isCached=$isCached")
                         if (!isCached) {
                             Timber.w("Blocked playback: Offline and not cached.")
                             _showNoInternetDialog.tryEmit(Unit)
                             return@launch
                         }
                     }
                }
            }

            // Store the original order so we can "unshuffle" later if the user turns shuffle off
            queueStateHolder.setOriginalQueueOrder(validSongs)
            queueStateHolder.saveOriginalQueueState(validSongs, queueName)

            // Check if the user wants shuffle to be persistent across different albums
            val isPersistent = userPreferencesRepository.persistentShuffleEnabledFlow.first()
            // Check if shuffle is currently active in the player
            val isShuffleOn = playbackStateHolder.stablePlayerState.value.isShuffleEnabled

            // If Persistent Shuffle is OFF, we reset shuffle to "false" every time a new album starts
            if (!isPersistent) {
                playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = false) }
            }

            // If shuffle is persistent and currently ON, we shuffle the new songs immediately
            val finalSongsToPlay = if (isPersistent && isShuffleOn) {
                // Shuffle the list but make sure the song you clicked stays at its current index or starts first
                withContext(Dispatchers.Default) {
                    QueueUtils.buildAnchoredShuffleQueueSuspending(
                        validSongs,
                        validSongs.indexOfFirst { it.id == validStartSong.id }.coerceAtLeast(0)
                    )
                }
            } else {
                // Otherwise, just use the normal sequential order
                validSongs
            }

            // Send the final list (shuffled or not) to the player engine
            internalPlaySongs(finalSongsToPlay, validStartSong, queueName, playlistId)
        }
    }

    // Start playback with shuffle enabled in one coroutine to avoid racing queue updates
    fun playSongsShuffled(
        songsToPlay: List<Song>, 
        queueName: String = "None", 
        playlistId: String? = null,
        startAtZero: Boolean = false
    ) {
        cancelPendingFullQueuePlayback()
        viewModelScope.launch {
            val result = queueStateHolder.prepareShuffledQueueSuspending(songsToPlay, queueName, startAtZero)
            if (result == null) {
                sendToast("No songs to shuffle.")
                return@launch
            }

            val (shuffledQueue, startSong) = result
            transitionSchedulerJob?.cancel()

            // Optimistically update shuffle state
            playbackStateHolder.updateStablePlayerState { it.copy(isShuffleEnabled = true) }
            launch { userPreferencesRepository.setShuffleOn(true) }

            internalPlaySongs(shuffledQueue, startSong, queueName, playlistId)
        }
    }

    fun playExternalUri(uri: Uri) {
        viewModelScope.launch {
            val externalResult = externalMediaStateHolder.buildExternalSongFromUri(uri)
            if (externalResult == null) {
                sendToast(context.getString(R.string.external_playback_error))
                return@launch
            }

            transitionSchedulerJob?.cancel()

            val queueSongs = externalMediaStateHolder.buildExternalQueue(externalResult, uri)
            val immutableQueue = queueSongs.toPlaybackQueue()

            _playerUiState.update { state ->
                state.copy(
                    currentPlaybackQueue = immutableQueue,
                    currentQueueSourceName = context.getString(R.string.external_queue_label),
                    showDismissUndoBar = false,
                    dismissedSong = null,
                    dismissedQueue = persistentListOf(),
                    dismissedQueueName = "",
                    dismissedPosition = 0L
                )
            }
            playbackStateHolder.setCurrentPosition(0L)

            playbackStateHolder.updateStablePlayerState { state ->
                state.copy(
                    currentSong = externalResult.song,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = externalResult.song.duration,
                    lyrics = null,
                    isLoadingLyrics = false
                )
            }

            _sheetState.value = PlayerSheetState.COLLAPSED
            _isSheetVisible.value = true

            internalPlaySongs(queueSongs, externalResult.song, context.getString(R.string.external_queue_label), null)
            showPlayer()
        }
    }

    fun showPlayer() {
        if (stablePlayerState.value.currentSong != null) {
            _isSheetVisible.value = true
        }
    }

    private fun setPreparingSong(songId: String?) {
        _playerUiState.update { state ->
            if (state.preparingSongId == songId) state else state.copy(preparingSongId = songId)
        }
    }

    private fun beginPreparingSong(song: Song) {
        setPreparingSong(song.id)
        viewModelScope.launch(Dispatchers.IO) {
            val albumArtUri = song.albumArtUriString
            if (albumArtUri.isNullOrBlank()) {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = null,
                    currentSongUriString = null,
                    isPreload = false
                )
            } else {
                themeStateHolder.extractAndGenerateColorScheme(
                    albumArtUriAsUri = albumArtUri.toUri(),
                    currentSongUriString = albumArtUri,
                    isPreload = false
                )
            }
        }
    }

    private fun clearPreparingSongIfMatching(mediaId: String? = null) {
        val preparingSongId = _playerUiState.value.preparingSongId ?: return
        if (mediaId == null || preparingSongId == mediaId) {
            setPreparingSong(null)
        }
    }

    private suspend fun preparePlaybackQueue(
        songsToPlay: List<Song>,
        startSongId: String,
        playlistId: String?
    ): PreparedPlaybackQueue = withContext(Dispatchers.Default) {
        val mediaItems = ArrayList<MediaItem>(songsToPlay.size)
        var startIndex = 0
        var foundStartIndex = false

        songsToPlay.forEachIndexed { index, song ->
            if (!foundStartIndex && song.id == startSongId) {
                startIndex = index
                foundStartIndex = true
            }

            mediaItems += buildPlaybackMediaItem(song, playlistId)
        }

        PreparedPlaybackQueue(
            mediaItems = mediaItems,
            startIndex = startIndex
        )
    }



    private suspend fun internalPlaySongs(songsToPlay: List<Song>, startSong: Song, queueName: String = "None", playlistId: String? = null) {
        if (songsToPlay.isEmpty()) {
            clearPreparingSongIfMatching()
            return
        }
        val effectiveStartSong = songsToPlay.firstOrNull { it.id == startSong.id } ?: songsToPlay.first()

        // Update dynamic shortcut for last played playlist
        if (playlistId != null && queueName != "None") {
            appShortcutManager.updateLastPlaylistShortcut(playlistId, queueName)
        }

        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            clearPreparingSongIfMatching()
            val remoteLoaded = castTransferStateHolder.playRemoteQueue(
                songsToPlay = songsToPlay,
                startSong = effectiveStartSong,
                isShuffleEnabled = playbackStateHolder.stablePlayerState.value.isShuffleEnabled
            )

            if (!remoteLoaded) {
                Timber.tag(CAST_LOG_TAG).w(
                    "Remote queue load failed in internalPlaySongs (songId=%s queueSize=%d).",
                    effectiveStartSong.id,
                    songsToPlay.size
                )
                castSession.remoteMediaClient?.requestStatus()
                return
            }

            _playerUiState.update { it.copy(currentPlaybackQueue = songsToPlay.toPlaybackQueue(), currentQueueSourceName = queueName) }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
        } else {
            beginPreparingSong(effectiveStartSong)
            _playerUiState.update {
                it.copy(
                    currentPlaybackQueue = songsToPlay.toPlaybackQueue(),
                    currentQueueSourceName = queueName
                )
            }
            playbackStateHolder.updateStablePlayerState {
                it.copy(
                    currentSong = effectiveStartSong,
                    isPlaying = true,
                    playWhenReady = true,
                    totalDuration = effectiveStartSong.duration.coerceAtLeast(0L)
                )
            }
            _isSheetVisible.value = true

            // Pre-resolve the starting song's cloud URI before ExoPlayer touches it.
            // This populates the resolvedUriCache so resolveDataSpec finds it instantly.
            val startingUri = MediaItemBuilder.playbackUri(effectiveStartSong)
            if (
                startingUri.scheme == "telegram" ||
                startingUri.scheme == "netease" ||
                startingUri.scheme == "qqmusic" ||
                startingUri.scheme == "navidrome" ||
                startingUri.scheme == "jellyfin"
            ) {
                if (startingUri.scheme == "telegram") {
                    ensureTelegramPlaybackObserversStarted()
                }
                dualPlayerEngine.resolveCloudUri(startingUri)
            }

            val preparedPlaybackQueue = preparePlaybackQueue(
                songsToPlay = songsToPlay,
                startSongId = effectiveStartSong.id,
                playlistId = playlistId
            )

            val playSongsAction = {
                // Use Direct Engine Access to avoid TransactionTooLargeException on Binder
                val enginePlayer = dualPlayerEngine.masterPlayer

                if (preparedPlaybackQueue.mediaItems.isNotEmpty()) {
                    // Direct access: No IPC limit involved
                    enginePlayer.setMediaItems(
                        preparedPlaybackQueue.mediaItems,
                        preparedPlaybackQueue.startIndex,
                        0L
                    )
                    enginePlayer.prepare()
                    enginePlayer.play()
                } else {
                    clearPreparingSongIfMatching(effectiveStartSong.id)
                }
                _playerUiState.update { it.copy(isLoadingInitialSongs = false) }
            }

            // We still check for mediaController to ensure the Service is bound and active
            // even though we aren't using it for the heavy lifting anymore.
            if (mediaController == null) {
                Timber.w("MediaController not available. Queuing playback action.")
                pendingPlaybackAction = playSongsAction
            } else {
                playSongsAction()
            }
        }
    }

    private suspend fun buildResolvedPlaybackMediaItem(song: Song): MediaItem {
        val mediaItem = MediaItemBuilder.build(song)
        val originalUri = mediaItem.localConfiguration?.uri ?: return mediaItem
        val scheme = originalUri.scheme
        if (
            scheme != "telegram" &&
            scheme != "netease" &&
            scheme != "qqmusic" &&
            scheme != "navidrome" &&
            scheme != "jellyfin"
        ) {
            return mediaItem
        }

        if (scheme == "telegram") {
            ensureTelegramPlaybackObserversStarted()
        }

        val resolvedUri = dualPlayerEngine.resolveCloudUri(originalUri)
        return if (resolvedUri == originalUri) {
            mediaItem
        } else {
            mediaItem.buildUpon().setUri(resolvedUri).build()
        }
    }


    private fun loadAndPlaySong(song: Song) {
        cancelPendingFullQueuePlayback()
        beginPreparingSong(song)
        playbackStateHolder.updateStablePlayerState {
            it.copy(
                currentSong = song,
                isPlaying = true,
                playWhenReady = true
            )
        }
        _isSheetVisible.value = true

        val controller = mediaController
        if (controller == null) {
            pendingPlaybackAction = {
                loadAndPlaySong(song)
            }
            return
        }

        viewModelScope.launch {
            val mediaItem = buildResolvedPlaybackMediaItem(song)
            if (controller.currentMediaItem?.mediaId == song.id) {
                if (!controller.isPlaying) controller.play()
            } else {
                controller.setMediaItem(mediaItem)
                controller.prepare()
                controller.play()
            }
        }
    }

// buildMediaMetadataForSong moved to MediaItemBuilder

    private fun syncShuffleStateWithSession(enabled: Boolean) {
        val controller = mediaController ?: return
        val args = Bundle().apply {
            putBoolean(MusicNotificationProvider.EXTRA_SHUFFLE_ENABLED, enabled)
        }
        controller.sendCustomCommand(
            SessionCommand(MusicNotificationProvider.CUSTOM_COMMAND_SET_SHUFFLE_STATE, Bundle()),
            args
        )
    }

    fun toggleShuffle(currentSongOverride: Song? = null) {
        cancelPendingFullQueuePlayback()
        val currentQueue = _playerUiState.value.currentPlaybackQueue.toList()
        val currentSong = currentSongOverride
            ?: playbackStateHolder.stablePlayerState.value.currentSong
            ?: mediaController?.currentMediaItem?.let { resolveSongFromMediaItem(it) }
            ?: currentQueue.firstOrNull()

        playbackStateHolder.toggleShuffle(
            currentSongs = currentQueue,
            currentSong = currentSong,
            currentQueueSourceName = _playerUiState.value.currentQueueSourceName,
            updateQueueCallback = { newQueue ->
                _playerUiState.update { it.copy(currentPlaybackQueue = newQueue.toPlaybackQueue()) }
            }
        )
    }

    fun cycleRepeatMode() {
        playbackStateHolder.cycleRepeatMode()
    }

    private suspend fun setFavoriteStatusEverywhere(songId: String, isFavorite: Boolean) {
        musicRepository.setFavoriteStatus(songId, isFavorite)
    }

    fun toggleFavorite() {
        playbackStateHolder.stablePlayerState.value.currentSong?.id?.let { songId ->
            viewModelScope.launch {
                val currentlyFavorite = favoriteSongIds.value.contains(songId)
                setFavoriteStatusEverywhere(songId, !currentlyFavorite)
            }
        }
    }

    fun toggleFavoriteSpecificSong(song: Song, removing: Boolean = false) {
        viewModelScope.launch {
            val currentlyFavorite = favoriteSongIds.value.contains(song.id)
            val targetFavoriteState = if (removing) false else !currentlyFavorite
            setFavoriteStatusEverywhere(song.id, targetFavoriteState)
        }
    }

    fun addSongToQueue(song: Song) {
        mediaController?.let { controller ->
            val mediaItem = buildPlaybackMediaItem(song)
            controller.addMediaItem(mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    fun addSongNextToQueue(song: Song) {
        mediaController?.let { controller ->
            val mediaItem = buildPlaybackMediaItem(song)

            val insertionIndex = if (controller.currentMediaItemIndex != C.INDEX_UNSET) {
                (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
            } else {
                controller.mediaItemCount
            }

            controller.addMediaItem(insertionIndex, mediaItem)
            // Queue UI is synced via onTimelineChanged listener
        }
    }

    private fun buildPlaybackMediaItem(song: Song, playlistId: String? = null): MediaItem {
        val baseItem = MediaItemBuilder.build(song)
        if (playlistId == null) {
            return baseItem
        }

        val mergedExtras = Bundle(baseItem.mediaMetadata.extras ?: Bundle()).apply {
            putString("playlistId", playlistId)
        }

        return baseItem.buildUpon()
            .setMediaMetadata(
                baseItem.mediaMetadata.buildUpon()
                    .setExtras(mergedExtras)
                    .build()
            )
            .build()
    }

    // =====================================================
    // Multi-Selection Batch Operations
    // =====================================================

    /**
     * Plays all selected songs, preserving their selection order.
     * Clears selection after starting playback.
     */
    fun playSelectedSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        playSongs(songs, songs.first(), "Selected Songs")
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds all selected songs to the end of the queue.
     * Clears selection after adding.
     */
    fun addSelectedToQueue(songs: List<Song>) {
        songs.forEach { addSongToQueue(it) }
        viewModelScope.launch {
            _toastEvents.emit("${songs.size} songs added to queue")
        }
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Adds all selected songs to play next, preserving selection order.
     * Songs are inserted in reverse order so they play in the correct sequence.
     * Clears selection after adding.
     */
    fun addSelectedAsNext(songs: List<Song>) {
        songs.reversed().forEach { addSongNextToQueue(it) }
        viewModelScope.launch {
            _toastEvents.emit("${songs.size} songs will play next")
        }
        multiSelectionStateHolder.clearSelection()
    }

    /**
     * Resolves songs from selected albums (in selection order), builds a queue, and starts playback.
     * For safety we process at most [MAX_ALBUM_BATCH_SELECTION] albums at once.
     */
    fun queueAndPlaySelectedAlbums(albums: List<Album>) {
        if (albums.isEmpty()) return

        val albumsToProcess = albums.take(MAX_ALBUM_BATCH_SELECTION)
        val wasTrimmed = albums.size > albumsToProcess.size

        viewModelScope.launch {
            try {
                val queuedSongs = withContext(Dispatchers.IO) {
                    buildList {
                        albumsToProcess.forEach { album ->
                            val albumSongs = musicRepository.getSongsForAlbum(album.id).first()
                            if (albumSongs.isNotEmpty()) {
                                addAll(
                                    albumSongs.sortedWith(
                                        compareBy<Song> { it.discNumber }
                                            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                                            .thenBy { it.title.lowercase(java.util.Locale.getDefault()) }
                                    )
                                )
                            }
                        }
                    }
                }

                if (queuedSongs.isEmpty()) {
                    _toastEvents.emit("No playable songs found in selected albums")
                    return@launch
                }

                val queueName = if (albumsToProcess.size == 1) {
                    albumsToProcess.first().title
                } else {
                    "Selected Albums"
                }

                playSongs(queuedSongs, queuedSongs.first(), queueName, null)
                _isSheetVisible.value = true

                if (wasTrimmed) {
                    _toastEvents.emit("Only the first $MAX_ALBUM_BATCH_SELECTION albums were queued")
                } else {
                    _toastEvents.emit("${albumsToProcess.size} albums queued (${queuedSongs.size} songs)")
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error queuing selected albums", e)
                _toastEvents.emit("Could not queue selected albums")
            }
        }
    }

    /**
     * Adds all selected songs to favorites.
     * Clears selection after liking.
     */
    fun likeSelectedSongs(songs: List<Song>) {
        viewModelScope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var likedCount = 0
            songs.forEach { song ->
                if (!favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song.id, true)
                    favIds.add(song.id)
                    likedCount++
                }
            }
            if (likedCount > 0) {
                _toastEvents.emit("$likedCount songs added to favorites")
            } else {
                _toastEvents.emit("All songs already in favorites")
            }
            multiSelectionStateHolder.clearSelection()
        }
    }

    /**
     * Removes all selected songs from favorites.
     * Clears selection after unliking.
     */
    fun unlikeSelectedSongs(songs: List<Song>) {
        viewModelScope.launch {
            val favIds = favoriteSongIds.value.toMutableSet()
            var unlikedCount = 0
            songs.forEach { song ->
                if (favIds.contains(song.id)) {
                    setFavoriteStatusEverywhere(song.id, false)
                    favIds.remove(song.id)
                    unlikedCount++
                }
            }
            if (unlikedCount > 0) {
                _toastEvents.emit("$unlikedCount songs removed from favorites")
            } else {
                _toastEvents.emit("No songs were in favorites")
            }
            multiSelectionStateHolder.clearSelection()
        }
    }

    /**
     * Shares all selected songs as a ZIP file.
     * Clears selection after initiating share.
     */
    fun shareSelectedAsZip(songs: List<Song>) {
        viewModelScope.launch {
            _toastEvents.emit("Creating ZIP file...")

            val result = ZipShareHelper.createAndShareZip(context, songs)

            result.onSuccess {
                multiSelectionStateHolder.clearSelection()
            }.onFailure { error ->
                _toastEvents.emit("Failed to share: ${error.localizedMessage}")
                println(
                    "Failed to share: ${error.localizedMessage}"
                )
            }
        }
    }

    /**
     * Deletes all selected songs from device with confirmation.
     * Shows a single confirmation dialog for all songs.
     */
    private var pendingBatchDeleteSongs: List<Song>? = null
    private var pendingBatchDeleteSkippedCount: Int = 0
    private var pendingBatchDeleteOnComplete: (() -> Unit)? = null

    fun deleteSelectedFromDevice(activity: Activity, songs: List<Song>, onComplete: () -> Unit) {
        viewModelScope.launch {
            // Filter out currently playing song
            val currentSongId = playbackStateHolder.stablePlayerState.value.currentSong?.id
            val deletableSongs = songs.filter { it.id != currentSongId }

            if (deletableSongs.isEmpty()) {
                _toastEvents.emit("Cannot delete currently playing song")
                return@launch
            }

            val skippedCount = songs.size - deletableSongs.size

            // On Android 11+, use system batch delete dialog
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = deletableSongs.mapNotNull { song ->
                    song.id.toLongOrNull()?.let { id ->
                        com.theveloper.pixelplay.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.theveloper.pixelplay.utils.MediaStorePermissionHelper
                        .createDeleteRequestIntentSender(activity, uris)
                    if (intentSender != null) {
                        pendingBatchDeleteSongs = deletableSongs
                        pendingBatchDeleteSkippedCount = skippedCount
                        pendingBatchDeleteOnComplete = onComplete
                        _deletePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            // Fallback for older Android or non-MediaStore songs
            val confirmed = showMultiDeleteConfirmation(activity, deletableSongs.size)
            if (!confirmed) {
                onComplete()
                return@launch
            }

            var successCount = 0
            deletableSongs.forEach { song ->
                val success = metadataEditStateHolder.deleteSong(song)
                if (success) {
                    removeFromMediaControllerQueue(song.id)
                    removeSong(song)
                    successCount++
                }
            }

            when {
                successCount == deletableSongs.size && skippedCount == 0 ->
                    _toastEvents.emit("$successCount files deleted")
                successCount == deletableSongs.size && skippedCount > 0 ->
                    _toastEvents.emit("$successCount files deleted ($skippedCount skipped - playing)")
                successCount > 0 ->
                    _toastEvents.emit("$successCount of ${deletableSongs.size} files deleted")
                else ->
                    _toastEvents.emit("Failed to delete files")
            }

            multiSelectionStateHolder.clearSelection()
            onComplete()
        }
    }

    private suspend fun showMultiDeleteConfirmation(activity: Activity, count: Int): Boolean {
        return withContext(Dispatchers.Main) {
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    return@withContext false
                }

                val userChoice = CompletableDeferred<Boolean>()

                val dialog = MaterialAlertDialogBuilder(activity)
                    .setTitle("Delete $count songs?")
                    .setMessage("These songs will be permanently deleted from your device and cannot be recovered.")
                    .setPositiveButton("Delete") { _, _ ->
                        userChoice.complete(true)
                    }
                    .setNegativeButton("Cancel") { _, _ ->
                        userChoice.complete(false)
                    }
                    .setOnCancelListener {
                        userChoice.complete(false)
                    }
                    .setCancelable(true)
                    .create()

                dialog.show()
                userChoice.await()
            } catch (e: Exception) {
                false
            }
        }
    }

    fun deleteFromDevice(activity: Activity, song: Song, onResult: (Boolean) -> Unit = {}){
        viewModelScope.launch {
            // Failsafe: Prevent deleting the currently playing song
            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                _toastEvents.emit("Cannot delete currently playing song")
                onResult(false)
                return@launch
            }

            // On Android 11+, use the system delete confirmation dialog via MediaStore.createDeleteRequest()
            // which both confirms AND handles deletion in one step (no MANAGE_EXTERNAL_STORAGE needed).
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = com.theveloper.pixelplay.utils.FileDeletionUtils
                    .getDeleteRequestIntentSender(activity, song.path)
                if (intentSender != null) {
                    pendingDeleteSong = song
                    pendingDeleteCallback = onResult
                    _deletePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            // Fallback for older Android or files not in MediaStore
            val userConfirmed = songRemovalStateHolder.showDeleteConfirmation(activity, song)
            if (!userConfirmed) {
                onResult(false)
                return@launch
            }

            val success = songRemovalStateHolder.deleteSongFile(song)
            if (success) {
                _toastEvents.emit("File deleted")
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                onResult(true)
            } else {
                _toastEvents.emit("Can't delete the file or file not found")
                onResult(false)
            }
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore delete request. */
    fun onDeletePermissionResult(granted: Boolean) {
        // Handle batch delete
        val batchSongs = pendingBatchDeleteSongs
        if (batchSongs != null) {
            val skippedCount = pendingBatchDeleteSkippedCount
            val onComplete = pendingBatchDeleteOnComplete
            pendingBatchDeleteSongs = null
            pendingBatchDeleteSkippedCount = 0
            pendingBatchDeleteOnComplete = null
            viewModelScope.launch {
                if (granted) {
                    // System already deleted the files — clean up library
                    batchSongs.forEach { song ->
                        removeFromMediaControllerQueue(song.id)
                        removeSong(song)
                    }
                    val count = batchSongs.size
                    if (skippedCount > 0) {
                        _toastEvents.emit("$count files deleted ($skippedCount skipped - playing)")
                    } else {
                        _toastEvents.emit("$count files deleted")
                    }
                } else {
                    _toastEvents.emit("Deletion cancelled")
                }
                multiSelectionStateHolder.clearSelection()
                onComplete?.invoke()
            }
            return
        }

        // Handle single delete
        val song = pendingDeleteSong ?: return
        val callback = pendingDeleteCallback
        pendingDeleteSong = null
        pendingDeleteCallback = null
        viewModelScope.launch {
            if (granted) {
                // The system already deleted the file — just clean up the library
                _toastEvents.emit("File deleted")
                removeFromMediaControllerQueue(song.id)
                removeSong(song)
                callback?.invoke(true)
            } else {
                callback?.invoke(false)
            }
        }
    }

    suspend fun removeSong(song: Song) {
        toggleFavoriteSpecificSong(song, true)
        playbackStateHolder.setCurrentPosition(0L)
        _playerUiState.update { currentState ->
            currentState.copy(
                currentPlaybackQueue = currentState.currentPlaybackQueue.removeSongById(song.id),
                currentQueueSourceName = ""
            )
        }
        _isSheetVisible.value = false
        songRemovalStateHolder.removeSongFromLibrary(song)
    }

    private fun removeFromMediaControllerQueue(songId: String) {
        val controller = mediaController ?: return

        try {
            // Get the current timeline and media item count
            val timeline = controller.currentTimeline
            val mediaItemCount = timeline.windowCount

            // Find the media item to remove by iterating through windows
            for (i in 0 until mediaItemCount) {
                val window = timeline.getWindow(i, Timeline.Window())
                if (window.mediaItem.mediaId == songId) {
                    // Remove the media item by index
                    controller.removeMediaItem(i)
                    break
                }
            }
        } catch (e: Exception) {
            Log.e("MediaController", "Error removing from queue: ${e.message}")
        }
    }

    private fun hasRemoteQueueItems(remoteMediaClient: RemoteMediaClient): Boolean {
        val mediaQueueCount = remoteMediaClient.mediaQueue?.itemCount ?: 0
        val statusQueueCount = remoteMediaClient.mediaStatus?.queueItems?.size ?: 0
        val snapshotQueueCount = castTransferStateHolder.lastRemoteQueue.size
        return mediaQueueCount > 0 || statusQueueCount > 0 || snapshotQueueCount > 0
    }

    private fun remoteQueueMatchesLocalQueue(
        remoteMediaClient: RemoteMediaClient,
        localQueue: List<Song>,
        localStartSong: Song?
    ): Boolean {
        if (localQueue.isEmpty()) return true

        val localQueueIds = localQueue.map { it.id }
        val status = remoteMediaClient.mediaStatus
        val remoteQueueIdsFromStatus = status
            ?.queueItems
            ?.mapNotNull { item ->
                item.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            .orEmpty()
        val remoteQueueIdsFromSnapshot = castTransferStateHolder.lastRemoteQueue.map { it.id }

        val queueMatches = when {
            remoteQueueIdsFromStatus.size == localQueueIds.size ->
                remoteQueueIdsFromStatus == localQueueIds
            remoteQueueIdsFromSnapshot.size == localQueueIds.size ->
                remoteQueueIdsFromSnapshot == localQueueIds
            remoteQueueIdsFromStatus.isNotEmpty() -> false
            remoteQueueIdsFromSnapshot.isNotEmpty() -> false
            else -> false
        }

        if (!queueMatches) return false

        val expectedSongId = localStartSong?.id ?: return true
        val remoteCurrentSongId = status
            ?.let { mediaStatus ->
                mediaStatus.getQueueItemById(mediaStatus.getCurrentItemId())
                    ?.customData
                    ?.optString("songId")
                    ?.takeIf { it.isNotBlank() }
            }
            ?: castTransferStateHolder.lastRemoteSongId

        return remoteCurrentSongId == null || remoteCurrentSongId == expectedSongId
    }

    fun playPause() {
        val castSession = castStateHolder.castSession.value
        if (castSession != null && castSession.remoteMediaClient != null) {
            val remoteMediaClient = castSession.remoteMediaClient!!
            if (remoteMediaClient.isPlaying) {
                castStateHolder.castPlayer?.pause()
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        isPlaying = false,
                        playWhenReady = false
                    )
                }
            } else {
                val localQueue = _playerUiState.value.currentPlaybackQueue.toList()
                val startSong = playbackStateHolder.stablePlayerState.value.currentSong ?: localQueue.firstOrNull()
                val remoteHasQueue = hasRemoteQueueItems(remoteMediaClient)
                val remoteQueueAligned = remoteQueueMatchesLocalQueue(remoteMediaClient, localQueue, startSong)
                val shouldResumeRemoteQueue = remoteHasQueue && (localQueue.isEmpty() || remoteQueueAligned)

                if (shouldResumeRemoteQueue) {
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else if (localQueue.isNotEmpty() && startSong != null) {
                    Timber.tag(CAST_LOG_TAG).i(
                        "Remote queue out of sync. Reloading remote queue (local=%d status=%d snapshot=%d).",
                        localQueue.size,
                        remoteMediaClient.mediaStatus?.queueItems?.size ?: 0,
                        castTransferStateHolder.lastRemoteQueue.size
                    )
                    viewModelScope.launch {
                        internalPlaySongs(localQueue, startSong, _playerUiState.value.currentQueueSourceName)
                    }
                } else if (remoteHasQueue) {
                    // No local queue available to reconcile; fallback to resuming remote queue.
                    castStateHolder.castPlayer?.play()
                    playbackStateHolder.updateStablePlayerState {
                        it.copy(
                            isPlaying = true,
                            playWhenReady = true
                        )
                    }
                } else {
                    Timber.tag(CAST_LOG_TAG).w("Cannot resume Cast playback: both local and remote queues are empty.")
                }
            }
        } else {
            mediaController?.let { controller ->
                if (controller.isPlaying) {
                    controller.pause()
                } else {
                    if (controller.currentMediaItem == null) {
                        val currentQueue = _playerUiState.value.currentPlaybackQueue
                        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong
                        when {
                            currentQueue.isNotEmpty() && currentSong != null -> {
                                viewModelScope.launch {
                                    transitionSchedulerJob?.cancel()
                                    internalPlaySongs(
                                        currentQueue.toList(),
                                        currentSong,
                                        _playerUiState.value.currentQueueSourceName
                                    )
                                }
                            }
                            currentSong != null -> {
                                loadAndPlaySong(currentSong)
                            }
                            libraryStateHolder.allSongs.value.isNotEmpty() -> {
                                loadAndPlaySong(libraryStateHolder.allSongs.value.first())
                            }
                            else -> {
                                controller.play()
                            }
                        }
                    } else {
                        controller.play()
                    }
                }
            }
        }
    }

    fun seekTo(position: Long) {
        playbackStateHolder.seekTo(position)
    }

    fun nextSong() {
        playbackStateHolder.nextSong()
    }

    fun previousSong() {
        playbackStateHolder.previousSong()
    }

    private fun startProgressUpdates() {
        playbackStateHolder.startProgressUpdates()
    }

    private fun stopProgressUpdates() {
        playbackStateHolder.stopProgressUpdates()
    }

    fun observeSongs(songIds: List<String>): Flow<List<Song>> {
        return musicRepository.getSongsByIds(songIds)
    }

    suspend fun getSongs(songIds: List<String>) : List<Song>{
        return musicRepository.getSongsByIds(songIds).first()
    }

    //Sorting
    fun sortSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortSongs(sortOption, persist)
    }

    fun sortAlbums(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortAlbums(sortOption, persist)
    }

    fun sortArtists(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortArtists(sortOption, persist)
    }

    fun sortFavoriteSongs(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFavoriteSongs(sortOption, persist)
    }

    fun sortFolders(sortOption: SortOption, persist: Boolean = true) {
        libraryStateHolder.sortFolders(sortOption, persist)
    }

    fun setFoldersPlaylistView(isPlaylistView: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFoldersPlaylistView(isPlaylistView)
            folderNavigationStateHolder.setFoldersPlaylistViewState(
                isPlaylistView = isPlaylistView,
                updateUiState = { mutation -> _playerUiState.update(mutation) }
            )
        }
    }

    fun setFoldersSource(source: FolderSource) {
        if (!ENABLE_FOLDERS_SOURCE_SWITCHING) return
        viewModelScope.launch {
            userPreferencesRepository.setFoldersSource(source)
        }
    }

    fun navigateToFolder(path: String) {
        folderNavigationStateHolder.navigateToFolder(
            path = path,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun navigateBackFolder() {
        folderNavigationStateHolder.navigateBackFolder(
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            onFolderChanged = { folderPath ->
                folderNavigationStateHolder.hydrateCurrentFolderSongsIfNeeded(
                    scope = viewModelScope,
                    folderPath = folderPath,
                    getUiState = { _playerUiState.value },
                    updateUiState = { mutation -> _playerUiState.update(mutation) },
                    requiresHydration = { song -> song.requiresHydration() },
                    hydrateSongs = { songs -> hydrateSongsIfNeeded(songs) }
                )
            }
        )
    }

    fun setAlbumsListView(isList: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumsListView(isList)
        }
    }

    fun updateSearchFilter(filterType: SearchFilterType) {
        searchStateHolder.updateSearchFilter(filterType)
    }

    fun loadSearchHistory(limit: Int = 15) {
        searchStateHolder.loadSearchHistory(limit)
    }

    fun onSearchQuerySubmitted(query: String) {
        searchStateHolder.onSearchQuerySubmitted(query)
    }

    fun performSearch(query: String) {
        searchStateHolder.performSearch(query)
    }

    fun deleteSearchHistoryItem(query: String) {
        searchStateHolder.deleteSearchHistoryItem(query)
    }

    fun clearSearchHistory() {
        searchStateHolder.clearSearchHistory()
    }

    // --- AI Playlist Generation ---

    // --- AI Playlist Generation ---

    fun showAiPlaylistSheet() {
        aiStateHolder.showAiPlaylistSheet()
    }

    fun dismissAiPlaylistSheet() {
        aiStateHolder.dismissAiPlaylistSheet()
    }

    fun clearAiPlaylistError() {
        aiStateHolder.clearAiPlaylistError()
    }

    fun generateAiPlaylist(
        prompt: String,
        minLength: Int,
        maxLength: Int,
        saveAsPlaylist: Boolean = false,
        playlistName: String? = null
    ) {
        aiStateHolder.generateAiPlaylist(
            prompt = prompt,
            minLength = minLength,
            maxLength = maxLength,
            saveAsPlaylist = saveAsPlaylist,
            playlistName = playlistName
        )
    }

    fun regenerateDailyMixWithPrompt(prompt: String) {
        aiStateHolder.regenerateDailyMixWithPrompt(prompt)
    }

    fun retryLastPlaylistGeneration() {
        aiStateHolder.retryLastPlaylistGeneration()
    }

    fun retryLastMetadataGeneration() {
        aiStateHolder.retryLastMetadataGeneration()
    }

    fun clearQueueExceptCurrent() {
        mediaController?.let { controller ->
            val currentSongIndex = controller.currentMediaItemIndex
            if (currentSongIndex == C.INDEX_UNSET) return@let
            val indicesToRemove = (0 until controller.mediaItemCount)
                .filter { it != currentSongIndex }
                .sortedDescending()

            for (index in indicesToRemove) {
                controller.removeMediaItem(index)
            }
        }
    }

    fun selectRoute(route: MediaRouter.RouteInfo) {
        castRouteStateHolder.selectRoute(route) { message ->
            viewModelScope.launch { _toastEvents.emit(message) }
        }
    }

    fun disconnect(resetConnecting: Boolean = true) {
        castRouteStateHolder.disconnect(resetConnecting = resetConnecting)
    }

    fun setRouteVolume(volume: Int) {
        castRouteStateHolder.setRouteVolume(volume)
    }

    fun refreshCastRoutes() {
        castRouteStateHolder.refreshCastRoutes(viewModelScope)
    }



    override fun onCleared() {
        mediaControllerPlaybackListener?.let { listener ->
            mediaController?.removeListener(listener)
            mediaControllerPlaybackListener = null
        }
        playbackStateHolder.setMediaController(null)
        mediaController?.release()
        mediaController = null
        mediaControllerFuture.cancel(true)
        super.onCleared()
        remoteQueueLoadJob?.cancel()
        castSongUiSyncJob?.cancel()
        stopProgressUpdates()
        listeningStatsTracker.onCleared()
        castTransferStateHolder.onCleared()
        castStateHolder.onCleared()
        searchStateHolder.onCleared()
        aiStateHolder.onCleared()
        libraryStateHolder.onCleared()
        sleepTimerStateHolder.onCleared()
        connectivityStateHolder.onCleared()
        queueUndoStateHolder.onCleared()
        playlistDismissUndoStateHolder.onCleared()
    }

    // Sleep Timer Control Functions - delegated to SleepTimerStateHolder
    fun setSleepTimer(durationMinutes: Int) {
        sleepTimerStateHolder.setSleepTimer(durationMinutes)
    }

    fun playCounted(count: Int) {
        sleepTimerStateHolder.playCounted(count)
    }

    fun cancelCountedPlay() {
        sleepTimerStateHolder.cancelCountedPlay()
    }

    fun setEndOfTrackTimer(enable: Boolean) {
        val currentSongId = stablePlayerState.value.currentSong?.id
        sleepTimerStateHolder.setEndOfTrackTimer(enable, currentSongId)
    }

    fun cancelSleepTimer(overrideToastMessage: String? = null, suppressDefaultToast: Boolean = false) {
        sleepTimerStateHolder.cancelSleepTimer(overrideToastMessage, suppressDefaultToast)
    }

    fun dismissPlaylistAndShowUndo() {
        playlistDismissUndoStateHolder.dismissPlaylistAndShowUndo(
            scope = viewModelScope,
            currentSong = playbackStateHolder.stablePlayerState.value.currentSong,
            queue = _playerUiState.value.currentPlaybackQueue,
            queueName = _playerUiState.value.currentQueueSourceName,
            position = playbackStateHolder.currentPosition.value,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            disconnectRemoteIfNeeded = {
                val hasCastSession = castStateHolder.castSession.value != null
                val shouldDisconnectRemote = hasCastSession ||
                    castStateHolder.isRemotePlaybackActive.value ||
                    castStateHolder.isCastConnecting.value
                if (shouldDisconnectRemote) {
                    if (hasCastSession) {
                        castTransferStateHolder.skipNextTransferBack()
                    }
                    disconnect()
                }
            },
            clearPlayback = {
                mediaController?.stop()
                mediaController?.clearMediaItems()
            },
            clearStablePlaybackState = {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = null,
                        isPlaying = false,
                        playWhenReady = false,
                        totalDuration = 0L
                    )
                }
            },
            setCurrentPosition = { playbackStateHolder.setCurrentPosition(it) },
            setSheetVisible = { _isSheetVisible.value = it }
        )
    }

    fun hideDismissUndoBar() {
        playlistDismissUndoStateHolder.hideDismissUndoBar { mutation ->
            _playerUiState.update(mutation)
        }
    }

    fun undoDismissPlaylist() {
        playlistDismissUndoStateHolder.undoDismissPlaylist(
            scope = viewModelScope,
            getUiState = { _playerUiState.value },
            updateUiState = { mutation -> _playerUiState.update(mutation) },
            playSongs = { songs, startSong, queueName ->
                playSongs(songs, startSong, queueName)
            },
            seekTo = { position -> mediaController?.seekTo(position) },
            setSheetVisible = { _isSheetVisible.value = it },
            setSheetCollapsed = { _sheetState.value = PlayerSheetState.COLLAPSED },
            emitToast = { message -> _toastEvents.emit(message) }
        )
    }

    fun getSongUrisForGenre(genreId: String): Flow<List<String>> {
        return musicRepository.getMusicByGenre(genreId).map { songs ->
            songs.take(4).mapNotNull { it.albumArtUriString?.takeIf { uri -> uri.isNotBlank() } }
        }
    }

    fun saveLastLibraryTabIndex(tabIndex: Int) {
        viewModelScope.launch {
            userPreferencesRepository.saveLastLibraryTabIndex(tabIndex)
        }
    }

    fun showSortingSheet() {
        libraryTabsStateHolder.showSortingSheet(_isSortingSheetVisible)
    }

    fun hideSortingSheet() {
        libraryTabsStateHolder.hideSortingSheet(_isSortingSheetVisible)
    }

    fun onLibraryTabSelected(tabIndex: Int) {
        libraryTabsStateHolder.onLibraryTabSelected(
            tabIndex = tabIndex,
            libraryTabs = libraryTabsFlow.value,
            loadedTabs = _loadedTabs,
            currentLibraryTabId = _currentLibraryTabId,
            saveLastTabIndex = { index -> userPreferencesRepository.saveLastLibraryTabIndex(index) },
            scope = viewModelScope,
            loadSongs = { loadSongsIfNeeded() },
            loadAlbums = { loadAlbumsIfNeeded() },
            loadArtists = { loadArtistsIfNeeded() },
            loadFolders = { loadFoldersFromRepository() }
        )
    }

    fun saveLibraryTabsOrder(tabs: List<String>) {
        viewModelScope.launch {
            val orderJson = Json.encodeToString(tabs)
            userPreferencesRepository.saveLibraryTabsOrder(orderJson)
        }
    }

    fun resetLibraryTabsOrder() {
        viewModelScope.launch {
            userPreferencesRepository.resetLibraryTabsOrder()
        }
    }

    fun selectSongForInfo(song: Song) {
        _selectedSongForInfo.value = song
        viewModelScope.launch {
            val hydrated = withContext(Dispatchers.IO) {
                musicRepository.getSong(song.id).first()
            } ?: return@launch
            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = hydrated
            }
        }
    }

    private fun loadLyricsForCurrentSong() {
        val currentSong = playbackStateHolder.stablePlayerState.value.currentSong ?: return
        // Delegate to LyricsStateHolder
        lyricsStateHolder.loadLyricsForSong(currentSong, lyricsSourcePreference.value)
    }

    fun editSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String? = null,
        newReplayGainAlbumGainDb: String? = null,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        viewModelScope.launch {
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Starting editSongMetadata via Holder")

            // On Android 11+, request MediaStore write permission for local songs
            val songId = song.id.toLongOrNull()
            if (songId != null && songId > 0 && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val intentSender = com.theveloper.pixelplay.utils.MediaStorePermissionHelper
                    .createWriteRequestForSong(context, songId)
                if (intentSender != null) {
                    // Store pending edit and request permission from the UI
                    pendingMetadataEdit = PendingMetadataEdit(
                        song = song,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        genre = newGenre,
                        lyrics = newLyrics,
                        trackNumber = newTrackNumber,
                        discNumber = newDiscNumber,
                        replayGainTrackGainDb = newReplayGainTrackGainDb,
                        replayGainAlbumGainDb = newReplayGainAlbumGainDb,
                        coverArtUpdate = coverArtUpdate
                    )
                    _writePermissionRequest.emit(intentSender)
                    return@launch
                }
            }

            performMetadataEdit(song, newTitle, newArtist, newAlbum, newGenre, newLyrics,
                newTrackNumber, newDiscNumber, newReplayGainTrackGainDb, newReplayGainAlbumGainDb, coverArtUpdate)
        }
    }

    /** Called from the UI after the user approves or denies the MediaStore write permission. */
    fun onWritePermissionResult(granted: Boolean) {
        // Handle batch genre edit
        val batchGenre = pendingBatchGenreEdit
        if (batchGenre != null) {
            pendingBatchGenreEdit = null
            if (!granted) {
                viewModelScope.launch { _toastEvents.emit("Permission denied – cannot edit files") }
                return
            }
            viewModelScope.launch { performBatchEditGenre(batchGenre.first, batchGenre.second) }
            return
        }

        // Handle single metadata edit
        val pending = pendingMetadataEdit ?: return
        pendingMetadataEdit = null
        if (!granted) {
            viewModelScope.launch {
                _toastEvents.emit("Permission denied – cannot edit this file")
            }
            return
        }
        viewModelScope.launch {
            performMetadataEdit(
                pending.song, pending.title, pending.artist, pending.album,
                pending.genre, pending.lyrics, pending.trackNumber, pending.discNumber,
                pending.replayGainTrackGainDb, pending.replayGainAlbumGainDb, pending.coverArtUpdate
            )
        }
    }

    private suspend fun performMetadataEdit(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newGenre: String,
        newLyrics: String,
        newTrackNumber: Int,
        newDiscNumber: Int?,
        newReplayGainTrackGainDb: String?,
        newReplayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?,
    ) {
        val previousAlbumArt = song.albumArtUriString

        val result = metadataEditStateHolder.saveMetadata(
            song = song,
            newTitle = newTitle,
            newArtist = newArtist,
            newAlbum = newAlbum,
            newGenre = newGenre,
            newLyrics = newLyrics,
            newTrackNumber = newTrackNumber,
            newDiscNumber = newDiscNumber,
            newReplayGainTrackGainDb = newReplayGainTrackGainDb,
            newReplayGainAlbumGainDb = newReplayGainAlbumGainDb,
            coverArtUpdate = coverArtUpdate
        )

        Log.e("PlayerViewModel", "METADATA_EDIT_VM: Result success=${result.success}")

        if (result.success && result.updatedSong != null) {
            val updatedSong = result.updatedSong
            val refreshedAlbumArtUri = result.updatedAlbumArtUri

            invalidateCoverArtCaches(previousAlbumArt, refreshedAlbumArtUri)

            _playerUiState.update { state ->
                val updatedQueue = state.currentPlaybackQueue.replaceSong(updatedSong)
                if (updatedQueue === state.currentPlaybackQueue) {
                    state
                } else {
                    state.copy(currentPlaybackQueue = updatedQueue)
                }
            }

            // libraryStateHolder.updateSong() below handles the SSOT update

            // Update the LibraryStateHolder which drives the UI
            libraryStateHolder.updateSong(updatedSong)

            if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                playbackStateHolder.updateStablePlayerState {
                    it.copy(
                        currentSong = updatedSong,
                        lyrics = result.parsedLyrics
                    )
                }

                // Update the player's current MediaItem to refresh notification artwork
                // This is efficient: only replaces metadata, not the media stream
                val controller = playbackStateHolder.mediaController
                if (controller != null) {
                    val currentIndex = controller.currentMediaItemIndex
                    if (currentIndex >= 0 && currentIndex < controller.mediaItemCount) {
                        val currentPosition = controller.currentPosition
                        val newMediaItem = MediaItemBuilder.build(updatedSong)
                        controller.replaceMediaItem(currentIndex, newMediaItem)
                        // Restore position since replaceMediaItem may reset it
                        controller.seekTo(currentIndex, currentPosition)
                    }
                }
            }

            if (_selectedSongForInfo.value?.id == song.id) {
                _selectedSongForInfo.value = updatedSong
            }

            if (coverArtUpdate != null) {
                purgeAlbumArtThemes(previousAlbumArt, updatedSong.albumArtUriString)
                val paletteTargetUri = updatedSong.albumArtUriString
                if (paletteTargetUri != null) {
                    themeStateHolder.getAlbumColorSchemeFlow(paletteTargetUri)
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(paletteTargetUri.toUri(), currentUri, isPreload = false)
                } else {
                    val currentUri = playbackStateHolder.stablePlayerState.value.currentSong?.albumArtUriString
                    themeStateHolder.extractAndGenerateColorScheme(null, currentUri, isPreload = false)
                }
            }

            // No need for full library sync - file, MediaStore, and local DB are already updated
            // syncManager.sync() was removed to avoid unnecessary wait time
            _toastEvents.emit("Metadata updated successfully")
        } else {
            val errorMessage = result.getUserFriendlyErrorMessage()
            Log.e("PlayerViewModel", "METADATA_EDIT_VM: Failed - ${result.error}: $errorMessage")
            _toastEvents.emit(errorMessage)
        }
    }

    private fun invalidateCoverArtCaches(vararg uriStrings: String?) {
        imageCacheManager.invalidateCoverArtCaches(*uriStrings)
    }

    private suspend fun purgeAlbumArtThemes(vararg uriStrings: String?) {
        val uris = uriStrings.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
        if (uris.isEmpty()) return

        withContext(Dispatchers.IO) {
            albumArtThemeDao.deleteThemesByUris(uris)
        }

        uris.forEach { uri ->
            // Cache invalidation delegated to ThemeStateHolder (if implemented) or relied on re-generation
            // individualAlbumColorSchemes was removed.
        }
    }

    suspend fun forceRegenerateAlbumPaletteForSong(song: Song): Boolean {
        val albumArtUri = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return false
        return runCatching {
            // Full reset: clear all cached variants for this URI and recreate every style from scratch.
            themeStateHolder.forceRegenerateColorScheme(
                uriString = albumArtUri,
                regenerateAllStyles = true
            )
            true
        }.getOrDefault(false)
    }

    suspend fun generateAiMetadata(song: Song, fields: List<String>): Result<SongMetadata> {
        return aiStateHolder.generateAiMetadata(song, fields)
    }

    private fun updateSongInStates(
        updatedSong: Song,
        newLyrics: Lyrics? = null,
        isLoadingLyrics: Boolean? = null
    ) {
        // Update the queue first
        val currentQueue = _playerUiState.value.currentPlaybackQueue
        val updatedQueue = currentQueue.replaceSong(updatedSong)

        if (updatedQueue !== currentQueue) {
            _playerUiState.update { it.copy(currentPlaybackQueue = updatedQueue) }
        }

        // Then, update the stable state
        playbackStateHolder.updateStablePlayerState { state ->
            // Only update lyrics if they are explicitly passed
            val finalLyrics = newLyrics ?: state.lyrics
            state.copy(
                currentSong = updatedSong,
                lyrics = if (state.currentSong?.id == updatedSong.id) finalLyrics else state.lyrics,
                isLoadingLyrics = isLoadingLyrics ?: state.isLoadingLyrics
            )
        }
    }

    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    /**
     * Busca la letra de la canción actual en el servicio remoto.
     */
    fun fetchLyricsForCurrentSong(forcePickResults: Boolean = false) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.fetchLyricsForSong(currentSong, forcePickResults, lyricsSourcePreference.value) { resId ->
            context.getString(resId)
        }
    }

    /**
     * Manual search lyrics using query provided by user (title and artist)
     */
    fun searchLyricsManually(title: String, artist: String? = null) {
        lyricsStateHolder.searchLyricsManually(title, artist)
    }

    fun acceptLyricsSearchResultForCurrentSong(result: LyricsSearchResult) {
        val currentSong = stablePlayerState.value.currentSong ?: return
        lyricsStateHolder.acceptLyricsSearchResult(result, currentSong)
    }

    fun resetLyricsForCurrentSong() {
        val songId = stablePlayerState.value.currentSong?.id?.toLongOrNull() ?: return
        lyricsStateHolder.resetLyrics(songId)
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    fun resetAllLyrics() {
        lyricsStateHolder.resetAllLyrics()
        playbackStateHolder.updateStablePlayerState { state -> state.copy(lyrics = null) }
    }

    /**
     * Procesa la letra importada de un archivo, la guarda y actualiza la UI.
     * @param songId El ID de la canción para la que se importa la letra.
     * @param lyricsContent El contenido de la letra como String.
     */
    fun importLyricsFromFile(songId: Long, validatedImport: ValidatedLyricsImport) {
        val currentSong = stablePlayerState.value.currentSong
        lyricsStateHolder.importLyricsFromFile(songId, validatedImport, currentSong)
    }

    /**
     * Resetea el estado de la búsqueda de letras a Idle.
     */
    fun resetLyricsSearchState() {
        lyricsStateHolder.resetSearchState()
    }

    private fun onBlockedDirectoriesChanged() {
        viewModelScope.launch {
            musicRepository.invalidateCachesDependentOnAllowedDirectories()
            resetAndLoadInitialData("Blocked directories changed")
        }
    }

    fun playSong(song: Song) {
        viewModelScope.launch {
            val controller = mediaController ?: return@launch
            val mediaItem = buildResolvedPlaybackMediaItem(song)

            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()

            _isSheetVisible.value = true
            _sheetState.value = PlayerSheetState.EXPANDED
        }
    }

    private var pendingBatchGenreEdit: Pair<List<Song>, String>? = null

    fun batchEditGenre(songs: List<Song>, newGenre: String) {
        if (songs.isEmpty()) return

        viewModelScope.launch {
            // On Android 11+, request write permission for all local songs upfront
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val uris = songs.mapNotNull { song ->
                    song.id.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
                        com.theveloper.pixelplay.utils.MediaStorePermissionHelper.getMediaStoreUri(id)
                    }
                }
                if (uris.isNotEmpty()) {
                    val intentSender = com.theveloper.pixelplay.utils.MediaStorePermissionHelper
                        .createWriteRequestIntentSender(context, uris)
                    if (intentSender != null) {
                        pendingBatchGenreEdit = songs to newGenre
                        _writePermissionRequest.emit(intentSender)
                        return@launch
                    }
                }
            }

            performBatchEditGenre(songs, newGenre)
        }
    }

    private suspend fun performBatchEditGenre(songs: List<Song>, newGenre: String) {
            Log.d("PlayerViewModel", "Starting batch genre update for ${songs.size} songs to '$newGenre'")
            _toastEvents.emit("Updating ${songs.size} songs...")

            var successCount = 0
            var failCount = 0

            songs.forEach { song ->
                val sourceSong = if (song.lyrics != null) {
                    song
                } else {
                    withContext(Dispatchers.IO) {
                        musicRepository.getSong(song.id).first()
                    } ?: song
                }

                val result = metadataEditStateHolder.saveMetadata(
                    song = sourceSong,
                    newTitle = sourceSong.title,
                    newArtist = sourceSong.artist,
                    newAlbum = sourceSong.album,
                    newGenre = newGenre,
                    newLyrics = sourceSong.lyrics ?: "",
                    newTrackNumber = sourceSong.trackNumber,
                    newDiscNumber =  sourceSong.discNumber,
                    coverArtUpdate = null
                )

                if (result.success && result.updatedSong != null) {
                    successCount++
                    val updatedSong = result.updatedSong

                    // Optimistic update of UI flows
                    // libraryStateHolder.updateSong() below handles the SSOT update
                    libraryStateHolder.updateSong(updatedSong)

                    if (playbackStateHolder.stablePlayerState.value.currentSong?.id == song.id) {
                        playbackStateHolder.updateStablePlayerState { it.copy(currentSong = updatedSong) }
                        val controller = playbackStateHolder.mediaController
                        if (controller != null) {
                            val idx = controller.currentMediaItemIndex
                            if (idx != C.INDEX_UNSET) {
                                controller.replaceMediaItem(idx, MediaItemBuilder.build(updatedSong))
                            }
                        }
                    }
                } else {
                    failCount++
                }
            }

            if (failCount == 0) {
                _toastEvents.emit("Successfully updated $successCount songs!")
            } else {
                _toastEvents.emit("Updated $successCount songs. Failed: $failCount")
            }
    }

    // Custom Genres Names
    val customGenres: StateFlow<Set<String>> = userPreferencesRepository.customGenresFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    val customGenreIcons: StateFlow<Map<String, Int>> = userPreferencesRepository.customGenreIconsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val isGenreGridView: StateFlow<Boolean> = userPreferencesRepository.isGenreGridViewFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )

    fun toggleGenreViewMode() {
        viewModelScope.launch {
            userPreferencesRepository.setGenreGridView(!isGenreGridView.value)
        }
    }

    fun addCustomGenre(genre: String, iconResId: Int? = null) {
        viewModelScope.launch {
            userPreferencesRepository.addCustomGenre(genre, iconResId)
        }
    }
}

internal fun Song.withRepositoryHydration(repositorySong: Song): Song {
    if (id != repositorySong.id) return this

    return repositorySong.copy(
        contentUriString = repositorySong.contentUriString.ifBlank { contentUriString },
        albumArtUriString = repositorySong.albumArtUriString ?: albumArtUriString,
        duration = repositorySong.duration.takeIf { it > 0L } ?: duration,
        lyrics = repositorySong.lyrics ?: lyrics
    )
}

internal fun Song.improvesLyricsLookupComparedTo(previousSong: Song): Boolean {
    return (previousSong.lyrics.isNullOrBlank() && !lyrics.isNullOrBlank()) ||
        (previousSong.path.isBlank() && path.isNotBlank()) ||
        (previousSong.contentUriString.isBlank() && contentUriString.isNotBlank())
}

internal fun parsePersistedLyrics(rawLyrics: String?): Lyrics? {
    val normalizedLyrics = rawLyrics?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val parsedLyrics = LyricsUtils.parseLyrics(normalizedLyrics)
    return parsedLyrics.takeIf {
        !it.synced.isNullOrEmpty() || !it.plain.isNullOrEmpty()
    }
}
