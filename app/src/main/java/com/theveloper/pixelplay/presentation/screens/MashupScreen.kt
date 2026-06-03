package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.viewmodel.MashupViewModel
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SearchStateHolder
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MashupScreen(
    onNavigateBack: () -> Unit,
    viewModel: MashupViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val allSongs by viewModel.uiState.map { it.allSongs }.collectAsStateWithLifecycle(initialValue = emptyList())

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.presentation_batch_d_mashup_title),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.presentation_batch_d_mashup_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Deck Selection
            DeckSlot(
                title = "Deck 1",
                song = uiState.deck1.song,
                onClick = { viewModel.openSongPicker(1) }
            )

            DeckSlot(
                title = "Deck 2",
                song = uiState.deck2.song,
                onClick = { viewModel.openSongPicker(2) }
            )

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { /* Start Mashup */ },
                enabled = uiState.deck1.song != null && uiState.deck2.song != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.presentation_batch_d_mashup_start))
            }
        }

        if (uiState.showSongPickerForDeck != null) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.closeSongPicker() }
            ) {
                SongPickerSheet(
                    songs = allSongs,
                    onSongSelected = { song ->
                        viewModel.loadSong(uiState.showSongPickerForDeck!!, song)
                    },
                    playerViewModel = playerViewModel
                )
            }
        }
    }
}

@Composable
private fun DeckSlot(
    title: String,
    song: Song?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (song != null) MaterialTheme.colorScheme.primaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (song != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(song.albumArtUriString)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(
                    song?.title ?: stringResource(R.string.presentation_batch_d_mashup_select_song),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                if (song != null) {
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SongPickerSheet(
    songs: List<Song>,
    onSongSelected: (Song) -> Unit,
    playerViewModel: PlayerViewModel
) {
    val searchResults by playerViewModel.playerUiState.map { it.searchResults }.collectAsStateWithLifecycle(initialValue = persistentListOf())
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredSongs = remember(songs, searchResults, searchQuery) {
        if (searchQuery.isBlank()) {
            songs.sortedByDescending { it.extensionId != null }
        } else {
            searchResults.mapNotNull { (it as? com.theveloper.pixelplay.data.model.SearchResultItem.SongItem)?.song }
        }
    }

    Column(modifier = Modifier.navigationBarsPadding().fillMaxHeight(0.8f)) {
        Text(
            text = stringResource(R.string.presentation_batch_d_mashup_select_song_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { 
                searchQuery = it
                playerViewModel.searchStateHolder.performSearch(it) 
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search songs for mashup...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { 
                        searchQuery = ""
                        playerViewModel.searchStateHolder.performSearch("") 
                    }) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            items(filteredSongs, key = { it.id }) { song ->
                SongPickerItem(song = song, onClick = { onSongSelected(song) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun SongPickerItem(song: Song, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        leadingContent = {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.albumArtUriString)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        },
        headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
        supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) }
    )
}
