package com.theveloper.pixelplay.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EqualizerPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
        val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")
        val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
        val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
        val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
        val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json")
        val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json")
    }

    val equalizerViewModeFlow: Flow<UserPreferencesRepository.EqualizerViewMode> = dataStore.data.map { preferences ->
        val modeString = preferences[Keys.VIEW_MODE]
        if (modeString != null) {
            try {
                UserPreferencesRepository.EqualizerViewMode.valueOf(modeString)
            } catch (_: Exception) {
                UserPreferencesRepository.EqualizerViewMode.SLIDERS
            }
        } else {
            val isGraph = preferences[booleanPreferencesKey("is_graph_view")] ?: false
            if (isGraph) UserPreferencesRepository.EqualizerViewMode.GRAPH else UserPreferencesRepository.EqualizerViewMode.SLIDERS
        }
    }

    val equalizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_ENABLED] ?: false
    }

    val equalizerPresetFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.EQUALIZER_PRESET] ?: "flat"
    }

    val equalizerCustomBandsFlow: Flow<List<Int>> = dataStore.data.map { preferences ->
        val stored = preferences[Keys.EQUALIZER_CUSTOM_BANDS]
        if (stored != null) {
            try {
                val decoded = json.decodeFromString<List<Int>>(stored)
                when {
                    decoded.size >= 10 -> decoded.take(10)
                    decoded.isEmpty() -> List(10) { 0 }
                    else -> decoded + List(10 - decoded.size) { 0 }
                }
            } catch (_: Exception) {
                List(10) { 0 }
            }
        } else {
            List(10) { 0 }
        }
    }

    val bassBoostStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_STRENGTH] ?: 0
    }

    val virtualizerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_STRENGTH] ?: 0
    }

    val bassBoostEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_ENABLED] ?: false
    }

    val virtualizerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_ENABLED] ?: false
    }

    val loudnessEnhancerEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_ENHANCER_ENABLED] ?: false
    }

    val loudnessEnhancerStrengthFlow: Flow<Int> = dataStore.data.map { preferences ->
        (preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] ?: 0).coerceIn(0, 1000)
    }

    val bassBoostDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.BASS_BOOST_DISMISSED] ?: false
    }

    val virtualizerDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.VIRTUALIZER_DISMISSED] ?: false
    }

    val loudnessDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.LOUDNESS_DISMISSED] ?: false
    }

    val customPresetsFlow: Flow<List<EqualizerPreset>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.CUSTOM_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<EqualizerPreset>>(jsonString)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    val pinnedPresetsFlow: Flow<List<String>> = dataStore.data.map { preferences ->
        val jsonString = preferences[Keys.PINNED_PRESETS]
        if (jsonString != null) {
            try {
                json.decodeFromString<List<String>>(jsonString)
            } catch (_: Exception) {
                EqualizerPreset.ALL_PRESETS.map { it.name }
            }
        } else {
            EqualizerPreset.ALL_PRESETS.map { it.name }
        }
    }

    suspend fun setEqualizerViewMode(mode: UserPreferencesRepository.EqualizerViewMode) =
        dataStore.edit { preferences ->
            preferences[Keys.VIEW_MODE] = mode.name
        }

    suspend fun setEqualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_ENABLED] = enabled
        }

    suspend fun setEqualizerPreset(preset: String) =
        dataStore.edit { preferences ->
            preferences[Keys.EQUALIZER_PRESET] = preset
        }

    suspend fun setEqualizerCustomBands(bands: List<Int>) =
        dataStore.edit { preferences ->
            val normalized = when {
                bands.size >= 10 -> bands.take(10)
                bands.isEmpty() -> List(10) { 0 }
                else -> bands + List(10 - bands.size) { 0 }
            }
            preferences[Keys.EQUALIZER_CUSTOM_BANDS] = json.encodeToString(normalized)
        }

    suspend fun setBassBoostStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setVirtualizerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_ENABLED] = enabled
        }

    suspend fun setVirtualizerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_ENABLED] = enabled
        }

    suspend fun setLoudnessEnhancerStrength(strength: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_ENHANCER_STRENGTH] = strength.coerceIn(0, 1000)
        }

    suspend fun setBassBoostDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.BASS_BOOST_DISMISSED] = dismissed
        }

    suspend fun setVirtualizerDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.VIRTUALIZER_DISMISSED] = dismissed
        }

    suspend fun setLoudnessDismissed(dismissed: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.LOUDNESS_DISMISSED] = dismissed
        }

    suspend fun setPinnedPresets(presetNames: List<String>) =
        dataStore.edit { preferences ->
            preferences[Keys.PINNED_PRESETS] = json.encodeToString(presetNames)
        }

    suspend fun saveCustomPreset(preset: EqualizerPreset) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == preset.name }
        current.add(preset)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }

    suspend fun deleteCustomPreset(presetName: String) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == presetName }
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        if (pinned.remove(presetName)) {
            setPinnedPresets(pinned)
        }
    }

    suspend fun renameCustomPreset(oldName: String, newName: String) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == oldName }
        if (index == -1) return

        current[index] = current[index].copy(name = newName, displayName = newName)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }

        val pinned = pinnedPresetsFlow.first().toMutableList()
        val pinnedIndex = pinned.indexOf(oldName)
        if (pinnedIndex != -1) {
            pinned[pinnedIndex] = newName
            setPinnedPresets(pinned)
        }

        val activePreset = dataStore.data.first()[Keys.EQUALIZER_PRESET]
        if (activePreset == oldName) {
            dataStore.edit { preferences ->
                preferences[Keys.EQUALIZER_PRESET] = newName
            }
        }
    }

    suspend fun updateCustomPresetBands(presetName: String, bandLevels: List<Int>) {
        val current = customPresetsFlow.first().toMutableList()
        val index = current.indexOfFirst { it.name == presetName }
        if (index == -1) return

        current[index] = current[index].copy(bandLevels = bandLevels)
        dataStore.edit { preferences ->
            preferences[Keys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }
}
