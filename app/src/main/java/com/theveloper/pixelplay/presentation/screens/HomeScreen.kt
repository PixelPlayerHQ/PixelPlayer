package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import android.content.Intent
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.presentation.components.AlbumArtCollage
import com.theveloper.pixelplay.presentation.components.BetaInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.Beta05CleanInstallDisclaimerDialog
import com.theveloper.pixelplay.presentation.components.ChangelogBottomSheet
import com.theveloper.pixelplay.presentation.netease.dashboard.NeteaseDashboardViewModel
import com.theveloper.pixelplay.presentation.jellyfin.dashboard.JellyfinDashboardViewModel
import com.theveloper.pixelplay.presentation.navidrome.dashboard.NavidromeDashboardViewModel
import com.theveloper.pixelplay.presentation.qqmusic.dashboard.QqMusicDashboardViewModel
import com.theveloper.pixelplay.presentation.components.DailyMixSection
import com.theveloper.pixelplay.presentation.components.HomeGradientTopBar
import com.theveloper.pixelplay.presentation.components.HomeOptionsBottomSheet
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSection
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedSectionMinSongsToShow
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.StatsOverviewCard
import com.theveloper.pixelplay.presentation.model.collectRecentlyPlayedSongIds
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.components.subcomps.PlayingEqIcon
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.components.StreamingProviderSheet
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

// Modern HomeScreen with collapsible top bar and staggered grid layout
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    paddingValuesParent: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    neteaseViewModel: NeteaseDashboardViewModel = hiltViewModel(),
    qqMusicViewModel: QqMusicDashboardViewModel = hiltViewModel(),
    navidromeViewModel: NavidromeDashboardViewModel = hiltViewModel(),
    jellyfinViewModel: JellyfinDashboardViewModel = hiltViewModel(),
    onOpenSidebar: () -> Unit
) {
    val context = LocalContext.current
    // DETECTAR MODO BENCHMARK
    val isBenchmarkMode = remember {
        (context as? android.app.Activity)?.intent?.getBooleanExtra("is_benchmark", false) ?: false
    }
    val statsViewModel: StatsViewModel = hiltViewModel()
    val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val dailyMixSongs by playerViewModel.dailyMixSongs.collectAsStateWithLifecycle()
    val curatedYourMixSongs by playerViewModel.yourMixSongs.collectAsStateWithLifecycle()
    val homeMixPreviewSongs by playerViewModel.homeMixPreviewSongs.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val usesFallbackHomeMix = remember(curatedYourMixSongs, dailyMixSongs) {
        curatedYourMixSongs.isEmpty() && dailyMixSongs.isEmpty()
    }
    val yourMixSongs = remember(curatedYourMixSongs, dailyMixSongs, homeMixPreviewSongs) {
        when {
            curatedYourMixSongs.isNotEmpty() -> curatedYourMixSongs
            dailyMixSongs.isNotEmpty() -> dailyMixSongs
            else -> homeMixPreviewSongs
        }
    }
    val recentSongIds = remember(playbackHistory) {
        collectRecentlyPlayedSongIds(
            playbackHistory = playbackHistory,
            maxItems = 64
        )
    }
    val recentlyPlayedSourceSongsInitialValue = remember(recentSongIds) {
        if (recentSongIds.isEmpty()) persistentListOf<Song>() else null
    }
    val recentlyPlayedSourceSongs by remember(recentSongIds, playerViewModel) {
        playerViewModel.observeSongs(recentSongIds)
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = recentlyPlayedSourceSongsInitialValue)
    val latestRecentlyPlayedSongs = remember(playbackHistory, recentlyPlayedSourceSongs) {
        val sourceSongs = recentlyPlayedSourceSongs ?: return@remember emptyList()
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = sourceSongs,
            maxItems = 64
        )
    }
    // Keep the visible Home snapshot stable and only refresh it once the screen is off-screen.
    var recentlyPlayedSongs by remember { mutableStateOf(latestRecentlyPlayedSongs) }
    val latestRecentlyPlayedSongsState = rememberUpdatedState(latestRecentlyPlayedSongs)

    LaunchedEffect(latestRecentlyPlayedSongs, lifecycleOwner) {
        val isHomeVisible = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (recentlyPlayedSongs.isEmpty() || !isHomeVisible) {
            recentlyPlayedSongs = latestRecentlyPlayedSongs
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                recentlyPlayedSongs = latestRecentlyPlayedSongsState.value
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val recentlyPlayedQueue = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    ReportDrawnWhen {
        yourMixSongs.isNotEmpty() || isBenchmarkMode
    }

    val yourMixSong: String = "Today's Mix for you"

    // 2) Observar sólo el currentSong (o null) para saber si mostrar padding
    val currentSong by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong }
    }.collectAsStateWithLifecycle(initialValue = null)

    // 3) Observe shuffle state for sync
    val isShuffleEnabled by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState
            .map { it.isShuffleEnabled }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)

    // Padding inferior si hay canción en reproducción
    val bottomPadding = if (currentSong != null) MiniPlayerHeight else 0.dp

    var showOptionsBottomSheet by remember { mutableStateOf(false) }
    var showChangelogBottomSheet by remember { mutableStateOf(false) }
    var showBetaInfoBottomSheet by remember { mutableStateOf(false) }
    var showStreamingProviderSheet by remember { mutableStateOf(false) }
    var cleanInstallDisclaimerDismissedThisSession by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    val betaSheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    LocalContext.current

    val weeklyStats by statsViewModel.weeklyOverview.collectAsStateWithLifecycle()

    // Drawer state for sidebar
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val shouldShowCleanInstallDisclaimer =
        settingsUiState.beta05CleanInstallDisclaimerDismissed == false &&
            !cleanInstallDisclaimerDismissedThisSession

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                HomeGradientTopBar(
                    onNavigationIconClick = {
                        navController.navigateSafely(Screen.Settings.route)
                    },
                    onMoreOptionsClick = {
                        showChangelogBottomSheet = true
                    },
                    onBetaClick = {
                        showBetaInfoBottomSheet = true
                    },
                    onTelegramClick = {
                         showStreamingProviderSheet = true
                    },
                    onMenuClick = {
                        // onOpenSidebar() // Disabled
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding(),
                    bottom = paddingValuesParent.calculateBottomPadding()
                            + 38.dp + bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Your Mix
                item(
                    key = "your_mix_header",
                    contentType = "your_mix_header"
                ) {
                    YourMixHeader(
                        song = yourMixSong,
                        isShuffleEnabled = isShuffleEnabled,
                        onPlayShuffled = {
                            if (yourMixSongs.isNotEmpty()) {
                                if (usesFallbackHomeMix) {
                                    playerViewModel.shuffleAllSongs(queueName = "Your Mix")
                                } else {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = yourMixSongs,
                                        queueName = "Your Mix",
                                        startAtZero = true,
                                    )
                                }
                            }
                        }
                    )
                }

                // Collage
                if (yourMixSongs.isNotEmpty()) {
                    item(
                        key = "album_art_collage",
                        contentType = "album_art_collage"
                    ) {
                        val basePattern = settingsUiState.collagePattern
                        val isAutoRotate = settingsUiState.collageAutoRotate
                        val patterns = remember { CollagePattern.entries }

                        val activePattern = if (isAutoRotate) {
                            var rotationIndex by rememberSaveable { mutableIntStateOf(-1) }
                            LaunchedEffect(Unit) { rotationIndex++ }
                            remember(rotationIndex) {
                                patterns[rotationIndex.coerceAtLeast(0) % patterns.size]
                            }
                        } else {
                            basePattern
                        }

                        AlbumArtCollage(
                            modifier = Modifier.fillMaxWidth(),
                            songs = yourMixSongs,
                            padding = 14.dp,
                            height = 400.dp,
                            pattern = activePattern,
                            onSongClick = { song ->
                                if (usesFallbackHomeMix) {
                                    playerViewModel.showAndPlaySongFromLibrary(song, queueName = "Your Mix")
                                } else {
                                    playerViewModel.showAndPlaySong(song, yourMixSongs, "Your Mix")
                                }
                            }
                        )
                    }
                }

                // Daily Mix
                if (dailyMixSongs.isNotEmpty()) {
                    item(
                        key = "daily_mix_section",
                        contentType = "daily_mix_section"
                    ) {
                        DailyMixSection(
                            songs = dailyMixSongs,
                            onClickOpen = {
                                navController.navigateSafely(Screen.DailyMixScreen.route)
                            },
                            onNavigateToAlbum = { song ->
                                navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                            },
                            onNavigateToArtist = { song ->
                                navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                            },
                            onNavigateToGenre = { song ->
                                song.genre?.let {
                                    navController.navigateSafely(Screen.GenreDetail.createRoute(java.net.URLEncoder.encode(it, "UTF-8")))
                                }
                            },
                            playerViewModel = playerViewModel
                        )
                    }
                }

                if (recentlyPlayedSongs.size >= RecentlyPlayedSectionMinSongsToShow) {
                    item(
                        key = "recently_played_section",
                        contentType = "recently_played_section"
                    ) {
                        RecentlyPlayedSection(
                            songs = recentlyPlayedSongs,
                            onSongClick = { song ->
                                if (recentlyPlayedQueue.isNotEmpty()) {
                                    playerViewModel.playSongs(
                                        songsToPlay = recentlyPlayedQueue,
                                        startSong = song,
                                        queueName = "Recently Played"
                                    )
                                }
                            },
                            onOpenAllClick = {
                                navController.navigateSafely(Screen.RecentlyPlayed.route)
                            },
                            currentSongId = currentSong?.id,
                            contentPadding = PaddingValues(start = 8.dp, end = 24.dp)
                        )
                    }
                }

                item(
                    key = "listening_stats_preview",
                    contentType = "listening_stats_preview"
                ) {
                    StatsOverviewCard(
                        summary = weeklyStats,
                        onClick = { navController.navigateSafely(Screen.Stats.route) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(170.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.2f to Color.Transparent,
                            0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                            1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }
    }
    if (showOptionsBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOptionsBottomSheet = false },
            sheetState = sheetState
        ) {
            HomeOptionsBottomSheet(
                onNavigateToMashup = {
                    scope.launch {
                        sheetState.hide()
                    }.invokeOnCompletion {
                        if (!sheetState.isVisible) {
                            showOptionsBottomSheet = false
                            navController.navigateSafely(Screen.DJSpace.route)
                        }
                    }
                }
            )
        }
    }
    if (showChangelogBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showChangelogBottomSheet = false },
            sheetState = sheetState
        ) {
            ChangelogBottomSheet()
        }
    }
    if (showBetaInfoBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBetaInfoBottomSheet = false },
            sheetState = betaSheetState,
            //contentWindowInsets = { WindowInsets.statusBars.only(WindowInsets.statusBars) }
        ) {
            BetaInfoBottomSheet()
        }
    }
    if (showStreamingProviderSheet) {
        val isNeteaseLoggedIn by neteaseViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isQqMusicLoggedIn by qqMusicViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isNavidromeLoggedIn by navidromeViewModel.isLoggedIn.collectAsStateWithLifecycle()
        val isJellyfinLoggedIn by jellyfinViewModel.isLoggedIn.collectAsStateWithLifecycle()
        StreamingProviderSheet(
            onDismissRequest = { showStreamingProviderSheet = false },
            isNeteaseLoggedIn = isNeteaseLoggedIn,
            onNavigateToNeteaseDashboard = {
                navController.navigateSafely(Screen.NeteaseDashboard.route)
            },
            isQqMusicLoggedIn = isQqMusicLoggedIn,
            onNavigateToQqMusicDashboard = {
                navController.navigateSafely(Screen.QqMusicDashboard.route)
            },
            isNavidromeLoggedIn = isNavidromeLoggedIn,
            onNavigateToNavidromeDashboard = {
                navController.navigateSafely(Screen.NavidromeDashboard.route)
            },
            isJellyfinLoggedIn = isJellyfinLoggedIn,
            onNavigateToJellyfinDashboard = {
                navController.navigateSafely(Screen.JellyfinDashboard.route)
            }
        )
    }
    if (shouldShowCleanInstallDisclaimer) {
        Beta05CleanInstallDisclaimerDialog(
            onDismiss = { dontShowAgain ->
                cleanInstallDisclaimerDismissedThisSession = true
                if (dontShowAgain) {
                    settingsViewModel.setBeta05CleanInstallDisclaimerDismissed(true)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun YourMixHeader(
    song: String,
    isShuffleEnabled: Boolean = false,
    onPlayShuffled: () -> Unit
) {
    val buttonCorners = 68.dp
    val colors = MaterialTheme.colorScheme

    val titleStyle = rememberYourMixTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(256.dp)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 12.dp)
        ) {
            // Your Mix Title
            Text(
                text = "Your\nMix",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
            )

            // Artist/Song subtitle
            Text(
                text = song,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        // Play Button - color changes based on shuffle state
        LargeExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp),
            onClick = onPlayShuffled,
            containerColor = if (isShuffleEnabled) colors.primary else colors.tertiaryContainer,
            contentColor = if (isShuffleEnabled) colors.onPrimary else colors.onTertiaryContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = buttonCorners,
                smoothnessAsPercentTR = 60,
                cornerRadiusBR = buttonCorners,
                smoothnessAsPercentTL = 60,
                cornerRadiusBL = buttonCorners,
                smoothnessAsPercentBR = 60,
                cornerRadiusTR = buttonCorners,
                smoothnessAsPercentBL = 60,
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_shuffle_24),
                contentDescription = "Shuffle Play",
                modifier = Modifier.size(36.dp)
            )
        }
    }
}


// SongListItem (modificado para aceptar parámetros individuales)
@Composable
fun SongListItemFavs(
    modifier: Modifier = Modifier,
    cardCorners: Dp = 12.dp,
    title: String,
    artist: String,
    albumArtUrl: String?,
    isPlaying: Boolean,
    isCurrentSong: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val containerColor = if (isCurrentSong) colors.primaryContainer.copy(alpha = 0.46f) else colors.surfaceContainer
    val contentColor = if (isCurrentSong) colors.primary else colors.onSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(cardCorners),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(0.9f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SmartImage(
                    model = albumArtUrl,
                    contentDescription = "Carátula de $title",
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Normal,
                        color = contentColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist, style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            if (isCurrentSong) {
                PlayingEqIcon(
                    modifier = Modifier
                        .weight(0.1f)
                        .padding(start = 8.dp)
                        .size(width = 18.dp, height = 16.dp), // similar al tamaño del ícono
                    color = colors.primary,
                    isPlaying = isPlaying  // o conectalo a tu estado real de reproducción
                )
            }
        }
    }
}

// Wrapper Composable for SongListItemFavs to isolate state observation
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SongListItemFavsWrapper(
    song: Song,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect the stablePlayerState once
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsStateWithLifecycle()

    // Derive isThisSongPlaying using remember
    val isThisSongPlaying = remember(song.id, stablePlayerState.currentSong?.id, stablePlayerState.isPlaying) {
        song.id == stablePlayerState.currentSong?.id
    }

    // Call the presentational composable
    SongListItemFavs(
        modifier = modifier,
        cardCorners = 0.dp,
        title = song.title,
        artist = song.displayArtist,
        albumArtUrl = song.albumArtUriString,
        isPlaying = stablePlayerState.isPlaying,
        isCurrentSong = song.id == stablePlayerState.currentSong?.id,
        onClick = onClick
    )
}


@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberYourMixTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(636),
                        FontVariation.width(152f),
                        FontVariation.Setting("ROND", 50f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(760),
            fontSize = 64.sp,
            lineHeight = 62.sp
        )
    }
}
