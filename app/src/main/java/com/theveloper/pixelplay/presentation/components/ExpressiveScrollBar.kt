package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private data class ScrollMetrics(
    val progress: Float,
    val totalItemsCount: Int,
    val maxScrollIndex: Int,
    val scrollableHeight: Float
)

private data class VisibleGridLineMetrics(
    val index: Int,
    val offsetPx: Int,
    val sizePx: Int
)

private fun estimateListFallbackStridePx(
    visibleItems: List<LazyListItemInfo>,
    spacingPx: Int
): Float {
    val strideSamples = visibleItems
        .zipWithNext()
        .mapNotNull { (current, next) ->
            (next.offset - current.offset)
                .takeIf { next.index == current.index + 1 && it > 0 }
                ?.toFloat()
        }

    return medianOrNull(strideSamples)
        ?: medianOrNull(visibleItems.map { it.size.toFloat() + spacingPx })
        ?: 1f
}

private fun observeListLayoutMetrics(
    layoutInfo: LazyListLayoutInfo,
    tracker: AxisObservationTracker
) {
    tracker.resetIfNeeded(
        totalItemsCount = layoutInfo.totalItemsCount,
        spacingPx = layoutInfo.mainAxisItemSpacing
    )

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return

    tracker.observeRepresentativeSample(
        strideSamplePx = estimateListFallbackStridePx(
            visibleItems = visibleItems,
            spacingPx = layoutInfo.mainAxisItemSpacing
        ),
        itemSizeSamplePx = medianOrNull(visibleItems.map { it.size.toFloat() })
    )

    visibleItems.forEach { item ->
        tracker.observeItemSize(index = item.index, sizePx = item.size.toFloat())
    }

    visibleItems
        .zipWithNext()
        .forEach { (current, next) ->
            if (next.index == current.index + 1) {
                tracker.observeStride(
                    index = current.index,
                    stridePx = (next.offset - current.offset).toFloat()
                )
            }
        }

    val lastVisibleItem = visibleItems.last()
    if (lastVisibleItem.index < layoutInfo.totalItemsCount - 1) {
        tracker.observeStride(
            index = lastVisibleItem.index,
            stridePx = (lastVisibleItem.size + layoutInfo.mainAxisItemSpacing).toFloat()
        )
    }
}

private fun buildVisibleGridLines(layoutInfo: LazyGridLayoutInfo): List<VisibleGridLineMetrics> {
    val isVertical =
        layoutInfo.orientation == androidx.compose.foundation.gestures.Orientation.Vertical
    val groupedLines = linkedMapOf<Int, MutableList<LazyGridItemInfo>>()

    layoutInfo.visibleItemsInfo.forEach { item ->
        val lineIndex = if (isVertical) item.row else item.column
        if (lineIndex >= 0) {
            groupedLines.getOrPut(lineIndex) { mutableListOf() }.add(item)
        }
    }

    return groupedLines
        .entries
        .map { (lineIndex, itemsInLine) ->
            VisibleGridLineMetrics(
                index = lineIndex,
                offsetPx = itemsInLine.minOf { if (isVertical) it.offset.y else it.offset.x },
                sizePx = itemsInLine.maxOf { if (isVertical) it.size.height else it.size.width }
            )
        }
        .sortedBy { it.index }
}

private fun estimateGridFallbackStridePx(
    visibleLines: List<VisibleGridLineMetrics>,
    spacingPx: Int
): Float {
    val strideSamples = visibleLines
        .zipWithNext()
        .mapNotNull { (current, next) ->
            (next.offsetPx - current.offsetPx)
                .takeIf { next.index == current.index + 1 && it > 0 }
                ?.toFloat()
        }

    return medianOrNull(strideSamples)
        ?: medianOrNull(visibleLines.map { it.sizePx.toFloat() + spacingPx })
        ?: 1f
}

private fun observeGridLayoutMetrics(
    layoutInfo: LazyGridLayoutInfo,
    tracker: AxisObservationTracker
): List<VisibleGridLineMetrics> {
    tracker.resetIfNeeded(
        totalItemsCount = layoutInfo.totalItemsCount,
        spacingPx = layoutInfo.mainAxisItemSpacing
    )

    val visibleLines = buildVisibleGridLines(layoutInfo)
    if (visibleLines.isEmpty()) return visibleLines

    tracker.observeRepresentativeSample(
        strideSamplePx = estimateGridFallbackStridePx(
            visibleLines = visibleLines,
            spacingPx = layoutInfo.mainAxisItemSpacing
        ),
        itemSizeSamplePx = medianOrNull(visibleLines.map { it.sizePx.toFloat() })
    )

    visibleLines.forEach { line ->
        tracker.observeItemSize(index = line.index, sizePx = line.sizePx.toFloat())
    }

    visibleLines
        .zipWithNext()
        .forEach { (current, next) ->
            if (next.index == current.index + 1) {
                tracker.observeStride(
                    index = current.index,
                    stridePx = (next.offsetPx - current.offsetPx).toFloat()
                )
            }
        }

    val totalLines = ((layoutInfo.totalItemsCount + layoutInfo.maxSpan - 1) / layoutInfo.maxSpan)
        .coerceAtLeast(1)
    val lastVisibleLine = visibleLines.last()
    if (lastVisibleLine.index < totalLines - 1) {
        tracker.observeStride(
            index = lastVisibleLine.index,
            stridePx = (lastVisibleLine.sizePx + layoutInfo.mainAxisItemSpacing).toFloat()
        )
    }

    return visibleLines
}

@Composable
fun ExpressiveScrollBar(
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    gridState: LazyGridState? = null,
    minHeight: Dp = 48.dp,
    thickness: Dp = 8.dp,
    indicatorExpandedWidth: Dp = 24.dp,
    indicatorExpandedWidthBoost: Dp = 4.dp,
    indicatorRightCornerRadius: Dp = 6.dp,
    paddingEnd: Dp = 4.dp,
    trackGap: Dp = 8.dp
) {
    val listMetricsTracker = remember(listState) { AxisObservationTracker() }
    val gridMetricsTracker = remember(gridState) { AxisObservationTracker() }
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var pendingScrollIndex by remember { mutableIntStateOf(-1) }
    val displayedProgress = remember { Animatable(0f) }
    var hasSyncedDisplayedProgress by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.secondaryContainer
    val innerIcon = Icons.Rounded.UnfoldMore
    val expandedIndicatorWidth = (indicatorExpandedWidth + indicatorExpandedWidthBoost).coerceAtLeast(thickness)
    val indicatorRightCornerRadiusPx = with(LocalDensity.current) { indicatorRightCornerRadius.toPx() }

    val isInteracting = isPressed || isDragging
    
    val animatedWidth by animateDpAsState(
        targetValue = if (isInteracting) expandedIndicatorWidth else thickness,
        animationSpec = tween(durationMillis = 200),
        label = "WidthAnimation"
    )
    
    val iconAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "IconAlpha"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(expandedIndicatorWidth + paddingEnd)
    ) {
        val density = LocalDensity.current
        val constraintsMaxWidth = maxWidth
        val constraintsMaxHeight = maxHeight
        val coarseJumpThresholdPx = with(density) { 16.dp.toPx() }
        val smoothJumpMinDistancePx = with(density) { 10.dp.toPx() }

        val canScrollForward by remember { derivedStateOf { listState?.canScrollForward ?: gridState?.canScrollForward ?: false } }
        val canScrollBackward by remember { derivedStateOf { listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false } }
        
        if (!canScrollForward && !canScrollBackward) return@BoxWithConstraints

        fun getScrollStats(): ScrollMetrics {
            val totalItemsCount: Int
            val currentScrollPx: Float
            val totalScrollableContentPx: Float
            val approximateMaxScrollIndex: Int

            if (listState != null) {
                val layoutInfo = listState.layoutInfo
                val visibleItems = layoutInfo.visibleItemsInfo
                totalItemsCount = layoutInfo.totalItemsCount

                if (visibleItems.isEmpty()) {
                    return ScrollMetrics(
                        progress = 0f,
                        totalItemsCount = totalItemsCount,
                        maxScrollIndex = 1,
                        scrollableHeight = 1f
                    )
                }

                observeListLayoutMetrics(layoutInfo, listMetricsTracker)

                val viewportHeightPx =
                    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                        .coerceAtLeast(1f)
                val itemStridePx = listMetricsTracker.representativeStridePx(
                    fallbackStridePx = estimateListFallbackStridePx(
                        visibleItems = visibleItems,
                        spacingPx = layoutInfo.mainAxisItemSpacing
                    )
                )
                val representativeItemSizePx = listMetricsTracker.representativeItemSizePx(
                    fallbackItemSizePx = medianOrNull(visibleItems.map { it.size.toFloat() }) ?: 1f
                )
                val estimatedVisibleItems = (viewportHeightPx / itemStridePx).coerceAtLeast(1f)
                val lastItemIndex = (totalItemsCount - 1).coerceAtLeast(0)

                currentScrollPx = (
                    listMetricsTracker.distanceBeforeIndex(
                        index = listState.firstVisibleItemIndex,
                        representativeStridePx = itemStridePx
                    ) + listState.firstVisibleItemScrollOffset
                    ).coerceAtLeast(0f)
                totalScrollableContentPx = (
                    layoutInfo.beforeContentPadding +
                        layoutInfo.afterContentPadding
                    ).toFloat()
                    .plus(
                        listMetricsTracker.distanceBeforeIndex(
                            index = lastItemIndex,
                            representativeStridePx = itemStridePx
                        ) + listMetricsTracker.itemSizePx(
                            index = lastItemIndex,
                            representativeItemSizePx = representativeItemSizePx
                        ) - viewportHeightPx
                    )
                    .coerceAtLeast(1f)
                approximateMaxScrollIndex =
                    (totalItemsCount - estimatedVisibleItems).toInt().coerceAtLeast(1)
            } else if (gridState != null) {
                val layoutInfo = gridState.layoutInfo
                totalItemsCount = layoutInfo.totalItemsCount

                val visibleLines = observeGridLayoutMetrics(layoutInfo, gridMetricsTracker)
                val viewportHeightPx =
                    (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
                        .coerceAtLeast(1f)
                val itemsPerRow = layoutInfo.maxSpan.coerceAtLeast(1)
                val totalRows = ((totalItemsCount + itemsPerRow - 1) / itemsPerRow).coerceAtLeast(1)
                val rowStridePx = gridMetricsTracker.representativeStridePx(
                    fallbackStridePx = estimateGridFallbackStridePx(
                        visibleLines = visibleLines,
                        spacingPx = layoutInfo.mainAxisItemSpacing
                    )
                )
                val representativeRowSizePx = gridMetricsTracker.representativeItemSizePx(
                    fallbackItemSizePx = medianOrNull(visibleLines.map { it.sizePx.toFloat() }) ?: 1f
                )
                val estimatedVisibleRows = (viewportHeightPx / rowStridePx).coerceAtLeast(1f)
                val currentRow = visibleLines.firstOrNull()?.index ?: 0
                val lastRowIndex = (totalRows - 1).coerceAtLeast(0)

                currentScrollPx = (
                    gridMetricsTracker.distanceBeforeIndex(
                        index = currentRow,
                        representativeStridePx = rowStridePx
                    ) + gridState.firstVisibleItemScrollOffset
                    ).coerceAtLeast(0f)
                totalScrollableContentPx = (
                    layoutInfo.beforeContentPadding +
                        layoutInfo.afterContentPadding
                    ).toFloat()
                    .plus(
                        gridMetricsTracker.distanceBeforeIndex(
                            index = lastRowIndex,
                            representativeStridePx = rowStridePx
                        ) + gridMetricsTracker.itemSizePx(
                            index = lastRowIndex,
                            representativeItemSizePx = representativeRowSizePx
                        ) - viewportHeightPx
                    )
                    .coerceAtLeast(1f)
                approximateMaxScrollIndex =
                    (((totalRows - estimatedVisibleRows).toInt().coerceAtLeast(1)) * itemsPerRow)
                        .coerceAtMost((totalItemsCount - 1).coerceAtLeast(1))
            } else {
                return ScrollMetrics(
                    progress = 0f,
                    totalItemsCount = 0,
                    maxScrollIndex = 1,
                    scrollableHeight = 1f
                )
            }

            if (totalItemsCount == 0) {
                return ScrollMetrics(
                    progress = 0f,
                    totalItemsCount = 0,
                    maxScrollIndex = 1,
                    scrollableHeight = 1f
                )
            }

            val forward = listState?.canScrollForward ?: gridState?.canScrollForward ?: false
            val backward = listState?.canScrollBackward ?: gridState?.canScrollBackward ?: false

            val boundedScrollPx = currentScrollPx.coerceIn(0f, totalScrollableContentPx)
            val realProgress = if (!forward && totalItemsCount > 0) {
                1f
            } else if (!backward) {
                0f
            } else {
                (boundedScrollPx / totalScrollableContentPx).coerceIn(0f, 0.999f)
            }

            val availableHeight = with(density) { constraintsMaxHeight.toPx() }
            val handleHeightPx = with(density) { minHeight.toPx() }
            val scrollableHeight = (availableHeight - handleHeightPx).coerceAtLeast(1f)

            return ScrollMetrics(
                progress = realProgress,
                totalItemsCount = totalItemsCount,
                maxScrollIndex = approximateMaxScrollIndex,
                scrollableHeight = scrollableHeight
            )
        }

        fun updateProgressFromTouch(touchY: Float, grabOffset: Float) {
            val stats = getScrollStats()
            val scrollableHeight = stats.scrollableHeight

            val targetHandleTop = touchY - grabOffset
            val newProgress = (targetHandleTop / scrollableHeight).coerceIn(0f, 1f)

            dragProgress = newProgress
            pendingScrollIndex = ((newProgress * stats.maxScrollIndex).toInt())
                .coerceIn(0, (stats.totalItemsCount - 1).coerceAtLeast(0))
        }

        LaunchedEffect(Unit) {
            snapshotFlow { pendingScrollIndex }
                .distinctUntilChanged()
                .collectLatest { index ->
                    if (index >= 0) {
                        listState?.scrollToItem(index)
                        gridState?.scrollToItem(index)
                    }
                }
        }

        LaunchedEffect(listState, gridState, constraintsMaxHeight, minHeight, isDragging) {
            if (isDragging) return@LaunchedEffect

            snapshotFlow { getScrollStats() }
                .distinctUntilChanged()
                .collectLatest { stats ->
                    val targetProgress = stats.progress
                    if (!hasSyncedDisplayedProgress) {
                        displayedProgress.snapTo(targetProgress)
                        hasSyncedDisplayedProgress = true
                    } else {
                        val handleDeltaPx =
                            abs(targetProgress - displayedProgress.value) * stats.scrollableHeight
                        val estimatedStepPx =
                            stats.scrollableHeight / stats.maxScrollIndex.coerceAtLeast(1).toFloat()
                        val shouldSmoothJump =
                            estimatedStepPx >= coarseJumpThresholdPx &&
                                handleDeltaPx >= smoothJumpMinDistancePx

                        if (shouldSmoothJump) {
                            displayedProgress.animateTo(
                                targetValue = targetProgress,
                                animationSpec = tween(
                                    durationMillis = 70,
                                    easing = FastOutSlowInEasing
                                )
                            )
                        } else {
                            displayedProgress.snapTo(targetProgress)
                        }
                    }
                }
        }

        LaunchedEffect(isDragging, dragProgress) {
            if (isDragging && dragProgress >= 0f) {
                displayedProgress.snapTo(dragProgress)
                hasSyncedDisplayedProgress = true
            }
        }

        val indicatorPath = remember { Path() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                awaitRelease()
                            } finally {
                                isPressed = false
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var grabOffset = 0f

                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true

                            val stats = getScrollStats()
                            val scrollableHeight = stats.scrollableHeight
                            val handleHeightPx = with(density) { minHeight.toPx() }

                            val visualProgress = displayedProgress.value
                            val handleY = visualProgress * scrollableHeight

                            val isTouchOnHandle = offset.y >= handleY && offset.y <= (handleY + handleHeightPx)

                            if (isTouchOnHandle) {
                                grabOffset = offset.y - handleY
                                dragProgress = visualProgress
                            } else {
                                grabOffset = handleHeightPx / 2f
                                updateProgressFromTouch(offset.y, grabOffset)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                        onDragCancel = {
                            isDragging = false
                            dragProgress = -1f
                            pendingScrollIndex = -1
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            updateProgressFromTouch(change.position.y, grabOffset)
                        }
                    )
                }
        ) {
            val rightAnchorX = with(density) { (constraintsMaxWidth - paddingEnd).toPx() }
            val trackX = rightAnchorX - with(density) { thickness.toPx() / 2 }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val stats = getScrollStats()
                val scrollableHeight = stats.scrollableHeight

                val visualProgress = displayedProgress.value
                val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else visualProgress
                val handleY = displayProgress * scrollableHeight
                val handleHeightPx = minHeight.toPx()

                val trackStrokeWidth = thickness.toPx()
                val indicatorWidthPx = animatedWidth.toPx()
                val gapPx = trackGap.toPx()
                val indicatorLeftCornerRadius = indicatorWidthPx / 2f
                val maxAllowedRightCornerRadius = minOf(indicatorWidthPx / 2f, handleHeightPx / 2f)
                val resolvedRightCornerRadius = indicatorRightCornerRadiusPx
                    .coerceIn(0f, maxAllowedRightCornerRadius)

                val currentIndicatorX = rightAnchorX - indicatorWidthPx

                if (handleY > gapPx) {
                    drawLine(
                        color = surfaceVariantColor,
                        start = Offset(trackX, 0f),
                        end = Offset(trackX, handleY - gapPx),
                        strokeWidth = trackStrokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                if (handleY + handleHeightPx + gapPx < size.height) {
                    drawLine(
                        color = surfaceVariantColor,
                        start = Offset(trackX, handleY + handleHeightPx + gapPx),
                        end = Offset(trackX, size.height),
                        strokeWidth = trackStrokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                indicatorPath.reset()
                indicatorPath.addRoundRect(
                    RoundRect(
                        rect = Rect(
                            offset = Offset(currentIndicatorX, handleY),
                            size = Size(indicatorWidthPx, handleHeightPx)
                        ),
                        topLeft = CornerRadius(indicatorLeftCornerRadius, indicatorLeftCornerRadius),
                        topRight = CornerRadius(resolvedRightCornerRadius, resolvedRightCornerRadius),
                        bottomRight = CornerRadius(resolvedRightCornerRadius, resolvedRightCornerRadius),
                        bottomLeft = CornerRadius(indicatorLeftCornerRadius, indicatorLeftCornerRadius)
                    )
                )
                drawPath(
                    path = indicatorPath,
                    color = primaryColor
                )
            }
            
            if (iconAlpha > 0f) {
               Box(
                   modifier = Modifier
                       .offset {
                           val stats = getScrollStats()
                           val scrollableHeight = stats.scrollableHeight
                           val visualProgress = displayedProgress.value
                           val displayProgress = if (isDragging && dragProgress >= 0f) dragProgress else visualProgress
                           val handleY = displayProgress * scrollableHeight
                           val handleHeightPx = with(density) { minHeight.toPx() }
                           
                           val iconSizePx = with(density) { 24.dp.toPx() }
                           val paddingEndPx = with(density) { paddingEnd.toPx() }
                           val animatedWidthPx = with(density) { animatedWidth.toPx() }
                           val maxWidthPx = with(density) { constraintsMaxWidth.toPx() }
                           
                           val x = maxWidthPx - paddingEndPx - (animatedWidthPx / 2) - (iconSizePx / 2)
                           val y = handleY + (handleHeightPx / 2) - (iconSizePx / 2)
                           
                           androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                       }
                       .size(24.dp)
                       .graphicsLayer { 
                           alpha = iconAlpha 
                           scaleX = iconAlpha
                           scaleY = iconAlpha
                       }
               ) {
                   Icon(
                       imageVector = innerIcon,
                       contentDescription = null,
                       tint = MaterialTheme.colorScheme.onPrimary,
                       modifier = Modifier.fillMaxSize()
                   )
               }
            }
        }
    }
}
