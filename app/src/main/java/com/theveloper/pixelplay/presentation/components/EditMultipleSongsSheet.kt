package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.theveloper.pixelplay.presentation.components.CoverArtCropperDialog
import com.theveloper.pixelplay.presentation.components.CoverArtCropResult
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.graphics.ImageBitmap
import android.net.Uri
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign

/**
 * Data class representing a field that can have mixed values across multiple songs
 */
private data class MixedValueField<T>(
    val value: T?,
    val isMixed: Boolean,
    val isModified: Boolean = false
)

private fun <T> List<T?>.toMixedValueField(): MixedValueField<T> {
    val distinct = this.distinct()
    return when {
        distinct.isEmpty() -> MixedValueField(null, false)
        distinct.size == 1 -> MixedValueField(distinct.first(), false)
        else -> MixedValueField(null, true)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditMultipleSongsSheet(
    visible: Boolean,
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (
        selectedSongs: List<Song>,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        composer: String?,
        genre: String?,
        lyrics: String?,
        trackNumber: Int?,
        discNumber: Int?,
        replayGainTrackGainDb: String?,
        replayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible

    if (transitionState.currentState || transitionState.targetState) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(animationSpec = tween(220)),
                exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(animationSpec = tween(200))
            ) {
                EditMultipleSongsContent(
                    songs = songs,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditMultipleSongsContent(
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSave: (
        selectedSongs: List<Song>,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        composer: String?,
        genre: String?,
        lyrics: String?,
        trackNumber: Int?,
        discNumber: Int?,
        replayGainTrackGainDb: String?,
        replayGainAlbumGainDb: String?,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit,
) {
    // Initialize mixed value fields
    val titleField = remember(songs) { songs.map { it.title }.toMixedValueField() }
    val artistField = remember(songs) { songs.map { it.displayArtist }.toMixedValueField() }
    val albumField = remember(songs) { songs.map { it.album }.toMixedValueField() }
    val albumArtistField = remember(songs) { songs.map { it.albumArtist }.toMixedValueField() }
    val genreField = remember(songs) { songs.map { it.genre }.toMixedValueField() }
    val lyricsField = remember(songs) { songs.map { it.lyrics }.toMixedValueField() }
    val trackNumberField = remember(songs) { songs.map { it.trackNumber }.toMixedValueField() }
    val discNumberField = remember(songs) { songs.map { it.discNumber }.toMixedValueField() }

    // Editable state
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    var album by remember { mutableStateOf<String?>(null) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var composer by remember { mutableStateOf<String?>(null) }
    var genre by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<String?>(null) }
    var trackNumber by remember { mutableStateOf<String?>(null) }
    var discNumber by remember { mutableStateOf<String?>(null) }
    var replayGainTrackGainDb by remember { mutableStateOf<String?>(null) }
    var replayGainAlbumGainDb by remember { mutableStateOf<String?>(null) }
    var coverArtUpdate by remember { mutableStateOf<CoverArtUpdate?>(null) }

    var coverArtPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var isCoverArtDeleted by remember { mutableStateOf(false) }
    var showCoverArtCropper by remember { mutableStateOf(false) }
    var pendingCoverArtUri by remember { mutableStateOf<Uri?>(null) }

    val pickCoverArtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            pendingCoverArtUri = uri
            showCoverArtCropper = true
        }
    }

    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    val textFieldShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 10.dp, smoothnessAsPercentBL = 60,
        cornerRadiusTR = 10.dp, smoothnessAsPercentBR = 60,
        cornerRadiusBL = 10.dp, smoothnessAsPercentTL = 60,
        cornerRadiusBR = 10.dp, smoothnessAsPercentTR = 60
    )

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }

    if (showCoverArtCropper && pendingCoverArtUri != null) {
        CoverArtCropperDialog(
            sourceUri = pendingCoverArtUri!!,
            onDismiss = {
                showCoverArtCropper = false
                pendingCoverArtUri = null
            },
            onConfirm = { result ->
                coverArtPreview = result.preview
                coverArtUpdate = result.update
                isCoverArtDeleted = false
                showCoverArtCropper = false
                pendingCoverArtUri = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = stringResource(R.string.batch_edit_toolbar_title, songs.size),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.displaySmall
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars
    ) { innerPadding ->
        val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = if (isKeyboardVisible) 8.dp else (navBarBottom + 100.dp),
                    start = 16.dp,
                    end = 16.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Info card
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.batch_edit_info_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Cover Art Editor Card
                item {
                    BatchCoverArtEditorCard(
                        modifier = Modifier.fillMaxWidth(),
                        songsCount = songs.size,
                        preview = coverArtPreview,
                        isDeleted = isCoverArtDeleted,
                        onPickNewArt = {
                            pickCoverArtLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onDelete = {
                            coverArtPreview = null
                            isCoverArtDeleted = true
                            coverArtUpdate = CoverArtUpdate(isDeletion = true)
                        },
                        onReset = {
                            coverArtPreview = null
                            coverArtUpdate = null
                            isCoverArtDeleted = false
                        }
                    )
                }

                // Artist field
                item {
                    BatchEditField(
                        value = artist ?: "",
                        onValueChange = { artist = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_artist),
                        placeholder = if (artistField.isMixed)
                            stringResource(R.string.batch_edit_mixed_values)
                        else
                            artistField.value ?: "",
                        icon = Icons.Rounded.Person,
                        tint = MaterialTheme.colorScheme.primary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape
                    )
                }

                // Album field
                item {
                    BatchEditField(
                        value = album ?: "",
                        onValueChange = { album = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_album),
                        placeholder = if (albumField.isMixed)
                            stringResource(R.string.batch_edit_mixed_values)
                        else
                            albumField.value ?: "",
                        icon = Icons.Rounded.Album,
                        tint = MaterialTheme.colorScheme.tertiary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape
                    )
                }

                // Album Artist field
                item {
                    BatchEditField(
                        value = albumArtist ?: "",
                        onValueChange = { albumArtist = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_album_artist),
                        placeholder = if (albumArtistField.isMixed)
                            stringResource(R.string.batch_edit_mixed_values)
                        else
                            albumArtistField.value ?: "",
                        icon = Icons.Rounded.Person,
                        tint = MaterialTheme.colorScheme.secondary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape
                    )
                }

                // Genre field
                item {
                    BatchEditField(
                        value = genre ?: "",
                        onValueChange = { genre = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_genre),
                        placeholder = if (genreField.isMixed)
                            stringResource(R.string.batch_edit_mixed_values)
                        else
                            genreField.value ?: "",
                        icon = Icons.Rounded.Category,
                        tint = MaterialTheme.colorScheme.secondary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape
                    )
                }

                // Composer field
                item {
                    BatchEditField(
                        value = composer ?: "",
                        onValueChange = { composer = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_composer),
                        placeholder = stringResource(R.string.batch_edit_optional),
                        icon = Icons.Rounded.MusicNote,
                        tint = MaterialTheme.colorScheme.tertiary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape
                    )
                }

                // ReplayGain Track
                item {
                    BatchEditField(
                        value = replayGainTrackGainDb ?: "",
                        onValueChange = { replayGainTrackGainDb = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_replaygain_track_db),
                        placeholder = stringResource(R.string.placeholder_replaygain_track_example),
                        icon = Icons.Rounded.RepeatOne,
                        tint = MaterialTheme.colorScheme.primary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape,
                        keyboardType = KeyboardType.Decimal
                    )
                }

                // ReplayGain Album
                item {
                    BatchEditField(
                        value = replayGainAlbumGainDb ?: "",
                        onValueChange = { replayGainAlbumGainDb = it.ifBlank { null } },
                        label = stringResource(R.string.song_field_replaygain_album_db),
                        placeholder = stringResource(R.string.placeholder_replaygain_album_example),
                        icon = Icons.Rounded.Repeat,
                        tint = MaterialTheme.colorScheme.tertiary,
                        textFieldColors = textFieldColors,
                        textFieldShape = textFieldShape,
                        keyboardType = KeyboardType.Decimal
                    )
                }
            }

            AnimatedVisibility(
                visible = !isKeyboardVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding() + 24.dp)
            ) {
                HorizontalFloatingToolbar(
                    expandedShadowElevation = 0.dp,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    expanded = true,
                    scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
                        exitDirection = FloatingToolbarExitDirection.Bottom
                    ),
                    content = {
                        FilledTonalButton(
                            onClick = onDismiss,
                            modifier = Modifier.height(48.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSave(
                                    songs,
                                    title,
                                    artist,
                                    album,
                                    albumArtist,
                                    composer,
                                    genre,
                                    lyrics,
                                    trackNumber?.toIntOrNull(),
                                    discNumber?.toIntOrNull(),
                                    replayGainTrackGainDb,
                                    replayGainAlbumGainDb,
                                    coverArtUpdate
                                )
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BatchEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    textFieldColors: TextFieldColors,
    textFieldShape: androidx.compose.ui.graphics.Shape,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelLarge
        )
        OutlinedTextField(
            value = value,
            shape = textFieldShape,
            colors = textFieldColors,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            leadingIcon = { Icon(icon, tint = tint, contentDescription = label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@Composable
private fun BatchCoverArtEditorCard(
    modifier: Modifier = Modifier,
    songsCount: Int,
    preview: ImageBitmap?,
    isDeleted: Boolean,
    onPickNewArt: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 12.dp, smoothnessAsPercentBL = 60,
            cornerRadiusTR = 12.dp, smoothnessAsPercentBR = 60,
            cornerRadiusBL = 12.dp, smoothnessAsPercentTL = 60,
            cornerRadiusBR = 12.dp, smoothnessAsPercentTR = 60,
        ),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.batch_edit_cover_art_heading),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val cropSize = minOf(maxWidth, 220.dp)
                Box(
                    modifier = Modifier
                        .size(cropSize)
                        .clip(RoundedCornerShape(22.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isDeleted -> {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_music_note_24),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        preview != null -> {
                            Image(
                                bitmap = preview,
                                contentDescription = stringResource(R.string.cd_cover_art_preview),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PhotoLibrary,
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(R.string.batch_edit_multiple_covers),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                    )
                                )
                            )
                    )
                }
            }

            Text(
                text = stringResource(R.string.batch_edit_cover_art_hint, songsCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                FilledTonalButton(onClick = onPickNewArt) {
                    Icon(Icons.Rounded.Image, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.batch_edit_set_cover_art),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    if (preview != null || isDeleted) {
                        TextButton(onClick = onReset) {
                            Icon(Icons.Rounded.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.action_reset),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.batch_edit_remove_all_art),
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