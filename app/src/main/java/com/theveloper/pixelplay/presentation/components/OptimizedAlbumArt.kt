package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.annotation.ExperimentalCoilApi
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.utils.LocalArtworkUri

@OptIn(ExperimentalCoilApi::class, ExperimentalComposeUiApi::class)
@Composable
fun OptimizedAlbumArt(
    uri: Any?,
    title: String,
    modifier: Modifier = Modifier,
    targetSize: Size = Size.ORIGINAL,
    placeholderModel: Any? = null
) {
    val context = LocalContext.current
    val isStableLocalArtwork = remember(uri) {
        when (uri) {
            is String -> LocalArtworkUri.isLocalArtworkUri(uri)
            is Uri -> LocalArtworkUri.isLocalArtworkUri(uri)
            is ImageRequest -> {
                val data = uri.data
                (data as? String)?.let(LocalArtworkUri::isLocalArtworkUri) == true ||
                    LocalArtworkUri.isLocalArtworkUri(data as? Uri)
            }
            else -> false
        }
    }

    if (renderDirectAlbumArt(
            model = uri,
            title = title,
            modifier = modifier
        )
    ) {
        return
    }

    val requestModel = remember(context, uri, targetSize) {
        when (uri) {
            is ImageRequest -> uri
            else -> ImageRequest.Builder(context)
                .data(uri)
                .crossfade(350) // Use Coil's native crossfade
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.ic_music_placeholder)
                .size(targetSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(if (isStableLocalArtwork) CachePolicy.DISABLED else CachePolicy.ENABLED)
                .build()
        }
    }

    // Use SubcomposeAsyncImage with Coil's native crossfade instead of Crossfade wrapper
    // This avoids recompositions on painter.state changes during scroll.
    SubcomposeAsyncImage(
        model = requestModel,
        contentDescription = "Album art of $title",
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = {
            if (placeholderModel != null) {
                 SubcomposeAsyncImage(
                    model = placeholderModel,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { PlaceholderContent(title = title) },
                    error = { PlaceholderContent(title = title) }
                )
            } else {
                PlaceholderContent(title = title)
            }
        },
        error = {
            PlaceholderContent(title = title)
        },
        success = {
            SubcomposeAsyncImageContent()
        }
    )
}

@Composable
private fun PlaceholderContent(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ic_music_placeholder),
            contentDescription = "$title placeholder",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(96.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                MaterialTheme.colorScheme.onSurfaceVariant
            ),
        )
    }
}

@Composable
private fun renderDirectAlbumArt(
    model: Any?,
    title: String,
    modifier: Modifier
): Boolean {
    return when (model) {
        is ImageRequest -> renderDirectAlbumArt(model.data, title, modifier)
        is ImageVector -> {
            Image(
                imageVector = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Painter -> {
            Image(
                painter = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is ImageBitmap -> {
            Image(
                bitmap = model,
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        is Bitmap -> {
            Image(
                bitmap = model.asImageBitmap(),
                contentDescription = "Album art of $title",
                contentScale = ContentScale.Crop,
                modifier = modifier.fillMaxSize()
            )
            true
        }
        else -> false
    }
}



//@Composable
//fun OptimizedAlbumArt(
//    uri: String?,
//    title: String,
//    expansionFraction: Float,
//    modifier: Modifier = Modifier,
//    targetSize: Size = Size.ORIGINAL
//) {
//    val context = LocalContext.current
//
//    val painter = rememberAsyncImagePainter(
//        model = ImageRequest.Builder(context)
//            .data(uri)
//            .crossfade(false)
//            .placeholder(R.drawable.ic_music_placeholder)
//            .error(R.drawable.rounded_broken_image_24)
//            .size(targetSize) // Usar el parámetro targetSize
//            .memoryCachePolicy(CachePolicy.ENABLED)
//            .diskCachePolicy(CachePolicy.ENABLED)
//            .build(),
//        onState = { state ->
//            Timber.tag("OptimizedAlbumArt")
//                .d("Painter State (Size: $targetSize): $state for URI: $uri")
//            if (state is AsyncImagePainter.State.Error) {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(state.result.throwable, "Coil Error State for URI: $uri")
//            }
//        }
//    )
//
//    val imageContainerModifier = modifier
//        .padding(vertical = lerp(4.dp, 16.dp, expansionFraction))
//        .fillMaxWidth(lerp(0.5f, 0.8f, expansionFraction))
//        .aspectRatio(1f)
//        //.clip(RoundedCornerShape(lerp(16.dp, 24.dp, expansionFraction)))
//        .graphicsLayer {
//            clip = true
//            alpha = expansionFraction
//        }
//
//    Crossfade(
//        targetState = painter.state,
//        modifier = imageContainerModifier,
//        animationSpec = tween(durationMillis = 350),
//        label = "AlbumArtCrossfade"
//    ) { currentState ->
//        when (currentState) {
//            is AsyncImagePainter.State.Loading,
//            is AsyncImagePainter.State.Empty -> { // Show static placeholder for Loading and Empty states
//                Image(
//                    painter = painterResource(id = R.drawable.ic_music_placeholder),
//                    contentDescription = "$title placeholder", // Adjusted content description
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize(),
//                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
//                )
//            }
//            is AsyncImagePainter.State.Error -> {
//                Timber.tag("OptimizedAlbumArt")
//                    .e(currentState.result.throwable, "Displaying error placeholder for URI: $uri")
//                Image(
//                    painter = painterResource(id = R.drawable.rounded_broken_image_24),
//                    contentDescription = "Error loading album art for $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            is AsyncImagePainter.State.Success -> {
//                Image(
//                    painter = currentState.painter,
//                    contentDescription = "Album art of $title",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier.fillMaxSize()
//                )
//            }
//            // Note: AsyncImagePainter.State.Empty is now handled with Loading.
//            // If a distinct visual for Empty is needed and it's different from Loading,
//            // it would need its own branch. For now, grouped with Loading to show the static placeholder.
//        }
//    }
//}
