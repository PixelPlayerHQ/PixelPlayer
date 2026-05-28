package com.theveloper.pixelplay.presentation.components

import android.app.Activity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.theveloper.pixelplay.data.model.Playlist
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.utils.resolvePlaylistCoverContentColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R

/**
 * Bottom sheet for batch operations on multiple selected playlists.
 * Designed to match the MultiSelectionBottomSheet (songs) in layout, animation and styling.
 *
 * @param selectedPlaylists List of selected playlists
 * @param onDismiss Callback when sheet is dismissed
 * @param onDeleteAll Delete all selected playlists
 * @param onExportAll Export all selected playlists as M3U files
 * @param onMergeAll Merge all selected playlists into one
 * @param onShareAll Share all selected playlists as ZIP file
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistMultiSelectionBottomSheet(
    selectedPlaylists: List<Playlist>,
    onDismiss: () -> Unit,
    onDeleteAll: () -> Unit,
    onExportAll: () -> Unit,
    onMergeAll: () -> Unit,
    onShareAll: () -> Unit
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val evenCornerRadius = 26.dp
    val buttonShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = evenCornerRadius, smoothnessAsPercentBR = 60,
        cornerRadiusBR = evenCornerRadius, smoothnessAsPercentTL = 60,
        cornerRadiusTL = evenCornerRadius, smoothnessAsPercentBL = 60,
        cornerRadiusBL = evenCornerRadius, smoothnessAsPercentTR = 60
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Box(
            modifier = Modifier
                .animateContentSize(animationSpec = tween(durationMillis = 200))
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Header with stacked playlist covers and count - matching song sheet layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stacked playlist covers
                    val stackedImageSize = 66.dp
                    val stackedOverlap = 33.dp
                    val stackedCount = selectedPlaylists.take(4).size
                    val stackedWidth = if (stackedCount > 0) {
                        (stackedImageSize - stackedOverlap) * (stackedCount - 1) + stackedImageSize
                    } else 0.dp

                    StackedPlaylistCovers(
                        playlists = selectedPlaylists.take(4),
                        modifier = Modifier
                            .height(74.dp)
                            .width(stackedWidth)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Playlist count and label
                    Column {
                        Text(
                            text = stringResource(R.string.multi_selection_playlists_count_upper, selectedPlaylists.size),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.multi_selection_selected),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = GoogleSansRounded
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Row 1: Delete, Export
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
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shape = CircleShape,
                                onClick = {
                                    onDeleteAll()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.cd_delete_all_songs)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.delete_action))
                            }

                            FilledTonalButton(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .heightIn(min = 66.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                ),
                                shape = CircleShape,
                                onClick = {
                                    onExportAll()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.FileDownload,
                                    contentDescription = stringResource(R.string.cd_export_all)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_export))
                            }
                        }
                    }

                    // Row 2: Merge, Share
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
                                onClick = {
                                    onMergeAll()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Merge,
                                    contentDescription = stringResource(R.string.cd_merge_all)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_merge))
                            }

                            FilledTonalButton(
                                modifier = Modifier
                                    .weight(0.5f)
                                    .heightIn(min = 66.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = CircleShape,
                                onClick = {
                                    onShareAll()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Share,
                                    contentDescription = stringResource(R.string.cd_share_all)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_share))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displays stacked playlist cover images with overlap effect for the bottom sheet header.
 * Mirrors the StackedAlbumArts component from MultiSelectionBottomSheet.
 */
@Composable
private fun StackedPlaylistCovers(
    playlists: List<Playlist>,
    modifier: Modifier = Modifier
) {
    val imageSize = 66.dp
    val overlap = 33.dp
    val borderWidth = 3.dp
    val borderColor = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        playlists.forEachIndexed { index, playlist ->
            val offsetX = index * (imageSize.value - overlap.value)

            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex((playlists.size - index).toFloat())
                    .size(imageSize)
                    .background(borderColor, CircleShape)
            ) {
                // Inner cover, inset by border width
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(borderWidth)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (playlist.coverImageUri != null) {
                        AsyncImage(
                            model = playlist.coverImageUri,
                            contentDescription = playlist.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    } else if (playlist.coverColorArgb != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color(playlist.coverColorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            // Show icon if available, otherwise show first letter of playlist name
                            val contentColor = resolvePlaylistCoverContentColor(
                                playlist.coverColorArgb,
                                MaterialTheme.colorScheme
                            )
                            
                            if (playlist.coverIconName != null) {
                                Icon(
                                    imageVector = getPlaylistIconByName(playlist.coverIconName) ?: androidx.compose.material.icons.Icons.Filled.MusicNote,
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Text(
                                    text = playlist.name.firstOrNull()?.toString()?.uppercase() ?: "P",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            }
                        }
                    } else {
                        // Default fallback: show playlist icon or first letter with theme color
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = playlist.name.firstOrNull()?.toString()?.uppercase() ?: "P",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getPlaylistIconByName(name: String?): androidx.compose.ui.graphics.vector.ImageVector? {
    return when (name) {
        "MusicNote" -> Icons.Rounded.MusicNote
        "Headphones" -> Icons.Rounded.Headphones
        "Album" -> Icons.Rounded.Album
        "Mic" -> Icons.Rounded.MicExternalOn
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.AutoMirrored.Rounded.QueueMusic
        else -> Icons.Rounded.MusicNote
    }
}
