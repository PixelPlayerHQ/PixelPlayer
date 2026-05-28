package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPreferencesScreen(
    navController: NavController,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_ai_preferences_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationIconClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_category_ai_preferences_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                // Max Songs Limit (using a simple Slider or Input - assuming a Slider for simplicity)
                Text("Max Songs for Context: ${uiState.maxSongsForContext}")
                Slider(
                    value = uiState.maxSongsForContext.toFloat(),
                    onValueChange = { settingsViewModel.setMaxSongsForContext(it.toInt()) },
                    valueRange = 10f..200f
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_include_liked_title),
                    subtitle = stringResource(R.string.settings_ai_include_liked_subtitle),
                    checked = uiState.includeLikedSongs,
                    onCheckedChange = { settingsViewModel.setIncludeLikedSongs(it) }
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_include_daily_mix_title),
                    subtitle = stringResource(R.string.settings_ai_include_daily_mix_subtitle),
                    checked = uiState.includeDailyMixHistory,
                    onCheckedChange = { settingsViewModel.setIncludeDailyMixHistory(it) }
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_include_habits_title),
                    subtitle = stringResource(R.string.settings_ai_include_habits_subtitle),
                    checked = uiState.includeUserHabits,
                    onCheckedChange = { settingsViewModel.setIncludeUserHabits(it) }
                )
            }

            item {
                Text(
                    text = stringResource(R.string.settings_ai_local_models_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_local_models_enabled_title),
                    subtitle = stringResource(R.string.settings_ai_local_models_enabled_subtitle),
                    checked = uiState.localMlEnabled,
                    onCheckedChange = { settingsViewModel.setLocalMlEnabled(it) },
                    enabled = uiState.localMlSupported
                )
            }

            if (!uiState.localMlSupported) {
                item {
                    Text(
                        text = uiState.localMlSupportMessage.ifEmpty {
                            stringResource(R.string.settings_ai_local_models_unsupported)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.localMlActiveModelId,
                    onValueChange = { settingsViewModel.setLocalMlActiveModelId(it) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.localMlSupported,
                    label = { Text(stringResource(R.string.settings_ai_local_model_id_title)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_local_model_id_placeholder)) }
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_local_models_fallback_title),
                    subtitle = stringResource(R.string.settings_ai_local_models_fallback_subtitle),
                    checked = uiState.localMlFallbackToRemote,
                    onCheckedChange = { settingsViewModel.setLocalMlFallbackToRemote(it) },
                    enabled = uiState.localMlSupported
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_local_models_gpu_title),
                    subtitle = stringResource(R.string.settings_ai_local_models_gpu_subtitle),
                    checked = uiState.localMlUseGpu,
                    onCheckedChange = { settingsViewModel.setLocalMlUseGpu(it) },
                    enabled = uiState.localMlSupported
                )
            }

            item {
                Text("Local model prompt context: ${uiState.localMlContextSize}")
                Slider(
                    value = uiState.localMlContextSize.toFloat(),
                    onValueChange = { settingsViewModel.setLocalMlContextSize(it.toInt()) },
                    valueRange = 20f..200f,
                    enabled = uiState.localMlSupported
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.localMlOllamaUrl,
                    onValueChange = { settingsViewModel.setLocalMlOllamaUrl(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_ai_local_models_ollama_url_title)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_local_models_ollama_url_placeholder)) }
                )
            }

            item {
                OutlinedTextField(
                    value = uiState.localMlHfToken,
                    onValueChange = { settingsViewModel.setLocalMlHfToken(it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_ai_local_models_hf_token_title)) },
                    placeholder = { Text(stringResource(R.string.settings_ai_local_models_hf_token_placeholder)) }
                )
            }
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompositionLocalProvider(LocalContentAlpha provides if (enabled) ContentAlpha.high else ContentAlpha.disabled) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
