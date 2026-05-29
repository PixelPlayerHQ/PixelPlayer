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
import com.theveloper.pixelplay.data.database.AiUsageDao
import com.theveloper.pixelplay.data.database.AiUsageEntity
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import com.theveloper.pixelplay.data.preferences.AlbumArtQuality
import com.theveloper.pixelplay.data.preferences.AlbumArtColorAccuracy
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.FullPlayerLoadingTweaks
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.repository.LyricsRepository
import com.theveloper.pixelplay.data.ai.AiDeviceCapabilities
import com.theveloper.pixelplay.data.ai.local.LocalModelCatalog
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelStatus
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.ai.AiModel
import com.theveloper.pixelplay.data.ai.AiHandler
import com.theveloper.pixelplay.data.ai.AiNotificationManager
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.player.HiFiCapabilityChecker
import com.theveloper.pixelplay.utils.AppLocaleManager
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemePair
import java.io.File

data class SettingsUiState(
    val isLoadingDirectories: Boolean = false,
    val appLanguageTag: String = AppLanguage.SYSTEM.tag,
    val appThemeMode: String = AppThemeMode.FOLLOW_SYSTEM,
    val playerThemePreference: String = ThemePreference.ALBUM_ART,
    val albumArtPaletteStyle: AlbumArtPaletteStyle = AlbumArtPaletteStyle.default,
    val albumArtColorAccuracy: Int = AlbumArtColorAccuracy.DEFAULT,
    val mockGenresEnabled: Boolean = false,
    val navBarCornerRadius: Int = 32,
    val navBarStyle: String = NavBarStyle.DEFAULT,
    val navBarCompactMode: Boolean = false,
    val carouselStyle: String = CarouselStyle.NO_PEEK,
    val libraryNavigationMode: String = LibraryNavigationMode.TAB_ROW,
    val launchTab: String = LaunchTab.HOME,
    val keepPlayingInBackground: Boolean = true,
    val disableCastAutoplay: Boolean = false,
    val resumeOnHeadsetReconnect: Boolean = false,
    val showQueueHistory: Boolean = true,
    val isCrossfadeEnabled: Boolean = false,
    val hiFiModeEnabled: Boolean = false,
    val hiFiModeDeviceSupported: Boolean = true,
    val crossfadeDuration: Int = 2000,
    val persistentShuffleEnabled: Boolean = false,
    val folderBackGestureNavigation: Boolean = true,
    val lyricsSourcePreference: LyricsSourcePreference = LyricsSourcePreference.EMBEDDED_FIRST,
    val autoScanLrcFiles: Boolean = false,
    val blockedDirectories: Set<String> = emptySet(),
    val availableModels: List<AiModel> = emptyList(),
    val isLoadingModels: Boolean = false,
    val modelsFetchError: String? = null,
    val appRebrandDialogShown: Boolean = false,
    val beta05CleanInstallDisclaimerDismissed: Boolean? = null,
    val fullPlayerLoadingTweaks: FullPlayerLoadingTweaks = FullPlayerLoadingTweaks(),
    val showPlayerFileInfo: Boolean = true,
    // Developer Options
    val albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM,
    val albumArtCacheLimitMb: Int = 200,
    val tapBackgroundClosesPlayer: Boolean = false,
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
    val minTracksPerAlbum: Int = 1,
    val replayGainEnabled: Boolean = false,
    val replayGainUseAlbumGain: Boolean = false,
    val isSafeTokenLimitEnabled: Boolean = true,
    // AI Preferences
    val aiProvider: String = "GEMINI",
    val currentApiKey: String = "",
    val currentModel: String = "",
    val aiTemperature: Float = 0.7f,
    val aiMaxTokens: Int = 2048,
    val aiEnableStreaming: Boolean = true,
    val aiIncludeContext: Boolean = true,
    val maxSongsForContext: Int = AiPreferencesRepository.DEFAULT_MAX_SONGS_FOR_CONTEXT,
    val includeLikedSongs: Boolean = true,
    val includeDailyMixHistory: Boolean = true,
    val includeUserHabits: Boolean = true,
    val localMlEnabled: Boolean = false,
    val localMlActiveModelId: String = "",
    val localMlSelectedModelId: String = "",
    val localMlFallbackToRemote: Boolean = true,
    val localMlUseGpu: Boolean = false,
    val localMlContextSize: Int = AiPreferencesRepository.DEFAULT_LOCAL_MODEL_CONTEXT_SIZE,
    val localMlOllamaUrl: String = "https://ollama.ai/api",
    val localMlHfToken: String = "",
    val localMlSupported: Boolean = true,
    val localMlSupportMessage: String = "",
    val availableLocalModels: List<com.theveloper.pixelplay.data.ai.local.LocalModelInfo> = emptyList(),
    val localModelStatuses: Map<String, com.theveloper.pixelplay.data.ai.local.ModelStatus> = emptyMap(),
    // Advanced AI settings
    val maxSongsForContextMin: Int = AiPreferencesRepository.MIN_SONGS_FOR_CONTEXT,
    val maxSongsForContextMax: Int = AiPreferencesRepository.MAX_SONGS_FOR_CONTEXT,
    val aiCacheMaxEntriesMin: Int = AiPreferencesRepository.MIN_CACHE_MAX_ENTRIES,
    val aiCacheMaxEntriesMax: Int = AiPreferencesRepository.MAX_CACHE_MAX_ENTRIES,
    val aiCacheTtlHoursMin: Int = AiPreferencesRepository.MIN_CACHE_TTL_HOURS,
    val aiCacheTtlHoursMax: Int = AiPreferencesRepository.MAX_CACHE_TTL_HOURS,
    val aiCacheMaxEntries: Int = AiPreferencesRepository.DEFAULT_CACHE_MAX_ENTRIES,
    val aiCacheTtlHours: Int = AiPreferencesRepository.DEFAULT_CACHE_TTL_HOURS,
    val aiCacheEnabled: Boolean = true,
    val localModelDownloadTimeoutMs: Long = AiPreferencesRepository.DEFAULT_LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS.toLong(),
    // Usage analytics
    val aiUsageTotalInputTokens: Long = 0L,
    val aiUsageTotalOutputTokens: Long = 0L,
    val aiUsageTotalApiCalls: Long = 0L,
    val aiUsageEstimatedCost: String = "0.00",
    // Advanced generation parameters
    val aiTopK: Int = AiPreferencesRepository.DEFAULT_TOP_K,
    val aiTopP: Float = 0.95f,
    val aiRepetitionPenalty: Float = 1.0f,
    val aiFrequencyPenalty: Float = 0.0f,
    val aiPresencePenalty: Float = 0.0f,
    // Telemetry / Data collection
    val telemetryIncludeSkipCount: Boolean = false,
    val telemetryIncludeCompletionRate: Boolean = false,
    val telemetryIncludeSessionDuration: Boolean = false,
    val telemetryIncludeTimeOfDay: Boolean = false,
    val telemetryIncludeGenreAffinity: Boolean = false,
    val telemetryIncludeArtistAffinity: Boolean = false,
    val telemetryIncludeReplayCount: Boolean = false,
    val telemetryIncludeQueuePatterns: Boolean = false
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
        val albumArtColorAccuracy: Int,
        val mockGenresEnabled: Boolean,
        val navBarCornerRadius: Int,
        val navBarStyle: String,
        val navBarCompactMode: Boolean,
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
        val hiFiModeEnabled: Boolean,
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

private sealed interface AiSettingsUpdate {
    data class GroupA(
        val isSafeTokenLimitEnabled: Boolean,
        val localMlEnabled: Boolean,
        val localMlActiveModelId: String,
        val localMlFallbackToRemote: Boolean,
        val localMlUseGpu: Boolean,
        val localMlContextSize: Int,
        val localMlOllamaUrl: String,
        val localMlHfToken: String,
        val aiProvider: String,
        val currentApiKey: String,
        val currentModel: String,
        val aiTemperature: Int,
        val aiMaxTokens: Int,
        val aiEnableStreaming: Boolean,
        val aiIncludeContext: Boolean,
        val localModelDownloadTimeoutMs: Long,
        val localMlSelectedModelId: String,
        val aiTopK: Int,
        val aiTopP: Int,
        val aiRepetitionPenalty: Int,
        val aiFrequencyPenalty: Int,
        val aiPresencePenalty: Int
    ) : AiSettingsUpdate

    data class GroupB(
        val aiCacheEnabled: Boolean,
        val aiCacheMaxEntries: Int,
        val aiCacheTtlHours: Int,
        val aiUsageTotalInputTokens: Long,
        val aiUsageTotalOutputTokens: Long,
        val aiUsageTotalApiCalls: Long,
        val aiUsageEstimatedCost: String,
        val telemetryIncludeSkipCount: Boolean,
        val telemetryIncludeCompletionRate: Boolean,
        val telemetryIncludeSessionDuration: Boolean,
        val telemetryIncludeTimeOfDay: Boolean,
        val telemetryIncludeGenreAffinity: Boolean,
        val telemetryIncludeArtistAffinity: Boolean,
        val telemetryIncludeReplayCount: Boolean,
        val telemetryIncludeQueuePatterns: Boolean
    ) : AiSettingsUpdate
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val aiDeviceCapabilities: AiDeviceCapabilities,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor,
    private val syncManager: SyncManager,
    private val aiHandler: AiHandler,
    private val aiUsageDao: AiUsageDao,
    private val lyricsRepository: LyricsRepository,
    private val musicRepository: MusicRepository,
    private val backupManager: BackupManager,
    private val localMlManager: com.theveloper.pixelplay.data.ai.local.LocalModelManager,
    private val notificationManager: AiNotificationManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var previousModelStatuses: Map<String, ModelStatus> = emptyMap()

    // AI Provider State
    val aiProvider: StateFlow<String> = aiPreferencesRepository.aiProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "GEMINI")

    // Generic AI settings reactive to the selected provider
    val currentAiApiKey: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiModel: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getModel(AiProvider.fromString(provider)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val currentAiSystemPrompt: StateFlow<String> = aiProvider
        .flatMapLatest { provider -> aiPreferencesRepository.getSystemPrompt(AiProvider.fromString(provider)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT)



    // Local Model StateFlows
    val availableLocalModels: StateFlow<List<LocalModelInfo>> = _uiState
        .map { it.availableLocalModels }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val localModelStatuses: StateFlow<Map<String, ModelStatus>> = localMlManager.statusMap
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val localMlSelectedModelId: StateFlow<String> = aiPreferencesRepository.localMlSelectedModelId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // Cache configuration StateFlows
    val aiCacheEnabled: StateFlow<Boolean> = aiPreferencesRepository.aiCacheEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val aiCacheMaxEntries: StateFlow<Int> = aiPreferencesRepository.aiCacheMaxEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_CACHE_MAX_ENTRIES)

    val aiCacheTtlHours: StateFlow<Int> = aiPreferencesRepository.aiCacheTtlHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_CACHE_TTL_HOURS)

    val localModelDownloadTimeoutMs: StateFlow<Long> = aiPreferencesRepository.localModelDownloadTimeoutMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_LOCAL_MODEL_DOWNLOAD_TIMEOUT_MS.toLong())

    // Usage analytics StateFlows
    val aiUsageTotalInputTokens: StateFlow<Long> = aiPreferencesRepository.aiUsageTotalInputTokens
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val aiUsageTotalOutputTokens: StateFlow<Long> = aiPreferencesRepository.aiUsageTotalOutputTokens
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val aiUsageTotalApiCalls: StateFlow<Long> = aiPreferencesRepository.aiUsageTotalApiCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val aiUsageEstimatedCost: StateFlow<String> = aiPreferencesRepository.aiUsageEstimatedCost
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0.00")

    // Telemetry StateFlows (for DataCollectionCard)
    val telemetryIncludeSkipCount: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeSkipCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeCompletionRate: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeCompletionRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeSessionDuration: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeSessionDuration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeTimeOfDay: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeTimeOfDay
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeGenreAffinity: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeGenreAffinity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeArtistAffinity: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeArtistAffinity
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeReplayCount: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeReplayCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val telemetryIncludeQueuePatterns: StateFlow<Boolean> = aiPreferencesRepository.telemetryIncludeQueuePatterns
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onAiApiKeyChange(apiKey: String) {
        viewModelScope.launch {
            val providerStr = aiProvider.value
            val provider = AiProvider.fromString(providerStr)
            aiPreferencesRepository.setApiKey(provider, apiKey)
            if (apiKey.isNotBlank()) fetchAvailableModels(apiKey, providerStr)
            else clearModelsState(providerStr)
        }
    }

    fun onAiSystemPromptChange(prompt: String) {
        viewModelScope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setSystemPrompt(provider, prompt)
        }
    }

    fun resetAiSystemPrompt() {
        viewModelScope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.resetSystemPrompt(provider)
        }
    }

    fun setLocalMlEnabled(enabled: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlEnabled(enabled) }
    }

    fun setLocalMlActiveModelId(modelId: String) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlActiveModelId(modelId) }
    }

    fun setLocalMlFallbackToRemote(fallback: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlFallbackToRemote(fallback) }
    }

    fun setLocalMlUseGpu(enabled: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlUseGpu(enabled) }
    }

    fun setLocalMlContextSize(size: Int) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlContextSize(size.coerceIn(20, 200)) }
    }

    fun setLocalMlOllamaUrl(url: String) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlOllamaUrl(url.trim()) }
    }

    fun setLocalMlHfToken(token: String) {
        viewModelScope.launch { aiPreferencesRepository.setLocalMlHfToken(token.trim()) }
    }

    fun setAiTemperature(temperature: Float) {
        viewModelScope.launch { aiPreferencesRepository.setAiTemperature((temperature * 100).toInt()) }
    }

    fun setAiMaxTokens(maxTokens: Int) {
        viewModelScope.launch { aiPreferencesRepository.setAiMaxTokens(maxTokens) }
    }

    fun setAiEnableStreaming(enabled: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setAiEnableStreaming(enabled) }
    }

    fun setAiIncludeContext(enabled: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setAiIncludeContext(enabled) }
    }

    fun clearAiUsageData() {
        viewModelScope.launch {
            aiUsageDao.clearUsage()
        }
    }

    val isSafeTokenLimitEnabled: StateFlow<Boolean> = aiPreferencesRepository.isSafeTokenLimitEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localMlEnabled: StateFlow<Boolean> = aiPreferencesRepository.localMlEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val localMlActiveModelId: StateFlow<String> = aiPreferencesRepository.localMlActiveModelId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val localMlFallbackToRemote: StateFlow<Boolean> = aiPreferencesRepository.localMlFallbackToRemote
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val localMlUseGpu: StateFlow<Boolean> = aiPreferencesRepository.localMlUseGpu
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val localMlContextSize: StateFlow<Int> = aiPreferencesRepository.localMlContextSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiPreferencesRepository.DEFAULT_LOCAL_MODEL_CONTEXT_SIZE)

    val localMlOllamaUrl: StateFlow<String> = aiPreferencesRepository.localMlOllamaUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://ollama.ai/api")

    val localMlHfToken: StateFlow<String> = aiPreferencesRepository.localMlHfToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val recentAiUsage: StateFlow<List<AiUsageEntity>> = aiUsageDao.getRecentUsages(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalPromptTokens: StateFlow<Int> = aiUsageDao.getTotalPromptTokens()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalOutputTokens: StateFlow<Int> = aiUsageDao.getTotalOutputTokens()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalThoughtTokens: StateFlow<Int> = aiUsageDao.getTotalThoughtTokens()
        .map { it ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val fileExplorerStateHolder = FileExplorerStateHolder(userPreferencesRepository, viewModelScope, context)

    val currentPath = fileExplorerStateHolder.currentPath
    val currentDirectoryChildren = fileExplorerStateHolder.currentDirectoryChildren
    val blockedDirectories = fileExplorerStateHolder.blockedDirectories
    val availableStorages = fileExplorerStateHolder.availableStorages
    val selectedStorageIndex = fileExplorerStateHolder.selectedStorageIndex
    val isLoadingDirectories = fileExplorerStateHolder.isLoading
    val isExplorerPriming = fileExplorerStateHolder.isPrimingExplorer
    val isExplorerReady = fileExplorerStateHolder.isExplorerReady
    val isCurrentDirectoryResolved = fileExplorerStateHolder.isCurrentDirectoryResolved
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

    private fun refreshLocalMlSupport() {
        val capabilities = aiDeviceCapabilities.getCapabilities()
        val supportedModels = LocalModelCatalog.all.filter { model ->
            aiDeviceCapabilities.canRunModel((model.fileSizeBytes / (1024 * 1024)).toInt())
        }

        val supported = capabilities.supportsTflite && supportedModels.isNotEmpty()
        val supportMessage = when {
            !capabilities.supportsTflite -> context.getString(R.string.settings_ai_local_models_unsupported_tflite)
            supportedModels.isEmpty() -> context.getString(
                R.string.settings_ai_local_models_unsupported_memory,
                capabilities.recommendedModelSizeMb
            )
            else -> ""
        }

        _uiState.update {
            it.copy(
                localMlSupported = supported,
                localMlSupportMessage = supportMessage,
                localMlEnabled = it.localMlEnabled && supported
            )
        }

        if (!supported) {
            viewModelScope.launch {
                aiPreferencesRepository.setLocalMlEnabled(false)
            }
        }
    }

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
        // One-time device capability check — result is cached inside HiFiCapabilityChecker
        _uiState.update {
            it.copy(
                hiFiModeDeviceSupported = HiFiCapabilityChecker.isSupported(),
                appLanguageTag = AppLocaleManager.currentLanguageTag(context)
            )
        }

        refreshLocalMlSupport()

        viewModelScope.launch {
            localMlManager.statusMap.collect { statuses ->
                statuses.forEach { (id, status) ->
                    val prev = previousModelStatuses[id]
                    val info = LocalModelCatalog.byId(id)
                    val name = info?.displayName ?: id
                    when {
                        status is ModelStatus.Downloading && prev !is ModelStatus.Downloading -> {
                            notificationManager.showProgress(
                                "Downloading $name",
                                "${status.progress}% - ${formatBytes(status.bytesDownloaded)} / ${formatBytes(status.totalBytes)}",
                                status.progress
                            )
                        }
                        status is ModelStatus.Downloading && prev is ModelStatus.Downloading -> {
                            val speed = if (status.speedBytesPerSec > 0) " ${formatBytes(status.speedBytesPerSec)}/s" else ""
                            val eta = if (status.etaSeconds > 0 && status.etaSeconds < 600) " ${formatDuration(status.etaSeconds)} left" else ""
                            notificationManager.showProgress(
                                "Downloading $name",
                                "${status.progress}%${speed}${eta}",
                                status.progress
                            )
                        }
                        status is ModelStatus.Ready && prev !is ModelStatus.Ready -> {
                            notificationManager.showCompletion("$name downloaded", "Model ready to use")
                        }
                        status is ModelStatus.Error && prev !is ModelStatus.Error -> {
                            notificationManager.showError("Download failed", "$name: ${status.message}")
                        }
                    }
                }
                previousModelStatuses = statuses
            }
        }

        // Consolidated collectors using combine() to reduce coroutine overhead
        // Instead of 20 separate coroutines, we use 2 combined flows
        
        // Group 1: Core UI settings (theme, navigation, appearance)
        viewModelScope.launch {
            combine<Any?, SettingsUiUpdate.Group1>(
                userPreferencesRepository.appRebrandDialogShownFlow,
                themePreferencesRepository.appThemeModeFlow,
                themePreferencesRepository.playerThemePreferenceFlow,
                themePreferencesRepository.albumArtPaletteStyleFlow,
                themePreferencesRepository.albumArtColorAccuracyFlow,
                userPreferencesRepository.mockGenresEnabledFlow,
                userPreferencesRepository.navBarCornerRadiusFlow,
                userPreferencesRepository.navBarStyleFlow,
                userPreferencesRepository.navBarCompactModeFlow,
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
                    albumArtColorAccuracy = values[4] as Int,
                    mockGenresEnabled = values[5] as Boolean,
                    navBarCornerRadius = values[6] as Int,
                    navBarStyle = values[7] as String,
                    navBarCompactMode = values[8] as Boolean,
                    libraryNavigationMode = values[9] as String,
                    carouselStyle = values[10] as String,
                    launchTab = values[11] as String,
                    showPlayerFileInfo = values[12] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        appRebrandDialogShown = update.appRebrandDialogShown,
                        appThemeMode = update.appThemeMode,
                        playerThemePreference = update.playerThemePreference,
                        albumArtPaletteStyle = update.albumArtPaletteStyle,
                        albumArtColorAccuracy = update.albumArtColorAccuracy,
                        mockGenresEnabled = update.mockGenresEnabled,
                        navBarCornerRadius = update.navBarCornerRadius,
                        navBarStyle = update.navBarStyle,
                        navBarCompactMode = update.navBarCompactMode,
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
                userPreferencesRepository.hiFiModeEnabledFlow,
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
                    hiFiModeEnabled = values[5] as Boolean,
                    crossfadeDuration = values[6] as Int,
                    persistentShuffleEnabled = values[7] as Boolean,
                    folderBackGestureNavigation = values[8] as Boolean,
                    lyricsSourcePreference = values[9] as LyricsSourcePreference,
                    autoScanLrcFiles = values[10] as Boolean,
                    blockedDirectories = @Suppress("UNCHECKED_CAST") (values[11] as Set<String>),
                    hapticsEnabled = values[12] as Boolean,
                    immersiveLyricsEnabled = values[13] as Boolean,
                    immersiveLyricsTimeout = values[14] as Long,
                    animatedLyricsBlurEnabled = values[15] as Boolean,
                    animatedLyricsBlurStrength = values[16] as Float
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        keepPlayingInBackground = update.keepPlayingInBackground,
                        disableCastAutoplay = update.disableCastAutoplay,
                        resumeOnHeadsetReconnect = update.resumeOnHeadsetReconnect,
                        showQueueHistory = update.showQueueHistory,
                        isCrossfadeEnabled = update.isCrossfadeEnabled,
                        hiFiModeEnabled = update.hiFiModeEnabled,
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
            userPreferencesRepository.albumArtCacheLimitMbFlow.collect { limitMb ->
                _uiState.update { it.copy(albumArtCacheLimitMb = limitMb) }
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
            userPreferencesRepository.minTracksPerAlbumFlow.collect { minTracks ->
                _uiState.update { it.copy(minTracksPerAlbum = minTracks) }
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

        // Group A: AI Core + Local ML — consolidated into 1 combine() to replace ~17 individual coroutines
        viewModelScope.launch {
            combine<Any?, AiSettingsUpdate.GroupA>(
                aiPreferencesRepository.isSafeTokenLimitEnabled,
                aiPreferencesRepository.localMlEnabled,
                aiPreferencesRepository.localMlActiveModelId,
                aiPreferencesRepository.localMlFallbackToRemote,
                aiPreferencesRepository.localMlUseGpu,
                aiPreferencesRepository.localMlContextSize,
                aiPreferencesRepository.localMlOllamaUrl,
                aiPreferencesRepository.localMlHfToken,
                aiProvider,
                currentAiApiKey,
                currentAiModel,
                aiPreferencesRepository.aiTemperature,
                aiPreferencesRepository.aiMaxTokens,
                aiPreferencesRepository.aiEnableStreaming,
                aiPreferencesRepository.aiIncludeContext,
                aiPreferencesRepository.localModelDownloadTimeoutMs,
                aiPreferencesRepository.localMlSelectedModelId,
                aiPreferencesRepository.aiTopK,
                aiPreferencesRepository.aiTopP,
                aiPreferencesRepository.aiRepetitionPenalty,
                aiPreferencesRepository.aiFrequencyPenalty,
                aiPreferencesRepository.aiPresencePenalty
            ) { values ->
                AiSettingsUpdate.GroupA(
                    isSafeTokenLimitEnabled = values[0] as Boolean,
                    localMlEnabled = values[1] as Boolean,
                    localMlActiveModelId = values[2] as String,
                    localMlFallbackToRemote = values[3] as Boolean,
                    localMlUseGpu = values[4] as Boolean,
                    localMlContextSize = values[5] as Int,
                    localMlOllamaUrl = values[6] as String,
                    localMlHfToken = values[7] as String,
                    aiProvider = values[8] as String,
                    currentApiKey = values[9] as String,
                    currentModel = values[10] as String,
                    aiTemperature = values[11] as Int,
                    aiMaxTokens = values[12] as Int,
                    aiEnableStreaming = values[13] as Boolean,
                    aiIncludeContext = values[14] as Boolean,
                    localModelDownloadTimeoutMs = values[15] as Long,
                    localMlSelectedModelId = values[16] as String,
                    aiTopK = values[17] as Int,
                    aiTopP = values[18] as Int,
                    aiRepetitionPenalty = values[19] as Int,
                    aiFrequencyPenalty = values[20] as Int,
                    aiPresencePenalty = values[21] as Int
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        isSafeTokenLimitEnabled = update.isSafeTokenLimitEnabled,
                        localMlEnabled = update.localMlEnabled,
                        localMlActiveModelId = update.localMlActiveModelId,
                        localMlFallbackToRemote = update.localMlFallbackToRemote,
                        localMlUseGpu = update.localMlUseGpu,
                        localMlContextSize = update.localMlContextSize,
                        localMlOllamaUrl = update.localMlOllamaUrl,
                        localMlHfToken = update.localMlHfToken,
                        aiProvider = update.aiProvider,
                        currentApiKey = update.currentApiKey,
                        currentModel = update.currentModel,
                        aiTemperature = update.aiTemperature / 100f,
                        aiMaxTokens = update.aiMaxTokens,
                        aiEnableStreaming = update.aiEnableStreaming,
                        aiIncludeContext = update.aiIncludeContext,
                        localModelDownloadTimeoutMs = update.localModelDownloadTimeoutMs,
                        localMlSelectedModelId = update.localMlSelectedModelId,
                        aiTopK = update.aiTopK,
                        aiTopP = update.aiTopP / 100f,
                        aiRepetitionPenalty = update.aiRepetitionPenalty / 100f,
                        aiFrequencyPenalty = update.aiFrequencyPenalty / 100f,
                        aiPresencePenalty = update.aiPresencePenalty / 100f
                    )
                }
            }
        }

        // Group B: Cache + Usage + Telemetry — consolidated into 1 combine() to replace ~15 individual coroutines
        viewModelScope.launch {
            combine<Any?, AiSettingsUpdate.GroupB>(
                aiPreferencesRepository.aiCacheEnabled,
                aiPreferencesRepository.aiCacheMaxEntries,
                aiPreferencesRepository.aiCacheTtlHours,
                aiPreferencesRepository.aiUsageTotalInputTokens,
                aiPreferencesRepository.aiUsageTotalOutputTokens,
                aiPreferencesRepository.aiUsageTotalApiCalls,
                aiPreferencesRepository.aiUsageEstimatedCost,
                aiPreferencesRepository.telemetryIncludeSkipCount,
                aiPreferencesRepository.telemetryIncludeCompletionRate,
                aiPreferencesRepository.telemetryIncludeSessionDuration,
                aiPreferencesRepository.telemetryIncludeTimeOfDay,
                aiPreferencesRepository.telemetryIncludeGenreAffinity,
                aiPreferencesRepository.telemetryIncludeArtistAffinity,
                aiPreferencesRepository.telemetryIncludeReplayCount,
                aiPreferencesRepository.telemetryIncludeQueuePatterns
            ) { values ->
                AiSettingsUpdate.GroupB(
                    aiCacheEnabled = values[0] as Boolean,
                    aiCacheMaxEntries = values[1] as Int,
                    aiCacheTtlHours = values[2] as Int,
                    aiUsageTotalInputTokens = values[3] as Long,
                    aiUsageTotalOutputTokens = values[4] as Long,
                    aiUsageTotalApiCalls = values[5] as Long,
                    aiUsageEstimatedCost = values[6] as String,
                    telemetryIncludeSkipCount = values[7] as Boolean,
                    telemetryIncludeCompletionRate = values[8] as Boolean,
                    telemetryIncludeSessionDuration = values[9] as Boolean,
                    telemetryIncludeTimeOfDay = values[10] as Boolean,
                    telemetryIncludeGenreAffinity = values[11] as Boolean,
                    telemetryIncludeArtistAffinity = values[12] as Boolean,
                    telemetryIncludeReplayCount = values[13] as Boolean,
                    telemetryIncludeQueuePatterns = values[14] as Boolean
                )
            }.collect { update ->
                _uiState.update { state ->
                    state.copy(
                        aiCacheEnabled = update.aiCacheEnabled,
                        aiCacheMaxEntries = update.aiCacheMaxEntries,
                        aiCacheTtlHours = update.aiCacheTtlHours,
                        aiUsageTotalInputTokens = update.aiUsageTotalInputTokens,
                        aiUsageTotalOutputTokens = update.aiUsageTotalOutputTokens,
                        aiUsageTotalApiCalls = update.aiUsageTotalApiCalls,
                        aiUsageEstimatedCost = update.aiUsageEstimatedCost,
                        telemetryIncludeSkipCount = update.telemetryIncludeSkipCount,
                        telemetryIncludeCompletionRate = update.telemetryIncludeCompletionRate,
                        telemetryIncludeSessionDuration = update.telemetryIncludeSessionDuration,
                        telemetryIncludeTimeOfDay = update.telemetryIncludeTimeOfDay,
                        telemetryIncludeGenreAffinity = update.telemetryIncludeGenreAffinity,
                        telemetryIncludeArtistAffinity = update.telemetryIncludeArtistAffinity,
                        telemetryIncludeReplayCount = update.telemetryIncludeReplayCount,
                        telemetryIncludeQueuePatterns = update.telemetryIncludeQueuePatterns
                    )
                }
            }
        }


        // Load available local models
        loadLocalModels()
    }

    private fun loadLocalModels() {
        viewModelScope.launch {
            val localModels = LocalModelCatalog.all.filter { model ->
                val modelSizeMb = (model.fileSizeBytes / (1024 * 1024)).toInt()
                aiDeviceCapabilities.canRunModel(modelSizeMb) || modelSizeMb <= 50
            }
            _uiState.update { it.copy(availableLocalModels = localModels) }

            // Seed statusMap with already-installed models
            localModels.filter { localMlManager.isInstalled(it.id) }.forEach { model ->
                val validated = localMlManager.validateModelFile(model.id)
                if (validated is LocalModelManager.ValidationResult.Ok) {
                    localMlManager.seedStatus(model.id, ModelStatus.Ready)
                } else if (validated is LocalModelManager.ValidationResult.SizeMismatch) {
                    Timber.w("Model size mismatch: ${model.id} (${validated.actual} vs ${validated.expected})")
                    localMlManager.deleteModel(model.id)
                }
            }

            // Collect local model status changes
            localMlManager.statusMap.collect { statuses: Map<String, ModelStatus> ->
                _uiState.update { it.copy(localModelStatuses = statuses) }
            }
        }
    }

    fun downloadLocalModel(modelInfo: LocalModelInfo) {
        val mb = modelInfo.fileSizeBytes / (1024 * 1024)
        notificationManager.showProgress(
            "Downloading ${modelInfo.displayName}",
            "Starting download... ($mb MB)",
            0
        )
        localMlManager.downloadModel(modelInfo)
    }

    fun cancelDownloadModel(modelId: String) {
        localMlManager.cancelDownload(modelId)
        val info = LocalModelCatalog.byId(modelId)
        if (info != null) {
            notificationManager.showInfo("Download cancelled", "${info.displayName} download cancelled")
        }
    }

    fun deleteLocalModel(modelId: String) {
        viewModelScope.launch {
            localMlManager.deleteModel(modelId)
            val currentStatuses = _uiState.value.localModelStatuses.toMutableMap()
            currentStatuses[modelId] = ModelStatus.NotDownloaded
            _uiState.update { it.copy(localModelStatuses = currentStatuses) }

            // Clear active model if deleted
            if (_uiState.value.localMlActiveModelId == modelId) {
                aiPreferencesRepository.setLocalMlActiveModelId("")
                _uiState.update { it.copy(localMlActiveModelId = "") }
            }
        }
    }

    fun selectLocalModel(modelId: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setLocalMlActiveModelId(modelId)
            localMlManager.setActiveModel(modelId)
            _uiState.update { it.copy(localMlActiveModelId = modelId) }
        }
    }

    fun importLocalModel(uri: Uri) {
        viewModelScope.launch {
            val modelId = "user_imported_${System.currentTimeMillis()}"
            localMlManager.importModel(uri, modelId).onSuccess { file ->
                val currentStatuses = _uiState.value.localModelStatuses.toMutableMap()
                currentStatuses[modelId] = ModelStatus.Ready
                _uiState.update { it.copy(localModelStatuses = currentStatuses) }
            }.onFailure { error ->
                val currentStatuses = _uiState.value.localModelStatuses.toMutableMap()
                currentStatuses[modelId] = ModelStatus.Error(error.message ?: "Import failed")
                _uiState.update { it.copy(localModelStatuses = currentStatuses) }
            }
        }
    }

    fun onLocalMlUseGpuChange(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setLocalMlUseGpu(enabled)
            _uiState.update { it.copy(localMlUseGpu = enabled) }
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

    fun openExplorer() {
        fileExplorerStateHolder.openExplorerRoot()
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

    fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) {
        viewModelScope.launch {
            themePreferencesRepository.setAlbumArtPaletteSettings(style, accuracyLevel)
        }
    }

    suspend fun getAlbumArtPalettePreview(
        uriString: String,
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ): ColorSchemePair? {
        return colorSchemeProcessor.getPreviewColorScheme(
            albumArtUri = uriString,
            paletteStyle = style,
            colorAccuracyLevel = accuracyLevel
        )
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

    fun setAppLanguage(languageTag: String) {
        val normalized = AppLanguage.normalize(languageTag)
        AppLocaleManager.applyLanguage(context, normalized)
        _uiState.update { it.copy(appLanguageTag = normalized) }
    }

    fun setNavBarStyle(style: String) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarStyle(style)
        }
    }

    fun setNavBarCompactMode(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setNavBarCompactMode(enabled)
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

    fun setHiFiModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHiFiModeEnabled(enabled)
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

    fun setSafeTokenLimitEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setSafeTokenLimitEnabled(enabled)
        }
    }

    fun setMaxSongsForContext(maxSongs: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setMaxSongsForContext(maxSongs)
        }
    }

    fun setIncludeLikedSongs(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeLikedSongs(include)
        }
    }

    fun setIncludeDailyMixHistory(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeDailyMixHistory(include)
        }
    }

    fun setIncludeUserHabits(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeUserHabits(include)
        }
    }

    fun setLocalMlSelectedModelId(modelId: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setLocalMlSelectedModelId(modelId)
        }
    }

    fun setLocalModelDownloadTimeoutMs(timeoutMs: Long) {
        viewModelScope.launch {
            aiPreferencesRepository.setLocalModelDownloadTimeoutMs(timeoutMs)
        }
    }

    fun setAiCacheEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiCacheEnabled(enabled)
        }
    }

    fun setAiCacheMaxEntries(maxEntries: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiCacheMaxEntries(maxEntries)
        }
    }

    fun setAiCacheTtlHours(ttlHours: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiCacheTtlHours(ttlHours)
        }
    }

    fun getAiUsageStats(): Pair<Long, Long> {
        return Pair(aiUsageTotalInputTokens.value, aiUsageTotalOutputTokens.value)
    }

    fun clearAiUsageMetrics() {
        viewModelScope.launch {
            aiPreferencesRepository.clearAiUsageMetrics()
        }
    }

    fun setPerModelTemperature(modelName: String, temperature: Float) {
        viewModelScope.launch {
            aiPreferencesRepository.setPerModelTemperature(modelName, (temperature * 100).toInt())
        }
    }

    fun clearPerModelTemperature(modelName: String) {
        viewModelScope.launch {
            aiPreferencesRepository.clearPerModelTemperature(modelName)
        }
    }

    fun setPerModelMaxTokens(modelName: String, tokens: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setPerModelMaxTokens(modelName, tokens)
        }
    }

    fun clearPerModelMaxTokens(modelName: String) {
        viewModelScope.launch {
            aiPreferencesRepository.clearPerModelMaxTokens(modelName)
        }
    }

    fun setProviderTimeout(provider: AiProvider, timeoutMs: Long) {
        viewModelScope.launch {
            aiPreferencesRepository.setProviderTimeout(provider, timeoutMs)
        }
    }

    fun getLocalModelDownloadUrl(modelId: String): String? {
        return availableLocalModels.value.find { it.id == modelId }?.downloadUrl
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

    fun setMinTracksPerAlbum(minTracks: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setMinTracksPerAlbum(minTracks)
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

    fun onAiProviderChange(provider: String) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiProvider(provider)

            // Clear existing models immediately to show loading state
            _uiState.update {
                it.copy(
                    availableModels = emptyList(),
                    modelsFetchError = null,
                    isLoadingModels = false
                )
            }

            // Fetch models for the newly selected provider if we have an API key
            val apiKey = aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)).first()

            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, provider)
            }
        }
    }

    fun onAiModelChange(model: String) {
        viewModelScope.launch {
            val provider = AiProvider.fromString(aiProvider.value)
            aiPreferencesRepository.setModel(provider, model)
            _uiState.update { it.copy(currentModel = model) }
        }
    }

    fun onAiTemperatureChange(temperature: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiTemperature(temperature)
            _uiState.update { it.copy(aiTemperature = temperature / 100f) }
        }
    }

    fun onAiTopKChange(value: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiTopK(value)
            _uiState.update { it.copy(aiTopK = value) }
        }
    }

    fun onAiTopPChange(value: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiTopP(value)
            _uiState.update { it.copy(aiTopP = value / 100f) }
        }
    }

    fun onAiRepetitionPenaltyChange(value: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiRepetitionPenalty(value)
            _uiState.update { it.copy(aiRepetitionPenalty = value / 100f) }
        }
    }

    fun onAiFrequencyPenaltyChange(value: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiFrequencyPenalty(value)
            _uiState.update { it.copy(aiFrequencyPenalty = value / 100f) }
        }
    }

    fun onAiPresencePenaltyChange(value: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiPresencePenalty(value)
            _uiState.update { it.copy(aiPresencePenalty = value / 100f) }
        }
    }

    fun onAiMaxTokensChange(maxTokens: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setAiMaxTokens(maxTokens)
            _uiState.update { it.copy(aiMaxTokens = maxTokens) }
        }
    }

    fun onMaxSongsForContextChange(size: Int) {
        viewModelScope.launch {
            aiPreferencesRepository.setMaxSongsForContext(size)
            _uiState.update { it.copy(maxSongsForContext = size) }
        }
    }

    fun onIncludeLikedSongsChange(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeLikedSongs(include)
            _uiState.update { it.copy(includeLikedSongs = include) }
        }
    }

    fun onIncludeDailyMixHistoryChange(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeDailyMixHistory(include)
            _uiState.update { it.copy(includeDailyMixHistory = include) }
        }
    }

    fun onIncludeUserHabitsChange(include: Boolean) {
        viewModelScope.launch {
            aiPreferencesRepository.setIncludeUserHabits(include)
            _uiState.update { it.copy(includeUserHabits = include) }
        }
    }

    fun loadModelsForCurrentProvider() {
        viewModelScope.launch {
            if (_uiState.value.isLoadingModels) return@launch
            if (_uiState.value.availableModels.isNotEmpty()) return@launch
            
            val provider = aiProvider.value
            val apiKey = aiPreferencesRepository.getApiKey(AiProvider.fromString(provider)).first()
            
            if (apiKey.isNotBlank()) {
                fetchAvailableModels(apiKey, provider)
            }
        }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1fGB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1fMB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1fKB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }

    private fun clearModelsState(provider: String) {
        _uiState.update {
            it.copy(
                availableModels = emptyList(),
                modelsFetchError = null
            )
        }
        viewModelScope.launch {
            aiPreferencesRepository.setModel(AiProvider.fromString(provider), "")
        }
    }

    private fun fetchAvailableModels(apiKey: String, providerName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingModels = true, modelsFetchError = null) }
            try {
                val provider = AiProvider.fromString(providerName)
                val modelsResult = aiHandler.fetchAvailableModels(provider, apiKey)
                val models = modelsResult.getOrThrow()
                
                _uiState.update { 
                    it.copy(
                        availableModels = models, 
                        isLoadingModels = false,
                        modelsFetchError = null
                    ) 
                }

                // Auto-select first model if nothing is selected yet
                val currentModel = aiPreferencesRepository.getModel(provider).first()
                val availableModelNames = models.map { it.name }.toSet()
                if (models.isNotEmpty() && (currentModel.isBlank() || currentModel !in availableModelNames)) {
                    val firstModel = models.first().name
                    aiPreferencesRepository.setModel(provider, firstModel)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        modelsFetchError = e.message ?: context.getString(R.string.models_fetch_failed),
                    )
                }
            }
        }
    }

    fun setNavBarCornerRadius(radius: Int) {
        viewModelScope.launch { userPreferencesRepository.setNavBarCornerRadius(radius) }
    }
    /**
     * Triggers a test crash to verify the crash handler is working correctly.
     * This should only be used for testing in Developer Options.
     */
    fun triggerTestCrash() {
        throw RuntimeException(context.getString(R.string.dev_test_crash_message))
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

    fun setAlbumArtCacheLimitMb(limitMb: Int) {
        viewModelScope.launch {
            userPreferencesRepository.setAlbumArtCacheLimitMb(limitMb)
            com.theveloper.pixelplay.utils.AlbumArtCacheManager.configuredCacheLimitMb = limitMb.toLong()
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
                title = context.getString(R.string.backup_progress_preparing_backup),
                detail = context.getString(R.string.backup_progress_starting_backup_task),
            )
            val result = backupManager.export(uri, sections) { progress ->
                _dataTransferProgress.value = progress
            }
            result.fold(
                onSuccess = { _dataTransferEvents.emit(context.getString(R.string.data_exported_successfully)) },
                onFailure = {
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.export_failed_format,
                            it.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
                },
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
                    _dataTransferEvents.emit(
                        context.getString(
                            R.string.backup_invalid_format,
                            error.localizedMessage ?: context.getString(R.string.error_unknown),
                        ),
                    )
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
                title = context.getString(R.string.backup_progress_preparing_restore),
                detail = context.getString(R.string.backup_progress_starting_task),
            )
            val result = backupManager.restore(uri, plan) { progress ->
                _dataTransferProgress.value = progress
            }
            when (result) {
                is RestoreResult.Success -> {
                    _dataTransferEvents.emit(context.getString(R.string.data_restored_successfully))
                    syncManager.sync()
                }
                is RestoreResult.PartialFailure -> {
                    val failedNames = result.failed.entries.joinToString { "${it.key.label}: ${it.value}" }
                    _dataTransferEvents.emit(
                        context.getString(R.string.restore_partial_unresolved_format, failedNames),
                    )
                    if (result.succeeded.isNotEmpty() || !result.rolledBack) {
                        syncManager.sync()
                    }
                }
                is RestoreResult.TotalFailure -> {
                    _dataTransferEvents.emit(context.getString(R.string.restore_failed_format, result.error))
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

    // Telemetry change handlers
    fun onTelemetrySkipCountChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeSkipCount(v) }
    }
    fun onTelemetryCompletionRateChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeCompletionRate(v) }
    }
    fun onTelemetrySessionDurationChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeSessionDuration(v) }
    }
    fun onTelemetryTimeOfDayChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeTimeOfDay(v) }
    }
    fun onTelemetryGenreAffinityChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeGenreAffinity(v) }
    }
    fun onTelemetryArtistAffinityChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeArtistAffinity(v) }
    }
    fun onTelemetryReplayCountChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeReplayCount(v) }
    }
    fun onTelemetryQueuePatternsChange(v: Boolean) {
        viewModelScope.launch { aiPreferencesRepository.setTelemetryIncludeQueuePatterns(v) }
    }
}
