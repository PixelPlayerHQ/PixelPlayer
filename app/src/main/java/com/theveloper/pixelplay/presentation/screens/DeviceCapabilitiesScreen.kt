package com.theveloper.pixelplay.presentation.screens

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.viewmodel.CodecInfo
import com.theveloper.pixelplay.presentation.viewmodel.DeviceCapabilitiesViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun DeviceCapabilitiesScreen(
    navController: NavController,
    viewModel: DeviceCapabilitiesViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel // Kept for consistency if needed for player sheet handling
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    
    // Top Bar Logic (Reused Pattern)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 180.dp 

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
                val isScrollingDown = delta < 0

                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch { topBarHeight.snapTo(newHeight) }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }
    
    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx
            if (topBarHeight.value != targetValue) {
                coroutineScope.launch { topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    Box(modifier = Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        if (state.isLoading) {
             Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator()
             }
        } else {
            val supportedCodecs = state.audioCapabilities?.supportedCodecs.orEmpty()
             LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(
                    top = currentTopBarHeightDp + 8.dp,
                    start = 16.dp,
                    end = 16.dp,
                    bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                 // Device Info Section
                 item {
                     DeviceInfoExpressiveSection(deviceInfo = state.deviceInfo)
                 }
                 
                 // Audio Capabilities
                 item {
                     state.audioCapabilities?.let { audio ->
                         CapabilitySection(title = "Audio Output", icon = Icons.Rounded.Speaker) {
                             InfoRow("Sample Rate", "${audio.outputSampleRate} Hz")
                             InfoRow("Frames Per Buffer", "${audio.outputFramesPerBuffer}")
                             InfoRow("Low Latency Support", if (audio.isLowLatencySupported) "Yes" else "No")
                             InfoRow("Pro Audio Support", if (audio.isProAudioSupported) "Yes" else "No")
                         }
                     }
                 }
                 
                 // ExoPlayer Info
                 item {
                     state.exoPlayerInfo?.let { exo ->
                         CapabilitySection(title = "ExoPlayer Engine", icon = Icons.Rounded.Memory) {
                             InfoRow("Version", exo.version)
                             InfoRow("Active Renderers", exo.renderers)
                             InfoRow("Decoder Counters", exo.decoderCounters)
                         }
                     }
                 }

                 // Codecs Header
                 item {
                     Text(
                         text = "Supported Audio Codecs",
                         style = MaterialTheme.typography.titleLarge,
                         color = MaterialTheme.colorScheme.onSurface,
                         fontWeight = FontWeight.Bold,
                         modifier = Modifier.padding(top = 8.dp, bottom = 2.dp, start = 4.dp)
                     )
                 }

                 // Codec List
                 if (supportedCodecs.isNotEmpty()) {
                     item {
                         SegmentedCodecList(codecs = supportedCodecs)
                     }
                 }
             }
        }
        
        // Top Bar
        CollapsibleCommonTopBar(
            title = "Device Capabilities",
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = { navController.popBackStack() },
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp
        )
    }
}

@Composable
fun CapabilitySection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    val sectionShape = AbsoluteSmoothCornerShape(28.dp, 60)
    Card(
        shape = sectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(16.dp, 60),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))
//                HorizontalDivider(
//                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f)
//                )
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Surface(
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.44f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.56f)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
fun DeviceInfoExpressiveSection(deviceInfo: Map<String, String>) {
    val orderedEntries = remember(deviceInfo) { orderedDeviceInfoEntries(deviceInfo) }
    val heroEntries = orderedEntries.take(2)
    val detailEntries = orderedEntries.drop(2)
    val sectionShape = AbsoluteSmoothCornerShape(30.dp, 60)

    Card(
        shape = sectionShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.52f),
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.34f),
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = AbsoluteSmoothCornerShape(16.dp, 60),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.94f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Device Info",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                //HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.24f))
                if (heroEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        heroEntries.forEach { (label, value) ->
                            DeviceInfoHeroTile(
                                label = label,
                                value = value,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (heroEntries.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
                detailEntries.chunked(2).forEach { rowEntries ->
                    if (rowEntries.size == 2) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeviceInfoStatTile(
                                label = rowEntries[0].first,
                                value = rowEntries[0].second,
                                modifier = Modifier.weight(1f)
                            )
                            DeviceInfoStatTile(
                                label = rowEntries[1].first,
                                value = rowEntries[1].second,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        DeviceInfoStatTile(
                            label = rowEntries[0].first,
                            value = rowEntries[0].second,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceInfoHeroTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceInfoStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = AbsoluteSmoothCornerShape(14.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SegmentedCodecList(codecs: List<CodecInfo>) {
    val listShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(listShape),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        codecs.forEachIndexed { index, codec ->
            CodecCard(
                codec = codec,
                shape = settingsSegmentShape(
                    index = index,
                    count = codecs.size,
                    outerCorner = 18.dp,
                    innerCorner = 8.dp
                )
            )
        }
    }
}

@Composable
fun CodecCard(
    codec: CodecInfo,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.36f),
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = codec.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (codec.isHardwareAccelerated) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "HW Accelerated",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(4.dp).size(16.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = codec.supportedTypes.joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun settingsSegmentShape(
    index: Int,
    count: Int,
    outerCorner: Dp,
    innerCorner: Dp
): RoundedCornerShape {
    return when {
        count <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = innerCorner,
            bottomEnd = innerCorner
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = innerCorner,
            topEnd = innerCorner,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        else -> RoundedCornerShape(innerCorner)
    }
}

private fun orderedDeviceInfoEntries(deviceInfo: Map<String, String>): List<Pair<String, String>> {
    val preferredOrder = listOf(
        "Manufacturer",
        "Model",
        "Brand",
        "Device",
        "Android Version",
        "SDK Version",
        "Hardware"
    )
    val orderedEntries = mutableListOf<Pair<String, String>>()
    val seenKeys = mutableSetOf<String>()

    preferredOrder.forEach { key ->
        deviceInfo[key]?.let { value ->
            orderedEntries += key to value
            seenKeys += key
        }
    }

    deviceInfo.forEach { (key, value) ->
        if (key !in seenKeys) {
            orderedEntries += key to value
        }
    }
    return orderedEntries
}
