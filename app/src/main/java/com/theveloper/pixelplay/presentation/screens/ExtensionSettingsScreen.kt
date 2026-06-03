package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.presentation.viewmodel.ExtensionSettingsViewModel
import dev.brahmkshatriya.echo.common.settings.*
import dev.brahmkshatriya.echo.common.Extension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionSettingsScreen(
    extensionId: String,
    onBack: () -> Unit,
    viewModel: ExtensionSettingsViewModel = hiltViewModel()
) {
    val extension by viewModel.extension.collectAsState()
    val settings by viewModel.settingsItems.collectAsState()

    LaunchedEffect(extensionId) {
        viewModel.loadExtension(extensionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    val name = (extension as? Extension<*>)?.metadata?.name ?: "Extension Settings"
                    Text(name) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            items(settings) { setting ->
                // Basic display to ensure compilation
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "Setting: ${setting.javaClass.simpleName}", style = MaterialTheme.typography.labelMedium)
                        // properties will be added once verified
                    }
                }
            }
        }
    }
}
