package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.components.resolveNavBarOccupiedHeight
import com.theveloper.pixelplay.presentation.utils.GenreIconProvider
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.ui.theme.LocalPixelPlayDarkTheme
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(UnstableApi::class)
@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    selectedGenreIds: Set<String> = emptySet(),
    onGenreLongPress: (Genre) -> Unit = {},
    onGenreSelectionToggle: (Genre) -> Unit = {},
    getSelectionIndex: (String) -> Int? = { null }
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.no_genres_available), style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val systemNavBarHeight = getNavigationBarHeight()
    val customGenreIcons = playerViewModel.customGenreIcons.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
        context = kotlin.coroutines.EmptyCoroutineContext
    ).value

    // Persistence: Collect from ViewModel
    val isGridView by playerViewModel.isGenreGridView.collectAsStateWithLifecycle()
    val navBarCompactMode by playerViewModel.navBarCompactMode.collectAsStateWithLifecycle()

    LazyVerticalGrid(
        columns = if (isGridView) GridCells.Fixed(2) else GridCells.Fixed(1),
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusBR = 0.dp,
                smoothnessAsPercentBR = 70,
                cornerRadiusBL = 0.dp,
                smoothnessAsPercentBL = 70
            )),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = 28.dp + resolveNavBarOccupiedHeight(systemNavBarHeight, navBarCompactMode) + MiniPlayerHeight
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.browse_by_genre),
                    style = MaterialTheme.typography.titleLarge
                )
                
                // Toggle Button with persistence and styling
                // Animate between rounded corners in list mode and circular appearance in grid mode.
                val animatedCornerRadius = animateDpAsState(
                    targetValue = if (!isGridView) 12.dp else 50.dp,
                    label = "shapeAnimation"
                )

                FilledIconButton(
                    onClick = { playerViewModel.toggleGenreViewMode() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(animatedCornerRadius.value)
                ) {
                    Icon(
                        imageVector = if (isGridView) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        contentDescription = "Toggle Grid/List View"
                    )
                }
            }
        }
        items(genres, key = { it.id }) { genre ->
            GenreCard(
                genre = genre,
                customIcons = customGenreIcons,
                onClick = { onGenreClick(genre) },
                isGridView = isGridView,
                isSelectionMode = isSelectionMode,
                isSelected = selectedGenreIds.contains(genre.id),
                selectionIndex = getSelectionIndex(genre.id),
                onLongPress = { onGenreLongPress(genre) },
                onSelectionToggle = { onGenreSelectionToggle(genre) }
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    customIcons: Map<String, Int>,
    onClick: () -> Unit,
    isGridView: Boolean,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    selectionIndex: Int? = null,
    onLongPress: () -> Unit = {},
    onSelectionToggle: () -> Unit = {}
) {
    val isDark = LocalPixelPlayDarkTheme.current
    val themeColor = remember(genre, isDark) {
        com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = isDark,
            fallbackGenreId = genre.id
        )
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    val shape = RoundedCornerShape(20.dp)

    val selectionScale by animateFloatAsState(
        targetValue = if (isSelected) 0.98f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "genreCardSelectionScale"
    )
    val selectionBorderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.5.dp else 0.dp,
        animationSpec = tween(durationMillis = 200),
        label = "genreCardSelectionBorder"
    )

    // Layout Modifier Logic
    val cardModifier = if (isGridView) {
        Modifier.aspectRatio(1.2f)
    } else {
        Modifier.fillMaxWidth().height(100.dp) // Fixed height for list view, full width
    }

    Card(
        modifier = cardModifier
            .scale(selectionScale)
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = selectionBorderWidth,
                        color = MaterialTheme.colorScheme.primary,
                        shape = shape
                    )
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .pointerInput(isSelectionMode) {
                detectTapGestures(
                    onTap = {
                        if (isSelectionMode) {
                            onSelectionToggle()
                        } else {
                            onClick()
                        }
                    },
                    onLongPress = {
                        onLongPress()
                    }
                )
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(backgroundColor)
        ) {
            val textMeasurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val titleStartPadding = 14.dp
            val titleEndPadding = if (isGridView) 14.dp else 96.dp
            val titlePresentation = remember(
                genre.id,
                genre.name,
                isGridView,
                maxWidth,
                density.density,
                density.fontScale
            ) {
                val startPaddingPx = with(density) { titleStartPadding.roundToPx() }
                val endPaddingPx = with(density) { titleEndPadding.roundToPx() }
                GenreTypography.resolveTitlePresentation(
                    genreId = genre.id,
                    genreName = genre.name,
                    isGridView = isGridView,
                    cardWidthPx = with(density) { maxWidth.roundToPx() },
                    horizontalPaddingPx = (startPaddingPx + endPaddingPx) / 2,
                    textMeasurer = textMeasurer
                )
            }

            // Genre image in bottom-right corner
            Box(
                modifier = Modifier
                    .size(90.dp) 
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 16.dp) 
            ) {
                SmartImage(
                    model = GenreIconProvider.getGenreImageResource(genre.name, customIcons),
                    contentDescription = "Genre illustration",
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.55f),
                    colorFilter = ColorFilter.tint(onBackgroundColor),
                    contentScale = ContentScale.Crop
                )
            }

            // Genre name in top-left corner
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = titleStartPadding, top = 14.dp, end = titleEndPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = titlePresentation.firstLine,
                    style = titlePresentation.style,
                    color = onBackgroundColor,
                    softWrap = false,
                    minLines = 1,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                titlePresentation.secondLine?.let { secondLine ->
                    Text(
                        text = secondLine,
                        style = titlePresentation.style,
                        color = onBackgroundColor,
                        softWrap = false,
                        minLines = 1,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(titlePresentation.secondLineWidthFraction)
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectionIndex != null && selectionIndex >= 0) {
                        Text(
                            text = selectionIndex.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
