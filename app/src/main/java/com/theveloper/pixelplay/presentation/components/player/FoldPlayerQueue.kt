package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.subcomps.EnhancedSongListItem
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun FoldPlayerQueue(
    queue: ImmutableList<Song>,
    currentSongId: String?,
    queueName: String,
    onSongClick: (Song, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentIndex = queue.indexOfFirst { it.id == currentSongId }
    LaunchedEffect(currentSongId, queue.size) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }
    Column(modifier) {
        Text(
            text = queueName.ifBlank { stringResource(R.string.queue_source_fallback_label) },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (queue.isEmpty()) {
            Text(
                text = stringResource(R.string.queue_empty_label),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(queue, key = { index, song -> "${song.id}-$index" }) { index, song ->
                    val current = song.id == currentSongId
                    EnhancedSongListItem(
                        song = song,
                        isPlaying = current,
                        isCurrentSong = current,
                        showMoreOptionsButton = false,
                        onMoreOptionsClick = {},
                        onClick = { onSongClick(song, index) },
                    )
                }
            }
        }
    }
}
