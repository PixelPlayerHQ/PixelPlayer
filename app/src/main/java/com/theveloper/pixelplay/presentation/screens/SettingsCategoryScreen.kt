package com.theveloper.pixelplay.presentation.screens

import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.components.BackupModuleSelectionDialog
import com.theveloper.pixelplay.data.preferences.AiPreferencesRepository
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.backup.model.BackupHistoryEntry
import com.theveloper.pixelplay.data.backup.model.BackupOperationType
import com.theveloper.pixelplay.data.backup.model.BackupSection
import com.theveloper.pixelplay.data.backup.model.BackupTransferProgressUpdate
import com.theveloper.pixelplay.data.backup.model.ModuleRestoreDetail
import com.theveloper.pixelplay.data.backup.model.RestorePlan
import com.theveloper.pixelplay.data.preferences.AppLanguage
import com.theveloper.pixelplay.data.preferences.AppThemeMode
import com.theveloper.pixelplay.data.preferences.CollagePattern
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.data.preferences.LaunchTab
import com.theveloper.pixelplay.data.preferences.LibraryNavigationMode
import com.theveloper.pixelplay.data.preferences.NavBarStyle
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.LyricsSourcePreference
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.ExpressiveTopBarContent
import com.theveloper.pixelplay.presentation.components.FileExplorerDialog
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.model.SettingsCategory
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.viewmodel.LyricsRefreshProgress
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsCategoryScreen(
    categoryId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = hiltViewModel(),
    statsViewModel: com.theveloper.pixelplay.presentation.viewmodel.StatsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val category = SettingsCategory.fromId(categoryId) ?: return
    val context = LocalContext.current
    
    // State Collection (Duplicated from SettingsScreen for now to ensure functionality)
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val currentAiApiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle()
    val currentAiModel by settingsViewModel.currentAiModel.collectAsStateWithLifecycle()
    val currentAiSystemPrompt by settingsViewModel.currentAiSystemPrompt.collectAsStateWithLifecycle()
    val aiProvider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val currentPath by settingsViewModel.currentPath.collectAsStateWithLifecycle()
    val directoryChildren by settingsViewModel.currentDirectoryChildren.collectAsStateWithLifecycle()
    val availableStorages by settingsViewModel.availableStorages.collectAsStateWithLifecycle()
    val selectedStorageIndex by settingsViewModel.selectedStorageIndex.collectAsStateWithLifecycle()
    val isLoadingDirectories by settingsViewModel.isLoadingDirectories.collectAsStateWithLifecycle()
    val isExplorerPriming by settingsViewModel.isExplorerPriming.collectAsStateWithLifecycle()
    val isExplorerReady by settingsViewModel.isExplorerReady.collectAsStateWithLifecycle()
    val isCurrentDirectoryResolved by settingsViewModel.isCurrentDirectoryResolved.collectAsStateWithLifecycle()
    val isSyncing by settingsViewModel.isSyncing.collectAsStateWithLifecycle()
    val syncProgress by settingsViewModel.syncProgress.collectAsStateWithLifecycle()
    val dataTransferProgress by settingsViewModel.dataTransferProgress.collectAsStateWithLifecycle()
    val paletteRegenerateTargets by playerViewModel.paletteRegenerationTargets.collectAsStateWithLifecycle()
    val explorerRoot = settingsViewModel.explorerRoot()

    // Local State
    var showExplorerSheet by remember { mutableStateOf(false) }
    var refreshRequested by remember { mutableStateOf(false) }
    var syncRequestObservedRunning by remember { mutableStateOf(false) }
    var syncIndicatorLabel by remember { mutableStateOf<String?>(null) }
    var showClearLyricsDialog by remember { mutableStateOf(false) }
    var showRebuildDatabaseWarning by remember { mutableStateOf(false) }
    var showRegenerateDailyMixDialog by remember { mutableStateOf(false) }
    var showRegenerateStatsDialog by remember { mutableStateOf(false) }
    var showRegenerateAllPalettesDialog by remember { mutableStateOf(false) }
    var showExportDataDialog by remember { mutableStateOf(false) }
    var showImportFlow by remember { mutableStateOf(false) }
    var exportSections by remember { mutableStateOf(BackupSection.defaultSelection) }
    var importFileUri by remember { mutableStateOf<Uri?>(null) }
    var minSongDurationDraft by remember(uiState.minSongDuration) {
        mutableStateOf(uiState.minSongDuration.toFloat())
    }
    var minTracksPerAlbumDraft by remember(uiState.minTracksPerAlbum) {
        mutableStateOf(uiState.minTracksPerAlbum.toFloat())
    }
    var albumArtCacheLimitDraft by remember(uiState.albumArtCacheLimitMb) {
        mutableStateOf(uiState.albumArtCacheLimitMb.toFloat())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            settingsViewModel.exportAppData(uri, exportSections)
        }
    }

    val importFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            importFileUri = uri
            settingsViewModel.inspectBackupFile(uri)
        }
    }

    LaunchedEffect(Unit) {
        settingsViewModel.dataTransferEvents.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isSyncing, refreshRequested) {
        if (!refreshRequested) return@LaunchedEffect

        if (isSyncing) {
            syncRequestObservedRunning = true
        } else if (syncRequestObservedRunning) {
            Toast.makeText(context, context.getString(R.string.toast_library_sync_finished), Toast.LENGTH_SHORT).show()
            refreshRequested = false
            syncRequestObservedRunning = false
            syncIndicatorLabel = null
        }
    }

    var showPaletteRegenerateSheet by remember { mutableStateOf(false) }
    var isPaletteRegenerateRunning by remember { mutableStateOf(false) }
    var isPaletteBulkRegenerateRunning by remember { mutableStateOf(false) }
    var paletteBulkCompletedCount by remember { mutableStateOf(0) }
    var paletteBulkTotalCount by remember { mutableStateOf(0) }
    var paletteSongSearchQuery by remember { mutableStateOf("") }
    val paletteRegenerateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isAnyPaletteRegenerateRunning = isPaletteRegenerateRunning || isPaletteBulkRegenerateRunning
    val filteredPaletteSongs = remember(paletteRegenerateTargets, paletteSongSearchQuery) {
        val query = paletteSongSearchQuery.trim()
        if (query.isBlank()) {
            paletteRegenerateTargets
        } else {
            paletteRegenerateTargets.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.displayArtist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true)
            }
        }
    }

    // Auto-load models when entering the AI settings page or when provider/key changes
    LaunchedEffect(category, aiProvider) {
        if (category == SettingsCategory.AI_INTEGRATION) {
            // Small delay to let StateFlows settle after navigation
            kotlinx.coroutines.delay(200)
            settingsViewModel.loadModelsForCurrentProvider()
        }
    }

    // TopBar Animations (identical to SettingsScreen)
    // TopBar Animations (identical to SettingsScreen)
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    
    val categoryTitle = stringResource(category.titleRes)
    val isLongTitle = categoryTitle.length > 13
    
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = if (isLongTitle) 200.dp else 180.dp //for 2 lines use 220 and make text use \n

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }
    
    val titleMaxLines = if (isLongTitle) 2 else 1

    val topBarHeight = remember(maxTopBarHeightPx) { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value, maxTopBarHeightPx) {
        collapseFraction =
                1f -
                        ((topBarHeight.value - minTopBarHeightPx) /
                                        (maxTopBarHeightPx - minTopBarHeightPx))
                                .coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (!isScrollingDown &&
                                (lazyListState.firstVisibleItemIndex > 0 ||
                                        lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight =
                        (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
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
            val canExpand =
                    lazyListState.firstVisibleItemIndex == 0 &&
                            lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue =
                    if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier =
            Modifier.nestedScroll(nestedScrollConnection).fillMaxSize()
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                start = 16.dp,
                end = 16.dp,
                bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp
            )
        ) {
            item {
               // Use a simple Column for now, or ExpressiveSettingsGroup if preferred strictly for items
               Column(
                    modifier = Modifier.background(Color.Transparent)
               ) {
                    when (category) {
                        SettingsCategory.LIBRARY -> {
                            SettingsSubsection(title = stringResource(R.string.setcat_library_structure)) {
                                SettingsItem(
                                    title = stringResource(R.string.setcat_excluded_directories_title),
                                    subtitle = stringResource(R.string.setcat_excluded_directories_subtitle),
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = {
                                        showExplorerSheet = true
                                        settingsViewModel.openExplorer()
                                    }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_artists_title),
                                    subtitle = stringResource(R.string.setcat_artists_subtitle),
                                    leadingIcon = { Icon(Icons.Outlined.Person, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely(Screen.ArtistSettings.route) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_filtering)) {
                                SliderSettingsItem(
                                    label = stringResource(R.string.setcat_min_song_duration),
                                    value = minSongDurationDraft,
                                    valueRange = 0f..120000f,
                                    steps = 23, // 0, 5, 10, 15, ... 120 seconds (24 positions, 23 steps)
                                    onValueChange = { minSongDurationDraft = it },
                                    onValueChangeFinished = {
                                        val selectedDuration = minSongDurationDraft.toInt()
                                        if (selectedDuration != uiState.minSongDuration) {
                                            settingsViewModel.setMinSongDuration(selectedDuration)
                                        }
                                    },
                                    valueText = { value -> "${(value / 1000).toInt()}s" }
                                )
                                SliderSettingsItem(
                                    label = stringResource(R.string.setcat_min_tracks_per_album),
                                    value = minTracksPerAlbumDraft,
                                    valueRange = 1f..5f,
                                    steps = 3, // 1, 2, 3, 4, 5
                                    onValueChange = { minTracksPerAlbumDraft = it },
                                    onValueChangeFinished = {
                                        val selectedTracks = minTracksPerAlbumDraft.toInt()
                                        if (selectedTracks != uiState.minTracksPerAlbum) {
                                            settingsViewModel.setMinTracksPerAlbum(selectedTracks)
                                        }
                                    },
                                    valueText = { value -> "${value.toInt()}" }
                                )
                                SliderSettingsItem(
                                    label = stringResource(R.string.setcat_album_art_cache_limit),
                                    value = albumArtCacheLimitDraft,
                                    valueRange = 50f..1500f,
                                    steps = 28, // 50, 100, 150, ... 1500 (30 stops)
                                    onValueChange = { albumArtCacheLimitDraft = it },
                                    onValueChangeFinished = {
                                        val selectedLimit = albumArtCacheLimitDraft.toInt()
                                        if (selectedLimit != uiState.albumArtCacheLimitMb) {
                                            settingsViewModel.setAlbumArtCacheLimitMb(selectedLimit)
                                        }
                                    },
                                    valueText = { value -> "${value.toInt()} MB" }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_sync_scanning)) {
                                RefreshLibraryItem(
                                    isSyncing = isSyncing,
                                    syncProgress = syncProgress,
                                    activeOperationLabel = if (isSyncing) syncIndicatorLabel else null,
                                    onFullSync = {
                                        if (isSyncing) return@RefreshLibraryItem
                                        refreshRequested = true
                                        syncRequestObservedRunning = false
                                        syncIndicatorLabel = context.getString(R.string.setcat_sync_full_rescan_label)
                                        Toast.makeText(context, context.getString(R.string.toast_full_rescan_started), Toast.LENGTH_SHORT).show()
                                        settingsViewModel.fullSyncLibrary()
                                    },
                                    onRebuild = {
                                        if (isSyncing) return@RefreshLibraryItem
                                        showRebuildDatabaseWarning = true
                                    }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_auto_scan_lrc_title),
                                    subtitle = stringResource(R.string.setcat_auto_scan_lrc_subtitle),
                                    checked = uiState.autoScanLrcFiles,
                                    onCheckedChange = { settingsViewModel.setAutoScanLrcFiles(it) },
                                    leadingIcon = { Icon(Icons.Outlined.Folder, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(
                                title = stringResource(R.string.setcat_lyrics_management),
                                addBottomSpace = false
                            ) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_lyrics_source_priority_label),
                                    description = stringResource(R.string.setcat_lyrics_source_priority_desc),
                                    options = mapOf(
                                        LyricsSourcePreference.EMBEDDED_FIRST.name to stringResource(R.string.setcat_lyrics_embedded_first),
                                        LyricsSourcePreference.API_FIRST.name to stringResource(R.string.setcat_lyrics_online_first),
                                        LyricsSourcePreference.LOCAL_FIRST.name to stringResource(R.string.setcat_lyrics_local_first)
                                    ),
                                    selectedKey = uiState.lyricsSourcePreference.name,
                                    onSelectionChanged = { key ->
                                        settingsViewModel.setLyricsSourcePreference(
                                            LyricsSourcePreference.fromName(key)
                                        )
                                    },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_reset_imported_lyrics_title),
                                    subtitle = stringResource(R.string.setcat_reset_imported_lyrics_subtitle),
                                    leadingIcon = { Icon(Icons.Outlined.ClearAll, null, tint = MaterialTheme.colorScheme.secondary) },
                                    onClick = { showClearLyricsDialog = true }
                                )
                            }
                        }
                        SettingsCategory.APPEARANCE -> {
                            val useSmoothCorners by settingsViewModel.useSmoothCorners.collectAsStateWithLifecycle()

                            SettingsSubsection(title = stringResource(R.string.setcat_global_theme)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_language_label),
                                    description = stringResource(R.string.setcat_language_desc),
                                    options = AppLanguage.getLanguageOptions(context),
                                    selectedKey = uiState.appLanguageTag,
                                    onSelectionChanged = {
                                        settingsViewModel.setAppLanguage(it)
                                        (context as? Activity)?.recreate()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Language, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_app_theme_label),
                                    description = stringResource(R.string.setcat_app_theme_desc),
                                    options = mapOf(
                                        AppThemeMode.LIGHT to stringResource(R.string.setcat_theme_light),
                                        AppThemeMode.DARK to stringResource(R.string.setcat_theme_dark),
                                        AppThemeMode.FOLLOW_SYSTEM to stringResource(R.string.setcat_theme_follow_system)
                                    ),
                                    selectedKey = uiState.appThemeMode,
                                    onSelectionChanged = { settingsViewModel.setAppThemeMode(it) },
                                    leadingIcon = { Icon(Icons.Outlined.LightMode, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_smooth_corners_title),
                                    subtitle = stringResource(R.string.setcat_smooth_corners_subtitle),
                                    checked = useSmoothCorners,
                                    onCheckedChange = settingsViewModel::setUseSmoothCorners,
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_now_playing)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_player_theme_label),
                                    description = stringResource(R.string.setcat_player_theme_desc),
                                    options = mapOf(
                                        ThemePreference.ALBUM_ART to stringResource(R.string.setcat_player_theme_album_art),
                                        ThemePreference.DYNAMIC to stringResource(R.string.setcat_player_theme_dynamic)
                                    ),
                                    selectedKey = uiState.playerThemePreference,
                                    onSelectionChanged = { settingsViewModel.setPlayerThemePreference(it) },
                                    leadingIcon = { Icon(Icons.Outlined.PlayCircle, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_show_player_file_info_title),
                                    subtitle = stringResource(R.string.setcat_show_player_file_info_subtitle),
                                    checked = uiState.showPlayerFileInfo,
                                    onCheckedChange = { settingsViewModel.setShowPlayerFileInfo(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_attach_file_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_album_art_palette_title),
                                    subtitle = stringResource(R.string.setcat_album_art_palette_subtitle, uiState.albumArtPaletteStyle.label),
                                    leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely(Screen.PaletteStyle.route) }
                                )
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_carousel_style_label),
                                    description = stringResource(R.string.setcat_carousel_style_desc),
                                    options = mapOf(
                                        CarouselStyle.NO_PEEK to stringResource(R.string.setcat_carousel_no_peek),
                                        CarouselStyle.ONE_PEEK to stringResource(R.string.setcat_carousel_one_peek)
                                    ),
                                    selectedKey = uiState.carouselStyle,
                                    onSelectionChanged = { settingsViewModel.setCarouselStyle(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_view_carousel_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_home_collage)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_collage_pattern_label),
                                    description = stringResource(R.string.setcat_collage_pattern_desc),
                                    options = CollagePattern.entries.associate { it.storageKey to it.label },
                                    selectedKey = uiState.collagePattern.storageKey,
                                    onSelectionChanged = { key ->
                                        settingsViewModel.setCollagePattern(CollagePattern.fromStorageKey(key))
                                    },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_view_column_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_auto_rotate_patterns_title),
                                    subtitle = stringResource(R.string.setcat_auto_rotate_patterns_subtitle),
                                    checked = uiState.collageAutoRotate,
                                    onCheckedChange = { settingsViewModel.setCollageAutoRotate(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_navigation_bar)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_navbar_style_label),
                                    description = stringResource(R.string.setcat_navbar_style_desc),
                                    options = mapOf(
                                        NavBarStyle.DEFAULT to stringResource(R.string.setcat_navbar_style_default),
                                        NavBarStyle.FULL_WIDTH to stringResource(R.string.setcat_navbar_style_full_width)
                                    ),
                                    selectedKey = uiState.navBarStyle,
                                    onSelectionChanged = { settingsViewModel.setNavBarStyle(it) },
                                    leadingIcon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_compact_mode_title),
                                    subtitle = stringResource(R.string.setcat_compact_mode_subtitle),
                                    checked = uiState.navBarCompactMode,
                                    onCheckedChange = { settingsViewModel.setNavBarCompactMode(it) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.rounded_view_week_24),
                                            null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_navbar_corner_title),
                                    subtitle = stringResource(R.string.setcat_navbar_corner_subtitle),
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_rounded_corner_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely("nav_bar_corner_radius") }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_lyrics_screen)) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_immersive_lyrics_title),
                                    subtitle = stringResource(R.string.setcat_immersive_lyrics_subtitle),
                                    checked = uiState.immersiveLyricsEnabled,
                                    onCheckedChange = { settingsViewModel.setImmersiveLyricsEnabled(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_lyrics_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )

                                if (uiState.immersiveLyricsEnabled) {
                                    ThemeSelectorItem(
                                        label = stringResource(R.string.setcat_auto_hide_delay_label),
                                        description = stringResource(R.string.setcat_auto_hide_delay_desc),
                                        options = mapOf(
                                            "3000" to stringResource(R.string.setcat_lyrics_delay_3s),
                                            "4000" to stringResource(R.string.setcat_lyrics_delay_4s),
                                            "5000" to stringResource(R.string.setcat_lyrics_delay_5s),
                                            "6000" to stringResource(R.string.setcat_lyrics_delay_6s)
                                        ),
                                        selectedKey = uiState.immersiveLyricsTimeout.toString(),
                                        onSelectionChanged = { settingsViewModel.setImmersiveLyricsTimeout(it.toLong()) },
                                        leadingIcon = { Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.secondary) }
                                    )
                                }
                            }

                            SettingsSubsection(
                                title = stringResource(R.string.setcat_app_navigation_section),
                                addBottomSpace = false
                            ) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_default_tab_label),
                                    description = stringResource(R.string.setcat_default_tab_desc),
                                    options = mapOf(
                                        LaunchTab.HOME to stringResource(R.string.tab_home),
                                        LaunchTab.SEARCH to stringResource(R.string.search),
                                        LaunchTab.LIBRARY to stringResource(R.string.tab_library),
                                    ),
                                    selectedKey = uiState.launchTab,
                                    onSelectionChanged = { settingsViewModel.setLaunchTab(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.tab_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_library_navigation_label),
                                    description = stringResource(R.string.setcat_library_navigation_desc),
                                    options = mapOf(
                                        LibraryNavigationMode.TAB_ROW to stringResource(R.string.setcat_library_nav_tab_row),
                                        LibraryNavigationMode.COMPACT_PILL to stringResource(R.string.setcat_library_nav_compact_pill)
                                    ),
                                    selectedKey = uiState.libraryNavigationMode,
                                    onSelectionChanged = { settingsViewModel.setLibraryNavigationMode(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_library_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }
                        }
                        SettingsCategory.PLAYBACK -> {
                            SettingsSubsection(title = stringResource(R.string.setcat_background_playback)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_keep_playing_label),
                                    description = stringResource(R.string.setcat_keep_playing_desc),
                                    options = mapOf("true" to stringResource(R.string.label_on), "false" to stringResource(R.string.label_off)),
                                    selectedKey = if (uiState.keepPlayingInBackground) "true" else "false",
                                    onSelectionChanged = { settingsViewModel.setKeepPlayingInBackground(it.toBoolean()) },
                                    leadingIcon = { Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_battery_optimization_title),
                                    subtitle = stringResource(R.string.setcat_battery_optimization_subtitle),
                                    onClick = {
                                        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                                            Toast.makeText(context, context.getString(R.string.toast_battery_already_disabled), Toast.LENGTH_SHORT).show()
                                            return@SettingsItem
                                        }
                                        try {
                                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = "package:${context.packageName}".toUri()
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(fallbackIntent)
                                            } catch (e2: Exception) {
                                                Toast.makeText(context, context.getString(R.string.toast_battery_settings_unavailable), Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_all_inclusive_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_replaygain_section)) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_replaygain_enable_title),
                                    subtitle = stringResource(R.string.setcat_replaygain_enable_subtitle),
                                    checked = uiState.replayGainEnabled,
                                    onCheckedChange = { settingsViewModel.setReplayGainEnabled(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                AnimatedVisibility(
                                    visible = uiState.replayGainEnabled,
                                    enter = expandVertically(animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)) + fadeIn(animationSpec = spring(stiffness = 400f)),
                                    exit = shrinkVertically(animationSpec = spring(stiffness = 500f)) + fadeOut(animationSpec = spring(stiffness = 500f))
                                ) {
                                    ThemeSelectorItem(
                                        label = stringResource(R.string.setcat_gain_mode_label),
                                        description = stringResource(R.string.setcat_gain_mode_desc),
                                        options = mapOf("track" to stringResource(R.string.setcat_gain_mode_track), "album" to stringResource(R.string.setcat_gain_mode_album)),
                                        selectedKey = if (uiState.replayGainUseAlbumGain) "album" else "track",
                                        onSelectionChanged = { settingsViewModel.setReplayGainUseAlbumGain(it == "album") },
                                        leadingIcon = { Icon(painterResource(R.drawable.rounded_volume_down_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                    )
                                }
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_cast)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_cast_autoplay_label),
                                    description = stringResource(R.string.setcat_cast_autoplay_desc),
                                    options = mapOf("false" to stringResource(R.string.label_enabled), "true" to stringResource(R.string.label_disabled)),
                                    selectedKey = if (uiState.disableCastAutoplay) "true" else "false",
                                    onSelectionChanged = { settingsViewModel.setDisableCastAutoplay(it.toBoolean()) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_cast_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_headphones)) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_headphones_resume_title),
                                    subtitle = stringResource(R.string.setcat_headphones_resume_subtitle),
                                    checked = uiState.resumeOnHeadsetReconnect,
                                    onCheckedChange = { settingsViewModel.setResumeOnHeadsetReconnect(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_headphones_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_queue_transitions)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_crossfade_label),
                                    description = stringResource(R.string.setcat_crossfade_desc),
                                    options = mapOf("true" to stringResource(R.string.label_enabled), "false" to stringResource(R.string.label_disabled)),
                                    selectedKey = if (uiState.isCrossfadeEnabled) "true" else "false",
                                    onSelectionChanged = { settingsViewModel.setCrossfadeEnabled(it.toBoolean()) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_align_justify_space_even_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                if (uiState.isCrossfadeEnabled) {
                                    SliderSettingsItem(
                                        label = stringResource(R.string.setcat_crossfade_duration),
                                        value = uiState.crossfadeDuration.toFloat(),
                                        valueRange = 1000f..12000f,
                                        steps= 10,
                                        onValueChange = { settingsViewModel.setCrossfadeDuration(it.toInt()) },
                                        valueText = { value -> "${(value / 1000).toInt()}s" }
                                    )
                                }
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_hifi_mode_title),
                                    subtitle = if (uiState.hiFiModeDeviceSupported)
                                        stringResource(R.string.setcat_hifi_mode_subtitle_supported)
                                    else
                                        stringResource(R.string.setcat_hifi_mode_subtitle_unsupported),
                                    checked = uiState.hiFiModeEnabled,
                                    onCheckedChange = { settingsViewModel.setHiFiModeEnabled(it) },
                                    enabled = uiState.hiFiModeDeviceSupported,
                                    leadingIcon = { Icon(painterResource(R.drawable.outline_high_quality_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_persistent_shuffle_title),
                                    subtitle = stringResource(R.string.setcat_persistent_shuffle_subtitle),
                                    checked = uiState.persistentShuffleEnabled,
                                    onCheckedChange = { settingsViewModel.setPersistentShuffleEnabled(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_shuffle_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_show_queue_history_title),
                                    subtitle = stringResource(R.string.setcat_show_queue_history_subtitle),
                                    checked = uiState.showQueueHistory,
                                    onCheckedChange = { settingsViewModel.setShowQueueHistory(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_queue_music_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }

                        }
                        SettingsCategory.BEHAVIOR -> {
                            SettingsSubsection(
                                title = stringResource(R.string.setcat_folders)
                            ) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_folder_back_gesture_title),
                                    subtitle = stringResource(R.string.setcat_folder_back_gesture_subtitle),
                                    checked = uiState.folderBackGestureNavigation,
                                    onCheckedChange = { settingsViewModel.setFolderBackGestureNavigation(it) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.rounded_touch_app_24),
                                            null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                )
                            }
                            SettingsSubsection(
                                title = stringResource(R.string.setcat_player_gestures)
                            ) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_tap_bg_closes_title),
                                    subtitle = stringResource(R.string.setcat_tap_bg_closes_subtitle),
                                    checked = uiState.tapBackgroundClosesPlayer,
                                    onCheckedChange = { settingsViewModel.setTapBackgroundClosesPlayer(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }
                            SettingsSubsection(
                                title = stringResource(R.string.setcat_haptics),
                                addBottomSpace = false
                            ) {
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_haptic_feedback_title),
                                    subtitle = stringResource(R.string.setcat_haptic_feedback_subtitle),
                                    checked = uiState.hapticsEnabled,
                                    onCheckedChange = { settingsViewModel.setHapticsEnabled(it) },
                                    leadingIcon = { Icon(painterResource(R.drawable.rounded_touch_app_24), null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                            }
                        }
                        SettingsCategory.AI_INTEGRATION -> {
                            // AI Provider Selection
                            SettingsSubsection(title = stringResource(R.string.setcat_ai_provider_section)) {
                                ThemeSelectorItem(
                                    label = stringResource(R.string.setcat_ai_provider_label),
                                    description = stringResource(R.string.setcat_ai_provider_desc),
                                    options = com.theveloper.pixelplay.data.ai.provider.AiProvider.entries.associate { it.name to it.displayName },
                                    selectedKey = aiProvider,
                                    onSelectionChanged = { settingsViewModel.onAiProviderChange(it) },
                                    leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) }
                                )
                                SwitchSettingItem(
                                    title = stringResource(R.string.setcat_safe_token_title),
                                    subtitle = if (uiState.isSafeTokenLimitEnabled) {
                                        stringResource(R.string.setcat_safe_token_on)
                                    } else {
                                        stringResource(R.string.setcat_safe_token_off)
                                    },
                                    checked = uiState.isSafeTokenLimitEnabled,
                                    onCheckedChange = { settingsViewModel.setSafeTokenLimitEnabled(it) },
                                    leadingIcon = {
                                        Icon(
                                            painterResource(R.drawable.rounded_monitoring_24),
                                            null,
                                            tint = if (uiState.isSafeTokenLimitEnabled) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                )
                            }
                            
                            // Consolidated API Key Section
                            SettingsSubsection(title = stringResource(R.string.setcat_credentials)) {
                                val provider = com.theveloper.pixelplay.data.ai.provider.AiProvider.fromString(aiProvider)
                                val sourceLabel = when(provider) {
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.GEMINI -> stringResource(R.string.setcat_ai_source_gemini)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.DEEPSEEK -> stringResource(R.string.setcat_ai_source_deepseek)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.GROQ -> stringResource(R.string.setcat_ai_source_groq)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.MISTRAL -> stringResource(R.string.setcat_ai_source_mistral)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.NVIDIA -> stringResource(R.string.setcat_ai_source_nvidia)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.KIMI -> stringResource(R.string.setcat_ai_source_kimi)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.GLM -> stringResource(R.string.setcat_ai_source_glm)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.OPENAI -> stringResource(R.string.setcat_ai_source_openai)
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.OPENROUTER -> "OpenRouter (openrouter.ai)"
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.ANTHROPIC -> "Anthropic Claude (anthropic.com)"
                                    com.theveloper.pixelplay.data.ai.provider.AiProvider.OLLAMA -> "Ollama (localhost:11434)"
                                }
                                
                                AiApiKeyItem(
                                    apiKey = currentAiApiKey,
                                    onApiKeySave = { settingsViewModel.onAiApiKeyChange(it) },
                                    title = stringResource(R.string.setcat_ai_api_key_title, provider.displayName),
                                    subtitle = stringResource(R.string.setcat_ai_api_key_subtitle, sourceLabel)
                                )
                            }

                            // Model Selection Section
                            if (currentAiApiKey.isNotBlank() || !com.theveloper.pixelplay.data.ai.provider.AiProvider.fromString(aiProvider).requiresApiKey) {
                                SettingsSubsection(title = stringResource(R.string.setcat_model_selection)) {
                                    if (uiState.isLoadingModels) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = stringResource(R.string.setcat_loading_models),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    } else if (uiState.modelsFetchError != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.errorContainer,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = uiState.modelsFetchError ?: stringResource(R.string.models_fetch_failed),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                        }
                                    } else if (uiState.availableModels.isNotEmpty()) {
                                        ThemeSelectorItem(
                                            label = stringResource(R.string.setcat_ai_model_label),
                                            description = stringResource(R.string.setcat_ai_model_desc),
                                            options = uiState.availableModels.associate { it.name to it.displayName },
                                            selectedKey = currentAiModel.ifEmpty { uiState.availableModels.firstOrNull()?.name ?: "" },
                                            onSelectionChanged = { settingsViewModel.onAiModelChange(it) },
                                            leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) }
                                        )
                                    }
                                }
                            }

                            // Prompt Behavior Section
                            SettingsSubsection(
                                title = stringResource(R.string.setcat_prompt_behavior),
                                addBottomSpace = false
                            ) {
                                AiSystemPromptItem(
                                    systemPrompt = currentAiSystemPrompt,
                                    defaultPrompt = com.theveloper.pixelplay.data.preferences.AiPreferencesRepository.DEFAULT_SYSTEM_PROMPT,
                                    onSystemPromptSave = { settingsViewModel.onAiSystemPromptChange(it) },
                                    onReset = { settingsViewModel.resetAiSystemPrompt() },
                                    title = stringResource(R.string.setcat_system_prompt_title),
                                    subtitle = stringResource(R.string.setcat_system_prompt_subtitle)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            SettingsSubsection(title = stringResource(R.string.setcat_ai_usage_report)) {
                                val recentAiUsage by settingsViewModel.recentAiUsage.collectAsStateWithLifecycle()
                                val totalPromptTokens by settingsViewModel.totalPromptTokens.collectAsStateWithLifecycle()
                                val totalOutputTokens by settingsViewModel.totalOutputTokens.collectAsStateWithLifecycle()
                                val totalThoughtTokens by settingsViewModel.totalThoughtTokens.collectAsStateWithLifecycle()

                                val totalTokens = totalPromptTokens + totalOutputTokens + totalThoughtTokens
                                val totalTokStr = String.format(Locale.US, "%,d", totalTokens)
                                val promptTokStr = String.format(Locale.US, "%,d", totalPromptTokens)
                                val outputTokStr = String.format(Locale.US, "%,d", totalOutputTokens)
                                val thoughtTokStr = String.format(Locale.US, "%,d", totalThoughtTokens)

                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_total_consumption_title),
                                    subtitle = stringResource(
                                        R.string.setcat_ai_usage_tokens_subtitle,
                                        totalTokStr,
                                        promptTokStr,
                                        outputTokStr,
                                        thoughtTokStr
                                    ),
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.rounded_monitoring_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary
                                        )
                                    },
                                    primaryActionLabel = stringResource(R.string.setcat_ai_clear_logs),
                                    onPrimaryAction = { settingsViewModel.clearAiUsageData() }
                                )

                                if (recentAiUsage.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    var expanded by remember { mutableStateOf(false) }
                                    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f)
                                    
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { expanded = !expanded },
                                        color = Color.Transparent
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    painter = painterResource(R.drawable.rounded_monitoring_24),
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = stringResource(R.string.setcat_ai_activity_log_title, recentAiUsage.size),
                                                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.ExpandMore,
                                                contentDescription = if (expanded) stringResource(R.string.cd_hide) else stringResource(R.string.cd_show),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.rotate(rotation)
                                            )
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = expanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp, bottom = 8.dp)
                                        ) {
                                            val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                                            val groupedUsage = recentAiUsage.groupBy { 
                                                dateFormat.format(Date(it.timestamp)) 
                                            }

                                            groupedUsage.forEach { (date, items) ->
                                                AiUsageDateHeader(date = date)
                                                items.forEach { usage ->
                                                    AiUsageLogItem(usage = usage)
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        SettingsCategory.BACKUP_RESTORE -> {
                            if (!uiState.backupInfoDismissed) {
                                BackupInfoNoticeCard(
                                    onDismiss = { settingsViewModel.setBackupInfoDismissed(true) }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_create_backup)) {
                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_export_backup_title),
                                    subtitle = stringResource(
                                        R.string.setcat_export_backup_subtitle,
                                        buildBackupSelectionSummary(context, exportSections)
                                    ),
                                    icon = {
                                        Icon(
                                            painter = painterResource(R.drawable.outline_save_24),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    primaryActionLabel = stringResource(R.string.setcat_select_export),
                                    onPrimaryAction = { showExportDataDialog = true },
                                    enabled = !uiState.isDataTransferInProgress
                                )
                            }

                            SettingsSubsection(
                                title = stringResource(R.string.setcat_restore_backup_section),
                                addBottomSpace = false
                            ) {
                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_import_backup_title),
                                    subtitle = stringResource(R.string.setcat_import_backup_subtitle),
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Restore,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                    },
                                    primaryActionLabel = stringResource(R.string.setcat_select_restore),
                                    onPrimaryAction = { showImportFlow = true },
                                    enabled = !uiState.isDataTransferInProgress
                                )
                            }
                        }
                        SettingsCategory.DEVELOPER -> {
                            SettingsSubsection(title = stringResource(R.string.setcat_experiments)) {
                                SettingsItem(
                                    title = stringResource(R.string.setcat_experimental_title),
                                    subtitle = stringResource(R.string.setcat_experimental_subtitle),
                                    leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, stringResource(R.string.cd_open), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely(Screen.Experimental.route) }
                                )
                                SettingsItem(
                                    title = stringResource(R.string.setcat_test_setup_title),
                                    subtitle = stringResource(R.string.setcat_test_setup_subtitle),
                                    leadingIcon = { Icon(Icons.Rounded.Science, null, tint = MaterialTheme.colorScheme.tertiary) },
                                    onClick = {
                                        settingsViewModel.resetSetupFlow()
                                    }
                                )
                            }

                            SettingsSubsection(title = stringResource(R.string.setcat_maintenance)) {
                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_force_daily_mix_title),
                                    subtitle = stringResource(R.string.setcat_force_daily_mix_subtitle),
                                    icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                    primaryActionLabel = stringResource(R.string.setcat_regenerate_daily_mix_action),
                                    onPrimaryAction = { showRegenerateDailyMixDialog = true }
                                )
                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_force_stats_title),
                                    subtitle = stringResource(R.string.setcat_force_stats_subtitle),
                                    icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.secondary) },
                                    primaryActionLabel = stringResource(R.string.setcat_regenerate_stats_action),
                                    onPrimaryAction = { showRegenerateStatsDialog = true }
                                )
                                ActionSettingsItem(
                                    title = stringResource(R.string.setcat_force_palette_title),
                                    subtitle = if (paletteRegenerateTargets.isEmpty()) {
                                        stringResource(R.string.setcat_force_palette_empty)
                                    } else {
                                        stringResource(R.string.setcat_force_palette_subtitle)
                                    },
                                    icon = { Icon(Icons.Outlined.Style, null, tint = MaterialTheme.colorScheme.secondary) },
                                    primaryActionLabel = if (isPaletteBulkRegenerateRunning) stringResource(R.string.setcat_regenerating) else stringResource(R.string.setcat_regenerate_all),
                                    onPrimaryAction = { showRegenerateAllPalettesDialog = true },
                                    secondaryActionLabel = stringResource(R.string.setcat_choose_song),
                                    onSecondaryAction = { showPaletteRegenerateSheet = true },
                                    enabled = paletteRegenerateTargets.isNotEmpty() && !isAnyPaletteRegenerateRunning
                                )
                            }

                            SettingsSubsection(
                                title = stringResource(R.string.setcat_diagnostics),
                                addBottomSpace = false
                            ) {
                                SettingsItem(
                                    title = stringResource(R.string.setcat_trigger_crash_title),
                                    subtitle = stringResource(R.string.setcat_trigger_crash_subtitle),
                                    leadingIcon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { settingsViewModel.triggerTestCrash() }
                                )
                            }
                        }
                        SettingsCategory.ABOUT -> {
                            SettingsSubsection(
                                title = stringResource(R.string.setcat_application),
                                addBottomSpace = false
                            ) {
                                SettingsItem(
                                    title = stringResource(R.string.setcat_about_pixelplayer_title),
                                    subtitle = stringResource(R.string.setcat_about_pixelplayer_subtitle),
                                    leadingIcon = { Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.secondary) },
                                    trailingIcon = { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                    onClick = { navController.navigateSafely("about") }
                                )
                            }
                        }
                        SettingsCategory.EQUALIZER -> {
                             // Equalizer has its own screen, so this block is unreachable via standard navigation
                             // but required for exhaustiveness.
                        }
                        SettingsCategory.DEVICE_CAPABILITIES -> {
                             // Device Capabilities has its own screen
                        }

                    }
               }
            }

            item {
                // Spacer handled by contentPadding
                Spacer(Modifier.height(1.dp))
            }
        }

        CollapsibleCommonTopBar(
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onBackClick,
            title = categoryTitle,
            maxLines = titleMaxLines
        )

        // Block interaction during transition
        var isTransitioning by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(com.theveloper.pixelplay.presentation.navigation.TRANSITION_DURATION.toLong())
            isTransitioning = false
        }
        
        if (isTransitioning) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                   awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent()
                        }
                    }
                }
            )
        }
    }

    BackupTransferProgressDialogHost(progress = dataTransferProgress)

    // Dialogs
    FileExplorerDialog(
        visible = showExplorerSheet,
        currentPath = currentPath,
        directoryChildren = directoryChildren,
        availableStorages = availableStorages,
        selectedStorageIndex = selectedStorageIndex,
        isLoading = isLoadingDirectories,
        isPriming = isExplorerPriming,
        isReady = isExplorerReady,
        isCurrentDirectoryResolved = isCurrentDirectoryResolved,
        isAtRoot = settingsViewModel.isAtRoot(),
        rootDirectory = explorerRoot,
        onNavigateTo = settingsViewModel::loadDirectory,
        onNavigateUp = settingsViewModel::navigateUp,
        onNavigateHome = { settingsViewModel.loadDirectory(explorerRoot) },
        onToggleAllowed = settingsViewModel::toggleDirectoryAllowed,
        onRefresh = settingsViewModel::refreshExplorer,
        onStorageSelected = settingsViewModel::selectStorage,
        onDone = {
            settingsViewModel.applyPendingDirectoryRuleChanges()
            showExplorerSheet = false
        },
        onDismiss = {
            settingsViewModel.applyPendingDirectoryRuleChanges()
            showExplorerSheet = false
        }
    )

    if (showPaletteRegenerateSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                if (!isAnyPaletteRegenerateRunning) {
                    showPaletteRegenerateSheet = false
                    paletteSongSearchQuery = ""
                }
            },
            sheetState = paletteRegenerateSheetState
        ) {
            PaletteRegenerateSongSheetContent(
                songs = filteredPaletteSongs,
                isRunning = isPaletteRegenerateRunning,
                searchQuery = paletteSongSearchQuery,
                onSearchQueryChange = { paletteSongSearchQuery = it },
                onClearSearch = { paletteSongSearchQuery = "" },
                onSongClick = { song ->
                    if (isAnyPaletteRegenerateRunning) return@PaletteRegenerateSongSheetContent
                    isPaletteRegenerateRunning = true
                    coroutineScope.launch {
                        val success = playerViewModel.forceRegenerateAlbumPaletteForSong(song)
                        isPaletteRegenerateRunning = false
                        if (success) {
                            showPaletteRegenerateSheet = false
                            paletteSongSearchQuery = ""
                            Toast.makeText(
                                context,
                                context.getString(R.string.dialog_palette_regenerated, song.title),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.dialog_palette_regenerate_failed, song.title),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            )
        }
    }

    if (showRegenerateAllPalettesDialog) {
        AlertDialog(
            icon = {
                Icon(
                    Icons.Outlined.Style,
                    null,
                    tint = if (isPaletteBulkRegenerateRunning) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            },
            title = {
                Text(
                    if (isPaletteBulkRegenerateRunning) {
                        stringResource(R.string.dialog_regenerating_palettes_title)
                    } else {
                        stringResource(R.string.dialog_regenerate_palettes_title)
                    }
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isPaletteBulkRegenerateRunning) {
                            stringResource(R.string.dialog_regenerate_palettes_progress_body, paletteBulkTotalCount)
                        } else {
                            stringResource(R.string.dialog_regenerate_palettes_confirm_body, paletteRegenerateTargets.size)
                        }
                    )

                    if (isPaletteBulkRegenerateRunning) {
                        val progress = if (paletteBulkTotalCount > 0) {
                            paletteBulkCompletedCount.toFloat() / paletteBulkTotalCount.toFloat()
                        } else {
                            0f
                        }

                        LinearWavyProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )

                        Text(
                            text = stringResource(R.string.dialog_palette_progress_format, paletteBulkCompletedCount, paletteBulkTotalCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            onDismissRequest = {
                if (!isPaletteBulkRegenerateRunning) {
                    showRegenerateAllPalettesDialog = false
                }
            },
            confirmButton = {
                if (isPaletteBulkRegenerateRunning) {
                    TextButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Text(stringResource(R.string.working_ellipsis), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else {
                    TextButton(
                        onClick = {
                            isPaletteBulkRegenerateRunning = true
                            paletteBulkCompletedCount = 0
                            paletteBulkTotalCount = paletteRegenerateTargets.size

                            coroutineScope.launch {
                                var successCount = 0
                                paletteRegenerateTargets.forEachIndexed { index, song ->
                                    if (playerViewModel.forceRegenerateAlbumPaletteForSong(song)) {
                                        successCount++
                                    }
                                    paletteBulkCompletedCount = index + 1
                                }

                                isPaletteBulkRegenerateRunning = false
                                showRegenerateAllPalettesDialog = false

                                val totalCount = paletteRegenerateTargets.size
                                Toast.makeText(
                                    context,
                                    if (successCount == totalCount) {
                                        context.getString(R.string.toast_regenerated_palettes_all, successCount)
                                    } else {
                                        context.getString(R.string.toast_regenerated_palettes_partial, successCount, totalCount)
                                    },
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.dialog_regenerate))
                    }
                }
            },
            dismissButton = {
                if (!isPaletteBulkRegenerateRunning) {
                    TextButton(
                        onClick = { showRegenerateAllPalettesDialog = false }
                    ) {
                        Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        )
    }
    
     // Dialogs logic (copied)
    if (showClearLyricsDialog) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null) },
            title = { Text(stringResource(R.string.dialog_reset_lyrics_title)) },
            text = { Text(stringResource(R.string.dialog_cannot_undo)) },
            onDismissRequest = { showClearLyricsDialog = false },
            confirmButton = { TextButton(onClick = { showClearLyricsDialog = false; playerViewModel.resetAllLyrics() }) { Text(stringResource(R.string.confirm), maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            dismissButton = { TextButton(onClick = { showClearLyricsDialog = false }) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    
    if (showRebuildDatabaseWarning) {
        AlertDialog(
            icon = { Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.dialog_rebuild_database_title)) },
            text = { Text(stringResource(R.string.dialog_rebuild_database_message)) },
            onDismissRequest = { showRebuildDatabaseWarning = false },
            confirmButton = { 
                TextButton(
                    onClick = { 
                        showRebuildDatabaseWarning = false
                        refreshRequested = true
                        syncRequestObservedRunning = false
                        syncIndicatorLabel = context.getString(R.string.sync_indicator_rebuilding)
                        Toast.makeText(context, context.getString(R.string.toast_rebuilding_database), Toast.LENGTH_SHORT).show()
                        settingsViewModel.rebuildDatabase() 
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text(stringResource(R.string.rebuild)) 
                } 
            },
            dismissButton = { TextButton(onClick = { showRebuildDatabaseWarning = false }) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    if (showRegenerateDailyMixDialog) {
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_instant_mix_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.dialog_regenerate_daily_mix_title)) },
            text = { Text(stringResource(R.string.dialog_regenerate_daily_mix_body)) },
            onDismissRequest = { showRegenerateDailyMixDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateDailyMixDialog = false
                        playerViewModel.forceUpdateDailyMix()
                        Toast.makeText(context, context.getString(R.string.toast_daily_mix_regeneration_started), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.dialog_regenerate), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateDailyMixDialog = false }) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    if (showRegenerateStatsDialog) {
        AlertDialog(
            icon = { Icon(painterResource(R.drawable.rounded_monitoring_24), null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.dialog_regenerate_stats_title)) },
            text = { Text(stringResource(R.string.dialog_regenerate_stats_body)) },
            onDismissRequest = { showRegenerateStatsDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRegenerateStatsDialog = false
                        statsViewModel.forceRegenerateStats()
                        Toast.makeText(context, context.getString(R.string.toast_stats_regeneration_started), Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(stringResource(R.string.dialog_regenerate), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = { TextButton(onClick = { showRegenerateStatsDialog = false }) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) } }
        )
    }

    if (showExportDataDialog) {
        BackupSectionSelectionDialog(
            operation = BackupOperationType.EXPORT,
            title = stringResource(R.string.setcat_export_backup_title),
            supportingText = stringResource(R.string.backup_dialog_supporting_export),
            selectedSections = exportSections,
            confirmLabel = stringResource(R.string.backup_confirm_export_pxpl),
            inProgress = uiState.isDataTransferInProgress,
            onDismiss = { showExportDataDialog = false },
            onSelectionChanged = { exportSections = it },
            onConfirm = {
                showExportDataDialog = false
                val fileName = context.getString(R.string.backup_file_name_format, System.currentTimeMillis())
                exportLauncher.launch(fileName)
            }
        )
    }

    if (showImportFlow) {
        val restorePlan = uiState.restorePlan
        if (restorePlan != null && importFileUri != null) {
            // Step 2: Module selection from inspected backup
            ImportModuleSelectionDialog(
                plan = restorePlan,
                inProgress = uiState.isDataTransferInProgress,
                onDismiss = {
                    showImportFlow = false
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onBack = {
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onSelectionChanged = { settingsViewModel.updateRestorePlanSelection(it) },
                onConfirm = {
                    settingsViewModel.restoreFromPlan(importFileUri!!)
                    showImportFlow = false
                    importFileUri = null
                }
            )
        } else {
            // Step 1: File selection with backup history
            ImportFileSelectionDialog(
                backupHistory = uiState.backupHistory,
                isInspecting = uiState.isInspectingBackup,
                onDismiss = {
                    showImportFlow = false
                    importFileUri = null
                    settingsViewModel.clearRestorePlan()
                },
                onBrowseFile = { importFilePicker.launch("*/*") },
                onHistoryItemSelected = { entry ->
                    val uri = entry.uri.toUri()
                    importFileUri = uri
                    settingsViewModel.inspectBackupFile(uri)
                },
                onRemoveHistoryEntry = { settingsViewModel.removeBackupHistoryEntry(it) }
            )
        }
    }
}

private fun buildBackupSelectionSummary(context: Context, selected: Set<BackupSection>): String {
    if (selected.isEmpty()) return context.getString(R.string.backup_summary_none)
    val total = BackupSection.entries.size
    return if (selected.size == total) {
        context.getString(R.string.backup_summary_all)
    } else {
        context.getString(R.string.backup_summary_partial, selected.size, total)
    }
}

private fun backupSectionIconRes(section: BackupSection): Int {
    return section.iconRes
}

@Composable
private fun BackupInfoNoticeCard(
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_upload_file_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.backup_how_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.backup_how_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.rounded_close_24),
                    contentDescription = stringResource(R.string.cd_close_notice),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupSectionSelectionDialog(
    operation: BackupOperationType,
    title: String,
    supportingText: String,
    selectedSections: Set<BackupSection>,
    confirmLabel: String,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onSelectionChanged: (Set<BackupSection>) -> Unit,
    onConfirm: () -> Unit
) {
    val listState = rememberLazyListState()
    val selectedCount = selectedSections.size
    val totalCount = BackupSection.entries.size
    val transitionState = remember { MutableTransitionState(false) }
    var shouldShowDialog by remember { mutableStateOf(true) }
    var onDialogHiddenAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    transitionState.targetState = shouldShowDialog

    fun closeDialog(afterClose: () -> Unit) {
        if (!shouldShowDialog) return
        onDialogHiddenAction = afterClose
        shouldShowDialog = false
    }

    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (!transitionState.currentState && !transitionState.targetState) {
            onDialogHiddenAction?.let { action ->
                onDialogHiddenAction = null
                action()
            }
        }
    }

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = { closeDialog(onDismiss) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "backup_section_dialog"
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentWindowInsets = WindowInsets.systemBars,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 24.sp,
                                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                                        ),
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                navigationIcon = {
                                    FilledIconButton(
                                        modifier = Modifier.padding(start = 6.dp),
                                        onClick = { closeDialog(onDismiss) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_close)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        },
                        bottomBar = {
                            BottomAppBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledIconButton(
                                            onClick = { onSelectionChanged(BackupSection.entries.toSet()) },
                                            enabled = !inProgress,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.round_select_all_24),
                                                contentDescription = stringResource(R.string.cd_select_all)
                                            )
                                        }
                                        FilledIconButton(
                                            onClick = { onSelectionChanged(emptySet()) },
                                            enabled = !inProgress,
                                            colors = IconButtonDefaults.filledIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                contentColor = MaterialTheme.colorScheme.onSurface
                                            )
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.baseline_deselect_24),
                                                contentDescription = stringResource(R.string.cd_clear_selection)
                                            )
                                        }
                                    }

                                    ExtendedFloatingActionButton(
                                        onClick = { closeDialog(onConfirm) },
                                        modifier = Modifier
                                            .padding(end = 6.dp)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = if (operation == BackupOperationType.EXPORT) {
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.primaryContainer
                                        },
                                        contentColor = if (operation == BackupOperationType.EXPORT) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        }
                                    ) {
                                        if (inProgress) {
                                            LoadingIndicator(modifier = Modifier.height(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (operation == BackupOperationType.EXPORT) stringResource(R.string.backup_exporting) else stringResource(R.string.backup_importing),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        if (operation == BackupOperationType.EXPORT) {
                                                            R.drawable.outline_save_24
                                                        } else {
                                                            R.drawable.rounded_upload_file_24
                                                        }
                                                    ),
                                                    contentDescription = confirmLabel
                                                )

                                                Text(
                                                    text = if (operation == BackupOperationType.EXPORT) {
                                                        stringResource(R.string.setcat_export_backup_title)
                                                    } else {
                                                        stringResource(R.string.setcat_import_backup_title)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = supportingText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = stringResource(R.string.backup_sections_selected_format, selectedCount, totalCount),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (inProgress) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LoadingIndicator(modifier = Modifier.height(24.dp))
                                            Text(
                                                text = stringResource(R.string.backup_transfer_in_progress),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(BackupSection.entries, key = { it.key }) { section ->
                                    val isSelected = section in selectedSections
                                    BackupSectionSelectableCard(
                                        section = section,
                                        selected = isSelected,
                                        enabled = !inProgress,
                                        onToggle = {
                                            onSelectionChanged(
                                                if (isSelected) selectedSections - section else selectedSections + section
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun BackupSectionSelectableCard(
    section: BackupSection,
    selected: Boolean,
    enabled: Boolean,
    detail: ModuleRestoreDetail? = null,
    onToggle: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        } else {
            Color.Transparent
        },
        label = "backup_section_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 1.dp,
        label = "backup_section_border_width"
    )
    val iconContainerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "backup_section_icon_bg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "backup_section_icon_tint"
    )

    Surface(
        onClick = onToggle,
        enabled = enabled,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(width = borderWidth, color = borderColor),
        tonalElevation = if (selected) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = iconContainerColor,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(backupSectionIconRes(section)),
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = GoogleSansRounded
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = section.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (detail != null && detail.entryCount > 0) {
                        Text(
                            text = stringResource(R.string.backup_entries_will_replace, detail.entryCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Switch(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    enabled = enabled,
                    thumbContent = {
                        AnimatedContent(
                            targetState = selected,
                            transitionSpec = { fadeIn(tween(100)) togetherWith fadeOut(tween(100)) },
                            label = "switch_thumb_icon"
                        ) { isSelected ->
                            Icon(
                                imageVector = if (isSelected) Icons.Rounded.Check else Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(SwitchDefaults.IconSize)
                            )
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedIconColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedIconColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
        }
    }
}

private const val BackupTransferDialogMinimumVisibilityMs = 1500L

@Composable
private fun BackupTransferProgressDialogHost(progress: BackupTransferProgressUpdate?) {
    var visibleProgress by remember { mutableStateOf<BackupTransferProgressUpdate?>(null) }
    var visibleSinceMs by remember { mutableStateOf(0L) }
    var isHoldingForMinimumTime by remember { mutableStateOf(false) }

    LaunchedEffect(progress) {
        if (progress != null) {
            if (visibleProgress == null || isHoldingForMinimumTime) {
                visibleSinceMs = SystemClock.elapsedRealtime()
            }
            isHoldingForMinimumTime = false
            visibleProgress = progress
            return@LaunchedEffect
        }

        val currentVisibleProgress = visibleProgress ?: return@LaunchedEffect
        isHoldingForMinimumTime = true
        val elapsed = SystemClock.elapsedRealtime() - visibleSinceMs
        val remaining = BackupTransferDialogMinimumVisibilityMs - elapsed
        if (remaining > 0) {
            delay(remaining)
        }
        if (visibleProgress == currentVisibleProgress) {
            visibleProgress = null
            visibleSinceMs = 0L
        }
        isHoldingForMinimumTime = false
    }

    visibleProgress?.let { currentProgress ->
        BackupTransferProgressDialog(progress = currentProgress)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BackupTransferProgressDialog(progress: BackupTransferProgressUpdate) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "BackupTransferProgress"
    )
    val progressPercent = (animatedProgress * 100f).roundToInt().coerceIn(0, 100)
    val statusText = when (progress.operation) {
        BackupOperationType.EXPORT -> stringResource(R.string.backup_exporting)
        else -> stringResource(R.string.backup_importing)
    }
    val stepText = stringResource(
        R.string.backup_step_format,
        progress.step.coerceAtLeast(1),
        progress.totalSteps
    )

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (progress.operation == BackupOperationType.EXPORT) {
                        stringResource(R.string.backup_dialog_creating)
                    } else {
                        stringResource(R.string.backup_dialog_restoring)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )

                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.84f),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.presentation_batch_f_percent, progressPercent),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize * 1.4f
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                LinearWavyProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )

                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.presentation_batch_f_status_bullet_step, statusText, stepText),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                AnimatedContent(
                    targetState = progress.detail,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "BackupStepDetail"
                ) { animatedDetail ->
                    Text(
                        text = animatedDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                progress.section?.let { section ->
                    Text(
                        text = section.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportFileSelectionDialog(
    backupHistory: List<BackupHistoryEntry>,
    isInspecting: Boolean,
    onDismiss: () -> Unit,
    onBrowseFile: () -> Unit,
    onHistoryItemSelected: (BackupHistoryEntry) -> Unit,
    onRemoveHistoryEntry: (BackupHistoryEntry) -> Unit
) {
    val context = LocalContext.current
    val transitionState = remember { MutableTransitionState(false) }
    var shouldShowDialog by remember { mutableStateOf(true) }
    var onDialogHiddenAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    transitionState.targetState = shouldShowDialog

    fun closeDialog(afterClose: () -> Unit) {
        if (!shouldShowDialog) return
        onDialogHiddenAction = afterClose
        shouldShowDialog = false
    }

    LaunchedEffect(transitionState.currentState, transitionState.targetState) {
        if (!transitionState.currentState && !transitionState.targetState) {
            onDialogHiddenAction?.let { action ->
                onDialogHiddenAction = null
                action()
            }
        }
    }

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = { closeDialog(onDismiss) },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200)),
                label = "import_file_selection_dialog"
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        contentWindowInsets = WindowInsets.systemBars,
                        topBar = {
                            CenterAlignedTopAppBar(
                                title = {
                                    Text(
                                        text = stringResource(R.string.import_backup_toolbar_title),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontSize = 24.sp,
                                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                                        ),
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                navigationIcon = {
                                    FilledIconButton(
                                        modifier = Modifier.padding(start = 6.dp),
                                        onClick = { closeDialog(onDismiss) },
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                            contentColor = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.cd_close)
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                                )
                            )
                        },
                        bottomBar = {
                            BottomAppBar(
                                windowInsets = WindowInsets.navigationBars,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ExtendedFloatingActionButton(
                                        onClick = onBrowseFile,
                                        modifier = Modifier.height(48.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    ) {
                                        if (isInspecting) {
                                            LoadingIndicator(modifier = Modifier.height(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.import_inspecting),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.rounded_upload_file_24),
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.import_browse_file),
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    ) { innerPadding ->
                        Column(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.62f),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.import_backup_hint),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (backupHistory.isNotEmpty()) {
                                Text(
                                    text = stringResource(R.string.import_recent_backups),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                                )
                            }

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (backupHistory.isEmpty()) {
                                    item {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                                            shape = RoundedCornerShape(18.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(24.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Restore,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(36.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                )
                                                Text(
                                                    text = stringResource(R.string.import_no_recent_title),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = stringResource(R.string.import_no_recent_subtitle),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    items(backupHistory, key = { it.uri }) { entry ->
                                        BackupHistoryCard(
                                            entry = entry,
                                            context = context,
                                            onSelect = { onHistoryItemSelected(entry) },
                                            onRemove = { onRemoveHistoryEntry(entry) }
                                        )
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

@Composable
private fun BackupHistoryCard(
    entry: BackupHistoryEntry,
    context: Context,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    val unknownShort = stringResource(R.string.presentation_batch_f_unknown_short)
    val dateText = remember(entry.createdAt) {
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
        sdf.format(java.util.Date(entry.createdAt))
    }
    val sizeText = remember(entry.sizeBytes) {
        if (entry.sizeBytes > 0) Formatter.formatShortFileSize(context, entry.sizeBytes) else ""
    }
    val moduleCount = entry.modules.size

    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_upload_file_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = GoogleSansRounded
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = dateText +
                            if (sizeText.isNotEmpty()) {
                                stringResource(R.string.presentation_batch_f_dot_space_suffix, sizeText)
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.cd_remove_from_history),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(
                        R.string.backup_history_modules_line,
                        moduleCount,
                        entry.appVersion.ifEmpty { unknownShort },
                        entry.schemaVersion
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImportModuleSelectionDialog(
    plan: RestorePlan,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onSelectionChanged: (Set<BackupSection>) -> Unit,
    onConfirm: () -> Unit
) {
    BackupModuleSelectionDialog(
        plan = plan,
        inProgress = inProgress,
        onDismiss = onDismiss,
        onBack = onBack,
        onSelectionChanged = onSelectionChanged,
        onConfirm = onConfirm
    )
}
@Composable
private fun PaletteRegenerateSongSheetContent(
    songs: List<Song>,
    isRunning: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.palette_force_regenerate_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.palette_force_regenerate_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isRunning,
            placeholder = { Text(stringResource(R.string.search_songs_by_title_artist_album)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = onClearSearch,
                        enabled = !isRunning
                    ) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = stringResource(R.string.cd_clear_search))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        if (isRunning) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.palette_regenerating_progress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 460.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (songs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.search_no_songs_match),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(songs, key = { it.id }) { song ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !isRunning) { onSongClick(song) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.displayArtist,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.album.isNotBlank()) {
                                Text(
                                    text = song.album,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubsectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsSubsection(
    title: String,
    addBottomSpace: Boolean = true,
    content: @Composable () -> Unit
) {
    SettingsSubsectionHeader(title)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Transparent),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        content()
    }
    if (addBottomSpace) {
        Spacer(modifier = Modifier.height(10.dp))
    }
}
