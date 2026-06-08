package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.CarouselStyle
import com.theveloper.pixelplay.presentation.components.scoped.PrefetchAlbumNeighbors
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.first

import com.theveloper.pixelplay.data.preferences.AlbumArtQuality

// ====== TIPOS/STATE DEL CARRUSEL (wrapper para mantener compatibilidad) ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRoundedParallaxCarouselState(
    initialPage: Int,
    pageCount: () -> Int
): CarouselState = rememberCarouselState(initialItem = initialPage, itemCount = pageCount)

// ====== TU SECCIÓN: ACOPLADA AL NUEVO API ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCarouselSection(
    currentSong: Song?,
    queue: ImmutableList<Song>,
    expansionFraction: Float,
    currentMediaItemIndex: Int = -1,
    requestedScrollIndex: Int? = null,
    onSongSelected: (Song, Int) -> Unit,
    onAlbumClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier,
    carouselStyle: String = CarouselStyle.NO_PEEK,
    itemSpacing: Dp = 8.dp,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM
) {
    if (queue.isEmpty()) return

    // Mantiene compatibilidad con tu llamada actual
    val initialIndex = remember(currentSong?.id, currentMediaItemIndex, queue) {
        resolveCurrentQueueIndex(
            currentSong = currentSong,
            currentMediaItemIndex = currentMediaItemIndex,
            queue = queue
        )
    }

    val carouselState = rememberRoundedParallaxCarouselState(
        initialPage = initialIndex,
        pageCount = { queue.size }
    )

    // Snappy, critically-damped (no-overshoot) spring for programmatic skip scrolls.
    // The previous MotionScheme.expressive().defaultSpatialSpec was a slow, bouncy spring whose
    // long settle tail produced many frames of per-item measure/clip work, reading as "laggy".
    // A no-bounce spring snaps in and settles fast, matching the native feel of the open/close
    // animation while cutting the number of expensive carousel frames.
    val carouselAnimationSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    }

    // Calculate target size based on quality
    val targetSize = remember(albumArtQuality) {
        if (albumArtQuality.maxSize == 0) SafeOriginalAlbumArtSize
        else Size(albumArtQuality.maxSize, albumArtQuality.maxSize)
    }

    // Player -> Carousel
    val currentSongIndex = remember(currentSong?.id, currentMediaItemIndex, queue) {
        resolveCurrentQueueIndex(
            currentSong = currentSong,
            currentMediaItemIndex = currentMediaItemIndex,
            queue = queue
        )
    }
    val requestedTargetIndex = remember(requestedScrollIndex, queue) {
        requestedScrollIndex?.takeIf { it in queue.indices }
    }
    val effectiveTargetIndex = requestedTargetIndex ?: currentSongIndex
    val carouselItemKeys = remember(queue) {
        buildQueueOccurrenceKeys(queue)
    }

    PrefetchAlbumNeighbors(
        isActive = expansionFraction > 0.08f,
        pagerState = carouselState.pagerState,
        queue = queue,
        radius = 1,
        targetSize = targetSize,
        anchorIndex = effectiveTargetIndex
    )
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    var lastSettledSongId by remember { mutableStateOf(currentSong?.id) }

    // Track whether the *user* is actively dragging the carousel (via the pager's interaction
    // source) so we can tell a real swipe apart from our own programmatic skip/seek scrolls.
    var isUserDragging by remember { mutableStateOf(false) }
    var userDragSettlePending by remember { mutableStateOf(false) }
    LaunchedEffect(carouselState) {
        carouselState.pagerState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> {
                    isUserDragging = true
                    userDragSettlePending = true
                }
                is DragInteraction.Stop, is DragInteraction.Cancel -> isUserDragging = false
            }
        }
    }

    // Player -> Carousel. Retargets continuously: each press cancels the in-flight animation and
    // animates from the current position to the new target, so a rapid skip burst scrolls smoothly
    // through the albums instead of freezing. We only ever defer to an active *user* drag — never
    // to our own (cancelled) programmatic scroll, which is what previously caused the freeze: the
    // effect restarted on every press and then blocked waiting for the just-cancelled scroll to go
    // idle, so the carousel never advanced until the user stopped tapping.
    LaunchedEffect(effectiveTargetIndex, requestedTargetIndex, queue) {
        if (isUserDragging) {
            snapshotFlow { isUserDragging }.first { !it }
        }
        
        val currentPage = carouselState.pagerState.currentPage
        if (currentPage != effectiveTargetIndex) {
            val isShiftOnly = currentSong?.id != null && 
                              currentSong.id == lastSettledSongId && 
                              requestedTargetIndex == null
            
            if (isShiftOnly) {
                // Same song moved to a new index: scroll instantly to maintain focus
                // and avoid showing the wrong item for the duration of an animation.
                carouselState.pagerState.scrollToPage(effectiveTargetIndex)
            } else {
                programmaticScrollInProgress = true
                try {
                    carouselState.animateScrollToItem(effectiveTargetIndex, animationSpec = carouselAnimationSpec)
                } finally {
                    programmaticScrollInProgress = false
                }
            }
        }
        lastSettledSongId = currentSong?.id
    }

    val hapticFeedback = LocalHapticFeedback.current
    // Carousel -> Player (cuando se detiene el scroll)
    LaunchedEffect(carouselState, currentSongIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                // Only a settle that followed a real user drag changes the song. Programmatic
                // skip/seek scrolls (and their mid-burst cancellations) must not feed back into
                // the player — it already drove them — otherwise a rapid skip burst would fire
                // spurious selections and fight the optimistic carousel index.
                if (!userDragSettlePending) return@collect
                userDragSettlePending = false
                val settled = carouselState.pagerState.currentPage
                if (settled != currentSongIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settled)?.let { onSongSelected(it, settled) }
                }
            }
    }

    val corner = 18.dp//lerp(36.dp, 15.dp, expansionFraction.coerceIn(0f, 1f))

    BoxWithConstraints(modifier = modifier) {
        val availableWidth = this.maxWidth

        RoundedHorizontalMultiBrowseCarousel(
            state = carouselState,
            modifier = Modifier.fillMaxSize(), // Fill the space provided by the parent's modifier
            itemSpacing = itemSpacing,
            itemCornerRadius = corner,
            suppressNoPeekSettleCorrection = requestedTargetIndex != null || programmaticScrollInProgress,
            carouselStyle = if (carouselState.pagerState.pageCount == 1) CarouselStyle.NO_PEEK else carouselStyle, // Handle single-item case
            carouselWidth = availableWidth, // Pass the full width for layout calculations
            itemKey = { index -> carouselItemKeys.getOrNull(index) ?: "queue_item_$index" },
            content = { index ->
                val song = queue[index]
                val isFocusedItem = carouselState.pagerState.currentPage == index
                Box(
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
                        .clickable(
                            enabled = isFocusedItem && song.albumId != -1L,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAlbumClick(song) }
                        )
                ) { // Enforce 1:1 aspect ratio for the item itself
                    OptimizedAlbumArt(
                        uri = song.albumArtUriString,
                        title = song.title,
                        modifier = Modifier.fillMaxSize(),
                        targetSize = targetSize,
                        placeholderModel = if (song.albumArtUriString?.startsWith("telegram_art") == true) {
                             "${song.albumArtUriString}?quality=thumb"
                        } else null
                    )
                }
            }
        )
    }
}

private fun resolveCurrentQueueIndex(
    currentSong: Song?,
    currentMediaItemIndex: Int,
    queue: ImmutableList<Song>
): Int {
    val songId = currentSong?.id ?: return 0
    if (currentMediaItemIndex in queue.indices && queue[currentMediaItemIndex].id == songId) {
        return currentMediaItemIndex
    }
    return queue.indexOfFirst { it.id == songId }
        .takeIf { it >= 0 }
        ?: queue.indexOf(currentSong)
            .takeIf { it >= 0 }
        ?: 0
}

private fun buildQueueOccurrenceKeys(queue: ImmutableList<Song>): List<String> {
    val occurrencesBySongId = HashMap<String, Int>()
    return queue.mapIndexed { index, song ->
        val occurrence = occurrencesBySongId.getOrDefault(song.id, 0)
        occurrencesBySongId[song.id] = occurrence + 1
        "queue_carousel_${song.id}_${occurrence}_${song.albumArtUriString.orEmpty().hashCode()}_$index"
    }
}
