@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoGraph
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import com.theveloper.pixelplay.utils.formatListeningDurationLong
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.outlined.Album
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayCircleOutline
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography

private const val PULL_TO_REFRESH_MIN_DURATION_MS = 3500L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun StatsScreen(
    navController: NavController,
    statsViewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by statsViewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 62.dp + statusBarHeight
    val maxTopBarHeight = 176.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val scrollingDown = delta < 0

                if (!scrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsume = !(scrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsume) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val target = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != target) {
                coroutineScope.launch {
                    topBarHeight.animateTo(target, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
    val tabsHeight = 62.dp
    val showRangeTabIndicator = false
    val tabIndicatorExtraSpacing = if (showRangeTabIndicator) 8.dp else 0.dp
    val tabContentSpacing = 20.dp
    var selectedTimelineMetric by rememberSaveable { mutableStateOf(TimelineMetric.ListeningTime) }
    var selectedCategoryDimension by rememberSaveable { mutableStateOf(CategoryDimension.Song) }
    val pullToRefreshState = rememberPullToRefreshState()
    var isPullRefreshAnimating by remember { mutableStateOf(false) }
    var isPullRefreshMinDelayActive by remember { mutableStateOf(false) }
    var hasPendingPullRefresh by remember { mutableStateOf(false) }
    val isRefreshingFromViewModel by rememberUpdatedState(uiState.isRefreshing)

    val onPullRefresh: () -> Unit = {
        if (hasPendingPullRefresh || uiState.isLoading) {
            Unit
        } else {
            hasPendingPullRefresh = true
            isPullRefreshAnimating = true
            isPullRefreshMinDelayActive = true
            statsViewModel.requestStatsRefresh()
            coroutineScope.launch {
                delay(PULL_TO_REFRESH_MIN_DURATION_MS)
                isPullRefreshMinDelayActive = false
                if (!isRefreshingFromViewModel) {
                    isPullRefreshAnimating = false
                    hasPendingPullRefresh = false
                }
            }
        }
    }

    LaunchedEffect(uiState.isRefreshing, hasPendingPullRefresh, isPullRefreshMinDelayActive) {
        if (!hasPendingPullRefresh) return@LaunchedEffect
        if (uiState.isRefreshing) {
            isPullRefreshAnimating = true
        } else if (!isPullRefreshMinDelayActive) {
            isPullRefreshAnimating = false
            hasPendingPullRefresh = false
        }
    }

    PullToRefreshBox(
        isRefreshing = isPullRefreshAnimating,
        onRefresh = onPullRefresh,
        state = pullToRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullToRefreshState,
                isRefreshing = isPullRefreshAnimating,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = currentTopBarHeightDp + tabsHeight + tabIndicatorExtraSpacing + 4.dp)
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .nestedScroll(nestedScrollConnection)
        ) {
            if (uiState.isLoading && summary == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ContainedLoadingIndicator()
                }
            } else {
                val showDailyRhythm = summary?.range == StatsTimeRange.DAY || summary?.range == StatsTimeRange.WEEK

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = currentTopBarHeightDp + tabsHeight + tabIndicatorExtraSpacing + tabContentSpacing + 0.dp,
                        bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    item(key = "hero_section") {
                        StatsHeroSection(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        ListeningTimelineSection(
                            summary = summary,
                            selectedMetric = selectedTimelineMetric,
                            onMetricSelected = { selectedTimelineMetric = it },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        CategoryMetricsSection(
                            summary = summary,
                            selectedDimension = selectedCategoryDimension,
                            onDimensionSelected = { selectedCategoryDimension = it },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    item {
                        ListeningHabitsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TopArtistsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TopAlbumsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        TrackConcentrationCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    item {
                        SongStatsCard(
                            summary = summary,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(5f)
            ) {
                val solidAlpha = (collapseFraction * 2f).coerceIn(0f, 1f)
                val backgroundColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = solidAlpha)

                Column(
                    modifier = Modifier
                        .background(backgroundColor)
                        .padding(bottom = 8.dp) // Reduced padding below tabs
                ) {
                    CollapsibleCommonTopBar(
                        title = "Listening Stats",
                        collapseFraction = collapseFraction,
                        headerHeight = currentTopBarHeightDp,
                        onBackClick = { navController.popBackStack() },
                        containerColor = Color.Transparent,
                        actions = {
                            FilledIconButton(
                                modifier = Modifier
                                    .padding(end = 12.dp),
                                onClick = statsViewModel::requestStatsRefresh,
                                enabled = !uiState.isLoading && !uiState.isRefreshing && !isPullRefreshAnimating,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Refresh listening stats"
                                )
                            }
                        }
                    )

                    RangeTabsHeader(
                        ranges = uiState.availableRanges,
                        selected = uiState.selectedRange,
                        onRangeSelected = statsViewModel::onRangeSelected,
                        indicatorSpacing = tabIndicatorExtraSpacing,
                        showIndicator = showRangeTabIndicator,
                    )
                }
            }
        }
    }
}

// StatsTopBar removed, replaced by CollapsibleCommonTopBar

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsHeroSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val hasData = (summary?.totalDurationMs ?: 0L) > 0 || (summary?.totalPlayCount ?: 0) > 0
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Time Card - Primary Container
        HeroCard(
            title = "Listening",
            value = if (hasData) formatListeningDurationCompact(summary?.totalDurationMs ?: 0L) else "--",
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )

        // Plays Card - Tertiary Container
        HeroCard(
            title = "Plays",
            value = if (hasData) "${summary?.totalPlayCount ?: 0}" else "--",
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        )
    }
}

@Composable
private fun HeroCard(
    title: String,
    value: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = contentColor.copy(alpha = 0.85f)
        )
        Text(
            text = value,
            style = ExpTitleTypography.displayMedium.copy(fontSize = 32.sp),
            fontWeight = FontWeight.Bold,
            color = contentColor
        )
    }
}

// Empty state component with M3 Expressive styling
@Composable
private fun StatsEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedStarShape(sides = 8, curve = 0.1, rotation = 0f))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun SummaryPill(
    label: String,
    value: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SummaryHeroTile(
    title: String,
    value: String,
    supporting: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .widthIn(min = 160.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SummaryProgressRow(
    title: String,
    label: String?,
    supporting: String,
    progress: Float,
    modifier: Modifier = Modifier
) {
    val displayLabel = label ?: "—"
    val progressValue = progress.coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.9f))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayLabel,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        LinearWavyProgressIndicator(
            progress = { progressValue },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RangeTabsHeader(
    ranges: List<StatsTimeRange>,
    selected: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit,
    indicatorSpacing: Dp,
    showIndicator: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedIndex = remember(ranges, selected) { ranges.indexOf(selected).coerceAtLeast(0) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(1f),
        color = Color.Transparent,
        //shadowElevation = 6.dp
    ) {
        PrimaryScrollableTabRow(
            modifier = if (indicatorSpacing > 0.dp) Modifier.padding(bottom = indicatorSpacing) else Modifier,
            selectedTabIndex = selectedIndex,
            edgePadding = 12.dp,
            divider = {},
            containerColor = Color.Transparent,
            indicator = {
                if (showIndicator) {
                    TabRowDefaults.PrimaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(
                            selectedTabIndex = selectedIndex,
                            matchContentSize = true
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            }
        ) {
            ranges.forEachIndexed { index, range ->
                TabAnimation(
                    index = index,
                    selectedIndex = selectedIndex,
                    onClick = { onRangeSelected(range) },
                    selectedColor = MaterialTheme.colorScheme.primary,
                    onSelectedColor = MaterialTheme.colorScheme.onPrimary,
                    unselectedColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    onUnselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    title = range.displayName
                ) {
                    Text(
                        text = range.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun ListeningHabitsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Consistent with Hero
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Listening habits",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (summary == null) {
                StatsEmptyState(
                    icon = Icons.Outlined.History,
                    title = "No habits yet",
                    subtitle = "We will surface your listening habits once we know you better."
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HabitMetric(
                        icon = Icons.Outlined.History,
                        label = "Total sessions",
                        value = summary.totalSessions.toString()
                    )
                    HabitMetric(
                        icon = Icons.Outlined.Hearing,
                        label = "Avg session",
                        value = formatListeningDurationCompact(summary.averageSessionDurationMs)
                    )
                    HabitMetric(
                        icon = Icons.Outlined.Bolt,
                        label = "Longest session",
                        value = if (summary.longestSessionDurationMs > 0L) {
                            formatListeningDurationCompact(summary.longestSessionDurationMs)
                        } else {
                            "—"
                        }
                    )
                    HabitMetric(
                        icon = Icons.Outlined.AutoGraph,
                        label = "Sessions/day",
                        value = String.format(Locale.US, "%.1f", summary.averageSessionsPerDay)
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                HighlightRow(
                    title = "Most active day",
                    value = summary.peakDayLabel ?: "—",
                    supporting = if (summary.peakDayDurationMs > 0L) {
                        formatListeningDurationCompact(summary.peakDayDurationMs)
                    } else {
                        "No playback yet"
                    },
                    icon = Icons.Outlined.CalendarMonth
                )
                summary.peakTimeline?.let { peak ->
                    HighlightRow(
                        title = "Peak timeline slot",
                        value = peak.label,
                        supporting = formatListeningDurationCompact(peak.totalDurationMs),
                        icon = Icons.Outlined.AutoGraph
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitMetric(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatMinutesWindowLabel(startMinute: Int, endMinute: Int): String {
    val safeStart = startMinute.coerceIn(0, 24 * 60)
    val safeEnd = endMinute.coerceIn(0, 24 * 60)
    return "${formatHourLabel(safeStart)} – ${formatHourLabel(safeEnd)}"
}

private fun formatHourLabel(minute: Int): String {
    val normalized = minute.coerceIn(0, 24 * 60)
    val hours = normalized / 60
    val mins = normalized % 60
    return String.format(Locale.getDefault(), "%02d:%02d", hours % 24, mins)
}

@Composable
private fun HighlightRow(
    title: String,
    value: String,
    supporting: String,
    icon: ImageVector
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class TimelineMetric(
    val displayName: String,
    val description: String,
    val extractValue: (PlaybackStatsRepository.TimelineEntry) -> Double,
    val formatValue: (PlaybackStatsRepository.TimelineEntry) -> String
) {
    ListeningTime(
        displayName = "Listening time",
        description = "Total listening captured in the selected range.",
        extractValue = { it.totalDurationMs.toDouble() },
        formatValue = { formatListeningDurationCompact(it.totalDurationMs) }
    ),
    PlayCount(
        displayName = "Play count",
        description = "How many sessions you completed per segment.",
        extractValue = { it.playCount.toDouble() },
        formatValue = { "${it.playCount} plays" }
    ),
    AverageSession(
        displayName = "Avg. session",
        description = "Average listening length for each segment.",
        extractValue = { entry ->
            if (entry.playCount > 0) entry.totalDurationMs.toDouble() / entry.playCount.toDouble() else 0.0
        },
        formatValue = { entry ->
            val average = if (entry.playCount > 0) entry.totalDurationMs / entry.playCount else 0L
            formatListeningDurationCompact(average)
        }
    )
}

private enum class CategoryDimension(
    val displayName: String,
    val cardTitle: String,
    val highlightTitle: String
) {
    Genre(
        displayName = "Genre",
        cardTitle = "Listening by genre",
        highlightTitle = "Top genre"
    ),
    Artist(
        displayName = "Artist",
        cardTitle = "Listening by artist",
        highlightTitle = "Top artist"
    ),
    Album(
        displayName = "Album",
        cardTitle = "Listening by album",
        highlightTitle = "Top album"
    ),
    Song(
        displayName = "Song",
        cardTitle = "Listening by song",
        highlightTitle = "Top song"
    )
}

private data class CategoryMetricEntry(
    val label: String,
    val durationMs: Long,
    val supporting: String
)

private enum class TimelineChartLayout {
    Vertical,
    Horizontal
}

private data class TimelineChartSpec(
    val layout: TimelineChartLayout,
    val minItemWidth: Dp,
    val maxItemWidth: Dp,
    val maxVisibleItems: Int,
    val chartHeight: Dp,
    val labelMaxLines: Int,
    val horizontalContentPadding: Dp
)

private data class CategoryChartPalette(
    val containerColor: Color,
    val contentColor: Color,
    val accentColor: Color,
    val accentOnColor: Color
)

private data class TrackShareSlice(
    val label: String,
    val durationMs: Long,
    val color: Color
)

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberStatsSectionTitleStyle(): TextStyle {
    return remember {
        androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(570)
                    )
                )
            ),
            fontWeight = FontWeight(570),
            fontSize = 24.sp,
            lineHeight = 28.sp,
            letterSpacing = (-0.2).sp
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberStatsAxisLabelStyle(range: StatsTimeRange): TextStyle {
    val fontSize = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> 10.sp
        StatsTimeRange.MONTH, StatsTimeRange.ALL -> 11.sp
        StatsTimeRange.YEAR -> 10.sp
    }
    return remember(range, fontSize) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(520)
                    )
                )
            ),
            fontWeight = FontWeight(520),
            fontSize = fontSize,
            lineHeight = if (range == StatsTimeRange.YEAR) 12.sp else 11.sp,
            letterSpacing = 0.sp
        )
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun rememberStatsMetricValueStyle(compact: Boolean): TextStyle {
    return remember(compact) {
        TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.genre_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(650),
                        FontVariation.width(95f),
                        FontVariation.grade(30)
                    )
                )
            ),
            fontWeight = FontWeight(650),
            fontSize = if (compact) 10.sp else 12.sp,
            lineHeight = if (compact) 12.sp else 14.sp,
            letterSpacing = 0.sp
        )
    }
}

@Composable
private fun ListeningTimelineSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedMetric: TimelineMetric,
    onMetricSelected: (TimelineMetric) -> Unit,
    modifier: Modifier = Modifier
) {
    val range = summary?.range ?: StatsTimeRange.WEEK
    val timeline = summary?.timeline.orEmpty()
    val hasTimeline = timeline.isNotEmpty() && timeline.any { it.totalDurationMs > 0L || it.playCount > 0 }
    val sectionTitleStyle = rememberStatsSectionTitleStyle()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Listening timeline",
                style = sectionTitleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = timelineSupportingCopy(selectedMetric = selectedMetric, range = range),
                style = MaterialTheme.typography.bodyMedium, // Increased size
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            TimelineMetric.entries.forEach { metric ->
                val isSelected = metric == selectedMetric
                FilterChip(
                    selected = isSelected,
                    onClick = { onMetricSelected(metric) },
                    label = {
                        Text(
                            text = metric.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape, // Fully rounded
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        if (!hasTimeline) {
            StatsEmptyState(
                icon = Icons.Outlined.PlayCircleOutline,
                title = "No listening data yet",
                subtitle = "Press play to start building your listening timeline"
            )
        } else {
            val cardColor = when (range) {
                StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                StatsTimeRange.ALL -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.40f)
            }
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = cardColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 0.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = when (range) {
                                    StatsTimeRange.DAY -> "Daily rhythm"
                                    StatsTimeRange.WEEK -> "Weekly rhythm"
                                    StatsTimeRange.MONTH -> "Monthly rhythm"
                                    StatsTimeRange.YEAR -> "Year at a glance"
                                    StatsTimeRange.ALL -> "All-time progression"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = when (range) {
                                    StatsTimeRange.DAY -> "Grouped into 4-hour segments"
                                    StatsTimeRange.WEEK -> "Grouped by day of week"
                                    StatsTimeRange.MONTH -> "Grouped by week of month"
                                    StatsTimeRange.YEAR -> "Grouped by month"
                                    StatsTimeRange.ALL -> "Grouped by year"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TimelineMetricBadge(metric = selectedMetric)
                    }

                    TimelineBarChart(
                        entries = timeline,
                        metric = selectedMetric,
                        range = range
                    )
                }
            }

            summary?.peakTimeline?.let { peak ->
                HighlightRow(
                    title = "Peak segment",
                    value = formatTimelineLabelForRange(peak.label, range),
                    supporting = when (selectedMetric) {
                        TimelineMetric.ListeningTime -> formatListeningDurationCompact(peak.totalDurationMs)
                        TimelineMetric.PlayCount -> "${peak.playCount} plays"
                        TimelineMetric.AverageSession -> {
                            val average = if (peak.playCount > 0) peak.totalDurationMs / peak.playCount else 0L
                            formatListeningDurationCompact(average)
                        }
                    },
                    icon = Icons.Outlined.AutoGraph
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryMetricsSection(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    selectedDimension: CategoryDimension,
    onDimensionSelected: (CategoryDimension) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = categoryPaletteFor(selectedDimension)
    val sectionTitleStyle = rememberStatsSectionTitleStyle()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Top categories",
                style = sectionTitleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Compare how you listen across genres, artists, albums, and songs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            CategoryDimension.entries.reversed().forEach { dimension ->
                val isSelected = dimension == selectedDimension
                val chipPalette = categoryPaletteFor(dimension)
                FilterChip(
                    selected = isSelected,
                    onClick = { onDimensionSelected(dimension) },
                    label = {
                        Text(
                            text = dimension.displayName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    },
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = chipPalette.accentColor,
                        selectedLabelColor = chipPalette.accentOnColor,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        borderColor = Color.Transparent,
                        selectedBorderColor = Color.Transparent,
                        enabled = true,
                        selected = isSelected
                    )
                )
            }
        }

        val entries = remember(summary, selectedDimension) {
            val base = when (selectedDimension) {
                CategoryDimension.Genre -> summary?.topGenres.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.genre,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueArtists} artists"
                    )
                }

                CategoryDimension.Artist -> summary?.topArtists.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.artist,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueSongs} tracks"
                    )
                }

                CategoryDimension.Album -> summary?.topAlbums.orEmpty().map {
                    CategoryMetricEntry(
                        label = it.album,
                        durationMs = it.totalDurationMs,
                        supporting = "${it.playCount} plays • ${it.uniqueSongs} tracks"
                    )
                }

                CategoryDimension.Song -> summary?.topSongs.orEmpty().map {
                    val supportingParts = buildList {
                        add("${it.playCount} plays")
                        if (it.artist.isNotBlank()) {
                            add(it.artist)
                        }
                    }
                    CategoryMetricEntry(
                        label = it.title,
                        durationMs = it.totalDurationMs,
                        supporting = supportingParts.joinToString(separator = " • ")
                    )
                }
            }
            base.filter { it.durationMs > 0L }
        }

        if (entries.isEmpty()) {
            StatsEmptyState(
                icon = Icons.Outlined.MusicNote,
                title = "No category data yet",
                subtitle = "Press play to surface your listening highlights"
            )
        } else {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = palette.containerColor)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = selectedDimension.cardTitle,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = palette.contentColor
                    )
                    CategoryHorizontalBarChart(entries = entries, palette = palette)
                }
            }
        }
    }
}

@Composable
private fun CategoryHorizontalBarChart(
    entries: List<CategoryMetricEntry>,
    palette: CategoryChartPalette,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val maxDuration = entries.maxOf { it.durationMs }.coerceAtLeast(1L)
    val metricStyle = rememberStatsMetricValueStyle(compact = false)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        entries.forEachIndexed { index, entry ->
            val progress = (entry.durationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f)
            val isTop = index == 0
            val rowColor = if (isTop) {
                palette.accentColor.copy(alpha = 0.16f)
            } else {
                palette.contentColor.copy(alpha = 0.06f)
            }
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = rowColor
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryRankBadge(
                            rank = index + 1,
                            accentColor = palette.accentColor,
                            accentOnColor = palette.accentOnColor,
                            highlighted = isTop
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = palette.contentColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (entry.supporting.isNotBlank()) {
                                Text(
                                    text = entry.supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = palette.contentColor.copy(alpha = 0.76f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        Text(
                            text = formatListeningDurationCompact(entry.durationMs),
                            style = metricStyle,
                            fontWeight = if (isTop) FontWeight.SemiBold else FontWeight.Medium,
                            color = palette.contentColor
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = if (isTop) palette.accentColor else palette.accentColor.copy(alpha = 0.74f),
                        trackColor = palette.contentColor.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRankBadge(
    rank: Int,
    accentColor: Color,
    accentOnColor: Color,
    highlighted: Boolean
) {
    val metricStyle = rememberStatsMetricValueStyle(compact = false)
    val containerColor = if (highlighted) {
        accentColor
    } else {
        accentColor.copy(alpha = 0.24f)
    }
    val contentColor = if (highlighted) {
        accentOnColor
    } else {
        accentColor
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        contentColor = contentColor
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rank.toString(),
                style = metricStyle,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TimelineMetricBadge(
    metric: TimelineMetric,
    modifier: Modifier = Modifier
) {
    val (containerColor, contentColor) = when (metric) {
        TimelineMetric.ListeningTime -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        TimelineMetric.PlayCount -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        TimelineMetric.AverageSession -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = metric.displayName,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun TimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return
    val spec = timelineChartSpecFor(range = range, entryCount = entries.size)

    when (spec.layout) {
        TimelineChartLayout.Vertical -> VerticalTimelineBarChart(
            entries = entries,
            metric = metric,
            range = range,
            spec = spec,
            modifier = modifier
        )

        TimelineChartLayout.Horizontal -> HorizontalTimelineBarChart(
            entries = entries,
            metric = metric,
            range = range,
            spec = spec,
            modifier = modifier
        )
    }
}

@Composable
private fun VerticalTimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    spec: TimelineChartSpec,
    modifier: Modifier = Modifier
) {
    val maxMetricValue = entries.maxOfOrNull { metric.extractValue(it) }?.coerceAtLeast(0.0) ?: 0.0
    val hasNonZeroValues = maxMetricValue > 0.0
    val axisLabelStyle = rememberStatsAxisLabelStyle(range)
    val metricValueStyle = rememberStatsMetricValueStyle(compact = true)

    val highlightColor = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primary
        StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondary
        StatsTimeRange.ALL -> MaterialTheme.colorScheme.tertiary
    }
    val regularColor = highlightColor.copy(alpha = 0.72f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val spacing = 10.dp
            val itemCount = entries.size.coerceAtLeast(1)
            val innerWidth = (maxWidth - (spec.horizontalContentPadding * 2)).coerceAtLeast(0.dp)
            val spacingTotal = spacing * (itemCount - 1).coerceAtLeast(0)
            val minimumChartWidth = (spec.minItemWidth * itemCount) + spacingTotal
            val needsHorizontalScroll = minimumChartWidth > innerWidth || entries.size > spec.maxVisibleItems
            val fittedItemWidth = ((innerWidth - spacingTotal) / itemCount).coerceIn(spec.minItemWidth, spec.maxItemWidth)
            val itemWidth = if (needsHorizontalScroll) spec.minItemWidth else fittedItemWidth

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(spec.chartHeight),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalAlignment = Alignment.Bottom,
                    userScrollEnabled = needsHorizontalScroll,
                    contentPadding = PaddingValues(horizontal = spec.horizontalContentPadding)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { index, entry -> "${entry.label}-$index" }
                    ) { _, entry ->
                        val value = metric.extractValue(entry).coerceAtLeast(0.0)
                        val progress = if (maxMetricValue > 0.0) (value / maxMetricValue).toFloat().coerceIn(0f, 1f) else 0f
                        val isPeak = hasNonZeroValues && (maxMetricValue - value) <= maxMetricValue * 0.01
                        val label = formatTimelineLabelForRange(entry.label, range)

                        Column(
                            modifier = Modifier.width(itemWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = metric.formatValue(entry),
                                style = metricValueStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(trackColor),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(if (progress == 0f) 0f else progress)
                                        .clip(CircleShape)
                                        .background(if (isPeak) highlightColor else regularColor)
                                )
                            }
                            Text(
                                text = label,
                                style = axisLabelStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = spec.labelMaxLines,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HorizontalTimelineBarChart(
    entries: List<PlaybackStatsRepository.TimelineEntry>,
    metric: TimelineMetric,
    range: StatsTimeRange,
    spec: TimelineChartSpec,
    modifier: Modifier = Modifier
) {
    val maxMetricValue = entries.maxOfOrNull { metric.extractValue(it) }?.coerceAtLeast(0.0) ?: 0.0
    val hasNonZeroValues = maxMetricValue > 0.0
    val axisLabelStyle = rememberStatsAxisLabelStyle(range)
    val metricValueStyle = rememberStatsMetricValueStyle(compact = true)
    val highlightColor = when (range) {
        StatsTimeRange.DAY, StatsTimeRange.WEEK -> MaterialTheme.colorScheme.primary
        StatsTimeRange.MONTH, StatsTimeRange.YEAR -> MaterialTheme.colorScheme.secondary
        StatsTimeRange.ALL -> MaterialTheme.colorScheme.tertiary
    }
    val regularColor = highlightColor.copy(alpha = 0.72f)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val edgeContentPadding = spec.horizontalContentPadding

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        entries.forEach { entry ->
            val value = metric.extractValue(entry).coerceAtLeast(0.0)
            val progress = if (maxMetricValue > 0.0) (value / maxMetricValue).toFloat().coerceIn(0f, 1f) else 0f
            val isPeak = hasNonZeroValues && (maxMetricValue - value) <= maxMetricValue * 0.01
            val label = formatTimelineLabelForRange(entry.label, range)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(edgeContentPadding))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        modifier = Modifier.widthIn(min = 72.dp, max = 96.dp),
                        style = axisLabelStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(26.dp)
                            .clip(CircleShape)
                            .background(trackColor),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(if (progress == 0f) 0f else progress)
                                .clip(CircleShape)
                                .background(if (isPeak) highlightColor else regularColor)
                        )
                    }
                    Text(
                        text = metric.formatValue(entry),
                        modifier = Modifier.widthIn(min = 58.dp),
                        style = metricValueStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(edgeContentPadding))
            }
        }
    }
}

private fun timelineSupportingCopy(
    selectedMetric: TimelineMetric,
    range: StatsTimeRange
): String {
    val rangeCopy = when (range) {
        StatsTimeRange.DAY -> "Split into 4-hour windows to reveal your daily rhythm."
        StatsTimeRange.WEEK -> "Daily bars make week-to-week habits easy to compare."
        StatsTimeRange.MONTH -> "Weekly bars show how the month is trending."
        StatsTimeRange.YEAR -> "Monthly bars show seasonality across the year."
        StatsTimeRange.ALL -> "Yearly bars summarize your full history."
    }
    return "${selectedMetric.description} $rangeCopy"
}

private fun formatTimelineLabelForRange(
    rawLabel: String,
    range: StatsTimeRange
): String {
    val label = rawLabel.trim()
    if (label.isBlank()) return "—"
    return when (range) {
        StatsTimeRange.DAY -> {
            val match = Regex("(?i)^(\\d{1,2})(am|pm)$").matchEntire(label)
            if (match != null) {
                "${match.groupValues[1]} ${match.groupValues[2].uppercase(Locale.US)}"
            } else {
                label
            }
        }

        StatsTimeRange.YEAR -> monthThreeLetters(label)
        else -> label
    }
}

private fun monthThreeLetters(label: String): String {
    val cleaned = label.trim()
    if (cleaned.isEmpty()) return "—"
    return cleaned.take(3)
}

private fun timelineChartSpecFor(
    range: StatsTimeRange,
    entryCount: Int
): TimelineChartSpec {
    return when (range) {
        StatsTimeRange.DAY -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 52.dp,
            maxItemWidth = 72.dp,
            maxVisibleItems = 5,
            chartHeight = 224.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 20.dp
        )

        StatsTimeRange.WEEK -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 50.dp,
            maxItemWidth = 68.dp,
            maxVisibleItems = 6,
            chartHeight = 224.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 20.dp
        )

        StatsTimeRange.MONTH -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 62.dp,
            maxItemWidth = 82.dp,
            maxVisibleItems = 4,
            chartHeight = 232.dp,
            labelMaxLines = 1,
            horizontalContentPadding = 16.dp
        )

        StatsTimeRange.YEAR -> TimelineChartSpec(
            layout = TimelineChartLayout.Vertical,
            minItemWidth = 56.dp,
            maxItemWidth = 68.dp,
            maxVisibleItems = 5,
            chartHeight = 236.dp,
            labelMaxLines = 2,
            horizontalContentPadding = 16.dp
        )

        StatsTimeRange.ALL -> {
            val isSparse = entryCount <= 4
            TimelineChartSpec(
                layout = TimelineChartLayout.Vertical,
                minItemWidth = if (isSparse) 62.dp else 56.dp,
                maxItemWidth = if (isSparse) 78.dp else 66.dp,
                maxVisibleItems = if (isSparse) 4 else 6,
                chartHeight = 228.dp,
                labelMaxLines = 1,
                horizontalContentPadding = 16.dp
            )
        }
    }
}

@Composable
private fun categoryPaletteFor(dimension: CategoryDimension): CategoryChartPalette {
    return when (dimension) {
        CategoryDimension.Genre -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            accentColor = MaterialTheme.colorScheme.tertiary,
            accentOnColor = MaterialTheme.colorScheme.onTertiary
        )

        CategoryDimension.Artist -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            accentColor = MaterialTheme.colorScheme.primary,
            accentOnColor = MaterialTheme.colorScheme.onPrimary
        )

        CategoryDimension.Album -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            accentColor = MaterialTheme.colorScheme.secondary,
            accentOnColor = MaterialTheme.colorScheme.onSecondary
        )

        CategoryDimension.Song -> CategoryChartPalette(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            accentColor = MaterialTheme.colorScheme.primary,
            accentOnColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

@Composable
private fun TopArtistsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    val supportingColor = contentColor.copy(alpha = 0.76f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Consistent with Hero
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Top artists",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = contentColor
            )
            val artists = summary?.topArtists.orEmpty()
            if (artists.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = "No top artists",
                    subtitle = "Keep listening and your favorite artists will show up here."
                )
            } else {
                val maxDuration = artists.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    artists.forEachIndexed { index, artistSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                ArtistAvatar(
                                    name = artistSummary.artist,
                                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                                    contentColor = MaterialTheme.colorScheme.secondary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${artistSummary.artist}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistSummary.playCount} plays • ${artistSummary.uniqueSongs} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = supportingColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(artistSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = supportingColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (artistSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondary,
                                trackColor = contentColor.copy(alpha = 0.18f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistAvatar(
    name: String,
    containerColor: Color,
    contentColor: Color
) {
    val initials = remember(name) {
        name.split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString(separator = "") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium,
            color = contentColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TopAlbumsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.tertiaryContainer
    val contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    val supportingColor = contentColor.copy(alpha = 0.76f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp), // Consistent with Hero
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "Top albums",
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = contentColor
            )
            val albums = summary?.topAlbums.orEmpty()
            if (albums.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Outlined.Album,
                    title = "No top albums",
                    subtitle = "Albums you revisit often will appear here."
                )
            } else {
                val maxDuration = albums.maxOf { it.totalDurationMs }.coerceAtLeast(1L)
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    albums.forEachIndexed { index, albumSummary ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SmartImage(
                                    model = albumSummary.albumArtUri,
                                    contentDescription = albumSummary.album,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${index + 1}. ${albumSummary.album}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = contentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${albumSummary.playCount} plays • ${albumSummary.uniqueSongs} tracks",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = supportingColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatListeningDurationCompact(albumSummary.totalDurationMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = supportingColor
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (albumSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.tertiary,
                                trackColor = contentColor.copy(alpha = 0.18f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongStatsCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            val songs = summary?.songs.orEmpty()
            var showAll by rememberSaveable(songs) { mutableStateOf(songs.size <= 8) }
            val displayedSongs = remember(songs, showAll) {
                if (showAll || songs.size <= 8) songs else songs.take(8)
            }
            val maxDuration = songs.maxOfOrNull { it.totalDurationMs }?.coerceAtLeast(1L) ?: 1L
            val positions = remember(songs) { songs.mapIndexed { index, song -> song.songId to index }.toMap() }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "Tracks in this range",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = "Most played tracks for the selected time range.",
                    style = MaterialTheme.typography.bodySmall,
                    color = supportingColor
                )
            }

            if (songs.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Outlined.MusicNote,
                    title = "No top tracks",
                    subtitle = "Listen to your favorites to see them highlighted here."
                )
            } else {
                Column(
                    modifier = Modifier.animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    displayedSongs.forEach { songSummary ->
                        val position = positions[songSummary.songId] ?: songs.indexOf(songSummary)
                        val accentColor = when {
                            position == 0 -> MaterialTheme.colorScheme.primary
                            position < 3 -> MaterialTheme.colorScheme.secondary
                            else -> MaterialTheme.colorScheme.tertiary
                        }
                        val accentOnColor = when {
                            position == 0 -> MaterialTheme.colorScheme.onPrimary
                            position < 3 -> MaterialTheme.colorScheme.onSecondary
                            else -> MaterialTheme.colorScheme.onTertiary
                        }
                        val rowContainerColor = when {
                            position == 0 -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            position < 3 -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f)
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = rowContainerColor
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CategoryRankBadge(
                                        rank = position + 1,
                                        accentColor = accentColor,
                                        accentOnColor = accentOnColor,
                                        highlighted = position == 0
                                    )
                                    SmartImage(
                                        model = songSummary.albumArtUri,
                                        contentDescription = songSummary.title,
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(14.dp)),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = songSummary.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = contentColor
                                        )
                                        Text(
                                            text = songSummary.artist,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = supportingColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${songSummary.playCount} plays",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = supportingColor
                                        )
                                    }
                                    Text(
                                        text = formatListeningDurationCompact(songSummary.totalDurationMs),
                                        style = rememberStatsMetricValueStyle(compact = true),
                                        color = contentColor
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { (songSummary.totalDurationMs.toFloat() / maxDuration.toFloat()).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(7.dp)
                                        .clip(CircleShape),
                                    color = accentColor,
                                    trackColor = supportingColor.copy(alpha = 0.20f)
                                )
                            }
                        }
                    }
                }

                if (songs.size > 8) {
                    TextButton(
                        onClick = { showAll = !showAll },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Text(
                            text = if (showAll) "Collapse tracks" else "Show all tracks",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackConcentrationCard(
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    modifier: Modifier = Modifier
) {
    val songs = summary?.songs.orEmpty()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = "Track concentration",
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    modifier = Modifier.padding(start = 6.dp),
                    text = "How your listening time is distributed across your top tracks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                )
            }

            if (songs.isEmpty()) {
                StatsEmptyState(
                    icon = Icons.Outlined.AutoGraph,
                    title = "No concentration data yet",
                    subtitle = "Play more tracks to see how focused your listening is."
                )
            } else {
                val totalDuration = songs.sumOf { it.totalDurationMs }.coerceAtLeast(1L)
                val topOneDuration = songs.firstOrNull()?.totalDurationMs ?: 0L
                val topThreeDuration = songs.take(3).sumOf { it.totalDurationMs }
                val topThreeShare = (topThreeDuration.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                val averagePlaysPerTrack = (
                    (summary?.totalPlayCount ?: songs.sumOf { it.playCount }).toFloat() /
                        songs.size.coerceAtLeast(1).toFloat()
                    )
                val topOneColor = MaterialTheme.colorScheme.primary
                val topTwoThreeColor = MaterialTheme.colorScheme.secondary
                val othersColor = MaterialTheme.colorScheme.tertiary
                val slices = buildList {
                    if (topOneDuration > 0L) {
                        add(
                            TrackShareSlice(
                                label = "Top 1",
                                durationMs = topOneDuration,
                                color = topOneColor
                            )
                        )
                    }
                    val topTwoToThree = (topThreeDuration - topOneDuration).coerceAtLeast(0L)
                    if (topTwoToThree > 0L) {
                        add(
                            TrackShareSlice(
                                label = "Top 2-3",
                                durationMs = topTwoToThree,
                                color = topTwoThreeColor
                            )
                        )
                    }
                    val remaining = (totalDuration - topThreeDuration).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        add(
                            TrackShareSlice(
                                label = "Others",
                                durationMs = remaining,
                                color = othersColor
                            )
                        )
                    }
                }

                TrackDistributionOverview(
                    slices = slices,
                    totalDurationMs = totalDuration,
                    topThreeShare = topThreeShare,
                    averagePlaysPerTrack = averagePlaysPerTrack,
                    uniqueTracks = songs.size
                )
            }
        }
    }
}

@Composable
private fun TrackDistributionOverview(
    slices: List<TrackShareSlice>,
    totalDurationMs: Long,
    topThreeShare: Float,
    averagePlaysPerTrack: Float,
    uniqueTracks: Int,
    modifier: Modifier = Modifier
) {
    val metricStyle = rememberStatsMetricValueStyle(compact = false)
    val onColor = MaterialTheme.colorScheme.onSurface
    val supporting = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest
        ) {
            BoxWithConstraints(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                val compact = maxWidth < 420.dp
                if (compact) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TrackDistributionDonut(
                            slices = slices,
                            totalDurationMs = totalDurationMs,
                            topThreeShare = topThreeShare,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        TrackDistributionStats(
                            metricStyle = metricStyle,
                            topThreeShare = topThreeShare,
                            averagePlaysPerTrack = averagePlaysPerTrack,
                            uniqueTracks = uniqueTracks
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TrackDistributionDonut(
                            slices = slices,
                            totalDurationMs = totalDurationMs,
                            topThreeShare = topThreeShare
                        )
                        TrackDistributionStats(
                            modifier = Modifier.weight(1f),
                            metricStyle = metricStyle,
                            topThreeShare = topThreeShare,
                            averagePlaysPerTrack = averagePlaysPerTrack,
                            uniqueTracks = uniqueTracks
                        )
                    }
                }
            }
        }

        if (slices.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slices.forEach { slice ->
                    val share = (slice.durationMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(slice.color)
                            )
                            Text(
                                text = slice.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = supporting,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${(share * 100f).roundToInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = onColor
                            )
                            Text(
                                text = formatListeningDurationCompact(slice.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = supporting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackDistributionStats(
    metricStyle: TextStyle,
    topThreeShare: Float,
    averagePlaysPerTrack: Float,
    uniqueTracks: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Listening concentration",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Top 3 tracks account for ${(topThreeShare * 100f).roundToInt()}% of your listening time.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = String.format(Locale.US, "%.1f", averagePlaysPerTrack),
                        style = metricStyle,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Avg plays/track",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f)
                    )
                }
            }
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.52f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = uniqueTracks.toString(),
                        style = metricStyle,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Unique tracks",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackDistributionDonut(
    slices: List<TrackShareSlice>,
    totalDurationMs: Long,
    topThreeShare: Float,
    modifier: Modifier = Modifier
) {
    val strokePx = with(LocalDensity.current) { 18.dp.toPx() }
    val visualGapPx = with(LocalDensity.current) { 4.dp.toPx() }
    val trackColor = Color.Transparent
    Box(
        modifier = modifier.size(158.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            val safeTotal = totalDurationMs.coerceAtLeast(1L).toFloat()
            val arcInset = (strokePx / 2f) + 1f
            val diameter = (size.minDimension - (arcInset * 2f)).coerceAtLeast(0f)
            val radius = (diameter - strokePx).coerceAtLeast(0f) / 2f
            val gapAngle = if (slices.size > 1 && radius > 0f) {
                // Round caps extend ~stroke/2 at both ends, so centerline gap must include that.
                val centerlineGapPx = visualGapPx + strokePx
                ((centerlineGapPx / radius) * (180f / PI.toFloat())).coerceIn(1f, 24f)
            } else {
                0f
            }
            val arcTopLeft = Offset(
                x = (size.width - diameter) / 2f,
                y = (size.height - diameter) / 2f
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokePx,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            )

            slices.forEach { slice ->
                val rawSweep = (slice.durationMs.toFloat() / safeTotal) * 360f
                val sweep = (rawSweep - gapAngle).coerceAtLeast(0f)
                if (sweep > 0.2f) {
                    drawArc(
                        color = slice.color,
                        startAngle = startAngle + (gapAngle / 2f),
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = strokePx,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                startAngle += rawSweep
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "${(topThreeShare * 100f).roundToInt()}%",
                style = rememberStatsMetricValueStyle(compact = false).copy(
                    fontSize = 24.sp,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Top 3 share",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
