package com.theveloper.pixelplay.presentation.components

import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.Alignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size // Import Coil's Size
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.theveloper.pixelplay.R

val SmartImageCompactListTargetSize = Size(96, 96)
val SmartImageListTargetSize = Size(128, 128)

@Composable
fun SmartImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderResId: Int = R.drawable.ic_music_placeholder,
    errorResId: Int = R.drawable.ic_music_placeholder,
    shape: Shape = RectangleShape,
    contentScale: ContentScale = ContentScale.Crop,
    crossfadeDurationMillis: Int = 300,
    useDiskCache: Boolean = true,
    useMemoryCache: Boolean = true,
    allowHardware: Boolean = false,
    targetSize: Size = Size(300, 300),
    colorFilter: ColorFilter? = null,
    alpha: Float = 1f,
    placeholderModel: Any? = null,
    placeHolderBackgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    onState: ((AsyncImagePainter.State) -> Unit)? = null
) {
    val context = LocalContext.current
    val clippedModifier = modifier.clip(shape)

    @Suppress("NAME_SHADOWING")
    val model = when (model) {
        is ImageRequest -> handleDirectModel(
            data = model.data,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: model
        else -> handleDirectModel(
            data = model,
            modifier = clippedModifier,
            contentDescription = contentDescription,
            contentScale = contentScale,
            colorFilter = colorFilter,
            alpha = alpha
        ) ?: model
    }

    if (model == null || model is ImageVector || model is Painter || model is ImageBitmap || model is Bitmap) {
        if (model == null) {
            Placeholder(
                modifier = clippedModifier,
                drawableResId = placeholderResId,
                contentDescription = contentDescription,
                containerColor = placeHolderBackgroundColor,
                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                alpha = alpha
            )
        }
        // Already rendered or null.
        return
    }

    val request = remember(
        context,
        model,
        crossfadeDurationMillis,
        useDiskCache,
        useMemoryCache,
        allowHardware,
        targetSize
    ) {
        when (model) {
            is ImageRequest -> model
            else -> ImageRequest.Builder(context)
                .data(model)
                .crossfade(crossfadeDurationMillis)
                .diskCachePolicy(if (useDiskCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .memoryCachePolicy(if (useMemoryCache) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .allowHardware(allowHardware)
                .apply {
                    size(targetSize)
                }
                .build()
        }
    }

    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = clippedModifier,
        contentScale = contentScale,
        colorFilter = colorFilter,
        alpha = alpha
    ) {
        val state = painter.state

        LaunchedEffect(state) {
            onState?.invoke(state)
        }

        var lastSuccessPainter by remember(request.data) { mutableStateOf<Painter?>(null) }

        when (state) {
            is AsyncImagePainter.State.Success -> {
                lastSuccessPainter = state.painter
                SubcomposeAsyncImageContent()
            }
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading -> {
                val cachedPainter = lastSuccessPainter
                if (cachedPainter != null) {
                    Image(
                        painter = cachedPainter,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                        alpha = alpha
                    )
                } else if (placeholderModel != null) {
                    // Render placeholder model (e.g. low-res thumbnail)
                     SubcomposeAsyncImage(
                        model = placeholderModel,
                        contentDescription = null, // Decorative placeholder
                        modifier = Modifier.fillMaxSize(),
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                        alpha = alpha,
                        error = {
                            Placeholder(
                                modifier = Modifier.fillMaxSize(),
                                drawableResId = placeholderResId,
                                contentDescription = contentDescription,
                                containerColor = placeHolderBackgroundColor,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                alpha = alpha
                            )
                        }
                    )
                } else {
                    Placeholder(
                        modifier = Modifier.fillMaxSize(),
                        drawableResId = placeholderResId,
                        contentDescription = contentDescription,
                        containerColor = placeHolderBackgroundColor,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        alpha = alpha
                    )
                }
            }
            is AsyncImagePainter.State.Error -> {
                val cachedPainter = lastSuccessPainter
                if (cachedPainter != null) {
                    Image(
                        painter = cachedPainter,
                        contentDescription = contentDescription,
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = contentScale,
                        colorFilter = colorFilter,
                        alpha = alpha
                    )
                } else {
                    Placeholder(
                        modifier = Modifier.fillMaxSize(),
                        drawableResId = errorResId,
                        contentDescription = contentDescription,
                        containerColor = placeHolderBackgroundColor,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        alpha = alpha
                    )
                }
            }
        }
    }
}

@Composable
private fun handleDirectModel(
    data: Any?,
    modifier: Modifier,
    contentDescription: String?,
    contentScale: ContentScale,
    colorFilter: ColorFilter?,
    alpha: Float
): Any? {
    return when (data) {
        is ImageVector -> {
            Image(
                imageVector = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Painter -> {
            Image(
                painter = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is ImageBitmap -> {
            Image(
                bitmap = data,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        is Bitmap -> {
            Image(
                bitmap = data.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = colorFilter,
                alpha = alpha
            )
            data
        }
        else -> null
    }
}

@Composable
private fun Placeholder(
    modifier: Modifier,
    @DrawableRes drawableResId: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    alpha: Float,
) {
    Box(
        modifier = modifier
            .alpha(alpha)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(drawableResId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(iconColor),
            modifier = Modifier.size(32.dp),
            contentScale = ContentScale.Fit
        )
    }
}
