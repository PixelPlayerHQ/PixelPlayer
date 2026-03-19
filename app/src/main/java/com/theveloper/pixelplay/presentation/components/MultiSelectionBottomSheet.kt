package com.theveloper.pixelplay.presentation.components

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Bottom sheet for batch operations on multiple selected songs.
 * Modeled after SongInfoBottomSheet but without the Info/Edit tabs.
 *
 * @param selectedSongs List of selected songs in selection order
 * @param favoriteSongIds Set of song IDs that are currently favorited
 * @param onDismiss Callback when sheet is dismissed
 * @param onPlayAll Play all selected songs
 * @param onAddToQueue Add all to end of queue
 * @param onPlayNext Add all to play next
 * @param onAddToPlaylist Open playlist picker for batch add
 * @param onToggleLikeAll Toggle like status - if all are liked, unlike all; otherwise like all
 * @param onShareAll Share all as ZIP file
 * @param onDeleteAll Delete all from device (with confirmation)
 */
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MultiSelectionBottomSheet(
    selectedSongs: List<Song>,
    favoriteSongIds: Set<String> = emptySet(),
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleLikeAll: (shouldLike: Boolean) -> Unit,
    onShareAll: () -> Unit,
    onDeleteAll: (activity: Activity, onResult: (Boolean) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // Compute if all selected songs are liked
    val allAreLiked by remember(selectedSongs, favoriteSongIds) {
        derivedStateOf {
            selectedSongs.isNotEmpty() && selectedSongs.all { favoriteSongIds.contains(it.id) }
        }
    }
    
    val evenCornerRadius = 26.dp
    val buttonShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = evenCornerRadius, smoothnessAsPercentBR = 60, 
        cornerRadiusBR = evenCornerRadius, smoothnessAsPercentTL = 60, 
        cornerRadiusTL = evenCornerRadius, smoothnessAsPercentBL = 60,
        cornerRadiusBL = evenCornerRadius, smoothnessAsPercentTR = 60
    )

    val favoriteButtonCornerRadius by animateDpAsState(
        targetValue = if (allAreLiked) evenCornerRadius else 60.dp,
        animationSpec = tween(durationMillis = 300), label = "FavoriteCornerAnimation"
    )
    val favoriteButtonContainerColor by animateColorAsState(
        targetValue = if (allAreLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContainerColorAnimation"
    )
    val favoriteButtonContentColor by animateColorAsState(
        targetValue = if (allAreLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 300), label = "FavoriteContentColorAnimation"
    )

    val favoriteButtonShape = remember(favoriteButtonCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTR = favoriteButtonCornerRadius, smoothnessAsPercentBR = 60, cornerRadiusBR = favoriteButtonCornerRadius,
            smoothnessAsPercentTL = 60, cornerRadiusTL = favoriteButtonCornerRadius, smoothnessAsPercentBL = 60,
            cornerRadiusBL = favoriteButtonCornerRadius, smoothnessAsPercentTR = 60
        )
    }
    
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
                // Header with stacked album arts and count - row anchored left
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stacked album arts
                    // Stacked album arts - use calculated width to avoid overlap
                    val stackedImageSize = 66.dp
                    val stackedOverlap = 33.dp
                    val stackedCount = selectedSongs.take(4).size
                    val stackedWidth = if (stackedCount > 0) {
                        (stackedImageSize - stackedOverlap) * (stackedCount - 1) + stackedImageSize
                    } else 0.dp
                    
                    StackedAlbumArts(
                        songs = selectedSongs.take(4),
                        modifier = Modifier
                            .height(74.dp)
                            .width(stackedWidth)
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Song count and label
                    Column {
                        Text(
                            text = "${selectedSongs.size} SONGS",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(
                            modifier = Modifier
                                .height(4.dp)
                                //.fillMaxWidth()
                        )
                        Text(
                            text = "selected",
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
                    // Row 1: Play, Favorite, Share
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
                                onClick = {
                                    onPlayAll()
                                    onDismiss()
                                },
                                elevation = FloatingActionButtonDefaults.elevation(0.dp),
                                shape = buttonShape,
                                icon = {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play all")
                                },
                                text = {
                                    Text(
                                        modifier = Modifier.padding(end = 0.dp),
                                        style = MaterialTheme.typography.titleMediumEmphasized,
                                        text = "Play All"
                                    )
                                }
                            )
                            // Like/Unlike toggle button
                            // If all are liked -> clicking will unlike all
                            // If any is not liked -> clicking will like all
                            FilledIconButton(
                                modifier = Modifier
                                    .weight(0.25f)
                                    .fillMaxHeight(),
                                onClick = {
                                    onToggleLikeAll(!allAreLiked) // true = like all, false = unlike all
                                    onDismiss()
                                },
                                shape = favoriteButtonShape,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = favoriteButtonContainerColor,
                                    contentColor = favoriteButtonContentColor
                                )
                            ) {
                                Icon(
                                    modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                    imageVector = if (allAreLiked) 
                                        Icons.Rounded.HeartBroken 
                                    else
                                        Icons.Rounded.FavoriteBorder,
                                    contentDescription = if (allAreLiked) "Unlike all" else "Like all"
                                )
                            }
                            
                            FilledTonalIconButton(
                                modifier = Modifier
                                    .weight(0.25f)
                                    .fillMaxHeight(),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                ),
                                onClick = {
                                    onShareAll()
                                    onDismiss()
                                },
                                shape = CircleShape
                            ) {
                                Icon(
                                    modifier = Modifier.size(FloatingActionButtonDefaults.LargeIconSize),
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share all as ZIP"
                                )
                            }
                        }
                    }
                    
                    // Row 2: Add to Queue, Play Next
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
                                onClick = {
                                    onAddToQueue()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Add all to Queue"
                                )
                                Spacer(Modifier.width(14.dp))
                                Text("Add to Queue")
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
                                onClick = {
                                    onPlayNext()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.QueueMusic,
                                    contentDescription = "Play Next"
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Next")
                            }
                        }
                    }
                    
                    // Row 3: Add to Playlist, Delete
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
                                    onAddToPlaylist()
                                    onDismiss()
                                }
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistAdd,
                                    contentDescription = "Add to Playlist"
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Playlist")
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
                                    val activity = (context as? Activity)
                                    if (activity != null) {
                                        onDeleteAll(activity) { success ->
                                            if (success) onDismiss()
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete all"
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Delete All")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Displays stacked album art images with overlap effect for the bottom sheet header.
 * Uses CircleShape with a border matching the sheet background color.
 */
@Composable
private fun StackedAlbumArts(
    songs: List<Song>,
    modifier: Modifier = Modifier
) {
    val imageSize = 66.dp
    val overlap = 33.dp
    val borderWidth = 3.dp
    // Use surfaceContainerLow as it matches the bottom sheet background
    val borderColor = MaterialTheme.colorScheme.surfaceContainerLow
    
    Box(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        songs.forEachIndexed { index, song ->
            val offsetX = index * (imageSize.value - overlap.value)
            
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.dp.roundToPx(), 0) }
                    .zIndex((songs.size - index).toFloat())
                    .size(imageSize)
                    // "Border" created by background color
                    .background(borderColor, CircleShape)
            ) {
                // Inner image, inset by border width
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(borderWidth)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    SmartImage(
                        model = song.albumArtUriString,
                        contentDescription = song.title,
                        shape = CircleShape,
                        targetSize = Size(168, 168),
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }
    }
}
