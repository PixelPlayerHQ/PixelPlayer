package com.theveloper.pixelplay.presentation.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import java.net.URLEncoder
import timber.log.Timber
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import dev.shreyaspatil.capturable.capturable
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.focusModifier
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.theveloper.pixelplay.data.media.AudioMetadataReader
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import dev.shreyaspatil.capturable.controller.rememberCaptureController
import java.io.ByteArrayOutputStream
import java.util.Locale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

private fun formatReplayGainForInput(gainDb: Float?): String {
    return gainDb?.let { String.format(Locale.US, "%.2f", it) }.orEmpty()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditSongSheet(
    visible: Boolean,
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
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
                EditSongContent(
                    song = song,
                    onDismiss = onDismiss,
                    onSave = onSave
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditSongContent(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        artist: String,
        album: String,
        albumArtist: String,
        composer: String,
        genre: String,
        lyrics: String,
        trackNumber: Int,
        discNumber: Int?,
        replayGainTrackGainDb: String,
        replayGainAlbumGainDb: String,
        coverArtUpdate: CoverArtUpdate?
    ) -> Unit,
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.displayArtist) }
    var album by remember { mutableStateOf(song.album) }
    var albumArtist by remember { mutableStateOf(song.albumArtist ?: "") }
    var composer by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf(song.genre ?: "") }
    var lyrics by remember { mutableStateOf(song.lyrics ?: "") }
    var trackNumber by remember { mutableStateOf(song.trackNumber.toString()) }
    var discNumber by remember { mutableStateOf(song.discNumber?.toString() ?: "") }
    var replayGainTrackGainDb by remember { mutableStateOf("") }
    var replayGainAlbumGainDb by remember { mutableStateOf("") }
    var coverArtPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var editedCoverArt by remember { mutableStateOf<CoverArtUpdate?>(null) }
    var isCoverArtDeleted by remember { mutableStateOf(false) }
    var showCoverArtCropper by remember { mutableStateOf(false) }
    var pendingCoverArtUri by remember { mutableStateOf<Uri?>(null) }

    var showInfoDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val pickCoverArtLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            pendingCoverArtUri = uri
            showCoverArtCropper = true
        }
    }

    LaunchedEffect(song) {
        title = song.title
        artist = song.displayArtist
        album = song.album
        albumArtist = song.albumArtist ?: ""
        composer = ""
        genre = song.genre ?: ""
        lyrics = song.lyrics ?: ""
        trackNumber = song.trackNumber.toString()
        discNumber = song.discNumber?.toString() ?: ""
        replayGainTrackGainDb = ""
        replayGainAlbumGainDb = ""
        coverArtPreview = null
        editedCoverArt = null
        isCoverArtDeleted = false

        if (song.path.isNotBlank()) {
            val embeddedMetadata = withContext(Dispatchers.IO) {
                try {
                    val file = java.io.File(song.path)
                    if (file.exists()) {
                        AudioMetadataReader.read(file, readArtwork = false)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to read embedded metadata for EditSongSheet")
                    null
                }
            }

            if (lyrics.isBlank()) {
                embeddedMetadata?.lyrics?.takeIf { it.isNotBlank() }?.let { embeddedLyrics ->
                    lyrics = embeddedLyrics
                }
            }

            embeddedMetadata?.composer?.takeIf { it.isNotBlank() }?.let { composer = it }
            replayGainTrackGainDb = formatReplayGainForInput(embeddedMetadata?.replayGainTrackGainDb)
            replayGainAlbumGainDb = formatReplayGainForInput(embeddedMetadata?.replayGainAlbumGainDb)
        }
    }

    if (showCoverArtCropper && pendingCoverArtUri != null) {
        CoverArtCropperDialog(
            sourceUri = pendingCoverArtUri!!,
            onDismiss = {
                showCoverArtCropper = false
                pendingCoverArtUri = null
            },
            onConfirm = { result ->
                coverArtPreview = result.preview
                editedCoverArt = result.update
                isCoverArtDeleted = false
                showCoverArtCropper = false
                pendingCoverArtUri = null
            }
        )
    }

    // Definición de colores para los TextFields
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
    )

    // Definición de la forma para los TextFields
    val textFieldShape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 10.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusTR = 10.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusBL = 10.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBR = 10.dp,
        smoothnessAsPercentTR = 60
    )

    val scrollBehavior = FloatingToolbarDefaults.exitAlwaysScrollBehavior(
        exitDirection = FloatingToolbarExitDirection.Bottom
    )

    // --- Diálogo de Información ---
    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.cd_info_icon)) },
            title = { Text(stringResource(R.string.edit_song_info_dialog_title)) },
            text = { Text(stringResource(R.string.edit_song_info_dialog_body)) },
            confirmButton = {
                TextButton(onClick = { showInfoDialog = false }) {
                    Text(stringResource(R.string.edit_song_got_it), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val isKeyboardVisible by remember { derivedStateOf { imeInsets.getBottom(density) > 0 } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        modifier = Modifier.padding(start = 10.dp),
                        text = stringResource(R.string.edit_song_toolbar_title),
                        fontFamily = GoogleSansRounded,
                        style = MaterialTheme.typography.displaySmall
                    )
                },
                actions = {
                    FilledTonalIconButton(
                        modifier = Modifier.padding(end = 10.dp),
                        onClick = { showInfoDialog = true },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.cd_show_metadata_info))
                    }
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
            item {
                CoverArtEditorCard(
                    modifier = Modifier.fillMaxWidth(),
                    albumArtUri = song.albumArtUriString,
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
                        editedCoverArt = CoverArtUpdate(isDeletion = true)
                    },
                    onReset = {
                        coverArtPreview = null
                        editedCoverArt = null
                        isCoverArtDeleted = false
                    }
                )
            }

            // --- Campo de Título ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_title),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = title,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.song_field_title)) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, tint = MaterialTheme.colorScheme.tertiary, contentDescription = stringResource(R.string.cd_song_title_icon)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Artista ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_artist),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = artist,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { artist = it },
                        placeholder = { Text(stringResource(R.string.song_field_artist)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, tint = MaterialTheme.colorScheme.primary, contentDescription = stringResource(R.string.song_field_artist)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Álbum ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_album),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = album,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { album = it },
                        placeholder = { Text(stringResource(R.string.song_field_album)) },
                        leadingIcon = { Icon(Icons.Rounded.Album, tint = MaterialTheme.colorScheme.tertiary, contentDescription = stringResource(R.string.song_field_album)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Album Artist ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_album_artist),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = albumArtist,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { albumArtist = it },
                        placeholder = { Text(stringResource(R.string.song_field_album_artist)) },
                        leadingIcon = { Icon(Icons.Rounded.Person, tint = MaterialTheme.colorScheme.secondary, contentDescription = stringResource(R.string.song_field_album_artist)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Género ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_genre),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = genre,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { genre = it },
                        placeholder = { Text(stringResource(R.string.song_field_genre)) },
                        leadingIcon = { Icon(Icons.Rounded.Category, tint = MaterialTheme.colorScheme.secondary, contentDescription = stringResource(R.string.song_field_genre)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Compositor ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_composer),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = composer,
                        colors = textFieldColors,
                        shape = textFieldShape,
                        onValueChange = { composer = it },
                        placeholder = { Text(stringResource(R.string.song_field_composer)) },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, tint = MaterialTheme.colorScheme.tertiary, contentDescription = stringResource(R.string.song_field_composer)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // --- Campo de Track Number ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_track_number),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = trackNumber,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { trackNumber = it },
                        placeholder = { Text(stringResource(R.string.song_field_track_number)) },
                        leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, tint = MaterialTheme.colorScheme.secondary, contentDescription = stringResource(R.string.cd_track_number_icon)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // --- Campo de Disc Number ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_disc_number),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = discNumber,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { discNumber = it },
                        placeholder = { Text(stringResource(R.string.song_field_disc_number)) },
                        leadingIcon = { Icon(Icons.Rounded.FormatListNumbered, tint = MaterialTheme.colorScheme.secondary, contentDescription = stringResource(R.string.cd_disc_number_icon)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }

            // --- Campo de ReplayGain Track ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_replaygain_track_db),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = replayGainTrackGainDb,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { replayGainTrackGainDb = it },
                        placeholder = { Text(stringResource(R.string.placeholder_replaygain_track_example)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.RepeatOne,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = stringResource(R.string.cd_replaygain_track)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // --- Campo de ReplayGain Album ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.song_field_replaygain_album_db),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedTextField(
                        value = replayGainAlbumGainDb,
                        shape = textFieldShape,
                        colors = textFieldColors,
                        onValueChange = { replayGainAlbumGainDb = it },
                        placeholder = { Text(stringResource(R.string.placeholder_replaygain_album_example)) },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Repeat,
                                tint = MaterialTheme.colorScheme.tertiary,
                                contentDescription = stringResource(R.string.cd_replaygain_album)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // --- Campo de Letra ---
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = stringResource(R.string.lyrics),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lyrics,
                            colors = textFieldColors,
                            shape = textFieldShape,
                            onValueChange = { lyrics = it },
                            placeholder = { Text(stringResource(R.string.lyrics)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, tint = MaterialTheme.colorScheme.primary, contentDescription = stringResource(R.string.lyrics)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalIconButton(
                            onClick = {
                                val encodedTitle = URLEncoder.encode(title, "UTF-8")
                                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                                val url = "https://lrclib.net/search/$encodedTitle%20$encodedArtist"
                                val intent = Intent(Intent.ACTION_VIEW).setData(Uri.parse(url))
                                context.startActivity(intent)
                            },
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_search_24),
                                contentDescription = stringResource(R.string.cd_search_lyrics_lrclib)
                            )
                        }
                    }
                }
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
                    scrollBehavior = scrollBehavior,
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
                        Spacer(
                            modifier = Modifier.width(8.dp)
                        )
                        Button(
                            onClick = {
                                val resolvedTrackNumber = trackNumber.toIntOrNull() ?: song.trackNumber
                                val resolvedDiscNumber = discNumber.toIntOrNull()
                                onSave(
                                    title.trim(),
                                    artist.trim(),
                                    album.trim(),
                                    albumArtist.trim(),
                                    composer.trim(),
                                    genre.trim(),
                                    lyrics,
                                    resolvedTrackNumber,
                                    resolvedDiscNumber,
                                    replayGainTrackGainDb.trim(),
                                    replayGainAlbumGainDb.trim(),
                                    editedCoverArt
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
private fun CoverArtEditorCard(
    modifier: Modifier = Modifier,
    albumArtUri: String?,
    preview: ImageBitmap?,
    isDeleted: Boolean,
    onPickNewArt: () -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTL = 12.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusTR = 12.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = 12.dp,
            smoothnessAsPercentTL = 60,
            cornerRadiusBR = 12.dp,
            smoothnessAsPercentTR = 60,
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
                text = stringResource(R.string.edit_song_cover_art_heading),
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

                        albumArtUri != null -> {
                            SmartImage(
                                model = albumArtUri,
                                contentDescription = stringResource(R.string.cd_current_song_cover_art),
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                placeholderResId = R.drawable.rounded_music_note_24,
                                errorResId = R.drawable.rounded_broken_image_24
                            )
                        }

                        else -> {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_music_note_24),
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                text = stringResource(R.string.edit_song_cover_art_hint),
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
                    Text(stringResource(R.string.edit_song_change_cover_art), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                if (preview != null || isDeleted) {
                    TextButton(onClick = onReset) {
                        Icon(Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.action_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                } else if (albumArtUri != null) {
                    FilledTonalButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.edit_song_delete_cover_art), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

data class CoverArtCropResult(
    val preview: ImageBitmap,
    val update: CoverArtUpdate,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun CoverArtCropperDialog(
    sourceUri: Uri,
    onDismiss: () -> Unit,
    onConfirm: (CoverArtCropResult) -> Unit,
) {
    val context = LocalContext.current
    val dialogScope = rememberCoroutineScope()
    val captureController = rememberCaptureController()
    var loadedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(0f) }

    LaunchedEffect(sourceUri) {
        isLoading = true
        loadError = null
        val bitmap = withContext(Dispatchers.IO) { decodeBitmapFromUri(context, sourceUri) }
        if (bitmap != null) {
            loadedBitmap = bitmap.asImageBitmap()
        } else {
            loadError = context.getString(R.string.edit_song_unable_to_load_image)
        }
        isLoading = false
        scale = 1f
        offset = Offset.Zero
    }

    LaunchedEffect(containerSize, scale, loadedBitmap) {
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset, scale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = newScale
        loadedBitmap?.let { bitmap ->
            offset = clampOffset(offset + panChange, newScale, containerSize, bitmap.width, bitmap.height)
        }
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    BasicAlertDialog(
        onDismissRequest = {
            if (!isSaving) onDismiss()
        }
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    val cropSide = minOf(maxWidth, 320.dp)
                    val density = LocalDensity.current
                    val cropSidePx = remember(cropSide) { with(density) { cropSide.toPx() } }

                    LaunchedEffect(cropSidePx) {
                        containerSize = cropSidePx
                    }

                    Box(
                        modifier = Modifier
                            .size(cropSide)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceDim)
                            .clipToBounds()
                            .capturable(controller = captureController)
                            .transformable(transformableState)
                    ) {
                        when {
                            isLoading -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            loadError != null -> {
                                Text(
                                    text = loadError!!,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(16.dp)
                                )
                            }

                            loadedBitmap != null -> {
                                val bitmap = loadedBitmap!!
                                val baseScale = maxOf(containerSize / bitmap.width, containerSize / bitmap.height)
                                val displayWidth = with(LocalDensity.current) { (bitmap.width * baseScale).toDp() }
                                val displayHeight = with(LocalDensity.current) { (bitmap.height * baseScale).toDp() }
                                
                                Image(
                                    bitmap = loadedBitmap!!,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .requiredSize(displayWidth, displayHeight)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = offset.x
                                            translationY = offset.y
                                        }
                                )

                                Canvas(modifier = Modifier.matchParentSize()) {
                                    val step = size.width / 3f
                                    for (index in 1 until 3) {
                                        val lineOffset = step * index
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(lineOffset, 0f),
                                            end = Offset(lineOffset, size.height),
                                            strokeWidth = 2f
                                        )
                                        drawLine(
                                            color = gridColor,
                                            start = Offset(0f, lineOffset),
                                            end = Offset(size.width, lineOffset),
                                            strokeWidth = 2f
                                        )
                                    }
                                    drawRect(color = gridColor, style = Stroke(width = 3f))
                                }
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.edit_song_crop_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
                ) {
                    TextButton(
                        enabled = !isSaving,
                        onClick = onDismiss
                    ) {
                        Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    val canConfirm = !isLoading && loadError == null && loadedBitmap != null
                    Button(
                        enabled = canConfirm && !isSaving,
                        onClick = {
                            if (!canConfirm) return@Button
                            dialogScope.launch {
                                isSaving = true
                                val captured = captureController.captureAsync().await()
                                if (captured != null) {
                                    val bytes = withContext(Dispatchers.IO) {
                                        imageBitmapToJpeg(captured)
                                    }
                                    if (bytes != null) {
                                        onConfirm(
                                            CoverArtCropResult(
                                                preview = captured,
                                                update = CoverArtUpdate(bytes, COVER_ART_MIME_TYPE)
                                            )
                                        )
                                    } else {
                                        Timber.w("Failed to convert captured cover art to JPEG")
                                    }
                                } else {
                                    Timber.w("CaptureController returned null bitmap")
                                }
                                isSaving = false
                            }
                        }
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.edit_song_apply_cover_art))
                    }
                }
            }
        }
    }
}

private const val COVER_ART_MIME_TYPE = "image/jpeg"

private fun clampOffset(
    offset: Offset,
    scale: Float,
    containerSize: Float,
    bitmapWidth: Int,
    bitmapHeight: Int
): Offset {
    if (containerSize <= 0f || bitmapWidth <= 0 || bitmapHeight <= 0) return Offset.Zero

    // When using ContentScale.Crop, the image is scaled to fill the container size.
    // The base scale factor is the maximum of the width and height ratios.
    val baseScale = maxOf(containerSize / bitmapWidth, containerSize / bitmapHeight)
    val totalScale = baseScale * scale

    val scaledWidth = bitmapWidth * totalScale
    val scaledHeight = bitmapHeight * totalScale

    // Calculate maximum translation bounds (half of the overflow).
    val maxX = maxOf(0f, (scaledWidth - containerSize) / 2f)
    val maxY = maxOf(0f, (scaledHeight - containerSize) / 2f)

    return Offset(
        x = offset.x.coerceIn(-maxX, maxX),
        y = offset.y.coerceIn(-maxY, maxY)
    )
}

private fun imageBitmapToJpeg(imageBitmap: ImageBitmap): ByteArray? {
    return try {
        val bitmap = imageBitmap.asAndroidBitmap()
        ByteArrayOutputStream().use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.toByteArray()
        }
    } catch (error: Exception) {
        Timber.e(error, "Unable to compress image bitmap to JPEG")
        null
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val maxDimension = 2048
            val width = info.size.width
            val height = info.size.height
            val largerSide = maxOf(width, height)
            if (largerSide > maxDimension) {
                val scale = largerSide.toFloat() / maxDimension
                decoder.setTargetSize(
                    (width / scale).roundToInt(),
                    (height / scale).roundToInt()
                )
            }
            decoder.isMutableRequired = false
        }
    } catch (error: Exception) {
        Timber.e(error, "Failed to decode bitmap from $uri")
        null
    }
}
