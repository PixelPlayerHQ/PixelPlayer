@file:OptIn(ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.local.LocalModelInfo
import com.theveloper.pixelplay.data.ai.local.ModelSource
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
    val currentAiModel by settingsViewModel.currentAiModel.collectAsStateWithLifecycle(initialValue = "")
    val currentApiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle(initialValue = "")

    // Import model launcher
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { settingsViewModel.importLocalModel(it) }
    }

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

            // ===== API KEY =====
            if (uiState.aiProvider != "LOCAL" && uiState.aiProvider != "OLLAMA") {
                item {
                    ApiKeyInputCard(
                        provider = uiState.aiProvider,
                        apiKey = currentApiKey,
                        onApiKeyChange = { settingsViewModel.onAiApiKeyChange(it) }
                    )
                }
            }

            // ===== MODEL SELECTION (Cloud Providers) =====
            if (uiState.aiProvider != "LOCAL") {
                item {
                    Text(
                        text = "Model",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    ModelSelectionCard(
                        provider = uiState.aiProvider,
                        selectedModel = currentAiModel,
                        onModelChange = { settingsViewModel.onAiModelChange(it) }
                    )
                }

                // Temperature & Max Tokens
                item {
                    AdvancedSettingsCard(
                        temperature = uiState.aiTemperature,
                        maxTokens = uiState.aiMaxTokens,
                        onTemperatureChange = { settingsViewModel.onAiTemperatureChange(it) },
                        onMaxTokensChange = { settingsViewModel.onAiMaxTokensChange(it) }
                    )
                }
            }

            // ===== LOCAL MODELS =====
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(
                        onClick = {
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import")
                    }
                }
            }

            item {
                Text(
                    text = "Download models to use AI features offline without internet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Model Cards
            items(localModels) { model ->
                val status = modelStatuses[model.id] ?: ModelStatus.NotDownloaded
                LocalModelCard(
                    model = model,
                    status = status,
                    isSelected = uiState.localMlActiveModelId == model.id,
                    onDownload = { settingsViewModel.downloadLocalModel(model) },
                    onDelete = { settingsViewModel.deleteLocalModel(model.id) },
                    onSelect = { settingsViewModel.selectLocalModel(model.id) }
                )
            }

            if (localModels.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "No models available for your device. Your device may not meet the minimum requirements.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
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
                    contextSize = uiState.maxSongsForContext,
                    includeLikedSongs = uiState.includeLikedSongs,
                    includeHistory = uiState.includeDailyMixHistory,
                    onContextSizeChange = { settingsViewModel.onMaxSongsForContextChange(it) },
                    onIncludeLikedSongsChange = { settingsViewModel.onIncludeLikedSongsChange(it) },
                    onIncludeHistoryChange = { settingsViewModel.onIncludeDailyMixHistoryChange(it) }
                )
            }

            // ===== HARDWARE LOCK =====
            item {
                Text(
                    text = "Hardware",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            item {
                SwitchPreference(
                    title = stringResource(R.string.settings_ai_hardware_lock_title),
                    subtitle = stringResource(R.string.settings_ai_hardware_lock_subtitle),
                    checked = uiState.localMlUseGpu,
                    onCheckedChange = { settingsViewModel.onLocalMlUseGpuChange(it) }
                )
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
    var expanded by remember { mutableStateOf(false) }

    val providers = listOf(
        "GEMINI" to "Google Gemini" to "Fast, capable, good for playlists",
        "OPENAI" to "OpenAI GPT" to "GPT-4o, high quality",
        "ANTHROPIC" to "Anthropic Claude" to "Long context, good reasoning",
        "DEEPSEEK" to "DeepSeek" to "Fast, affordable",
        "OLLAMA" to "Ollama Server" to "Connect to your Ollama server",
        "LOCAL" to "Local (Offline)" to "Use downloaded models"
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
                    value = providers.find { it.first.first == selectedProvider }?.first?.second ?: selectedProvider,
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
                    providers.forEach { (provider, description) ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(provider.first, fontWeight = FontWeight.Medium)
                                    Text(
                                        description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                onProviderChange(provider.first)
                                expanded = false
                            },
                            leadingIcon = if (selectedProvider == provider.first) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null
                        )
                    }
                }
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
                text = "Get your API key from ${provider.lowercase()}.com/api",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ModelSelectionCard(
    provider: String,
    selectedModel: String,
    onModelChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val models = when (provider) {
        "GEMINI" -> listOf("gemini-2.0-flash-exp", "gemini-2.5-flash", "gemini-1.5-pro")
        "OPENAI" -> listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo")
        "ANTHROPIC" -> listOf("claude-3-5-sonnet-20241022", "claude-3-opus-20240229")
        "DEEPSEEK" -> listOf("deepseek-chat", "deepseek-coder")
        else -> listOf("default")
    }

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
}

@Composable
fun AdvancedSettingsCard(
    temperature: Int,
    maxTokens: Int,
    onTemperatureChange: (Int) -> Unit,
    onMaxTokensChange: (Int) -> Unit
) {
    var showAdvanced by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showAdvanced = !showAdvanced },
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
                Text("Advanced Settings", style = MaterialTheme.typography.titleSmall)
                Icon(
                    if (showAdvanced) Icons.Default.expand_less else Icons.Default.expand_more,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Temperature
                    Text(
                        text = "Temperature: ${temperature / 100f}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = temperature.toFloat(),
                        onValueChange = { onTemperatureChange(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 9
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Max Tokens
                    Text(
                        text = "Max Tokens: $maxTokens",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = maxTokens.toFloat(),
                        onValueChange = { onMaxTokensChange(it.toInt()) },
                        valueRange = 256f..4096f,
                        steps = 14
                    )
                }
            }
        }
    }
}

@Composable
fun LocalModelCard(
    model: LocalModelInfo,
    status: ModelStatus,
    isSelected: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onSelect: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
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
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

                // Action buttons based on status
                when (status) {
                    is ModelStatus.NotDownloaded -> {
                        FilledTonalButton(onClick = onDownload) {
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
                                OutlinedButton(onClick = onSelect) {
                                    Text("Use")
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            IconButton(onClick = { showDeleteConfirm = true }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
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
                            FilledTonalButton(onClick = onDownload) {
                                Text("Retry")
                            }
                        }
                    }
                    is ModelStatus.Importing -> {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }
            }

            // Progress bar for downloading
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

    // Delete confirmation dialog
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

@Composable
fun ContextSettingsCard(
    contextSize: Int,
    includeLikedSongs: Boolean,
    includeHistory: Boolean,
    onContextSizeChange: (Int) -> Unit,
    onIncludeLikedSongsChange: (Boolean) -> Unit,
    onIncludeHistoryChange: (Boolean) -> Unit
) {
    var showContextSettings by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showContextSettings = !showContextSettings },
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
                Column {
                    Text(
                        text = "Context Size: $contextSize songs",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "How many songs to include as context for AI recommendations",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    if (showContextSettings) Icons.Default.expand_less else Icons.Default.expand_more,
                    contentDescription = null
                )
            }

            AnimatedVisibility(visible = showContextSettings) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Context size slider
                    Slider(
                        value = contextSize.toFloat(),
                        onValueChange = { onContextSizeChange(it.toInt()) },
                        valueRange = 10f..100f,
                        steps = 8
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Toggle options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include liked songs")
                        Switch(
                            checked = includeLikedSongs,
                            onCheckedChange = onIncludeLikedSongsChange
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Include listening history")
                        Switch(
                            checked = includeHistory,
                            onCheckedChange = onIncludeHistoryChange
                        )
                    }
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
            containerColor = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
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

private fun formatSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1fGB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1fMB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1fKB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}