package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel

// 1. Time Filters Definition
enum class TimeFilter(val label: String) {
    TODAY("Today"),
    WEEK_TO_DATE("Week to Date"),
    MONTH_TO_DATE("Month to Date"),
    YEAR_TO_DATE("Year to Date"),
    ALL_TIME("All Time")
}

// 2. Dynamic Logic Wrapper connecting to ViewModel
@Composable
fun DynamicCategoryScreen(
    title: String,
    baseSongs: List<Song>,
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(TimeFilter.WEEK_TO_DATE) }

    // REAL VIEWMODEL FILTERING
    val filteredSongs = remember(baseSongs, selectedFilter) {
        playerViewModel.filterSongsByTime(baseSongs, selectedFilter)
    }

    AdvancedFilteredListScreen(
        title = title,
        songs = filteredSongs,
        selectedFilter = selectedFilter,
        onFilterSelected = { selectedFilter = it },
        onBackClick = onBackClick,
        onSongClick = onSongClick
    )
}

// 3. Perfect Material 3 UI (Matching your Screenshot)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFilteredListScreen(
    title: String,
    songs: List<Song>,
    selectedFilter: TimeFilter,
    onFilterSelected: (TimeFilter) -> Unit,
    onBackClick: () -> Unit,
    onSongClick: (Song) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    Surface(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onBackClick() },
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Large Screen Title
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // Horizontal Filter Chips (Material 3 Expressive Theming)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(TimeFilter.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        modifier = Modifier.clickable { onFilterSelected(filter) },
                        shape = RoundedCornerShape(24.dp), // Perfect Pill Shape
                        // Dynamic Material Theming
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = if (!isSelected) BorderStroke(1.2.dp, MaterialTheme.colorScheme.outlineVariant) else null
                    ) {
                        Text(
                            text = filter.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Content Area (Empty State or List)
            if (songs.isEmpty()) {
                // Empty State Card (Screenshot match)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(24.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No recent plays in ${selectedFilter.label.lowercase()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Change the range or play more songs to fill this timeline.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Populated Vertical Song List
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(songs.size) { index ->
                        val song = songs[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSongClick(song) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                modifier = Modifier.size(56.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(0.dp)
                            ) {
                                SmartImage(
                                    model = song.albumArtUriString,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.displayArtist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

