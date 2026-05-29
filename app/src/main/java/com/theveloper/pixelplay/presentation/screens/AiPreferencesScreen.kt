@file:OptIn(ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelStatus
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

@Composable
fun AiPreferencesScreen(
    navController: NavController,
    onNavigationIconClick: () -> Unit,
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val localModels by settingsViewModel.availableLocalModels.collectAsStateWithLifecycle(initialValue = emptyList())
    val modelStatuses by settingsViewModel.localModelStatuses.collectAsStateWithLifecycle(initialValue = emptyMap())
    var showDeveloperOptions by remember { mutableStateOf(false) }
    var showModelCatalog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_category_ai_preferences_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigationIconClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_category_ai_preferences_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Provider Selection Section
            item {
                Text(
                    text = stringResource(R.string.settings_ai_provider_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            item {
                ProviderSelectionCard(
                    selectedProvider = uiState.aiProvider,
                    onProviderChange = { settingsViewModel.onAiProviderChange(it) }
                )
            }

            // API Key Section (for cloud providers)
            if (uiState.aiProvider != "LOCAL" && uiState.aiProvider != "OLLAMA") {
                item {
                    ApiKeyInputCard(
                        provider = uiState.aiProvider,
                        apiKey = uiState.currentApiKey,
                        onApiKeyChange = { settingsViewModel.onAiApiKeyChange(it) }
                    )
                }
            }

            // Model Selection Section
            if (uiState.aiProvider != "LOCAL") {
                item {
                    Text(
                        text = stringResource(R.string.settings_ai_model_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                item {
                    ModelSelectionCard(
                        provider = uiState.aiProvider,
                        selectedModel = uiState.currentModel,
                        availableModels = uiState.availableModels,
                        isLoading = uiState.isLoadingModels,
                        onModelChange = { settingsViewModel.onAiModelChange(it) }
                    )
                }
            }

            // Generation Settings Section
            item {
                Text(
                    text = stringResource(R.string.settings_ai_developer_options_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_streaming_title),
                    subtitle = stringResource(R.string.settings_ai_streaming_subtitle),
                    checked = uiState.aiEnableStreaming,
                    onCheckedChange = { settingsViewModel.setAiEnableStreaming(it) }
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_include_context_title),
                    subtitle = stringResource(R.string.settings_ai_include_context_subtitle),
                    checked = uiState.aiIncludeContext,
                    onCheckedChange = { settingsViewModel.setAiIncludeContext(it) }
                )
            }

            // Temperature Slider
            item {
                SliderPreference(
                    title = stringResource(R.string.settings_ai_temperature_title),
                    subtitle = stringResource(R.string.settings_ai_temperature_subtitle),
                    value = uiState.aiTemperature,
                    valueRange = 0f..2f,
                    onValueChange = { settingsViewModel.setAiTemperature(it) },
                    valueFormatter = { String.format("%.1f", it) }
                )
            }

            // Max Tokens Slider
            item {
                SliderPreference(
                    title = stringResource(R.string.settings_ai_max_tokens_title),
                    subtitle = stringResource(R.string.settings_ai_max_tokens_subtitle),
                    value = uiState.aiMaxTokens.toFloat(),
                    valueRange = 256f..8192f,
                    onValueChange = { settingsViewModel.setAiMaxTokens(it.toInt()) },
                    valueFormatter = { "${it.toInt()}" }
                )
            }

            // Context Settings Section
            item {
                Text(
                    text = stringResource(R.string.setcat_prompt_behavior),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                )
            }

            item {
                SliderPreference(
                    title = stringResource(R.string.settings_ai_max_songs_title),
                    subtitle = stringResource(R.string.settings_ai_max_songs_subtitle),
                    value = uiState.maxSongsForContext.toFloat(),
                    valueRange = 10f..200f,
                    onValueChange = { settingsViewModel.setMaxSongsForContext(it.toInt()) },
                    valueFormatter = { "${it.toInt()}" }
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

            // Local Models Section
            item {
                Text(
                    text = stringResource(R.string.settings_ai_local_models_section),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
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
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = uiState.localMlSupportMessage.ifEmpty {
                                    stringResource(R.string.settings_ai_local_models_unsupported)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Model Selection Dropdown for Local Models
            if (uiState.localMlSupported) {
                item {
                    LocalModelDropdown(
                        selectedModelId = uiState.localMlActiveModelId,
                        availableModels = localModels,
                        modelStatuses = modelStatuses,
                        onModelSelect = { settingsViewModel.setLocalMlActiveModelId(it) }
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_ai_local_models_fallback_title),
                        subtitle = stringResource(R.string.settings_ai_local_models_fallback_subtitle),
                        checked = uiState.localMlFallbackToRemote,
                        onCheckedChange = { settingsViewModel.setLocalMlFallbackToRemote(it) }
                    )
                }

                item {
                    SwitchPreference(
                        title = stringResource(R.string.settings_ai_local_models_gpu_title),
                        subtitle = stringResource(R.string.settings_ai_local_models_gpu_subtitle),
                        checked = uiState.localMlUseGpu,
                        onCheckedChange = { settingsViewModel.setLocalMlUseGpu(it) }
                    )
                }

                item {
                    SliderPreference(
                        title = "Local Model Context Size",
                        subtitle = "Number of songs to include in local context",
                        value = uiState.localMlContextSize.toFloat(),
                        valueRange = 20f..200f,
                        onValueChange = { settingsViewModel.setLocalMlContextSize(it.toInt()) },
                        valueFormatter = { "${it.toInt()}" }
                    )
                }

                // Model Catalog Button
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelCatalog = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = stringResource(R.string.settings_ai_model_catalog_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = stringResource(R.string.settings_ai_model_catalog_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                }

                // Ollama URL
                item {
                    OutlinedTextField(
                        value = uiState.localMlOllamaUrl,
                        onValueChange = { settingsViewModel.setLocalMlOllamaUrl(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_ai_local_models_ollama_url_title)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_local_models_ollama_url_placeholder)) },
                        singleLine = true
                    )
                }

                // HuggingFace Token
                item {
                    OutlinedTextField(
                        value = uiState.localMlHfToken,
                        onValueChange = { settingsViewModel.setLocalMlHfToken(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.settings_ai_local_models_hf_token_title)) },
                        placeholder = { Text(stringResource(R.string.settings_ai_local_models_hf_token_placeholder)) },
                        singleLine = true
                    )
                }

                // Developer Options for Local Models
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.settings_ai_developer_options_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = stringResource(R.string.settings_ai_developer_options_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Hardware Lock Toggle with Warning
                            var showHwWarning by remember { mutableStateOf(false) }
                            var hwLocked by remember { mutableStateOf(true) }

                            SwitchPreference(
                                title = stringResource(R.string.settings_ai_hardware_lock_title),
                                subtitle = stringResource(R.string.settings_ai_hardware_lock_subtitle),
                                checked = hwLocked,
                                onCheckedChange = { newValue ->
                                    if (!newValue) {
                                        showHwWarning = true
                                    } else {
                                        hwLocked = true
                                    }
                                }
                            )

                            if (showHwWarning) {
                                AlertDialog(
                                    onDismissRequest = { showHwWarning = false },
                                    title = { Text("Warning") },
                                    text = { Text(stringResource(R.string.settings_ai_hardware_lock_warning)) },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                hwLocked = false
                                                showHwWarning = false
                                            }
                                        ) {
                                            Text("Disable Anyway")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showHwWarning = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun ProviderSelectionCard(
    selectedProvider: String,
    onProviderChange: (String) -> Unit
) {
    val providers = listOf(
        "GEMINI" to "Google Gemini",
        "OPENAI" to "OpenAI",
        "ANTHROPIC" to "Anthropic Claude",
        "DEEPSEEK" to "DeepSeek",
        "OLLAMA" to "Ollama (Local)",
        "LOCAL" to "Local Models"
    )

    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = providers.find { it.first == selectedProvider }?.second ?: selectedProvider,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            providers.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onProviderChange(id)
                        expanded = false
                    },
                    leadingIcon = if (selectedProvider == id) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

@Composable
fun ApiKeyInputCard(
    provider: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    var hidden by remember { mutableStateOf(true) }

    OutlinedTextField(
        value = apiKey,
        onValueChange = onApiKeyChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_ai_api_key_title)) },
        placeholder = { Text("Enter your $provider API key") },
        singleLine = true,
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            IconButton(onClick = { hidden = !hidden }) {
                Text(if (hidden) "Show" else "Hide")
            }
        }
    )
}

@Composable
fun ModelSelectionCard(
    provider: String,
    selectedModel: String,
    availableModels: List<com.theveloper.pixelplay.data.ai.AiModel>,
    isLoading: Boolean,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val models = availableModels.map { it.name }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel.ifEmpty { "Select model" },
            onValueChange = {},
            readOnly = true,
            enabled = !isLoading,
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No models available") },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                models.forEach { model ->
                    DropdownMenuItem(
                        text = { Text(model) },
                        onClick = {
                            onModelChange(model)
                            expanded = false
                        },
                        leadingIcon = if (selectedModel == model) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun LocalModelDropdown(
    selectedModelId: String,
    availableModels: List<LocalModelInfo>,
    modelStatuses: Map<String, ModelStatus>,
    onModelSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedModel = availableModels.find { it.id == selectedModelId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedModel?.displayName ?: "Select a local model",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (availableModels.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No models available") },
                    onClick = { expanded = false },
                    enabled = false
                )
            } else {
                availableModels.forEach { model ->
                    val status = modelStatuses[model.id] ?: ModelStatus.NotDownloaded
                    val statusText = when (status) {
                        is ModelStatus.Ready -> stringResource(R.string.settings_ai_model_downloaded)
                        is ModelStatus.Downloading -> stringResource(R.string.settings_ai_model_downloading)
                        is ModelStatus.Error -> status.message
                        else -> ""
                    }

                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(model.displayName)
                                if (statusText.isNotEmpty()) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (status is ModelStatus.Error)
                                            MaterialTheme.colorScheme.error
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            onModelSelect(model.id)
                            expanded = false
                        },
                        leadingIcon = if (selectedModelId == model.id) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onCheckedChange(!checked) } else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@Composable
fun SliderPreference(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = valueFormatter(value),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}