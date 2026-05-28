package com.theveloper.pixelplay.presentation.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.AutoSizingTextToFill
import com.theveloper.pixelplay.utils.formatDuration
import com.theveloper.pixelplay.utils.shapes.RoundedStarShape
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.ai.SongMetadata
import com.theveloper.pixelplay.data.media.CoverArtUpdate
import com.theveloper.pixelplay.ui.theme.MontserratFamily
import com.theveloper.pixelplay.presentation.viewmodel.SongInfoBottomSheetViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SongInfoBottomSheetViewModel.ToneTarget
import kotlinx.coroutines.launch

import androidx.compose.ui.graphics.TransformOrigin
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.AudioMetaUtils
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SongInfoBottomSheet(
    song: Song,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDismiss: () -> Unit,
    onPlaySong: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddNextToQueue: () -> Unit,
    onAddToPlayList: () -> Unit,
    onDeleteFromDevice: (activity: Activity, song: Song, onResult: (Boolean) -> Unit) -> Unit,
    onNavigateToAlbum: () -> Unit,
    onNavigateToArtist: () -> Unit,
    onNavigateToArtistById: (Long) -> Unit = { onNavigateToArtist() },
    onNavigateToGenre: () -> Unit,
    onEditSong: (
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
    generateAiMetadata: suspend (List<String>) -> Result<SongMetadata>,
    removeFromListTrigger: () -> Unit,
    isGeneratingMetadata: Boolean = false,
    aiMetadataSuccess: Boolean = false,
    aiError: String? = null,
    onRetryMetadata: () -> Unit = {},
    songInfoViewModel: SongInfoBottomSheetViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    var showEditSheet by remember { mutableStateOf(false) }
    var showArtistPicker by remember { mutableStateOf(false) }
    var showTonePickerDialog by remember { mutableStateOf(false) }
    var toneConfirmationTarget by remember { mutableStateOf<ToneTarget?>(null) }
    var pendingTonePermissionSong by remember { mutableStateOf<Song?>(null) }
    var pendingTonePermissionTarget by remember { mutableStateOf<ToneTarget?>(null) }
    val audioMeta by songInfoViewModel.audioMeta.collectAsStateWithLifecycle()
    val resolvedArtists by songInfoViewModel.resolvedArtists.collectAsStateWithLifecycle()
    val isPixelPlayWatchAvailable by songInfoViewModel.isPixelPlayWatchAvailable.collectAsStateWithLifecycle()
    val isWatchAvailabilityResolved by songInfoViewModel.isWatchAvailabilityResolved.collectAsStateWithLifecycle()
    val isSendingToWatch by songInfoViewModel.isSendingToWatch.collectAsStateWithLifecycle()
    val watchTransfers by songInfoViewModel.watchTransfers.collectAsStateWithLifecycle()
    val watchSongIds by songInfoViewModel.watchSongIds.collectAsStateWithLifecycle()
    val reachableWatchNodeIds by songInfoViewModel.reachableWatchNodeIds.collectAsStateWithLifecycle()
    val latestSongWatchTransfer = remember(song.id, watchTransfers) {
        watchTransfers.values
            .asSequence()
            .filter { it.songId == song.id }
            .maxByOrNull { it.updatedAtMillis }
    }
    val currentSongTransfer = latestSongWatchTransfer?.takeIf {
        it.status == com.theveloper.pixelplay.shared.WearTransferProgress.STATUS_TRANSFERRING
    }
    val currentSongTransferPercent = ((currentSongTransfer?.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)
    val isSongSavedOnWatch = remember(
        song.id,
        watchSongIds,
        reachableWatchNodeIds,
        currentSongTransfer,
    ) {
        currentSongTransfer == null && songInfoViewModel.isSongSavedOnAllReachableWatches(song.id)
    }
    val canSendToWatch = remember(song.path, song.contentUriString) {
        songInfoViewModel.isLocalSongForWatchTransfer(song)
    }

    LaunchedEffect(songInfoViewModel) {
        songInfoViewModel.refreshWatchAvailability()
    }

    val ringtonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val pendingSong = pendingTonePermissionSong
        val pendingTarget = pendingTonePermissionTarget
        pendingTonePermissionSong = null
        pendingTonePermissionTarget = null
        if (pendingSong == null || pendingTarget == null) {
            return@rememberLauncherForActivityResult
        }
        if (songInfoViewModel.hasSystemWritePermission()) {
            songInfoViewModel.setSongAsTone(pendingSong, pendingTarget) { result ->
                val message = when (result) {
                    is SongInfoBottomSheetViewModel.ToneActionResult.Success -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.Error -> result.message
                    is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> result.message
                }
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.song_info_ringtone_permission_missing),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestToneSystemWritePermission(songToSet: Song, target: ToneTarget, message: String) {
        pendingTonePermissionSong = songToSet
        pendingTonePermissionTarget = target
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        try {
            ringtonePermissionLauncher.launch(songInfoViewModel.createSystemWriteSettingsIntent())
        } catch (_: ActivityNotFoundException) {
            try {
                ringtonePermissionLauncher.launch(Intent(Settings.ACTION_SETTINGS))
            } catch (e: Exception) {
                pendingTonePermissionSong = null
                pendingTonePermissionTarget = null
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.song_info_ringtone_failed,
                        e.localizedMessage ?: ""
                    ),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun handleToneResult(
        songToSet: Song,
        target: ToneTarget,
        result: SongInfoBottomSheetViewModel.ToneActionResult
    ) {
        when (result) {
            is SongInfoBottomSheetViewModel.ToneActionResult.Success -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.Error -> {
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
            }
            is SongInfoBottomSheetViewModel.ToneActionResult.NeedsSystemWritePermission -> {
                requestToneSystemWritePermission(songToSet, target, result.message)
            }
        }
    }

    fun setCurrentSongAsTone(target: ToneTarget) {
        songInfoViewModel.setSongAsTone(song, target) { result ->
            handleToneResult(song, target, result)
        }
    }

    var lastShownWatchTransferError by remember(song.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(
        latestSongWatchTransfer?.requestId,
        latestSongWatchTransfer?.status,
        latestSongWatchTransfer?.error,
    ) {
        val failedTransfer = latestSongWatchTransfer?.takeIf {
            it.status == com.theveloper.pixelplay.shared.WearTransferProgress.STATUS_FAILED &&
                    !it.error.isNullOrBlank()
        } ?: return@LaunchedEffect
        val errorKey = "${failedTransfer.requestId}:${failedTransfer.error}"
        if (lastShownWatchTransferError == errorKey) return@LaunchedEffect
        lastShownWatchTransferError = errorKey
        Toast.makeText(context, failedTransfer.error, Toast.LENGTH_SHORT).show()
    }

    val shouldOfferWatchTransfer = remember(
        canSendToWatch,
        currentSongTransfer,
        isPixelPlayWatchAvailable,
        isSongSavedOnWatch,
        isWatchAvailabilityResolved,
    ) {
        currentSongTransfer == null &&
                canSendToWatch &&
                isWatchAvailabilityResolved &&
                isPixelPlayWatchAvailable &&
                !isSongSavedOnWatch
    }
    val shouldShowWatchTransferLoading = remember(
        canSendToWatch,
        isWatchAvailabilityResolved,
    ) {
        canSendToWatch &&
                !isWatchAvailabilityResolved
    }

    val evenCornerRadiusElems = 26.dp

    val listItemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = 20.dp, smoothnessAsPercentBR = 60, cornerRadiusBR = 20.dp,
            smoothnessAsPercentTL = 60, cornerRadiusTL = 20.dp, smoothnessAsPercentBL = 60,
            cornerRadiusBL = 20.dp, smoothnessAsPercentTR = 60
        )
    }
    val albumArtShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }
    val playButtonShape = remember(evenCornerRadiusElems) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = evenCornerRadiusElems, smoothnessAsPercentBR = 60, cornerRadiusBR = evenCornerRadiusElems,
            smoothnessAsPercentTL = 60, cornerRadiusTL = evenCornerRadiusElems, smoothnessAsPercentBL = 60,
            cornerRadiusBL = evenCornerRadiusElems, smoothnessAsPercentTR = 60
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { true }
    )

    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (isFavorite) evenCornerRadiusElems else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (isFavorite) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )
    val sendToWatchContainerColor by animateColorAsState(
        targetValue = if (isSendingToWatch) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        animationSpec = tween(durationMillis = 250),
        label = "SendToWatchContainerColorAnimation"
    )
    val sendToWatchContentColor by animateColorAsState(
        targetValue = if (isSendingToWatch) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        animationSpec = tween(durationMillis = 250),
        label = "SendToWatchContentColorAnimation"
    )

    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }
    val infoSegmentContainerShape = remember {
        RoundedCornerShape(20.dp)
    }
    val infoSegmentItemShape = remember {
        RoundedCornerShape(8.dp)
    }

    val audioMetaLabel = remember(audioMeta) {
        val meta = audioMeta ?: return@remember null
        val formatLabel = AudioMetaUtils.mimeTypeToFormat(meta.mimeType)
            .takeIf { it != "-" }
            ?.uppercase(java.util.Locale.getDefault())
        val parts = buildList {
            meta.sampleRate?.takeIf { it > 0 }
                ?.let { add(String.format(java.util.Locale.US, "%.1f kHz", it / 1000.0)) }
            meta.bitrate?.takeIf { it > 0 }
                ?.let { add("${it / 1000} kbps") }
            formatLabel?.let { add(it) }
        }
        parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
    val songLocationInfo = remember(song.path, song.contentUriString) {
        songInfoViewModel.getSongLocationInfo(song)
    }

    LaunchedEffect(song.id) {
        songInfoViewModel.loadAudioMeta(song)
        songInfoViewModel.loadArtistsForSong(song)
    }

    val pagerState = androidx.compose.foundation.pager.rememberPagerState(pageCount = { 2 })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = {
            if (!showEditSheet) {
                onDismiss()
            }
        },
        sheetState = sheetState,
    ) {
        // AQUÍ APLICAMOS EL FIX: Anulamos la fábrica de overscroll para todo lo que esté aquí adentro
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                    ) {
                        // Fila para la carátula del álbum y el título (Always visible)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmartImage(
                                model = song.albumArtUriString,
                                contentDescription = stringResource(R.string.widget_album_art),
                                shape = albumArtShape,
                                modifier = Modifier.size(80.dp),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                AutoSizingTextToFill(
                                    modifier = Modifier.padding(end = 4.dp),
                                    fontWeight = FontWeight.Light,
                                    text = song.title
                                )
                            }
                            FilledTonalIconButton(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(vertical = 6.dp),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceBright,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                onClick = { showEditSheet = true },
                            ) {
                                Icon(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_song_metadata)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Swipeable Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(
                                animationSpec = tween(durationMillis = 280),
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
                                0 -> { // Options / Actions
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                MediumExtendedFloatingActionButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .fillMaxHeight(),
                                                    onClick = onPlaySong,
                                                    elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                                    shape = playButtonShape,
                                                    icon = {
                                                        Icon(Icons.Rounded.PlayArrow, contentDescription = stringResource(R.string.cd_play_song_action))
                                                    },
                                                    text = {
                                                        Text(
                                                            modifier = Modifier.padding(end = 10.dp),
                                                            text = stringResource(R.string.play_playback)
                                                        )
                                                    }
                                                )

                                                FilledIconButton(
                                                    modifier = Modifier
                                                        .weight(0.25f)
                                                        .fillMaxHeight(),
                                                    onClick = onToggleFavorite,
                                                    shape = favoriteButtonShape,
                                                    colors = IconButtonDefaults.filledIconButtonColors(
                                                        containerColor = favoriteButtonContainerColor,
                                                        contentColor = favoriteButtonContentColor
                                                    )
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                                        imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = stringResource(
                                                            if (isFavorite) R.string.cd_remove_from_favorites else R.string.cd_add_to_favorites
                                                        )
                                                    )
                                                }

                                                FilledTonalIconButton(
                                                    modifier = Modifier
                                                        .weight(0.25f)
                                                        .fillMaxHeight(),
                                                    onClick = {
                                                        try {
                                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                                type = "audio/*"
                                                                putExtra(Intent.EXTRA_STREAM, song.contentUriString.toUri())
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                            }
                                                            context.startActivity(
                                                                Intent.createChooser(
                                                                    shareIntent,
                                                                    context.getString(R.string.song_info_share_chooser_title)
                                                                )
                                                            )
                                                        } catch (e: Exception) {
                                                            Toast.makeText(
                                                            context,
                                                            context.getString(R.string.error_share_song_format, e.localizedMessage ?: ""),
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        }
                                                    },
                                                    shape = CircleShape
                                                ) {
                                                    Icon(
                                                        modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                                        imageVector = Icons.Rounded.Share,
                                                        contentDescription = stringResource(R.string.cd_share_song_file)
                                                    )
                                                }
                                            }
                                        }
                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.6f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                                    shape = CircleShape,
                                                    onClick = onAddToQueue
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.QueueMusic,
                                                        contentDescription = stringResource(R.string.cd_add_to_queue)
                                                    )
                                                    Spacer(Modifier.width(14.dp))
                                                    Text(stringResource(R.string.action_add_to_queue))
                                                }
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.4f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiary,
                                                        contentColor = MaterialTheme.colorScheme.onTertiary
                                                    ),
                                                    contentPadding = PaddingValues(horizontal = 0.dp),
                                                    shape = CircleShape,
                                                    onClick = onAddNextToQueue
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.QueueMusic,
                                                        contentDescription = stringResource(R.string.cd_play_next_in_queue)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.action_queue_next))
                                                }
                                            }
                                        }

                                        item {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(IntrinsicSize.Min),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                                    ),
                                                    shape = CircleShape,
                                                    onClick = onAddToPlayList
                                                ) {
                                                    Icon(
                                                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                                                        contentDescription = stringResource(R.string.cd_add_to_playlist)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.shortcut_playlist_short))
                                                }

                                                FilledTonalButton(
                                                    modifier = Modifier
                                                        .weight(0.5f)
                                                        .heightIn(min = 66.dp),
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    ),
                                                    shape = CircleShape,
                                                    onClick = {
                                                        (context as? Activity)?.let { activity ->
                                                            onDeleteFromDevice(activity, song) { result ->
                                                                if (result) {
                                                                    removeFromListTrigger()
                                                                    onDismiss()
                                                                }
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    Icon(
                                                        Icons.Default.DeleteForever,
                                                        contentDescription = stringResource(R.string.delete_action)
                                                    )
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.delete_action))
                                                }
                                            }
                                        }

                                        val shouldRenderWatchTransferRow =
                                            currentSongTransfer != null ||
                                                    shouldOfferWatchTransfer ||
                                                    shouldShowWatchTransferLoading
                                        item {
                                            if (shouldRenderWatchTransferRow) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(IntrinsicSize.Min),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    RingtoneActionButton(
                                                        modifier = Modifier
                                                            .weight(0.38f)
                                                            .fillMaxHeight(),
                                                        showText = true,
                                                        compactText = true,
                                                        onClick = { showTonePickerDialog = true },
                                                    )

                                                    FilledTonalButton(
                                                        modifier = Modifier
                                                            .weight(0.62f)
                                                            .fillMaxHeight(),
                                                        colors = ButtonDefaults.filledTonalButtonColors(
                                                            containerColor = if (isPixelPlayWatchAvailable) {
                                                                sendToWatchContainerColor
                                                            } else {
                                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                                            },
                                                            contentColor = if (isPixelPlayWatchAvailable) {
                                                                sendToWatchContentColor
                                                            } else {
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            },
                                                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                        ),
                                                        shape = CircleShape,
                                                        enabled = shouldOfferWatchTransfer && !isSendingToWatch,
                                                        onClick = {
                                                            songInfoViewModel.sendSongToWatch(song) { message ->
                                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    ) {
                                                        if (shouldShowWatchTransferLoading) {
                                                            LoadingIndicator(modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(stringResource(R.string.song_info_checking_watch))
                                                        } else if (isSendingToWatch) {
                                                            LoadingIndicator(modifier = Modifier.size(18.dp))
                                                            Spacer(Modifier.width(10.dp))
                                                            Text(
                                                                when {
                                                                    currentSongTransfer != null && currentSongTransfer.totalBytes > 0L ->
                                                                        stringResource(
                                                                            R.string.song_info_transferring_percent,
                                                                            currentSongTransferPercent
                                                                        )
                                                                    currentSongTransfer != null ->
                                                                        stringResource(R.string.song_info_transferring_to_watch)
                                                                    else ->
                                                                        stringResource(R.string.song_info_transfer_in_progress)
                                                                }
                                                            )
                                                        } else {
                                                            Icon(
                                                                painter = painterResource(R.drawable.rounded_watch_arrow_down_24),
                                                                contentDescription = stringResource(
                                                                    if (isPixelPlayWatchAvailable) {
                                                                        R.string.cd_send_song_to_watch
                                                                    } else {
                                                                        R.string.cd_watch_unavailable
                                                                    }
                                                                )
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                stringResource(
                                                                    if (isPixelPlayWatchAvailable) {
                                                                        R.string.song_info_send_to_watch
                                                                    } else {
                                                                        R.string.song_info_watch_unavailable
                                                                    }
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                            } else {
                                                RingtoneActionButton(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .heightIn(min = 66.dp),
                                                    showText = true,
                                                    onClick = { showTonePickerDialog = true },
                                                )
                                            }
                                        }

                                        item {
                                            Spacer(Modifier.height(80.dp))
                                        }
                                    }
                                }
                                1 -> { // Details / Info
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(infoSegmentContainerShape),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_info_label_duration),
                                                    supporting = formatDuration(song.duration),
                                                    icon = Icons.Rounded.Schedule,
                                                    iconDescription = stringResource(R.string.cd_duration_icon),
                                                    shape = infoSegmentItemShape,
                                                )

                                                if (!song.genre.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_field_genre),
                                                        supporting = song.genre,
                                                        icon = Icons.Rounded.MusicNote,
                                                        iconDescription = stringResource(R.string.cd_genre_icon),
                                                        shape = infoSegmentItemShape,
                                                        onClick = onNavigateToGenre,
                                                    )
                                                }

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_album),
                                                    supporting = song.album,
                                                    icon = Icons.Rounded.Album,
                                                    iconDescription = stringResource(R.string.cd_album_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = onNavigateToAlbum,
                                                )

                                                SongInfoSegmentedListItem(
                                                    headline = stringResource(R.string.song_field_artist),
                                                    supporting = song.displayArtist,
                                                    icon = Icons.Rounded.Person,
                                                    iconDescription = stringResource(R.string.cd_artist_icon),
                                                    shape = infoSegmentItemShape,
                                                    onClick = {
                                                        if (song.artists.size > 1) {
                                                            showArtistPicker = true
                                                        } else {
                                                            onNavigateToArtist()
                                                        }
                                                    },
                                                )

                                                if (!audioMetaLabel.isNullOrEmpty()) {
                                                    SongInfoSegmentedListItem(
                                                        headline = stringResource(R.string.song_info_label_song_metadata),
                                                        supporting = audioMetaLabel,
                                                        icon = Icons.Rounded.Info,
                                                        iconDescription = stringResource(R.string.cd_audio_format_icon),
                                                        shape = infoSegmentItemShape,
                                                    )
                                                }

                                                SongInfoSegmentedListItem(
                                                    headline = songLocationInfo.label,
                                                    supporting = songLocationInfo.value,
                                                    icon = if (songLocationInfo.isCloud) Icons.Rounded.Cloud else Icons.Rounded.AudioFile,
                                                    iconDescription = stringResource(
                                                        if (songLocationInfo.isCloud) {
                                                            R.string.cd_provider_icon
                                                        } else {
                                                            R.string.cd_file_icon
                                                        }
                                                    ),
                                                    shape = infoSegmentItemShape,
                                                )
                                            }
                                        }
                                        item {
                                            Spacer(Modifier.height(80.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Tab Bar
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
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
                        title = stringResource(R.string.song_info_tab_options),
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
                                Icons.Rounded.Menu,
                                contentDescription = stringResource(R.string.cd_options),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.song_info_tab_options_badge),
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    TabAnimation(
                        index = 1,
                        title = stringResource(R.string.song_info_tab_details),
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
                                Icons.Rounded.Info,
                                contentDescription = stringResource(R.string.cd_details_tab),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.song_info_tab_info_badge),
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    EditSongSheet(
        visible = showEditSheet,
        song = song,
        onDismiss = { showEditSheet = false },
        onSave = { title, artist, album, albumArtist, composer, genre, lyrics, trackNumber, discNumber, replayGainTrackGainDb, replayGainAlbumGainDb, coverArt ->
            onEditSong(
                title,
                artist,
                album,
                albumArtist,
                composer,
                genre,
                lyrics,
                trackNumber,
                discNumber,
                replayGainTrackGainDb,
                replayGainAlbumGainDb,
                coverArt
            )
            showEditSheet = false
        },
    )

    val artistPickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showArtistPicker && resolvedArtists.isNotEmpty()) {
        com.theveloper.pixelplay.presentation.components.player.PlayerArtistPickerBottomSheet(
            song = song,
            artists = resolvedArtists,
            sheetState = artistPickerSheetState,
            onDismiss = { showArtistPicker = false },
            onArtistClick = { artist ->
                showArtistPicker = false
                onNavigateToArtistById(artist.id)
            }
        )
    }

    if (showTonePickerDialog) {
        ToneTargetPickerDialog(
            onDismiss = { showTonePickerDialog = false },
            onTargetSelected = { target ->
                showTonePickerDialog = false
                toneConfirmationTarget = target
            }
        )
    }

    toneConfirmationTarget?.let { target ->
        ToneConfirmationDialog(
            song = song,
            target = target,
            onDismiss = { toneConfirmationTarget = null },
            onConfirm = {
                toneConfirmationTarget = null
                setCurrentSongAsTone(target)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneTargetPickerDialog(
    onDismiss: () -> Unit,
    onTargetSelected: (ToneTarget) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = null)
                    Text(
                        text = stringResource(R.string.song_info_tone_picker_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.song_info_tone_picker_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Column(
                    modifier = Modifier.clip(RoundedCornerShape(22.dp)),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    ToneTarget.values().forEach { target ->
                        ToneTargetOption(
                            target = target,
                            onClick = { onTargetSelected(target) },
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneTargetOption(
    target: ToneTarget,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape = RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        leadingContent = {
            ToneDialogIcon(
                target = target,
                modifier = Modifier.size(42.dp),
                iconModifier = Modifier.size(22.dp),
            )
        },
        headlineContent = {
            Text(
                text = stringResource(target.titleResId),
                fontWeight = FontWeight.SemiBold,
            )
        },
        supportingContent = {
            Text(stringResource(target.subtitleResId))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToneConfirmationDialog(
    song: Song,
    target: ToneTarget,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusTR = 32.dp,
                smoothnessAsPercentBR = 60,
                cornerRadiusBR = 32.dp,
                smoothnessAsPercentTL = 60,
                cornerRadiusTL = 32.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 32.dp,
                smoothnessAsPercentTR = 60,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToneDialogIcon(target = target)
                    Text(
                        text = stringResource(R.string.song_info_tone_confirm_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(
                        R.string.song_info_tone_confirm_body,
                        song.title,
                        stringResource(target.confirmLabelResId),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    FilledTonalButton(onClick = onConfirm) {
                        Text(stringResource(R.string.song_info_tone_confirm_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToneDialogIcon(
    target: ToneTarget?,
    modifier: Modifier = Modifier.size(56.dp),
    iconModifier: Modifier = Modifier.size(28.dp),
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        when (target) {
            ToneTarget.Ringtone -> Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Notification -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            ToneTarget.Alarm -> Icon(
                painter = painterResource(R.drawable.rounded_alarm_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            null -> Icon(
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = null,
                modifier = iconModifier,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun RingtoneActionButton(
    modifier: Modifier,
    showText: Boolean,
    compactText: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ButtonDefaults.filledTonalButtonColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )

    if (showText) {
        FilledTonalButton(
            modifier = modifier,
            colors = colors,
            contentPadding = PaddingValues(horizontal = if (compactText) 12.dp else 18.dp),
            shape = CircleShape,
            onClick = onClick,
        ) {
            Icon(
                modifier = Modifier.size(if (compactText) 20.dp else 24.dp),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.cd_choose_song_tone),
            )
            Spacer(Modifier.width(if (compactText) 6.dp else 8.dp))
            Text(
                text = stringResource(
                    if (compactText) R.string.song_info_set_as_short else R.string.song_info_choose_tone
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    } else {
        FilledTonalIconButton(
            modifier = modifier,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = CircleShape,
            onClick = onClick,
        ) {
            Icon(
                modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                painter = painterResource(R.drawable.rounded_notifications_active_24),
                contentDescription = stringResource(R.string.cd_choose_song_tone),
            )
        }
    }
}

private val ToneTarget.titleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_title
        ToneTarget.Notification -> R.string.song_info_tone_notification_title
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_title
    }

private val ToneTarget.subtitleResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_subtitle
        ToneTarget.Notification -> R.string.song_info_tone_notification_subtitle
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_subtitle
    }

private val ToneTarget.confirmLabelResId: Int
    get() = when (this) {
        ToneTarget.Ringtone -> R.string.song_info_tone_ringtone_label
        ToneTarget.Notification -> R.string.song_info_tone_notification_label
        ToneTarget.Alarm -> R.string.song_info_tone_alarm_label
    }

@Composable
private fun SongInfoSegmentedListItem(
    headline: String,
    supporting: String,
    icon: ImageVector,
    iconDescription: String,
    shape: Shape,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .clip(shape)
        .let { baseModifier ->
            if (onClick != null) {
                baseModifier.clickable(onClick = onClick)
            } else {
                baseModifier
            }
        }

    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(headline) },
            supportingContent = { Text(supporting) },
            leadingContent = {
                Icon(
                    imageVector = icon,
                    contentDescription = iconDescription,
                )
            }
        )
    }
}
