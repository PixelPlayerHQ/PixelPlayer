@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.media3.common.util.UnstableApi
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.model.SourceScope
import com.theveloper.pixelplay.presentation.screens.TabAnimation
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import com.theveloper.pixelplay.ui.theme.ShapeCache
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.map

@Composable
fun SongPickerBottomSheet(
    onDismiss: () -> Unit,
    onSongsSelected: (Set<String>) -> Unit,
    initialSelectedIds: Set<String> = emptySet(),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedSongIds = remember { 
        mutableStateMapOf<String, Boolean>().apply {
            initialSelectedIds.forEach { put(it, true) }
        }
    }

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Black.copy(alpha = 0.6f)
    ) {
        SongPickerContent(
            selectedSongIds = selectedSongIds,
            onConfirm = { 
                onSongsSelected(it)
                onDismiss()
            },
            playerViewModel = playerViewModel
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerContent(
    selectedSongIds: MutableMap<String, Boolean>,
    onConfirm: (Set<String>) -> Unit,
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    val sourceScope by playerViewModel.playlistPickerSourceScope.collectAsStateWithLifecycle()
    val hasCloudSongs by playerViewModel.hasCloudSongsFlow.collectAsStateWithLifecycle()
    val showCloudFilter = hasCloudSongs != false

    LaunchedEffect(hasCloudSongs, sourceScope) {
        if (hasCloudSongs == false && sourceScope != SourceScope.Local) {
            playerViewModel.setPlaylistPickerSourceScope(SourceScope.Local)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 26.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.song_picker_title),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = GoogleSansRounded
                )
            }
        },
        bottomBar = {
            if (showCloudFilter) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val tabs = listOf(
                        SourceScope.Local to R.string.library_storage_filter_offline,
                        SourceScope.All to R.string.library_storage_filter_all_songs
                    )
                    val selectedTabIndex = tabs.indexOfFirst { it.first == sourceScope }.coerceAtLeast(0)

                    PrimaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier = Modifier
                            .weight(1f)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(5.dp),
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {}
                    ) {
                        tabs.forEachIndexed { index, (scope, labelRes) ->
                            TabAnimation(
                                index = index,
                                title = stringResource(labelRes),
                                selectedIndex = selectedTabIndex,
                                onClick = { playerViewModel.setPlaylistPickerSourceScope(scope) },
                                transformOrigin = if (index == 0) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    if (scope == SourceScope.Local) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_phonef),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Cloud,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(labelRes),
                                        fontFamily = GoogleSansRounded,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    FilledIconButton(
                        onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) },
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.cd_confirm_add_songs),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    LargeExtendedFloatingActionButton(
                        onClick = { onConfirm(selectedSongIds.filterValues { it }.keys) },
                        modifier = Modifier.align(Alignment.CenterEnd),
                        shape = RoundedCornerShape(20.dp),
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.song_picker_action_add),
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        SongPickerSelectionPane(
            selectedSongIds = selectedSongIds,
            modifier = Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding()),
            contentPadding = PaddingValues(
                bottom = if (showCloudFilter) 120.dp else 128.dp,
                top = 8.dp
            ),
            playerViewModel = playerViewModel
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerSelectionPane(
    selectedSongIds: MutableMap<String, Boolean>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp),
    playerViewModel: PlayerViewModel = hiltViewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    val sourceScope by playerViewModel.playlistPickerSourceScope.collectAsStateWithLifecycle()
    
    val pagedSongs = playerViewModel.playlistPickerSongs.collectAsLazyPagingItems()
    val pagedFavoriteSongs = playerViewModel.playlistPickerFavoriteSongs.collectAsLazyPagingItems()

    val favoriteIds by playerViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    
    val searchResultsInitialValue: List<Song>? = remember(searchQuery) {
        if (searchQuery.isBlank()) emptyList() else null
    }
    val searchResults by remember(searchQuery, playerViewModel, sourceScope) {
        playerViewModel.searchSongs(searchQuery)
            .map { songs ->
                when (sourceScope) {
                    SourceScope.Local -> songs.filter { s ->
                        s.extensionId == null
                    }
                    is SourceScope.Extension -> songs.filter { s ->
                        s.extensionId == (sourceScope as SourceScope.Extension).extensionId
                    }
                    else -> songs
                }
            }
            .map<List<Song>, List<Song>?> { it }
    }.collectAsStateWithLifecycle(initialValue = searchResultsInitialValue)

    val animatedAlbumCornerRadius = 60.dp
    val albumShape = remember(animatedAlbumCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedAlbumCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedAlbumCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedAlbumCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedAlbumCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        SongPickerSearchField(
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = {
                    Text(
                        text = stringResource(R.string.song_picker_filter_favorites),
                        fontFamily = GoogleSansRounded,
                        fontWeight = if (favoritesOnly) FontWeight.Bold else FontWeight.Medium
                    )
                },
                shape = if (favoritesOnly) ShapeCache.smooth12 else ShapeCache.smoothPill,
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = favoritesOnly,
                    borderColor = Color.Transparent,
                    selectedBorderColor = Color.Transparent,
                    borderWidth = 0.dp,
                    selectedBorderWidth = 0.dp
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                leadingIcon = {
                    Icon(
                        painter = painterResource(
                            if (favoritesOnly) R.drawable.round_favorite_24
                            else R.drawable.round_favorite_border_24
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                    )
                }
            )
        }

        when {
            searchQuery.isNotBlank() -> {
                val displayed = (searchResults ?: emptyList()).let { results ->
                    if (favoritesOnly) results.filter { it.id in favoriteIds } else results
                }
                SongPickerList(
                    filteredSongs = displayed,
                    isLoading = searchResults == null,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    searchQuery = searchQuery,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding
                )
            }
            favoritesOnly -> {
                SongPickerPagingList(
                    pagedSongs = pagedFavoriteSongs,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    tabId = LibraryTabId.LIKED,
                    sourceScope = sourceScope,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding
                )
            }
            else -> {
                SongPickerPagingList(
                    pagedSongs = pagedSongs,
                    selectedSongIds = selectedSongIds,
                    albumShape = albumShape,
                    tabId = LibraryTabId.SONGS,
                    sourceScope = sourceScope,
                    modifier = Modifier.weight(1f),
                    contentPadding = contentPadding
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SongPickerPagingList(
    pagedSongs: LazyPagingItems<Song>,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    tabId: LibraryTabId,
    sourceScope: SourceScope,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp)
) {
    when {
        pagedSongs.loadState.refresh is LoadState.Loading && pagedSongs.itemCount == 0 -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        pagedSongs.loadState.refresh is LoadState.Error && pagedSongs.itemCount == 0 -> {
            val error = (pagedSongs.loadState.refresh as LoadState.Error).error
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = error.localizedMessage ?: stringResource(R.string.song_picker_error_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = { pagedSongs.retry() }) {
                        Text(stringResource(R.string.library_retry), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        pagedSongs.itemCount == 0 && pagedSongs.loadState.refresh is LoadState.NotLoading && pagedSongs.loadState.append.endOfPaginationReached -> {
            SongPickerEmptyState(
                tabId = tabId,
                sourceScope = sourceScope,
                modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding())
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding
            ) {
                items(
                    count = pagedSongs.itemCount,
                    key = { index -> pagedSongs[index]?.id ?: "item_$index" }
                ) { index ->
                    pagedSongs[index]?.let { song ->
                        SongPickerItem(
                            song = song,
                            isSelected = selectedSongIds[song.id] == true,
                            onToggle = { selectedSongIds[song.id] = it },
                            albumShape = albumShape
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SongPickerEmptyState(
    tabId: LibraryTabId,
    sourceScope: SourceScope,
    modifier: Modifier = Modifier
) {
    val spec = when (tabId) {
        LibraryTabId.LIKED -> when (sourceScope) {
            SourceScope.Local -> Triple(R.drawable.round_favorite_24, R.string.lib_empty_liked_offline_title, R.string.lib_empty_liked_offline_subtitle)
            else -> Triple(R.drawable.round_favorite_24, R.string.lib_empty_liked_all_title, R.string.lib_empty_liked_all_subtitle)
        }
        else -> when (sourceScope) {
            SourceScope.Local -> Triple(R.drawable.rounded_music_off_24, R.string.lib_empty_songs_offline_title, R.string.lib_empty_songs_offline_subtitle)
            else -> Triple(R.drawable.rounded_music_off_24, R.string.lib_empty_songs_all_title, R.string.lib_empty_songs_all_subtitle)
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(spec.first),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(spec.second),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(spec.third),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SongPickerSearchField(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.song_picker_search_placeholder)) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(painterResource(R.drawable.rounded_close_24), null)
                }
            }
        },
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun SongPickerList(
    filteredSongs: List<Song>,
    isLoading: Boolean,
    selectedSongIds: MutableMap<String, Boolean>,
    albumShape: androidx.compose.ui.graphics.Shape,
    searchQuery: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    if (isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (filteredSongs.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.search_no_results_for, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = contentPadding
        ) {
            items(filteredSongs, key = { it.id }) { song ->
                SongPickerItem(
                    song = song,
                    isSelected = selectedSongIds[song.id] == true,
                    onToggle = { selectedSongIds[song.id] = it },
                    albumShape = albumShape
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun SongPickerItem(
    song: Song,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    albumShape: androidx.compose.ui.graphics.Shape
) {
    ListItem(
        modifier = Modifier.clickable { onToggle(!isSelected) },
        headlineContent = {
            Text(
                text = song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Text(
                text = song.artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(song.albumArtUriString)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(albumShape),
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(R.drawable.ic_music_placeholder),
                    error = painterResource(R.drawable.ic_music_placeholder)
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.3f), albumShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },
        trailingContent = {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun IconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, content = content)
}
