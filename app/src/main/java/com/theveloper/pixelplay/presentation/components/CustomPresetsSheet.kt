package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.equalizer.EqualizerPreset
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPresetsSheet(
    presets: List<EqualizerPreset>,
    pinnedPresetsNames: List<String>,
    onPresetSelected: (EqualizerPreset) -> Unit,
    onPinToggled: (EqualizerPreset) -> Unit,
    onRename: (EqualizerPreset) -> Unit,
    onDelete: (EqualizerPreset) -> Unit,
    onDismiss: () -> Unit
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_presets_saved_title),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            if (presets.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.presentation_batch_g_presets_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn {
                    items(presets, key = { it.name }) { preset ->
                        CustomPresetItem(
                            preset = preset,
                            isPinned = pinnedPresetsNames.contains(preset.name),
                            onClick = {
                                onPresetSelected(preset)
                                onDismiss()
                            },
                            onPinClick = { onPinToggled(preset) },
                            onRenameClick = { onRename(preset) },
                            onDeleteClick = { onDelete(preset) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CustomPresetItem(
    preset: EqualizerPreset,
    isPinned: Boolean,
    onClick: () -> Unit,
    onPinClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = preset.displayName.firstOrNull()?.toString()?.uppercase() ?: "C",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontFamily = GoogleSansRounded
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = preset.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Row {
             IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isPinned) stringResource(R.string.presentation_batch_g_presets_cd_unpin) else stringResource(R.string.presentation_batch_g_presets_cd_pin),
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRenameClick) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.presentation_batch_g_presets_cd_rename),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.presentation_batch_g_presets_cd_delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
