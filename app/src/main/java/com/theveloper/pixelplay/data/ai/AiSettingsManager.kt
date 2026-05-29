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
        val huggingFaceToken: String? = null,
        val topK: Int = 40,
        val topP: Float = 0.95f,
        val repetitionPenalty: Float = 1.0f,
        val frequencyPenalty: Float = 0.0f,
        val presencePenalty: Float = 0.0f
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
                huggingFaceToken = aiPreferencesRepository.localMlHfToken.first().takeIf { it.isNotEmpty() },
                topK = aiPreferencesRepository.aiTopK.first(),
                topP = aiPreferencesRepository.aiTopP.first() / 100f,
                repetitionPenalty = aiPreferencesRepository.aiRepetitionPenalty.first() / 100f,
                frequencyPenalty = aiPreferencesRepository.aiFrequencyPenalty.first() / 100f,
                presencePenalty = aiPreferencesRepository.aiPresencePenalty.first() / 100f
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
            val sizeMb = if (model.fileSizeBytes > 0) model.fileSizeBytes / (1024 * 1024) else 0
            capabilities.recommendedModelSizeMb >= sizeMb
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
        aiPreferencesRepository.setAiTopK(newState.topK)
        aiPreferencesRepository.setAiTopP((newState.topP * 100).toInt())
        aiPreferencesRepository.setAiRepetitionPenalty((newState.repetitionPenalty * 100).toInt())
        aiPreferencesRepository.setAiFrequencyPenalty((newState.frequencyPenalty * 100).toInt())
        aiPreferencesRepository.setAiPresencePenalty((newState.presencePenalty * 100).toInt())
    }

    suspend fun set(block: AiSettingsState.() -> AiSettingsState) { updateSetting(block) }

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
                set { copy(localModelId = modelId, localModelEnabled = true) }
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
        val aiProvider = try { AiProvider.valueOf(provider) } catch (_: Exception) { null }
        return aiProvider?.models?.ifEmpty {
            when (provider) {
                "LOCAL" -> _availableModels.value.map { it.id }
                else -> emptyList()
            }
        } ?: emptyList()
    }

    /**
     * Checks if the current provider is ready for API calls.
     */
    fun isProviderReady(): Boolean {
        val state = _settingsState.value
        return when (state.activeProvider) {
            "LOCAL" -> {
                val modelId = state.localModelId
                state.localModelEnabled && modelId != null && localMlManager.isInstalled(modelId)
            }
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
                } else {
                    val modelId = state.localModelId
                    if (modelId == null) {
                        ValidationResult.Error("No local model selected")
                    } else if (!localMlManager.isInstalled(modelId)) {
                        ValidationResult.Error("Selected model not downloaded")
                    } else {
                        ValidationResult.Valid
                    }
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
                set { copy(localModelId = null, localModelEnabled = false) }
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