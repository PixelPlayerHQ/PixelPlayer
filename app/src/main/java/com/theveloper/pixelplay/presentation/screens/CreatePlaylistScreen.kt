@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.theveloper.pixelplay.presentation.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.PlaylistShapeType
import com.theveloper.pixelplay.data.model.SmartPlaylistRule
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.presentation.components.ImageCropView
import com.theveloper.pixelplay.presentation.components.SongPickerSelectionPane
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.ShapeCache
import com.theveloper.pixelplay.utils.resolvePlaylistCoverContentColor
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

enum class PlaylistCreationMode {
    MANUAL, SMART
}

@Composable
fun smartPlaylistRuleTitle(rule: SmartPlaylistRule): String = when (rule) {
    SmartPlaylistRule.TOP_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_top_played_title)
    SmartPlaylistRule.RECENTLY_ADDED -> "Recently Added"
    SmartPlaylistRule.RECENTLY_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_recently_played_title)
    SmartPlaylistRule.NEVER_PLAYED -> "Never Played"
    SmartPlaylistRule.LONGEST_SONGS -> "Longest Songs"
    SmartPlaylistRule.SHORTEST_SONGS -> "Shortest Songs"
    SmartPlaylistRule.FORGOTTEN_FAVORITES -> "Forgotten Favorites"
    SmartPlaylistRule.NEW_GEMS -> "New Gems"
}

@Composable
fun smartPlaylistRuleSubtitle(rule: SmartPlaylistRule): String = when (rule) {
    SmartPlaylistRule.TOP_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_top_played_subtitle)
    SmartPlaylistRule.RECENTLY_ADDED -> "Latest songs added to your library"
    SmartPlaylistRule.RECENTLY_PLAYED -> stringResource(R.string.presentation_batch_f_smart_rule_recently_played_subtitle)
    SmartPlaylistRule.NEVER_PLAYED -> "Songs you haven't played yet"
    SmartPlaylistRule.LONGEST_SONGS -> "Songs with the longest duration"
    SmartPlaylistRule.SHORTEST_SONGS -> "Songs with the shortest duration"
    SmartPlaylistRule.FORGOTTEN_FAVORITES -> "Songs you loved but forgot"
    SmartPlaylistRule.NEW_GEMS -> "New songs with low play count"
}

@Composable
fun CreatePlaylistDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            CreatePlaylistContent(
                onDismiss = onDismiss,
                onGenerateClick = onGenerateClick,
                onCreate = onCreate
            )
        }
    }
}

@Composable
fun EditPlaylistDialog(
    visible: Boolean,
    currentName: String,
    currentImageUri: String?,
    currentColor: Int?,
    currentIconName: String?,
    currentShapeType: PlaylistShapeType?,
    currentShapeDetail1: Float?,
    currentShapeDetail2: Float?,
    currentShapeDetail3: Float?,
    currentShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            EditPlaylistContent(
                initialName = currentName,
                initialImageUri = currentImageUri,
                initialColor = currentColor,
                initialIconName = currentIconName,
                initialShapeType = currentShapeType,
                initialShapeDetail1 = currentShapeDetail1,
                initialShapeDetail2 = currentShapeDetail2,
                initialShapeDetail3 = currentShapeDetail3,
                initialShapeDetail4 = currentShapeDetail4,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CreatePlaylistContent(
    onDismiss: () -> Unit,
    onGenerateClick: () -> Unit,
    onCreate: (String, String?, Int?, String?, List<String>, Float, Float, Float, String?, Float?, Float?, Float?, Float?, String?) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var playlistName by remember { mutableStateOf("") }
    var currentStep by remember { mutableStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    var creationMode by remember { mutableStateOf(PlaylistCreationMode.MANUAL) }
    var selectedSmartRule by remember { mutableStateOf(SmartPlaylistRule.TOP_PLAYED) }
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var showCropUi by remember { mutableStateOf(false) }
    var imageBitmap by remember(selectedImageUri) { mutableStateOf<ImageBitmap?>(null) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val defaultColor = MaterialTheme.colorScheme.primaryContainer.toArgb()
    var selectedColor by remember { mutableStateOf<Int?>(defaultColor) }
    var selectedIconName by remember { mutableStateOf<String?>("MusicNote") }
    var selectedShapeType by remember { mutableStateOf(PlaylistShapeType.Circle) }
    var smoothRectCornerRadius by remember { mutableFloatStateOf(20f) }
    var smoothRectSmoothness by remember { mutableFloatStateOf(60f) }
    var starCurve by remember { mutableDoubleStateOf(0.15) }
    var starRotation by remember { mutableFloatStateOf(0f) }
    var starScale by remember { mutableFloatStateOf(1f) }
    var starSides by remember { mutableIntStateOf(5) }

    LaunchedEffect(selectedImageUri) {
         if (selectedImageUri != null) {
             val loader = ImageLoader(context)
             val request = ImageRequest.Builder(context).data(selectedImageUri).allowHardware(false).build()
             val result = loader.execute(request)
             if (result.drawable is android.graphics.drawable.BitmapDrawable) {
                 imageBitmap = (result.drawable as android.graphics.drawable.BitmapDrawable).bitmap.asImageBitmap()
             }
         }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedImageUri = it; showCropUi = true }
    }

    BackHandler(enabled = showCropUi || (currentStep == 1 && creationMode == PlaylistCreationMode.MANUAL)) {
        if (showCropUi) showCropUi = false else currentStep = 0
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (showCropUi) "Adjust Cover" else if (currentStep == 0) "New Playlist" else "Add Songs",
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showCropUi) showCropUi = false else if (currentStep == 1) currentStep = 0 else onDismiss() }) {
                        Icon(if (showCropUi || currentStep == 1) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close, null)
                    }
                }
            )
        },
        floatingActionButton = {
            if (!showCropUi) {
                ExtendedFloatingActionButton(
                    text = { Text(if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) "Next" else "Create") },
                    icon = { Icon(if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.Check, null) },
                    onClick = {
                        if (currentStep == 0 && creationMode == PlaylistCreationMode.MANUAL) {
                            if (playlistName.isNotBlank()) currentStep = 1
                        } else {
                            val shapeTypeForSave = if (selectedTab == 2) selectedShapeType.name else null
                            val (d1, d2, d3, d4) = if (selectedTab == 2) {
                                when (selectedShapeType) {
                                    PlaylistShapeType.SmoothRect -> Quadruple(smoothRectCornerRadius, smoothRectSmoothness, 0f, 0f)
                                    PlaylistShapeType.Star -> Quadruple(starCurve.toFloat(), starRotation, starScale, starSides.toFloat())
                                    else -> Quadruple(0f, 0f, 0f, 0f)
                                }
                            } else Quadruple(null, null, null, null)

                            onCreate(playlistName, selectedImageUri?.toString(), if(selectedTab == 2) selectedColor else null, if(selectedTab == 2) selectedIconName else null, selectedSongIds.filterValues { it }.keys.toList(), cropScale, cropOffset.x, cropOffset.y, shapeTypeForSave, d1, d2, d3, d4, if(creationMode == PlaylistCreationMode.SMART) selectedSmartRule.storageKey else null)
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentStep == 1) {
                val sourceScope by playerViewModel.playlistPickerSourceScope.collectAsStateWithLifecycle()
                PrimaryTabRow(selectedTabIndex = if(sourceScope == SourceScope.Local) 0 else 1) {
                    Tab(selected = sourceScope == SourceScope.Local, onClick = { playerViewModel.setPlaylistPickerSourceScope(SourceScope.Local) }) { Text("Local") }
                    Tab(selected = sourceScope == SourceScope.All, onClick = { playerViewModel.setPlaylistPickerSourceScope(SourceScope.All) }) { Text("All") }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (currentStep == 0) {
                PlaylistFormContent(
                    playlistName = playlistName,
                    onNameChange = { playlistName = it },
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    selectedImageUri = selectedImageUri,
                    showCropUi = showCropUi,
                    onShowCropUiChange = { showCropUi = it },
                    cropScale = cropScale,
                    onCropScaleChange = { cropScale = it },
                    cropOffset = cropOffset,
                    onCropOffsetChange = { cropOffset = it },
                    imageBitmap = imageBitmap,
                    imagePickerLauncher = imagePickerLauncher,
                    selectedColor = selectedColor,
                    onColorChange = { selectedColor = it },
                    selectedIconName = selectedIconName,
                    onIconChange = { selectedIconName = it },
                    selectedShapeType = selectedShapeType,
                    onShapeTypeChange = { selectedShapeType = it },
                    smoothRectCornerRadius = smoothRectCornerRadius,
                    onSmoothRectCornerRadiusChange = { smoothRectCornerRadius = it },
                    smoothRectSmoothness = smoothRectSmoothness,
                    onSmoothRectSmoothnessChange = { smoothRectSmoothness = it },
                    starSides = starSides,
                    onStarSidesChange = { starSides = it },
                    starCurve = starCurve,
                    onStarCurveChange = { starCurve = it },
                    starRotation = starRotation,
                    onStarRotationChange = { starRotation = it },
                    starScale = starScale,
                    onStarScaleChange = { starScale = it },
                    creationMode = creationMode,
                    onCreationModeChange = { creationMode = it },
                    selectedSmartRule = selectedSmartRule,
                    onSmartRuleChange = { selectedSmartRule = it },
                    onImageUriChange = { selectedImageUri = it }
                )
            } else {
                SongPickerSelectionPane(selectedSongIds = selectedSongIds, playerViewModel = playerViewModel)
            }
        }
    }
}

@Composable
fun EditPlaylistContent(
    initialName: String,
    initialImageUri: String?,
    initialColor: Int?,
    initialIconName: String?,
    initialShapeType: PlaylistShapeType?,
    initialShapeDetail1: Float?,
    initialShapeDetail2: Float?,
    initialShapeDetail3: Float?,
    initialShapeDetail4: Float?,
    onDismiss: () -> Unit,
    onSave: (String, String?, Int?, String?, Float, Float, Float, String?, Float?, Float?, Float?, Float?) -> Unit
) {
    // Similar to CreatePlaylistContent but for editing
    Text("Edit Content Placeholder")
}

@Composable
private fun PlaylistFormContent(
    modifier: Modifier = Modifier,
    playlistName: String,
    onNameChange: (String) -> Unit,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    selectedImageUri: Uri?,
    showCropUi: Boolean,
    onShowCropUiChange: (Boolean) -> Unit,
    cropScale: Float,
    onCropScaleChange: (Float) -> Unit,
    cropOffset: androidx.compose.ui.geometry.Offset,
    onCropOffsetChange: (androidx.compose.ui.geometry.Offset) -> Unit,
    imageBitmap: ImageBitmap?,
    imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    selectedColor: Int?,
    onColorChange: (Int) -> Unit,
    selectedIconName: String?,
    onIconChange: (String) -> Unit,
    selectedShapeType: PlaylistShapeType,
    onShapeTypeChange: (PlaylistShapeType) -> Unit,
    smoothRectCornerRadius: Float,
    onSmoothRectCornerRadiusChange: (Float) -> Unit,
    smoothRectSmoothness: Float,
    onSmoothRectSmoothnessChange: (Float) -> Unit,
    starSides: Int,
    onStarSidesChange: (Int) -> Unit,
    starCurve: Double,
    onStarCurveChange: (Double) -> Unit,
    starRotation: Float,
    onStarRotationChange: (Float) -> Unit,
    starScale: Float,
    onStarScaleChange: (Float) -> Unit,
    showCreationModeSelector: Boolean = true,
    creationMode: PlaylistCreationMode,
    onCreationModeChange: (PlaylistCreationMode) -> Unit,
    selectedSmartRule: SmartPlaylistRule,
    onSmartRuleChange: (SmartPlaylistRule) -> Unit,
    onGenerateClick: (() -> Unit)? = null,
    onImageUriChange: (Uri?) -> Unit
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        OutlinedTextField(value = playlistName, onValueChange = onNameChange, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        // Rest of the form
    }
}

fun getIconByName(name: String?): ImageVector? = when (name) {
    "MusicNote" -> Icons.Rounded.MusicNote
    "Headphones" -> Icons.Rounded.Headphones
    else -> Icons.Rounded.MusicNote
}

fun getThemeContentColor(colorArgb: Int, scheme: ColorScheme): Color = resolvePlaylistCoverContentColor(colorArgb, scheme)
