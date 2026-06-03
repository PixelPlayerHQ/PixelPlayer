package com.theveloper.pixelplay.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.extensions.PixelPlayExtensionHost
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.brahmkshatriya.echo.common.Extension
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.extension.loader.ExtensionLoader
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils
import dev.brahmkshatriya.echo.extension.loader.ExtensionUtils.get
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExtensionSettingsViewModel @Inject constructor(
    private val extensionEngine: ExtensionLoader,
    private val host: PixelPlayExtensionHost
) : ViewModel() {

    private val _extension = MutableStateFlow<Extension<*>?>(null)
    val extension: StateFlow<Extension<* >?> = _extension.asStateFlow()

    private val _settingsItems = MutableStateFlow<List<Setting>>(emptyList())
    val settingsItems: StateFlow<List<Setting>> = _settingsItems.asStateFlow()

    fun loadExtension(extensionId: String) {
        viewModelScope.launch {
            val ext = extensionEngine.all.value.find { it.metadata.id == extensionId }
            _extension.value = ext
            _settingsItems.value = ext?.get { getSettingItems() }?.getOrNull() ?: emptyList()
        }
    }

    fun updateSetting(key: String, value: Any) {
        val ext = _extension.value ?: return
        viewModelScope.launch {
            val currentSettings = ExtensionUtils.getSettings(host.context, ext.metadata)
            when (value) {
                is Boolean -> currentSettings.putBoolean(key, value)
                is String -> currentSettings.putString(key, value)
                is Int -> currentSettings.putInt(key, value)
            }
            ext.get { setSettings(currentSettings) }
            // Refresh settings UI
            _settingsItems.value = ext.get { getSettingItems() }?.getOrNull() ?: emptyList()
        }
    }
}
