package com.theveloper.pixelplay.presentation.download

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.data.download.InstalledExtension
import com.theveloper.pixelplay.data.download.RepoExtension
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    viewModel: ExtensionsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Refresh the (cached) registry listing every time the repo tab opens.
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.browseRepo()
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::install)
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.extensions_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { filePicker.launch(arrayOf("*/*")) },
                        enabled = !ui.installing,
                    ) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.extensions_install), fontFamily = GoogleSansRounded)
                    }
                },
            )
        },
        snackbarHost = {
            // Lift above the main screen's bottom nav + mini player overlay.
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = MiniPlayerHeight + 24.dp))
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Clear the main screen's bottom nav + mini player overlay.
                .padding(bottom = MiniPlayerHeight + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            if (ui.installing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(5.dp),
                containerColor = Color.Transparent,
                divider = {},
                indicator = {},
            ) {
                TabAnimation(
                    index = 0,
                    title = stringResource(R.string.extensions_tab_installed),
                    selectedIndex = selectedTab,
                    onClick = { selectedTab = 0 },
                ) {
                    Text(
                        text = stringResource(R.string.extensions_tab_installed),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontFamily = GoogleSansRounded,
                    )
                }
                TabAnimation(
                    index = 1,
                    title = stringResource(R.string.extensions_tab_repo),
                    selectedIndex = selectedTab,
                    onClick = { selectedTab = 1 },
                ) {
                    Text(
                        text = stringResource(R.string.extensions_tab_repo),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        fontFamily = GoogleSansRounded,
                    )
                }
            }
            if (selectedTab == 1) {
                val repoError = ui.repoError
                val registryError = ui.registryError
                when {
                    ui.registryEditing || ui.registryUrl == null -> RegistryUrlInput(
                        initialUrl = ui.registryUrl.orEmpty(),
                        error = registryError,
                        enabled = !ui.repoLoading,
                        onSubmit = viewModel::setRegistryUrl,
                        onCancel = if (ui.registryUrl != null) ({ viewModel.consumeRegistryEdit() }) else null,
                        modifier = Modifier.weight(1f),
                    )
                    ui.repoLoading -> TabPlaceholder(
                        stringResource(R.string.extensions_repo_loading),
                        modifier = Modifier.weight(1f),
                    )
                    repoError != null -> TabPlaceholder(
                        repoError,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.error,
                    )
                    else -> LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ui.repoBrowse, key = { it.id }) { repoExtension ->
                            RepoExtensionRow(
                                extension = repoExtension,
                                installedVersion = ui.extensions.firstOrNull { it.id == repoExtension.id }?.version,
                                installing = ui.installing,
                                onInstall = { viewModel.installFromRepo(repoExtension.id) },
                            )
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = ui.registryUrl.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                IconButton(onClick = viewModel::editRegistry) {
                                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.extensions_registry_edit))
                                }
                                IconButton(onClick = viewModel::clearRegistry) {
                                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.extensions_registry_clear))
                                }
                            }
                        }
                    }
                }
            } else if (ui.extensions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.extensions_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.extensions, key = { it.id }) { extension ->
                        ExtensionRow(
                            extension = extension,
                            onToggle = { viewModel.toggle(extension.id, it) },
                            onRemove = { viewModel.remove(extension.id) },
                        )
                    }
                    item {
                        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.extensions_disclaimer),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegistryUrlInput(
    initialUrl: String,
    error: String?,
    enabled: Boolean,
    onSubmit: (String) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var url by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.extensions_registry_hint),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
        )
        SmoothOutlinedField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.extensions_registry_label),
            placeholder = stringResource(R.string.extensions_registry_placeholder),
            supportingText = error,
            isError = error != null,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit(url) }),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = enabled) {
                    Text(stringResource(android.R.string.cancel), fontFamily = GoogleSansRounded)
                }
            }
            TextButton(onClick = { onSubmit(url) }, enabled = enabled && url.isNotBlank()) {
                Text(stringResource(R.string.extensions_registry_load), fontFamily = GoogleSansRounded)
            }
        }
        if (initialUrl.isEmpty()) RegistryGuide()
    }
}

/** Registry category ids are English keys; show them in the app language. */
@Composable
private fun categoryLabel(category: String): String? = when (category.lowercase()) {
    "" -> null
    "metadata" -> stringResource(R.string.extensions_type_metadata)
    "download" -> stringResource(R.string.extensions_type_download)
    "lyrics" -> stringResource(R.string.extensions_type_lyrics)
    "utility" -> stringResource(R.string.extensions_type_utility)
    "integration" -> stringResource(R.string.extensions_type_integration)
    else -> category
}

/** How to find a registry URL; Go (resolveRegistryURL) accepts a GitHub repo or a direct registry.json link. */
@Composable
private fun RegistryGuide() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.extensions_registry_guide_title),
                style = MaterialTheme.typography.titleSmall,
                fontFamily = GoogleSansRounded,
            )
            listOf(
                R.string.extensions_registry_guide_1,
                R.string.extensions_registry_guide_2,
                R.string.extensions_registry_guide_3,
                R.string.extensions_registry_guide_4,
            ).forEachIndexed { i, res ->
                Text(
                    text = "${i + 1}. ${stringResource(res)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.extensions_registry_compatible),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
            )
            val uriHandler = LocalUriHandler.current
            TextButton(onClick = { runCatching { uriHandler.openUri(COMPATIBLE_EXTENSIONS_URL) } }) {
                Text(COMPATIBLE_EXTENSIONS_URL, fontFamily = GoogleSansRounded)
            }
        }
    }
}

private const val COMPATIBLE_EXTENSIONS_URL = "https://spotiflac.com/extension"

@Composable
private fun TabPlaceholder(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            color = color,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RepoExtensionRow(
    extension: RepoExtension,
    installedVersion: String?,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.displayName.ifEmpty { extension.name.ifEmpty { extension.id } },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        extension.version.takeIf { it.isNotEmpty() }?.let { "v$it" },
                        categoryLabel(extension.category),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!extension.description.isNullOrEmpty()) {
                    Text(
                        text = extension.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (installedVersion != null) {
                Text(
                    text = if (installedVersion.isEmpty()) {
                        stringResource(R.string.extensions_install_done)
                    } else {
                        "v$installedVersion"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onInstall, enabled = !installing) {
                    Text(stringResource(R.string.download_cd_download), fontFamily = GoogleSansRounded)
                }
            }
        }
    }
}

@Composable
private fun ExtensionRow(
    extension: InstalledExtension,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val statusLabel = stringResource(
                    when {
                        extension.error != null -> R.string.extensions_status_error
                        extension.enabled -> R.string.extensions_status_enabled
                        else -> R.string.extensions_status_disabled
                    },
                )
                // Same "v<version> · <category>" line and description as the repository rows.
                val category = extension.types.mapNotNull { type ->
                    when (type) {
                        "metadata_provider" -> R.string.extensions_type_metadata
                        "download_provider" -> R.string.extensions_type_download
                        "lyrics_provider" -> R.string.extensions_type_lyrics
                        else -> null
                    }
                }.map { stringResource(it) }.joinToString(", ")
                Text(
                    text = listOfNotNull(
                        extension.version.takeIf { it.isNotEmpty() }?.let { "v$it" },
                        category.takeIf { it.isNotEmpty() },
                        statusLabel,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!extension.description.isNullOrEmpty()) {
                    Text(
                        text = extension.description,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                extension.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Switch(checked = extension.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.extensions_cd_remove),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
