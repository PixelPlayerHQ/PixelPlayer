package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.presentation.components.LocalMaterialTheme
import com.theveloper.pixelplay.presentation.components.player.BottomToggleRow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsMoreBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    lyrics: Lyrics?,
    showSyncedLyrics: Boolean,
    isSyncControlsVisible: Boolean,
    onSaveLyricsAsLrc: () -> Unit,
    onResetImportedLyrics: () -> Unit,
    onToggleSyncControls: () -> Unit,
    isImmersiveTemporarilyDisabled: Boolean,
    onSetImmersiveTemporarilyDisabled: (Boolean) -> Unit,
    keepScreenOnForLyrics: Boolean,
    onKeepScreenOnForLyricsChange: (Boolean) -> Unit,
    // BottomToggleRow params
    isShuffleEnabled: Boolean,
    repeatMode: Int,
    isFavoriteProvider: () -> Boolean,
    onShuffleToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    // Colors
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onAccentColor: Color = MaterialTheme.colorScheme.onPrimary,
    tertiaryColor: Color = MaterialTheme.colorScheme.tertiary,
    onTertiaryColor: Color = MaterialTheme.colorScheme.onTertiary
) {
    val navigationBarsPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var showResetDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = navigationBarsPadding + 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // No Title - "Expressive" relies on visual grouping

            val itemBackgroundColor = contentColor.copy(alpha = 0.08f)

            // Lyrics Actions Group
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = "Lyrics",
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                 // Save lyrics to .lrc
                if (lyrics != null) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.save_lyrics_dialog_title).substringBefore("?")) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_save_24),
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onSaveLyricsAsLrc()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }

                // Reset imported lyrics
                val resetShape = if (lyrics != null) {
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                } else {
                    RoundedCornerShape(24.dp)
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.reset_imported_lyrics)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_restart_alt_24),
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(resetShape)
                        .background(itemBackgroundColor)
                        .clickable {
                            showResetDialog = true
                        },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        leadingIconColor = contentColor
                    )
                )
            }

            // Keep Screen On toggle
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 6.dp, bottom = 6.dp),
                    text = stringResource(R.string.display),
                    color = accentColor,
                    style = MaterialTheme.typography.bodyLargeEmphasized
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.keep_screen_on)) },
                    supportingContent = { Text(stringResource(R.string.keep_screen_on_description)) },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.LightMode,
                            contentDescription = null
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = keepScreenOnForLyrics,
                            onCheckedChange = onKeepScreenOnForLyricsChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = onAccentColor,
                                checkedTrackColor = accentColor,
                                uncheckedThumbColor = contentColor,
                                uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(itemBackgroundColor),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent,
                        headlineColor = contentColor,
                        supportingColor = contentColor.copy(alpha = 0.6f),
                        leadingIconColor = contentColor
                    )
                )
            }

            if (showResetDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("Reset Lyrics?") },
                    text = { Text("Are you sure you want to reset the lyrics for this song?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                showResetDialog = false
                                onDismissRequest()
                                onResetImportedLyrics()
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetDialog = false }
                        ) {
                            Text("Cancel")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            }

            // Sync Settings Group
            if (showSyncedLyrics) {
                 Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                     Text(
                         modifier = Modifier
                             .padding(start = 6.dp, bottom = 6.dp),
                         text = "Controls",
                         color = accentColor,
                         style = MaterialTheme.typography.bodyLargeEmphasized
                     )
                    ListItem(
                        headlineContent = { Text(if (isSyncControlsVisible) "Hide Sync Controls" else "Adjust Sync") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = null
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                            .background(itemBackgroundColor)
                            .clickable {
                                onDismissRequest()
                                onToggleSyncControls()
                            },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )

                    // Immersive Mode Toggle
                    ListItem(
                        headlineContent = { Text("Disable Immersive (Once)") },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = null
                            )
                        },
                        trailingContent = {
                             Switch(
                                modifier = Modifier,
                                checked = isImmersiveTemporarilyDisabled,
                                onCheckedChange = { 
                                    onSetImmersiveTemporarilyDisabled(it)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = onAccentColor,
                                    checkedTrackColor = accentColor,
                                    uncheckedThumbColor = contentColor,
                                    uncheckedTrackColor = contentColor.copy(alpha = 0.3f)
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                            .background(itemBackgroundColor),
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            headlineColor = contentColor,
                            leadingIconColor = contentColor
                        )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            // Playback Options
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    //.background(contentColor.copy(alpha = 0.08f))
                    .padding(vertical = 0.dp, horizontal = 0.dp)
            ) {
                 BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp),
                    isShuffleEnabled = isShuffleEnabled,
                    repeatMode = repeatMode,
                    isFavoriteProvider = isFavoriteProvider,
                    onShuffleToggle = onShuffleToggle,
                    onRepeatToggle = onRepeatToggle,
                    onFavoriteToggle = onFavoriteToggle
                )
            }
        }
    }
}
