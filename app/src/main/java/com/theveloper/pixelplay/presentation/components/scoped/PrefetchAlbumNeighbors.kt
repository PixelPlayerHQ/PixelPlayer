package com.theveloper.pixelplay.presentation.components.scoped

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.utils.LocalArtworkUri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun PrefetchAlbumNeighborsImg(
    current: Song?,
    queue: ImmutableList<Song>,
    radius: Int = 1
) {
    if (current == null) return
    val context = LocalContext.current
    val loader = remember { coil.ImageLoader(context) }
    val index = remember(current, queue) { queue.indexOfFirst { it.id == current.id } }
    LaunchedEffect(index, queue) {
        if (index == -1) return@LaunchedEffect
        val bounds = (maxOf(0, index - radius))..(minOf(queue.lastIndex, index + radius))
        for (i in bounds) {
            if (i == index) continue
            queue[i].albumArtUriString?.let { data ->
                val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(data)) CachePolicy.DISABLED else CachePolicy.ENABLED
                val req = coil.request.ImageRequest.Builder(context)
                    .data(data)
                    .memoryCacheKey("album:$data")
                    .diskCacheKey(if (diskPolicy == CachePolicy.DISABLED) null else "album:$data")
                    .diskCachePolicy(diskPolicy)
                    .size(coil.size.Size.ORIGINAL)
                    .build()
                loader.enqueue(req)
            }
        }
    }
}


@Composable
fun PrefetchAlbumNeighbors(
    isActive: Boolean,
    pagerState: PagerState,
    queue: ImmutableList<Song>,
    radius: Int = 1,
    targetSize: Size = Size(600, 600)
) {
    if (!isActive || queue.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)

    LaunchedEffect(pagerState, queue) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val indices = (page - radius..page + radius)
                    .filter { it in queue.indices && it != page }
                indices.forEach { idx ->
                    queue[idx].albumArtUriString?.let { uri ->
                        val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) coil.request.CachePolicy.DISABLED else coil.request.CachePolicy.ENABLED
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .size(targetSize)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(diskPolicy)
                            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                        imageLoader.enqueue(req) // fire-and-forget
                    }
                }
            }
    }
}

/**
 * Prefetch album art for neighboring pages with virtual-to-real index mapping.
 * Use this when the pager uses virtual indices (e.g., for infinite looping).
 */
@Composable
fun PrefetchAlbumNeighborsWithMapping(
    isActive: Boolean,
    pagerState: PagerState,
    queue: ImmutableList<Song>,
    radius: Int = 1,
    targetSize: Size = Size(600, 600),
    virtualToRealIndex: (Int) -> Int
) {
    if (!isActive || queue.isEmpty()) return
    val context = LocalContext.current
    val imageLoader = coil.Coil.imageLoader(context)

    LaunchedEffect(pagerState, queue) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { virtualPage ->
                val currentRealIndex = virtualToRealIndex(virtualPage)
                val neighborRealIndices = (currentRealIndex - radius..currentRealIndex + radius)
                    .filter { it in queue.indices && it != currentRealIndex }
                    .toSet()
                
                neighborRealIndices.forEach { realIdx ->
                    queue[realIdx].albumArtUriString?.let { uri ->
                        val diskPolicy = if (LocalArtworkUri.isLocalArtworkUri(uri)) 
                            coil.request.CachePolicy.DISABLED 
                        else 
                            coil.request.CachePolicy.ENABLED
                        val req = coil.request.ImageRequest.Builder(context)
                            .data(uri)
                            .size(targetSize)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(diskPolicy)
                            .networkCachePolicy(coil.request.CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                        imageLoader.enqueue(req)
                    }
                }
            }
    }
}
