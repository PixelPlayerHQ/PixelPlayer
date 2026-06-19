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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
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
import com.theveloper.pixelplay.data.model.LibraryTabId
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReorderTabsSheet(
    visibleTabs: List<String>,
    hiddenTabs: List<String>,
    onSave: (visible: List<String>, hidden: Set<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var localVisibleTabs by remember { mutableStateOf(visibleTabs) }
    var localHiddenTabs by remember { mutableStateOf(hiddenTabs) }

    LaunchedEffect(visibleTabs, hiddenTabs) {
        localVisibleTabs = visibleTabs
        localHiddenTabs = hiddenTabs
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
                        // Local state will be updated by the LaunchedEffect when visibleTabs/hiddenTabs change via VM
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
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState

            // Only move if both items are in the visible section
            if (fromKey.startsWith("v_") && toKey.startsWith("v_")) {
                val fromId = fromKey.removePrefix("v_")
                val toId = toKey.removePrefix("v_")

                val fromIdx = localVisibleTabs.indexOf(fromId)
                val toIdx = localVisibleTabs.indexOf(toId)

                if (fromIdx != -1 && toIdx != -1) {
                    localVisibleTabs = localVisibleTabs.toMutableList().apply {
                        add(toIdx, removeAt(fromIdx))
                    }
                    // Haptic feedback on reorder
                    performAppCompatHapticFeedback(
                        view,
                        appHapticsConfig,
                        HapticFeedbackConstants.CLOCK_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                }
            }
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
                    onReset = { showResetDialog = true },
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(400) // Visual confirmation
                            onSave(localVisibleTabs, localHiddenTabs.toSet())
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
                        contentPadding = PaddingValues(bottom = 150.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        items(localVisibleTabs, key = { "v_$it" }) { tab ->
                            ReorderableItem(reorderableState, key = "v_$tab") { isDragging ->
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
                                        .height(60.dp)
                                        .clip(CircleShape),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = stringResource(R.string.reorder_tabs_cd_drag_handle),
                                            modifier = Modifier.draggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = LibraryTabId.fromStorageKey(tab)
                                                .let { stringResource(it.titleRes) },
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )

                                        if (localVisibleTabs.size > 2) {
                                            Surface(
                                                onClick = {
                                                    performAppCompatHapticFeedback(
                                                        view,
                                                        appHapticsConfig,
                                                        HapticFeedbackConstants.CLOCK_TICK,
                                                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                                    )
                                                    localVisibleTabs = localVisibleTabs.filter { it != tab }
                                                    localHiddenTabs = localHiddenTabs + tab
                                                },
                                                modifier = Modifier.size(36.dp),
                                                shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                                            ) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Clear,
                                                        contentDescription = stringResource(R.string.reorder_tabs_cd_remove_tab),
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        } else {
                                            Spacer(modifier = Modifier.width(36.dp))
                                        }
                                    }
                                }
                            }
                        }

                        if (localHiddenTabs.isNotEmpty()) {
                            item(key = "h_hidden") {
                                Text(
                                    text = stringResource(R.string.reorder_tabs_hidden_section),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = GoogleSansRounded,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            items(localHiddenTabs, key = { "h_$it" }) { tab ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clip(CircleShape),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = LibraryTabId.fromStorageKey(tab)
                                                .let { stringResource(it.titleRes) },
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            onClick = {
                                                performAppCompatHapticFeedback(
                                                    view,
                                                    appHapticsConfig,
                                                    HapticFeedbackConstants.CLOCK_TICK,
                                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                                )
                                                localHiddenTabs = localHiddenTabs.filter { it != tab }
                                                localVisibleTabs = localVisibleTabs + tab
                                            },
                                            modifier = Modifier.size(36.dp),
                                            shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                        ) {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Add,
                                                    contentDescription = stringResource(R.string.reorder_tabs_cd_add_tab),
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
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
                onClick = onReset
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
