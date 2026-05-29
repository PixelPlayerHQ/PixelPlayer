package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.data.ai.local.LocalModelManager
import com.theveloper.pixelplay.data.ai.local.LocalModelCatalog
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelSource
import com.theveloper.pixelplay.data.ai.local.ModelStatus
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
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
    private val localMlManager: LocalModelManager,
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
        val ollamaEndpoint: String = "https://ollama.ai/api",
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
        try {
            val provider = aiPreferencesRepository.aiProvider.first()
            val model = aiPreferencesRepository.getModel(AiProvider.fromString(provider)).first()

            _settingsState.value = AiSettingsState(
                activeProvider = provider,
                activeModel = model,
                temperature = aiPreferencesRepository.aiTemperature.first() / 100f,
                maxTokens = aiPreferencesRepository.aiMaxTokens.first(),
                enableStreaming = aiPreferencesRepository.aiEnableStreaming.first(),
                includeContext = aiPreferencesRepository.aiIncludeContext.first(),
                contextWindowSize = aiPreferencesRepository.maxSongsForContext.first(),
                includeLikedSongs = aiPreferencesRepository.includeLikedSongs.first(),
                includeDailyMixHistory = aiPreferencesRepository.includeDailyMixHistory.first(),
                includeUserHabits = aiPreferencesRepository.includeUserHabits.first(),
                localModelEnabled = aiPreferencesRepository.localMlEnabled.first(),
                localModelId = aiPreferencesRepository.localMlActiveModelId.first().takeIf { it.isNotEmpty() },
                ollamaEndpoint = aiPreferencesRepository.localMlOllamaUrl.first(),
                huggingFaceToken = aiPreferencesRepository.localMlHfToken.first().takeIf { it.isNotEmpty() }
            )

            // Load available models based on device capabilities
            refreshAvailableModels()
        } catch (e: Exception) {
            // Handle loading error - keep default state
            e.printStackTrace()
        }
    }

    /**
     * Refreshes the list of available models for this device.
     */
    fun refreshAvailableModels() {
        val capabilities = aiDeviceCapabilities.getCapabilities()
        val catalogModels = LocalModelCatalog.all

        // Filter by device capabilities - only show models that fit in device RAM
        val filteredModels = catalogModels.filter { model: LocalModelInfo ->
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
        aiPreferencesRepository.setModel(AiProvider.fromString(newState.activeProvider), newState.activeModel)
        aiPreferencesRepository.setAiTemperature((newState.temperature * 100).toInt())
        aiPreferencesRepository.setAiMaxTokens(newState.maxTokens)
        aiPreferencesRepository.setAiEnableStreaming(newState.enableStreaming)
        aiPreferencesRepository.setAiIncludeContext(newState.includeContext)
        aiPreferencesRepository.setMaxSongsForContext(newState.contextWindowSize)
        aiPreferencesRepository.setIncludeLikedSongs(newState.includeLikedSongs)
        aiPreferencesRepository.setIncludeDailyMixHistory(newState.includeDailyMixHistory)
        aiPreferencesRepository.setIncludeUserHabits(newState.includeUserHabits)
        aiPreferencesRepository.setLocalMlEnabled(newState.localModelEnabled)
        aiPreferencesRepository.setLocalMlActiveModelId(newState.localModelId ?: "")
        aiPreferencesRepository.setLocalMlOllamaUrl(newState.ollamaEndpoint)
        aiPreferencesRepository.setLocalMlHfToken(newState.huggingFaceToken ?: "")
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
     * Sets whether to include context.
     */
    suspend fun setIncludeContext(include: Boolean) {
        updateSetting { copy(includeContext = include) }
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
     * Enables/disables local models.
     */
    suspend fun setLocalModelEnabled(enabled: Boolean) {
        updateSetting { copy(localModelEnabled = enabled) }
    }

    /**
     * Sets the active local model.
     */
    suspend fun setLocalModelId(modelId: String?) {
        updateSetting { copy(localModelId = modelId, localModelEnabled = modelId != null) }
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
     * Downloads and sets up a local model.
     */
    suspend fun setupLocalModel(modelId: String): Boolean {
        return try {
            // Find the model info from catalog
            val modelInfo = LocalModelCatalog.all.find { it.id == modelId }
            if (modelInfo == null) {
                Timber.tag("AiSettingsManager").e("Model not found in catalog: $modelId")
                return false
            }

            // Download using the model info and wait for completion using first()
            val finalStatus = localMlManager.downloadModel(modelInfo).first()
            Timber.tag("AiSettingsManager").d("Download completed with status: $finalStatus")

            // Check if download was successful
            val isReady = finalStatus is ModelStatus.Ready || localMlManager.isInstalled(modelId)
            if (isReady) {
                setLocalModelId(modelId)
                setLocalModelEnabled(true)
                true
            } else {
                Timber.tag("AiSettingsManager").e("Download failed: $finalStatus")
                false
            }
        } catch (e: Exception) {
            Timber.tag("AiSettingsManager").e(e, "Failed to setup local model: $modelId")
            false
        }
    }

    /**
     * Gets the current provider's model options.
     */
    fun getProviderModels(provider: String): List<String> {
        return when (provider) {
            "GEMINI" -> listOf("gemini-2.0-flash-exp", "gemini-1.5-pro", "gemini-1.5-flash")
            "OPENAI" -> listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo")
            "ANTHROPIC" -> listOf("claude-sonnet-4-20250514", "claude-haiku-4-20250307")
            "OLLAMA" -> listOf("llama3", "mistral", "phi3", "tinyllama", "llama2")
            "LOCAL" -> _availableModels.value.map { it.id }
            else -> emptyList()
        }
    }

    /**
     * Checks if the current provider is ready for API calls.
     */
    fun isProviderReady(): Boolean {
        val state = _settingsState.value
        return when (state.activeProvider) {
            "LOCAL" -> state.localModelEnabled && state.localModelId != null && 
                       localMlManager.isInstalled(state.localModelId)
            "OLLAMA" -> state.ollamaEndpoint.isNotBlank()
            else -> true  // Cloud providers assume API keys are set elsewhere
        }
    }

    /**
     * Validates if current settings can make API calls.
     */
    suspend fun validateSettings(): ValidationResult {
        val state = _settingsState.value

        return when (state.activeProvider) {
            "LOCAL" -> {
                if (!state.localModelEnabled) {
                    ValidationResult.Error("Local models are disabled")
                } else if (state.localModelId == null) {
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
            "GEMINI", "OPENAI", "ANTHROPIC" -> {
                // API key validation happens at the provider level
                ValidationResult.Valid
            }
            else -> ValidationResult.Error("Unknown provider: ${state.activeProvider}")
        }
    }

    /**
     * Gets the status of a specific local model.
     */
    fun getModelStatus(modelId: String): ModelStatus {
        return localMlManager.getModelStatus(modelId)
    }

    /**
     * Deletes a downloaded local model.
     */
    suspend fun deleteLocalModel(modelId: String): Boolean {
        return try {
            val success = localMlManager.deleteModel(modelId)
            if (success && _settingsState.value.localModelId == modelId) {
                setLocalModelId(null)
                setLocalModelEnabled(false)
            }
            success
        } catch (e: Exception) {
            false
        }
    }

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Error(val message: String) : ValidationResult()
    }
}