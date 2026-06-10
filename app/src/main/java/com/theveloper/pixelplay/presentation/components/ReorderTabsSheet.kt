@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import android.view.HapticFeedbackConstants
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.library.LibraryTabId
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderTabsSheet(
    tabs: List<String>,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var localTabs by remember { mutableStateOf(tabs) }

    LaunchedEffect(tabs) {
        localTabs = tabs
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.reorder_tabs_reset_dialog_title)) },
            text = { Text(stringResource(R.string.reorder_tabs_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        localTabs = tabs
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.common_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.common_cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            localTabs = localTabs.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            // Haptic feedback on reorder
            performAppCompatHapticFeedback(
                view,
                appHapticsConfig,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        },
        lazyListState = listState
    )
    var isLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.reorder_tabs_sheet_title),
                        style = MaterialTheme.typography.displaySmall,
                        fontFamily = GoogleSansRounded
                    )
                }
            },
            floatingActionButton = {
                FloatingToolBar(
                    modifier = Modifier,
                    onReset = { showResetDialog = true }, // This will now trigger the dialog
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(700) // Simulate network/db operation
                            onReorder(localTabs)
                            isLoading = false
                            onDismiss()
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContainedLoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reorder_tabs_reordering))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localTabs, key = { it }) { tab ->
                            ReorderableItem(reorderableState, key = tab) { isDragging ->
                                LaunchedEffect(isDragging) {
                                    if (isDragging) {
                                        performAppCompatHapticFeedback(
                                            view,
                                            appHapticsConfig,
                                            HapticFeedbackConstants.GESTURE_START
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = stringResource(R.string.reorder_tabs_cd_drag_handle),
                                            modifier = Modifier.draggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = LibraryTabId.fromStableKey(tab)
                                                ?.let { stringResource(it.labelRes) }
                                                ?: tab,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FloatingToolBar(
    modifier: Modifier,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
){
    val backgroundShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 22.dp,
        smoothnessAsPercentTL = 60,
        cornerRadiusTL = 22.dp,
        smoothnessAsPercentTR = 60,
        cornerRadiusBR = 22.dp,
        smoothnessAsPercentBL = 60,
        cornerRadiusBL = 22.dp,
        smoothnessAsPercentBR = 60
    )
    Box(
        modifier = modifier
            .padding(8.dp)
            .background(
                shape = backgroundShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            )
    ){
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                modifier = Modifier.align(Alignment.CenterVertically),
                onClick = onReset // This now calls the lambda from the parent
            ) {
                Icon(
                    painter = painterResource(R.drawable.outline_restart_alt_24),
                    contentDescription = stringResource(R.string.common_reset),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            MediumExtendedFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.CenterVertically),
                shape = CircleShape,
                onClick = onClick,
                icon = { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.common_done)) },
                text = { Text(stringResource(R.string.common_done)) }
            )
        }
    }
}