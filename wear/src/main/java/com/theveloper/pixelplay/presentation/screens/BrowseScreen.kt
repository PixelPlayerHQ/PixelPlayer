package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.ui.graphics.vector.ImageVector
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.AlwaysOnScalingPositionIndicator
import com.theveloper.pixelplay.presentation.components.WearTopTimeText
import com.theveloper.pixelplay.presentation.theme.LocalWearPalette
import com.theveloper.pixelplay.presentation.theme.screenBackgroundColor
import com.theveloper.pixelplay.presentation.theme.surfaceContainerColor

/**
 * Root browse screen showing library categories.
 * Categories are hardcoded (no network request needed) — the user navigates
 * deeper to load actual library content from the phone.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun BrowseScreen(
    onCategoryClick: (browseType: String, title: String) -> Unit,
) {
    val columnState = rememberResponsiveColumnState()
    val palette = LocalWearPalette.current
    val background = palette.screenBackgroundColor()
    val libraryTitleFont = remember {
        FontFamily(
            Font(
                resId = R.font.gflex_variable,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(650),
                    FontVariation.width(146f),
                    FontVariation.Setting("ROND", 56f),
                    FontVariation.Setting("XTRA", 520f),
                    FontVariation.Setting("YOPQ", 90f),
                    FontVariation.Setting("YTLC", 505f),
                ),
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            columnState = columnState,
        ) {
            item { Spacer(modifier = Modifier.height(2.dp)) }

            item {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.title2,
                    fontFamily = libraryTitleFont,
                    fontWeight = FontWeight(760),
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }

            item {
                BrowseCategoryChip(
                    label = "Favorites",
                    icon = Icons.Rounded.Favorite,
                    iconTint = palette.favoriteActive,
                    onClick = { onCategoryClick("favorites", "Favorites") },
                )
            }

            item {
                BrowseCategoryChip(
                    label = "Playlists",
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    iconTint = palette.shuffleActive,
                    onClick = { onCategoryClick("playlists", "Playlists") },
                )
            }

            item {
                BrowseCategoryChip(
                    label = "Albums",
                    icon = Icons.Rounded.Album,
                    iconTint = palette.repeatActive,
                    onClick = { onCategoryClick("albums", "Albums") },
                )
            }

            item {
                BrowseCategoryChip(
                    label = "Artists",
                    icon = Icons.Rounded.Person,
                    iconTint = palette.textSecondary,
                    onClick = { onCategoryClick("artists", "Artists") },
                )
            }

            item {
                BrowseCategoryChip(
                    label = "All Songs",
                    icon = Icons.Rounded.MusicNote,
                    iconTint = palette.textSecondary,
                    onClick = { onCategoryClick("all_songs", "All Songs") },
                )
            }

            item {
                BrowseCategoryChip(
                    label = "On Watch",
                    icon = Icons.Rounded.Watch,
                    iconTint = palette.textSecondary,
                    onClick = { onCategoryClick("downloads", "On Watch") },
                )
            }
        }

        AlwaysOnScalingPositionIndicator(
            listState = columnState.state,
            modifier = Modifier.align(Alignment.CenterEnd),
            color = palette.textPrimary,
        )

        WearTopTimeText(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(5f),
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun BrowseCategoryChip(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
) {
    val palette = LocalWearPalette.current
    Chip(
        label = {
            Text(
                text = label,
                color = palette.textPrimary,
            )
        },
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        },
        onClick = onClick,
        colors = ChipDefaults.chipColors(
            backgroundColor = palette.surfaceContainerColor(),
            contentColor = palette.chipContent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
