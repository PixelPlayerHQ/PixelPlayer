package com.theveloper.pixelplay.presentation.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.backup.BackupManager
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupOperationType
import com.theveloper.pixelplay.data.backup.model.BackupTransferProgressUpdate
import com.theveloper.pixelplay.data.backup.model.BackupHistoryEntry
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.backup.model.RestoreResult
import com.theveloper.pixelplay.data.backup.model.ValidationError
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.ai.GeminiModel
import com.theveloper.pixelplay.data.ai.provider.AiClientFactory
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.model.Song
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val albumArtPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val resumeOnHeadsetReconnect: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = false,
    val crossfadeDuration: Int = 2000,
    val persistentShuffleEnabled: Boolean = false,
    val folderBackGestureNavigation: Boolean = false,
    val lyricsSourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val availableModels: List<GeminiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val appRebrandDialogShown: Boolean = false,
    val beta05CleanInstallDisclaimerDismissed: Boolean? = null,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val showPlayerFileInfo: Boolean = true,
    val usePlayerSheetV2: Boolean = true,
    // Developer Options
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val tapBackgroundClosesPlayer: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val immersiveLyricsEnabled: Boolean = false,
    val immersiveLyricsTimeout: Long = 4000L,
    val useAnimatedLyrics: Boolean = false,
    val animatedLyricsBlurEnabled: Boolean = true,
    val animatedLyricsBlurStrength: Float = 2.5f,
    val backupInfoDismissed: Boolean = false,
    val isDataTransferInProgress: Boolean = false,
    val restorePlan: RestorePlan? = null,
    val backupHistory: List<BackupHistoryEntry> = emptyList(),
    val backupValidationErrors: List<ValidationError> = emptyList(),
    val isInspectingBackup: Boolean = false,
    val collagePattern: CollagePattern = CollagePattern.default,
    val collageAutoRotate: Boolean = false,
    val minSongDuration: Int = 10000,
    val replayGainEnabled: Boolean = false,
    val replayGainUseAlbumGain: Boolean = false
)

data class FailedSongInfo(
    val id: String,
    val title: String,
    val artist: String
)

data class LyricsRefreshProgress(
    val totalSongs: Int = 0,
    val currentCount: Int = 0,
    val savedCount: Int = 0,
    val notFoundCount: Int = 0,
    val skippedCount: Int = 0,
    val isComplete: Boolean = false,
    val failedSongs: List<FailedSongInfo> = emptyList()
) {
    val hasProgress: Boolean get() = totalSongs > 0
    val progress: Float get() = if (totalSongs > 0) currentCount.toFloat() / totalSongs else 0f
    val hasFailedSongs: Boolean get() = failedSongs.isNotEmpty()
}

// Helper classes for consolidated combine() collectors to reduce coroutine overhead
private sealed interface SettingsUiUpdate {
    data class Group1(
        val appRebrandDialogShown: Boolean,
        val appThemeMode: String,
        val playerThemePreference: String,
        val albumArtPaletteStyle: AlbumArtPaletteStyle,
        val mockGenresEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val libraryNavigationMode: String,
        val carouselStyle: String,
        val launchTab: String,
        val showPlayerFileInfo: Boolean
    ) : SettingsUiUpdate
    
    data class Group2(
        val keepPlayingInBackground: Boolean,
        val disableCastAutoplay: Boolean,
        val resumeOnHeadsetReconnect: Boolean,
        val showQueueHistory: Boolean,
        val isCrossfadeEnabled: Boolean,
        val crossfadeDuration: Int,
        val persistentShuffleEnabled: Boolean,
        val folderBackGestureNavigation: Boolean,
        val lyricsSourcePreference: LyricsSourcePreference,
        val autoScanLrcFiles: Boolean,
        val blockedDirectories: Set<String>,
        val hapticsEnabled: Boolean,
        val immersiveLyricsEnabled: Boolean,
        val immersiveLyricsTimeout: Long,
        val animatedLyricsBlurEnabled: Boolean,
        val animatedLyricsBlurStrength: Float
    ) : SettingsUiUpdate
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val syncManager: SyncManager,
    private val aiClientFactory: AiClientFactory,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: BackupManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    val geminiApiKey: StateFlow<String> = aiPreferencesRepository.geminiApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiModel: StateFlow<String> = aiPreferencesRepository.geminiModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val geminiSystemPrompt: StateFlow<String> = aiPreferencesRepository.geminiSystemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT)

    val deepseekSystemPrompt: StateFlow<String> = aiPreferencesRepository.deepseekSystemPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_DEEPSEEK_SYSTEM_PROMPT)
    
    // AI Provider Settings
    val aiProvider: StateFlow<String> = aiPreferencesRepository.aiProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "GEMINI")
    
    val deepseekApiKey: StateFlow<String> = aiPreferencesRepository.deepseekApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    
    val deepseekModel: StateFlow<String> = aiPreferencesRepository.deepseekModel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    private var hasPendingDirectoryRuleChanges = false
    private var latestDirectoryRuleUpdateJob: Job? = null

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val syncProgress: StateFlow<SyncProgress> = syncManager.syncProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SyncProgress()
        )

    private val _dataTransferEvents = MutableSharedFlow<String>()
    val dataTransferEvents: SharedFlow<String> = _dataTransferEvents.asSharedFlow()

    init {
        viewModelScope.launch {
            backupManager.getBackupHistory().collect { history ->
                _uiState.update { it.copy(backupHistory = history) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collagePatternFlow.collect { pattern ->
                _uiState.update { it.copy(collagePattern = pattern) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.collageAutoRotateFlow.collect { autoRotate ->
                _uiState.update { it.copy(collageAutoRotate = autoRotate) }
            }
        }
    }

    private val _dataTransferProgress = MutableStateFlow<BackupTransferProgressUpdate?>(null)
    val dataTransferProgress: StateFlow<BackupTransferProgressUpdate?> = _dataTransferProgress.asStateFlow()

    init {
        // Consolidated collectors using combine() to reduce coroutine overhead
        // Instead of 20 separate coroutines, we use 2 combined flows
        
        // Group 1: Core UI settings (theme, navigation, appearance)
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group1>(
                userPreferencesRepository.appRebrandDialogShownFlow,
                themePreferencesRepository.appThemeModeFlow,
                themePreferencesRepository.playerThemePreferenceFlow,
                themePreferencesRepository.albumArtPaletteStyleFlow,
                userPreferencesRepository.mockGenresEnabledFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.libraryNavigationModeFlow,
                userPreferencesRepository.carouselStyleFlow,
                userPreferencesRepository.launchTabFlow,
                userPreferencesRepository.showPlayerFileInfoFlow
            ) { values ->
                SettingsUiUpdate.Group1(
                    appRebrandDialogShown = values[0] as Boolean,
                    appThemeMode = values[1] as String,
                    playerThemePreference = values[2] as String,
                    albumArtPaletteStyle = values[3] as AlbumArtPaletteStyle,
                    mockGenresEnabled = values[4] as Boolean,
                    navBarCornerRadius = values[5] as Int,
                    navBarStyle = values[6] as String,
                    libraryNavigationMode = values[7] as String,
                    carouselStyle = values[8] as String,
                    launchTab = values[9] as String,
                    showPlayerFileInfo = values[10] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        appRebrandDialogShown = update.appRebrandDialogShown,
                        appThemeMode = update.appThemeMode,
                        playerThemePreference = update.playerThemePreference,
                        albumArtPaletteStyle = update.albumArtPaletteStyle,
                        mockGenresEnabled = update.mockGenresEnabled,
                        navBarCornerRadius = update.navBarCornerRadius,
                        navBarStyle = update.navBarStyle,
                        libraryNavigationMode = update.libraryNavigationMode,
                        carouselStyle = update.carouselStyle,
                        launchTab = update.launchTab,
                        showPlayerFileInfo = update.showPlayerFileInfo
                    )
                }
            }
        }
        
        // Group 2: Playback and system settings
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group2>(
                userPreferencesRepository.keepPlayingInBackgroundFlow,
                userPreferencesRepository.disableCastAutoplayFlow,
                userPreferencesRepository.resumeOnHeadsetReconnectFlow,
                userPreferencesRepository.showQueueHistoryFlow,
                userPreferencesRepository.isCrossfadeEnabledFlow,
                userPreferencesRepository.crossfadeDurationFlow,
                userPreferencesRepository.persistentShuffleEnabledFlow,
                userPreferencesRepository.folderBackGestureNavigationFlow,
                userPreferencesRepository.lyricsSourcePreferenceFlow,
                userPreferencesRepository.autoScanLrcFilesFlow,
                userPreferencesRepository.blockedDirectoriesFlow,
                userPreferencesRepository.hapticsEnabledFlow,
                userPreferencesRepository.immersiveLyricsEnabledFlow,
                userPreferencesRepository.immersiveLyricsTimeoutFlow,
                userPreferencesRepository.animatedLyricsBlurEnabledFlow,
                userPreferencesRepository.animatedLyricsBlurStrengthFlow
            ) { values ->
                SettingsUiUpdate.Group2(
                    keepPlayingInBackground = values[0] as Boolean,
                    disableCastAutoplay = values[1] as Boolean,
                    resumeOnHeadsetReconnect = values[2] as Boolean,
                    showQueueHistory = values[3] as Boolean,
                    isCrossfadeEnabled = values[4] as Boolean,
                    crossfadeDuration = values[5] as Int,
                    persistentShuffleEnabled = values[6] as Boolean,
                    folderBackGestureNavigation = values[7] as Boolean,
                    lyricsSourcePreference = values[8] as LyricsSourcePreference,
                    autoScanLrcFiles = values[9] as Boolean,
                    blockedDirectories = @Suppress("UNCHECKED_CAST") (values[10] as Set<String>),
                    hapticsEnabled = values[11] as Boolean,
                    immersiveLyricsEnabled = values[12] as Boolean,
                    immersiveLyricsTimeout = values[13] as Long,
                    animatedLyricsBlurEnabled = values[14] as Boolean,
                    animatedLyricsBlurStrength = values[15] as Float
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        keepPlayingInBackground = update.keepPlayingInBackground,
                        disableCastAutoplay = update.disableCastAutoplay,
                        resumeOnHeadsetReconnect = update.resumeOnHeadsetReconnect,
                        showQueueHistory = update.showQueueHistory,
                        isCrossfadeEnabled = update.isCrossfadeEnabled,
                        crossfadeDuration = update.crossfadeDuration,
                        persistentShuffleEnabled = update.persistentShuffleEnabled,
                        folderBackGestureNavigation = update.folderBackGestureNavigation,
                        lyricsSourcePreference = update.lyricsSourcePreference,
                        autoScanLrcFiles = update.autoScanLrcFiles,
                        blockedDirectories = update.blockedDirectories,
                        hapticsEnabled = update.hapticsEnabled,
                        immersiveLyricsEnabled = update.immersiveLyricsEnabled,
                        immersiveLyricsTimeout = update.immersiveLyricsTimeout,
                        animatedLyricsBlurEnabled = update.animatedLyricsBlurEnabled,
                        animatedLyricsBlurStrength = update.animatedLyricsBlurStrength
                    )
                }
            }
        }
        
        // Group 3: Remaining individual collectors (loading state, tweaks)
        viewModelScope.launch {
            userPreferencesRepository.fullPlayerLoadingTweaksFlow.collect { tweaks ->
                _uiState.update { it.copy(fullPlayerLoadingTweaks = tweaks) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.usePlayerSheetV2Flow.collect { enabled ->
                _uiState.update { it.copy(usePlayerSheetV2 = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.useAnimatedLyricsFlow.collect { enabled ->
                _uiState.update { it.copy(useAnimatedLyrics = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.backupInfoDismissedFlow.collect { dismissed ->
                _uiState.update { it.copy(backupInfoDismissed = dismissed) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.beta05CleanInstallDisclaimerDismissedFlow.collect { dismissed ->
                _uiState.update { it.copy(beta05CleanInstallDisclaimerDismissed = dismissed) }
            }
        }

        viewModelScope.launch {
            fileExplorerStateHolder.isLoading.collect { loading ->
                _uiState.update { it.copy(isLoadingDirectories = loading) }
            }
        }

        // Beta Features Collectors
        viewModelScope.launch {
            userPreferencesRepository.albumArtQualityFlow.collect { quality ->
                _uiState.update { it.copy(albumArtQuality = quality) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.tapBackgroundClosesPlayerFlow.collect { enabled ->
                _uiState.update { it.copy(tapBackgroundClosesPlayer = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.minSongDurationFlow.collect { duration ->
                _uiState.update { it.copy(minSongDuration = duration) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.replayGainEnabledFlow.collect { enabled ->
                _uiState.update { it.copy(replayGainEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            userPreferencesRepository.replayGainUseAlbumGainFlow.collect { useAlbum ->
                _uiState.update { it.copy(replayGainUseAlbumGain = useAlbum) }
            }
        }
    }

    fun setAppRebrandDialogShown(wasShown: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAppRebrandDialogShown(wasShown)
        }
    }

    fun setBeta05CleanInstallDisclaimerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBeta05CleanInstallDisclaimerDismissed(dismissed)
        }
    }

    fun toggleDirectoryAllowed(file: File) {
        hasPendingDirectoryRuleChanges = true
        latestDirectoryRuleUpdateJob = viewModelScope.launch {
            fileExplorerStateHolder.toggleDirectoryAllowed(file)
        }
    }

    fun applyPendingDirectoryRuleChanges() {
        if (!hasPendingDirectoryRuleChanges) return
        hasPendingDirectoryRuleChanges = false
        viewModelScope.launch {
            latestDirectoryRuleUpdateJob?.join()
            syncManager.forceRefresh()
        }
    }

    fun loadDirectory(file: File) {
        fileExplorerStateHolder.loadDirectory(file)
    }

    fun primeExplorer() {
        fileExplorerStateHolder.primeExplorerRoot()
    }

    fun navigateUp() {
        fileExplorerStateHolder.navigateUp()
    }

    fun refreshExplorer() {
        fileExplorerStateHolder.refreshCurrentDirectory()
    }

    fun selectStorage(index: Int) {
        fileExplorerStateHolder.selectStorage(index)
    }

    fun refreshAvailableStorages() {
        fileExplorerStateHolder.refreshAvailableStorages()
    }

    fun isAtRoot(): Boolean = fileExplorerStateHolder.isAtRoot()

    fun explorerRoot(): File = fileExplorerStateHolder.rootDirectory()

    // Método para guardar la preferencia de tema del reproductor
    fun setPlayerThemePreference(preference: String) {
        viewModelScope.launch {
            themePreferencesRepository.setPlayerThemePreference(preference)
        }
    }

    fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteStyle(style)
        }
    }

    fun setCollagePattern(pattern: CollagePattern) {
        viewModelScope.launch {
            userPreferencesRepository.setCollagePattern(pattern)
        }
    }

    fun setCollageAutoRotate(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCollageAutoRotate(enabled)
        }
    }

    fun setAppThemeMode(mode: String) {
        viewModelScope.launch {
            themePreferencesRepository.setAppThemeMode(mode)
        }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setLibraryNavigationMode(mode: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLibraryNavigationMode(mode)
        }
    }

    fun setCarouselStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setCarouselStyle(style)
        }
    }

    fun setShowPlayerFileInfo(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowPlayerFileInfo(show)
        }
    }

    fun setLaunchTab(tab: String) {
        viewModelScope.launch {
            userPreferencesRepository.setLaunchTab(tab)
        }
    }

    fun setKeepPlayingInBackground(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setKeepPlayingInBackground(enabled)
        }
    }

    fun setDisableCastAutoplay(disabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDisableCastAutoplay(disabled)
        }
    }

    fun setResumeOnHeadsetReconnect(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setResumeOnHeadsetReconnect(enabled)
        }
    }

    fun setShowQueueHistory(show: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setShowQueueHistory(show)
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeEnabled(enabled)
        }
    }

    fun setCrossfadeDuration(duration: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setCrossfadeDuration(duration)
        }
    }

    fun setPersistentShuffleEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setPersistentShuffleEnabled(enabled)
        }
    }

    fun setFolderBackGestureNavigation(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFolderBackGestureNavigation(enabled)
        }
    }

    fun setLyricsSourcePreference(preference: LyricsSourcePreference) {
        viewModelScope.launch {
            userPreferencesRepository.setLyricsSourcePreference(preference)
        }
    }

    fun setAutoScanLrcFiles(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoScanLrcFiles(enabled)
        }
    }

    fun setDelayAllFullPlayerContent(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAllFullPlayerContent(enabled)
        }
    }

    fun setDelayAlbumCarousel(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayAlbumCarousel(enabled)
        }
    }

    fun setDelaySongMetadata(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelaySongMetadata(enabled)
        }
    }

    fun setDelayProgressBar(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayProgressBar(enabled)
        }
    }

    fun setDelayControls(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setDelayControls(enabled)
        }
    }

    fun setFullPlayerPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholders(enabled)
            if (!enabled) {
                userPreferencesRepository.setTransparentPlaceholders(false)
            }
        }
    }

    fun setTransparentPlaceholders(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTransparentPlaceholders(enabled)
        }
    }

    fun setFullPlayerPlaceholdersOnClose(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerPlaceholdersOnClose(enabled)
        }
    }

    fun setFullPlayerSwitchOnDragRelease(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerSwitchOnDragRelease(enabled)
        }
    }

    fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerAppearThreshold(thresholdPercent)
        }
    }

    fun setFullPlayerCloseThreshold(thresholdPercent: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setFullPlayerCloseThreshold(thresholdPercent)
        }
    }

    fun setUsePlayerSheetV2(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUsePlayerSheetV2(enabled)
        }
    }

    fun setUseAnimatedLyrics(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseAnimatedLyrics(enabled)
        }
    }

    fun setAnimatedLyricsBlurEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurEnabled(enabled)
        }
    }

    fun setAnimatedLyricsBlurStrength(strength: Float) {
        viewModelScope.launch {
            userPreferencesRepository.setAnimatedLyricsBlurStrength(strength)
        }
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.forceRefresh()
        }
    }



    /**
     * Performs a full library rescan - rescans all files from scratch.
     * Use when songs are missing or metadata is incorrect.
     */
    fun fullSyncLibrary() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.fullSync()
        }
    }

    fun setMinSongDuration(durationMs: Int) {
        viewModelScope.launch {
            if (durationMs == _uiState.value.minSongDuration) return@launch
            userPreferencesRepository.setMinSongDuration(durationMs)
            // Trigger a library rescan so the change takes effect in the database
            syncManager.fullSync()
        }
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainEnabled(enabled)
        }
    }

    fun setReplayGainUseAlbumGain(useAlbumGain: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setReplayGainUseAlbumGain(useAlbumGain)
        }
    }

    fun setImmersiveLyricsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsEnabled(enabled)
        }
    }

    fun setImmersiveLyricsTimeout(timeout: Long) {
        viewModelScope.launch {
            userPreferencesRepository.setImmersiveLyricsTimeout(timeout)
        }
    }

    /**
     * Completely rebuilds the database from scratch.
     * Clears all data including user edits (lyrics, favorites) and rescans.
     * Use when database is corrupted or as a last resort.
     */
    fun rebuildDatabase() {
        viewModelScope.launch {
            if (isSyncing.value) return@launch
            syncManager.rebuildDatabase()
        }
    }

    fun onGeminiApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiApiKey(apiKey)

            // Fetch models when API key changes and is not empty
            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, "GEMINI")
            } else {
                // Clear models if API key is empty
                _uiState.update {
                    it.copy(
                        availableModels = emptyList(),
                        modelsFetchError = null
                    )
                }
                aiPreferencesRepository.setGeminiModel("")
            }
        }
    }
    
    fun onAiProviderChange(provider: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiProvider(provider)

            // Fetch models for the selected provider
            val apiKey = when (provider) {
                "GEMINI" -> geminiApiKey.value
                "DEEPSEEK" -> deepseekApiKey.value
                else -> ""
            }

            _uiState.update {
                it.copy(
                    availableModels = emptyList(),
                    modelsFetchError = null
                )
            }

            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, provider)
            }
        }
    }
    
    fun onDeepseekApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setDeepseekApiKey(apiKey)
            
            // Fetch models when API key changes and is not empty
            if (apiKey.isNotBlank() && aiProvider.value == "DEEPSEEK") {
                fetchAvailableModels(apiKey, "DEEPSEEK")
            } else if (apiKey.isBlank()) {
                // Clear models if API key is empty
                _uiState.update {
                    it.copy(
                        availableModels = emptyList(),
                        modelsFetchError = null
                    )
                }
                aiPreferencesRepository.setDeepseekModel("")
            }
        }
    }
    
    fun onDeepseekModelChange(model: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setDeepseekModel(model)
        }
    }

    fun onDeepseekSystemPromptChange(prompt: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setDeepseekSystemPrompt(prompt)
        }
    }

    fun resetDeepseekSystemPrompt() {
        viewModelScope.launch {
            aiPreferencesRepository.resetDeepseekSystemPrompt()
        }
    }

    fun fetchAvailableModels(apiKey: String, providerName: String = aiProvider.value) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelsFetchError = null) }

            val result = runCatching {
                val provider = AiProvider.fromString(providerName)
                val client = aiClientFactory.createClient(provider, apiKey)
                client.getAvailableModels(apiKey).map { modelName ->
                    GeminiModel(name = modelName, displayName = formatModelDisplayName(modelName))
                }
            }

            result.onSuccess { rawModels ->
                val models = rawModels.distinctBy { it.name }
                _uiState.update {
                    it.copy(
                        availableModels = models,
                        isLoadingModels = false,
                        modelsFetchError = null
                    )
                }

                // Auto-select first model if none is selected
                val currentModel = when (providerName) {
                    "DEEPSEEK" -> aiPreferencesRepository.deepseekModel.first()
                    else -> aiPreferencesRepository.geminiModel.first()
                }
                if (currentModel.isEmpty() && models.isNotEmpty()) {
                    when (providerName) {
                        "DEEPSEEK" -> aiPreferencesRepository.setDeepseekModel(models.first().name)
                        else -> aiPreferencesRepository.setGeminiModel(models.first().name)
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = error.message ?: "Failed to fetch models"
                    )
                }
            }
        }
    }

    private fun formatModelDisplayName(modelName: String): String {
        return modelName
            .removePrefix("models/")
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }

    fun onGeminiModelChange(modelName: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiModel(modelName)
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCornerRadius(radius)
        }
    }

    fun onGeminiSystemPromptChange(prompt: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setGeminiSystemPrompt(prompt)
        }
    }

    fun resetGeminiSystemPrompt() {
        viewModelScope.launch {
            aiPreferencesRepository.resetGeminiSystemPrompt()
        }
    }



    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException("Test crash triggered from Developer Options - This is intentional for testing the crash reporting system")
    }

    fun resetSetupFlow() {
        viewModelScope.launch {
            userPreferencesRepository.setInitialSetupDone(false)
        }
    }

    // ===== Developer Options =====

    val albumArtQuality: StateFlow<AlbumArtQuality> = userPreferencesRepository.albumArtQualityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AlbumArtQuality.MEDIUM)

    val useSmoothCorners: StateFlow<Boolean> = userPreferencesRepository.useSmoothCornersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tapBackgroundClosesPlayer: StateFlow<Boolean> = userPreferencesRepository.tapBackgroundClosesPlayerFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAlbumArtQuality(quality: AlbumArtQuality) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtQuality(quality)
        }
    }

    fun setUseSmoothCorners(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setUseSmoothCorners(enabled)
        }
    }

    fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setTapBackgroundClosesPlayer(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHapticsEnabled(enabled)
        }
    }

    fun setBackupInfoDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setBackupInfoDismissed(dismissed)
        }
    }

    fun exportAppData(uri: Uri, sections: Set<BackupSection>) {
        if (sections.isEmpty() || _uiState.value.isDataTransferInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.EXPORT,
                step = 0,
                totalSteps = 1,
                title = "Preparing backup",
                detail = "Starting backup task."
            )
            val result = backupManager.export(uri, sections) { progress ->
                _dataTransferProgress.value = progress
            }
            result.fold(
                onSuccess = { _dataTransferEvents.emit("Data exported successfully") },
                onFailure = { _dataTransferEvents.emit("Export failed: ${it.localizedMessage ?: "Unknown error"}") }
            )
            delay(300)
            _uiState.update { it.copy(isDataTransferInProgress = false) }
            _dataTransferProgress.value = null
        }
    }

    fun inspectBackupFile(uri: Uri) {
        if (_uiState.value.isInspectingBackup) return
        viewModelScope.launch {
            _uiState.update { it.copy(isInspectingBackup = true, backupValidationErrors = emptyList(), restorePlan = null) }
            val result = backupManager.inspectBackup(uri)
            result.fold(
                onSuccess = { plan ->
                    _uiState.update { it.copy(restorePlan = plan, isInspectingBackup = false) }
                },
                onFailure = { error ->
                    _dataTransferEvents.emit("Invalid backup: ${error.localizedMessage ?: "Unknown error"}")
                    _uiState.update { it.copy(isInspectingBackup = false) }
                }
            )
        }
    }

    fun updateRestorePlanSelection(selectedModules: Set<BackupSection>) {
        _uiState.update { state ->
            state.restorePlan?.let { plan ->
                state.copy(restorePlan = plan.copy(selectedModules = selectedModules))
            } ?: state
        }
    }

    fun restoreFromPlan(uri: Uri) {
        val plan = _uiState.value.restorePlan ?: return
        if (plan.selectedModules.isEmpty() || _uiState.value.isDataTransferInProgress) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDataTransferInProgress = true) }
            _dataTransferProgress.value = BackupTransferProgressUpdate(
                operation = BackupOperationType.IMPORT,
                step = 0,
                totalSteps = 1,
                title = "Preparing restore",
                detail = "Starting restore task."
            )
            val result = backupManager.restore(uri, plan) { progress ->
                _dataTransferProgress.value = progress
            }
            when (result) {
                is RestoreResult.Success -> {
                    _dataTransferEvents.emit("Data restored successfully")
                    syncManager.sync()
                }
                is RestoreResult.PartialFailure -> {
                    val failedNames = result.failed.entries.joinToString { "${it.key.label}: ${it.value}" }
                    _dataTransferEvents.emit(
                        "Restore completed with unresolved issues. Failed: $failedNames"
                    )
                    if (result.succeeded.isNotEmpty() || !result.rolledBack) {
                        syncManager.sync()
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _dataTransferEvents.emit("Restore failed: ${result.error}")
                }
            }
            delay(300)
            _uiState.update { it.copy(isDataTransferInProgress = false, restorePlan = null) }
            _dataTransferProgress.value = null
        }
    }

    fun clearRestorePlan() {
        _uiState.update { it.copy(restorePlan = null, backupValidationErrors = emptyList()) }
    }

    fun removeBackupHistoryEntry(entry: BackupHistoryEntry) {
        viewModelScope.launch {
            backupManager.removeBackupHistoryEntry(entry.uri)
        }
    }
}
