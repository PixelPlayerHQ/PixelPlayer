package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.brahmkshatriya.echo.common.models.EchoMediaItem
import dev.brahmkshatriya.echo.common.models.Shelf

@Composable
fun ExtensionShelvesSection(
    shelves: List<Shelf>,
    onItemClick: (EchoMediaItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        shelves.forEach { shelf ->
            ExtensionShelf(
                shelf = shelf,
                onItemClick = onItemClick
            )
        }
    }
}

@Composable
fun ExtensionShelf(
    shelf: Shelf,
    onItemClick: (EchoMediaItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 20.dp),
            text = shelf.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        when (shelf) {
            is Shelf.Lists<*> -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(shelf.list) { item ->
                        if (item is EchoMediaItem) {
                            ExtensionMediaItemCard(
                                item = item,
                                onClick = { onItemClick(item) }
                            )
                        }
                    }
                }
            }
            is Shelf.Item -> {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ExtensionMediaItemCard(
                        item = shelf.media,
                        onClick = { onItemClick(shelf.media) }
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
fun ExtensionMediaItemCard(
    item: EchoMediaItem,
    onClick: () -> Unit
) {
    val title = item.title
    val subtitle = item.subtitleWithOutE ?: ""
    val imageUrl = (item.cover as? dev.brahmkshatriya.echo.common.models.ImageHolder.NetworkRequestImageHolder)?.request?.url

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        SmartImage(
            model = imageUrl,
            contentDescription = title,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
