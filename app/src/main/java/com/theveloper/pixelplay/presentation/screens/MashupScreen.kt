package com.theveloper.pixelplay.presentation.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.viewmodel.DeckState
import com.theveloper.pixelplay.presentation.viewmodel.MashupViewModel
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MashupScreen(
    mashupViewModel: MashupViewModel = hiltViewModel()
) {
    val mashupUiState by mashupViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DJ Space") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val isLoading1 = mashupUiState.deck1.song == null && mashupUiState.showSongPickerForDeck == 1
                    val isLoading2 = mashupUiState.deck2.song == null && mashupUiState.showSongPickerForDeck == 2

                    // El resto de la UI (DeckUi, Crossfader) permanece igual
                    DeckUi(
                        deckNumber = 1,
                        deckState = mashupUiState.deck1,
                        isLoading = isLoading1,
                        loadingMessage = "Loading...",
                        onPlayPause = { mashupViewModel.playPause(1) },
                        onVolumeChange = { mashupViewModel.setVolume(1, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(1) },
                        onSeek = { progress -> mashupViewModel.seek(1, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(1, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(1, amount) }
                    )
                    DeckUi(
                        deckNumber = 2,
                        deckState = mashupUiState.deck2,
                        isLoading = isLoading2,
                        loadingMessage = "Loading...",
                        onPlayPause = { mashupViewModel.playPause(2) },
                        onVolumeChange = { mashupViewModel.setVolume(2, it) },
                        onSelectSong = { mashupViewModel.openSongPicker(2) },
                        onSeek = { progress -> mashupViewModel.seek(2, progress) },
                        onSpeedChange = { speed -> mashupViewModel.setSpeed(2, speed) },
                        onNudge = { amount -> mashupViewModel.nudge(2, amount) }
                    )
                }

                Crossfader(
                    value = mashupUiState.crossfaderValue,
                    onValueChange = { mashupViewModel.onCrossfaderChange(it) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            if (mashupUiState.showSongPickerForDeck != null) {
                ModalBottomSheet(
                    onDismissRequest = { mashupViewModel.closeSongPicker() },
                    sheetState = sheetState
                ) {
                    SongPickerSheet(
                        songs = mashupUiState.allSongs,
                        onSongSelected = { song ->
                            scope.launch {
                                val deck = mashupUiState.showSongPickerForDeck ?: return@launch
                                mashupViewModel.loadSong(deck, song)
                            }
                        }
                    )
                }
            }
        }
    }
}



@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeckUi(
    deckNumber: Int,
    deckState: DeckState,
    isLoading: Boolean,
    loadingMessage: String,
    onPlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onSelectSong: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onNudge: (Long) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Deck $deckNumber",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    textAlign = TextAlign.Center
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = !isLoading) { onSelectSong() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (deckState.song != null) {
                            SmartImage(
                                model = deckState.song.albumArtUriString,
                                contentDescription = "Song Cover",
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(painterResource(id = R.drawable.rounded_playlist_add_24), "Load Song", modifier = Modifier.size(40.dp))
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(deckState.song?.title ?: "No song loaded", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(deckState.song?.artist ?: "...", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = deckState.progress,
                            onValueChange = onSeek,
                            valueRange = 0f..1f,
                            enabled = deckState.song != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                AnimatedVisibility(deckState.song != null && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Stem Separation not available yet.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(onClick = { onNudge(-100) }, enabled = deckState.song != null) { Text("<<") }
                    IconButton(onClick = onPlayPause, enabled = deckState.song != null, modifier = Modifier.size(56.dp)) {
                        Icon(painter = painterResource(if (deckState.isPlaying) R.drawable.rounded_pause_24 else R.drawable.rounded_play_arrow_24), contentDescription = "Play/Pause", modifier = Modifier.fillMaxSize())
                    }
                    OutlinedButton(onClick = { onNudge(100) }, enabled = deckState.song != null) { Text(">>") }
                }

                Column(modifier = Modifier.padding(top = 8.dp)) {
                    SliderControl(label = "Volume", value = deckState.volume, onValueChange = onVolumeChange, valueRange = 0f..1f, enabled = deckState.song != null)
                    SliderControl(label = "Speed", value = deckState.speed, onValueChange = onSpeedChange, valueRange = 0.5f..2f, steps = 14, enabled = deckState.song != null) {
                        Text(text = "x${"%.2f".format(deckState.speed)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (isLoading) {
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
                    Spacer(Modifier.height(16.dp))
                    Text(loadingMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun SliderControl(
    label: String, value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0, enabled: Boolean, endContent: @Composable (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(60.dp), color = if(enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f))
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, steps = steps, modifier = Modifier.weight(1f), enabled = enabled)
        if (endContent != null) {
            Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) { endContent() }
        }
    }
}

@Composable
private fun Crossfader(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Crossfader", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Deck 1", style = MaterialTheme.typography.bodyMedium)
            Slider(value = value, onValueChange = onValueChange, valueRange = -1f..1f, modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp))
            Text("Deck 2", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SongPickerSheet(songs: List<Song>, onSongSelected: (Song) -> Unit) {
    Column(modifier = Modifier.navigationBarsPadding()) {
        Text("Select a Song", style = MaterialTheme.typography.titleLarge, modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp), textAlign = TextAlign.Center)
        LazyColumn(modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)) {
            items(songs, key = { it.id }) { song ->
                SongPickerItem(song = song, onClick = { onSongSelected(song) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SongPickerItem(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmartImage(
            model = song.albumArtUriString,
            contentDescription = "Song Cover",
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
            Text(text = song.displayArtist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
