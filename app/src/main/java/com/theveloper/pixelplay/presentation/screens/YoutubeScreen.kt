@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DockedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.MultiSelectionBottomSheet
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.components.subcomps.SelectionActionRow
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.ChangelogModalSheet
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.youtube.auth.YoutubeLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.youtube.YoutubeClient
import com.theveloper.pixelplay.youtube.YoutubeHit
import com.theveloper.pixelplay.youtube.YoutubePlaybackQueue
import com.theveloper.pixelplay.youtube.YoutubeSearchFilter
import com.theveloper.pixelplay.youtube.YoutubeSection
import com.theveloper.pixelplay.youtube.YoutubeViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val WIDE_SCREEN_DP = 600
/** How close to the end of the list a row must be to pull the next page. */
private const val LOAD_MORE_THRESHOLD = 12

@Composable
fun YoutubeScreen(
    section: YoutubeSection,
    playerViewModel: PlayerViewModel,
    navController: NavController,
    viewModel: YoutubeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val liked by viewModel.likedTracks.collectAsStateWithLifecycle()
    val recent by viewModel.recentTracks.collectAsStateWithLifecycle()
    val likedIds by viewModel.likedSongIds.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var showChangelog by remember { mutableStateOf(false) }
    var showBeta by remember { mutableStateOf(false) }
    var selecting by remember { mutableStateOf(false) }
    var selectedUrls by remember { mutableStateOf(listOf<String>()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var showMultiSelectionSheet by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val libraryPlaylists by viewModel.libraryPlaylists.collectAsStateWithLifecycle()
    val stablePlayer by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val configuration = LocalConfiguration.current
    val isWideScreen = configuration.screenWidthDp >= WIDE_SCREEN_DP
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
    val bottomBarHeight = resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode)
    val contentPadding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = bottomBarHeight + MiniPlayerHeight + 30.dp,
    )
    val currentSongId = stablePlayer.currentSong?.id
    val isPlaying = stablePlayer.isPlaying
    val browseSection = when (section) {
        YoutubeSection.SEARCH -> YoutubeSection.SEARCH
        YoutubeSection.LIKED -> YoutubeSection.LIKED
        YoutubeSection.PLAYLISTS -> YoutubeSection.PLAYLISTS
        YoutubeSection.RADIO -> YoutubeSection.RADIO
        else -> YoutubeSection.HOME
    }
    val title = when (browseSection) {
        YoutubeSection.HOME -> stringResource(R.string.youtube_home_title)
        YoutubeSection.SEARCH -> stringResource(R.string.youtube_search_title)
        YoutubeSection.LIKED -> stringResource(R.string.youtube_liked_title)
        YoutubeSection.PLAYLISTS -> stringResource(R.string.youtube_playlists_title)
        YoutubeSection.RADIO -> stringResource(R.string.youtube_radio_title)
    }
    val rows = when (browseSection) {
        // Signed-in likes stay in YouTube Music order (newest first). Local likes
        // only appear when there is no account list, so they cannot bury new likes.
        YoutubeSection.LIKED -> if (account.isSignedIn && uiState.results.isNotEmpty()) {
            uiState.results
        } else {
            uiState.results.ifEmpty { liked }
        }
        else -> uiState.results
    }
    // Home shelves and recent plays are not in results, so selection actions
    // have to resolve those tracks or the options button has nothing to open.
    val selectableHits = if (browseSection == YoutubeSection.HOME && uiState.collectionTitle == null) {
        (recent + uiState.shelves.flatMap { it.items })
            .filter { it.isSelectableTrack() }
            .distinctBy { it.url }
    } else {
        rows.filter { it.isSelectableTrack() }
    }
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()

    LaunchedEffect(section) {
        viewModel.openSection(section)
    }
    LaunchedEffect(section, playerViewModel) {
        if (section == YoutubeSection.SEARCH) {
            playerViewModel.searchNavDoubleTapEvents.collect {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    }
    LaunchedEffect(stablePlayer.currentSong?.id) {
        stablePlayer.currentSong?.let(viewModel::rememberPlayed)
    }
    // Resolve the streams the user is most likely to tap first, so playback is instant.
    val warmUpCandidates = rows.ifEmpty { recent }.ifEmpty { uiState.shelves.flatMap { it.items } }
    val warmUpKey = remember(warmUpCandidates) {
        warmUpCandidates.asSequence().map { it.url }.take(4).joinToString()
    }
    LaunchedEffect(warmUpKey) {
        if (warmUpCandidates.isNotEmpty()) viewModel.warmUpTracks(warmUpCandidates)
    }
    LaunchedEffect(browseSection, listState) {
        if (browseSection != YoutubeSection.LIKED) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible to info.totalItemsCount
        }.distinctUntilChanged().collect { (lastVisible, total) ->
            if (total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD) {
                viewModel.loadMoreLiked()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.notices.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (uiState.collectionTitle != null && !selecting) {
        BackHandler { viewModel.closeCollection() }
    }
    if (selecting) {
        BackHandler {
            selecting = false
            selectedUrls = emptyList()
            showMultiSelectionSheet = false
        }
    }

    val playGate = remember { AtomicBoolean(false) }
    val playWhenReady = stablePlayer.playWhenReady
    val playHit: (YoutubeHit, Boolean) -> Unit = { hit, asRadio ->
        val thisId = YoutubeClient.songIdForUrl(hit.url)
        val isThisSong = currentSongId == thisId
        when {
            hit.kind == YoutubeHit.Kind.COLLECTION && !asRadio -> viewModel.openCollection(hit)
            // Already playing this track: pause. Already paused: resume. Still preparing: ignore.
            isThisSong && isPlaying -> playerViewModel.playPause()
            isThisSong && !playWhenReady -> playerViewModel.playPause()
            isThisSong -> Unit
            !playGate.compareAndSet(false, true) -> Unit
            else -> {
            playerViewModel.showPreparingSong(placeholderSong(hit))
            scope.launch {
                try {
                    if (asRadio) {
                        runCatching { viewModel.radioTracks(hit) }
                            .onSuccess { queue ->
                                val playable = queue.ifEmpty { listOf(hit) }
                                val queued = viewModel.songsFromContext(playable.first(), playable)
                                val start = viewModel.resolvePlayable(playable.first())
                                playerViewModel.playSongs(
                                    songsToPlay = listOf(start) + queued.drop(1),
                                    startSong = start,
                                    queueName = "YouTube Radio",
                                )
                                viewModel.warmUpTracks(playable)
                            }
                            .onFailure { error ->
                                viewModel.reportError(error.message ?: "Couldn't start radio")
                            }
                    } else {
                        runCatching {
                            val inPlaylist = uiState.collectionTitle != null
                            val contextHits = if (inPlaylist) {
                                YoutubePlaybackQueue.playlistTracks(hit, rows)
                            } else {
                                YoutubePlaybackQueue.remainingTracks(hit, rows)
                            }
                            val queued = contextHits.map(viewModel::listSong)
                            val start = viewModel.resolvePlayable(hit)
                            val songs = queued.map { song -> if (song.id == start.id) start else song }
                            Triple(start, songs, inPlaylist)
                        }
                            .onSuccess { (start, songs, inPlaylist) ->
                                playerViewModel.playSongs(
                                    songsToPlay = songs,
                                    startSong = start,
                                    queueName = uiState.collectionTitle ?: "YouTube Music",
                                    keepPlayedSongs = inPlaylist,
                                )
                                viewModel.warmUpTracks(
                                    YoutubePlaybackQueue.remainingTracks(hit, rows),
                                )
                            }
                            .onFailure { error ->
                                viewModel.reportError(error.message ?: "Couldn't play this track")
                            }
                    }
                } finally {
                    playGate.set(false)
                }
            }
            }
        }
    }

    val onSongClick: (YoutubeHit, Boolean) -> Unit = { hit, asRadio ->
        if (selecting) {
            if (hit.isSelectableTrack()) {
                selectedUrls = selectedUrls.toggleUrl(hit.url)
                if (selectedUrls.isEmpty()) {
                    selecting = false
                    showMultiSelectionSheet = false
                }
            }
        } else {
            playHit(hit, asRadio)
        }
    }
    val onSongLongPress: (YoutubeHit) -> Unit = { hit ->
        if (hit.isSelectableTrack()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            selecting = true
            selectedUrls = selectedUrls.toggleUrl(hit.url)
            if (selectedUrls.isEmpty()) {
                selecting = false
                showMultiSelectionSheet = false
            }
            viewModel.ensureLibraryPlaylists()
        }
    }
    val onSongActions: (YoutubeHit) -> Unit = { hit ->
        if (hit.isSelectableTrack()) {
            selecting = true
            selectedUrls = listOf(hit.url)
            showMultiSelectionSheet = true
            viewModel.ensureLibraryPlaylists()
        }
    }
    val selectedHits = remember(selectedUrls, selectableHits) {
        val byUrl = selectableHits.associateBy { it.url }
        selectedUrls.mapNotNull { byUrl[it] }
    }
    val selectedSongs = remember(selectedHits) { selectedHits.map(viewModel::listSong) }
    val canSelectTracks = selectableHits.isNotEmpty()
    fun clearYoutubeSelection() {
        selecting = false
        selectedUrls = emptyList()
        showMultiSelectionSheet = false
    }

    val headerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(headerColor),
        containerColor = headerColor,
        topBar = {
            YoutubeCompactHeader(
                title = uiState.collectionTitle ?: title,
                showTitle = browseSection != YoutubeSection.HOME || uiState.collectionTitle != null,
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                showSearch = browseSection == YoutubeSection.SEARCH && uiState.collectionTitle == null,
                searchFocusRequester = searchFocusRequester,
                searchFilter = uiState.filter,
                onFilter = viewModel::setFilter,
                onBack = when {
                    selecting -> {
                        { clearYoutubeSelection() }
                    }
                    uiState.collectionTitle != null -> viewModel::closeCollection
                    else -> null
                },
                signedIn = account.isSignedIn,
                onBeta = { showBeta = true },
                onChangelog = { showChangelog = true },
                onAccount = { navController.navigateSafely(Screen.YoutubeSettings.route) },
                onSettings = { navController.navigateSafely(Screen.Settings.route) },
                onCreatePlaylist = if (
                    browseSection == YoutubeSection.PLAYLISTS &&
                    uiState.collectionTitle == null &&
                    account.isSignedIn
                ) {
                    {
                        selectedUrls = emptyList()
                        showCreatePlaylist = true
                    }
                } else {
                    null
                },
                onSelectTracks = if (canSelectTracks && !selecting) {
                    { selecting = true }
                } else {
                    null
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .background(headerColor),
        ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 34.dp,
                cornerRadiusTR = 34.dp,
                cornerRadiusBL = 0.dp,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentTL = 60,
                smoothnessAsPercentTR = 60,
                smoothnessAsPercentBL = 60,
                smoothnessAsPercentBR = 60,
            ),
        ) {
        Column(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = selecting,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) {
            SelectionActionRow(
                selectedCount = selectedUrls.size,
                onSelectAll = {
                    selectedUrls = selectableHits.map { it.url }
                },
                onDeselect = { clearYoutubeSelection() },
                onOptionsClick = {
                    if (selectedHits.isNotEmpty()) {
                        showMultiSelectionSheet = true
                        viewModel.ensureLibraryPlaylists()
                    }
                },
                modifier = Modifier
                    .padding(top = 6.dp, start = 10.dp, end = 10.dp, bottom = 4.dp)
                    .heightIn(min = 56.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = viewModel::refresh,
            state = pullState,
            modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullState,
                            isRefreshing = uiState.isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter),
                            containerColor = androidx.compose.ui.graphics.Color.Transparent,
                            elevation = 0.dp,
                        )
                    },
        ) {
            Column(Modifier.fillMaxSize()) {
                uiState.error?.takeUnless { browseSection == YoutubeSection.LIKED && !account.isSignedIn }?.let { message ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontFamily = GoogleSansRounded,
                        )
                    }
                }

                when {
                    browseSection == YoutubeSection.HOME && uiState.collectionTitle == null -> {
                        if (uiState.isLoading && uiState.shelves.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        } else if (uiState.shelves.isEmpty() && recent.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(contentPadding),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyYoutubeState(message = stringResource(R.string.youtube_home_empty))
                                }
                                FeedListeningStatsButton(
                                    onClick = { navController.navigateSafely(Screen.Stats.route) },
                                )
                            }
                        } else {
                            val mixHits = uiState.shelves
                                .firstOrNull { it.id == "yours" }
                                ?.items
                                ?.filter { it.kind == YoutubeHit.Kind.TRACK }
                                .orEmpty()
                                .ifEmpty {
                                    uiState.shelves.flatMap { it.items }.filter { it.kind == YoutubeHit.Kind.TRACK }
                                }
                            val mixSongs = remember(mixHits) {
                                mixHits.take(8).map { placeholderSong(it) }.toImmutableList()
                            }
                            LazyColumn(contentPadding = contentPadding) {
                                if (mixHits.isNotEmpty()) {
                                item(key = "your_mix") {
                                    YourMixHeader(
                                        song = stringResource(R.string.youtube_todays_mix),
                                        onPlayShuffled = {
                                            val seed = mixHits.shuffled()
                                            if (seed.isNotEmpty()) onSongClick(seed.first(), false)
                                        },
                                    )
                                }
                                if (mixSongs.isNotEmpty()) {
                                    item(key = "collage") {
                                        AlbumArtCollage(
                                            songs = mixSongs,
                                            modifier = Modifier.fillMaxWidth(),
                                            height = 280.dp,
                                            padding = 8.dp,
                                            onSongClick = { song ->
                                                mixHits.firstOrNull { YoutubeClient.songIdForUrl(it.url) == song.id }
                                                    ?.let { onSongClick(it, false) }
                                            },
                                        )
                                    }
                                }
                                }
                                if (recent.isNotEmpty()) {
                                    item(key = "recent") {
                                        Text(
                                            text = stringResource(R.string.youtube_recent_title),
                                            modifier = Modifier.padding(bottom = 8.dp, top = 12.dp),
                                            style = MaterialTheme.typography.titleLarge,
                                            fontFamily = GoogleSansRounded,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            items(recent, key = { "recent-${it.url}" }) { hit ->
                                                YoutubeTile(
                                                    hit = hit,
                                                    modifier = Modifier.width(168.dp),
                                                    isCurrent = currentSongId == YoutubeClient.songIdForUrl(hit.url),
                                                    isPlaying = isPlaying,
                                                    selecting = selecting,
                                                    selected = hit.url in selectedUrls,
                                                    onClick = { onSongClick(hit, false) },
                                                    onLongPress = { onSongLongPress(hit) },
                                                )
                                            }
                                        }
                                    }
                                }
                                items(uiState.shelves, key = { it.id }) { shelf ->
                                    Text(
                                        text = shelf.title,
                                        modifier = Modifier.padding(bottom = 8.dp, top = 12.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        items(shelf.items, key = { it.url }) { hit ->
                            YoutubeTile(
                                hit = hit,
                                modifier = Modifier.width(168.dp),
                                                isCurrent = currentSongId == YoutubeClient.songIdForUrl(hit.url),
                                                isPlaying = isPlaying,
                                                subtitle = if (shelf.startsRadio) {
                                                    stringResource(R.string.youtube_radio_from, hit.artist)
                                                } else {
                                                    hit.artist
                                                },
                                                selecting = selecting,
                                                selected = hit.url in selectedUrls,
                                                onClick = { onSongClick(hit, shelf.startsRadio) },
                                                onLongPress = { onSongLongPress(hit) },
                                            )
                                        }
                                    }
                                }
                                item(key = "listening_stats") {
                                    FeedListeningStatsButton(
                                        onClick = { navController.navigateSafely(Screen.Stats.route) },
                                    )
                                }
                            }
                        }
                    }
                    uiState.isLoading && rows.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    rows.isEmpty() -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            EmptyYoutubeState(
                                message = when (browseSection) {
                                    YoutubeSection.LIKED -> stringResource(
                                        if (account.isSignedIn) R.string.youtube_liked_empty else R.string.youtube_liked_signed_out,
                                    )
                                    YoutubeSection.PLAYLISTS -> stringResource(R.string.youtube_playlists_empty)
                                    YoutubeSection.RADIO -> stringResource(R.string.youtube_radio_empty)
                                    YoutubeSection.SEARCH -> stringResource(R.string.youtube_search_empty)
                                    YoutubeSection.HOME -> stringResource(
                                        if (uiState.collectionTitle != null) R.string.youtube_playlist_empty else R.string.youtube_home_empty,
                                    )
                                }
                            )
                            if (browseSection == YoutubeSection.LIKED && !account.isSignedIn) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        context.startActivity(Intent(context, YoutubeLoginActivity::class.java))
                                    },
                                ) {
                                    Text(stringResource(R.string.youtube_sign_in), fontFamily = GoogleSansRounded)
                                }
                            }
                        }
                    }
                    isWideScreen &&
                        uiState.collectionTitle == null &&
                        (browseSection == YoutubeSection.HOME ||
                            rows.any { it.kind == YoutubeHit.Kind.COLLECTION }) -> {
                        val columns = if (configuration.screenWidthDp >= 840) 4 else 3
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            contentPadding = contentPadding,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(rows, key = { it.url }) { hit ->
                                YoutubeTile(
                                    hit = hit,
                                    isCurrent = currentSongId == YoutubeClient.songIdForUrl(hit.url),
                                    isPlaying = isPlaying,
                                    selecting = selecting,
                                    selected = hit.url in selectedUrls,
                                    onClick = { onSongClick(hit, browseSection == YoutubeSection.RADIO) },
                                    onLongPress = { onSongLongPress(hit) },
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = contentPadding,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (browseSection == YoutubeSection.RADIO) {
                                item(key = "radio_explainer") {
                                    Text(
                                        text = stringResource(R.string.youtube_radio_explainer),
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        fontFamily = GoogleSansRounded,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            itemsIndexed(rows, key = { _, hit -> hit.url }) { _, hit ->
                                val song = remember(hit) { placeholderSong(hit) }
                                YoutubeTrackRow(
                                    song = song,
                                    isPlaying = isPlaying && currentSongId == song.id,
                                    isCurrent = currentSongId == song.id,
                                    liked = song.id in likedIds || viewModel.isLiked(hit),
                                    selecting = selecting,
                                    selected = selectedUrls.indexOf(hit.url).let { if (it >= 0) it + 1 else null },
                                    onPlay = { onSongClick(hit, browseSection == YoutubeSection.RADIO) },
                                    onLongPress = { onSongLongPress(hit) },
                                    onActions = { onSongActions(hit) },
                                    onLike = { viewModel.toggleLike(hit) },
                                )
                            }
                            if (uiState.isLoadingMore) {
                                item(key = "loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        LoadingIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
        }
        }
        }
    }
    if (showChangelog) {
        ChangelogModalSheet(onDismiss = { showChangelog = false })
    }
    if (showBeta) {
        ModalBottomSheet(onDismissRequest = { showBeta = false }) {
            BetaInfoBottomSheet()
        }
    }
    if (showAddToPlaylist) {
        ModalBottomSheet(onDismissRequest = { showAddToPlaylist = false }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.youtube_add_to_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                TextButton(onClick = {
                    showAddToPlaylist = false
                    showCreatePlaylist = true
                }) {
                    Text(stringResource(R.string.youtube_new_playlist), fontFamily = GoogleSansRounded)
                }
                if (libraryPlaylists.isEmpty()) {
                    Text(
                        text = stringResource(R.string.youtube_add_to_playlist_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    libraryPlaylists.forEach { playlist ->
                        Surface(
                            onClick = {
                                viewModel.addHitsToPlaylist(playlist, selectedHits)
                                showAddToPlaylist = false
                                selecting = false
                                selectedUrls = emptyList()
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            Text(
                                text = playlist.title,
                                modifier = Modifier.padding(16.dp),
                                fontFamily = GoogleSansRounded,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showCreatePlaylist) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text(stringResource(R.string.youtube_new_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.youtube_playlist_name)) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.createPlaylistAndAdd(newPlaylistName, selectedHits)
                        newPlaylistName = ""
                        showCreatePlaylist = false
                        selecting = false
                        selectedUrls = emptyList()
                    },
                    enabled = newPlaylistName.trim().isNotEmpty(),
                ) {
                    Text(stringResource(R.string.youtube_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) {
                    Text(stringResource(R.string.youtube_cancel))
                }
            },
        )
    }
    if (showMultiSelectionSheet && selectedSongs.isNotEmpty()) {
        MultiSelectionBottomSheet(
            selectedSongs = selectedSongs,
            favoriteSongIds = likedIds + selectedHits.filter(viewModel::isLiked).map {
                YoutubeClient.songIdForUrl(it.url)
            }.toSet(),
            onDismiss = { showMultiSelectionSheet = false },
            onPlayAll = {
                if (selectedHits.size == 1) {
                    playHit(selectedHits.first(), browseSection == YoutubeSection.RADIO)
                } else {
                    playerViewModel.playSongs(
                        songsToPlay = selectedSongs,
                        startSong = selectedSongs.first(),
                        queueName = uiState.collectionTitle ?: "YouTube Music",
                    )
                }
                clearYoutubeSelection()
            },
            onAddToQueue = {
                playerViewModel.addSelectedToQueue(selectedSongs)
                clearYoutubeSelection()
            },
            onPlayNext = {
                playerViewModel.addSelectedAsNext(selectedSongs)
                clearYoutubeSelection()
            },
            onAddToPlaylist = {
                viewModel.ensureLibraryPlaylists()
                showAddToPlaylist = true
                showMultiSelectionSheet = false
            },
            onToggleLikeAll = { shouldLike ->
                selectedHits.forEach { hit ->
                    if (viewModel.isLiked(hit) != shouldLike) viewModel.toggleLike(hit)
                }
                showMultiSelectionSheet = false
            },
            onShareAll = {
                val text = selectedHits.joinToString("\n") { it.url }
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        },
                        selectedHits.first().title,
                    )
                )
                showMultiSelectionSheet = false
            },
            onDeleteAll = { _, onResult -> onResult(false) },
            onBatchEdit = { showMultiSelectionSheet = false },
            showBatchEdit = false,
            showDelete = false,
        )
    }
}

@Composable
private fun YoutubeCompactHeader(
    title: String?,
    showTitle: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    showSearch: Boolean,
    searchFocusRequester: FocusRequester,
    searchFilter: YoutubeSearchFilter,
    onFilter: (YoutubeSearchFilter) -> Unit,
    onBack: (() -> Unit)?,
    signedIn: Boolean,
    onBeta: () -> Unit,
    onChangelog: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
    onCreatePlaylist: (() -> Unit)? = null,
    onSelectTracks: (() -> Unit)? = null,
) {
    val headerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    Column(Modifier.fillMaxWidth().background(headerColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.youtube_back),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onBeta,
                    shape = CircleShape,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.topbar_beta_letter) + " " + stringResource(R.string.topbar_beta_label),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (showTitle && !title.isNullOrBlank()) {
                Text(
                    text = title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (onSelectTracks != null) {
                FilledIconButton(onClick = onSelectTracks) {
                    Icon(
                        imageVector = Icons.Rounded.SelectAll,
                        contentDescription = stringResource(R.string.youtube_select),
                    )
                }
            }
            if (onCreatePlaylist != null) {
                FilledIconButton(onClick = onCreatePlaylist) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.youtube_new_playlist),
                    )
                }
            }
            FilledIconButton(onClick = onAccount) {
                Icon(
                    imageVector = if (signedIn) Icons.Rounded.AccountCircle else Icons.Rounded.AccountCircle,
                    contentDescription = stringResource(R.string.youtube_sign_in),
                )
            }
            FilledIconButton(onClick = onChangelog) {
                Icon(
                    painter = painterResource(R.drawable.round_newspaper_24),
                    contentDescription = stringResource(R.string.topbar_cd_changelog),
                )
            }
            FilledIconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.rounded_settings_24),
                    contentDescription = stringResource(R.string.common_settings),
                )
            }
        }
        if (showSearch) {
            YoutubeSearchField(
                query = query,
                onQueryChange = onQueryChange,
                focusRequester = searchFocusRequester,
                searchFilter = searchFilter,
                onFilter = onFilter,
            )
        }
    }
}

@Composable
private fun YoutubeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    focusRequester: FocusRequester,
    searchFilter: YoutubeSearchFilter,
    onFilter: (YoutubeSearchFilter) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val corner = 28.dp
    val fieldColors = SearchBarDefaults.inputFieldColors(
        focusedTextColor = MaterialTheme.colorScheme.onSurface,
        unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
    )
    DockedSearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { keyboard?.hide() },
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        stringResource(R.string.youtube_search_placeholder),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = stringResource(R.string.youtube_search_title),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                keyboard?.hide()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.youtube_clear_search),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                } else {
                    null
                },
                colors = fieldColors,
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(corner)),
        shape = RoundedCornerShape(corner),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            dividerColor = Color.Transparent,
            inputFieldColors = fieldColors,
        ),
    ) {}
    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        YoutubeSearchFilter.entries.forEach { filter ->
            FilterChip(
                selected = searchFilter == filter,
                onClick = { onFilter(filter) },
                label = {
                    Text(
                        stringResource(
                            when (filter) {
                                YoutubeSearchFilter.SONGS -> R.string.youtube_filter_songs
                                YoutubeSearchFilter.ALBUMS -> R.string.youtube_filter_albums
                                YoutubeSearchFilter.PLAYLISTS -> R.string.youtube_filter_playlists
                            }
                        ),
                        fontFamily = GoogleSansRounded,
                    )
                },
            )
        }
    }
}

@Composable
private fun YoutubeTrackRow(
    song: Song,
    isPlaying: Boolean,
    isCurrent: Boolean,
    liked: Boolean,
    selecting: Boolean,
    selected: Int?,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    onActions: () -> Unit,
    onLike: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        EnhancedSongListItem(
            modifier = Modifier
                .weight(1f)
                .combinedClickable(onClick = onPlay, onLongClick = onLongPress),
            song = song,
            isPlaying = isPlaying,
            isCurrentSong = isCurrent,
            isSelected = selected != null,
            selectionIndex = selected,
            isSelectionMode = selecting,
            showMoreOptionsButton = !selecting,
            handleGestures = false,
            onLongPress = onLongPress,
            onMoreOptionsClick = { onActions() },
            onClick = onPlay,
        )
        if (!selecting) {
            IconButton(onClick = onLike) {
                Icon(
                    imageVector = if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(if (liked) R.string.youtube_unlike else R.string.youtube_like),
                    tint = if (liked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun YoutubeTile(
    hit: YoutubeHit,
    isCurrent: Boolean,
    isPlaying: Boolean,
    subtitle: String = hit.artist,
    modifier: Modifier = Modifier,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {},
) {
    val selectable = hit.isSelectableTrack()
    Surface(
        modifier = modifier.combinedClickable(
            onClick = onClick,
            onLongClick = { if (selectable) onLongPress() },
        ),
        shape = RoundedCornerShape(24.dp),
        color = when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            isCurrent -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
    ) {
        Column(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(
                    model = hit.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop,
                )
                if (selecting && selectable) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = if (selected) 0.45f else 0.18f,
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Icon(
                                imageVector = Icons.Rounded.SelectAll,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                } else if (isCurrent && isPlaying) {
                    PlayingEqIcon(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = hit.title,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FeedListeningStatsButton(onClick: () -> Unit) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 32.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 32.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBL = 32.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusTR = 32.dp,
        smoothnessAsPercentBL = 60,
    )
    LargeExtendedFloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 8.dp)
            .heightIn(min = 72.dp),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = shape,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoGraph,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.stats_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.youtube_feed_stats_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                fontFamily = GoogleSansRounded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyYoutubeState(message: String) {
    Column(
        modifier = Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            fontFamily = GoogleSansRounded,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun placeholderSong(hit: YoutubeHit): Song {
    val id = YoutubeClient.songIdForUrl(hit.url)
    YoutubeClient.rememberWatchUrl(id, hit.url)
    return Song(
        id = id,
        title = hit.title,
        artist = hit.artist,
        artistId = -1L,
        artists = emptyList(),
        album = hit.album,
        albumId = -1L,
        path = hit.url,
        contentUriString = hit.url,
        albumArtUriString = hit.thumbnailUrl,
        duration = hit.durationSec.coerceAtLeast(0L) * 1000L,
        genre = null,
        mimeType = null,
        bitrate = null,
        sampleRate = null,
    )
}

private fun YoutubeHit.isSelectableTrack(): Boolean = kind == YoutubeHit.Kind.TRACK

private fun List<String>.toggleUrl(url: String): List<String> =
    if (url in this) this - url else this + url
