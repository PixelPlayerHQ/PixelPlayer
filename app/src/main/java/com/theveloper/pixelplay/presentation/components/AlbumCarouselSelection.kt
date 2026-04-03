package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
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
import kotlin.math.abs

import com.theveloper.pixelplay.data.preferences.AlbumArtQuality

// ====== TIPOS/STATE DEL CARRUSEL (wrapper para mantener compatibilidad) ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberRoundedParallaxCarouselState(
    initialPage: Int,
    pageCount: () -> Int
): CarouselState {
    val realItemCount = pageCount()
    val initialVirtualPage = remember(initialPage, realItemCount) {
        middleStartPage(realItemCount, initialPage)
    }
    return rememberCarouselState(
        initialItem = initialVirtualPage,
        itemCount = { if (realItemCount > 0) Int.MAX_VALUE else 0 }
    )
}

// ====== TU SECCIÓN: ACOPLADA AL NUEVO API ======

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumCarouselSection(
    currentSong: Song?,
    queue: ImmutableList<Song>,
    expansionFraction: Float,
    requestedScrollIndex: Int? = null,
    onSongSelected: (Song) -> Unit,
    modifier: Modifier = Modifier,
    carouselStyle: String = CarouselStyle.NO_PEEK,
    itemSpacing: Dp = 8.dp,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM
) {
    if (queue.isEmpty()) return
    val realItemCount = queue.size

    // Mantiene compatibilidad con tu llamada actual
    val initialIndex = remember(currentSong?.id, queue) {
        val songId = currentSong?.id ?: return@remember 0
        queue.indexOfFirst { it.id == songId }
            .takeIf { it >= 0 }
            ?: queue.indexOf(currentSong)
                .takeIf { it >= 0 }
                ?: 0
    }

    val carouselState = rememberRoundedParallaxCarouselState(
        initialPage = initialIndex,
        pageCount = { queue.size }
    )

    // Calculate target size based on quality
    val targetSize = remember(albumArtQuality) {
        if (albumArtQuality.maxSize == 0) Size.ORIGINAL
        else Size(albumArtQuality.maxSize, albumArtQuality.maxSize)
    }

    PrefetchAlbumNeighbors(
        isActive = expansionFraction > 0.08f,
        pagerState = carouselState.pagerState,
        queue = queue,
        realItemCount = realItemCount,
        radius = 1,
        targetSize = targetSize
    )

    // Player -> Carousel
    val currentSongIndex = remember(currentSong?.id, queue) {
        val songId = currentSong?.id ?: return@remember 0
        queue.indexOfFirst { it.id == songId }
            .takeIf { it >= 0 }
            ?: queue.indexOf(currentSong)
                .takeIf { it >= 0 }
                ?: 0
    }
    val requestedTargetIndex = remember(requestedScrollIndex, queue) {
        requestedScrollIndex?.takeIf { it in queue.indices }
    }
    val effectiveTargetIndex = requestedTargetIndex ?: currentSongIndex
    val effectiveTargetVirtualPage = remember(effectiveTargetIndex, carouselState.pagerState.currentPage, realItemCount) {
        nearestVirtualPageForRealIndex(
            currentVirtualPage = carouselState.pagerState.currentPage,
            targetRealIndex = effectiveTargetIndex,
            realItemCount = realItemCount
        )
    }
    val smoothCarouselSpec = remember { tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing) }
    var ignoreNextSettledSelectionForIndex by remember { mutableStateOf<Int?>(null) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveTargetVirtualPage, requestedTargetIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .first { !it }
        if (carouselState.pagerState.currentPage != effectiveTargetVirtualPage) {
            if (requestedTargetIndex != null) {
                ignoreNextSettledSelectionForIndex = normalizeIndex(effectiveTargetIndex, realItemCount)
            }
            programmaticScrollInProgress = true
            try {
                carouselState.animateScrollToItem(effectiveTargetVirtualPage, animationSpec = smoothCarouselSpec)
            } finally {
                programmaticScrollInProgress = false
            }
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    // Carousel -> Player (cuando se detiene el scroll)
    LaunchedEffect(carouselState, currentSongIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                val settledIndex = normalizeIndex(carouselState.pagerState.currentPage, realItemCount)
                if (ignoreNextSettledSelectionForIndex == settledIndex) {
                    ignoreNextSettledSelectionForIndex = null
                    return@collect
                }
                if (settledIndex != currentSongIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settledIndex)?.let(onSongSelected)
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
            carouselStyle = if (realItemCount == 1) CarouselStyle.NO_PEEK else carouselStyle, // Handle single-item case
            carouselWidth = availableWidth // Pass the full width for layout calculations
        ) { index ->
            val realIndex = normalizeIndex(index, realItemCount)
            val song = queue[realIndex]
            key(song.id) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .aspectRatio(1f)
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
        }
    }
}

private const val InfiniteCarouselVirtualCount = Int.MAX_VALUE

private fun normalizeIndex(index: Int, realItemCount: Int): Int {
    if (realItemCount <= 0) return 0
    val mod = index % realItemCount
    return if (mod >= 0) mod else mod + realItemCount
}

private fun middleStartPage(realItemCount: Int, initialRealIndex: Int): Int {
    if (realItemCount <= 0) return 0
    val middle = InfiniteCarouselVirtualCount / 2
    val alignedMiddle = middle - normalizeIndex(middle, realItemCount)
    return alignedMiddle + normalizeIndex(initialRealIndex, realItemCount)
}

private fun nearestVirtualPageForRealIndex(
    currentVirtualPage: Int,
    targetRealIndex: Int,
    realItemCount: Int
): Int {
    if (realItemCount <= 0) return 0

    val normalizedTarget = normalizeIndex(targetRealIndex, realItemCount)
    val normalizedCurrent = normalizeIndex(currentVirtualPage, realItemCount)
    val base = currentVirtualPage - normalizedCurrent

    val candidate1 = base.toLong() + normalizedTarget
    val candidate2 = base.toLong() + normalizedTarget + realItemCount
    val candidate3 = base.toLong() + normalizedTarget - realItemCount

    val currentLong = currentVirtualPage.toLong()
    val maxValid = InfiniteCarouselVirtualCount.toLong()

    val candidates = listOf(candidate1, candidate2, candidate3)
        .filter { it in 0L until maxValid }

    return candidates.minByOrNull { abs(it - currentLong) }?.toInt()
        ?: (base.toLong() + normalizedTarget).coerceIn(0L, maxValid - 1L).toInt()
}

