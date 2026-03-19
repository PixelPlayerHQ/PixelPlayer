package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.theveloper.pixelplay.data.WearAudioOutputRoute
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.theveloper.pixelplay.data.WearOutputTarget
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.outputRouteIcon
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.screenBackgroundColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerHighestColor
import com.theveloper.pixelplay.presentation.viewmodel.WearPlayerViewModel
import com.theveloper.pixelplay.shared.WearPlayerState
import com.theveloper.pixelplay.shared.WearVolumeState
import kotlinx.coroutines.delay

@Composable
fun OutputScreen(
    viewModel: WearPlayerViewModel = hiltViewModel(),
) {
    val outputTarget by viewModel.outputTarget.collectAsState()
    val isPhoneConnected by viewModel.isPhoneConnected.collectAsState()
    val canCurrentSongPlayOnWatch by viewModel.canCurrentSongPlayOnWatch.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val phoneVolumeState by viewModel.phoneVolumeState.collectAsState()
    val watchAudioRoutes by viewModel.watchAudioRoutes.collectAsState()
    val watchVolumeState by viewModel.watchVolumeState.collectAsState()
    val palette = LocalWearPalette.current
    val columnState = rememberResponsiveColumnState()
    val phoneRouteType = phoneVolumeState.routeType.ifBlank { WearVolumeState.ROUTE_TYPE_PHONE }
    val phoneRouteName = phoneVolumeState.routeName.ifBlank { "Phone" }
    val canSwitchToWatch = canCurrentSongPlayOnWatch || outputTarget == WearOutputTarget.WATCH

    DisposableEffect(viewModel) {
        viewModel.setWatchRouteDiscoveryEnabled(true)
        onDispose {
            viewModel.setWatchRouteDiscoveryEnabled(false)
        }
    }
    LaunchedEffect(viewModel) {
        while (true) {
            viewModel.refreshWatchAudioState()
            delay(700L)
        }
    }

    val background = palette.screenBackgroundColor()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(18.dp)) }

            item {
                Text(
                    text = "Device",
                    style = MaterialTheme.typography.title2,
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                Text(
                    text = "Available outputs",
                    style = MaterialTheme.typography.caption2,
                    color = palette.textSecondary.copy(alpha = 0.86f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                OutputTargetChip(
                    label = "Phone",
                    subtitle = when {
                        !isPhoneConnected -> "Phone disconnected"
                        outputTarget == WearOutputTarget.PHONE -> "Controlling · $phoneRouteName"
                        playerState.songId.isBlank() -> "Switch to phone playback"
                        else -> "Switch current song to $phoneRouteName"
                    },
                    icon = outputRouteIcon(phoneRouteType),
                    selected = outputTarget == WearOutputTarget.PHONE,
                    enabled = isPhoneConnected,
                    onClick = { viewModel.selectOutput(WearOutputTarget.PHONE) },
                )
            }

            if (watchAudioRoutes.isEmpty()) {
                item {
                    OutputTargetChip(
                        label = watchVolumeState.routeName.ifBlank { "Watch speaker" },
                        subtitle = when {
                            outputTarget == WearOutputTarget.WATCH && playerState.isPlaying -> "Playing on watch"
                            outputTarget == WearOutputTarget.WATCH -> "Watch selected"
                            canSwitchToWatch -> "Switch current song to watch"
                            playerState.songId.isBlank() -> "Play a song first"
                            else -> "Save this song on watch first"
                        },
                        icon = Icons.Rounded.Watch,
                        selected = outputTarget == WearOutputTarget.WATCH,
                        enabled = canSwitchToWatch,
                        onClick = { viewModel.selectOutput(WearOutputTarget.WATCH) },
                    )
                }
            } else {
                watchAudioRoutes.forEach { route ->
                    item {
                        OutputTargetChip(
                            label = route.name,
                            subtitle = watchOutputSubtitle(
                                route = route,
                                outputTarget = outputTarget,
                                playerState = playerState,
                                canSwitchToWatch = canSwitchToWatch,
                            ),
                            icon = outputRouteIcon(route.routeType),
                            selected = outputTarget == WearOutputTarget.WATCH && route.isActive,
                            enabled = canSwitchToWatch,
                            onClick = { viewModel.selectWatchOutput(route.id) },
                        )
                    }
                }
            }

            item {
                OutputTargetChip(
                    label = "Bluetooth devices",
                    subtitle = if (watchAudioRoutes.any { it.isBluetooth }) {
                        "Find or connect another headset"
                    } else {
                        "Connect headphones to watch"
                    },
                    icon = Icons.Rounded.Bluetooth,
                    selected = false,
                    enabled = true,
                    onClick = viewModel::openWatchOutputPicker,
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

private fun watchOutputSubtitle(
    route: WearAudioOutputRoute,
    outputTarget: WearOutputTarget,
    playerState: WearPlayerState,
    canSwitchToWatch: Boolean,
): String {
    return when {
        route.isActive && outputTarget == WearOutputTarget.WATCH && playerState.isPlaying ->
            "Playing on watch"
        route.isActive && outputTarget == WearOutputTarget.WATCH ->
            "Selected on watch"
        route.isConnecting ->
            "Connecting"
        route.isConnected && outputTarget == WearOutputTarget.WATCH ->
            "Connected to watch"
        !canSwitchToWatch && playerState.songId.isBlank() ->
            "Play a song first"
        !canSwitchToWatch ->
            "Save this song on watch first"
        route.isConnected ->
            "Switch current song to ${route.name}"
        else ->
            "Connect and play on watch"
    }
}

@Composable
private fun OutputTargetChip(
    label: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    val containerColor = when {
        !enabled -> palette.surfaceContainerHighestColor()
        selected -> palette.controlContainer.copy(alpha = 0.95f)
        else -> palette.surfaceContainerColor()
    }
    val contentColor = when {
        !enabled -> palette.textSecondary
        selected -> palette.controlContent
        else -> palette.textPrimary
    }
    val secondaryColor = when {
        !enabled -> palette.textSecondary.copy(alpha = 0.82f)
        selected -> palette.controlContent.copy(alpha = 0.76f)
        else -> palette.textSecondary.copy(alpha = 0.80f)
    }

    Chip(
        label = {
            Text(
                text = label,
                color = contentColor,
            )
        },
        secondaryLabel = {
            Text(
                text = subtitle,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        enabled = enabled,
        colors = ChipDefaults.chipColors(
            backgroundColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
