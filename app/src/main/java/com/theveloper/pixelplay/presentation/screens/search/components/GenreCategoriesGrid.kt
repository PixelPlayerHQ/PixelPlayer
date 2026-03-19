package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.annotation.OptIn
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.model.Genre
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.NavBarContentHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.getNavigationBarHeight
import com.theveloper.pixelplay.presentation.utils.GenreIconProvider
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(UnstableApi::class)
@Composable
fun GenreCategoriesGrid(
    genres: List<Genre>,
    onGenreClick: (Genre) -> Unit,
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    if (genres.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No genres available.", style = MaterialTheme.typography.bodyLarge)
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
            bottom = 28.dp + NavBarContentHeight + MiniPlayerHeight + systemNavBarHeight
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
                    text = "Browse by genre",
                    style = MaterialTheme.typography.titleLarge
                )
                
                // Toggle Button with persistence and styling
                // "Round to Square (12dp) when selected" logic:
                // Assuming List View is the "Selected" / "Alternative" state.
                val shape = androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (!isGridView) 12f else 50f, // 12dp for List, 50% (Circle) for Grid
                    label = "shapeAnimation"
                )
                
                androidx.compose.material3.FilledIconButton(
                    onClick = { playerViewModel.toggleGenreViewMode() },
                    colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ),
                    shape = RoundedCornerShape(shape.value.dp)
                ) {
                androidx.compose.material3.Icon(
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
                isGridView = isGridView
            )
        }
    }
}

@Composable
private fun GenreCard(
    genre: Genre,
    customIcons: Map<String, Int>,
    onClick: () -> Unit,
    isGridView: Boolean
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val themeColor = remember(genre, isDark) {
        com.theveloper.pixelplay.ui.theme.GenreThemeUtils.getGenreThemeColor(
            genre = genre,
            isDark = isDark,
            fallbackGenreId = genre.id
        )
    }
    val backgroundColor = themeColor.container
    val onBackgroundColor = themeColor.onContainer

    // Layout Modifier Logic
    val cardModifier = if (isGridView) {
        Modifier.aspectRatio(1.2f)
    } else {
        Modifier.fillMaxWidth().height(100.dp) // Fixed height for list view, full width
    }

    Card(
        modifier = cardModifier
            .clip(AbsoluteSmoothCornerShape(
                cornerRadiusTR = 24.dp,
                smoothnessAsPercentTL = 70,
                cornerRadiusTL = 24.dp,
                smoothnessAsPercentTR = 70,
                cornerRadiusBR = 24.dp,
                smoothnessAsPercentBL = 70,
                cornerRadiusBL = 24.dp,
                smoothnessAsPercentBR = 70
            ))
            .clickable(onClick = onClick),
        shape = AbsoluteSmoothCornerShape(
            cornerRadiusTR = 24.dp,
            smoothnessAsPercentTL = 70,
            cornerRadiusTL = 24.dp,
            smoothnessAsPercentTR = 70,
            cornerRadiusBR = 24.dp,
            smoothnessAsPercentBL = 70,
            cornerRadiusBL = 24.dp,
            smoothnessAsPercentBR = 70
        ),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(backgroundColor)
        ) {
            // Imagen del género en esquina inferior derecha
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

            // Nombre del género en esquina superior izquierda
            val baseStyle = GenreTypography.getGenreStyle(genre.id, genre.name)
            val finalStyle = if (isGridView) baseStyle else baseStyle.copy(
                fontSize = baseStyle.fontSize * 1.3f // 30% larger in List View
            )

            Text(
                text = genre.name,
                style = finalStyle.copy(
                    lineHeight = 24.sp 
                ),
                color = onBackgroundColor,
                softWrap = true,
                minLines = 1,
                maxLines = 3, 
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth(if (isGridView) 0.65f else 0.8f) // More width in List View (80%)
                    .padding(start = 14.dp, top = 14.dp, end = 0.dp)
            )
        }
    }
}
