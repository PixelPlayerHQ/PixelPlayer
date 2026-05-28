package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.viewmodel.SortOption
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreSortBottomSheet(
    onDismiss: () -> Unit,
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onShuffle: () -> Unit,
    headerContent: @Composable (() -> Unit)? = null
) {
    @Suppress("DEPRECATION")
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,

        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp) // Extra bottom padding for navigation bar
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.presentation_batch_g_genre_sort_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Big expresssive buttons for actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Shuffle Button (Prominent)
                Button(
                    onClick = onShuffle,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = AbsoluteSmoothCornerShape(16.dp, 60)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.presentation_batch_g_genre_sort_shuffle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (headerContent != null) {
                Spacer(modifier = Modifier.height(16.dp))
                headerContent()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Text(
                text = stringResource(R.string.presentation_batch_g_genre_sort_by),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Expressive Sort Options (Cards instead of simple rows)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SortOptionCard(
                    text = stringResource(R.string.presentation_batch_g_genre_sort_artist),
                    icon = Icons.Rounded.Person,
                    isSelected = currentSort == SortOption.ARTIST,
                    onClick = { onSortSelected(SortOption.ARTIST) }
                )
                SortOptionCard(
                    text = stringResource(R.string.presentation_batch_g_genre_sort_album),
                    icon = Icons.Rounded.Album,
                    isSelected = currentSort == SortOption.ALBUM,
                    onClick = { onSortSelected(SortOption.ALBUM) }
                )
                SortOptionCard(
                    text = stringResource(R.string.presentation_batch_g_genre_sort_title_opt),
                    icon = Icons.Rounded.SortByAlpha,
                    isSelected = currentSort == SortOption.TITLE,
                    onClick = { onSortSelected(SortOption.TITLE) }
                )
            }
        }
    }
}

@Composable
fun SortOptionCard(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = AbsoluteSmoothCornerShape(16.dp, 60),
        modifier = Modifier.fillMaxWidth().height(64.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                modifier = Modifier.weight(1f)
            )
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.presentation_batch_g_cd_selected),
                    tint = contentColor
                )
            }
        }
    }
}
