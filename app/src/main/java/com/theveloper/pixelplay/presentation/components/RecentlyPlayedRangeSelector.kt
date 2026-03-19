@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import androidx.compose.foundation.lazy.items

private val RecentlyPlayedRangeChipHeight = 44.dp
private val RecentlyPlayedRangeChipHorizontalPadding = 16.dp
private val RecentlyPlayedRangeChipIconSpacing = 8.dp
private val RecentlyPlayedRangeChipBorderWidth = 2.dp

@Composable
fun RecentlyPlayedRangeSelector(
    selected: StatsTimeRange,
    onRangeSelected: (StatsTimeRange) -> Unit,
    modifier: Modifier = Modifier
) {
    val motionScheme = MotionScheme.expressive()

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(StatsTimeRange.entries, key = { it.name }) { range ->
            RecentlyPlayedRangeChip(
                label = range.displayName,
                selected = selected == range,
                onClick = { onRangeSelected(range) },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    fadeOutSpec = null,
                    placementSpec = motionScheme.fastSpatialSpec()
                )
            )
        }
    }
}

@Composable
private fun RecentlyPlayedRangeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val motionScheme = MotionScheme.expressive()

    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.tertiary else Color.Transparent,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "RecentlyPlayedRangeChipContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onTertiary else colors.tertiary,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "RecentlyPlayedRangeChipContentColor"
    )
    val iconSlotWidth by animateDpAsState(
        targetValue = if (selected) {
            FilterChipDefaults.IconSize + RecentlyPlayedRangeChipIconSpacing
        } else {
            0.dp
        },
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "RecentlyPlayedRangeChipIconSlotWidth"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "RecentlyPlayedRangeChipIconAlpha"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "RecentlyPlayedRangeChipIconScale"
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier
            .semantics { role = Role.Tab },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(
            width = RecentlyPlayedRangeChipBorderWidth,
            color = colors.tertiary
        ),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = RecentlyPlayedRangeChipHeight)
                .height(RecentlyPlayedRangeChipHeight)
                .padding(
                    start = RecentlyPlayedRangeChipHorizontalPadding,
                    end = RecentlyPlayedRangeChipHorizontalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(iconSlotWidth)
                    .clipToBounds(),
                contentAlignment = Alignment.CenterStart
            ) {
                Icon(
                    imageVector = Icons.Outlined.History,
                    contentDescription = null,
                    modifier = Modifier
                        .size(FilterChipDefaults.IconSize)
                        .graphicsLayer {
                            alpha = iconAlpha
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
