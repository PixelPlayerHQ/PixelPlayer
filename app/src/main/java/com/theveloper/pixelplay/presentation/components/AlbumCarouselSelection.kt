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
    requestedScrollIndex: Int? = null,
    onSongSelected: (Song) -> Unit,
    modifier: Modifier = Modifier,
    carouselStyle: String = CarouselStyle.NO_PEEK,
    itemSpacing: Dp = 8.dp,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM
) {
    if (queue.isEmpty()) return

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
    val smoothCarouselSpec = remember { tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing) }
    var ignoreNextSettledSelectionForPage by remember { mutableStateOf<Int?>(null) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    LaunchedEffect(effectiveTargetIndex, requestedTargetIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .first { !it }
        if (carouselState.pagerState.currentPage != effectiveTargetIndex) {
            if (requestedTargetIndex != null) {
                ignoreNextSettledSelectionForPage = effectiveTargetIndex
            }
            programmaticScrollInProgress = true
            try {
                carouselState.animateScrollToItem(effectiveTargetIndex, animationSpec = smoothCarouselSpec)
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
                val settled = carouselState.pagerState.currentPage
                if (ignoreNextSettledSelectionForPage == settled) {
                    ignoreNextSettledSelectionForPage = null
                    return@collect
                }
                if (settled != currentSongIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settled)?.let(onSongSelected)
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
            carouselWidth = availableWidth // Pass the full width for layout calculations
        ) { index ->
            val song = queue[index]
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
