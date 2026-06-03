package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.MusicFolder
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import java.io.File
import androidx.compose.ui.res.stringResource

import com.theveloper.pixelplay.data.model.SourceScope
import androidx.compose.ui.graphics.painter.Painter

val defaultShape = RoundedCornerShape(26.dp) // Fallback shape
val genHeight = 42.dp

@Composable
fun LibraryActionRow(
    onMainActionClick: () -> Unit,
    iconRotation: Float,
    onSortClick: () -> Unit,
    onLocateClick: () -> Unit = {},
    showSortButton: Boolean,
    showLocateButton: Boolean = false,
    isPlaylistTab: Boolean,
    isFoldersTab: Boolean,
    modifier: Modifier = Modifier,
    // Breadcrumb parameters
    currentFolder: MusicFolder?,
    folderRootPath: String,
    folderRootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit,
    isShuffleEnabled: Boolean = false,
    // Source Scope
    showSourceScopeButton: Boolean = false,
    currentSourceScope: SourceScope = SourceScope.All,
    sourceIconPainter: Painter? = null,
    onSourceScopeClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        horizontalArrangement = Arrangement.Absolute.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = isFoldersTab,
            label = "ActionRowContent",
            transitionSpec = {
                if (targetState) { // Transition to Folders (Breadcrumbs)
                    slideInVertically { height -> height } + fadeIn() togetherWith
                            slideOutVertically { height -> -height } + fadeOut()
                } else { // Transition to other tabs (Buttons)
                    slideInVertically { height -> -height } + fadeIn() togetherWith
                            slideOutVertically { height -> height } + fadeOut()
                }
            },
            modifier = Modifier.weight(1f)
        ) { isFolders ->
            if (isFolders) {
                Breadcrumbs(
                    currentFolder = currentFolder,
                    rootPath = folderRootPath,
                    rootLabel = folderRootLabel,
                    onFolderClick = onFolderClick,
                    onNavigateBack = onNavigateBack
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val buttonContainerColor = MaterialTheme.colorScheme.tertiaryContainer
                    val buttonContentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    
                    FilledTonalButton(
                        onClick = onMainActionClick,
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = buttonContainerColor,
                            contentColor = buttonContentColor
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier.height(genHeight)
                    ) {
                        val icon = if (isPlaylistTab) Icons.AutoMirrored.Rounded.PlaylistAdd else Icons.Rounded.Shuffle
                        val text = if (isPlaylistTab) {
                            stringResource(R.string.library_action_new)
                        } else {
                            stringResource(R.string.shortcut_shuffle_short)
                        }
                        val contentDesc = if (isPlaylistTab) {
                            stringResource(R.string.cd_create_new_playlist)
                        } else {
                            stringResource(R.string.cd_shuffle_play)
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = contentDesc,
                                modifier = Modifier.size(20.dp).rotate(iconRotation)
                            )
                            Text(
                                modifier = Modifier.animateContentSize(),
                                text = text,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }


        Spacer(modifier = Modifier.width(8.dp))

        if (showSortButton) {
            val outerCorner = 26.dp
            
            val sortStartCorner by animateDpAsState(
                targetValue = if (showLocateButton || showSourceScopeButton) 8.dp else outerCorner,
                label = "SortStartCorner"
            )

            val filterEndCorner = 8.dp 
            val filterStartCorner by animateDpAsState(
                targetValue = if (showLocateButton) 8.dp else outerCorner,
                label = "FilterStartCorner"
            )
            
            val locateEndCorner = 8.dp 

            val gapBetweenLocateAndNext by animateDpAsState(
                targetValue = if (showLocateButton) 4.dp else 0.dp,
                label = "GapLocate"
            )
            val gapBetweenFilterAndSort by animateDpAsState(
                targetValue = if (showSourceScopeButton) 4.dp else 0.dp,
                label = "GapFilter"
            )


            Row(verticalAlignment = Alignment.CenterVertically) {
                // Locate Button
                AnimatedVisibility(
                    visible = showLocateButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                    FilledTonalIconButton(
                        onClick = onLocateClick,
                        shape = RoundedCornerShape(
                            topStart = outerCorner,
                            bottomStart = outerCorner,
                            topEnd = locateEndCorner,
                            bottomEnd = locateEndCorner
                        ),
                        modifier = Modifier.size(genHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = stringResource(R.string.cd_locate_current_song)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(gapBetweenLocateAndNext))

                // Source Scope Button
                AnimatedVisibility(
                    visible = showSourceScopeButton,
                    enter = slideInHorizontally(initialOffsetX = { it / 2 }) + fadeIn(),
                    exit = slideOutHorizontally(targetOffsetX = { it / 2 }) + fadeOut()
                ) {
                     val label = when(currentSourceScope) {
                         SourceScope.All -> stringResource(R.string.library_storage_filter_all_songs)
                         SourceScope.Local -> stringResource(R.string.library_storage_filter_offline)
                         is SourceScope.Extension -> stringResource(R.string.library_storage_filter_online)
                     }
                     val tooltipState = rememberTooltipState()

                    @OptIn(ExperimentalMaterial3Api::class)
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = {
                            PlainTooltip {
                                Text(label)
                            }
                        },
                        state = tooltipState
                    ) {
                        FilledTonalButton(
                            onClick = onSourceScopeClick,
                            shape = RoundedCornerShape(
                                topStart = filterStartCorner,
                                bottomStart = filterStartCorner,
                                topEnd = filterEndCorner,
                                bottomEnd = filterEndCorner
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(genHeight).animateContentSize()
                        ) {
                             Row(
                                 verticalAlignment = Alignment.CenterVertically,
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 if (currentSourceScope is SourceScope.Extension && sourceIconPainter != null) {
                                     Icon(
                                         painter = sourceIconPainter,
                                         contentDescription = null,
                                         tint = Color.Unspecified,
                                         modifier = Modifier.size(20.dp)
                                     )
                                 } else {
                                     val icon = when(currentSourceScope) {
                                         SourceScope.All -> Icons.Rounded.Dataset
                                         SourceScope.Local -> Icons.Rounded.PhoneAndroid
                                         else -> Icons.Rounded.Cloud
                                     }
                                     Icon(
                                         imageVector = icon,
                                         contentDescription = null,
                                         modifier = Modifier.size(20.dp)
                                     )
                                 }
                                 
                                 AnimatedVisibility(visible = currentSourceScope !is SourceScope.All) {
                                     Text(
                                         text = label,
                                         style = MaterialTheme.typography.labelLarge,
                                         maxLines = 1,
                                         overflow = TextOverflow.Ellipsis
                                     )
                                 }
                             }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(gapBetweenFilterAndSort))

                // Sort Button
                FilledTonalIconButton(
                    onClick = onSortClick,
                    shape = RoundedCornerShape(
                        topStart = sortStartCorner,
                        bottomStart = sortStartCorner,
                        topEnd = outerCorner,
                        bottomEnd = outerCorner
                    ),
                    modifier = Modifier.size(genHeight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = stringResource(R.string.cd_sort_options),
                    )
                }
            }
        }
    }
}

@Composable
fun Breadcrumbs(
    currentFolder: MusicFolder?,
    rootPath: String,
    rootLabel: String,
    onFolderClick: (String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val rowState = rememberLazyListState()
    val pathSegments = remember(currentFolder?.path, rootPath, rootLabel) {
        val path = currentFolder?.path ?: rootPath
        val normalizedRoot = rootPath.removeSuffix("/")
        val normalizedPath = path.removeSuffix("/")
        val relativePath = normalizedPath
            .removePrefix(normalizedRoot)
            .removePrefix("/")

        if (!normalizedPath.startsWith(normalizedRoot) || relativePath.isEmpty() || normalizedPath == normalizedRoot) {
            listOf(rootLabel to rootPath)
        } else {
            listOf(rootLabel to rootPath) + relativePath.split("/").scan("") { acc, segment ->
                "$acc/$segment"
            }.drop(1).map {
                val file = File(rootPath, it)
                file.name to file.path
            }
        }
    }

    val showStartFade by remember { derivedStateOf { rowState.canScrollBackward } }
    val showEndFade by remember { derivedStateOf { rowState.canScrollForward } }

    LaunchedEffect(pathSegments.size) {
        if (pathSegments.isNotEmpty()) {
            rowState.animateScrollToItem(pathSegments.lastIndex + 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onNavigateBack,
            modifier = Modifier.size(36.dp),
            enabled = currentFolder != null
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
        }
        Spacer(Modifier.width(8.dp))

        LazyRow(
            state = rowState,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                // 1. Forzamos que el contenido se dibuje en una capa separada.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    // 2. Dibujamos el contenido original (el LazyRow).
                    drawContent()

                    // 3. Dibujamos los gradientes que actúan como "máscaras de borrado".
                    val gradientWidth = 24.dp.toPx()

                    // Máscara para el borde IZQUIERDO
                    if (showStartFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de transparente a opaco (negro)
                                colors = listOf(Color.Transparent, Color.Black),
                                endX = gradientWidth
                            ),
                            // DstIn mantiene el contenido del LazyRow solo donde esta capa es opaca.
                            blendMode = BlendMode.DstIn
                        )
                    }

                    // Máscara para el borde DERECHO
                    if (showEndFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                // Gradiente de opaco (negro) a transparente
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = this.size.width - gradientWidth
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
        ) {
            item { Spacer(modifier = Modifier.width(12.dp)) }

            items(pathSegments.size, key = { pathSegments[it].second }) { index ->
                val (name, path) = pathSegments[index]
                val isLast = index == pathSegments.lastIndex
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = GoogleSansRounded,
                    color = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(enabled = !isLast) { onFolderClick(path) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (!isLast) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            item { Spacer(modifier = Modifier.width(12.dp)) }
        }
    }
}
