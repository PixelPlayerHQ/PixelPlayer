package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.ToggleSegmentButton
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.soundcloud.SoundCloudSearchHit
import com.theveloper.pixelplay.soundcloud.SoundCloudSection
import com.theveloper.pixelplay.soundcloud.SoundCloudViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch

/** Material medium width — foldables / tablets / landscape. */
private const val WIDE_SCREEN_DP = 600

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SoundCloudScreen(
    playerViewModel: PlayerViewModel,
    navController: NavController,
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: SoundCloudViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stablePlayer by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val sections = remember {
        SoundCloudSection.entries.filter { it != SoundCloudSection.SEARCH }
    }
    val selectedIndex = sections.indexOf(uiState.section).let { if (it < 0) -1 else it }
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_DP
    val useTiles = uiState.tilesOverride ?: isWideScreen
    val remoteTrackCount = remember(uiState.results) {
        uiState.results.count { it.kind == SoundCloudSearchHit.Kind.TRACK }
    }
    val canShuffle = !uiState.isLoading && when (uiState.section) {
        SoundCloudSection.DOWNLOADS -> uiState.downloads.isNotEmpty()
        else -> remoteTrackCount > 0
    }
    val gridColumns = when {
        configuration.screenWidthDp >= 840 -> 4
        isWideScreen -> 3
        else -> 2
    }
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = MiniPlayerHeight + 36.dp,
    )
    val currentSongId = stablePlayer.currentSong?.id
    val isPlaying = stablePlayer.isPlaying

    val onHitPlay: (SoundCloudSearchHit) -> Unit = remember(viewModel, playerViewModel, scope) {
        { hit ->
            if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                viewModel.openPlaylist(hit)
            } else {
                scope.launch {
                    viewModel.setBusy(hit.url)
                    try {
                        val song = viewModel.resolveToSong(hit)
                        playerViewModel.playSongs(
                            songsToPlay = listOf(song),
                            startSong = song,
                            queueName = "SoundCloud",
                        )
                        viewModel.setIdle()
                    } catch (t: Throwable) {
                        viewModel.setIdle(error = t.message ?: t::class.java.simpleName)
                    }
                }
            }
        }
    }

    val onDownloadPlay: (Song) -> Unit = remember(uiState.downloads, playerViewModel) {
        { song ->
            playerViewModel.playSongs(
                songsToPlay = uiState.downloads,
                startSong = song,
                queueName = "SoundCloud · Downloads",
            )
        }
    }

    val onShuffle: () -> Unit = {
        scope.launch {
            try {
                if (uiState.section == SoundCloudSection.DOWNLOADS) {
                    val songs = uiState.downloads
                    if (songs.isEmpty()) return@launch
                    playerViewModel.playSongsShuffled(
                        songsToPlay = songs,
                        queueName = "SoundCloud · Downloads",
                        startAtZero = true,
                    )
                } else {
                    val songs = viewModel.resolveTracksForPlayback()
                    if (songs.isEmpty()) return@launch
                    val queueName = uiState.browsingPlaylistTitle
                        ?.let { "SoundCloud · $it" }
                        ?: "SoundCloud · ${uiState.section.name.lowercase().replaceFirstChar { it.titlecase() }}"
                    playerViewModel.playSongsShuffled(
                        songsToPlay = songs,
                        queueName = queueName,
                        startAtZero = true,
                    )
                    viewModel.setIdle()
                }
            } catch (t: Throwable) {
                viewModel.setIdle(error = t.message ?: t::class.java.simpleName)
            }
        }
    }

    // Keep Back inside SoundCloud when browsing a playlist (don't jump to Library).
    BackHandler(enabled = uiState.canNavigateBack) {
        viewModel.navigateBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
    ) {
        TopAppBar(
            title = {
                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = stringResource(R.string.soundcloud_tab_title),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 40.sp,
                    letterSpacing = 1.sp,
                )
            },
            actions = {
                FilledIconButton(
                    modifier = Modifier.padding(end = 14.dp),
                    onClick = { navController.navigateSafely(Screen.Experimental.route) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_settings_24),
                        contentDescription = stringResource(R.string.soundcloud_cd_open_settings),
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
        )

        PrimaryScrollableTabRow(
            selectedTabIndex = selectedIndex.coerceAtLeast(0),
            containerColor = Color.Transparent,
            edgePadding = 12.dp,
            indicator = {},
            divider = {},
        ) {
            sections.forEachIndexed { index, section ->
                TabAnimation(
                    index = index,
                    title = section.name,
                    selectedIndex = if (selectedIndex < 0) -1 else selectedIndex,
                    onClick = { viewModel.selectSection(section) },
                ) {
                    Text(
                        text = stringResource(section.labelRes).uppercase(),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text(stringResource(R.string.soundcloud_query_label)) },
            placeholder = { Text(stringResource(R.string.soundcloud_query_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (uiState.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_clear_search),
                        )
                    }
                }
            },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToggleSegmentButton(
                modifier = Modifier.weight(1f),
                active = useTiles,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeCornerRadius = 32.dp,
                onClick = { viewModel.setUseTiles(true) },
                text = stringResource(R.string.soundcloud_view_tiles),
                imageVector = Icons.Rounded.ViewModule,
            )
            ToggleSegmentButton(
                modifier = Modifier.weight(1f),
                active = !useTiles,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                activeContentColor = MaterialTheme.colorScheme.onPrimary,
                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                activeCornerRadius = 32.dp,
                onClick = { viewModel.setUseTiles(false) },
                text = stringResource(R.string.soundcloud_view_list),
                imageVector = Icons.AutoMirrored.Rounded.ViewList,
            )
            FilledTonalButton(
                onClick = onShuffle,
                enabled = canShuffle,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = stringResource(R.string.soundcloud_cd_shuffle),
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.soundcloud_shuffle))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        uiState.browsingPlaylistTitle?.let { title ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.navigateBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
                Text(
                    text = stringResource(R.string.soundcloud_playlist_browsing, title),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        uiState.errorMessage?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        if (uiState.isLoading && uiState.resolvingKey == null && !uiState.isRefreshing) {
            LoadingIndicator(
                modifier = Modifier
                    .padding(24.dp)
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }

        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullToRefreshState,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            if (useTiles) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (uiState.section == SoundCloudSection.DOWNLOADS) {
                        items(uiState.downloads, key = { it.id }) { song ->
                            SoundCloudSongTile(
                                song = song,
                                isCurrent = currentSongId == song.id,
                                isPlaying = currentSongId == song.id && isPlaying,
                                onPlay = { onDownloadPlay(song) },
                            )
                        }
                    } else {
                        items(uiState.results, key = { it.url + it.kind.name }) { hit ->
                            SoundCloudResultTile(
                                hit = hit,
                                enabled = !uiState.isLoading,
                                isResolving = uiState.resolvingKey == hit.url,
                                onPlay = { onHitPlay(hit) },
                                onDownload = { viewModel.download(hit) },
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (uiState.section == SoundCloudSection.DOWNLOADS) {
                        items(uiState.downloads, key = { it.id }) { song ->
                            EnhancedSongListItem(
                                song = song,
                                isPlaying = currentSongId == song.id && isPlaying,
                                isCurrentSong = currentSongId == song.id,
                                showMoreOptionsButton = false,
                                onMoreOptionsClick = {},
                                onClick = { onDownloadPlay(song) },
                            )
                        }
                    } else {
                        items(uiState.results, key = { it.url + it.kind.name }) { hit ->
                            SoundCloudResultRow(
                                hit = hit,
                                enabled = !uiState.isLoading,
                                isResolving = uiState.resolvingKey == hit.url,
                                onPlay = { onHitPlay(hit) },
                                onDownload = { viewModel.download(hit) },
                            )
                        }
                    }
                }
            }
        }
    }
}

private val SoundCloudSection.labelRes: Int
    get() = when (this) {
        SoundCloudSection.FEED -> R.string.soundcloud_pill_feed
        SoundCloudSection.DISCOVER -> R.string.soundcloud_pill_discover
        SoundCloudSection.SEARCH -> R.string.soundcloud_pill_search
        SoundCloudSection.LIKES -> R.string.soundcloud_pill_likes
        SoundCloudSection.TRACKS -> R.string.soundcloud_pill_tracks
        SoundCloudSection.PLAYLISTS -> R.string.soundcloud_pill_playlists
        SoundCloudSection.DOWNLOADS -> R.string.soundcloud_pill_downloads
    }

@Composable
private fun SoundCloudSongTile(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPlay),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!song.albumArtUriString.isNullOrBlank()) {
                SmartImage(
                    model = song.albumArtUriString,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Cloud,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.Center),
                    )
                }
            }
            if (isCurrent) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.Cloud,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = song.title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = song.displayArtist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoundCloudResultTile(
    hit: SoundCloudSearchHit,
    enabled: Boolean,
    isResolving: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !isResolving, onClick = onPlay),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (!hit.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = hit.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                            Icons.Rounded.PlaylistPlay
                        } else {
                            Icons.Rounded.Cloud
                        },
                        contentDescription = null,
                        modifier = Modifier
                            .padding(28.dp)
                            .align(Alignment.Center),
                    )
                }
            }
            if (isResolving) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(40.dp)
                            .align(Alignment.Center),
                    )
                }
            } else if (hit.kind == SoundCloudSearchHit.Kind.TRACK) {
                IconButton(
                    onClick = onDownload,
                    enabled = enabled,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.soundcloud_download),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = hit.title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = hit.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SoundCloudResultRow(
    hit: SoundCloudSearchHit,
    enabled: Boolean,
    isResolving: Boolean = false,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled && !isResolving, onClick = onPlay),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                if (!hit.thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = hit.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                                Icons.Rounded.PlaylistPlay
                            } else {
                                Icons.Rounded.Cloud
                            },
                            contentDescription = null,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                if (isResolving) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LoadingIndicator(
                            modifier = Modifier
                                .size(28.dp)
                                .align(Alignment.Center),
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                )
                Text(
                    text = hit.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                val meta = when {
                    hit.kind == SoundCloudSearchHit.Kind.PLAYLIST && hit.streamCount > 0 ->
                        stringResource(R.string.soundcloud_playlist_count, hit.streamCount)
                    hit.durationSec > 0 -> formatDuration(hit.durationSec)
                    else -> null
                }
                meta?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (hit.kind == SoundCloudSearchHit.Kind.TRACK && !isResolving) {
                IconButton(onClick = onDownload, enabled = enabled) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = stringResource(R.string.soundcloud_download),
                    )
                }
            }
            if (isResolving) {
                LoadingIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(
                    imageVector = if (hit.kind == SoundCloudSearchHit.Kind.PLAYLIST) {
                        Icons.Rounded.PlaylistPlay
                    } else {
                        Icons.Rounded.PlayArrow
                    },
                    contentDescription = stringResource(R.string.soundcloud_play),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}
