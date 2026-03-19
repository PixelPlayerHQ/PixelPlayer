package com.theveloper.pixelplay.presentation.qqmusic.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.data.database.QqMusicPlaylistEntity
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QqMusicDashboardScreen(
    onBack: () -> Unit,
    viewModel: QqMusicDashboardViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val loggedOut by viewModel.loggedOut.collectAsStateWithLifecycle()
    val showSyncTypeDialog by viewModel.showSyncTypeDialog.collectAsStateWithLifecycle()
    val isSyncing = syncState is SyncState.Syncing

    LaunchedEffect(loggedOut) {
        if (loggedOut) onBack()
    }

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    val syncMessage = when (val s = syncState) {
        is SyncState.Success -> s.message
        is SyncState.Error -> "Error: ${s.message}"
        is SyncState.Syncing -> "Syncing…"
        else -> null
    }

    LaunchedEffect(syncState) {
        if (syncState is SyncState.Success || syncState is SyncState.Error) {
            kotlinx.coroutines.delay(3000)
            viewModel.clearSyncState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "QQ Music",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.showSyncTypeDialog() },
                        enabled = !isSyncing
                    ) {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = "Sync All Playlists",
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(onClick = {
                        viewModel.logout()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Logout"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sync status banner
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = slideInVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = fadeOut()
            ) {
                syncMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.startsWith("Error"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            }

            // User info header
            viewModel.nickname?.let { nickname ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val avatarUrl = viewModel.avatarUrl
                        if (avatarUrl != null) {
                            SmartImage(
                                model = avatarUrl,
                                contentDescription = "User Avatar",
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.tertiary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = nickname.firstOrNull()?.toString() ?: "Q",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = nickname,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${playlists.size} playlists synced",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playlists header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playlists",
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                if (playlists.isEmpty()) {
                    TextButton(onClick = { viewModel.syncAll() }) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Sync", fontFamily = GoogleSansRounded)
                    }
                }
            }

            // Playlist list
            if (playlists.isEmpty() && !isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "No playlists synced yet",
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap sync to fetch your playlists",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = playlists,
                        key = { it.id }
                    ) { playlist ->
                        QqPlaylistCard(
                            playlist = playlist,
                            onSyncClick = { viewModel.syncPlaylist(playlist.id) },
                            onDeleteClick = { viewModel.deletePlaylist(playlist.id) },
                            cardShape = cardShape,
                            isSyncing = isSyncing
                        )
                    }
                }
            }
        }
    }

    // Sync type selection dialog
    if (showSyncTypeDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { viewModel.hideSyncTypeDialog() }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                shape = cardShape,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Select Playlist Type",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Choose which playlists to sync:",
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(8.dp))

                    // All playlists option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.syncAll(com.theveloper.pixelplay.data.qqmusic.QqMusicRepository.PlaylistSyncType.ALL) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.CloudSync,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "All Playlists",
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Created & Collected",
                                    fontFamily = GoogleSansRounded,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Created playlists option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.syncAll(com.theveloper.pixelplay.data.qqmusic.QqMusicRepository.PlaylistSyncType.CREATED) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Created Playlists",
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Collected playlists option
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.syncAll(com.theveloper.pixelplay.data.qqmusic.QqMusicRepository.PlaylistSyncType.COLLECTED) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Collected Playlists",
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QqPlaylistCard(
    playlist: QqMusicPlaylistEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist cover
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (!playlist.coverUrl.isNullOrBlank()) {
                    SmartImage(
                        model = playlist.coverUrl,
                        contentDescription = playlist.name,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songCount} songs",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sync button
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = "Sync",
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
