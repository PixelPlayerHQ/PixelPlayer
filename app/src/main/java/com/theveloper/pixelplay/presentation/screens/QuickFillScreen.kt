@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.presentation.components.SongPickerList
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.utils.GenreIconProvider
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R

@Composable
fun QuickFillDialog(
    visible: Boolean,
    songs: List<Song>, // Initial list of songs (from Unknown genre)
    customGenres: Set<String>,
    customGenreIcons: Map<String, Int>,
    onDismiss: () -> Unit,
    onApply: (List<Song>, String) -> Unit,
    onAddCustomGenre: (String, Int) -> Unit
) {
    if (visible) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
        ) {
            QuickFillContent(
                songs = songs,
                customGenres = customGenres,
                customGenreIcons = customGenreIcons,
                onDismiss = onDismiss,
                onApply = onApply,
                onAddCustomGenre = onAddCustomGenre
            )
        }
    }
}

@Composable
fun QuickFillContent(
    songs: List<Song>,
    customGenres: Set<String>,
    customGenreIcons: Map<String, Int>,
    onDismiss: () -> Unit,
    onApply: (List<Song>, String) -> Unit,
    onAddCustomGenre: (String, Int) -> Unit
) {
    var step by remember { mutableIntStateOf(0) } // 0: Select Songs, 1: Select Genre
    val selectedSongIds = remember { mutableStateMapOf<String, Boolean>() }
    var selectedGenre by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    BackHandler {
        if (step > 0) step-- else onDismiss()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CenterAlignedTopAppBar(
                title = {
                    AnimatedContent(targetState = step, label = "Title") { s ->
                        Text(
                            if (s == 0) stringResource(R.string.presentation_batch_b_quick_fill_select_songs)
                            else stringResource(R.string.presentation_batch_b_quick_fill_choose_genre),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                                textGeometricTransform = TextGeometricTransform(scaleX = 1.1f)
                            )
                        )
                    }
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { if (step > 0) step-- else onDismiss() },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        Icon(
                            if (step > 0) Icons.AutoMirrored.Rounded.ArrowBack else Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.auth_cd_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    if (targetState > initialState) {
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    } else {
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                    }
                },
                modifier = Modifier.weight(1f)
            ) { s ->
                if (s == 0) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                               value = searchQuery,
                               onValueChange = { searchQuery = it },
                               modifier = Modifier
                                   .fillMaxWidth()
                                   .padding(16.dp),
                               label = { Text(stringResource(R.string.presentation_batch_b_search_songs_label)) },
                               leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                               trailingIcon = if (searchQuery.isNotEmpty()) {
                                   { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Rounded.Clear, null) } }
                               } else null,
                               singleLine = true,
                               shape = RoundedCornerShape(16.dp),
                               colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                               )
                        )
                        
                        val filteredSongs = remember(songs, searchQuery) {
                            if (searchQuery.isBlank()) songs 
                            else songs.filter { it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true) }
                        }
                        
                        SongPickerList(
                            filteredSongs = filteredSongs,
                            isLoading = false,
                            selectedSongIds = selectedSongIds,
                            modifier = Modifier.weight(1f),
                            albumShape = CircleShape,
                            searchQuery = searchQuery,
                            contentPadding = PaddingValues(bottom = 100.dp) // Space for docked toolbar
                        )
                    }
                } else {
                    GenreValidatorContent(
                        selectedGenre = selectedGenre,
                        customGenres = customGenres,
                        customGenreIcons = customGenreIcons,
                        onGenreSelected = { selectedGenre = it },
                        onAddCustomGenre = onAddCustomGenre,
                        contentPadding = PaddingValues(bottom = 100.dp)
                    )
                }
            }
        }
        
        // Docked Toolbar
        val isNextEnabled = if (step == 0) selectedSongIds.containsValue(true) else selectedGenre != null
        
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = 16.dp) // System bars handling?
                .fillMaxWidth()
                .height(64.dp)
                .imePadding(),
            shape = AbsoluteSmoothCornerShape(32.dp, 60),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp), // Symmetric 10dp horizontal padding
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step == 0) {
                    // Segmented Buttons: Select All | Clear
                    Row(
                        modifier = Modifier.height(44.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Select All (Left Segment)
                        FilledTonalButton(
                            onClick = { songs.forEach { selectedSongIds[it.id] = true } },
                            shape = RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(stringResource(R.string.presentation_batch_b_select_all), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        
                        // Divider/Spacer
                        Spacer(modifier = Modifier.width(2.dp))
                        
                        // Clear (Right Segment)
                        FilledTonalButton(
                            onClick = { selectedSongIds.clear() },
                            shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 50.dp, bottomEnd = 50.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text(stringResource(R.string.presentation_batch_b_clear), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                } else {
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = run {
                            val genre = selectedGenre
                            if (genre != null) {
                                stringResource(R.string.presentation_batch_b_genre_label_format, genre)
                            } else {
                                stringResource(R.string.presentation_batch_b_select_a_genre)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(16.dp)) // Added separation
                }

                // Action Button
                Button(
                    onClick = {
                        if (step == 0) {
                            step = 1
                        } else {
                            val songsToUpdate = songs.filter { selectedSongIds[it.id] == true }
                            if (songsToUpdate.isNotEmpty() && selectedGenre != null) {
                                onApply(songsToUpdate, selectedGenre!!)
                            }
                        }
                    },
                    enabled = isNextEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = CircleShape,
                    modifier = Modifier
                        .height(44.dp) // Removed padding(end=8.dp) for symmetry
                ) {
                    Text(
                        text = if (step == 0) stringResource(R.string.cd_next_step)
                        else stringResource(R.string.presentation_batch_b_quick_fill),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (step == 0) Icons.AutoMirrored.Rounded.ArrowForward else Icons.Rounded.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun GenreValidatorContent(
    selectedGenre: String?,
    customGenres: Set<String>,
    customGenreIcons: Map<String, Int>,
    onGenreSelected: (String) -> Unit,
    onAddCustomGenre: (String, Int) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    val addCustomCd = stringResource(R.string.presentation_batch_b_add_custom_genre)
    val newGenreLabel = stringResource(R.string.presentation_batch_b_new_genre)
    val addCustomGenreTitle = stringResource(R.string.presentation_batch_b_add_custom_genre_title)
    val genreNameLabel = stringResource(R.string.presentation_batch_b_genre_name_label)
    val selectIconLabel = stringResource(R.string.presentation_batch_b_select_icon)
    val addLabel = stringResource(R.string.presentation_batch_b_add)

    // Merge standard genres and custom genres
    val allGenres = remember(customGenres) {
        (GenreIconProvider.DEFAULT_GENRES + customGenres.toList()).sorted()
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        contentPadding = PaddingValues(
            start = 16.dp, 
            end = 16.dp, 
            top = 16.dp, 
            bottom = contentPadding.calculateBottomPadding() + 16.dp // Respect passed padding
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            // Add Custom Button
            Card(
                onClick = { showCustomDialog = true },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                modifier = Modifier.height(80.dp)
            ) {
                 Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                     Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         Icon(Icons.Rounded.Add, addCustomCd, tint = MaterialTheme.colorScheme.primary)
                         Text(newGenreLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                     }
                 }
            }
        }
        
        items(allGenres, key = { it }) { genre ->
            val isSelected = selectedGenre == genre
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            
            Card(
                onClick = { onGenreSelected(genre) },
                colors = CardDefaults.cardColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                modifier = Modifier.height(80.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                    ) {
                        SmartImage(
                            model = GenreIconProvider.getGenreImageResource(genre, customGenreIcons),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp),
                            colorFilter = ColorFilter.tint(if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = genre, 
                        style = MaterialTheme.typography.bodyMedium, 
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    
    if (showCustomDialog) {
        var newGenreName by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableIntStateOf(GenreIconProvider.SELECTABLE_ICONS.first()) }
        
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(addCustomGenreTitle) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newGenreName,
                        onValueChange = { newGenreName = it },
                        label = { Text(genreNameLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        selectIconLabel,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    // Icon Picker Grid
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(200.dp) // Fixed height for scrollability inside dialog
                    ) {
                        items(GenreIconProvider.SELECTABLE_ICONS, key = { it }) { iconRes ->
                            val isSelected = selectedIcon == iconRes
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .clickable { selectedIcon = iconRes }
                                    .padding(10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(id = iconRes),
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newGenreName.isNotBlank()) {
                            onAddCustomGenre(newGenreName, selectedIcon)
                            onGenreSelected(newGenreName)
                            showCustomDialog = false
                        }
                    }
                ) { Text(addLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        )
    }
}
