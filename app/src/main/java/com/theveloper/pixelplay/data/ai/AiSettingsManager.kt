package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.local.LocalMlManager
import com.theveloper.pixelplay.data.ai.local.LocalModelCatalog
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelSource
import com.theveloper.pixelplay.data.ai.local.ModelStatus
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for all AI-related settings and preferences.
 * Coordinates between cloud AI, local models, and user preferences.
 */
@Singleton
class AiSettingsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val aiPreferencesRepository: AiPreferencesRepository,
    private val localMlManager: LocalMlManager,
    private val aiDeviceCapabilities: AiDeviceCapabilities
) {
    data class AiSettingsState(
        val activeProvider: String = "GEMINI",
        val activeModel: String = "gemini-2.0-flash-exp",
        val temperature: Float = 0.7f,
        val maxTokens: Int = 2048,
        val enableStreaming: Boolean = true,
        val includeContext: Boolean = true,
        val contextWindowSize: Int = 50,
        val includeLikedSongs: Boolean = true,
        val includeDailyMixHistory: Boolean = true,
        val includeUserHabits: Boolean = true,
        val localModelEnabled: Boolean = false,
        val localModelId: String? = null,
        val ollamaEndpoint: String = "http://localhost:11434",
        val huggingFaceToken: String? = null
    )

    private val _settingsState = MutableStateFlow(AiSettingsState())
    val settingsState: StateFlow<AiSettingsState> = _settingsState.asStateFlow()

    private val _availableModels = MutableStateFlow<List<LocalModelInfo>>(emptyList())
    val availableModels: StateFlow<List<LocalModelInfo>> = _availableModels.asStateFlow()

    /**
     * Loads settings from preferences.
     */
    suspend fun loadSettings() {
        val prefs = aiPreferencesRepository.getPreferences.first()

        _settingsState.value = AiSettingsState(
            activeProvider = prefs.aiProvider,
            activeModel = prefs.aiModel,
            temperature = prefs.aiTemperature,
            maxTokens = prefs.aiMaxTokens,
            enableStreaming = prefs.aiEnableStreaming,
            includeContext = prefs.aiIncludeContext,
            contextWindowSize = prefs.maxSongsForContext,
            includeLikedSongs = prefs.includeLikedSongs,
            includeDailyMixHistory = prefs.includeDailyMixHistory,
            includeUserHabits = prefs.includeUserHabits
        )

        // Load available models based on device capabilities
        refreshAvailableModels()
    }

    /**
     * Refreshes the list of available models for this device.
     */
    fun refreshAvailableModels() {
        val capabilities = aiDeviceCapabilities.getCapabilities()
        val catalogModels = LocalModelCatalog.forDevice()

        // Filter by device capabilities
        val filteredModels = catalogModels.filter { model ->
            capabilities.recommendedModelSizeMb >= (model.fileSizeBytes / (1024 * 1024))
        }

        _availableModels.value = filteredModels
    }

    /**
     * Gets all available models from all sources.
     */
    fun getAllModelSources(): Map<ModelSource, List<LocalModelInfo>> {
        return _availableModels.value.groupBy { it.source }
    }

    /**
     * Gets models from a specific source.
     */
    fun getModelsBySource(source: ModelSource): List<LocalModelInfo> {
        return _availableModels.value.filter { it.source == source }
    }

    /**
     * Updates a single setting.
     */
    suspend fun updateSetting(block: AiSettingsState.() -> AiSettingsState) {
        val newState = block(_settingsState.value)
        _settingsState.value = newState

        // Persist to preferences
        aiPreferencesRepository.setAiProvider(newState.activeProvider)
        aiPreferencesRepository.setAiModel(newState.activeModel)
        aiPreferencesRepository.setAiTemperature(newState.temperature)
        aiPreferencesRepository.setAiMaxTokens(newState.maxTokens)
        aiPreferencesRepository.setAiEnableStreaming(newState.enableStreaming)
        aiPreferencesRepository.setAiIncludeContext(newState.includeContext)
        aiPreferencesRepository.setMaxSongsForContext(newState.contextWindowSize)
        aiPreferencesRepository.setIncludeLikedSongs(newState.includeLikedSongs)
        aiPreferencesRepository.setIncludeDailyMixHistory(newState.includeDailyMixHistory)
        aiPreferencesRepository.setIncludeUserHabits(newState.includeUserHabits)
    }

    /**
     * Sets the active AI provider.
     */
    suspend fun setActiveProvider(provider: String) {
        updateSetting { copy(activeProvider = provider) }
    }

    /**
     * Sets the active model for the current provider.
     */
    suspend fun setActiveModel(model: String) {
        updateSetting { copy(activeModel = model) }
    }

    /**
     * Sets the temperature for generation.
     */
    suspend fun setTemperature(temperature: Float) {
        updateSetting { copy(temperature = temperature.coerceIn(0f, 2f)) }
    }

    /**
     * Sets the max tokens for generation.
     */
    suspend fun setMaxTokens(maxTokens: Int) {
        updateSetting { copy(maxTokens = maxTokens.coerceIn(256, 8192)) }
    }

    /**
     * Enables/disables streaming.
     */
    suspend fun setStreamingEnabled(enabled: Boolean) {
        updateSetting { copy(enableStreaming = enabled) }
    }

    /**
     * Sets the context window size.
     */
    suspend fun setContextWindowSize(size: Int) {
        updateSetting { copy(contextWindowSize = size.coerceIn(10, 200)) }
    }

    /**
     * Sets whether to include liked songs in context.
     */
    suspend fun setIncludeLikedSongs(include: Boolean) {
        updateSetting { copy(includeLikedSongs = include) }
    }

    /**
     * Sets whether to include daily mix history in context.
     */
    suspend fun setIncludeDailyMixHistory(include: Boolean) {
        updateSetting { copy(includeDailyMixHistory = include) }
    }

    /**
     * Sets whether to include user habits in context.
     */
    suspend fun setIncludeUserHabits(include: Boolean) {
        updateSetting { copy(includeUserHabits = include) }
    }

    /**
     * Sets the Ollama endpoint.
     */
    suspend fun setOllamaEndpoint(endpoint: String) {
        updateSetting { copy(ollamaEndpoint = endpoint) }
    }

    /**
     * Sets the HuggingFace token.
     */
    suspend fun setHuggingFaceToken(token: String?) {
        updateSetting { copy(huggingFaceToken = token) }
    }

    /**
     * Enables local model mode.
     */
    suspend fun setLocalModelEnabled(enabled: Boolean, modelId: String? = null) {
        updateSetting { copy(localModelEnabled = enabled, localModelId = modelId) }
    }

    /**
     * Gets the current provider's model options.
     */
    fun getProviderModels(provider: String): List<String> {
        return when (provider) {
            "GEMINI" -> listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
            "OPENAI" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo")
            "ANTHROPIC" -> listOf("claude-sonnet-4-20250514", "claude-haiku-4-20250307")
            "OLLAMA" -> listOf("llama3", "mistral", "phi3", "tinyllama")
            else -> emptyList()
        }
    }

    /**
     * Validates if current settings can make API calls.
     */
    suspend fun validateSettings(): ValidationResult {
        val state = _settingsState.value

        return when (state.activeProvider) {
            "LOCAL" -> {
                if (!state.localModelEnabled || state.localModelId == null) {
                    ValidationResult.Error("No local model selected")
                } else if (!localMlManager.isInstalled(state.localModelId)) {
                    ValidationResult.Error("Selected model not downloaded")
                } else {
                    ValidationResult.Valid
                }
            }
            "OLLAMA" -> {
                if (state.ollamaEndpoint.isBlank()) {
                    ValidationResult.Error("Ollama endpoint not configured")
                } else {
                    ValidationResult.Valid
                }
            }
            else -> ValidationResult.Valid
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}