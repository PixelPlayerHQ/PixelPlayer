package com.theveloper.pixelplay.data.equalizer

import android.media.audiofx.Equalizer
import android.media.audiofx.BassBoost
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages Android's built-in audio effects (Equalizer, BassBoost, Virtualizer).
 * Attaches to ExoPlayer's audio session ID for real-time audio processing.
 * 
 * Thread-safe: All effect operations run on the main thread.
 * Crossfade compatible: Effects are attached to the audio session, not the player instance.
 */
@Singleton
class EqualizerManager @Inject constructor() {
    
    companion object {
        private const val TAG = "EqualizerManager"
        private const val NUM_BANDS = 10
        private const val MIN_LEVEL = -15
        private const val MAX_LEVEL = 15
        private const val MAX_LOUDNESS_GAIN_MB = 1000
    }
    
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentAudioSessionId: Int = 0

    val isAttached: Boolean
        get() = equalizer != null && currentAudioSessionId != 0

    val hasAnyEnabledEffects: Boolean
        get() = _isEnabled.value ||
            _bassBoostEnabled.value ||
            _virtualizerEnabled.value ||
            _loudnessEnhancerEnabled.value
    
    // Normalized band levels (-15 to +15 for UI)
    private val _bandLevels = MutableStateFlow(List(NUM_BANDS) { 0 })
    val bandLevels: StateFlow<List<Int>> = _bandLevels.asStateFlow()
    
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()
    
    private val _currentPresetName = MutableStateFlow("flat")
    val currentPresetName: StateFlow<String> = _currentPresetName.asStateFlow()
    
    private val _bassBoostEnabled = MutableStateFlow(false)
    val bassBoostEnabled: StateFlow<Boolean> = _bassBoostEnabled.asStateFlow()

    private val _bassBoostStrength = MutableStateFlow(0)
    val bassBoostStrength: StateFlow<Int> = _bassBoostStrength.asStateFlow()

    private val _virtualizerEnabled = MutableStateFlow(false)
    val virtualizerEnabled: StateFlow<Boolean> = _virtualizerEnabled.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(0)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    private val _loudnessEnhancerEnabled = MutableStateFlow(false)
    val loudnessEnhancerEnabled: StateFlow<Boolean> = _loudnessEnhancerEnabled.asStateFlow()

    private val _loudnessEnhancerStrength = MutableStateFlow(0)
    val loudnessEnhancerStrength: StateFlow<Int> = _loudnessEnhancerStrength.asStateFlow()
    
    // Actual millibel range from the device's equalizer
    private var minEqLevel: Short = -1500
    private var maxEqLevel: Short = 1500

    private var loudnessEnhancer: android.media.audiofx.LoudnessEnhancer? = null

    // Global device capabilities (Checking existence of effect UUIDs)
    private var isBassBoostSupportedGlobal = false
    private var isVirtualizerSupportedGlobal = false
    private var effectsDisabledForProcess = false
    private var effectsDisableReason: String? = null
    
    init {
        checkDeviceSupport()
    }
    
    private fun checkDeviceSupport() {
        try {
            val effects = android.media.audiofx.AudioEffect.queryEffects()
            isBassBoostSupportedGlobal = effects.any { it.type == android.media.audiofx.AudioEffect.EFFECT_TYPE_BASS_BOOST }
            isVirtualizerSupportedGlobal = effects.any { it.type == android.media.audiofx.AudioEffect.EFFECT_TYPE_VIRTUALIZER }
            Timber.tag(TAG).d("Global Support Check - BassBoost: $isBassBoostSupportedGlobal, Virtualizer: $isVirtualizerSupportedGlobal")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to query global audio effects")
            // Fallback to assuming false until proven otherwise? Or true? 
            // Better false to avoid broken UI, but unlikely to fail.
        }
    }

    private fun markBassBoostUnavailable(reason: String) {
        if (!isBassBoostSupportedGlobal && bassBoost == null) return

        isBassBoostSupportedGlobal = false
        _bassBoostEnabled.value = false
        bassBoost?.runCatching { release() }
        bassBoost = null
        Timber.tag(TAG).w("BassBoost disabled for this process: %s", reason)
    }

    private fun markVirtualizerUnavailable(reason: String) {
        if (!isVirtualizerSupportedGlobal && virtualizer == null) return

        isVirtualizerSupportedGlobal = false
        _virtualizerEnabled.value = false
        virtualizer?.runCatching { release() }
        virtualizer = null
        Timber.tag(TAG).w("Virtualizer disabled for this process: %s", reason)
    }

    /**
     * Attaches the equalizer to an audio session ID.
     * Call this when the player is created or swapped during crossfade.
     */
    /**
     * Attaches the equalizer to an audio session ID.
     * Call this when the player is created or swapped during crossfade.
     */
    suspend fun attachToAudioSession(audioSessionId: Int) {
        if (effectsDisabledForProcess) {
            Timber.tag(TAG).d(
                "Skipping attachToAudioSession($audioSessionId): audio effects disabled (%s)",
                effectsDisableReason ?: "unknown reason"
            )
            return
        }

        if (audioSessionId == 0) {
            Timber.tag(TAG).w("Invalid audio session ID: 0")
            return
        }
        
        if (currentAudioSessionId == audioSessionId && equalizer != null) {
            Timber.tag(TAG).d("Already attached to session $audioSessionId")
            return
        }
        
        Timber.tag(TAG).d("Attaching to audio session: $audioSessionId")
        release()
        
        try {
            // Initialize Equalizer
            equalizer = try {
                Equalizer(0, audioSessionId).apply {
                    minEqLevel = bandLevelRange[0]
                    maxEqLevel = bandLevelRange[1]
                    enabled = _isEnabled.value
                }
            } catch (e: Exception) {
                // Some OEM/route combinations do not expose an effect engine for this session.
                // Disable effects for this process to avoid repeated hard failures and log spam.
                effectsDisabledForProcess = true
                effectsDisableReason = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}"
                _isEnabled.value = false
                _bassBoostEnabled.value = false
                _virtualizerEnabled.value = false
                _loudnessEnhancerEnabled.value = false
                isBassBoostSupportedGlobal = false
                isVirtualizerSupportedGlobal = false
                Timber.tag(TAG).w(
                    e,
                    "Audio effects unavailable on this device/audio route. Disabling EQ stack for this process."
                )
                release()
                return
            }
            
            // Retry loop for effects that might fail initially
            val maxRetries = 3
            var retryCount = 0
            
            while (bassBoost == null && retryCount < maxRetries) {
                try {
                    bassBoost = BassBoost(0, audioSessionId).apply {
                        enabled = _bassBoostEnabled.value
                        if (strengthSupported) {
                            setStrength(_bassBoostStrength.value.toShort())
                        }
                    }
                    if (bassBoost != null) Timber.tag(TAG).d("BassBoost initialized on attempt ${retryCount + 1}")
                } catch (e: Exception) {
                    Timber.tag(TAG).w("BassBoost init failed (attempt ${retryCount + 1}): ${e.message}")
                    if (retryCount < maxRetries - 1) kotlinx.coroutines.delay(300)
                }
                retryCount++
            }
            if (bassBoost == null) {
                markBassBoostUnavailable("No effect engine was created for audio session $audioSessionId after $maxRetries attempts")
            }
            
            retryCount = 0
            while (virtualizer == null && retryCount < maxRetries) {
                 try {
                    virtualizer = Virtualizer(0, audioSessionId).apply {
                        enabled = _virtualizerEnabled.value
                        if (strengthSupported) {
                            setStrength(_virtualizerStrength.value.toShort())
                        }
                    }
                    if (virtualizer != null) Timber.tag(TAG).d("Virtualizer initialized on attempt ${retryCount + 1}")
                } catch (e: Exception) {
                    Timber.tag(TAG).w("Virtualizer init failed (attempt ${retryCount + 1}): ${e.message}")
                    if (retryCount < maxRetries - 1) kotlinx.coroutines.delay(300)
                }
                retryCount++
            }
            if (virtualizer == null) {
                markVirtualizerUnavailable("No effect engine was created for audio session $audioSessionId after $maxRetries attempts")
            }

            // Initialize Loudness Enhancer (usually robust, but let's be safe)
            loudnessEnhancer = try {
                android.media.audiofx.LoudnessEnhancer(audioSessionId).apply {
                    setTargetGain(_loudnessEnhancerStrength.value.coerceIn(0, MAX_LOUDNESS_GAIN_MB))
                    enabled = _loudnessEnhancerEnabled.value
                }
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "LoudnessEnhancer not supported on this device")
                null
            }
            
            currentAudioSessionId = audioSessionId
            
            // Apply current band levels with proper mapping
            val deviceBandCount = equalizer?.numberOfBands?.toInt() ?: 0
            Timber.tag(TAG).d("Device supports $deviceBandCount bands, UI has ${_bandLevels.value.size} bands")
            applyBandLevels(_bandLevels.value)
            applyCurrentEffectStateToAttachedEffects()
            
            Timber.tag(TAG).d("Effects attached successfully. EQ bands: ${equalizer?.numberOfBands}, Range: $minEqLevel to $maxEqLevel")
            
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize audio effects")
            release()
        }
    }

    suspend fun attachToAudioSessionIfNeeded(audioSessionId: Int) {
        if (!hasAnyEnabledEffects) {
            Timber.tag(TAG).d(
                "Skipping attachToAudioSession($audioSessionId): all audio effects are disabled"
            )
            releaseIfUnused()
            return
        }

        attachToAudioSession(audioSessionId)
    }
    
    /**
     * Enables or disables the equalizer.
     */
    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        try {
            equalizer?.enabled = enabled
            Timber.tag(TAG).d("Equalizer enabled: $enabled")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set equalizer enabled state")
        }
        releaseIfUnused()
    }
    
    /**
     * Sets the level for a specific band.
     * @param bandIndex 0-4 for the 5 bands
     * @param level -15 to +15 normalized level
     */
    fun setBandLevel(bandIndex: Int, level: Int) {
        if (bandIndex !in 0 until NUM_BANDS) return
        
        val clampedLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        val newLevels = _bandLevels.value.toMutableList()
        newLevels[bandIndex] = clampedLevel
        _bandLevels.value = newLevels
        
        applyBandLevel(bandIndex, clampedLevel)
        
        // Switch to custom preset when manually adjusting
        _currentPresetName.value = "custom"
    }
    
    /**
     * Applies a preset to the equalizer.
     */
    fun applyPreset(preset: EqualizerPreset) {
        _currentPresetName.value = preset.name
        _bandLevels.value = preset.bandLevels
        applyBandLevels(preset.bandLevels)
        Timber.tag(TAG).d("Applied preset: ${preset.displayName}")
    }

    /**
     * Sets bass boost enabled state.
     */
    fun setBassBoostEnabled(enabled: Boolean) {
        if (!isBassBoostSupportedGlobal) {
            _bassBoostEnabled.value = false
            releaseIfUnused()
            return
        }
        _bassBoostEnabled.value = enabled
        try {
            bassBoost?.enabled = enabled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set bass boost enabled")
        }
        releaseIfUnused()
    }
    
    /**
     * Sets bass boost strength (0-1000).
     */
    fun setBassBoostStrength(strength: Int) {
        if (!isBassBoostSupportedGlobal) return

        val clampedStrength = strength.coerceIn(0, 1000)
        _bassBoostStrength.value = clampedStrength
        
        try {
            bassBoost?.apply {
                if (strengthSupported) {
                    setStrength(clampedStrength.toShort())
                }
            }
            Timber.tag(TAG).d("Bass boost strength: $clampedStrength")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set bass boost")
        }
    }

    /**
     * Sets virtualizer enabled state.
     */
    fun setVirtualizerEnabled(enabled: Boolean) {
        if (!isVirtualizerSupportedGlobal) {
            _virtualizerEnabled.value = false
            releaseIfUnused()
            return
        }
        _virtualizerEnabled.value = enabled
        try {
            virtualizer?.enabled = enabled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set virtualizer enabled")
        }
        releaseIfUnused()
    }
    
    /**
     * Sets virtualizer (surround) strength (0-1000).
     */
    fun setVirtualizerStrength(strength: Int) {
        if (!isVirtualizerSupportedGlobal) return

        val clampedStrength = strength.coerceIn(0, 1000)
        _virtualizerStrength.value = clampedStrength
        
        try {
            virtualizer?.apply {
                if (strengthSupported) {
                    setStrength(clampedStrength.toShort())
                }
            }
            Timber.tag(TAG).d("Virtualizer strength: $clampedStrength")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set virtualizer")
        }
    }

    /**
     * Sets loudness enhancer enabled state.
     */
    fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        _loudnessEnhancerEnabled.value = enabled
        try {
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set loudness enhancer enabled")
        }
        releaseIfUnused()
    }

    /**
     * Sets loudness enhancer strength (gain in mB).
     * 0 to 1000mB (10dB) is used as a stable cross-device range.
     */
    fun setLoudnessEnhancerStrength(strength: Int) {
        val clampedStrength = strength.coerceIn(0, MAX_LOUDNESS_GAIN_MB)
        _loudnessEnhancerStrength.value = clampedStrength

        try {
            loudnessEnhancer?.setTargetGain(clampedStrength)
            Timber.tag(TAG).d("Loudness enhancer strength: $clampedStrength")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set loudness enhancer")
        }
    }
    
    /**
     * Restores equalizer state from saved preferences.
     */
    fun restoreState(
        enabled: Boolean,
        presetName: String,
        customBands: List<Int>,
        bassBoostEnabled: Boolean,
        bassBoostStrength: Int,
        virtualizerEnabled: Boolean,
        virtualizerStrength: Int,
        loudnessEnabled: Boolean,
        loudnessStrength: Int
    ) {
        _isEnabled.value = enabled
        _bassBoostEnabled.value = bassBoostEnabled
        _bassBoostStrength.value = bassBoostStrength
        _virtualizerEnabled.value = virtualizerEnabled
        _virtualizerStrength.value = virtualizerStrength
        _loudnessEnhancerEnabled.value = loudnessEnabled
        _loudnessEnhancerStrength.value = loudnessStrength.coerceIn(0, MAX_LOUDNESS_GAIN_MB)
        
        val preset = if (presetName == "custom") {
            EqualizerPreset.custom(customBands)
        } else {
            EqualizerPreset.fromName(presetName)
        }
        
        _currentPresetName.value = preset.name
        _bandLevels.value = preset.bandLevels
        
        // Apply if already attached
        if (equalizer != null) {
            if (!hasAnyEnabledEffects) {
                releaseIfUnused()
                return
            }
            equalizer?.enabled = enabled
            applyBandLevels(preset.bandLevels)
            applyCurrentEffectStateToAttachedEffects()
        }
    }

    private fun releaseIfUnused() {
        if (!hasAnyEnabledEffects && isAttached) {
            Timber.tag(TAG).d("Releasing audio effects because all effect toggles are disabled")
            release()
        }
    }

    private fun applyCurrentEffectStateToAttachedEffects() {
        try {
            bassBoost?.apply {
                enabled = _bassBoostEnabled.value
                if (strengthSupported) {
                    setStrength(_bassBoostStrength.value.coerceIn(0, 1000).toShort())
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed applying bass boost state")
        }

        try {
            virtualizer?.apply {
                enabled = _virtualizerEnabled.value
                if (strengthSupported) {
                    setStrength(_virtualizerStrength.value.coerceIn(0, 1000).toShort())
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed applying virtualizer state")
        }

        try {
            loudnessEnhancer?.apply {
                setTargetGain(_loudnessEnhancerStrength.value.coerceIn(0, MAX_LOUDNESS_GAIN_MB))
                enabled = _loudnessEnhancerEnabled.value
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed applying loudness state")
        }
    }
    
    private fun applyBandLevels(levels: List<Int>) {
        val eq = equalizer ?: return
        val deviceBandCount = eq.numberOfBands.toInt()
        
        if (deviceBandCount <= 0) return
        
        // Map UI bands (10) to device bands (typically 5)
        // If device has fewer bands than UI, we need to average/map appropriately
        val uiBandCount = levels.size
        
        if (deviceBandCount >= uiBandCount) {
            // Device has same or more bands than UI - apply directly
            levels.forEachIndexed { index, level ->
                applyBandLevelDirect(index, level)
            }
        } else {
            // Device has fewer bands than UI - map UI bands to device bands
            // Calculate how many UI bands map to each device band
            val ratio = uiBandCount.toFloat() / deviceBandCount.toFloat()
            
            for (deviceBand in 0 until deviceBandCount) {
                // Calculate which UI bands this device band covers
                val startUiBand = (deviceBand * ratio).toInt()
                val endUiBand = ((deviceBand + 1) * ratio).toInt().coerceAtMost(uiBandCount)
                
                // Average the UI band levels for this device band
                var sum = 0
                var count = 0
                for (uiBand in startUiBand until endUiBand) {
                    if (uiBand < levels.size) {
                        sum += levels[uiBand]
                        count++
                    }
                }
                
                val averageLevel = if (count > 0) sum / count else 0
                applyBandLevelDirect(deviceBand, averageLevel)
            }
        }
    }
    
    private fun applyBandLevelDirect(bandIndex: Int, normalizedLevel: Int) {
        val eq = equalizer ?: return
        if (bandIndex >= eq.numberOfBands) return
        
        // Convert normalized level (-15 to +15) to device millibel range
        val range = maxEqLevel - minEqLevel
        val millibelLevel = (minEqLevel + (normalizedLevel + 15) * range / 30).toShort()
        
        try {
            eq.setBandLevel(bandIndex.toShort(), millibelLevel)
            Timber.tag(TAG).v("Set band $bandIndex to $millibelLevel mB (normalized: $normalizedLevel)")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to set band $bandIndex level")
        }
    }
    
    private fun applyBandLevel(bandIndex: Int, normalizedLevel: Int) {
        // This now triggers a full reapply to ensure proper mapping
        val currentLevels = _bandLevels.value.toMutableList()
        if (bandIndex < currentLevels.size) {
            currentLevels[bandIndex] = normalizedLevel.coerceIn(MIN_LEVEL, MAX_LEVEL)
            applyBandLevels(currentLevels)
        }
    }
    
    /**
     * Gets the center frequencies for all bands.
     */
    fun getBandFrequencies(): List<Int> {
        val eq = equalizer ?: return listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        return (0 until eq.numberOfBands).map { band ->
            eq.getCenterFreq(band.toShort()) / 1000 // Convert milliHz to Hz
        }
    }
    
    /**
     * Checks if bass boost is supported on this device.
     */
    fun isBassBoostSupported(): Boolean = isBassBoostSupportedGlobal
    
    /**
     * Checks if virtualizer is supported on this device.
     */
    fun isVirtualizerSupported(): Boolean = isVirtualizerSupportedGlobal

    /**
     * Checks if loudness enhancer is supported on this device.
     */
    fun isLoudnessEnhancerSupported(): Boolean = loudnessEnhancer != null || android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT
    
    /**
     * Releases all audio effect resources.
     */
    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            loudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error releasing audio effects")
        }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudnessEnhancer = null
        currentAudioSessionId = 0
        Timber.tag(TAG).d("Audio effects released")
    }
}
