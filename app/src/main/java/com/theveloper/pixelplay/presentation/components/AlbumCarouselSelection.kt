package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.drop
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
    onAlbumClick: (Song) -> Unit = {},
    modifier: Modifier = Modifier,
    carouselStyle: String = CarouselStyle.NO_PEEK,
    itemSpacing: Dp = 8.dp,
    albumArtQuality: AlbumArtQuality = AlbumArtQuality.MEDIUM
) {
    if (queue.isEmpty()) return

    val realItemCount = queue.size
    val enableLoop = realItemCount > 1
    
    fun realIndexToVirtual(realIndex: Int): Int = if (enableLoop) realIndex + 1 else realIndex
    fun virtualIndexToReal(virtualIndex: Int): Int {
        if (!enableLoop) return virtualIndex
        return when (virtualIndex) {
            0 -> realItemCount - 1
            realItemCount + 1 -> 0
            else -> virtualIndex - 1
        }
    }
    
    val virtualItemCount = if (enableLoop) realItemCount + 2 else realItemCount

    // Mantiene compatibilidad con tu llamada actual
    val initialRealIndex = remember(currentSong?.id, queue) {
        val songId = currentSong?.id ?: return@remember 0
        queue.indexOfFirst { it.id == songId }
            .takeIf { it >= 0 }
            ?: queue.indexOf(currentSong)
                .takeIf { it >= 0 }
                ?: 0
    }
    val initialVirtualIndex = realIndexToVirtual(initialRealIndex)

    val carouselState = rememberRoundedParallaxCarouselState(
        initialPage = initialVirtualIndex,
        pageCount = { virtualItemCount }
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
    val currentSongRealIndex = remember(currentSong?.id, queue) {
        val songId = currentSong?.id ?: return@remember 0
        queue.indexOfFirst { it.id == songId }
            .takeIf { it >= 0 }
            ?: queue.indexOf(currentSong)
                .takeIf { it >= 0 }
                ?: 0
    }
    val requestedTargetRealIndex = remember(requestedScrollIndex, queue) {
        requestedScrollIndex?.takeIf { it in queue.indices }
    }
    val effectiveTargetRealIndex = requestedTargetRealIndex ?: currentSongRealIndex
    val effectiveTargetVirtualIndex = realIndexToVirtual(effectiveTargetRealIndex)
    
    val smoothCarouselSpec = remember { tween<Float>(durationMillis = 360, easing = FastOutSlowInEasing) }
    var ignoreNextSettledSelectionForVirtualPage by remember { mutableStateOf<Int?>(null) }
    var programmaticScrollInProgress by remember { mutableStateOf(false) }
    var loopJumpInProgress by remember { mutableStateOf(false) }
    
    LaunchedEffect(effectiveTargetVirtualIndex, requestedTargetRealIndex, queue) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .first { !it }
        val currentVirtual = carouselState.pagerState.currentPage
        val currentReal = virtualIndexToReal(currentVirtual)
        if (currentReal != effectiveTargetRealIndex) {
            if (requestedTargetRealIndex != null) {
                ignoreNextSettledSelectionForVirtualPage = effectiveTargetVirtualIndex
            }
            programmaticScrollInProgress = true
            try {
                carouselState.animateScrollToItem(effectiveTargetVirtualIndex, animationSpec = smoothCarouselSpec)
            } finally {
                programmaticScrollInProgress = false
            }
        }
    }

    val hapticFeedback = LocalHapticFeedback.current
    // Carousel -> Player (cuando se detiene el scroll)
    LaunchedEffect(carouselState, currentSongRealIndex, queue, enableLoop) {
        snapshotFlow { carouselState.pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .filter { !it }
            .collect {
                if (loopJumpInProgress) return@collect
                
                val settledVirtual = carouselState.pagerState.currentPage
                
                if (enableLoop) {
                    when (settledVirtual) {
                        // The last song corresponding to the actual queue
                        0 -> {
                            loopJumpInProgress = true
                            val targetVirtual = realItemCount
                            val targetRealIndex = virtualIndexToReal(targetVirtual)
                            ignoreNextSettledSelectionForVirtualPage = targetVirtual
                            carouselState.scrollToItem(targetVirtual)
                            loopJumpInProgress = false
                            if (targetRealIndex != currentSongRealIndex) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                queue.getOrNull(targetRealIndex)?.let(onSongSelected)
                            }
                            return@collect
                        }
                        // The first song corresponding to the actual queue
                        virtualItemCount - 1 -> {
                            loopJumpInProgress = true
                            val targetVirtual = 1
                            val targetRealIndex = virtualIndexToReal(targetVirtual)
                            ignoreNextSettledSelectionForVirtualPage = targetVirtual
                            carouselState.scrollToItem(targetVirtual)
                            loopJumpInProgress = false
                            if (targetRealIndex != currentSongRealIndex) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                queue.getOrNull(targetRealIndex)?.let(onSongSelected)
                            }
                            return@collect
                        }
                    }
                }

                if (ignoreNextSettledSelectionForVirtualPage == settledVirtual) {
                    ignoreNextSettledSelectionForVirtualPage = null
                    return@collect
                }
                
                val settledRealIndex = virtualIndexToReal(settledVirtual)
                if (settledRealIndex != currentSongRealIndex) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    queue.getOrNull(settledRealIndex)?.let(onSongSelected)
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
            suppressNoPeekSettleCorrection = requestedTargetRealIndex != null || programmaticScrollInProgress || loopJumpInProgress,
            carouselStyle = if (realItemCount == 1) CarouselStyle.NO_PEEK else carouselStyle,
            carouselWidth = availableWidth
        ) { virtualIndex ->
            val realIndex = virtualIndexToReal(virtualIndex)
            val song = queue[realIndex]
            val isFocusedItem = carouselState.pagerState.currentPage == virtualIndex
            key(song.id, virtualIndex) {
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
        }
    }
}
