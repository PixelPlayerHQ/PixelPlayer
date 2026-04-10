package com.theveloper.pixelplay

import com.theveloper.pixelplay.presentation.navigation.navigateSafely

// import androidx.compose.ui.platform.LocalView // No longer needed for this
// import androidx.core.view.WindowInsetsCompat // No longer needed for this
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Trace
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.theveloper.pixelplay.data.github.GitHubAnnouncementPropertiesService
import com.theveloper.pixelplay.data.github.PlayStoreAnnouncementRemoteConfig
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.MusicService
import com.theveloper.pixelplay.data.worker.SyncManager
import com.theveloper.pixelplay.data.worker.SyncProgress
import com.theveloper.pixelplay.presentation.components.AllFilesAccessDialog
import com.theveloper.pixelplay.presentation.components.AppSidebarDrawer
import com.theveloper.pixelplay.presentation.components.CrashReportDialog
import com.theveloper.pixelplay.presentation.components.DismissUndoBar
import com.theveloper.pixelplay.presentation.components.DrawerDestination
import com.theveloper.pixelplay.presentation.components.MiniPlayerBottomSpacer
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.PlayerInternalNavigationBar
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementDefaults
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementDialog
import com.theveloper.pixelplay.presentation.components.PlayStoreAnnouncementUiModel
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheet
import com.theveloper.pixelplay.presentation.components.UnifiedPlayerSheetV2
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarSurfaceHeight
import com.theveloper.pixelplay.presentation.navigation.AppNavigation
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.screens.SetupScreen
import com.theveloper.pixelplay.presentation.viewmodel.MainViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.PixelPlayTheme
import com.theveloper.pixelplay.utils.CrashHandler
import com.theveloper.pixelplay.utils.LogUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.presentation.utils.AppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.NoOpHapticFeedback
import com.theveloper.pixelplay.utils.CrashLogData
import javax.annotation.concurrent.Immutable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map


@Immutable
data class BottomNavItem(
    val label: String,
    @DrawableRes val iconResId: Int,
    @DrawableRes val selectedIconResId: Int? = null,
    val screen: Screen
)

private data class DismissUndoBarSlice(
    val isVisible: Boolean = false,
    val durationMillis: Long = 4000L
)

@UnstableApi
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val playerViewModel: PlayerViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private var isUIVisiblyReady = false
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository // Inject here
    @Inject
    lateinit var themePreferencesRepository: ThemePreferencesRepository
    // For handling shortcut navigation - using StateFlow so composables can observe changes
    private val _pendingPlaylistNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _pendingShuffleAll = kotlinx.coroutines.flow.MutableStateFlow(false)

    private val requestAllFilesAccessLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        // Handle the result in onResume
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        LogUtils.d(this, "onCreate")
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        // MD3 Optimization: Release Splash Screen immediately to render UI skeleton.
        // Data loading is handled via optimistic UI and smooth transitions.
        splashScreen.setKeepOnScreenCondition { false }

        // LEER SEÑAL DE BENCHMARK
        val isBenchmarkMode = intent.getBooleanExtra("is_benchmark", false)

        setContent {
            val systemDarkTheme = isSystemInDarkTheme()
            val appThemeMode by themePreferencesRepository.appThemeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.FOLLOW_SYSTEM)
            val useDarkTheme = when (appThemeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                else -> systemDarkTheme
            }
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle()
            
            // Crash report dialog state
            var showCrashReportDialog by remember { mutableStateOf(false) }
            var crashLogData by remember { mutableStateOf<CrashLogData?>(null) }
            
            // Permissions Logic
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            @OptIn(ExperimentalPermissionsApi::class)
            val permissionState = rememberMultiplePermissionsState(permissions = permissions)
            val needsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    !android.os.Environment.isExternalStorageManager()

            // Determine if we need to show Setup based on completion OR missing permissions
            val permissionsValid = permissionState.allPermissionsGranted && !needsAllFilesAccess
            val showSetupScreen = remember(isSetupComplete, permissionsValid, isBenchmarkMode) {
                when {
                    isBenchmarkMode -> false
                    isSetupComplete == null -> null
                    else -> !isSetupComplete!! || !permissionsValid
                }
            }

            // Sync Trigger: When we are NOT showing setup (meaning permissions are good and setup is done)
            LaunchedEffect(showSetupScreen) {
                if (showSetupScreen == false) {
                     LogUtils.i(this, "Setup complete/skipped and permissions valid. Starting sync.")
                     mainViewModel.startSync()
                }
            }

            // Check for crash log when app starts
            LaunchedEffect(Unit) {
                if (!isBenchmarkMode && CrashHandler.hasCrashLog()) {
                    crashLogData = CrashHandler.getCrashLog()
                    showCrashReportDialog = true
                }
            }

            PixelPlayTheme(
                darkTheme = useDarkTheme
            ) {
                var contentVisible by remember { mutableStateOf(false) }
                val contentAlpha by animateFloatAsState(
                    targetValue = if (contentVisible) 1f else 0f,
                    animationSpec = tween(600, easing = LinearOutSlowInEasing),
                    label = "AppContentAlpha"
                )

                LaunchedEffect(Unit) {
                    // Delay slightly to ensure first frame layout is done behind Splash
                    delay(100)
                    contentVisible = true
                }

                Surface(
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha }, 
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSetupScreen == null) {
                        SetupGateLoadingScreen()
                    } else {
                        AnimatedContent(
                            targetState = showSetupScreen,
                            transitionSpec = {
                                if (targetState) {
                                    // Transition to Setup
                                    fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                                } else {
                                    // Transition from Setup to Main App
                                    scaleIn(initialScale = 0.95f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)) togetherWith
                                            slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450))
                                }
                            },
                            label = "SetupTransition"
                        ) { shouldShowSetup ->
                            if (shouldShowSetup) {
                                SetupScreen(onSetupComplete = {
                                    // Repository-backed setup completion updates the gate automatically.
                                })
                            } else {
                                MainAppContent(playerViewModel, mainViewModel)
                            }
                        }
                    }

                    // Show crash report dialog if needed
                    if (showCrashReportDialog && crashLogData != null) {
                        CrashReportDialog(
                            crashLog = crashLogData!!,
                            onDismiss = {
                                CrashHandler.clearCrashLog()
                                crashLogData = null
                                showCrashReportDialog = false
                            }
                        )
                    }
                }
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when {
            // Handle shuffle all shortcut / tile
            intent.action == MainActivityIntentContract.ACTION_SHUFFLE_ALL -> {
                android.util.Log.d("TileDebug", "handleIntent: ACTION_SHUFFLE_ALL received")
                playerViewModel.triggerShuffleAllFromTile()
                intent.action = null // Clear action to prevent re-triggering
            }
            
            // Handle playlist shortcut
            intent.action == MainActivityIntentContract.ACTION_OPEN_PLAYLIST -> {
                intent.getStringExtra(MainActivityIntentContract.EXTRA_PLAYLIST_ID)?.let { playlistId ->
                    _pendingPlaylistNavigation.value = playlistId
                }
                intent.action = null
            }

            intent.getBooleanExtra("ACTION_SHOW_PLAYER", false) -> {
                playerViewModel.showPlayer()
            }

            intent.action == android.content.Intent.ACTION_VIEW && intent.data != null -> {
                intent.data?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }

            intent.action == android.content.Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true -> {
                resolveStreamUri(intent)?.let { uri ->
                    persistUriPermissionIfNeeded(intent, uri)
                    playerViewModel.playExternalUri(uri)
                }
                clearExternalIntentPayload(intent)
            }
            
            intent.action == "com.theveloper.pixelplay.ACTION_PLAY_SONG" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                     intent.getParcelableExtra("song", com.theveloper.pixelplay.data.model.Song::class.java)?.let { song ->
                         playerViewModel.playSong(song)
                     }
                } else {
                     @Suppress("DEPRECATION")
                     intent.getParcelableExtra<com.theveloper.pixelplay.data.model.Song>("song")?.let { song ->
                         playerViewModel.playSong(song)
                     }
                }
                intent.action = null
            }
        }
    }
    
    private fun resolveStreamUri(intent: Intent): android.net.Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(android.content.Intent.EXTRA_STREAM, android.net.Uri::class.java)?.let { return it }
        } else {
            @Suppress("DEPRECATION")
            val legacyUri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (legacyUri != null) return legacyUri
        }

        intent.clipData?.let { clipData ->
            if (clipData.itemCount > 0) {
                return clipData.getItemAt(0).uri
            }
        }

        return intent.data
    }

    private fun persistUriPermissionIfNeeded(intent: Intent, uri: android.net.Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            val hasPersistablePermission = intent.flags and android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
            if (hasPersistablePermission) {
                val takeFlags = intent.flags and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                if (takeFlags != 0) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (securityException: SecurityException) {
                        android.util.Log.w("MainActivity", "Unable to persist URI permission for $uri", securityException)
                    } catch (illegalArgumentException: IllegalArgumentException) {
                        android.util.Log.w("MainActivity", "Persistable URI permission not granted for $uri", illegalArgumentException)
                    }
                }
            }
        }
    }

    private fun clearExternalIntentPayload(intent: Intent) {
        intent.data = null
        intent.clipData = null
        intent.removeExtra(android.content.Intent.EXTRA_STREAM)
    }

    private fun openExternalUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            LogUtils.w(this, "No activity available to open URL: $url")
        }
    }

    private fun PlayStoreAnnouncementRemoteConfig.toUiModel(): PlayStoreAnnouncementUiModel {
        val fallback = PlayStoreAnnouncementDefaults.Template
        return fallback.copy(
            enabled = enabled,
            playStoreUrl = playStoreUrl ?: fallback.playStoreUrl,
            title = title ?: fallback.title,
            body = body ?: fallback.body,
            primaryActionLabel = primaryActionLabel ?: fallback.primaryActionLabel,
            dismissActionLabel = dismissActionLabel ?: fallback.dismissActionLabel,
            linkPendingMessage = linkPendingMessage ?: fallback.linkPendingMessage,
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun SetupGateLoadingScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = "Preparing setup…",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainAppContent(playerViewModel: PlayerViewModel, mainViewModel: MainViewModel) {
        Trace.beginSection("MainActivity.MainAppContent")
        val navController = rememberNavController()
        val isSyncing by mainViewModel.isSyncing.collectAsStateWithLifecycle()
        val isLibraryEmpty by mainViewModel.isLibraryEmpty.collectAsStateWithLifecycle()
        val hasCompletedInitialSync by mainViewModel.hasCompletedInitialSync.collectAsStateWithLifecycle()
        val syncProgress by mainViewModel.syncProgress.collectAsStateWithLifecycle()
        
        // isMediaControllerReady used below for playlist navigation gate
        val isMediaControllerReady by playerViewModel.isMediaControllerReady.collectAsStateWithLifecycle()
        
        // Observe pending playlist navigation
        val pendingPlaylistNav by _pendingPlaylistNavigation.collectAsStateWithLifecycle()
        var processedPlaylistId by remember { mutableStateOf<String?>(null) }
        
        LaunchedEffect(pendingPlaylistNav, isMediaControllerReady) {
            val playlistId = pendingPlaylistNav
            // Only process if we have a new playlist ID that hasn't been processed yet
            if (playlistId != null && playlistId != processedPlaylistId && isMediaControllerReady) {
                processedPlaylistId = playlistId
                // Wait for navigation graph to be ready (retry with delay)
                var success = false
                var attempts = 0
                while (!success && attempts < 50) { // 5 seconds max
                    try {
                        success = navController.navigateSafely(Screen.PlaylistDetail.createRoute(playlistId))
                        if (success) {
                            _pendingPlaylistNavigation.value = null
                        } else {
                            delay(100)
                            attempts++
                        }
                    } catch (e: IllegalArgumentException) {
                        delay(100)
                        attempts++
                    }
                }
            } else if (playlistId == null) {
                // Reset so the same playlist can be opened again
                processedPlaylistId = null
            }
        }

        // Estado para controlar si el indicador de carga puede mostrarse después de un delay
        var canShowLoadingIndicator by remember { mutableStateOf(false) }
        // Track when the loading indicator was first shown for minimum display time
        var loadingShownTimestamp by remember { mutableStateOf(0L) }
        val minimumDisplayDuration = 1500L // Show loading for at least 1.5 seconds

        val shouldPotentiallyShowLoading = isSyncing && isLibraryEmpty && !hasCompletedInitialSync

        LaunchedEffect(shouldPotentiallyShowLoading) {
            if (shouldPotentiallyShowLoading) {
                // Espera un breve período antes de permitir que se muestre el indicador de carga
                // Ajusta este valor según sea necesario (por ejemplo, 300-500 ms)
                delay(300L)
                // Vuelve a verificar la condición después del delay,
                // ya que el estado podría haber cambiado.
                if (mainViewModel.isSyncing.value && mainViewModel.isLibraryEmpty.value) {
                    canShowLoadingIndicator = true
                    loadingShownTimestamp = System.currentTimeMillis()
                }
            } else {
                // Ensure minimum display time before hiding
                if (canShowLoadingIndicator && loadingShownTimestamp > 0) {
                    val elapsed = System.currentTimeMillis() - loadingShownTimestamp
                    val remaining = minimumDisplayDuration - elapsed
                    if (remaining > 0) {
                        delay(remaining)
                    }
                }
                canShowLoadingIndicator = false
                loadingShownTimestamp = 0L
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MainUI(playerViewModel, navController)

            // Muestra el LoadingOverlay solo si las condiciones se cumplen Y el delay ha pasado
            if (canShowLoadingIndicator) {
                LoadingOverlay(syncProgress)
            }
        }
        Trace.endSection() // End MainActivity.MainAppContent
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    @Composable
    private fun MainUI(playerViewModel: PlayerViewModel, navController: NavHostController) {
        Trace.beginSection("MainActivity.MainUI")

        val commonNavItems = remember {
            persistentListOf(
                BottomNavItem("Home", R.drawable.rounded_home_24, R.drawable.home_24_rounded_filled, Screen.Home),
                BottomNavItem("Search", R.drawable.rounded_search_24, R.drawable.rounded_search_24, Screen.Search),
                BottomNavItem("Library", R.drawable.rounded_library_music_24, R.drawable.round_library_music_24, Screen.Library)
            )
        }
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        var isSearchBarActive by remember { mutableStateOf(false) }

        val routesWithHiddenNavigationBar = remember {
            setOf(
                Screen.Settings.route,
                Screen.Accounts.route,
                Screen.PlaylistDetail.route,
                Screen.DailyMixScreen.route,
                Screen.RecentlyPlayed.route,
                Screen.GenreDetail.route,
                Screen.AlbumDetail.route,
                Screen.ArtistDetail.route,
                Screen.DJSpace.route,
                Screen.NavBarCrRad.route,
                Screen.About.route,
                Screen.Stats.route,
                Screen.EditTransition.route,
                Screen.Experimental.route,
                Screen.ArtistSettings.route,
                Screen.Equalizer.route,
                Screen.SettingsCategory.route,
                Screen.DelimiterConfig.route,
                Screen.PaletteStyle.route,
                Screen.RecentlyPlayed.route,
                Screen.DeviceCapabilities.route,
                Screen.EasterEgg.route,
                Screen.WordDelimiterConfig.route
            )
        }
        val shouldHideNavigationBar by remember(currentRoute, isSearchBarActive) {
            derivedStateOf {
                if (currentRoute == Screen.Search.route && isSearchBarActive) {
                    true
                } else {
                    currentRoute?.let { route ->
                        routesWithHiddenNavigationBar.any { hiddenRoute ->
                            if (hiddenRoute.contains("{")) {
                                route.startsWith(hiddenRoute.substringBefore("{"))
                            } else {
                                route == hiddenRoute
                            }
                        }
                    } ?: false
                }
            }
        }

        val navBarStyle by playerViewModel.navBarStyle.collectAsStateWithLifecycle()
        val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()
        val navBarCornerRadius by playerViewModel.navBarCornerRadius.collectAsStateWithLifecycle()
        val hapticsEnabled by playerViewModel.hapticsEnabled.collectAsStateWithLifecycle()
        val rootView = LocalView.current
        val platformHapticFeedback = LocalHapticFeedback.current
        val appHapticsConfig = remember(hapticsEnabled) {
            AppHapticsConfig(enabled = hapticsEnabled)
        }
        val scopedHapticFeedback = remember(platformHapticFeedback, appHapticsConfig.enabled) {
            if (appHapticsConfig.enabled) platformHapticFeedback else NoOpHapticFeedback
        }

        val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        LaunchedEffect(hapticsEnabled, rootView) {
            rootView.isHapticFeedbackEnabled = hapticsEnabled
            rootView.rootView?.isHapticFeedbackEnabled = hapticsEnabled
        }

        val horizontalPadding = if (navBarStyle == NavBarStyle.DEFAULT) {
            if (systemNavBarInset > 30.dp) 14.dp else systemNavBarInset
        } else {
            0.dp
        }
        val animatedBottomBarPadding by animateDpAsState(
            targetValue = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else systemNavBarInset,
            animationSpec = tween(400),
            label = "BottomBarPadding"
        )
        val bottomBarPadding = animatedBottomBarPadding
        val navBarHeight = resolveNavBarSurfaceHeight(navBarStyle, systemNavBarInset, navBarCompactMode)
        val navBarOccupiedHeight by remember(systemNavBarInset, navBarCompactMode) {
            derivedStateOf { resolveNavBarOccupiedHeight(systemNavBarInset, navBarCompactMode) }
        }
        val navBarVisibilityProgress by animateFloatAsState(
            targetValue = if (shouldHideNavigationBar) 0f else 1f,
            animationSpec = tween(
                durationMillis = 220,
                easing = LinearOutSlowInEasing
            ),
            label = "NavBarVisibilityProgress"
        )
        val visibleNavBarOccupiedHeight by remember(navBarOccupiedHeight, navBarVisibilityProgress) {
            derivedStateOf { navBarOccupiedHeight * navBarVisibilityProgress }
        }
        val miniPlayerBottomMargin by remember(systemNavBarInset, visibleNavBarOccupiedHeight) {
            derivedStateOf {
                if (visibleNavBarOccupiedHeight > systemNavBarInset) {
                    visibleNavBarOccupiedHeight
                } else {
                    systemNavBarInset
                }
            }
        }
        val shouldRenderNavigationBar by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                !shouldHideNavigationBar || navBarVisibilityProgress > 0.01f
            }
        }
        val isNavBarEffectivelyHidden by remember(shouldHideNavigationBar, navBarVisibilityProgress) {
            derivedStateOf {
                shouldHideNavigationBar && navBarVisibilityProgress <= 0.01f
            }
        }

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val announcementService = remember { GitHubAnnouncementPropertiesService() }
        var playStoreAnnouncement by remember { mutableStateOf(PlayStoreAnnouncementDefaults.Template) }
        var showPlayStoreAnnouncement by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            if (PlayStoreAnnouncementDefaults.LOCAL_PREVIEW_ENABLED) {
                playStoreAnnouncement = PlayStoreAnnouncementDefaults.HardcodedPreview
                showPlayStoreAnnouncement = true
                return@LaunchedEffect
            }

            announcementService.fetchPlayStoreAnnouncement()
                .onSuccess { remoteConfig ->
                    val resolvedAnnouncement = remoteConfig.toUiModel()
                    playStoreAnnouncement = resolvedAnnouncement
                    showPlayStoreAnnouncement = resolvedAnnouncement.enabled
                }
                .onFailure { throwable ->
                    LogUtils.w(
                        this@MainActivity,
                        "Remote announcement unavailable. Keeping popup disabled. ${throwable.message ?: ""}",
                    )
                }
        }

        CompositionLocalProvider(
            LocalAppHapticsConfig provides appHapticsConfig,
            LocalHapticFeedback provides scopedHapticFeedback
        ) {
            AppSidebarDrawer(
                drawerState = drawerState,
                selectedRoute = currentRoute ?: Screen.Home.route,
                onDestinationSelected = { destination ->
                    scope.launch { drawerState.close() }
                    when (destination) {
                        DrawerDestination.Home -> navController.navigateSafely(Screen.Home.route) {
                            popUpTo(Screen.Home.route) { inclusive = true }
                        }
                        DrawerDestination.Equalizer -> navController.navigateSafely(Screen.Equalizer.route)
                        DrawerDestination.Settings -> navController.navigateSafely(Screen.Settings.route)
                        DrawerDestination.Telegram -> {
                            val intent = Intent(this@MainActivity, com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity::class.java)
                            startActivity(intent)
                        }
                        else -> {}
                    }
                }
        ) {

                Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (shouldRenderNavigationBar) {
                        val playerContentExpansionFraction = playerViewModel.playerContentExpansionFraction.value
                        val currentSongId by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong?.id }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = null)
                        val showPlayerContentArea = currentSongId != null
                        val navBarElevation = 3.dp

                        val playerContentActualBottomRadiusTargetValue by remember(
                            navBarStyle,
                            showPlayerContentArea,
                            playerContentExpansionFraction,
                        ) {
                            derivedStateOf {
                                if (navBarStyle == NavBarStyle.FULL_WIDTH) {
                                    return@derivedStateOf lerp(navBarCornerRadius.dp, 26.dp, playerContentExpansionFraction)
                                }

                                if (showPlayerContentArea) {
                                    if (playerContentExpansionFraction < 0.2f) {
                                        lerp(12.dp, 26.dp, (playerContentExpansionFraction / 0.2f).coerceIn(0f, 1f))
                                    } else {
                                        26.dp
                                    }
                                } else {
                                    navBarCornerRadius.dp
                                }
                            }
                        }

                        val playerContentActualBottomRadius by animateDpAsState(
                            targetValue = playerContentActualBottomRadiusTargetValue,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            ),
                            label = "PlayerContentBottomRadius"
                        )

                        val navBarHideFraction = if (showPlayerContentArea) playerContentExpansionFraction else 0f
                        val navBarHideFractionClamped = navBarHideFraction.coerceIn(0f, 1f)

                        val animatedNavBarCornerRadius by animateDpAsState(
                            targetValue = navBarCornerRadius.dp,
                            animationSpec = tween(400),
                            label = "NavBarCornerRadius"
                        )

                        val actualShape = remember(playerContentActualBottomRadius, showPlayerContentArea, navBarStyle, animatedNavBarCornerRadius) {
                            val bottomRadius = if (navBarStyle == NavBarStyle.FULL_WIDTH) 0.dp else animatedNavBarCornerRadius
                            AbsoluteSmoothCornerShape(
                                cornerRadiusTL = playerContentActualBottomRadius,
                                smoothnessAsPercentBR = 60,
                                cornerRadiusTR = playerContentActualBottomRadius,
                                smoothnessAsPercentTL = 60,
                                cornerRadiusBL = bottomRadius,
                                smoothnessAsPercentTR = 60,
                                cornerRadiusBR = bottomRadius,
                                smoothnessAsPercentBL = 60
                            )
                        }

                        var componentHeightPx by remember { mutableStateOf(0) }
                        val density = LocalDensity.current
                        val shadowOverflowPx = remember(navBarElevation, density) {
                            with(density) { (navBarElevation * 8).toPx() }
                        }
                        val bottomBarPaddingPx = remember(bottomBarPadding, density) {
                            with(density) { bottomBarPadding.toPx() }
                        }
                        val animatedTranslationY by remember(
                            navBarHideFractionClamped,
                            componentHeightPx,
                            shadowOverflowPx,
                            bottomBarPaddingPx,
                        ) {
                            derivedStateOf {
                                (componentHeightPx + shadowOverflowPx + bottomBarPaddingPx) * navBarHideFractionClamped
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(visibleNavBarOccupiedHeight)
                                .clipToBounds()
                        ) {
                            val onSearchIconDoubleTap = remember(playerViewModel) {
                                { playerViewModel.onSearchNavIconDoubleTapped() }
                            }

                            Surface(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = bottomBarPadding)
                                    .onSizeChanged { componentHeightPx = it.height }
                                    .graphicsLayer {
                                        translationY = animatedTranslationY
                                        alpha = 1f
                                    }
                                    .height(navBarHeight)
                                    .padding(horizontal = horizontalPadding),
                                color = NavigationBarDefaults.containerColor,
                                shape = actualShape,
                                shadowElevation = navBarElevation
                            ) {
                                PlayerInternalNavigationBar(
                                    navController = navController,
                                    navItems = commonNavItems,
                                    currentRoute = currentRoute,
                                    navBarStyle = navBarStyle,
                                    compactMode = navBarCompactMode,
                                    onSearchIconDoubleTap = onSearchIconDoubleTap,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                ) { innerPadding ->
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val configuration = LocalWindowInfo.current
                        val screenHeightPx = remember(configuration) { with(density) { configuration.containerSize.height } }
                        val containerHeight = this.maxHeight

                        val showPlayerContentInitially by remember {
                            playerViewModel.stablePlayerState
                                .map { it.currentSong?.id != null }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = false)
                        val usePlayerSheetV2 by userPreferencesRepository.usePlayerSheetV2Flow.collectAsStateWithLifecycle(initialValue = true)

                        val routesWithHiddenMiniPlayer = remember { setOf(Screen.NavBarCrRad.route) }
                        val shouldHideMiniPlayer by remember(currentRoute) {
                            derivedStateOf { currentRoute in routesWithHiddenMiniPlayer }
                        }

                        val miniPlayerH = with(density) { MiniPlayerHeight.toPx() }
                        val totalSheetHeightWhenContentCollapsedPx = if (showPlayerContentInitially && !shouldHideMiniPlayer) miniPlayerH else 0f

                        val bottomMargin = miniPlayerBottomMargin

                        val spacerPx = with(density) { MiniPlayerBottomSpacer.toPx() }
                        val sheetCollapsedTargetY = screenHeightPx - totalSheetHeightWhenContentCollapsedPx - with(density){ bottomMargin.toPx() } - spacerPx

                        AppNavigation(
                            playerViewModel = playerViewModel,
                            navController = navController,
                            paddingValues = innerPadding,
                            userPreferencesRepository = userPreferencesRepository,
                            onSearchBarActiveChange = { isSearchBarActive = it },
                            onOpenSidebar = { scope.launch { drawerState.open() } }
                        )

                        if (usePlayerSheetV2) {
                            UnifiedPlayerSheetV2(
                                playerViewModel = playerViewModel,
                                sheetCollapsedTargetY = sheetCollapsedTargetY,
                                collapsedStateHorizontalPadding = horizontalPadding,
                                hideMiniPlayer = shouldHideMiniPlayer,
                                containerHeight = containerHeight,
                                navController = navController,
                                isNavBarHidden = isNavBarEffectivelyHidden
                            )
                        } else {
                            UnifiedPlayerSheet(
                                playerViewModel = playerViewModel,
                                sheetCollapsedTargetY = sheetCollapsedTargetY,
                                collapsedStateHorizontalPadding = horizontalPadding,
                                hideMiniPlayer = shouldHideMiniPlayer,
                                containerHeight = containerHeight,
                                navController = navController,
                                isNavBarHidden = isNavBarEffectivelyHidden
                            )
                        }

                        val dismissUndoBarSlice by remember {
                            playerViewModel.playerUiState
                                .map { state ->
                                    DismissUndoBarSlice(
                                        isVisible = state.showDismissUndoBar,
                                        durationMillis = state.undoBarVisibleDuration
                                    )
                                }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = DismissUndoBarSlice())
                        val onUndoDismissPlaylist = remember(playerViewModel) {
                            { playerViewModel.undoDismissPlaylist() }
                        }
                        val onCloseDismissUndoBar = remember(playerViewModel) {
                            { playerViewModel.hideDismissUndoBar() }
                        }

                        AnimatedVisibility(
                            visible = dismissUndoBarSlice.isVisible,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = innerPadding.calculateBottomPadding() + MiniPlayerBottomSpacer)
                                .padding(horizontal = horizontalPadding)
                        ) {
                            DismissUndoBar(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MiniPlayerHeight)
                                    .padding(horizontal = 14.dp),
                                onUndo = onUndoDismissPlaylist,
                                onClose = onCloseDismissUndoBar,
                                durationMillis = dismissUndoBarSlice.durationMillis
                            )
                        }

                        if (showPlayStoreAnnouncement) {
                            PlayStoreAnnouncementDialog(
                                announcement = playStoreAnnouncement,
                                onDismiss = { showPlayStoreAnnouncement = false },
                                onOpenPlayStore = { url ->
                                    showPlayStoreAnnouncement = false
                                    openExternalUrl(url)
                                }
                            )
                        }
                    }
                }
            }
        }

        Trace.endSection()
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    private fun LoadingOverlay(syncProgress: SyncProgress) {
        // Animate progress smoothly instead of jumping in steps
        val animatedProgress by androidx.compose.animation.core.animateFloatAsState(
            targetValue = syncProgress.progress,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
            ),
            label = "SyncProgressAnimation"
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                .clickable(enabled = false, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                CircularWavyProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Preparing your library...",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                if (syncProgress.hasProgress) {
                    Spacer(modifier = Modifier.height(16.dp))
                    androidx.compose.material3.LinearWavyProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scanned ${syncProgress.currentCount} of ${syncProgress.totalCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }


    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onStart() {
        super.onStart()
        LogUtils.d(this, "onStart")
        playerViewModel.onMainActivityStart()

        if (intent.getBooleanExtra("is_benchmark", false)) {
            // Benchmark mode no longer loads dummy data - uses real library data instead
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        mediaControllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        mediaControllerFuture?.addListener({
        }, MoreExecutors.directExecutor())
    }

    override fun onStop() {
        super.onStop()
        LogUtils.d(this, "onStop")
        mediaControllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }

    override fun onResume() {
        super.onResume()
    }


}
