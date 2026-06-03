package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dataset
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.SourceScope
import dev.brahmkshatriya.echo.common.MusicExtension

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceSelectionSheet(
    currentScope: SourceScope,
    installedExtensions: List<MusicExtension>,
    onScopeSelected: (SourceScope) -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_d_library_source),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    SourceItem(
                        label = stringResource(R.string.library_storage_filter_all_songs),
                        icon = { Icon(Icons.Rounded.Dataset, null) },
                        selected = currentScope == SourceScope.All,
                        onClick = {
                            onScopeSelected(SourceScope.All)
                            onDismiss()
                        }
                    )
                }

                item {
                    SourceItem(
                        label = stringResource(R.string.library_storage_filter_offline),
                        icon = { Icon(Icons.Rounded.PhoneAndroid, null) },
                        selected = currentScope == SourceScope.Local,
                        onClick = {
                            onScopeSelected(SourceScope.Local)
                            onDismiss()
                        }
                    )
                }

                if (installedExtensions.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.presentation_batch_d_extensions_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    items(installedExtensions) { extension ->
                        val iconModel = when (val icon = extension.metadata.icon) {
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder -> icon.request.url
                            is dev.brahmkshatriya.echo.common.models.ImageHolder.ResourceUriImageHolder -> icon.uri
                            else -> null
                        }
                        SourceItem(
                            label = extension.metadata.name,
                            icon = {
                                AsyncImage(
                                    model = iconModel,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(id = R.drawable.ic_music_placeholder)
                                )
                            },
                            selected = (currentScope as? SourceScope.Extension)?.extensionId == extension.metadata.id,
                            onClick = {
                                onScopeSelected(SourceScope.Extension(extension.metadata.id))
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItem(
    label: String,
    icon: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_check_circle_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun painterResource(id: Int): androidx.compose.ui.graphics.painter.Painter {
    return androidx.compose.ui.res.painterResource(id)
}
