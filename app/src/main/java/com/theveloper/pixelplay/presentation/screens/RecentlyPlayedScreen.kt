package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

import android.os.Trace
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlaylistBottomSheet
import com.theveloper.pixelplay.presentation.components.RecentlyPlayedRangeSelector
import com.theveloper.pixelplay.presentation.components.SongInfoBottomSheet
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.model.RecentlyPlayedSongUiModel
import com.theveloper.pixelplay.presentation.model.mapRecentlyPlayedSongs
import com.theveloper.pixelplay.presentation.viewmodel.PlaylistViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentlyPlayedScreen(
    playerViewModel: PlayerViewModel,
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    navController: NavController
) {
    Trace.beginSection("RecentlyPlayedScreen.Composition")

    val allSongs by playerViewModel.allSongsFlow.collectAsStateWithLifecycle()
    val playbackHistory by playerViewModel.playbackHistory.collectAsStateWithLifecycle()
    val currentSongId by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.currentSong?.id }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by remember(playerViewModel.stablePlayerState) {
        playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = false)
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val selectedSongForInfo by playerViewModel.selectedSongForInfo.collectAsStateWithLifecycle()
    val playlistUiState by playlistViewModel.uiState.collectAsStateWithLifecycle()

    var selectedRange by rememberSaveable { mutableStateOf(StatsTimeRange.WEEK) }
    val lazyListState = rememberLazyListState()
    var showSongInfoBottomSheet by remember { mutableStateOf(false) }
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val bottomBarHeightDp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val recentlyPlayedSongs = remember(playbackHistory, allSongs, selectedRange) {
        mapRecentlyPlayedSongs(
            playbackHistory = playbackHistory,
            songs = allSongs,
            range = selectedRange,
            maxItems = Int.MAX_VALUE
        )
    }
    val groupedSongs = remember(recentlyPlayedSongs, selectedRange) {
        groupRecentlyPlayedSongs(
            songs = recentlyPlayedSongs,
            range = selectedRange
        )
    }
    val queueSongs = remember(recentlyPlayedSongs) {
        recentlyPlayedSongs.map { it.song }.toImmutableList()
    }

    val bgColors = listOf(
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f),
        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        MaterialTheme.colorScheme.surface
    )

    val backgroundBrush = remember {
        Brush.verticalGradient(
            colors = bgColors,
            endY = 1200f
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (allSongs.isEmpty() && playbackHistory.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "recently_played_header") {
                    ExpressiveRecentlyPlayedHeader(
                        songs = recentlyPlayedSongs,
                        selectedRange = selectedRange,
                        scrollState = lazyListState
                    )
                }

                item(key = "recently_played_range_selector") {
                    RecentlyPlayedRangeSelector(
                        selected = selectedRange,
                        onRangeSelected = { selectedRange = it },
                        modifier = Modifier.padding(horizontal = 0.dp)
                    )
                }

                if (recentlyPlayedSongs.isNotEmpty()) {
                    item(key = "recently_played_actions") {
                        RecentlyPlayedActions(
                            onPlay = {
                                val firstSong = queueSongs.firstOrNull() ?: return@RecentlyPlayedActions
                                playerViewModel.playSongs(queueSongs, firstSong, "Recently Played")
                            },
                            onShuffle = {
                                playerViewModel.playSongsShuffled(
                                    songsToPlay = queueSongs,
                                    queueName = "Recently Played"
                                )
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }

                if (recentlyPlayedSongs.isEmpty()) {
                    item(key = "recently_played_empty") {
                        RecentlyPlayedEmptyState(
                            range = selectedRange,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    groupedSongs.forEachIndexed { groupIndex, group ->
                        item(key = "recently_played_time_${groupIndex}_${group.key}") {
                            RecentlyPlayedTimestampDivider(
                                label = group.label,
                                isHourBucket = group.isHourBucket,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        items(group.songs, key = { songUi -> songUi.song.id }) { item ->
                            EnhancedSongListItem(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                song = item.song,
                                isCurrentSong = currentSongId == item.song.id,
                                isPlaying = currentSongId == item.song.id && isPlaying,
                                onClick = {
                                    playerViewModel.playSongs(
                                        songsToPlay = queueSongs,
                                        startSong = item.song,
                                        queueName = "Recently Played"
                                    )
                                },
                                onMoreOptionsClick = { song ->
                                    playerViewModel.selectSongForInfo(song)
                                    showSongInfoBottomSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showSongInfoBottomSheet && selectedSongForInfo != null) {
            val song = selectedSongForInfo!!
            SongInfoBottomSheet(
                song = song,
                isFavorite = favoriteSongIds.contains(song.id),
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(song)
                },
                onDismiss = {
                    showSongInfoBottomSheet = false
                    showPlaylistBottomSheet = false
                },
                onPlaySong = {
                    if (queueSongs.isNotEmpty()) {
                        playerViewModel.playSongs(queueSongs, song, "Recently Played")
                    }
                    showSongInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(song)
                    showSongInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigateSafely(Screen.AlbumDetail.createRoute(song.albumId))
                    showSongInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigateSafely(Screen.ArtistDetail.createRoute(song.artistId))
                    showSongInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                    playerViewModel.editSongMetadata(
                        song,
                        newTitle,
                        newArtist,
                        newAlbum,
                        newGenre,
                        newLyrics,
                        newTrackNumber,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(song, fields)
                },
                removeFromListTrigger = {}
            )

            if (showPlaylistBottomSheet) {
                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    songs = listOf(song),
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }

        FilledIconButton(
            onClick = { navController.popBackStack() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back"
            )
        }
    }

    Trace.endSection()
}

@Composable
private fun ExpressiveRecentlyPlayedHeader(
    songs: List<RecentlyPlayedSongUiModel>,
    selectedRange: StatsTimeRange,
    scrollState: LazyListState
) {
    val highlightedArt = remember(songs) { songs.take(4).map { it.song.albumArtUriString } }
    val parallaxOffset by remember {
        derivedStateOf {
            if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.36f else 0f
        }
    }
    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 520f)).coerceIn(0f, 1f)
        }
    }

    val surface = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val titleStyle = rememberRecentlyPlayedTitleStyle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            secondary.copy(alpha = 0.24f),
                            primary.copy(alpha = 0.10f),
                            surface.copy(alpha = 0.95f),
                            surface
                        ),
                        endY = 780f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
//            Surface(
//                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.88f),
//                shape = RoundedCornerShape(50),
//                tonalElevation = 0.dp
//            ) {
//                Text(
//                    text = "LIVE HISTORY · ${selectedRange.displayName.uppercase()}",
//                    style = MaterialTheme.typography.labelMedium,
//                    fontWeight = FontWeight.SemiBold,
//                    color = MaterialTheme.colorScheme.onSecondaryContainer,
//                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
//                )
//            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Recently Played",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberRecentlyPlayedTitleStyle(): TextStyle {
    return remember {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(560),
                        FontVariation.width(122f),
                        FontVariation.grade(40),
                        FontVariation.Setting("ROND", 100f),
                        FontVariation.Setting("XTRA", 520f),
                        FontVariation.Setting("YOPQ", 90f),
                        FontVariation.Setting("YTLC", 505f)
                    )
                )
            ),
            fontWeight = FontWeight(560),
            fontSize = 34.sp,
            lineHeight = 38.sp,
            letterSpacing = (-0.4).sp
        )
    }
}

@Composable
private fun RecentlyPlayedActions(
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 52.dp,
                topEnd = 14.dp,
                bottomStart = 52.dp,
                bottomEnd = 14.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Play latest")
        }

        FilledTonalButton(
            onClick = onShuffle,
            modifier = Modifier
                .weight(1f)
                .fillMaxSize(),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 52.dp,
                bottomStart = 14.dp,
                bottomEnd = 52.dp
            )
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Shuffle")
        }
    }
}

@Composable
private fun RecentlyPlayedTimestampDivider(
    label: String,
    isHourBucket: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val railColor = if (isHourBucket) colors.primary else colors.secondary
    val chipContainer = if (isHourBucket) {
        colors.primaryContainer.copy(alpha = 0.78f)
    } else {
        colors.secondaryContainer.copy(alpha = 0.78f)
    }
    val chipContent = if (isHourBucket) colors.onPrimaryContainer else colors.onSecondaryContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0f),
                            railColor.copy(alpha = 0.50f)
                        )
                    )
                )
        )
        Surface(
            color = chipContainer,
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTL = 22.dp,
                smoothnessAsPercentTR = 60,
                cornerRadiusTR = 22.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = 22.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBR = 22.dp,
                smoothnessAsPercentTL = 60
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(railColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = chipContent
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            railColor.copy(alpha = 0.50f),
                            railColor.copy(alpha = 0f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun RecentlyPlayedEmptyState(
    range: StatsTimeRange,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentTL = 60
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No recent plays in ${range.displayName.lowercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Change the range or play more songs to fill this timeline.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private data class TimestampGroup(
    val key: String,
    val label: String,
    val isHourBucket: Boolean,
    val songs: List<RecentlyPlayedSongUiModel>
)

private fun groupRecentlyPlayedSongs(
    songs: List<RecentlyPlayedSongUiModel>,
    range: StatsTimeRange
): List<TimestampGroup> {
    if (songs.isEmpty()) return emptyList()
    val zoneId = ZoneId.systemDefault()
    val sorted = songs.sortedByDescending { it.lastPlayedTimestamp }

    val groups = mutableListOf<TimestampGroup>()
    var currentBucketKey: String? = null
    var currentLabel: String? = null
    var currentIsHourBucket = false
    var bucket = mutableListOf<RecentlyPlayedSongUiModel>()

    sorted.forEach { item ->
        val timeBucket = resolveTimestampBucket(
            timestamp = item.lastPlayedTimestamp,
            range = range,
            zoneId = zoneId
        )
        if (currentBucketKey == null || currentBucketKey == timeBucket.key) {
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket += item
        } else {
            groups += TimestampGroup(
                key = currentBucketKey ?: "",
                label = currentLabel ?: "",
                isHourBucket = currentIsHourBucket,
                songs = bucket
            )
            currentBucketKey = timeBucket.key
            currentLabel = timeBucket.label
            currentIsHourBucket = timeBucket.isHourBucket
            bucket = mutableListOf(item)
        }
    }

    if (bucket.isNotEmpty() && currentLabel != null && currentBucketKey != null) {
        groups += TimestampGroup(
            key = currentBucketKey ?: "",
            label = currentLabel ?: "",
            isHourBucket = currentIsHourBucket,
            songs = bucket
        )
    }

    return groups
}

private data class TimestampBucket(
    val key: String,
    val label: String,
    val isHourBucket: Boolean
)

private fun resolveTimestampBucket(
    timestamp: Long,
    range: StatsTimeRange,
    zoneId: ZoneId
): TimestampBucket {
    val safeTimestamp = timestamp.coerceAtLeast(0L)
    val safeNow = System.currentTimeMillis().coerceAtLeast(0L)
    val zonedDateTime = Instant.ofEpochMilli(safeTimestamp).atZone(zoneId)
    val date = zonedDateTime.toLocalDate()
    val nowDate = Instant.ofEpochMilli(safeNow).atZone(zoneId).toLocalDate()

    if (range == StatsTimeRange.DAY) {
        val hourStart = zonedDateTime
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return TimestampBucket(
            key = hourStart.toInstant().toEpochMilli().toString(),
            label = hourStart.format(DateTimeFormatter.ofPattern("h a", Locale.getDefault())),
            isHourBucket = true
        )
    }

    return when {
        date == nowDate -> {
            TimestampBucket(
                key = date.toString(),
                label = "Today",
                isHourBucket = false
            )
        }
        date == nowDate.minusDays(1) -> {
            TimestampBucket(
                key = date.toString(),
                label = "Yesterday",
                isHourBucket = false
            )
        }
        range == StatsTimeRange.YEAR || range == StatsTimeRange.ALL -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())),
                isHourBucket = false
            )
        }
        else -> {
            TimestampBucket(
                key = date.toString(),
                label = date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())),
                isHourBucket = false
            )
        }
    }
}
