@file:OptIn(ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelStatus
import com.theveloper.pixelplay.data.ai.provider.AiProvider
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
    val currentAiModel by settingsViewModel.currentAiModel.collectAsStateWithLifecycle(initialValue = "")
    val currentApiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle(initialValue = "")
    val currentAiSystemPrompt by settingsViewModel.currentAiSystemPrompt.collectAsStateWithLifecycle(initialValue = "")

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.importLocalModel(it) }
    }

    val isOnlineProvider = uiState.aiProvider != "LOCAL" && uiState.aiProvider != "OLLAMA"
    val isLocalProvider = uiState.aiProvider == "LOCAL"
    val isOllamaProvider = uiState.aiProvider == "OLLAMA"

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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Configure AI-powered features like smart playlists and music recommendations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ===== PROVIDER SELECTION =====
            item {
                Text(
                    text = "AI Provider",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            item {
                ProviderSelectionCard(
                    selectedProvider = uiState.aiProvider,
                    onProviderChange = { settingsViewModel.onAiProviderChange(it) }
                )
            }

            // ===== ONLINE PROVIDER SETTINGS =====
            if (isOnlineProvider) {
                item {
                    if (AiProvider.valueOf(uiState.aiProvider).requiresApiKey) {
                        ApiKeyInputCard(
                            provider = uiState.aiProvider,
                            apiKey = currentApiKey,
                            onApiKeyChange = { settingsViewModel.onAiApiKeyChange(it) }
                        )
                    }
                }

                item {
                    ModelSelectionCard(
                        provider = uiState.aiProvider,
                        selectedModel = currentAiModel,
                        onModelChange = { settingsViewModel.onAiModelChange(it) }
                    )
                }

                item {
                    AdvancedSettingsCard(
                        temperature = (uiState.aiTemperature * 100).toInt(),
                        maxTokens = uiState.aiMaxTokens,
                        onTemperatureChange = { settingsViewModel.onAiTemperatureChange(it) },
                        onMaxTokensChange = { settingsViewModel.onAiMaxTokensChange(it) }
                    )
                }

                item {
                    SystemPromptCard(
                        systemPrompt = currentAiSystemPrompt,
                        onSystemPromptChange = { settingsViewModel.onAiSystemPromptChange(it) },
                        onReset = { settingsViewModel.resetAiSystemPrompt() }
                    )
                }
            }

            // ===== LOCAL/OLLAMA PROVIDER SETTINGS =====
            if (!isOnlineProvider) {
                item {
                    Text(
                        text = if (isOllamaProvider) "Ollama Server Settings" else "Local Model Settings",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                if (isOllamaProvider) {
                    item {
                        ApiKeyInputCard(
                            provider = "OLLAMA",
                            apiKey = currentApiKey,
                            onApiKeyChange = { settingsViewModel.onAiApiKeyChange(it) }
                        )
                    }
                    item {
                        ModelSelectionCard(
                            provider = "OLLAMA",
                            selectedModel = currentAiModel,
                            onModelChange = { settingsViewModel.onAiModelChange(it) }
                        )
                    }
                    item {
                        OllamaConnectionCard(
                            ollamaUrl = uiState.localMlOllamaUrl,
                            onOllamaUrlChange = { settingsViewModel.setLocalMlOllamaUrl(it) }
                        )
                    }
                }
            }

            // ===== LOCAL MODELS (always visible, greyed out if not local provider) =====
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Local Models",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                            .copy(alpha = if (isLocalProvider) 1f else 0.4f)
                    )
                    TextButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = isLocalProvider
                    ) {
                        Icon(
                            Icons.Default.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
            }

            if (!isLocalProvider) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Switch provider to \"Local Model (Device)\" to configure local model settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }

            items(localModels) { model ->
                val status = modelStatuses[model.id] ?: ModelStatus.NotDownloaded
                LocalModelCard(
                    model = model,
                    status = status,
                    isSelected = uiState.localMlActiveModelId == model.id,
                    onDownload = { settingsViewModel.downloadLocalModel(model) },
                    onDelete = { settingsViewModel.deleteLocalModel(model.id) },
                    onSelect = { settingsViewModel.selectLocalModel(model.id) },
                    enabled = isLocalProvider
                )
            }

            if (localModels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (isLocalProvider) 1f else 0.4f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No models available for your device. Your device may not meet the minimum requirements.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                    .copy(alpha = if (isLocalProvider) 1f else 0.4f)
                            )
                        }
                    }
                }
            }

            item {
                SwitchPreference(
                    title = "Use GPU Acceleration",
                    subtitle = "Use hardware GPU for faster local model inference",
                    checked = uiState.localMlUseGpu,
                    onCheckedChange = { settingsViewModel.onLocalMlUseGpuChange(it) },
                    enabled = isLocalProvider
                )
            }

            if (isLocalProvider) {
                item {
                    OllamaConnectionCard(
                        ollamaUrl = uiState.localMlOllamaUrl,
                        onOllamaUrlChange = { settingsViewModel.setLocalMlOllamaUrl(it) }
                    )
                }
            }

            // ===== CONTEXT SETTINGS =====
            item {
                Text(
                    text = "Context Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                ContextSettingsCard(
                    maxSongsForContext = uiState.maxSongsForContext,
                    minValue = uiState.maxSongsForContextMin,
                    maxValue = uiState.maxSongsForContextMax,
                    includeLikedSongs = uiState.includeLikedSongs,
                    includeDailyMixHistory = uiState.includeDailyMixHistory,
                    includeUserHabits = uiState.includeUserHabits,
                    onMaxSongsForContextChange = { settingsViewModel.onMaxSongsForContextChange(it) },
                    onIncludeLikedSongsChange = { settingsViewModel.onIncludeLikedSongsChange(it) },
                    onIncludeDailyMixHistoryChange = { settingsViewModel.onIncludeDailyMixHistoryChange(it) },
                    onIncludeUserHabitsChange = { settingsViewModel.onIncludeUserHabitsChange(it) }
                )
            }

            // ===== TELEMETRY / DATA COLLECTION =====
            item {
                Text(
                    text = "Data Collection & Privacy",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                DataCollectionCard(
                    telemetryIncludeSkipCount = uiState.telemetryIncludeSkipCount,
                    telemetryIncludeCompletionRate = uiState.telemetryIncludeCompletionRate,
                    telemetryIncludeSessionDuration = uiState.telemetryIncludeSessionDuration,
                    telemetryIncludeTimeOfDay = uiState.telemetryIncludeTimeOfDay,
                    telemetryIncludeGenreAffinity = uiState.telemetryIncludeGenreAffinity,
                    telemetryIncludeArtistAffinity = uiState.telemetryIncludeArtistAffinity,
                    telemetryIncludeReplayCount = uiState.telemetryIncludeReplayCount,
                    telemetryIncludeQueuePatterns = uiState.telemetryIncludeQueuePatterns,
                    onSkipCountChange = { settingsViewModel.onTelemetrySkipCountChange(it) },
                    onCompletionRateChange = { settingsViewModel.onTelemetryCompletionRateChange(it) },
                    onSessionDurationChange = { settingsViewModel.onTelemetrySessionDurationChange(it) },
                    onTimeOfDayChange = { settingsViewModel.onTelemetryTimeOfDayChange(it) },
                    onGenreAffinityChange = { settingsViewModel.onTelemetryGenreAffinityChange(it) },
                    onArtistAffinityChange = { settingsViewModel.onTelemetryArtistAffinityChange(it) },
                    onReplayCountChange = { settingsViewModel.onTelemetryReplayCountChange(it) },
                    onQueuePatternsChange = { settingsViewModel.onTelemetryQueuePatternsChange(it) }
                )
            }

            // ===== CACHE SETTINGS =====
            item {
                Text(
                    text = "Cache Settings",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                CacheSettingsCard(
                    aiCacheEnabled = uiState.aiCacheEnabled,
                    aiCacheMaxEntries = uiState.aiCacheMaxEntries,
                    aiCacheTtlHours = uiState.aiCacheTtlHours,
                    aiCacheMaxEntriesMin = uiState.aiCacheMaxEntriesMin,
                    aiCacheMaxEntriesMax = uiState.aiCacheMaxEntriesMax,
                    aiCacheTtlHoursMin = uiState.aiCacheTtlHoursMin,
                    aiCacheTtlHoursMax = uiState.aiCacheTtlHoursMax,
                    onCacheEnabledChange = { settingsViewModel.setAiCacheEnabled(it) },
                    onCacheMaxEntriesChange = { settingsViewModel.setAiCacheMaxEntries(it) },
                    onCacheTtlHoursChange = { settingsViewModel.setAiCacheTtlHours(it) }
                )
            }

            // ===== NOTIFICATION & BEHAVIOR =====
            item {
                Text(
                    text = "Behavior",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                NotificationSettingsCard(
                    aiIncludeContext = uiState.aiIncludeContext,
                    aiEnableStreaming = uiState.aiEnableStreaming,
                    isSafeTokenLimitEnabled = uiState.isSafeTokenLimitEnabled,
                    onIncludeContextChange = { settingsViewModel.setAiIncludeContext(it) },
                    onEnableStreamingChange = { settingsViewModel.setAiEnableStreaming(it) },
                    onSafeTokenLimitChange = { settingsViewModel.setSafeTokenLimitEnabled(it) }
                )
            }

            // ===== USAGE STATISTICS =====
            item {
                Text(
                    text = "Usage Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                UsageStatsCard(
                    totalInputTokens = uiState.aiUsageTotalInputTokens,
                    totalOutputTokens = uiState.aiUsageTotalOutputTokens,
                    totalApiCalls = uiState.aiUsageTotalApiCalls,
                    estimatedCost = uiState.aiUsageEstimatedCost,
                    onClearMetrics = { settingsViewModel.clearAiUsageMetrics() }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

// ===== PROVIDER SELECTION =====

@Composable
fun ProviderSelectionCard(
    selectedProvider: String,
    onProviderChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val providers = listOf(
        "GEMINI" to ("Google Gemini" to "Fast, capable, good for playlists"),
        "DEEPSEEK" to ("DeepSeek" to "Fast, affordable, strong reasoning"),
        "GROQ" to ("Groq" to "Fast inference, open models"),
        "MISTRAL" to ("Mistral" to "High quality, multiple sizes"),
        "NVIDIA" to ("NVIDIA NIM" to "GPU-accelerated inference"),
        "KIMI" to ("Kimi (Moonshot)" to "Long context support"),
        "GLM" to ("Zhipu GLM" to "Chinese + English capable"),
        "OPENAI" to ("OpenAI" to "GPT-4o, broadest ecosystem"),
        "OPENROUTER" to ("OpenRouter" to "Multi-provider gateway"),
        "ANTHROPIC" to ("Anthropic Claude" to "Long context, safe AI"),
        "OLLAMA" to ("Ollama Server" to "Connect to your own server"),
        "LOCAL" to ("Local (Offline)" to "Run models on-device")
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = providers.find { it.first == selectedProvider }?.second?.first ?: selectedProvider,
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
                    providers.forEach { (key, pair) ->
                        val (name, desc) = pair
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(name, fontWeight = FontWeight.Medium)
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProviderChange(key)
                                expanded = false
                            },
                            leadingIcon = if (selectedProvider == key) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

// ===== API KEY =====

@Composable
fun ApiKeyInputCard(
    provider: String,
    apiKey: String,
    onApiKeyChange: (String) -> Unit
) {
    var hidden by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "API Key",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Enter your $provider API key") },
                singleLine = true,
                visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
                trailingIcon = {
                    IconButton(onClick = { hidden = !hidden }) {
                        Icon(
                            if (hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (hidden) "Show" else "Hide"
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Get your API key from the ${provider.lowercase()} provider dashboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== MODEL SELECTION =====

@Composable
fun ModelSelectionCard(
    provider: String,
    selectedModel: String,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val aiProvider = remember(provider) { try { AiProvider.valueOf(provider) } catch (_: Exception) { null } }
    val providerModels = aiProvider?.models ?: emptyList()
    val allModels = remember(providerModels) { if (providerModels.isEmpty()) listOf("default") else providerModels }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Model",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = selectedModel.ifEmpty { "Select model" },
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
                    allModels.forEach { model ->
                        val isCompatible = aiProvider == null || aiProvider.models.isEmpty() || model in aiProvider.models
                        DropdownMenuItem(
                            text = {
                                Text(
                                    model,
                                    color = if (isCompatible) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            },
                            onClick = {
                                onModelChange(model)
                                expanded = false
                            },
                            enabled = isCompatible,
                            leadingIcon = if (selectedModel == model) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

// ===== ADVANCED SETTINGS =====

@Composable
fun AdvancedSettingsCard(
    temperature: Int,
    maxTokens: Int,
    onTemperatureChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }

    CollapsibleCard(
        expanded = showAdvanced,
        onToggle = { showAdvanced = !showAdvanced },
        contentPadding = 16.dp,
        title = { Text("Advanced Settings", style = MaterialTheme.typography.titleSmall) }
    ) {
        Text(text = "Temperature: ${temperature / 100f}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = temperature.toFloat(),
            onValueChange = { onTemperatureChange(it.toInt()) },
            valueRange = 1f..200f,
            steps = 19
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.01", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = "Max Tokens: $maxTokens", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = maxTokens.toFloat(),
            onValueChange = { onMaxTokensChange(it.toInt()) },
            valueRange = 128f..16000f,
            steps = 19
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("128", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("16000", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ===== SYSTEM PROMPT =====

@Composable
fun SystemPromptCard(
    systemPrompt: String,
    onSystemPromptChange: (String) -> Unit,
    onReset: () -> Unit
) {
    var showPrompt by remember { mutableStateOf(false) }

    CollapsibleCard(
        expanded = showPrompt,
        onToggle = { showPrompt = !showPrompt },
        title = { Text("System Prompt", style = MaterialTheme.typography.titleSmall) }
    ) {
        OutlinedTextField(
            value = systemPrompt,
            onValueChange = onSystemPromptChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 200.dp),
            maxLines = 8
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onReset) {
            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset to Default")
        }
    }
}

// ===== LOCAL MODEL CARD =====

@Composable
fun LocalModelCard(
    model: LocalModelInfo,
    status: ModelStatus,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit,
    enabled: Boolean = true
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val containerAlpha = if (enabled) 1f else 0.4f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                !enabled -> MaterialTheme.colorScheme.surface
                isSelected -> MaterialTheme.colorScheme.primaryContainer
                status is ModelStatus.Ready -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = containerAlpha)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = containerAlpha)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(formatSize(model.fileSizeBytes)) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("${model.ramRequiredMb}MB RAM") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Memory,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }

                when (status) {
                    is ModelStatus.NotDownloaded -> {
                        FilledTonalButton(onClick = onDownload, enabled = enabled) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Download")
                        }
                    }
                    is ModelStatus.Downloading -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                progress = { status.progress / 100f },
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 4.dp
                            )
                            Text(
                                text = "${status.progress}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    is ModelStatus.Ready -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                OutlinedButton(onClick = onSelect, enabled = enabled) {
                                    Text("Use")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(onClick = { showDeleteConfirm = true }, enabled = enabled) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = if (enabled) MaterialTheme.colorScheme.error
                                           else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                    is ModelStatus.Error -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "Error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            FilledTonalButton(onClick = onDownload, enabled = enabled) {
                                Text("Retry")
                            }
                        }
                    }
                    is ModelStatus.Importing -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            if (status is ModelStatus.Downloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { status.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    text = "Downloaded ${formatSize(status.downloaded)} / ${formatSize(model.fileSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Model?") },
            text = { Text("Are you sure you want to delete ${model.displayName}? You'll need to download it again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ===== OLLAMA CONNECTION =====

@Composable
fun OllamaConnectionCard(
    ollamaUrl: String,
    onOllamaUrlChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Ollama Server URL",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = ollamaUrl,
                onValueChange = onOllamaUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://your-server:11434") },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter the URL of your Ollama server (e.g., http://192.168.1.100:11434)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ===== CONTEXT SETTINGS =====

@Composable
fun ContextSettingsCard(
    maxSongsForContext: Int,
    minValue: Int,
    maxValue: Int,
    includeLikedSongs: Boolean,
    includeDailyMixHistory: Boolean,
    includeUserHabits: Boolean,
    onMaxSongsForContextChange: (Int) -> Unit,
    onIncludeLikedSongsChange: (Boolean) -> Unit,
    onIncludeDailyMixHistoryChange: (Boolean) -> Unit,
    onIncludeUserHabitsChange: (Boolean) -> Unit
) {
    var showContextSettings by remember { mutableStateOf(false) }
    var contextTextInput by remember(maxSongsForContext) { mutableStateOf(maxSongsForContext.toString()) }

    CollapsibleCard(
        expanded = showContextSettings,
        onToggle = { showContextSettings = !showContextSettings },
        contentPadding = 16.dp,
        title = {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Context Size: $maxSongsForContext songs", style = MaterialTheme.typography.titleSmall)
                Text(text = "How many songs to include as context ($minValue-$maxValue)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) {
                    Slider(
                        value = maxSongsForContext.toFloat(),
                        onValueChange = {
                            onMaxSongsForContextChange(it.toInt())
                            contextTextInput = it.toInt().toString()
                        },
                        valueRange = minValue.toFloat()..maxValue.toFloat(),
                        steps = 48
                    )

                    OutlinedTextField(
                        value = contextTextInput,
                        onValueChange = { value ->
                            contextTextInput = value
                            value.toIntOrNull()?.let { num ->
                                onMaxSongsForContextChange(num.coerceIn(minValue, maxValue))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Exact number of songs") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    ToggleRow(
                        title = "Include liked songs",
                        checked = includeLikedSongs,
                        onCheckedChange = onIncludeLikedSongsChange
                    )

                    ToggleRow(
                        title = "Include listening history",
                        checked = includeDailyMixHistory,
                        onCheckedChange = onIncludeDailyMixHistoryChange
                    )

                    ToggleRow(
                        title = "Include user habits",
                        checked = includeUserHabits,
                        onCheckedChange = onIncludeUserHabitsChange
                    )
    }
}

// ===== DATA COLLECTION & PRIVACY =====

@Composable
fun DataCollectionCard(
    telemetryIncludeSkipCount: Boolean,
    telemetryIncludeCompletionRate: Boolean,
    telemetryIncludeSessionDuration: Boolean,
    telemetryIncludeTimeOfDay: Boolean,
    telemetryIncludeGenreAffinity: Boolean,
    telemetryIncludeArtistAffinity: Boolean,
    telemetryIncludeReplayCount: Boolean,
    telemetryIncludeQueuePatterns: Boolean,
    onSkipCountChange: (Boolean) -> Unit,
    onCompletionRateChange: (Boolean) -> Unit,
    onSessionDurationChange: (Boolean) -> Unit,
    onTimeOfDayChange: (Boolean) -> Unit,
    onGenreAffinityChange: (Boolean) -> Unit,
    onArtistAffinityChange: (Boolean) -> Unit,
    onReplayCountChange: (Boolean) -> Unit,
    onQueuePatternsChange: (Boolean) -> Unit
) {
    var showDataCollection by remember { mutableStateOf(false) }

    CollapsibleCard(
        expanded = showDataCollection,
        onToggle = { showDataCollection = !showDataCollection },
        title = {
            Column(modifier = Modifier.weight(1f)) {
                Text("Data Collection Preferences", style = MaterialTheme.typography.titleSmall)
                Text("Control which listening data is included for AI recommendations. All data stays on-device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    ) {
        ToggleRow("Skip count tracking", telemetryIncludeSkipCount, onSkipCountChange)
        ToggleRow("Completion rate", telemetryIncludeCompletionRate, onCompletionRateChange)
        ToggleRow("Session duration", telemetryIncludeSessionDuration, onSessionDurationChange)
        ToggleRow("Time of day patterns", telemetryIncludeTimeOfDay, onTimeOfDayChange)
        ToggleRow("Genre affinity", telemetryIncludeGenreAffinity, onGenreAffinityChange)
        ToggleRow("Artist affinity", telemetryIncludeArtistAffinity, onArtistAffinityChange)
        ToggleRow("Replay count", telemetryIncludeReplayCount, onReplayCountChange)
        ToggleRow("Queue patterns", telemetryIncludeQueuePatterns, onQueuePatternsChange)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Disabling all options means AI recommendations will not use your listening history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ===== CACHE SETTINGS =====

@Composable
fun CacheSettingsCard(
    aiCacheEnabled: Boolean,
    aiCacheMaxEntries: Int,
    aiCacheTtlHours: Int,
    aiCacheMaxEntriesMin: Int,
    aiCacheMaxEntriesMax: Int,
    aiCacheTtlHoursMin: Int,
    aiCacheTtlHoursMax: Int,
    onCacheEnabledChange: (Boolean) -> Unit,
    onCacheMaxEntriesChange: (Int) -> Unit,
    onCacheTtlHoursChange: (Int) -> Unit
) {
    var showCache by remember { mutableStateOf(false) }

    CollapsibleCard(
        expanded = showCache,
        onToggle = { showCache = !showCache },
        contentPadding = 12.dp,
        title = { Text("Cache Settings", style = MaterialTheme.typography.titleSmall) }
    ) {
        ToggleRow("Enable AI response cache", aiCacheEnabled, onCacheEnabledChange)
        if (aiCacheEnabled) {
            Text(text = "Max cache entries: $aiCacheMaxEntries", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = aiCacheMaxEntries.toFloat(),
                onValueChange = { onCacheMaxEntriesChange(it.toInt()) },
                valueRange = aiCacheMaxEntriesMin.toFloat()..aiCacheMaxEntriesMax.toFloat(),
                steps = 48
            )
            Text(text = "Cache TTL: $aiCacheTtlHours hours", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = aiCacheTtlHours.toFloat(),
                onValueChange = { onCacheTtlHoursChange(it.toInt()) },
                valueRange = aiCacheTtlHoursMin.toFloat()..aiCacheTtlHoursMax.toFloat(),
                steps = 19
            )
        }
    }
}

// ===== NOTIFICATION & BEHAVIOR =====

@Composable
fun NotificationSettingsCard(
    aiIncludeContext: Boolean,
    aiEnableStreaming: Boolean,
    isSafeTokenLimitEnabled: Boolean,
    onIncludeContextChange: (Boolean) -> Unit,
    onEnableStreamingChange: (Boolean) -> Unit,
    onSafeTokenLimitChange: (Boolean) -> Unit
) {
    var showNotifications by remember { mutableStateOf(false) }

    CollapsibleCard(
        expanded = showNotifications,
        onToggle = { showNotifications = !showNotifications },
        title = { Text("Notification & Behavior", style = MaterialTheme.typography.titleSmall) }
    ) {
        ToggleRow("Include context for AI", aiIncludeContext, onIncludeContextChange, subtitle = "Include listening context in AI prompts")
        ToggleRow("Enable streaming", aiEnableStreaming, onEnableStreamingChange, subtitle = "Stream AI responses in real-time")
        ToggleRow("Safe token limit", isSafeTokenLimitEnabled, onSafeTokenLimitChange, subtitle = "Limit token usage to prevent excessive consumption")
    }
}

// ===== USAGE STATISTICS =====

@Composable
fun UsageStatsCard(
    totalInputTokens: Long,
    totalOutputTokens: Long,
    totalApiCalls: Long,
    estimatedCost: String,
    onClearMetrics: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("AI Usage Statistics", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(12.dp))

            StatRow("Total API Calls", totalApiCalls.toString())
            StatRow("Total Input Tokens", totalInputTokens.toString())
            StatRow("Total Output Tokens", totalOutputTokens.toString())
            StatRow("Estimated Cost", "$${estimatedCost}")

            if (totalApiCalls > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onClearMetrics,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear Metrics")
                }
            }
        }
    }
}

// ===== GENERIC COMPONENTS =====

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant
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
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
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

// ===== GENERIC COLLAPSIBLE CARD =====

@Composable
private fun CollapsibleCard(
    expanded: Boolean,
    onToggle: () -> Unit,
    title: @Composable RowScope.() -> Unit,
    contentPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                title()
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = contentPadding)) {
                    content()
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1fGB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1fMB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1fKB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
