@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.telegram.channel

import com.theveloper.pixelplay.presentation.components.ExpressiveOfflineState

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TelegramChannelSearchSheet(
    onDismissRequest: () -> Unit,
    onSongSelected: (Song) -> Unit,
    viewModel: TelegramChannelSearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val foundChat by viewModel.foundChat.collectAsStateWithLifecycle()
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

    val inputShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        // can be used for resetting sheet on dismiss/every open to start as fresh. Theo you can use this if you want it to reset.
//        onDismissRequest = {
//            viewModel.resetState()
//            onDismissRequest()
//        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            // Expressive drag handle
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .width(48.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
        }
    ) {
        if (!isOnline) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp)
            ) {
                ExpressiveOfflineState(
                    onRetry = {},
                    isDialog = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .heightIn(min = 450.dp)
            ) {
                // Header with expressive typography
                Text(
                    text = stringResource(R.string.telegram_add_channel_sheet_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.telegram_add_channel_sheet_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Expressive Search Input
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(

                        value = searchQuery,
                        onValueChange = viewModel::onQueryChanged,
                        placeholder = {
                            Text(
                                stringResource(R.string.telegram_channel_search_placeholder),
                                fontFamily = GoogleSansRounded
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.weight(1f) .focusRequester(focusRequester),
                        singleLine = true,
                        shape = inputShape,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    // Search button with spring animation
                    val searchButtonEnabled = !isLoading && searchQuery.isNotBlank()

                    FilledIconButton(
                        onClick = viewModel::searchChannel,
                        enabled = searchButtonEnabled,
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = inputShape
                    ) {
                        if (isLoading) {
                            LoadingIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Rounded.Send,
                                contentDescription = stringResource(R.string.common_search)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Status Content Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            // Loading state with expressive animation
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                LoadingIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = statusMessage ?: stringResource(R.string.telegram_searching),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                // Two cases here, same as the dashboard sync UI:
                                // - Known total >= 5000 (flat channel): determinate bar with
                                //   a percentage.
                                // - Unknown total (forum topic fetch, approxTotal == -1): no
                                //   honest percentage available (see TelegramSyncProgress),
                                //   shown as an indeterminate bar with the running count
                                //   instead. Below 5000 with a known total, the indeterminate
                                //   LoadingIndicator above is enough since the fetch finishes
                                //   quickly anyway.
                                val progress = syncProgress
                                if (progress != null && (progress.approxTotal >= 5000 || progress.approxTotal == -1)) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (progress.approxTotal > 0) {
                                        val fraction = (progress.current.toFloat() / progress.approxTotal.toFloat())
                                            .coerceIn(0f, 1f)
                                        LinearWavyProgressIndicator(
                                            progress = { fraction },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${progress.current} / ${progress.approxTotal}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        LinearWavyProgressIndicator(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 32.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "${progress.current} songs so far",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        statusMessage != null -> {
                            // Status message with animated icon
                            val isSuccess = statusMessage!!.contains("Success", ignoreCase = true)

                            val iconScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "iconScale"
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Animated icon with background
                                Box(
                                    modifier = Modifier
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSuccess)
                                                MaterialTheme.colorScheme.primaryContainer
                                            else
                                                MaterialTheme.colorScheme.errorContainer
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSuccess)
                                            Icons.Rounded.CheckCircle
                                        else
                                            Icons.Rounded.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = if (isSuccess)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = statusMessage!!,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )

                                if (isSuccess) {
                                    Spacer(modifier = Modifier.height(32.dp))

                                    MediumExtendedFloatingActionButton(
                                        onClick = {
                                            viewModel.resetState()
                                            onDismissRequest()
                                        },
                                        text = {
                                            Text(
                                                stringResource(R.string.common_done),
                                                fontFamily = GoogleSansRounded,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        },
                                        icon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                                        expanded = true,
                                        shape = CircleShape,
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        else -> {
                            // Empty/Initial state
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Text(
                                    text = stringResource(R.string.telegram_search_channel_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = stringResource(R.string.telegram_search_channel_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = GoogleSansRounded,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
