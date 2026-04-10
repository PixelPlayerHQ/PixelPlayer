package com.theveloper.pixelplay.presentation.components

import androidx.annotation.FloatRange
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.*
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastMapIndexed
import kotlin.math.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateMeasurement
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import kotlinx.coroutines.flow.distinctUntilChanged

/* ================================================================================================
   PUBLIC API
   ================================================================================================ */

/** Estado del carrusel (idéntico al de M3, pero standalone) */
@ExperimentalMaterial3Api
class CarouselState(
    currentItem: Int = 0,
    @FloatRange(from = -0.5, to = 0.5) currentItemOffsetFraction: Float = 0f,
    itemCount: () -> Int,
) : ScrollableState {
    internal var pagerState: CarouselPagerState =
        CarouselPagerState(currentItem, currentItemOffsetFraction, itemCount)

    override val isScrollInProgress: Boolean get() = pagerState.isScrollInProgress
    val currentItem: Int get() = pagerState.currentPage

    override fun dispatchRawDelta(delta: Float): Float = pagerState.dispatchRawDelta(delta)

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ) { pagerState.scroll(scrollPriority, block) }

    suspend fun scrollToItem(item: Int) = pagerState.scrollToPage(item, 0f)

    @Suppress("UNUSED_PARAMETER")
    suspend fun animateScrollToItem(item: Int, animationSpec: AnimationSpec<Float> = spring()) {
        if ((item == pagerState.currentPage && pagerState.currentPageOffsetFraction == 0f) || pagerState.pageCount == 0) return
        val targetPage = if (pagerState.pageCount > 0) item.coerceIn(0, pagerState.pageCount - 1) else 0
        pagerState.animateScrollToPage(targetPage)
    }

    companion object {
        val Saver: Saver<CarouselState, *> =
            listSaver(
                save = {
                    listOf(
                        it.pagerState.currentPage,
                        it.pagerState.currentPageOffsetFraction,
                        it.pagerState.pageCount
                    )
                },
                restore = {
                    CarouselState(
                        currentItem = it[0] as Int,
                        currentItemOffsetFraction = it[1] as Float,
                        itemCount = { it[2] as Int }
                    )
                }
            )
    }
}

@ExperimentalMaterial3Api
@Composable
fun rememberCarouselState(initialItem: Int = 0, itemCount: () -> Int): CarouselState {
    return rememberSaveable(saver = CarouselState.Saver) {
        CarouselState(
            currentItem = initialItem,
            currentItemOffsetFraction = 0f,
            itemCount = itemCount
        )
    }.apply { pagerState.pageCountState.value = itemCount }
}

/** API pública del carrusel tipo Multi-Browse con clip redondeado real. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoundedHorizontalMultiBrowseCarousel(
    state: CarouselState,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior = PagerDefaults.flingBehavior(
        state = state.pagerState,
        pagerSnapDistance = PagerSnapDistance.atMost(1)
    ),
    userScrollEnabled: Boolean = true,
    itemCornerRadius: Dp = 16.dp,
    suppressNoPeekSettleCorrection: Boolean = false,
    carouselStyle: String,
    carouselWidth: Dp,
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
) {
    val density = LocalDensity.current
    val carouselWidthPx = with(density) { carouselWidth.toPx() }

    val maxNonFocalItems = when (carouselStyle) {
        CarouselStyle.NO_PEEK -> 0
        CarouselStyle.ONE_PEEK -> 1
        CarouselStyle.TWO_PEEK -> 2
        else -> 1 // Default to one peek
    }

    if (carouselStyle == CarouselStyle.NO_PEEK && !suppressNoPeekSettleCorrection) {
        val settleEpsilon = 0.001f
        LaunchedEffect(state.pagerState) {
            snapshotFlow {
                state.pagerState.isScrollInProgress to state.pagerState.currentPageOffsetFraction
            }
                .distinctUntilChanged()
                .collect { (inProgress, offset) ->
                    if (inProgress || abs(offset) <= settleEpsilon) return@collect

                    val pageCount = state.pagerState.pageCount
                    if (pageCount == 0) return@collect

                    // NO_PEEK must never stay visually between two album arts.
                    val targetPage = state.pagerState.currentPage.coerceIn(0, pageCount - 1)
                    state.scrollToItem(targetPage)
                }
        }
    }

    RoundedCarousel(
        state = state,
        orientation = Orientation.Horizontal,
        keylineList = { _, spacingPx ->
            val itemCount = state.pagerState.pageCountState.value.invoke()
            when (carouselStyle) {
                CarouselStyle.NO_PEEK -> multiBrowseKeylineList(
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    largeCounts = intArrayOf(1),
                    mediumCounts = intArrayOf(0),
                    smallCounts = intArrayOf(0)
                )
                CarouselStyle.ONE_PEEK -> multiBrowseKeylineList(
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx * 0.8f,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    alignment = CarouselAlignment.Start,
                    largeCounts = intArrayOf(1),
                    mediumCounts = intArrayOf(0),
                    smallCounts = intArrayOf(1)
                )
                CarouselStyle.TWO_PEEK -> {
                    // Manual keyline definition for [small, large, small]
                    val largeSize = carouselWidthPx * 0.6f // Main item is 60% of width
                    val smallSize = carouselWidthPx * 0.45f // Peek items are 45% of width
                    keylineListOf(
                        carouselMainAxisSize = carouselWidthPx,
                        itemSpacing = spacingPx,
                        carouselAlignment = CarouselAlignment.Center
                    ) {
                        add(smallSize) // Previous peek
                        add(largeSize) // Focused item
                        add(smallSize) // Next peek
                    }
                }
                else -> multiBrowseKeylineList( // Default to one peek
                    density = density,
                    carouselMainAxisSize = carouselWidthPx,
                    preferredItemSize = carouselWidthPx * 0.8f,
                    itemSpacing = spacingPx,
                    itemCount = itemCount,
                    alignment = CarouselAlignment.Start
                )
            }
        },
        contentPadding = PaddingValues(0.dp),
        maxNonFocalVisibleItemCount = maxNonFocalItems,
        modifier = modifier,
        itemSpacing = itemSpacing,
        flingBehavior = flingBehavior,
        userScrollEnabled = true, // Always allow user scrolling
        itemCornerRadius = itemCornerRadius,
        carouselStyle = carouselStyle, // Pass style down
        content = content
    )
}

/* ================================================================================================
   CORE (Carousel + PageSize + ItemScope con clip redondeado)
   ================================================================================================ */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoundedCarousel(
    state: CarouselState,
    orientation: Orientation,
    keylineList: (availableSpace: Float, itemSpacing: Float) -> KeylineList,
    contentPadding: PaddingValues,
    maxNonFocalVisibleItemCount: Int,
    modifier: Modifier = Modifier,
    itemSpacing: Dp = 0.dp,
    flingBehavior: TargetedFlingBehavior,
    userScrollEnabled: Boolean,
    itemCornerRadius: Dp,
    carouselStyle: String,
    content: @Composable CarouselItemScope.(itemIndex: Int) -> Unit,
) {
    val beforeContentPadding = contentPadding.calculateBeforeContentPadding(orientation)
    val afterContentPadding = contentPadding.calculateAfterContentPadding(orientation)
    val pageSize = remember(keylineList) {
        CarouselPageSize(keylineList, beforeContentPadding, afterContentPadding)
    }
    val snapPosition = KeylineSnapPosition(pageSize)

    HorizontalPager(
        state = state.pagerState,
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        pageSize = pageSize,
        pageSpacing = itemSpacing,
        beyondViewportPageCount = maxNonFocalVisibleItemCount,
        snapPosition = snapPosition,
        flingBehavior = flingBehavior,
        userScrollEnabled = userScrollEnabled,
        modifier = modifier.semantics { role = Role.Carousel }
    ) { page ->
        val carouselItemInfo = remember { CarouselItemDrawInfoImpl() }
        val scope = remember { CarouselItemScopeImpl(itemInfo = carouselItemInfo) }

        val cachedShape = remember(itemCornerRadius) {
            RoundedCornerShape(itemCornerRadius)
        }

        val clipShape = remember(cachedShape) {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density
                ): Outline {
                    // 1) Limitar la máscara al tamaño del layer (item)
                    val layerBounds = Rect(0f, 0f, size.width, size.height)
                    // intersecta con bounds y da un respiro sub-px para que no se vea “cortado”
                    val rect = carouselItemInfo.maskRect.intersect(layerBounds).inflate(0.5f)

                    // 2) Creamos un outline redondeado del tamaño del rect ya intersectado
                    val localSize = Size(rect.width, rect.height)
                    val baseOutline = cachedShape.createOutline(localSize, layoutDirection, density)

                    // 3) Lo pasamos a Path y lo trasladamos a (left,top) del maskRect
                    val path = Path().apply {
                        addOutline(baseOutline)
                        translate(Offset(rect.left, rect.top))
                    }
                    return Outline.Generic(path)
                }
            }
        }

//        val clipShape = remember {
//            object : Shape {
//                override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
//                    return Outline.Rectangle(carouselItemInfo.maskRect) // <-- RECTÁNGULO: causa el corte plano
//                }
//            }
//        }

        //val clipShape = rememberRoundedClipShape(carouselItemInfo, itemCornerRadius)

        val animatedAlpha by animateFloatAsState(
            targetValue = if (carouselStyle == CarouselStyle.ONE_PEEK && page > state.pagerState.currentPage + 1) 0f else 1f,
            animationSpec = tween(durationMillis = 200),
            label = "CarouselItemAlpha"
        )

        Box(
            modifier = Modifier
                .graphicsLayer { alpha = animatedAlpha }
                .carouselItem(
                    index = page,
                    state = state,
                    strategy = { pageSize.strategy },
                    carouselItemDrawInfo = carouselItemInfo,
                    clipShape = clipShape
                )
        ) {
            scope.content(page)
        }
    }
}

private class CarouselPageSize(
    private val keylineList: (availableSpace: Float, itemSpacing: Float) -> KeylineList,
    private val beforeContentPadding: Float,
    private val afterContentPadding: Float,
) : PageSize {
    private var strategyState by mutableStateOf(Strategy.Empty)
    val strategy: Strategy get() = strategyState

    override fun Density.calculateMainAxisPageSize(availableSpace: Int, pageSpacing: Int): Int {
        val keylines = keylineList(availableSpace.toFloat(), pageSpacing.toFloat())
        strategyState = Strategy(
            defaultKeylines = keylines,
            availableSpace = availableSpace.toFloat(),
            itemSpacing = pageSpacing.toFloat(),
            beforeContentPadding = beforeContentPadding,
            afterContentPadding = afterContentPadding,
        )
        return if (strategy.isValid) strategy.itemMainAxisSize.roundToInt() else availableSpace
    }
}

/* ItemScope con máscara que sigue el maskRect + radius */
@ExperimentalMaterial3Api
sealed interface CarouselItemScope {
    val carouselItemDrawInfo: CarouselItemDrawInfo
    @Composable fun Modifier.maskClip(shape: Shape): Modifier
    @Composable fun Modifier.maskBorder(border: BorderStroke, shape: Shape): Modifier
    @Composable fun rememberMaskShape(shape: Shape): androidx.compose.foundation.shape.GenericShape
}

@ExperimentalMaterial3Api
private class CarouselItemScopeImpl(private val itemInfo: CarouselItemDrawInfo) : CarouselItemScope {
    override val carouselItemDrawInfo: CarouselItemDrawInfo get() = itemInfo

    @Composable
    override fun Modifier.maskClip(shape: Shape): Modifier =
        clip(rememberMaskShape(shape))

    @Composable
    override fun Modifier.maskBorder(border: BorderStroke, shape: Shape): Modifier =
        border(border, rememberMaskShape(shape))

    @Composable
    override fun rememberMaskShape(shape: Shape): androidx.compose.foundation.shape.GenericShape {
        val density = LocalDensity.current
        return remember(carouselItemDrawInfo, density) {
            androidx.compose.foundation.shape.GenericShape { size, direction ->
                val rect = carouselItemDrawInfo.maskRect.intersect(Rect(0f, 0f, size.width, size.height))
                addOutline(shape.createOutline(rect.size, direction, density))
                translate(Offset(rect.left, rect.top))
            }
        }
    }
}

/* Clip redondeado que se actualiza por frame según maskRect (sin hairlines) */
@Composable
private fun rememberRoundedClipShape(
    itemInfo: CarouselItemDrawInfoImpl,
    itemCornerRadius: Dp
): Shape {
    val density = LocalDensity.current
    return remember(itemInfo, itemCornerRadius, density) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val eps = 0.75f
                val r = with(density) { itemCornerRadius.toPx() } + eps
                val rect = itemInfo.maskRect
                val rr = androidx.compose.ui.geometry.RoundRect(
                    rect.left - eps, rect.top - eps, rect.right + eps, rect.bottom + eps,
                    CornerRadius(r, r)
                )
                return Outline.Rounded(rr)
            }
        }
    }
}

/* ================================================================================================
   DRAW INFO & MODIFIER DE ITEM (clip y translate con strategy)
   ================================================================================================ */

@ExperimentalMaterial3Api
sealed interface CarouselItemDrawInfo {
    val size: Float
    val minSize: Float
    val maxSize: Float
    val maskRect: Rect
}

@OptIn(ExperimentalMaterial3Api::class)
private class CarouselItemDrawInfoImpl : CarouselItemDrawInfo {
    var sizeState by mutableFloatStateOf(0f)
    var minSizeState by mutableFloatStateOf(0f)
    var maxSizeState by mutableFloatStateOf(0f)
    var maskRectState by mutableStateOf(Rect.Zero)
    override val size: Float get() = sizeState
    override val minSize: Float get() = minSizeState
    override val maxSize: Float get() = maxSizeState
    override val maskRect: Rect get() = maskRectState
}

@OptIn(ExperimentalMaterial3Api::class)
private fun Modifier.carouselItem(
    index: Int,
    state: CarouselState,
    strategy: () -> Strategy,
    carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    clipShape: Shape,
): Modifier = this then CarouselItemModifierNodeElement(
    index = index,
    state = state,
    strategy = strategy,
    carouselItemDrawInfo = carouselItemDrawInfo,
    clipShape = clipShape
)

@OptIn(ExperimentalMaterial3Api::class)
private data class CarouselItemModifierNodeElement(
    val index: Int,
    val state: CarouselState,
    val strategy: () -> Strategy,
    val carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    val clipShape: Shape,
) : ModifierNodeElement<CarouselItemModifierNode>() {
    override fun create(): CarouselItemModifierNode {
        return CarouselItemModifierNode(
            index = index,
            state = state,
            strategy = strategy,
            carouselItemDrawInfo = carouselItemDrawInfo,
            clipShape = clipShape
        )
    }

    override fun update(node: CarouselItemModifierNode) {
        node.index = index
        node.state = state
        node.strategy = strategy
        node.carouselItemDrawInfo = carouselItemDrawInfo
        node.clipShape = clipShape
        node.invalidateMeasurement()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class CarouselItemModifierNode(
    var index: Int,
    var state: CarouselState,
    var strategy: () -> Strategy,
    var carouselItemDrawInfo: CarouselItemDrawInfoImpl,
    var clipShape: Shape,
) : Modifier.Node(), LayoutModifierNode {
    override fun MeasureScope.measure(measurable: Measurable, constraints: Constraints): MeasureResult {
        val strategyResult = strategy()
        if (!strategyResult.isValid) return layout(0, 0) {}

        val isVertical = state.pagerState.layoutInfo.orientation == Orientation.Vertical
        val isRtl = layoutDirection == LayoutDirection.Rtl

        val mainAxisSize = strategyResult.itemMainAxisSize
        val itemConstraints =
            if (isVertical) {
                constraints.copy(
                    minHeight = mainAxisSize.roundToInt(),
                    maxHeight = mainAxisSize.roundToInt()
                )
            } else {
                constraints.copy(
                    minWidth = mainAxisSize.roundToInt(),
                    maxWidth = mainAxisSize.roundToInt()
                )
            }

        val placeable = measurable.measure(itemConstraints)
        val itemZIndex =
            if (index == state.pagerState.currentPage) 1f
            else if (index == 0) 0f
            else 1f / index.toFloat()

        return layout(placeable.width, placeable.height) {
            placeable.placeWithLayer(0, 0, zIndex = itemZIndex) {
                // --- keylines e interpolación
                val scrollOffset = calculateCurrentScrollOffset(state, strategyResult)
                val maxScrollOffset = calculateMaxScrollOffset(state, strategyResult)
                val keylines =
                    strategyResult.getKeylineListForScrollOffset(scrollOffset, maxScrollOffset)
                val roundedKeylines =
                    strategyResult.getKeylineListForScrollOffset(
                        scrollOffset, maxScrollOffset, roundToNearestStep = true
                    )

                val itemSizeWithSpacing =
                    strategyResult.itemMainAxisSize + strategyResult.itemSpacing
                val unadjustedCenter =
                    (index * itemSizeWithSpacing) +
                            (strategyResult.itemMainAxisSize / 2f) - scrollOffset

                val before = keylines.getKeylineBefore(unadjustedCenter)
                val after = keylines.getKeylineAfter(unadjustedCenter)
                val progress = getProgress(before, after, unadjustedCenter)
                val ik = lerp(before, after, progress) // interpolated keyline
                val isOutOfKeylineBounds = before == after

                // --- centro local (coords del layer) y dimensiones de máscara
                val centerLocalX =
                    if (isVertical) size.width / 2f else strategyResult.itemMainAxisSize / 2f
                val centerLocalY =
                    if (isVertical) strategyResult.itemMainAxisSize / 2f else size.height / 2f
                val halfMaskW = if (isVertical) size.width / 2f else ik.size / 2f
                val halfMaskH = if (isVertical) ik.size / 2f else size.height / 2f

                // --- rect base (local)
                var left = centerLocalX - halfMaskW
                var right = centerLocalX + halfMaskW
                var top = centerLocalY - halfMaskH
                var bottom = centerLocalY + halfMaskH

                // --- recorte por desbordes contra el viewport del carrusel
                //     (evita que el contenedor "aplaste" el arco del peek)
                val containerSize = strategyResult.availableSpace
                if (!isVertical) {
                    val centerInContainer = ik.offset
                    val overflowLeft = kotlin.math.max(0f, 0f - (centerInContainer - halfMaskW))
                    val overflowRight =
                        kotlin.math.max(0f, (centerInContainer + halfMaskW) - containerSize)
                    left += overflowLeft
                    right -= overflowRight
                } else {
                    val centerInContainer = ik.offset
                    val overflowTop = kotlin.math.max(0f, 0f - (centerInContainer - halfMaskH))
                    val overflowBottom =
                        kotlin.math.max(0f, (centerInContainer + halfMaskH) - containerSize)
                    top += overflowTop
                    bottom -= overflowBottom
                }

                // --- limitar además al propio layer (seguro)
                val layerBounds = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                val maskRect = Rect(left, top, right, bottom).intersect(layerBounds)

                // --- actualizar info para la máscara (para MaskScope, etc.)
                carouselItemDrawInfo.sizeState = ik.size
                carouselItemDrawInfo.minSizeState = roundedKeylines.minBy { it.size }.size
                carouselItemDrawInfo.maxSizeState = roundedKeylines.firstFocal.size
                carouselItemDrawInfo.maskRectState = maskRect

                // --- CLIP: siempre activado con la forma redondeada
                clip = true
                shape = clipShape

                // --- traslación final (pegado de bordes)
                var translation = ik.offset - unadjustedCenter
                if (isOutOfKeylineBounds) {
                    val outOfBoundsOffset =
                        (unadjustedCenter - ik.unadjustedOffset) / ik.size
                    translation += outOfBoundsOffset
                }
                if (isVertical) translationY = translation
                else translationX = if (isRtl) -translation else translation
            }
        }
    }
}


/* ================================================================================================
   STRATEGY (keylines shifting + snapping) — versión compacta y compatible
   ================================================================================================ */

private class Strategy(
    val defaultKeylines: KeylineList,
    val startKeylineSteps: List<KeylineList>,
    val endKeylineSteps: List<KeylineList>,
    val availableSpace: Float,
    val itemSpacing: Float,
    val beforeContentPadding: Float,
    val afterContentPadding: Float,
) {
    constructor(
        defaultKeylines: KeylineList,
        availableSpace: Float,
        itemSpacing: Float,
        beforeContentPadding: Float,
        afterContentPadding: Float,
    ) : this(
        defaultKeylines = defaultKeylines,
        startKeylineSteps = getStartKeylineSteps(defaultKeylines, availableSpace, itemSpacing, beforeContentPadding),
        endKeylineSteps = getEndKeylineSteps(defaultKeylines, availableSpace, itemSpacing, afterContentPadding),
        availableSpace = availableSpace,
        itemSpacing = itemSpacing,
        beforeContentPadding = beforeContentPadding,
        afterContentPadding = afterContentPadding
    )

    val itemMainAxisSize: Float get() = defaultKeylines.firstFocal.size
    val isValid: Boolean =
        defaultKeylines.isNotEmpty() && availableSpace != 0f && itemMainAxisSize != 0f

    private val startShiftDistance = getStartShiftDistance(startKeylineSteps, beforeContentPadding)
    private val endShiftDistance = getEndShiftDistance(endKeylineSteps, afterContentPadding)
    private val startShiftPoints = getStepInterpolationPoints(startShiftDistance, startKeylineSteps, true)
    private val endShiftPoints = getStepInterpolationPoints(endShiftDistance, endKeylineSteps, false)
    private var lastStartAndEndKeylineListSteps: List<KeylineList>? = null

    fun getKeylineListForScrollOffset(
        scrollOffset: Float,
        maxScrollOffset: Float,
        roundToNearestStep: Boolean = false,
    ): KeylineList {
        val positiveScrollOffset = max(0f, scrollOffset)
        val startShiftOffset = startShiftDistance
        val endShiftOffset = max(0f, maxScrollOffset - endShiftDistance)

        if (positiveScrollOffset in startShiftOffset..endShiftOffset) return defaultKeylines

        var interpolation = lerp(1f, 0f, 0f, startShiftOffset, positiveScrollOffset)
        var shiftPoints = startShiftPoints
        var steps = startKeylineSteps

        if (positiveScrollOffset > endShiftOffset) {
            interpolation = lerp(0f, 1f, endShiftOffset, maxScrollOffset, positiveScrollOffset)
            shiftPoints = endShiftPoints
            steps = endKeylineSteps
            if (endShiftOffset < 0.01f && startKeylineSteps.size == 2 && endKeylineSteps.size == 2) {
                if (lastStartAndEndKeylineListSteps == null) {
                    lastStartAndEndKeylineListSteps = listOf(startKeylineSteps.last(), endKeylineSteps.last())
                }
                steps = lastStartAndEndKeylineListSteps!!
            }
        }

        val range = getShiftPointRange(steps.size, shiftPoints, interpolation)
        if (roundToNearestStep) {
            val rounded = if (range.steppedInterpolation.roundToInt() == 0) range.fromStepIndex else range.toStepIndex
            return steps[rounded]
        }
        return lerp(steps[range.fromStepIndex], steps[range.toStepIndex], range.steppedInterpolation)
    }

    companion object {
        val Empty = Strategy(
            defaultKeylines = KeylineList.Empty,
            startKeylineSteps = emptyList(),
            endKeylineSteps = emptyList(),
            availableSpace = 0f,
            itemSpacing = 0f,
            beforeContentPadding = 0f,
            afterContentPadding = 0f
        )
    }
}

/* ================================================================================================
   KEYLINES + helpers
   ================================================================================================ */

private data class Keyline(
    val size: Float,
    val offset: Float,
    val unadjustedOffset: Float,
    val isFocal: Boolean,
    val isAnchor: Boolean,
    val isPivot: Boolean,
    val cutoff: Float,
)

private class KeylineList internal constructor(keylines: List<Keyline>) : List<Keyline> by keylines {
    val pivotIndex: Int = indexOfFirst { it.isPivot }
    val pivot: Keyline get() = get(pivotIndex)

    val firstNonAnchorIndex: Int = indexOfFirst { !it.isAnchor }
    val firstNonAnchor: Keyline get() = get(firstNonAnchorIndex)

    val lastNonAnchorIndex: Int = indexOfLast { !it.isAnchor }
    val lastNonAnchor: Keyline get() = get(lastNonAnchorIndex)

    val firstFocalIndex: Int = indexOfFirst { it.isFocal }
    val firstFocal: Keyline get() = get(firstFocalIndex)
    val lastFocalIndex: Int = indexOfLast { it.isFocal }
    val lastFocal: Keyline get() = get(lastFocalIndex)
    val focalCount: Int = lastFocalIndex - firstFocalIndex + 1

    fun isFirstFocalItemAtStartOfContainer(): Boolean {
        val firstFocalLeft = firstFocal.offset - (firstFocal.size / 2)
        return firstFocalLeft >= 0 && firstFocal == firstNonAnchor
    }

    fun isLastFocalItemAtEndOfContainer(carouselMainAxisSize: Float): Boolean {
        val lastFocalRight = lastFocal.offset + (lastFocal.size / 2)
        return lastFocalRight <= carouselMainAxisSize && lastFocal == lastNonAnchor
    }

    fun firstIndexAfterFocalRangeWithSize(size: Float): Int {
        val from = lastFocalIndex
        val to = lastIndex
        return (from..to).firstOrNull { i -> this[i].size == size } ?: lastIndex
    }

    fun lastIndexBeforeFocalRangeWithSize(size: Float): Int {
        val from = firstFocalIndex - 1
        val to = 0
        return (from downTo to).firstOrNull { i -> this[i].size == size } ?: to
    }

    fun getKeylineBefore(unadjustedOffset: Float): Keyline {
        for (i in indices.reversed()) {
            val k = get(i)
            if (k.unadjustedOffset < unadjustedOffset) return k
        }
        return first()
    }

    fun getKeylineAfter(unadjustedOffset: Float): Keyline {
        return firstOrNull { it.unadjustedOffset >= unadjustedOffset } ?: last()
    }

    companion object { val Empty = KeylineList(emptyList()) }
}

private fun emptyKeylineList() = KeylineList.Empty

@JvmInline
private value class CarouselAlignment private constructor(val value: Int) {
    companion object { val Start = CarouselAlignment(-1); val Center = CarouselAlignment(0); val End = CarouselAlignment(1) }
}

// Overload A: alineación (Start/Center/End)
private fun keylineListOf(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    carouselAlignment: CarouselAlignment,
    keylines: KeylineListScope.() -> Unit,
): KeylineList {
    val scope = KeylineListScopeImpl()
    keylines(scope)
    return scope.createWithAlignment(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        carouselAlignment = carouselAlignment
    )
}

// Overload B: pivote explícito (índice y offset)
private fun keylineListOf(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    pivotIndex: Int,
    pivotOffset: Float,
    keylines: KeylineListScope.() -> Unit,
): KeylineList {
    val scope = KeylineListScopeImpl()
    keylines(scope)
    return scope.createWithPivot(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        pivotIndex = pivotIndex,
        pivotOffset = pivotOffset
    )
}

/* Interpolaciones */
private fun lerp(start: Keyline, end: Keyline, fraction: Float) = Keyline(
    size = androidx.compose.ui.util.lerp(start.size, end.size, fraction),
    offset = androidx.compose.ui.util.lerp(start.offset, end.offset, fraction),
    unadjustedOffset = androidx.compose.ui.util.lerp(start.unadjustedOffset, end.unadjustedOffset, fraction),
    isFocal = if (fraction < .5f) start.isFocal else end.isFocal,
    isAnchor = if (fraction < .5f) start.isAnchor else end.isAnchor,
    isPivot = if (fraction < .5f) start.isPivot else end.isPivot,
    cutoff = androidx.compose.ui.util.lerp(start.cutoff, end.cutoff, fraction),
)

private fun lerp(from: KeylineList, to: KeylineList, fraction: Float): KeylineList {
    val list = from.fastMapIndexed { i, k -> lerp(k, to[i], fraction) }
    return KeylineList(list)
}

/* ================================================================================================
   MULTI-BROWSE keyline list + Arrangement (versión compacta)
   ================================================================================================ */

/** Arreglo de tamaños para small/medium/large y sus cantidades. */
private data class Arrangement(
    val smallCount: Int,
    val smallSize: Float,
    val mediumCount: Int,
    val mediumSize: Float,
    val largeCount: Int,
    val largeSize: Float,
) {
    fun itemCount() = smallCount + mediumCount + largeCount

    companion object {
        /** Búsqueda simple: prueba combinaciones y escoge la que mejor llena el espacio con menor “coste”. */
        fun findLowestCostArrangement(
            availableSpace: Float,
            itemSpacing: Float,
            targetSmallSize: Float,
            minSmallSize: Float,
            maxSmallSize: Float,
            smallCounts: IntArray,
            targetMediumSize: Float,
            mediumCounts: IntArray,
            targetLargeSize: Float,
            largeCounts: IntArray,
        ): Arrangement? {
            var best: Arrangement? = null
            var bestCost = Float.MAX_VALUE

            fun cost(sz: Float, target: Float) = abs(sz - target) / (target.takeIf { it > 0f } ?: 1f)

            for (lc in largeCounts) {
                for (mc in mediumCounts) {
                    for (sc in smallCounts) {
                        // fijar tamaños (small acotado, medium entre small y large, large <= target)
                        val large = min(targetLargeSize, availableSpace)
                        val small = targetSmallSize.coerceIn(minSmallSize, maxSmallSize)
                        val medium = if (targetMediumSize > 0f) {
                            targetMediumSize.coerceIn(small, large)
                        } else (large + small) / 2f

                        val items = lc + mc + sc
                        if (items == 0) continue
                        val totalSpacing = itemSpacing * (items - 1)
                        val total =
                            (lc * large) + (mc * medium) + (sc * small) + totalSpacing

                        // debe caber (o quedar muy cerca). Permitimos leve sobre/under-fill y penalizamos.
                        val over = max(0f, total - availableSpace)
                        val under = max(0f, availableSpace - total)

                        // coste por desviación de targets + espacio mal usado
                        val c =
                            cost(large, targetLargeSize) * lc +
                                    cost(medium, (large + small) / 2f) * mc +
                                    cost(small, targetSmallSize) * sc +
                                    (over * 3f + under) / (availableSpace + 1f)

                        if (c < bestCost) {
                            bestCost = c
                            best = Arrangement(sc, small, mc, medium, lc, large)
                        }
                    }
                }
            }
            return best
        }
    }
}

private fun multiBrowseKeylineList(
    density: Density,
    carouselMainAxisSize: Float,
    preferredItemSize: Float,
    itemSpacing: Float,
    itemCount: Int,
    minSmallItemSize: Float = with(density) { 40.dp.toPx() },
    maxSmallItemSize: Float = with(density) { 56.dp.toPx() },
    largeCounts: IntArray? = null,
    mediumCounts: IntArray = intArrayOf(1, 0),
    smallCounts: IntArray = intArrayOf(1),
    alignment: CarouselAlignment = CarouselAlignment.Start
): KeylineList {
    if (carouselMainAxisSize == 0f || preferredItemSize == 0f) return emptyKeylineList()

    var resolvedSmallCounts = smallCounts
    val targetLargeSize = min(preferredItemSize, carouselMainAxisSize)
    val targetSmallSize = (targetLargeSize / 3f).coerceIn(minSmallItemSize, maxSmallItemSize)
    val targetMediumSize = (targetLargeSize + targetSmallSize) / 2f

    if (carouselMainAxisSize < minSmallItemSize * 2) resolvedSmallCounts = intArrayOf(0)

    val resolvedLargeCounts = largeCounts ?: run {
        val minAvailableLargeSpace =
            carouselMainAxisSize - targetMediumSize * mediumCounts.max() - maxSmallItemSize * resolvedSmallCounts.max()
        val minLargeCount = max(1, floor(minAvailableLargeSpace / targetLargeSize).toInt())
        val maxLargeCount = ceil(carouselMainAxisSize / targetLargeSize).toInt()
        IntArray(maxLargeCount - minLargeCount + 1) { maxLargeCount - it }
    }
    val anchorSize = with(density) { 10.dp.toPx() }

    var arrangement =
        Arrangement.findLowestCostArrangement(
            availableSpace = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            targetSmallSize = targetSmallSize,
            minSmallSize = minSmallItemSize,
            maxSmallSize = maxSmallItemSize,
            smallCounts = resolvedSmallCounts,
            targetMediumSize = targetMediumSize,
            mediumCounts = mediumCounts,
            targetLargeSize = targetLargeSize,
            largeCounts = resolvedLargeCounts,
        ) ?: return emptyKeylineList()

    if (arrangement.itemCount() > itemCount) {
        var surplus = arrangement.itemCount() - itemCount
        var sc = arrangement.smallCount
        var mc = arrangement.mediumCount
        while (surplus > 0) {
            if (sc > 0) sc -= 1 else if (mc > 1) mc -= 1
            surplus -= 1
        }
        arrangement =
            Arrangement.findLowestCostArrangement(
                availableSpace = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                targetSmallSize = targetSmallSize,
                minSmallSize = minSmallItemSize,
                maxSmallSize = maxSmallItemSize,
                smallCounts = intArrayOf(sc),
                targetMediumSize = targetMediumSize,
                mediumCounts = intArrayOf(mc),
                targetLargeSize = targetLargeSize,
                largeCounts = resolvedLargeCounts,
            ) ?: arrangement
    }

    return createKeylineListFromArrangement(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        leftAnchorSize = anchorSize,
        rightAnchorSize = anchorSize,
        arrangement = arrangement,
        alignment = alignment
    )
}

private fun createKeylineListFromArrangement(
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    leftAnchorSize: Float,
    rightAnchorSize: Float,
    arrangement: Arrangement,
    alignment: CarouselAlignment
): KeylineList {
    return keylineListOf(carouselMainAxisSize, itemSpacing, alignment) {
        add(leftAnchorSize, isAnchor = true)
        repeat(arrangement.largeCount) { add(arrangement.largeSize) }
        repeat(arrangement.mediumCount) { add(arrangement.mediumSize) }
        repeat(arrangement.smallCount) { add(arrangement.smallSize) }
        add(rightAnchorSize, isAnchor = true)
    }
}

/* ================================================================================================
   SNAP + cálculos de scroll
   ================================================================================================ */

private fun getSnapPositionOffset(strategy: Strategy, itemIndex: Int, itemCount: Int): Int {
    if (!strategy.isValid) return 0
    var offset =
        (strategy.defaultKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()

    if (itemIndex <= strategy.startKeylineSteps.lastIndex) {
        val stepIndex = (strategy.startKeylineSteps.lastIndex - itemIndex).coerceIn(0, strategy.startKeylineSteps.lastIndex)
        val startKeylines = strategy.startKeylineSteps[stepIndex]
        offset = (startKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()
    }

    val lastItemIndex = itemCount - 1
    if (itemIndex >= lastItemIndex - strategy.endKeylineSteps.lastIndex &&
        itemCount > strategy.defaultKeylines.focalCount
    ) {
        val stepIndex =
            (strategy.endKeylineSteps.lastIndex - (lastItemIndex - itemIndex)).coerceIn(0, strategy.endKeylineSteps.lastIndex)
        val endKeylines = strategy.endKeylineSteps[stepIndex]
        offset = (endKeylines.firstFocal.unadjustedOffset - strategy.itemMainAxisSize / 2f).roundToInt()
    }
    return offset
}

private fun KeylineSnapPosition(pageSize: CarouselPageSize): SnapPosition =
    object : SnapPosition {
        override fun position(
            layoutSize: Int,
            itemSize: Int,
            beforeContentPadding: Int,
            afterContentPadding: Int,
            itemIndex: Int,
            itemCount: Int,
        ): Int = getSnapPositionOffset(pageSize.strategy, itemIndex, itemCount)
    }

@OptIn(ExperimentalMaterial3Api::class)
private fun calculateCurrentScrollOffset(state: CarouselState, strategy: Strategy): Float {
    val w = strategy.itemMainAxisSize + strategy.itemSpacing
    val curr = (state.pagerState.currentPage * w) + (state.pagerState.currentPageOffsetFraction * w)
    return curr - getSnapPositionOffset(strategy, state.pagerState.currentPage, state.pagerState.pageCount)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun calculateMaxScrollOffset(state: CarouselState, strategy: Strategy): Float {
    val itemCount = state.pagerState.pageCount.toFloat()
    val maxScroll = (strategy.itemMainAxisSize * itemCount) + (strategy.itemSpacing * (itemCount - 1))
    return (maxScroll - strategy.availableSpace).coerceAtLeast(0f)
}

private fun getProgress(before: Keyline, after: Keyline, unadjustedOffset: Float): Float {
    if (before == after) return 1f
    val total = after.unadjustedOffset - before.unadjustedOffset
    return (unadjustedOffset - before.unadjustedOffset) / total
}

/* ================================================================================================
   PAGER STATE (compat) + animateScrollToPage helper
   ================================================================================================ */

private const val MinPageOffset = -0.5f
private const val MaxPageOffset = 0.5f

class CarouselPagerState(
    currentPage: Int,
    currentPageOffsetFraction: Float,
    updatedPageCount: () -> Int,
) : PagerState(currentPage, currentPageOffsetFraction) {
    var pageCountState = mutableStateOf(updatedPageCount)
    override val pageCount: Int get() = pageCountState.value.invoke()

    companion object {
        val Saver: Saver<CarouselPagerState, *> =
            listSaver(
                save = {
                    listOf(
                        it.currentPage,
                        it.currentPageOffsetFraction.coerceIn(MinPageOffset, MaxPageOffset),
                        it.pageCountState.value
                    )
                },
                restore = {
                    CarouselPagerState(
                        currentPage = it[0] as Int,
                        currentPageOffsetFraction = it[1] as Float,
                        updatedPageCount = { it[2] as Int }
                    )
                }
            )
    }
}



/* ================================================================================================
   HELPERS de Padding
   ================================================================================================ */

@Composable
private fun PaddingValues.calculateBeforeContentPadding(orientation: Orientation): Float {
    val dp = if (orientation == Orientation.Vertical) calculateTopPadding()
    else calculateStartPadding(LocalLayoutDirection.current)
    return with(LocalDensity.current) { dp.toPx() }
}

@Composable
private fun PaddingValues.calculateAfterContentPadding(orientation: Orientation): Float {
    val dp = if (orientation == Orientation.Vertical) calculateBottomPadding()
    else calculateEndPadding(LocalLayoutDirection.current)
    return with(LocalDensity.current) { dp.toPx() }
}

/* ---------- helpers de desplazamiento/steps ---------- */

private fun getStartShiftDistance(
    startKeylineSteps: List<KeylineList>,
    beforeContentPadding: Float,
): Float {
    if (startKeylineSteps.isEmpty()) return 0f
    val dist = startKeylineSteps.last().first().unadjustedOffset -
            startKeylineSteps.first().first().unadjustedOffset
    return max(dist, beforeContentPadding)
}

private fun getEndShiftDistance(
    endKeylineSteps: List<KeylineList>,
    afterContentPadding: Float,
): Float {
    if (endKeylineSteps.isEmpty()) return 0f
    val dist = endKeylineSteps.first().last().unadjustedOffset -
            endKeylineSteps.last().last().unadjustedOffset
    return max(dist, afterContentPadding)
}

private fun getStartKeylineSteps(
    defaultKeylines: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    beforeContentPadding: Float,
): List<KeylineList> {
    if (defaultKeylines.isEmpty()) return emptyList()
    val steps = mutableListOf<KeylineList>()
    steps += defaultKeylines

    if (defaultKeylines.isFirstFocalItemAtStartOfContainer()) {
        if (beforeContentPadding != 0f) {
            steps += createShiftedKeylineListForContentPadding(
                from = defaultKeylines,
                carouselMainAxisSize = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                contentPadding = beforeContentPadding,
                pivot = defaultKeylines.firstFocal,
                pivotIndex = defaultKeylines.firstFocalIndex
            )
        }
        return steps
    }

    val startIndex = defaultKeylines.firstNonAnchorIndex
    val endIndex = defaultKeylines.firstFocalIndex
    val numberOfSteps = endIndex - startIndex

    if (numberOfSteps <= 0 && defaultKeylines.firstFocal.cutoff > 0) {
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = defaultKeylines,
            srcIndex = 0,
            dstIndex = 0,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        return steps
    }

    var i = 0
    while (i < numberOfSteps) {
        val prev = steps.last()
        val originalItemIndex = startIndex + i
        var dstIndex = defaultKeylines.lastIndex
        if (originalItemIndex > 0) {
            val originalNeighborBeforeSize = defaultKeylines[originalItemIndex - 1].size
            dstIndex = prev.firstIndexAfterFocalRangeWithSize(originalNeighborBeforeSize) - 1
        }
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = prev,
            srcIndex = defaultKeylines.firstNonAnchorIndex,
            dstIndex = dstIndex,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        i++
    }

    if (beforeContentPadding != 0f) {
        val last = steps.last()
        steps[steps.lastIndex] = createShiftedKeylineListForContentPadding(
            from = last,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            contentPadding = beforeContentPadding,
            pivot = last.firstFocal,
            pivotIndex = last.firstFocalIndex
        )
    }
    return steps
}

private fun getEndKeylineSteps(
    defaultKeylines: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    afterContentPadding: Float,
): List<KeylineList> {
    if (defaultKeylines.isEmpty()) return emptyList()
    val steps = mutableListOf<KeylineList>()
    steps += defaultKeylines

    if (defaultKeylines.isLastFocalItemAtEndOfContainer(carouselMainAxisSize)) {
        if (afterContentPadding != 0f) {
            steps += createShiftedKeylineListForContentPadding(
                from = defaultKeylines,
                carouselMainAxisSize = carouselMainAxisSize,
                itemSpacing = itemSpacing,
                contentPadding = -afterContentPadding,
                pivot = defaultKeylines.lastFocal,
                pivotIndex = defaultKeylines.lastFocalIndex
            )
        }
        return steps
    }

    val startIndex = defaultKeylines.lastFocalIndex
    val endIndex = defaultKeylines.lastNonAnchorIndex
    val numberOfSteps = endIndex - startIndex

    if (numberOfSteps <= 0 && defaultKeylines.lastFocal.cutoff > 0) {
        steps += moveKeylineAndCreateShiftedKeylineList(
            from = defaultKeylines,
            srcIndex = 0,
            dstIndex = 0,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        return steps
    }

    var i = 0
    while (i < numberOfSteps) {
        val prev = steps.last()
        val originalItemIndex = endIndex - i
        var dstIndex = 0
        if (originalItemIndex < defaultKeylines.lastIndex) {
            val originalNeighborAfterSize = defaultKeylines[originalItemIndex + 1].size
            dstIndex = prev.lastIndexBeforeFocalRangeWithSize(originalNeighborAfterSize) + 1
        }
        val keylines = moveKeylineAndCreateShiftedKeylineList(
            from = prev,
            srcIndex = defaultKeylines.lastNonAnchorIndex,
            dstIndex = dstIndex,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing
        )
        steps += keylines
        i++
    }

    if (afterContentPadding != 0f) {
        val last = steps.last()
        steps[steps.lastIndex] = createShiftedKeylineListForContentPadding(
            from = last,
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            contentPadding = -afterContentPadding,
            pivot = last.lastFocal,
            pivotIndex = last.lastFocalIndex
        )
    }
    return steps
}

/* ---------- helpers de shifting concreto ---------- */

private fun createShiftedKeylineListForContentPadding(
    from: KeylineList,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
    contentPadding: Float,
    pivot: Keyline,
    pivotIndex: Int,
): KeylineList {
    val numberOfNonAnchorKeylines = from.fastFilter { !it.isAnchor }.count()
    val sizeReduction = if (numberOfNonAnchorKeylines == 0) 0f else contentPadding / numberOfNonAnchorKeylines

    val newKeylines =
        keylineListOf(
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            pivotIndex = pivotIndex,
            pivotOffset = pivot.offset - (sizeReduction / 2f) + contentPadding
        ) {
            from.fastForEach { k -> add(k.size - abs(sizeReduction), k.isAnchor) }
        }

    // restaurar unadjustedOffset original para que Pager (que usa pageSize fijo) siga consistente
    return KeylineList(
        newKeylines.fastMapIndexed { i, k -> k.copy(unadjustedOffset = from[i].unadjustedOffset) }
    )
}

private fun moveKeylineAndCreateShiftedKeylineList(
    from: KeylineList,
    srcIndex: Int,
    dstIndex: Int,
    carouselMainAxisSize: Float,
    itemSpacing: Float,
): KeylineList {
    val pivotDir = if (srcIndex > dstIndex) 1 else -1
    val pivotDelta = (from[srcIndex].size - from[srcIndex].cutoff + itemSpacing) * pivotDir
    val newPivotIndex = from.pivotIndex + pivotDir
    val newPivotOffset = from.pivot.offset + pivotDelta

    return keylineListOf(
        carouselMainAxisSize = carouselMainAxisSize,
        itemSpacing = itemSpacing,
        pivotIndex = newPivotIndex,
        pivotOffset = newPivotOffset
    ) {
        from.toMutableList().move(srcIndex, dstIndex).fastForEach { k -> add(k.size, k.isAnchor) }
    }
}

private fun MutableList<Keyline>.move(srcIndex: Int, dstIndex: Int): MutableList<Keyline> {
    val k = removeAt(srcIndex)
    add(dstIndex, k)
    return this
}

/* ---------- interpolación en steps ---------- */

private data class ShiftPointRange(
    val fromStepIndex: Int,
    val toStepIndex: Int,
    val steppedInterpolation: Float,
)

private fun getStepInterpolationPoints(
    totalShiftDistance: Float,
    steps: List<KeylineList>,
    isShiftingLeft: Boolean,
): FloatArray {
    val points = FloatArray(max(1, steps.size))
    points[0] = 0f
    if (totalShiftDistance == 0f || steps.isEmpty()) return points

    for (i in 1 until steps.size) {
        val prev = steps[i - 1]
        val curr = steps[i]
        val distanceShifted =
            if (isShiftingLeft) {
                curr.first().unadjustedOffset - prev.first().unadjustedOffset
            } else {
                prev.last().unadjustedOffset - curr.last().unadjustedOffset
            }
        val stepPct = distanceShifted / totalShiftDistance
        val point = if (i == steps.lastIndex) 1f else points[i - 1] + stepPct
        points[i] = point
    }
    return points
}

private fun getShiftPointRange(
    stepsCount: Int,
    shiftPoint: FloatArray,
    interpolation: Float,
): ShiftPointRange {
    var lower = shiftPoint[0]
    for (i in 1 until stepsCount) {
        val upper = shiftPoint[i]
        if (interpolation <= upper) {
            return ShiftPointRange(
                fromStepIndex = i - 1,
                toStepIndex = i,
                steppedInterpolation = lerp(0f, 1f, lower, upper, interpolation)
            )
        }
        lower = upper
    }
    return ShiftPointRange(fromStepIndex = 0, toStepIndex = 0, steppedInterpolation = 0f)
}

private fun lerp(
    outputMin: Float,
    outputMax: Float,
    inputMin: Float,
    inputMax: Float,
    value: Float,
): Float {
    if (value <= inputMin) return outputMin
    if (value >= inputMax) return outputMax
    val t = (value - inputMin) / (inputMax - inputMin)
    return androidx.compose.ui.util.lerp(outputMin, outputMax, t)
}

// === Receiver scope para construir KeylineList ===
internal interface KeylineListScope {
    /**
     * Agrega un keyline (en orden de aparición visual).
     * @param size tamaño (px) del item en ese keyline
     * @param isAnchor true para anchors (usualmente extremos fuera de pantalla)
     */
    fun add(size: Float, isAnchor: Boolean = false)
}

private class KeylineListScopeImpl : KeylineListScope {

    private data class TmpKeyline(val size: Float, val isAnchor: Boolean)

    // Estado interno que usamos tanto en pivot como en alignment:
    private var firstFocalIndex: Int = -1
    private var focalItemSize: Float = 0f
    private val tmpKeylines = mutableListOf<TmpKeyline>()

    override fun add(size: Float, isAnchor: Boolean) {
        tmpKeylines.add(TmpKeyline(size, isAnchor))
        // guardamos el primer índice del mayor tamaño (primer focal)
        if (size > focalItemSize) {
            focalItemSize = size
            firstFocalIndex = tmpKeylines.lastIndex
        }
    }

    /** Encuentra el último índice focal caminando hacia delante mientras el tamaño coincida */
    private fun findLastFocalIndex(): Int {
        var last = firstFocalIndex
        if (firstFocalIndex in 0..tmpKeylines.lastIndex) {
            while (
                last + 1 <= tmpKeylines.lastIndex &&
                tmpKeylines[last + 1].size == focalItemSize
            ) {
                last++
            }
        }
        return last
    }

    /** Geometría para calcular cutoff del lado izquierdo/derecho del carrusel */
    private fun isCutoffLeft(size: Float, center: Float): Boolean {
        val left = center - size / 2f
        val right = center + size / 2f
        return left < 0f && right > 0f
    }

    private fun isCutoffRight(size: Float, center: Float, carouselMainAxisSize: Float): Boolean {
        val left = center - size / 2f
        val right = center + size / 2f
        return left < carouselMainAxisSize && right > carouselMainAxisSize
    }

    /**
     * Construye la lista final a partir de un pivote ya decidido (índice y offset de centro).
     * Genera offsets/unadjustedOffsets/cutoff consistentes a ambos lados del pivote.
     */
    fun createWithPivot(
        carouselMainAxisSize: Float,
        itemSpacing: Float,
        pivotIndex: Int,
        pivotOffset: Float,
    ): KeylineList {
        val lastFocalIndex = findLastFocalIndex()
        val list = mutableListOf<Keyline>()

        // Pivote
        val pivot = tmpKeylines[pivotIndex]
        val pivotCutoff =
            when {
                isCutoffLeft(pivot.size, pivotOffset) ->
                    (pivotOffset - pivot.size / 2f)
                isCutoffRight(pivot.size, pivotOffset, carouselMainAxisSize) ->
                    (pivotOffset + pivot.size / 2f) - carouselMainAxisSize
                else -> 0f
            }
        list += Keyline(
            size = pivot.size,
            offset = pivotOffset,
            unadjustedOffset = pivotOffset,
            isFocal = pivotIndex in firstFocalIndex..lastFocalIndex,
            isAnchor = pivot.isAnchor,
            isPivot = true,
            cutoff = pivotCutoff
        )

        // Antes del pivote (de izq→der en la lista resultante; insertamos al principio)
        var offset = pivotOffset - (focalItemSize / 2f) - itemSpacing
        var unadj  = pivotOffset - (focalItemSize / 2f) - itemSpacing
        for (original in (pivotIndex - 1) downTo 0) {
            val tmp = tmpKeylines[original]
            val center = offset - (tmp.size / 2f)
            val unadjCenter = unadj - (focalItemSize / 2f)
            val cutoff = if (isCutoffLeft(tmp.size, center)) abs(center - tmp.size / 2f) else 0f

            list.add(
                0,
                Keyline(
                    size = tmp.size,
                    offset = center,
                    unadjustedOffset = unadjCenter,
                    isFocal = original in firstFocalIndex..lastFocalIndex,
                    isAnchor = tmp.isAnchor,
                    isPivot = false,
                    cutoff = cutoff
                )
            )
            // ¡sin '-=' ambiguo!
            offset = offset - tmp.size - itemSpacing
            unadj  = unadj  - focalItemSize - itemSpacing
        }

        // Después del pivote
        offset = pivotOffset + (focalItemSize / 2f) + itemSpacing
        unadj  = pivotOffset + (focalItemSize / 2f) + itemSpacing
        for (original in (pivotIndex + 1)..tmpKeylines.lastIndex) {
            val tmp = tmpKeylines[original]
            val center = offset + (tmp.size / 2f)
            val unadjCenter = unadj + (focalItemSize / 2f)
            val cutoff =
                if (isCutoffRight(tmp.size, center, carouselMainAxisSize))
                    (center + tmp.size / 2f) - carouselMainAxisSize
                else 0f

            list += Keyline(
                size = tmp.size,
                offset = center,
                unadjustedOffset = unadjCenter,
                isFocal = original in firstFocalIndex..lastFocalIndex,
                isAnchor = tmp.isAnchor,
                isPivot = false,
                cutoff = cutoff
            )
            offset = offset + tmp.size + itemSpacing
            unadj  = unadj  + focalItemSize + itemSpacing
        }

        return KeylineList(list)
    }

    /**
     * Alineación por `CarouselAlignment` (Start / Center / End) — calcula pivote y delega en pivot.
     */
    fun createWithAlignment(
        carouselMainAxisSize: Float,
        itemSpacing: Float,
        carouselAlignment: CarouselAlignment,
    ): KeylineList {
        val lastFocalIndex = findLastFocalIndex()
        val focalCount = (lastFocalIndex - firstFocalIndex) + 1

        // Elegimos como pivote el primer focal
        val pivotIndex = firstFocalIndex
        val pivotOffset =
            when (carouselAlignment) {
                CarouselAlignment.Center -> {
                    // si el número de focales es par, el spacing parte el centro: compénsalo
                    val spacingSplit = if (itemSpacing == 0f || focalCount % 2 == 0) 0f else itemSpacing / 2f
                    (carouselMainAxisSize / 2f) -
                            ((focalItemSize / 2f) * focalCount) -
                            spacingSplit
                }
                CarouselAlignment.End   -> carouselMainAxisSize - (focalItemSize / 2f)
                else /* Start */        -> (focalItemSize / 2f)
            }

        return createWithPivot(
            carouselMainAxisSize = carouselMainAxisSize,
            itemSpacing = itemSpacing,
            pivotIndex = pivotIndex,
            pivotOffset = pivotOffset
        )
    }
}
