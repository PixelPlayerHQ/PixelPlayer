package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.stats.PlaybackStatsRepository
import com.theveloper.pixelplay.data.stats.StatsTimeRange
import com.theveloper.pixelplay.utils.formatListeningDurationCompact
import com.theveloper.pixelplay.utils.formatListeningDurationLong
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsOverviewCard(
    modifier: Modifier = Modifier,
    summary: PlaybackStatsRepository.PlaybackStatsSummary?,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val shape = AbsoluteSmoothCornerShape(
        cornerRadiusTL = 28.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 28.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusBL = 28.dp,
        smoothnessAsPercentBR = 60,
        cornerRadiusTR = 28.dp,
        smoothnessAsPercentBL = 60,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
                )
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.surfaceContainer),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        Modifier.padding(start = 24.dp, top = 24.dp, bottom = 24.dp)
                    ) {
                        Text(
                            text = "Listening stats",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = summary?.range?.displayName ?: StatsTimeRange.WEEK.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(40.dp)
                            .clip(CircleShape)//RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Crossfade(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    targetState = summary
                ) { currentSummary ->
                    if (currentSummary == null) {
                        PlaceholderOverviewContent()
                    } else {
                        OverviewContent(currentSummary)
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewContent(summary: PlaybackStatsRepository.PlaybackStatsSummary) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = formatListeningDurationLong(summary.totalDurationMs),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Total plays",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = summary.totalPlayCount.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Avg per day",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatListeningDurationCompact(summary.averageDailyDurationMs),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        val topTrack = summary.topSongs.firstOrNull()
        if (topTrack != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Top track",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = topTrack.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${topTrack.artist} • ${topTrack.playCount} plays",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        MiniListeningTimeline(summary)
    }
}

@Composable
private fun PlaceholderOverviewContent() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaceholderLine(width = 140.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            PlaceholderLine(width = 60.dp)
            PlaceholderLine(width = 60.dp)
        }
        PlaceholderLine(width = 120.dp)
        MiniListeningTimeline(null)
    }
}

@Composable
private fun MiniListeningTimeline(summary: PlaybackStatsRepository.PlaybackStatsSummary?) {
    val timeline = summary?.timeline ?: emptyList()
    val maxDuration = timeline.maxOfOrNull { it.totalDurationMs }?.takeIf { it > 0 } ?: 1L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        val entries = if (timeline.isEmpty()) {
            List(5) { null }
        } else {
            timeline.takeLast(minOf(7, timeline.size))
        }
        entries.forEach { entry ->
            val heightFraction = entry?.let { it.totalDurationMs.toFloat() / maxDuration.toFloat() }?.coerceIn(0f, 1f) ?: 0.1f
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((70.dp * heightFraction).coerceAtLeast(10.dp))
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary
//                            Brush.verticalGradient(
//                                listOf(
//                                    MaterialTheme.colorScheme.primary,
//                                    MaterialTheme.colorScheme.tertiary
//                                )
//                            )
                        )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry?.label ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlaceholderLine(width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .height(18.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    )
}
