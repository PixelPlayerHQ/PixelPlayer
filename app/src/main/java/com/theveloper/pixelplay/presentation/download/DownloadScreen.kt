package com.theveloper.pixelplay.presentation.download

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.download.CollectionInfo
import com.theveloper.pixelplay.data.download.DownloadJob
import com.theveloper.pixelplay.data.download.ExtTrack
import com.theveloper.pixelplay.data.download.FeedItem
import com.theveloper.pixelplay.data.download.FeedSection
import com.theveloper.pixelplay.data.download.InstalledExtension
import com.theveloper.pixelplay.data.download.JobStatus
import com.theveloper.pixelplay.data.download.SearchFilterOption
import com.theveloper.pixelplay.data.download.collectionTypeOf
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    viewModel: DownloadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onOpenExtensions: () -> Unit,
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.onFolderPicked(it.toString()) }
    }

    LaunchedEffect(ui.error) {
        ui.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(ui.jobs) { viewModel.checkPendingAuth() }
    // Album/artist pages reuse the top bar back button instead of adding their own.
    val detail = ui.details.lastOrNull()
    BackHandler(enabled = detail != null) { viewModel.closeDetail() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.download_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (detail != null) viewModel.closeDetail() else onNavigateBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenExtensions) {
                        Icon(
                            Icons.Rounded.Extension,
                            contentDescription = stringResource(R.string.download_cd_extensions),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Clear the main screen's bottom nav + mini player overlay.
                .padding(bottom = MiniPlayerHeight + 24.dp)
                .padding(horizontal = 16.dp),
        ) {
            if (!ui.safFolderSet) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.download_no_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { folderPicker.launch(null) }) {
                            Text(stringResource(R.string.download_pick_folder), fontFamily = GoogleSansRounded)
                        }
                    }
                }
            }

            if (detail != null) {
                DetailPane(
                    detail = detail,
                    onDownload = viewModel::requestDownload,
                    onDownloadAll = { viewModel.requestDownloadAll(detail.tracks, detail.title) },
                    onOpenAlbum = { viewModel.openAlbum(it, detail.providerId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                SmoothOutlinedField(
                    value = ui.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.padding(vertical = 4.dp),
                    placeholder = ui.searchExtension?.searchBehavior?.placeholder?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.download_search_hint),
                    enabled = !ui.searching,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(ui.query) }),
                    trailingIcon = {
                        if (ui.searching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { viewModel.search(ui.query) }) {
                                Icon(
                                    Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.download_cd_search),
                                )
                            }
                        }
                    },
                )

                if (ui.searchProviders.isNotEmpty()) {
                    SearchSourceBar(
                        providers = ui.searchProviders,
                        selected = ui.searchExtension,
                        filter = ui.searchFilter,
                        onProvider = viewModel::selectSearchProvider,
                        onFilter = viewModel::selectSearchFilter,
                    )
                }

                // Results, or the extension catalog until the user searches.
                if (ui.results.isEmpty() && !ui.searching) {
                    FeedCatalog(
                        sections = ui.feed,
                        loading = ui.feedLoading,
                        onItemClick = viewModel::openFeedItem,
                        onRefresh = viewModel::loadFeed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(ui.results, key = { "${it.providerId}:${it.itemType}:${it.id}" }) { track ->
                            val type = track.collectionType
                            if (type != null) {
                                CollectionRow(
                                    title = track.name,
                                    subtitle = listOf(stringResource(collectionTypeLabel(type)), track.artists)
                                        .filter { it.isNotBlank() }.joinToString(" · "),
                                    coverUrl = track.coverUrl,
                                    round = type == "artist",
                                    onClick = { viewModel.openResult(track) },
                                )
                            } else {
                                DownloadResultRow(track = track, onEnqueue = { viewModel.requestDownload(track) })
                            }
                        }
                    }
                }
            }

            // Queue: only takes space once something is queued.
            if (ui.jobs.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.download_queue),
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = ui.jobs.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ui.jobs, key = { it.itemId }) { job ->
                        DownloadJobRow(job = job, viewModel = viewModel, modifier = Modifier.animateItem())
                    }
                }
            }
        }

        ui.authPrompt?.let { prompt ->
            AuthCodeDialog(
                prompt = prompt,
                onSubmit = viewModel::submitAuthCode,
                onDismiss = viewModel::dismissAuth,
            )
        }

        ui.sheetTracks.firstOrNull()?.let { track ->
            DownloadOptionsSheet(
                track = track,
                title = ui.sheetTitle,
                providers = ui.downloadProviders,
                installedCount = ui.installedAll.size,
                lastProvider = ui.lastProvider,
                lastQuality = ui.lastQuality,
                onDownload = { providerId, quality -> viewModel.enqueue(ui.sheetTracks, quality, providerId) },
                onDismiss = viewModel::dismissDownloadSheet,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeedCatalog(
    sections: List<FeedSection>,
    loading: Boolean,
    onItemClick: (FeedItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pull down to reload the extension home feed; the old sections stay visible meanwhile.
    // Same expressive loading indicator as the library tabs.
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = loading,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                state = pullState,
                isRefreshing = loading,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
    ) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Empty feed still needs a scrollable child, or the pull gesture never reaches the box.
        if (sections.isEmpty()) item { Spacer(Modifier.fillParentMaxSize()) }
        items(sections, key = { "${it.uri}:${it.title}" }) { section ->
            Column {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(section.items, key = { "${it.type}:${it.id}:${it.uri}" }) { item ->
                        FeedItemCard(item = item, onClick = { onItemClick(item) })
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun FeedItemCard(item: FeedItem, onClick: () -> Unit) {
    val isArtist = item.type.equals("artist", ignoreCase = true)
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = item.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(136.dp)
                .clip(if (isArtist) CircleShape else RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        val subtitle = item.artists.ifBlank { item.description.orEmpty() }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Extension source picker + its search filters (songs / albums / artists / playlists). */
@Composable
private fun SearchSourceBar(
    providers: List<InstalledExtension>,
    selected: InstalledExtension?,
    filter: String?,
    onProvider: (String) -> Unit,
    onFilter: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    LazyRow(
        modifier = Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Box {
                FilterChip(
                    selected = false,
                    onClick = { menuOpen = true },
                    label = { Text(selected?.displayName.orEmpty(), fontFamily = GoogleSansRounded) },
                    leadingIcon = { Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                    trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = stringResource(R.string.download_search_source)) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    providers.forEach { ext ->
                        DropdownMenuItem(
                            text = { Text(ext.displayName, fontFamily = GoogleSansRounded) },
                            onClick = {
                                menuOpen = false
                                onProvider(ext.id)
                            },
                        )
                    }
                }
            }
        }
        items(selected?.searchBehavior?.filters.orEmpty(), key = { it.id }) { option ->
            // Same pill chips as the library SearchScreen filters.
            val isSelected = option.id == filter
            FilterChip(
                selected = isSelected,
                onClick = { onFilter(option.id) },
                label = { Text(filterLabel(option), fontFamily = GoogleSansRounded) },
                shape = CircleShape,
                border = null,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                ),
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.rounded_check_circle_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else null,
            )
        }
    }
}

@Composable
private fun filterLabel(option: SearchFilterOption): String =
    when (collectionTypeOf(option.id) ?: option.id.lowercase().removeSuffix("s")) {
        "album" -> stringResource(R.string.library_tab_albums)
        "artist" -> stringResource(R.string.library_tab_artists)
        "playlist" -> stringResource(R.string.library_tab_playlists)
        "track", "song" -> stringResource(R.string.library_tab_songs)
        else -> option.label.ifEmpty { option.id }
    }

private fun collectionTypeLabel(type: String): Int = when (type) {
    "artist" -> R.string.download_type_artist
    "playlist" -> R.string.download_type_playlist
    else -> R.string.download_type_album
}

@Composable
private fun CollectionRow(
    title: String,
    subtitle: String,
    coverUrl: String?,
    round: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(if (round) CircleShape else RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Album / playlist / artist page opened from search or the catalog. */
@Composable
private fun DetailPane(
    detail: DownloadViewModel.Detail,
    onDownload: (ExtTrack) -> Unit,
    onDownloadAll: () -> Unit,
    onOpenAlbum: (CollectionInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArtist = detail.type == "artist"
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = detail.coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(112.dp)
                            .clip(if (isArtist) CircleShape else RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(collectionTypeLabel(detail.type)),
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = detail.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = GoogleSansRounded,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (detail.subtitle.isNotBlank()) {
                            Text(
                                text = detail.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (!isArtist && detail.tracks.isNotEmpty()) {
                            Button(
                                onClick = onDownloadAll,
                                modifier = Modifier.padding(top = 8.dp),
                                shape = RoundedCornerShape(20.dp),
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.download_all, detail.tracks.size),
                                    fontFamily = GoogleSansRounded,
                                )
                            }
                        }
                    }
                }
            }
        }
        when {
            detail.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            detail.error != null -> item(key = "error") {
                Text(
                    text = detail.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
        if (isArtist && detail.tracks.isNotEmpty()) {
            item(key = "top-title") { SectionTitle(stringResource(R.string.download_top_tracks)) }
        }
        items(detail.tracks, key = { "t:${it.providerId}:${it.id}" }) { track ->
            DownloadResultRow(track = track, onEnqueue = { onDownload(track) })
        }
        if (detail.albums.isNotEmpty()) {
            item(key = "albums-title") { SectionTitle(stringResource(R.string.library_tab_albums)) }
            items(detail.albums, key = { "a:${it.id}" }) { album ->
                CollectionRow(
                    title = album.name,
                    subtitle = listOfNotNull(album.releaseDate?.take(4), album.albumType)
                        .filter { it.isNotBlank() }.joinToString(" · "),
                    coverUrl = album.coverUrl,
                    round = false,
                    onClick = { onOpenAlbum(album) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontFamily = GoogleSansRounded,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DownloadResultRow(track: ExtTrack, onEnqueue: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = track.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.name,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (track.albumName.isEmpty()) track.artists
                else stringResource(R.string.download_artists_album, track.artists, track.albumName),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.providerId.isNotEmpty()) {
                AssistChip(
                    onClick = {},
                    label = { Text(track.providerId, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.heightIn(min = 24.dp),
                )
            }
        }
        IconButton(onClick = onEnqueue) {
            Icon(
                Icons.Rounded.Download,
                contentDescription = stringResource(R.string.download_cd_download),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun DownloadJobRow(job: DownloadJob, viewModel: DownloadViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val active = job.status == JobStatus.QUEUED || job.status == JobStatus.DOWNLOADING
    val statusColor = when (job.status) {
        JobStatus.FAILED -> colors.error
        JobStatus.COMPLETED -> colors.primary
        else -> colors.onSurfaceVariant
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = job.track.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceVariant),
                )
                val badge = when (job.status) {
                    JobStatus.COMPLETED -> Icons.Rounded.CheckCircle to colors.primary
                    JobStatus.FAILED -> Icons.Rounded.ErrorOutline to colors.error
                    else -> null
                }
                badge?.let { (icon, tint) ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.scrim.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = job.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (job.track.artists.isNotEmpty()) {
                    Text(
                        text = job.track.artists,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (active) {
                    Spacer(Modifier.height(8.dp))
                    val progressModifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                    // Queued or still resolving (no bytes yet): indeterminate.
                    if (job.status == JobStatus.QUEUED || job.bytesTotal <= 0) {
                        LinearProgressIndicator(
                            modifier = progressModifier,
                            trackColor = colors.secondaryContainer,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { job.progress.coerceIn(0f, 1f) },
                            modifier = progressModifier,
                            trackColor = colors.secondaryContainer,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = bytesLine(job, context),
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = GoogleSansRounded,
                    color = statusColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (job.status == JobStatus.FAILED && !job.error.isNullOrBlank()) {
                    Text(
                        text = job.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (job.status) {
                JobStatus.QUEUED, JobStatus.DOWNLOADING ->
                    IconButton(onClick = { viewModel.cancel(job.itemId) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_cancel))
                    }
                JobStatus.FAILED -> {
                    IconButton(onClick = { viewModel.retry(job.itemId) }) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.download_retry),
                            tint = colors.primary,
                        )
                    }
                    IconButton(onClick = { viewModel.dismiss(job.itemId) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_dismiss))
                    }
                }
                JobStatus.COMPLETED, JobStatus.CANCELLED ->
                    IconButton(onClick = { viewModel.dismiss(job.itemId) }) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.download_dismiss))
                    }
            }
        }
    }
}

private fun bytesLine(job: DownloadJob, context: android.content.Context): String {
    val status = when (job.status) {
        JobStatus.QUEUED -> context.getString(R.string.download_status_queued)
        JobStatus.DOWNLOADING -> context.getString(R.string.download_status_downloading)
        JobStatus.COMPLETED -> context.getString(R.string.download_status_completed)
        JobStatus.FAILED -> context.getString(R.string.download_status_failed)
        JobStatus.CANCELLED -> context.getString(R.string.download_status_cancelled)
    }
    if (job.bytesReceived <= 0 && job.bytesTotal <= 0) return status
    val received = android.text.format.Formatter.formatShortFileSize(context, job.bytesReceived)
    return if (job.bytesTotal > 0) {
        "$status · $received / ${android.text.format.Formatter.formatShortFileSize(context, job.bytesTotal)}"
    } else {
        "$status · $received"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadOptionsSheet(
    track: ExtTrack,
    title: String,
    providers: List<InstalledExtension>,
    installedCount: Int,
    lastProvider: String?,
    lastQuality: String?,
    onDownload: (providerId: String, quality: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Search and download extensions may differ (e.g. found via a metadata-only extension).
    // Preselect: the track's own extension if it can download, else the last one used,
    // else the only one installed. Otherwise the user picks an extension first.
    var selectedProvider by remember(track, providers) {
        mutableStateOf(
            (providers.firstOrNull { it.id.equals(track.providerId, ignoreCase = true) }
                ?: providers.firstOrNull { it.id == lastProvider }
                ?: providers.singleOrNull())?.id.orEmpty(),
        )
    }
    // Quality IDs belong to the chosen extension, so the list appears once one is picked.
    val qualityOptions = remember(selectedProvider, providers) {
        providers.firstOrNull { it.id == selectedProvider }?.qualityOptions.orEmpty()
    }
    var selectedQuality by remember(selectedProvider, qualityOptions) {
        mutableStateOf(
            qualityOptions.firstOrNull { it.id == lastQuality }?.id
                ?: qualityOptions.firstOrNull()?.id
                ?: "LOSSLESS",
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Text(
                text = stringResource(R.string.download_options_title),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = title.ifEmpty { track.name },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            Text(
                text = stringResource(R.string.download_options_extension),
                style = MaterialTheme.typography.labelLarge,
                fontFamily = GoogleSansRounded,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            if (providers.isEmpty()) {
                Text(
                    text = if (installedCount == 0) stringResource(R.string.download_options_no_provider)
                    else stringResource(R.string.download_options_not_download_provider),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                providers.forEach { ext ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedProvider = ext.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedProvider == ext.id, onClick = { selectedProvider = ext.id })
                        Column {
                            Text(ext.displayName, style = MaterialTheme.typography.bodyMedium, fontFamily = GoogleSansRounded)
                            Text(
                                text = ext.id,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (qualityOptions.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.download_options_quality),
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                qualityOptions.forEach { q ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedQuality = q.id }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedQuality == q.id, onClick = { selectedQuality = q.id })
                        Column {
                            Text(
                                text = q.label.ifEmpty { q.id },
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded,
                            )
                            if (q.description.isNotEmpty()) {
                                Text(
                                    text = q.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = { onDownload(selectedProvider, selectedQuality) },
                enabled = selectedProvider.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 32.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.download_options_download), fontFamily = GoogleSansRounded)
            }
        }
    }
}

@Composable
private fun AuthCodeDialog(prompt: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_auth_title), fontFamily = GoogleSansRounded) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.download_auth_message, prompt),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                )
                Spacer(Modifier.height(12.dp))
                SmoothOutlinedField(
                    value = code,
                    onValueChange = { code = it },
                    label = stringResource(R.string.download_auth_code_label),
                    isError = false,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (code.isNotBlank()) onSubmit(code.trim()) },
                enabled = code.isNotBlank(),
            ) {
                Text(stringResource(R.string.download_auth_submit), fontFamily = GoogleSansRounded)
            }
        },
        dismissButton = {
            val uriHandler = LocalUriHandler.current
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.download_auth_cancel), fontFamily = GoogleSansRounded)
                }
                // The browser redirects to spotiflac://session-grant, handled by ExtensionCallbackActivity.
                if (prompt.startsWith("http", ignoreCase = true)) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(prompt) }; onDismiss() }) {
                        Text(stringResource(R.string.download_auth_open), fontFamily = GoogleSansRounded)
                    }
                }
            }
        },
    )
}
