package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                        Icon(androidx.compose.material.icons.Icons.AutoMirrored.Rounded.ArrowBack, null)
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
        }
    }
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
