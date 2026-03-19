package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.AutoScrollingTextOnDemand
import com.theveloper.pixelplay.presentation.components.ShimmerBox
import com.theveloper.pixelplay.presentation.components.SmartImage
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/**
 * Enhanced song list item with multi-selection support.
 * 
 * @param song The song to display
 * @param isPlaying Whether this song is currently playing
 * @param isCurrentSong Whether this is the current song in the queue (may be paused)
 * @param isLoading Whether to show loading shimmer state
 * @param showAlbumArt Whether to show the album art
 * @param albumArtSize Size of the album art thumbnail when shown
 * @param customShape Optional custom shape for the surface
 * @param isSelected Whether this item is selected in multi-selection mode
 * @param isSelectionMode Whether multi-selection mode is active
 * @param onLongPress Callback for long press gesture (activates selection)
 * @param onMoreOptionsClick Callback for more options button
 * @param onClick Callback for tap gesture
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedSongListItem(
    modifier: Modifier = Modifier,
    song: Song,
    isPlaying: Boolean,
    isCurrentSong: Boolean = false,
    isLoading: Boolean = false,
    showAlbumArt: Boolean = true,
    albumArtSize: Dp = 50.dp,
    customShape: androidx.compose.ui.graphics.Shape? = null,
    containerColorOverride: Color? = null,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    isSelectionMode: Boolean = false,
    showMoreOptionsButton: Boolean = true,
    onLongPress: () -> Unit = {},
    onMoreOptionsClick: (Song) -> Unit,
    onClick: () -> Unit
) {
    val albumArtTargetSizePx = with(LocalDensity.current) { albumArtSize.roundToPx() * 3 }

    // Animate corner radius based on current song state
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong && !isLoading) 50.dp else 22.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cornerRadiusAnimation"
    )

    val animatedAlbumCornerRadius by animateDpAsState(
        targetValue = if (isCurrentSong && !isLoading) 50.dp else 10.dp,
        animationSpec = tween(durationMillis = 400),
        label = "albumCornerRadiusAnimation"
    )

    // Selection animation - subtle scale effect
    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "selectionScaleAnimation"
    )

    // Selection border width animation
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "selectionBorderAnimation"
    )

    val surfaceShape = remember(animatedCornerRadius, customShape, isCurrentSong, isLoading) {
        if (customShape != null && (!isCurrentSong || isLoading)) {
            customShape
        } else {
            RoundedCornerShape(animatedCornerRadius)
        }
    }

    val albumShape = remember(animatedAlbumCornerRadius) {
        RoundedCornerShape(animatedAlbumCornerRadius)
    }

    val colors = MaterialTheme.colorScheme
    
    // Container colors - selection takes precedence over current song
    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.secondaryContainer
            isCurrentSong && !isLoading -> colors.primaryContainer
            else -> containerColorOverride ?: colors.surfaceContainerLow
        },
        animationSpec = tween(durationMillis = 300),
        label = "containerColorAnimation"
    )
    
    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> colors.onSecondaryContainer
            isCurrentSong && !isLoading -> colors.onPrimaryContainer
            else -> colors.onSurface
        },
        animationSpec = tween(durationMillis = 300),
        label = "contentColorAnimation"
    )

    // Selection border color
    val selectionBorderColor by animateColorAsState(
        targetValue = if (isSelected) colors.primary else colors.primary.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 250),
        label = "borderColorAnimation"
    )

    val mvContainerColor = if ((isCurrentSong) && !isLoading) colors.primaryContainer else colors.onSurface
    val mvContentColor = if ((isCurrentSong) && !isLoading) colors.onPrimaryContainer else colors.surfaceContainerHigh

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = containerColorOverride ?: colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if(showAlbumArt) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(albumArtSize)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = if(showAlbumArt) 0.dp else 4.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Actual Song Item Layout
        var applyTextMarquee by remember { mutableStateOf(false) }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .scale(selectionScale)
                .clip(surfaceShape)
                .then(
                    if (isSelected) {
                        Modifier.border(
                            width = selectionBorderWidth,
                            color = selectionBorderColor,
                            shape = surfaceShape
                        )
                    } else {
                        Modifier
                    }
                )
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = { 
                            if (isSelectionMode) {
                                // In selection mode, tap toggles selection
                                onLongPress()
                            } else {
                                onClick() 
                            }
                        },
                        onLongPress = { 
                            // Long press always activates/toggles selection
                            onLongPress()
                        },
                        onPress = {
                            if (!isSelectionMode) {
                                try {
                                    awaitRelease()
                                } finally {
                                    applyTextMarquee = false
                                }
                            }
                        }
                    )
                },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showAlbumArt) {
                    Box(
                        modifier = Modifier
                            .size(albumArtSize)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    ) {
                        SmartImage(
                            model = song.albumArtUriString,
                            contentDescription = song.title,
                            shape = albumShape,
                            targetSize = Size(albumArtTargetSizePx, albumArtTargetSizePx),
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Selection check overlay on album art
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        color = colors.primary.copy(alpha = 0.7f),
                                        shape = albumShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selectionIndex != null && selectionIndex >= 0) {
                                    Text(
                                        text = "$selectionIndex",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = colors.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                } else {
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    if (applyTextMarquee && !isSelectionMode) {
                        AutoScrollingTextOnDemand(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            gradientEdgeColor = containerColor,
                            expansionFractionProvider = { 1f },
                        )

                    } else {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = contentColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = song.displayArtist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                val showPlayingIndicator = isCurrentSong && !isSelectionMode
                val showTrailingAction = showMoreOptionsButton && !isSelectionMode

                if (showPlayingIndicator) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }

                if (showPlayingIndicator || showTrailingAction) {
                    Spacer(modifier = Modifier.width(12.dp))
                }

                if (showTrailingAction) {
                    FilledIconButton(
                        onClick = { onMoreOptionsClick(song) },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = mvContentColor,
                            contentColor = mvContainerColor
                        ),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More options for ${song.title}",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
