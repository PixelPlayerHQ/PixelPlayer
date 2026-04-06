package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.data.equalizer.EqualizerManager
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.data.preferences.EqualizerPreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json // Added import

data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val currentPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val bandLevels: List<Int> = List(10) { 0 },
    val editingPresetName: String? = null,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0f, // Changed to Float
    val virtualizerEnabled: Boolean = false,
    val virtualizerStrength: Float = 0f, // Changed to Float
    val loudnessEnhancerEnabled: Boolean = false,
    val loudnessEnhancerStrength: Float = 0f, // Changed to Float
    val isBassBoostSupported: Boolean = true,
    val isVirtualizerSupported: Boolean = true,
    val isLoudnessEnhancerSupported: Boolean = true,
    val viewMode: UserPreferencesRepository.EqualizerViewMode = UserPreferencesRepository.EqualizerViewMode.SLIDERS,
    val isBassBoostDismissed: Boolean = false,
    val isVirtualizerDismissed: Boolean = false,
    val isLoudnessDismissed: Boolean = false,
    val customPresets: List<EqualizerPreset> = emptyList(), // Added
    val pinnedPresetsNames: List<String> = emptyList(), // Added
) {
    // Computed property for accessible presets (Pinned)
    val accessiblePresets: List<EqualizerPreset>
        get() {
            // Map pinned names to actual Presets (Default or Custom)
            return pinnedPresetsNames.mapNotNull { name ->
                // First check custom presets
                customPresets.find { it.name == name }
                    ?: EqualizerPreset.fromName(name) // Then standard defaults
            }
        }
        
    // Computed property for All Available Presets (for Edit Sheet)
    val allAvailablePresets: List<EqualizerPreset>
        get() = EqualizerPreset.ALL_PRESETS + customPresets
}

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val equalizerManager: EqualizerManager,
    private val equalizerPreferencesRepository: EqualizerPreferencesRepository,
    private val dualPlayerEngine: DualPlayerEngine,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    companion object {
        private const val TAG = "EqualizerViewModel"
        private const val SLIDER_PERSIST_DEBOUNCE_MS = 150L
        private val json = Json { ignoreUnknownKeys = true } // Assuming Json is needed
    }

    private val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
    
    // UI-only state for view mode
    // UI-only state for view mode - Now persisted
    // val isGraphView: StateFlow<Boolean> = _isGraphView.asStateFlow() // Removed local state

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    private val _systemVolume = MutableStateFlow(0f)
    val systemVolume: StateFlow<Float> = _systemVolume.asStateFlow()

    private var persistBandLevelsJob: Job? = null
    private var persistBassBoostJob: Job? = null
    private var persistVirtualizerJob: Job? = null
    private var persistLoudnessJob: Job? = null
    
    init {
        initializeEqualizer()
        observeEqualizerState()
        loadSystemVolume()
    }
    
    private fun loadSystemVolume() {
        try {
            val current = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
            _systemVolume.value = if (max > 0) current.toFloat() / max.toFloat() else 0.5f
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load system volume")
        }
    }

    fun setSystemVolume(percent: Float) {
        viewModelScope.launch {
            try {
                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                val target = (percent * max).roundToInt().coerceIn(0, max)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0) // flag 0 to not show system UI
                _systemVolume.value = percent
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to set system volume")
            }
        }
    }
    
    private fun initializeEqualizer() {
        viewModelScope.launch {
            Timber.tag(TAG).d("Initializing equalizer...")
            
            if (!equalizerManager.isAttached) {
                val enabled = equalizerPreferencesRepository.equalizerEnabledFlow.first()
                val presetName = equalizerPreferencesRepository.equalizerPresetFlow.first()
                val customBands = equalizerPreferencesRepository.equalizerCustomBandsFlow.first()
                val bassBoostEnabled = equalizerPreferencesRepository.bassBoostEnabledFlow.first()
                val bassBoost = equalizerPreferencesRepository.bassBoostStrengthFlow.first()
                val virtualizerEnabled = equalizerPreferencesRepository.virtualizerEnabledFlow.first()
                val virtualizer = equalizerPreferencesRepository.virtualizerStrengthFlow.first()
                val loudnessEnabled = equalizerPreferencesRepository.loudnessEnhancerEnabledFlow.first()
                val loudnessStrength = equalizerPreferencesRepository.loudnessEnhancerStrengthFlow.first()
                
                equalizerManager.restoreState(
                    enabled, presetName, customBands, 
                    bassBoostEnabled, bassBoost, 
                    virtualizerEnabled, virtualizer,
                    loudnessEnabled, loudnessStrength
                )
                
                val initialSessionId = dualPlayerEngine.getAudioSessionId()
                if (initialSessionId != 0) {
                    equalizerManager.attachToAudioSessionIfNeeded(initialSessionId)
                }
            } else {
                Timber.tag(TAG).d("Equalizer already attached by service, skipping restore.")
            }
            
            // Update UI state with device capabilities
            _uiState.value = _uiState.value.copy(
                isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
            )

            dualPlayerEngine.activeAudioSessionId.collect { sessionId ->
                if (sessionId != 0) {
                    Timber.tag(TAG).d("Audio Session ID changed to $sessionId.")
                    _uiState.value = _uiState.value.copy(
                        isBassBoostSupported = equalizerManager.isBassBoostSupported(),
                        isVirtualizerSupported = equalizerManager.isVirtualizerSupported(),
                        isLoudnessEnhancerSupported = equalizerManager.isLoudnessEnhancerSupported()
                    )
                }
            }
        }
    }
    
    private fun observeEqualizerState() {
        viewModelScope.launch {
            // Combine flows for UI State
            combine(
                equalizerPreferencesRepository.equalizerEnabledFlow,
                equalizerPreferencesRepository.equalizerPresetFlow,
                equalizerPreferencesRepository.equalizerCustomBandsFlow,
                equalizerPreferencesRepository.bassBoostEnabledFlow,
                equalizerPreferencesRepository.bassBoostStrengthFlow,
                equalizerPreferencesRepository.virtualizerEnabledFlow,
                equalizerPreferencesRepository.virtualizerStrengthFlow,
                equalizerPreferencesRepository.loudnessEnhancerEnabledFlow,
                equalizerPreferencesRepository.loudnessEnhancerStrengthFlow,
                equalizerPreferencesRepository.bassBoostDismissedFlow,
                equalizerPreferencesRepository.virtualizerDismissedFlow,
                equalizerPreferencesRepository.loudnessDismissedFlow,
                equalizerPreferencesRepository.equalizerViewModeFlow,
                equalizerPreferencesRepository.customPresetsFlow, // Added
                equalizerPreferencesRepository.pinnedPresetsFlow // Added
            ) { values -> // Too many args for standard destructuring, use array/list access
                 val enabled = values[0] as Boolean
                 val presetName = values[1] as String
                 val customBands = (values[2] as? List<*>)
                     ?.mapNotNull { (it as? Number)?.toInt() }
                     ?: emptyList()
                 val bbEnabled = values[3] as Boolean
                 val bbStrength = values[4] as Int
                 val vEnabled = values[5] as Boolean
                 val vStrength = values[6] as Int
                 val lEnabled = values[7] as Boolean
                 val lStrength = values[8] as Int
                 val bbDismissed = values[9] as Boolean
                 val vDismissed = values[10] as Boolean
                 val lDismissed = values[11] as Boolean
                 val viewMode = values[12] as UserPreferencesRepository.EqualizerViewMode
                 val customPresets = (values[13] as? List<*>)?.filterIsInstance<EqualizerPreset>() ?: emptyList()
                 val pinnedPresets = (values[14] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

                val currentPreset = if (presetName == "custom") {
                    EqualizerPreset.custom(customBands)
                } else {
                     // Check custom presets first
                     customPresets.find { it.name == presetName }
                        ?: EqualizerPreset.fromName(presetName)
                }

                EqualizerUiState(
                    isEnabled = enabled,
                    currentPreset = currentPreset,
                    bandLevels = if (currentPreset.name == "custom") customBands else currentPreset.bandLevels,
                    editingPresetName = _uiState.value.editingPresetName,
                    bassBoostEnabled = bbEnabled,
                    bassBoostStrength = bbStrength.toFloat(), // Raw 0-1000
                    virtualizerEnabled = vEnabled,
                    virtualizerStrength = vStrength.toFloat(), // Raw 0-1000
                    loudnessEnhancerEnabled = lEnabled,
                    loudnessEnhancerStrength = lStrength.toFloat(), // Raw 0-1000
                    isBassBoostDismissed = bbDismissed,
                    isVirtualizerDismissed = vDismissed,
                    isLoudnessDismissed = lDismissed,
                    viewMode = viewMode,
                    // New State
                    customPresets = customPresets,
                    pinnedPresetsNames = pinnedPresets,
                    // Capabilities (Keep existing values)
                    isBassBoostSupported = _uiState.value.isBassBoostSupported,
                    isVirtualizerSupported = _uiState.value.isVirtualizerSupported,
                    isLoudnessEnhancerSupported = _uiState.value.isLoudnessEnhancerSupported
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    fun cycleViewMode() {
        viewModelScope.launch {
            val currentMode = _uiState.value.viewMode
            val nextMode = when (currentMode) {
                UserPreferencesRepository.EqualizerViewMode.SLIDERS -> UserPreferencesRepository.EqualizerViewMode.GRAPH
                UserPreferencesRepository.EqualizerViewMode.GRAPH -> UserPreferencesRepository.EqualizerViewMode.HYBRID
                UserPreferencesRepository.EqualizerViewMode.HYBRID -> UserPreferencesRepository.EqualizerViewMode.SLIDERS
            }
            equalizerPreferencesRepository.setEqualizerViewMode(nextMode)
        }
    }
    
    fun setEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        _uiState.update { current ->
            current.copy(isEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setEqualizerEnabled(enabled)
        }
    }

    fun toggleEqualizer() {
        setEnabled(!_uiState.value.isEnabled)
    }
    
    fun selectPreset(preset: EqualizerPreset) {
        persistBandLevelsJob?.cancel()
        equalizerManager.applyPreset(preset)
        _uiState.update { current ->
            current.copy(
                currentPreset = preset,
                bandLevels = preset.bandLevels,
                editingPresetName = null
            )
        }
        viewModelScope.launch {
            equalizerPreferencesRepository.setEqualizerPreset(preset.name)
            if (!preset.isCustom) {
                equalizerPreferencesRepository.setEqualizerCustomBands(preset.bandLevels)
            }
        }
    }
    
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in _uiState.value.bandLevels.indices) return
        val clampedLevel = level.coerceIn(-15, 15)

        equalizerManager.setBandLevel(bandIndex, clampedLevel)
        val updatedBands = equalizerManager.bandLevels.value
        _uiState.update { current ->
            val editingName = current.editingPresetName
                ?: current.currentPreset.name.takeIf { current.currentPreset.isCustom && it != "custom" }
            current.copy(
                currentPreset = EqualizerPreset.custom(updatedBands),
                bandLevels = updatedBands,
                editingPresetName = editingName
            )
        }

        persistBandLevelsJob?.cancel()
        persistBandLevelsJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setEqualizerCustomBands(updatedBands)
            equalizerPreferencesRepository.setEqualizerPreset("custom")
        }
    }
    
    fun saveCurrentAsCustomPreset(name: String) {
        viewModelScope.launch {
            // Create preset from current custom bands
            val bands = equalizerManager.bandLevels.value
            val preset = EqualizerPreset(name, name, bands, true)
            equalizerPreferencesRepository.saveCustomPreset(preset)
            
            // Also pin it automatically
            togglePinPreset(name)
            
            // Select it
            selectPreset(preset)
        }
    }
    
    fun deleteCustomPreset(preset: EqualizerPreset) {
        viewModelScope.launch {
            equalizerPreferencesRepository.deleteCustomPreset(preset.name)
            // If deleting current, revert to Flat
            if (_uiState.value.currentPreset.name == preset.name) {
                selectPreset(EqualizerPreset.FLAT)
            }
        }
    }
    
    fun renameCustomPreset(oldName: String, newName: String) {
        if (newName.isBlank() || oldName == newName) return
        viewModelScope.launch {
            equalizerPreferencesRepository.renameCustomPreset(oldName, newName)
        }
    }
    
    fun updateCustomPresetBands(presetName: String) {
        viewModelScope.launch {
            val bands = equalizerManager.bandLevels.value
            equalizerPreferencesRepository.updateCustomPresetBands(presetName, bands)
            selectPreset(EqualizerPreset(presetName, presetName, bands, true))
        }
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        equalizerManager.setBassBoostEnabled(enabled)
        _uiState.update { current ->
            current.copy(bassBoostEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setBassBoostEnabled(enabled)
        }
    }
    
    fun setBassBoostStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setBassBoostStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(bassBoostStrength = clampedStrength.toFloat())
        }

        persistBassBoostJob?.cancel()
        persistBassBoostJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setBassBoostStrength(clampedStrength)
        }
    }

    fun setVirtualizerEnabled(enabled: Boolean) {
        equalizerManager.setVirtualizerEnabled(enabled)
        _uiState.update { current ->
            current.copy(virtualizerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setVirtualizerEnabled(enabled)
        }
    }
    
    fun setVirtualizerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setVirtualizerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(virtualizerStrength = clampedStrength.toFloat())
        }

        persistVirtualizerJob?.cancel()
        persistVirtualizerJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setVirtualizerStrength(clampedStrength)
        }
    }

    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        equalizerManager.setLoudnessEnhancerEnabled(enabled)
        _uiState.update { current ->
            current.copy(loudnessEnhancerEnabled = enabled)
        }
        viewModelScope.launch {
            equalizerManager.attachToAudioSessionIfNeeded(dualPlayerEngine.getAudioSessionId())
            equalizerPreferencesRepository.setLoudnessEnhancerEnabled(enabled)
        }
    }

    fun setLoudnessEnhancerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, 1000)
        equalizerManager.setLoudnessEnhancerStrength(clampedStrength)
        _uiState.update { current ->
            current.copy(loudnessEnhancerStrength = clampedStrength.toFloat())
        }

        persistLoudnessJob?.cancel()
        persistLoudnessJob = viewModelScope.launch {
            delay(SLIDER_PERSIST_DEBOUNCE_MS)
            equalizerPreferencesRepository.setLoudnessEnhancerStrength(clampedStrength)
        }
    }

    fun setBassBoostDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setBassBoostDismissed(dismissed)
        }
    }

    fun setVirtualizerDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setVirtualizerDismissed(dismissed)
        }
    }

    fun setLoudnessDismissed(dismissed: Boolean) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setLoudnessDismissed(dismissed)
        }
    }
    
    fun updatePinnedPresetsOrder(newOrder: List<String>) {
        viewModelScope.launch {
            equalizerPreferencesRepository.setPinnedPresets(newOrder)
        }
    }
    
    fun resetPinnedPresetsToDefault() {
        viewModelScope.launch {
            // Reset to default order: all standard presets visible, in original order
            val defaultOrder = EqualizerPreset.ALL_PRESETS.map { it.name }
            equalizerPreferencesRepository.setPinnedPresets(defaultOrder)
        }
    }
    
    fun togglePinPreset(presetName: String) {
        viewModelScope.launch {
            val currentPinned = _uiState.value.pinnedPresetsNames.toMutableList()
            if (currentPinned.contains(presetName)) {
                currentPinned.remove(presetName)
            } else {
                currentPinned.add(presetName)
            }
            equalizerPreferencesRepository.setPinnedPresets(currentPinned)
        }
    }
    
    /**
     * Reattaches the equalizer to a new audio session.
     * Call this when the player swaps during crossfade.
     */
    fun reattachToPlayer() {
        viewModelScope.launch {
            val audioSessionId = dualPlayerEngine.getAudioSessionId()
            Timber.tag(TAG).d("Reattaching equalizer to new audio session: $audioSessionId")
            equalizerManager.attachToAudioSessionIfNeeded(audioSessionId)
        }
    }

    private fun persistLatestStateAsync() {
        val latest = _uiState.value
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                equalizerPreferencesRepository.setEqualizerEnabled(latest.isEnabled)
                equalizerPreferencesRepository.setEqualizerPreset(latest.currentPreset.name)
                equalizerPreferencesRepository.setEqualizerCustomBands(equalizerManager.bandLevels.value)
                equalizerPreferencesRepository.setBassBoostEnabled(latest.bassBoostEnabled)
                equalizerPreferencesRepository.setBassBoostStrength(latest.bassBoostStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setVirtualizerEnabled(latest.virtualizerEnabled)
                equalizerPreferencesRepository.setVirtualizerStrength(latest.virtualizerStrength.toInt().coerceIn(0, 1000))
                equalizerPreferencesRepository.setLoudnessEnhancerEnabled(latest.loudnessEnhancerEnabled)
                equalizerPreferencesRepository.setLoudnessEnhancerStrength(latest.loudnessEnhancerStrength.toInt().coerceIn(0, 1000))
            }.onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to flush equalizer state during onCleared")
            }
        }
    }
    
    override fun onCleared() {
        persistBandLevelsJob?.cancel()
        persistBassBoostJob?.cancel()
        persistVirtualizerJob?.cancel()
        persistLoudnessJob?.cancel()
        persistLatestStateAsync()
        super.onCleared()
        // Don't release equalizer here - it should persist across screen navigation
        Timber.tag(TAG).d("ViewModel cleared")
    }
}
