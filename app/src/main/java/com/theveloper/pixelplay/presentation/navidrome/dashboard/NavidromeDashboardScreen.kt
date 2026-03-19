package com.theveloper.pixelplay.presentation.navidrome.dashboard

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
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.NavidromePlaylistEntity
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NavidromeDashboardScreen(
    viewModel: NavidromeDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Subsonic",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBack,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {}
            )
        }
    ) { paddingValues ->
        DashboardContent(
            playlists = playlists,
            isSyncing = isSyncing,
            syncMessage = syncMessage,
            username = viewModel.username,
            onSyncAll = { viewModel.syncAllPlaylistsAndSongs() },
            onSyncPlaylist = { viewModel.syncPlaylistSongs(it) },
            onDeletePlaylist = { viewModel.deletePlaylist(it) },
            onLoadPlaylistSongs = { viewModel.loadPlaylistSongs(it) },
            onLogout = {
                viewModel.logout()
                onBack()
            },
            cardShape = cardShape,
            paddingValues = paddingValues
        )
    }
}

@Composable
private fun DashboardContent(
    playlists: List<NavidromePlaylistEntity>,
    isSyncing: Boolean,
    syncMessage: String?,
    username: String?,
    onSyncAll: () -> Unit,
    onSyncPlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onLoadPlaylistSongs: (String) -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    paddingValues: PaddingValues
) {
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
                        containerColor = if (message.contains("failed"))
                            MaterialTheme.colorScheme.errorContainer
                        else
                            MaterialTheme.colorScheme.primaryContainer
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
                                color = MaterialTheme.colorScheme.primary
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
        username?.let { name ->
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
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_navidrome),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Unspecified
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = name,
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

        SubsonicMenuCard(
            isSyncing = isSyncing,
            onSyncAll = onSyncAll,
            onLogout = onLogout,
            cardShape = cardShape
        )

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
                TextButton(onClick = onSyncAll) {
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.CloudQueue,
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
                    PlaylistCard(
                        playlist = playlist,
                        onSyncClick = { onSyncPlaylist(playlist.id) },
                        onDeleteClick = { onDeletePlaylist(playlist.id) },
                        onClick = { onLoadPlaylistSongs(playlist.id) },
                        cardShape = cardShape,
                        isSyncing = isSyncing
                    )
                }
            }
        }
    }
}

@Composable
private fun SubsonicMenuCard(
    isSyncing: Boolean,
    onSyncAll: () -> Unit,
    onLogout: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick actions",
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manage Navidrome, Airsonic and other Subsonic-compatible servers.",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = onSyncAll,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Syncing", fontFamily = GoogleSansRounded)
                    } else {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sync library", fontFamily = GoogleSansRounded)
                    }
                }

                FilledTonalButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect", fontFamily = GoogleSansRounded)
                }
            }
        }
    }
}

@Composable
private fun SongCard(
    song: Song,
    cardShape: AbsoluteSmoothCornerShape
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
            // Album art
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (song.albumArtUriString != null) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        contentScale = ContentScale.Crop,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: NavidromePlaylistEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.coverArtId != null) {
                    SmartImage(
                        model = "navidrome_cover://${playlist.coverArtId}",
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
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
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

            FilledTonalIconButton(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = "Sync",
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(8.dp))

            FilledTonalIconButton(
                onClick = onDeleteClick,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Remove",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
