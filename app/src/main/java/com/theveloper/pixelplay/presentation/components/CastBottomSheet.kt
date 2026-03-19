package com.theveloper.pixelplay.presentation.components

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.Intent
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.mediarouter.media.MediaRouter
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.BluetoothAudioDeviceState
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import android.content.pm.PackageManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastBottomSheet(
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {}
) {
    val routes by playerViewModel.castRoutes.collectAsStateWithLifecycle()
    val selectedRoute by playerViewModel.selectedRoute.collectAsStateWithLifecycle()
    val routeVolume by playerViewModel.routeVolume.collectAsStateWithLifecycle()
    val isRefreshing by playerViewModel.isRefreshingRoutes.collectAsStateWithLifecycle()
    val isWifiEnabled by playerViewModel.isWifiEnabled.collectAsStateWithLifecycle()
    val isWifiRadioOn by playerViewModel.isWifiRadioOn.collectAsStateWithLifecycle()
    val wifiName by playerViewModel.wifiName.collectAsStateWithLifecycle()
    val isBluetoothEnabled by playerViewModel.isBluetoothEnabled.collectAsStateWithLifecycle()
    val bluetoothName by playerViewModel.bluetoothName.collectAsStateWithLifecycle()
    val bluetoothAudioDeviceStates by playerViewModel.bluetoothAudioDeviceStates.collectAsStateWithLifecycle()
    val isRemotePlaybackActive by playerViewModel.isRemotePlaybackActive.collectAsStateWithLifecycle()
    val isCastConnecting by playerViewModel.isCastConnecting.collectAsStateWithLifecycle()
    val trackVolume by playerViewModel.trackVolume.collectAsStateWithLifecycle()
    val isPlaying = playerViewModel.stablePlayerState.collectAsStateWithLifecycle().value.isPlaying
    val context = LocalContext.current

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    var missingPermissions by remember { mutableStateOf(missingCastPermissions(context, requiredPermissions)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    LaunchedEffect(Unit) {
        missingPermissions = missingCastPermissions(context, requiredPermissions)
        if (missingPermissions.isEmpty()) {
            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
        }
    }

    val activeRoute = selectedRoute?.takeUnless { it.isDefault }
    val isRemoteSession = (isRemotePlaybackActive || isCastConnecting) && activeRoute != null

    val availableRoutes = if (isWifiEnabled) {
        routes.filterNot { it.isDefault }
    } else {
        emptyList()
    }
    val bluetoothDevices = bluetoothAudioDeviceStates
        .map { state -> state.copy(name = state.name.trim()) }
        .filter { it.name.isNotEmpty() }
        .distinctBy { it.stableId() }
    val activeBluetoothName = bluetoothName
        ?.trim()
        ?.takeIf { activeName ->
            activeName.isNotEmpty() && bluetoothDevices.any { it.name == activeName }
        }

    val devices = buildList {
        if (isWifiEnabled) {
            addAll(
                availableRoutes.map { route ->
                    val isRouteActive = activeRoute?.id == route.id
                    val normalizedConnectionState = when {
                        isRouteActive && isCastConnecting ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING
                        isRouteActive && isRemoteSession ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED ->
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        else -> route.connectionState
                    }

                    CastDeviceUi(
                        id = route.id,
                        name = route.name,
                        deviceType = route.deviceType,
                        playbackType = route.playbackType,
                        connectionState = normalizedConnectionState,
                        volumeHandling = route.volumeHandling,
                        volume = route.volume,
                        volumeMax = route.volumeMax,
                        isSelected = isRouteActive
                    )
                }
            )
        }

        if (isBluetoothEnabled) {
            bluetoothDevices.forEach { bluetoothDevice ->
                val isConnected = bluetoothDevice.name == activeBluetoothName
                add(
                    CastDeviceUi(
                        id = "bluetooth_${bluetoothDevice.stableId()}",
                        name = bluetoothDevice.name,
                        deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
                        playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
                        connectionState = if (isConnected) {
                            MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
                        } else {
                            MediaRouter.RouteInfo.CONNECTION_STATE_DISCONNECTED
                        },
                        volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
                        volume = if (isConnected) (trackVolume * 100).toInt() else 0,
                        volumeMax = 100,
                        isSelected = isConnected && !isRemoteSession,
                        batteryPercent = bluetoothDevice.batteryPercent,
                        isBluetooth = true
                    )
                )
            }
        }
    }

    val activeDevice = if (isRemoteSession) {
        val remoteRoute = checkNotNull(activeRoute)
        ActiveDeviceUi(
            id = remoteRoute.id,
            title = remoteRoute.name,
            subtitle = "Casting session",
            isRemote = true,
            icon = when (remoteRoute.deviceType) {
                MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
                MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
                MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
                else -> Icons.Filled.Cast
            },
            isConnecting = isCastConnecting,
            volume = routeVolume.toFloat().coerceAtLeast(0f),
            volumeRange = 0f..remoteRoute.volumeMax.toFloat().coerceAtLeast(1f),
            connectionLabel = if (isCastConnecting) "Connecting" else "Connected"
        )
    } else {
        val isBluetoothAudio = isBluetoothEnabled && !activeBluetoothName.isNullOrEmpty()
        ActiveDeviceUi(
            id = "phone",
            title = if (isBluetoothAudio) activeBluetoothName!! else "This phone",
            subtitle = if (isBluetoothAudio) "Bluetooth audio" else "Local playback",
            isRemote = false,
            icon = if (isBluetoothAudio) Icons.Rounded.Bluetooth else Icons.Rounded.Headphones,
            isConnecting = false,
            volume = trackVolume,
            volumeRange = 0f..1f,
            connectionLabel = if (isPlaying) "Playing" else "Paused"
        )
    }

    val uiState = CastSheetUiState(
        wifiRadioOn = isWifiRadioOn,
        wifiEnabled = isWifiEnabled,
        wifiSsid = wifiName,
        isScanning = isRefreshing && availableRoutes.isEmpty(),
        isRefreshing = isRefreshing,
        devices = devices,
        activeDevice = activeDevice,
        isBluetoothEnabled = isBluetoothEnabled,
        bluetoothName = activeBluetoothName
    )

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    DisposableEffect(Unit) {
        onExpansionChanged(1f)
        onDispose { onExpansionChanged(0f) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 12.dp
    ) {
        // AQUÍ APLICAMOS EL FIX: Anulamos la fábrica de overscroll para todo lo que esté aquí adentro
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 18.dp)
            ) {
                if (missingPermissions.isNotEmpty()) {
                    CastPermissionStep(
                        missingPermissions = missingPermissions,
                        onRequestPermissions = {
                            permissionLauncher.launch(missingPermissions.toTypedArray())
                        }
                    )
                } else {
                    CastSheetContent(
                        state = uiState,
                        onSelectDevice = { id ->
                            when {
                                id.startsWith("bluetooth_") -> {
                                    val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                else -> routes.firstOrNull { it.id == id }?.let { playerViewModel.selectRoute(it) }
                            }
                        },
                        onDisconnect = {
                            playerViewModel.disconnect()
                            onDismiss()
                        },
                        onVolumeChange = { value ->
                            if (uiState.activeDevice.isRemote) {
                                playerViewModel.setRouteVolume(value.toInt())
                            } else {
                                playerViewModel.setTrackVolume(value)
                            }
                        },
                        onTurnOnWifi = {
                            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onOpenBluetoothSettings = {
                            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        onRefresh = {
                            playerViewModel.refreshCastRoutes()
                            playerViewModel.refreshLocalConnectionInfo(refreshBluetoothDevices = true)
                        },
                        startWithControls = isRemoteSession
                    )
                }
            }
        }
    }
}

private data class CastDeviceUi(
    val id: String,
    val name: String,
    val deviceType: Int,
    val playbackType: Int,
    val connectionState: Int,
    val volumeHandling: Int,
    val volume: Int,
    val volumeMax: Int,
    val isSelected: Boolean,
    val batteryPercent: Int? = null,
    val isBluetooth: Boolean = false
)

private data class ActiveDeviceUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isRemote: Boolean,
    val icon: ImageVector,
    val isConnecting: Boolean,
    val volume: Float,
    val volumeRange: ClosedFloatingPointRange<Float>,
    val connectionLabel: String
)

private data class CastSheetUiState(
    val wifiRadioOn: Boolean,
    val wifiEnabled: Boolean,
    val wifiSsid: String? = null,
    val isScanning: Boolean,
    val isRefreshing: Boolean,
    val devices: List<CastDeviceUi>,
    val activeDevice: ActiveDeviceUi,
    val isBluetoothEnabled: Boolean,
    val bluetoothName: String? = null
)

private fun BluetoothAudioDeviceState.stableId(): String {
    return address ?: name.lowercase()
}

@Composable
private fun CastPermissionStep(
    missingPermissions: List<String>,
    onRequestPermissions: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = "Get ready to connect",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = "Allow PixelPlayer to see your nearby devices and current Wi‑Fi so we can keep your cast, Bluetooth audio, and speakers in sync.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PermissionHighlight(
                    icon = Icons.Rounded.Bluetooth,
                    title = "Nearby devices",
                    description = "Needed to read and control your connected Bluetooth audio gear."
                )
                PermissionHighlight(
                    icon = Icons.Rounded.Wifi,
                    title = "Location for Wi‑Fi",
                    description = "Android requires Location to share the Wi‑Fi network (SSID) you're on so we can find compatible cast devices."
                )
            }
        }

        Button(
            onClick = onRequestPermissions,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50)
        ) {
            Text(text = "Allow access")
        }

        if (missingPermissions.isNotEmpty()) {
            Text(
                text = "We only use these permissions for device interconnectivity — casting, controlling nearby speakers, and keeping audio in sync.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionHighlight(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun missingCastPermissions(context: Context, permissions: List<String>): List<String> {
    return permissions.filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun CastSheetContent(
    state: CastSheetUiState,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    startWithControls: Boolean = true
) {
    val allConnectivityOff = !state.wifiEnabled && !state.isBluetoothEnabled
    val configuration = LocalConfiguration.current
    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val maxPagerHeight = (
        configuration.screenHeightDp.dp -
            safeInsets.calculateTopPadding() -
            safeInsets.calculateBottomPadding() -
            212.dp
        ).coerceAtLeast(280.dp)
    val pagerState = rememberPagerState(
        initialPage = if (startWithControls) 0 else 1,
        pageCount = { 2 }
    )
    val scope = rememberCoroutineScope()
    var heightAnimationEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        heightAnimationEnabled = true
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape
                )
            ) {
                Text(
                    modifier = Modifier.padding(start = 6.dp, end = 8.dp),
                    text = "Connect device",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            AnimatedVisibility(
                visible = state.isScanning,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                label = "tabScanningIndicator"
            ) {
                BadgeChip(
                    text = "Scanning nearby",
                    iconVector = Icons.Filled.Refresh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxPagerHeight)
                .animateContentSize(
                    animationSpec = if (heightAnimationEnabled) {
                        tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    } else {
                        snap()
                    },
                    alignment = Alignment.TopCenter
                )
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .wrapContentHeight()
                    .fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> CastControlsTabContent(
                        state = state,
                        allConnectivityOff = allConnectivityOff,
                        onDisconnect = onDisconnect,
                        onVolumeChange = onVolumeChange,
                        onTurnOnWifi = onTurnOnWifi,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onRefresh = onRefresh,
                        bottomSpacing = 20.dp,
                    )
                    1 -> CastDevicesTabContent(
                        state = state,
                        allConnectivityOff = allConnectivityOff,
                        onSelectDevice = onSelectDevice,
                        onDisconnect = onDisconnect,
                        onTurnOnWifi = onTurnOnWifi,
                        onOpenBluetoothSettings = onOpenBluetoothSettings,
                        onRefresh = onRefresh,
                        maxContentHeight = maxPagerHeight,
                    )
                }
            }
        }

        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(5.dp),
            containerColor = Color.Transparent,
            divider = {},
            indicator = {}
        ) {
            TabAnimation(
                index = 0,
                title = "Controls",
                selectedIndex = pagerState.currentPage,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                transformOrigin = TransformOrigin(0f, 0.5f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Speaker,
                        contentDescription = "Controls",
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "CONTROLS",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TabAnimation(
                index = 1,
                title = "Devices",
                selectedIndex = pagerState.currentPage,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                },
                transformOrigin = TransformOrigin(1f, 0.5f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Devices,
                        contentDescription = "Devices",
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "DEVICES",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CastControlsTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    bottomSpacing: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ActiveDeviceHero(
            device = state.activeDevice,
            onDisconnect = onDisconnect,
            onVolumeChange = onVolumeChange
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Connectivity",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (allConnectivityOff) {
                            "Turn on Wi-Fi or Bluetooth"
                        } else {
                            "Manage active radios and rescan"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh connections")
                }
            }
            QuickSettingsRow(
                wifiOn = state.wifiRadioOn,
                wifiConnected = state.wifiEnabled,
                wifiSsid = state.wifiSsid,
                onWifiClick = onTurnOnWifi,
                bluetoothEnabled = state.isBluetoothEnabled,
                bluetoothName = state.bluetoothName,
                onBluetoothClick = onOpenBluetoothSettings
            )
        }

        if (allConnectivityOff) {
            WifiOffIllustration(
                onTurnOnWifi = onTurnOnWifi,
                onOpenBluetoothSettings = onOpenBluetoothSettings
            )
        }

        Spacer(modifier = Modifier.height(bottomSpacing))
    }
}

@Composable
private fun CastDevicesTabContent(
    state: CastSheetUiState,
    allConnectivityOff: Boolean,
    onSelectDevice: (String) -> Unit,
    onDisconnect: () -> Unit,
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    maxContentHeight: Dp,
) {
    val colors = MaterialTheme.colorScheme

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxContentHeight)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 20.dp)
    ) {
        item(key = "deviceSectionHeader") {
            DeviceSectionHeader(
                modifier = Modifier.fillMaxWidth(),
                hasDevices = state.devices.isNotEmpty(),
                    onRefresh = onRefresh
            )
        }

        item(key = "refreshIndicator") {
            AnimatedVisibility(
                visible = state.isRefreshing,
                enter = fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(180)),
                label = "refreshIndicator"
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp)),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.12f)
                )
            }
        }

        if (allConnectivityOff) {
            item(key = "wifiOff") {
                WifiOffIllustration(
                    onTurnOnWifi = onTurnOnWifi,
                    onOpenBluetoothSettings = onOpenBluetoothSettings
                )
            }
        } else if (state.isScanning && state.devices.isEmpty()) {
            item(key = "scanningPlaceholder") {
                ScanningPlaceholderList()
            }
        } else if (state.devices.isEmpty()) {
            item(key = "emptyDevices") {
                EmptyDeviceState()
            }
        } else {
            items(state.devices, key = { it.id }) { device ->
                CastDeviceRow(
                    device = device,
                    onSelect = { onSelectDevice(device.id) },
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}

@Composable
private fun CastSheetContainer(
    onDismiss: () -> Unit,
    onExpansionChanged: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }
    val hiddenOffsetPx = remember { mutableFloatStateOf(0f) }
    val sheetOffset = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(false) }
    var isDismissing by remember { mutableStateOf(false) }
    var pendingOpenAnimation by remember { mutableStateOf(false) }
    var dragOffsetCache by remember { mutableStateOf<Float?>(null) }
    var pendingDragTarget by remember { mutableStateOf<Float?>(null) }
    var dragSnapJob by remember { mutableStateOf<Job?>(null) }

    fun resetDragPipeline() {
        dragOffsetCache = null
        pendingDragTarget = null
        dragSnapJob?.cancel()
        dragSnapJob = null
    }

    fun launchDragSnapLoopIfNeeded() {
        if (dragSnapJob?.isActive == true) return
        dragSnapJob = scope.launch {
            while (isActive) {
                val target = pendingDragTarget ?: break
                pendingDragTarget = null
                sheetOffset.snapTo(target)
                withFrameNanos { }
            }
            dragSnapJob = null
        }
    }

    val scrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.45f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "scrimAlpha"
    )

    LaunchedEffect(sheetHeightPx) {
        if (sheetHeightPx == 0f) return@LaunchedEffect
        hiddenOffsetPx.floatValue = sheetHeightPx

        if (!isVisible) {
            sheetOffset.snapTo(sheetHeightPx)
            // Once we are snapped to the hidden position, make content visible (alpha 1)
            // so we can see it slide in.
            contentAlpha.snapTo(1f)
            isVisible = true
            onExpansionChanged(1f)
            pendingOpenAnimation = true
        }

        if (!isDismissing && sheetOffset.value > 0.5f) {
            resetDragPipeline()
            if (pendingOpenAnimation) {
                withFrameNanos { }
                pendingOpenAnimation = false
            }
            val hidden = hiddenOffsetPx.floatValue
            val travelFraction = if (hidden > 0f) {
                (abs(sheetOffset.value) / hidden).coerceIn(0f, 1f)
            } else {
                1f
            }
            val durationMillis = (220f + (130f * travelFraction)).toInt()
            sheetOffset.animateTo(0f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
        }
    }

    suspend fun animateToRest() {
        val hidden = hiddenOffsetPx.floatValue
        val travelFraction = if (hidden > 0f) {
            (abs(sheetOffset.value) / hidden).coerceIn(0f, 1f)
        } else {
            1f
        }
        val durationMillis = (170f + (120f * travelFraction)).toInt()
        sheetOffset.animateTo(0f, tween(durationMillis = durationMillis, easing = FastOutSlowInEasing))
    }

    fun dismissSheet(velocity: Float = 0f) {
        if (isDismissing) return
        isDismissing = true
        resetDragPipeline()
        val targetOffset = when {
            hiddenOffsetPx.floatValue > 0f -> hiddenOffsetPx.floatValue
            sheetHeightPx > 0f -> sheetHeightPx
            else -> sheetOffset.value + 1f // Ensure a movement path exists
        }
        scope.launch {
            isVisible = false
            onExpansionChanged(0f)
            try {
                sheetOffset.animateTo(
                    targetValue = targetOffset,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    initialVelocity = velocity
                )
            } finally {
                onDismiss()
            }
        }
    }

    val dragThreshold = with(density) { 72.dp.toPx() }

    // Shared logic for manual dragging (from header or nested scroll)
    fun onDrag(dragAmount: Float) {
        if (isDismissing) return
        val current = dragOffsetCache ?: sheetOffset.value
        val target = (current + dragAmount).coerceIn(0f, hiddenOffsetPx.floatValue)
        dragOffsetCache = target
        pendingDragTarget = target
        launchDragSnapLoopIfNeeded()
    }

    fun onDragEnd(velocity: Float) {
        if (isDismissing) return
        val settledOffset = pendingDragTarget ?: dragOffsetCache ?: sheetOffset.value
        resetDragPipeline()
        if (settledOffset > dragThreshold || velocity > 1400f) {
            dismissSheet(velocity)
        } else {
            scope.launch { animateToRest() }
        }
    }

    // Drag modifier for non-scrollable areas (e.g. Header)
    val sheetDragModifier = Modifier.pointerInput(dragThreshold, hiddenOffsetPx.floatValue) {
        val velocityTracker = VelocityTracker()
        detectVerticalDragGestures(
            onDragStart = { velocityTracker.resetTracking() },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                onDrag(dragAmount)
            },
            onDragEnd = {
                val velocity = velocityTracker.calculateVelocity().y
                onDragEnd(velocity)
            },
            onDragCancel = {
                resetDragPipeline()
                scope.launch { animateToRest() }
            }
        )
    }

    // Nested scroll connection for the list area
    val nestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                if (sheetOffset.value > 0f) {
                    // Sheet is moving (dragging up/down while partially open).
                    // We consume all vertical delta to move the sheet.
                    val delta = available.y
                    // Dragging up (delta < 0) reduces offset (moves sheet up towards 0).
                    // Dragging down (delta > 0) increases offset (moves sheet down).
                    // Logic in onDrag handles addition correctly.
                    onDrag(delta)
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
                // If list reached top and user drags down (available.y > 0)
                if (available.y > 0f) {
                    onDrag(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheetOffset.value > 0f) {
                    onDragEnd(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    BackHandler(enabled = isVisible && !isDismissing) { dismissSheet() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { dismissSheet() }
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .onGloballyPositioned {
                    val height = it.size.height.toFloat()
                    if (height != sheetHeightPx) sheetHeightPx = height
                }
                // Transform-only motion keeps frame budget predictable during open/close animations.
                .graphicsLayer {
                    translationY = sheetOffset.value
                    alpha = contentAlpha.value
                }
                .then(sheetDragModifier)
                .nestedScroll(nestedScrollConnection),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 12.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(modifier = Modifier.padding(bottom = 18.dp)) {
                content()
            }
        }
    }

    // Cast overlay fraction is handled at state-level to avoid frame-by-frame parent recomposition.
}

@Composable
private fun CollapsibleCastTopBar(
    modifier: Modifier = Modifier,
    collapseFraction: Float,
    isScanning: Boolean,
    wifiOn: Boolean,
    wifiConnected: Boolean,
    wifiSsid: String?,
    onWifiClick: () -> Unit,
    isBluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit,
    maxHeight: Dp
) {
    val contentAlpha by animateFloatAsState(
        targetValue = 1f - collapseFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "topBarAlpha"
    )
    val translationYOffset by animateDpAsState(
        targetValue = (-12).dp * collapseFraction,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "topBarTranslation"
    )
    val collapsedTitleAlpha by animateFloatAsState(
        targetValue = collapseFraction,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "collapsedTitle"
    )

    val density = LocalDensity.current

    Box(
        modifier = modifier
            .heightIn(min = 0.dp, max = maxHeight)
            .clipToBounds()
    ) {
        //Ch
//        Box(
//            modifier = Modifier
//                .align(Alignment.BottomStart)
//                .padding(bottom = 20.dp, start = 4.dp)
//                .graphicsLayer{
//                    alpha = (collapsedTitleAlpha)
//                }
//                .background(
//                    color = MaterialTheme.colorScheme.surfaceContainerLow,
//                    shape = CircleShape
//                )
//        ) {
//            Text(
//                text = "Connect device",
//                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
//                modifier = Modifier
//                    .align(Alignment.Center)
//                    .padding(horizontal = 10.dp, vertical = 6.dp)
//                    .graphicsLayer { alpha = (collapsedTitleAlpha) },
//                maxLines = 1
//            )
//        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 12.dp)
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = with(density) { translationYOffset.toPx() }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = CircleShape
                    )
            ) {
                Text(
                    modifier = Modifier.padding(start = 4.dp),
                    text = "Connect device",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(160)),
                label = "scanningIndicator"
            ) {
                BadgeChip(
                    text = "Scanning nearby",
                    iconVector = Icons.Filled.Refresh,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            }

            QuickSettingsRow(
                wifiOn = wifiOn,
                wifiConnected = wifiConnected,
                wifiSsid = wifiSsid,
                onWifiClick = onWifiClick,
                bluetoothEnabled = isBluetoothEnabled,
                bluetoothName = bluetoothName,
                onBluetoothClick = onBluetoothClick
            )
        }
    }
}

@Composable
private fun DeviceSectionHeader(
    modifier: Modifier = Modifier,
    hasDevices: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Nearby devices",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = if (hasDevices) "Tap to connect" else "No devices yet",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(
            onClick = onRefresh,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.clip(RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Refresh devices")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveDeviceHero(
    device: ActiveDeviceUi,
    onDisconnect: () -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    var sliderValue by remember(device.id, device.volume) { mutableFloatStateOf(device.volume) }
    LaunchedEffect(device.volume) { sliderValue = device.volume }
    val haptics = LocalHapticFeedback.current

    val discreteSteps = remember(device.volumeRange) {
        val span = device.volumeRange.endInclusive - device.volumeRange.start
        if (span <= 1f) 20 else span.toInt().coerceAtLeast(0) - 1
    }.coerceAtLeast(0)
    var lastStep by remember(device.id) { mutableIntStateOf(-1) }

    val heroShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 42.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusBR = 42.dp,
        smoothnessAsPercentTL = 70,
        smoothnessAsPercentTR = 70,
        smoothnessAsPercentBL = 70,
        smoothnessAsPercentBR = 70
    )

    Card(
        shape = heroShape,
        colors = CardDefaults.cardColors(
            containerColor = if (device.isRemote) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(62.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                    alpha = if (device.isConnecting) 0.18f else 0.12f
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (device.isConnecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(34.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                trackColor = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.2f),
                                strokeWidth = 4.dp,
                                strokeCap = StrokeCap.Round
                            )
                        } else {
                            Icon(
                                imageVector = device.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }

                // La columna de texto dicta la altura de la Row
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = device.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 2
                    )

                    val statusText = buildString {
                        append(device.subtitle)
                        append(" • ")
                        append(device.connectionLabel)
                    }

                    Text(
                        text = if (device.isConnecting && device.isRemote) "Connecting..." else statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1
                    )
                    if (device.isRemote) {
                        Button(
                            onClick = onDisconnect,
                            enabled = !device.isConnecting,
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ),
                            modifier = Modifier
                                .height(46.dp)
                                .padding(top = 4.dp)
                        ) {
                            Icon(
                                modifier = Modifier.size(22.dp),
                                painter = painterResource(R.drawable.rounded_mimo_disconnect_24),
                                contentDescription = "disconnect_icon",
                            )
                            Spacer(
                                modifier = Modifier.width(6.dp)
                            )
                            Text("Disconnect")
                        }
                    }
                }
            }

            // Sección de Volumen (Sin cambios)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (device.isRemote) "Device volume" else "Phone volume",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = buildVolumeLabel(sliderValue, device.volumeRange.endInclusive),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                val interactionSource = remember { MutableInteractionSource() }
                Slider(
                    value = sliderValue.coerceIn(device.volumeRange.start, device.volumeRange.endInclusive),
                    onValueChange = { newValue ->
                        sliderValue = newValue
                        val quantized = if (device.volumeRange.endInclusive <= 1f) {
                            (newValue * 20).toInt()
                        } else {
                            newValue.toInt()
                        }
                        if (quantized != lastStep) {
                            lastStep = quantized
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        onVolumeChange(newValue)
                    },
                    valueRange = device.volumeRange,
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            modifier = Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            colors = SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                inactiveTrackColor = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.2f),
                                thumbColor = MaterialTheme.colorScheme.onTertiary
                            )
                        )
                    },
                    thumb = { sliderState ->
                        SliderDefaults.Thumb(
                            modifier = Modifier
                                .height(36.dp),
                            interactionSource = interactionSource,
                            sliderState = sliderState,
                            colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        )
                    },
                    colors = SliderDefaults.colors(
                        activeTrackColor = MaterialTheme.colorScheme.tertiary,
                        inactiveTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f),
                        thumbColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private fun buildVolumeLabel(value: Float, max: Float): String {
    return if (max <= 1f) {
        "${(value * 100).toInt()}%"
    } else {
        "${value.toInt()} / ${max.toInt()}"
    }
}

@Composable
private fun EmptyDeviceState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = "Searching for devices...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Make sure your TV or speaker is on and sharing the same Wi‑Fi network.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CastDeviceRow(
    device: CastDeviceUi,
    onSelect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (containerColor, onContainer) = when {
        device.isSelected -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        device.isBluetooth -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }

    val isActiveDevice = device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED
    val scallopShape = RoundedStarShape(sides = 8, curve = 0.10, rotation = 0f)

    // Animaciones
    val infiniteRotation = rememberInfiniteTransition(label = "activeDeviceRotation")
    val rotation by infiniteRotation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "deviceRotation"
    )
    val backgroundScale by animateFloatAsState(
        targetValue = if (isActiveDevice) 1.16f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "activeDeviceScale"
    )

    val deviceIcon = when (device.deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Rounded.Tv
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> Icons.Rounded.Speaker
        MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP -> Icons.Rounded.Bluetooth
        else -> Icons.Filled.Cast
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape) // Mantenemos el clip circular para el ripple
            .clickable(
                enabled = true,
                onClick = when {
                    device.isBluetooth -> onSelect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> onDisconnect
                    device.isSelected &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> ({})
                    else -> onSelect
                }
            ),
        color = containerColor,
        tonalElevation = 2.dp
    ) {
        // Usamos Row con IntrinsicSize.Min para que la altura se adapte al contenido de texto
        // pero permita que el icono se centre verticalmente de forma real.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(12.dp), // Padding uniforme en los 4 lados
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Contenedor del Icono (Leading Content)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .padding(start = 4.dp), // Tamaño fijo para asegurar simetría
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer(
                            rotationZ = if (isActiveDevice) rotation else 0f,
                            scaleX = backgroundScale,
                            scaleY = backgroundScale
                        )
                        .background(
                            color = onContainer.copy(alpha = 0.12f),
                            shape = if (isActiveDevice) scallopShape else CircleShape
                        )
                )

                Icon(
                    imageVector = deviceIcon,
                    contentDescription = null,
                    tint = onContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Cuerpo de texto
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = onContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                val statusText = when {
                    device.isBluetooth &&
                        device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> "Connected"
                    device.isBluetooth -> "Available to connect"
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED -> "Connected"
                    device.isSelected && device.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING -> "Connecting"
                    else -> "Available"
                }

                val statusIcon = if (device.isBluetooth) R.drawable.rounded_bluetooth_24 else R.drawable.rounded_wifi_24

                BadgeChip(
                    text = statusText,
                    icon = statusIcon,
                    contentColor = onContainer
                )
            }

            when {
                device.isBluetooth && device.isSelected -> {
                    BluetoothMetricIndicator(
                        device = device,
                        contentColor = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
                device.volumeHandling == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE && device.isSelected -> {
                    Text(
                        text = "${device.volume}/${device.volumeMax}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainer,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothMetricIndicator(
    device: CastDeviceUi,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val batteryPercent = device.batteryPercent
    val value = batteryPercent ?: buildBluetoothVolumePercent(device)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (batteryPercent != null) {
            Icon(
                imageVector = Icons.Rounded.BatteryFull,
                contentDescription = "Battery level",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.rounded_volume_up_24),
                contentDescription = "Volume level",
                tint = contentColor,
                modifier = Modifier.size(14.dp)
            )
        }

        Text(
            text = "$value%",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

private fun buildBluetoothVolumePercent(device: CastDeviceUi): Int {
    return when {
        device.volumeMax > 0 -> ((device.volume.toFloat() / device.volumeMax) * 100).roundToInt()
        else -> device.volume
    }.coerceIn(0, 100)
}

@Composable
private fun BadgeChip(
    text: String,
    icon: Int? = null,
    iconVector: ImageVector? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(contentColor.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when {
            icon != null -> Icon(painterResource(id = icon), contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
            iconVector != null -> Icon(iconVector, contentDescription = null, tint = contentColor, modifier = Modifier.size(16.dp))
        }
        Text(
            text = text,
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
            overflow = TextOverflow.Ellipsis,
            color = contentColor
        )
    }
}

@Composable
private fun QuickSettingsRow(
    wifiOn: Boolean,
    wifiConnected: Boolean,
    wifiSsid: String?,
    onWifiClick: () -> Unit,
    bluetoothEnabled: Boolean,
    bluetoothName: String?,
    onBluetoothClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickSettingTile(
            label = if (wifiConnected && !wifiSsid.isNullOrEmpty()) wifiSsid else "Wi-Fi",
            subtitle = when {
                !wifiOn -> "Off"
                wifiConnected -> "Connected"
                else -> "On"
            },
            icon = if (wifiOn) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
            isActive = wifiOn,
            onClick = onWifiClick,
            modifier = Modifier.weight(1f)
        )
        QuickSettingTile(
            label = if (bluetoothEnabled && !bluetoothName.isNullOrEmpty()) bluetoothName else "Bluetooth",
            subtitle = if (bluetoothEnabled) {
                if (!bluetoothName.isNullOrEmpty()) "Connected" else "On"
            } else "Off",
            icon = if (bluetoothEnabled) Icons.Rounded.Bluetooth else Icons.Rounded.BluetoothDisabled,
            isActive = bluetoothEnabled,
            onClick = onBluetoothClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun QuickSettingTile(
    label: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Definimos la forma específica para el estado activo
    val activeShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 18.dp,
            cornerRadiusTR = 18.dp,
            cornerRadiusBL = 18.dp,
            cornerRadiusBR = 18.dp,
            smoothnessAsPercentTL = 70,
            smoothnessAsPercentTR = 70,
            smoothnessAsPercentBL = 70,
            smoothnessAsPercentBR = 70
        )
    }

    // El fondo del Tile ahora siempre es surface, pero mantenemos una transición suave si quisieras cambiarlo levemente
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = MaterialTheme.colorScheme.onSurface

    // Colores dinámicos para el ICONO (Círculo interno)
    val iconBoxColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.1f),
        label = "iconBoxColor"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.onPrimary else contentColor,
        label = "iconTint"
    )

    Surface(
        modifier = modifier
            .height(72.dp)
            // Aquí alternamos la forma según el estado
            .clip(if (isActive) activeShape else CircleShape)
            .clickable(onClick = onClick),
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // El contenedor del icono es el que lleva el color primario ahora
            Box(
                modifier = Modifier
                    .size(40.dp) // Un poco más grande para lucir la forma y el color
                    .clip(CircleShape)
                    .background(iconBoxColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = iconTint
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f) // Asegura que el texto ocupe el espacio restante
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun WifiOffIllustration(
    onTurnOnWifi: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 38.dp,
        cornerRadiusTR = 20.dp,
        cornerRadiusBL = 20.dp,
        cornerRadiusBR = 38.dp,
        smoothnessAsPercentTL = 70,
        smoothnessAsPercentTR = 70,
        smoothnessAsPercentBL = 70,
        smoothnessAsPercentBR = 70
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val prim1 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            val prim2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            val prim3 = MaterialTheme.colorScheme.primary
            Canvas(modifier = Modifier.size(120.dp)) {
                drawCircle(color = prim1, radius = size.minDimension / 2)
                drawCircle(
                    color = prim2,
                    radius = size.minDimension / 3,
                    style = Stroke(width = 10.dp.toPx())
                )
                drawCircle(color = prim3, radius = size.minDimension / 6)
            }
            Text(
                text = "Connections are off",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Turn on Wi‑Fi or Bluetooth to discover nearby devices",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onTurnOnWifi,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Turn on Wi‑Fi")
                }

                Button(
                    onClick = onOpenBluetoothSettings,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Text("Open Bluetooth")
                }
            }
        }
    }
}

@Composable
private fun ScanningPlaceholderList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 28.dp,
                            topEnd = 4.dp,
                            bottomEnd = 28.dp,
                            bottomStart = 4.dp
                        )
                    )
            )
        }
    }
}

@Composable
private fun ScanningIndicator(isActive: Boolean) {
    val infinite = rememberInfiniteTransition(label = "scanPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900, easing = FastOutSlowInEasing)),
        label = "pulse"
    )
    val prim = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .size(18.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            drawCircle(
                color = prim,
                radius = (size.minDimension / 2) * if (isActive) pulse else 0.8f,
                alpha = if (isActive) 0.8f else 0.4f
            )
        }
    }
}

@Composable
@Preview(showBackground = true)
private fun CastSheetScanningPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Home Wi-Fi",
        isScanning = true,
        isRefreshing = true,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.4f,
            volumeRange = 0f..1f,
            connectionLabel = "Playing"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Headphones"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetDevicesPreview() {
    val devices = listOf(
        CastDeviceUi(
            id = "1",
            name = "Living room TV",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_TV,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 8,
            volumeMax = 15,
            isSelected = false
        ),
        CastDeviceUi(
            id = "2",
            name = "Pixel Buds Pro",
            deviceType = MediaRouter.RouteInfo.DEVICE_TYPE_BLUETOOTH_A2DP,
            playbackType = MediaRouter.RouteInfo.PLAYBACK_TYPE_LOCAL,
            connectionState = MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTED,
            volumeHandling = MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE,
            volume = 12,
            volumeMax = 25,
            isSelected = true
        )
    )
    val state = CastSheetUiState(
        wifiRadioOn = true,
        wifiEnabled = true,
        wifiSsid = "Office 5G",
        isScanning = false,
        isRefreshing = false,
        devices = devices,
        activeDevice = ActiveDeviceUi(
            id = "2",
            title = "Pixel Buds Pro",
            subtitle = "Connected via Bluetooth",
            isRemote = true,
            icon = Icons.Rounded.Bluetooth,
            isConnecting = false,
            volume = 12f,
            volumeRange = 0f..25f,
            connectionLabel = "Connected"
        ),
        isBluetoothEnabled = true,
        bluetoothName = "Pixel Buds Pro"
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}

@Composable
@Preview(showBackground = true)
private fun CastSheetWifiOffPreview() {
    val state = CastSheetUiState(
        wifiRadioOn = false,
        wifiEnabled = false,
        isScanning = false,
        isRefreshing = false,
        devices = emptyList(),
        activeDevice = ActiveDeviceUi(
            id = "phone",
            title = "This phone",
            subtitle = "Local playback",
            isRemote = false,
            icon = Icons.Rounded.Headphones,
            isConnecting = false,
            volume = 0.5f,
            volumeRange = 0f..1f,
            connectionLabel = "Paused"
        ),
        isBluetoothEnabled = false
    )
    CastSheetContent(
        state = state,
        onSelectDevice = {},
        onDisconnect = {},
        onVolumeChange = {},
        onTurnOnWifi = {},
        onOpenBluetoothSettings = {},
        onRefresh = {}
    )
}
