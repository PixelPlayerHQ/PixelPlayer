@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.telegram.dashboard

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.database.TelegramChannelEntity
import com.theveloper.pixelplay.data.database.TelegramTopicEntity
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.NoInternetScreen
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun TelegramDashboardScreen(
    onAddChannel: () -> Unit,
    onBack: () -> Unit,
    viewModel: TelegramDashboardViewModel = hiltViewModel()
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val isRefreshingId by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val topicsMap by viewModel.topicsMap.collectAsStateWithLifecycle()
    val expandedChannels by viewModel.expandedChannels.collectAsStateWithLifecycle()
    var selectedChannelForActions by remember { mutableStateOf<TelegramChannelEntity?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val lazyListState = rememberLazyListState()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    val gradientColors = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    )

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    // Load cached topics for all channels on first composition
    LaunchedEffect(channels) {
        channels.forEach { channel ->
            viewModel.loadTopicsForChannel(channel.chatId)
        }
    }

    if (!isOnline) {
        NoInternetScreen(onRetry = viewModel::refreshChannels)
        return
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 176.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value, maxTopBarHeightPx) {
        collapseFraction = 1f - (
                (topBarHeight.value - minTopBarHeightPx) /
                        (maxTopBarHeightPx - minTopBarHeightPx)
                ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: NestedScrollSource): androidx.compose.ui.geometry.Offset {
                val delta = available.y
                val scrollingDown = delta < 0

                if (!scrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return androidx.compose.ui.geometry.Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsume = !(scrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsume) androidx.compose.ui.geometry.Offset(0f, consumed) else androidx.compose.ui.geometry.Offset.Zero
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
    val fabBottomPadding = navigationBottomPadding + 16.dp
    val collapsedTopBarAlpha = (collapseFraction / 0.6f).coerceIn(0f, 1f)
    val topBarContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(
        alpha = collapsedTopBarAlpha
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradientColors))
            .nestedScroll(nestedScrollConnection)
    ) {
        Crossfade(targetState = channels.isEmpty(), label = "telegramContentState") { isEmpty ->
            if (isEmpty) {
                ExpressiveEmptyState(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxSize()
                    //.padding(top = currentTopBarHeightDp + 8.dp)
                    ,
                    onAdd = onAddChannel
                )
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(
                        top = currentTopBarHeightDp + 8.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = fabBottomPadding + 110.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(
                        items = channels,
                        key = { _, channel -> channel.chatId }
                    ) { _, channel ->
                        val channelTopics = topicsMap[channel.chatId] ?: emptyList()
                        val isExpanded = channel.chatId in expandedChannels

                        ExpressiveChannelItem(
                            channel = channel,
                            isSyncing = isRefreshingId == channel.chatId,
                            topics = channelTopics,
                            isExpanded = isExpanded,
                            onSync = { viewModel.refreshChannel(channel) },
                            onOpenActions = { selectedChannelForActions = channel },
                            onToggleExpand = { viewModel.toggleChannelExpanded(channel.chatId) }
                        )
                    }
                }
            }
        }

        CollapsibleCommonTopBar(
            title = "Telegram Channels",
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBack,
            containerColor = topBarContainerColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .zIndex(5f)
        )

        val fabScale by animateFloatAsState(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "telegramFabScale"
        )

        if (channels.isNotEmpty()) {
            MediumExtendedFloatingActionButton(
                onClick = onAddChannel,
                text = {
                    Text(
                        "Add Channel",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                expanded = channels.isNotEmpty(),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomPadding)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                    },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = fabBottomPadding + 72.dp
                )
        )

        selectedChannelForActions?.let { selectedChannel ->
            ChannelActionsBottomSheet(
                channel = selectedChannel,
                isSyncing = isRefreshingId == selectedChannel.chatId,
                onDismiss = { selectedChannelForActions = null },
                onSync = {
                    selectedChannelForActions = null
                    viewModel.refreshChannel(selectedChannel)
                },
                onDelete = {
                    selectedChannelForActions = null
                    viewModel.removeChannel(selectedChannel.chatId)
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExpressiveChannelItem(
    channel: TelegramChannelEntity,
    isSyncing: Boolean,
    topics: List<TelegramTopicEntity>,
    isExpanded: Boolean,
    onSync: () -> Unit,
    onOpenActions: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 28.dp,
        cornerRadiusTL = 28.dp,
        cornerRadiusBR = 28.dp,
        cornerRadiusBL = 28.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    val imageShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp,
        cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp,
        cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    val usernameLabel = remember(channel.username) {
        channel.username
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("@")) it else "@$it" }
    }
    val lastSyncLabel = remember(channel.lastSyncTime) { formatLastSyncLabel(channel.lastSyncTime) }

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Channel header row ──────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(imageShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.photoPath.isNullOrEmpty()) {
                        AsyncImage(
                            model = File(channel.photoPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = channel.title.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.size(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = channel.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = usernameLabel ?: "Public Telegram channel",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── Meta pills ──────────────────────────────────────────────
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChannelMetaPill(
                    icon = Icons.Rounded.MusicNote,
                    label = "${channel.songCount} songs"
                )
                ChannelMetaPill(
                    icon = Icons.Rounded.AccessTime,
                    label = lastSyncLabel
                )
                if (topics.isNotEmpty()) {
                    ChannelMetaPill(
                        icon = Icons.Rounded.Topic,
                        label = "${topics.size} topics"
                    )
                }
            }

            // ── Action buttons ──────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onSync,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Syncing")
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Sync now")
                    }
                }

                // Show expand/collapse button only when topics exist
                if (topics.isNotEmpty()) {
                    FilledTonalIconButton(
                        onClick = onToggleExpand,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse topics" else "Show topics"
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = onOpenActions,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Channel options"
                    )
                }
            }

            // ── Expandable topics list ──────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded && topics.isNotEmpty(),
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Topics",
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    topics.forEach { topic ->
                        TopicRow(topic = topic)
                    }
                }
            }
        }
    }
}

// ─── Single topic row ─────────────────────────────────────────────────────────

@Composable
private fun TopicRow(topic: TelegramTopicEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
//            Text(
//                text = topic.iconEmoji?.takeIf { it.isNotBlank() } ?: "🎵",
//                style = MaterialTheme.typography.bodyMedium,
//                color = MaterialTheme.colorScheme.primary,
//            )
            Icon(
                imageVector = Icons.Rounded.Topic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = topic.name,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${topic.songCount} songs",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

//            if (topic.songCount > 0) {
//                Text(
//                    text = "${topic.songCount} songs",
//                    style = MaterialTheme.typography.labelSmall,
//                    fontFamily = GoogleSansRounded,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            } else {
//                Text(
//                    text = "Sync to load songs",
//                    style = MaterialTheme.typography.labelSmall,
//                    fontFamily = GoogleSansRounded,
//                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
//                )
//            }
        }

//        Icon(
//            imageVector = Icons.Rounded.ChevronRight,
//            contentDescription = null,
//            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
//            modifier = Modifier.size(18.dp)
//        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelActionsBottomSheet(
    channel: TelegramChannelEntity,
    isSyncing: Boolean,
    onDismiss: () -> Unit,
    onSync: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val usernameLabel = remember(channel.username) {
        channel.username
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.startsWith("@")) it else "@$it" }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
//            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = channel.title,
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = usernameLabel ?: "Public Telegram channel",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(14.dp))
            ChannelActionCard(
                title = if (isSyncing) "Syncing channel" else "Sync now",
                subtitle = if (isSyncing) {
                    "Updating songs from Telegram"
                } else {
                    "Fetch latest songs from this channel"
                },
                icon = Icons.Rounded.Sync,
                onClick = onSync,
                enabled = !isSyncing,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailingContent = if (isSyncing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else {
                    null
                }
            )
            Spacer(modifier = Modifier.height(14.dp))
            ChannelActionCard(
                title = "Remove channel",
                subtitle = "Stop syncing and remove cached songs",
                icon = Icons.Rounded.Delete,
                onClick = onDelete,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelActionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean = true,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 18.dp,
        cornerRadiusTL = 18.dp,
        cornerRadiusBR = 18.dp,
        cornerRadiusBL = 18.dp,
        smoothnessAsPercentTR = 60,
        smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60,
        smoothnessAsPercentBL = 60
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
        color = containerColor,
        contentColor = contentColor,
        shape = shape
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.size(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = contentColor.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (trailingContent != null) {
                trailingContent()
            } else {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun ChannelMetaPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ExpressiveEmptyState(
    modifier: Modifier = Modifier,
    onAdd: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "No Channels Synced",
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Add public Telegram channels to sync\nyour music library",
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = GoogleSansRounded,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        FilledTonalButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Add channel")
        }
    }
}

private fun formatLastSyncLabel(lastSyncTime: Long): String {
    if (lastSyncTime <= 0L) return "Never synced"
    val relative = DateUtils.getRelativeTimeSpanString(
        lastSyncTime,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    )
    return "Synced $relative"
}