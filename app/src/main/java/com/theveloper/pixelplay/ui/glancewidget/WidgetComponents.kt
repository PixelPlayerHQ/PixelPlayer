package com.theveloper.pixelplay.ui.glancewidget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import com.theveloper.pixelplay.R
import timber.log.Timber

@Composable
fun AlbumArtImage(
    modifier: GlanceModifier = GlanceModifier,
    bitmapData: ByteArray?,
    albumArtUri: String? = null,
    size: Dp,
    context: Context,
    cornerRadius: Dp
) {
    val imageProvider = bitmapData?.let { data ->
        val cacheKey = AlbumArtBitmapCache.getKey(data)
        var bitmap = AlbumArtBitmapCache.getBitmap(cacheKey)

        if (bitmap == null) {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)

                var inSampleSize = 1
                // Calculate target size in pixels
                val targetSizePx = (size.value * context.resources.displayMetrics.density).toInt()

                if (options.outHeight > targetSizePx || options.outWidth > targetSizePx) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while (halfHeight / inSampleSize >= targetSizePx &&
                        halfWidth / inSampleSize >= targetSizePx
                    ) {
                        inSampleSize *= 2
                    }
                }

                options.inSampleSize = inSampleSize
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, options)

                bitmap?.let { AlbumArtBitmapCache.putBitmap(cacheKey, it) }
            } catch (e: Exception) {
                Timber.tag("WidgetImage").e(e, "Error decoding bitmap")
                bitmap = null
            }
        }
        bitmap?.let { ImageProvider(it) }
    } ?: albumArtUri?.let { rawUri ->
        val cacheKey = "uri:$rawUri"
        var bitmap = AlbumArtBitmapCache.getBitmap(cacheKey)
        if (bitmap == null) {
            bitmap = decodeAlbumArtFromUri(
                context = context,
                rawUri = rawUri,
                requestedSize = size
            )
            bitmap?.let { AlbumArtBitmapCache.putBitmap(cacheKey, it) }
        }
        bitmap?.let { ImageProvider(it) }
    }

    Box(
        modifier = modifier
    ) {
        if (imageProvider != null) {
            Image(
                provider = imageProvider,
                contentDescription = "Album Art",
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(cornerRadius)
                    .background(GlanceTheme.colors.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_music_placeholder),
                    contentDescription = "Placeholder",
                    modifier = GlanceModifier.size(size * 0.6f),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant)
                )
            }
        }
    }
}

private fun decodeAlbumArtFromUri(
    context: Context,
    rawUri: String,
    requestedSize: Dp,
): Bitmap? {
    val targetPx = (requestedSize.value * context.resources.displayMetrics.density)
        .toInt()
        .coerceAtLeast(64)
    return decodeWidgetAlbumArtBitmap(
        context = context,
        rawUri = rawUri,
        targetWidthPx = targetPx,
        targetHeightPx = targetPx,
    )
}

@Composable
fun WidgetIconButton(
    modifier: GlanceModifier,
    action: Action,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    imageProvider: ImageProvider,
    contentDescription: String,
    iconSize: Dp = 20.dp,
    cornerRadius: Dp
) {
    Box(
        modifier = modifier
            .background(backgroundColor)
            .cornerRadius(cornerRadius)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = imageProvider,
            contentDescription = contentDescription,
            modifier = GlanceModifier.size(iconSize),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

// Convenience wrappers for specific actions
@Composable
fun PreviousButton(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 20.dp
) {
    val params = actionParametersOf(PlayerActions.key to PlayerActions.PREVIOUS)
    WidgetIconButton(
        modifier = modifier,
        action = actionRunCallback<PlayerControlActionCallback>(params),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        imageProvider = ImageProvider(R.drawable.rounded_skip_previous_24),
        contentDescription = "Previous",
        iconSize = iconSize,
        cornerRadius = cornerRadius
    )
}

@Composable
fun NextButton(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 20.dp
) {
    val params = actionParametersOf(PlayerActions.key to PlayerActions.NEXT)
    WidgetIconButton(
        modifier = modifier,
        action = actionRunCallback<PlayerControlActionCallback>(params),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        imageProvider = ImageProvider(R.drawable.rounded_skip_next_24),
        contentDescription = "Next",
        iconSize = iconSize,
        cornerRadius = cornerRadius
    )
}

@Composable
fun PlayPauseButton(
    modifier: GlanceModifier,
    isPlaying: Boolean,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 22.dp
) {
    val params = actionParametersOf(PlayerActions.key to PlayerActions.PLAY_PAUSE)
    WidgetIconButton(
        modifier = modifier,
        action = actionRunCallback<PlayerControlActionCallback>(params),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        imageProvider = ImageProvider(
            if (isPlaying) R.drawable.rounded_pause_24
            else R.drawable.rounded_play_arrow_24
        ),
        contentDescription = if (isPlaying) "Pause" else "Play",
        iconSize = iconSize,
        cornerRadius = cornerRadius
    )
}

@Composable
fun ShuffleButton(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    iconColor: ColorProvider,
    cornerRadius: Dp
) {
    val params = actionParametersOf(PlayerActions.key to PlayerActions.SHUFFLE)
    WidgetIconButton(
        modifier = modifier,
        action = actionRunCallback<PlayerControlActionCallback>(params),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        imageProvider = ImageProvider(R.drawable.rounded_shuffle_24),
        contentDescription = "Shuffle",
        cornerRadius = cornerRadius
    )
}

@Composable
fun RepeatButton(
    modifier: GlanceModifier,
    backgroundColor: ColorProvider,
    iconRes: Int,
    iconColor: ColorProvider,
    cornerRadius: Dp
) {
    val params = actionParametersOf(PlayerActions.key to PlayerActions.REPEAT)
    WidgetIconButton(
        modifier = modifier,
        action = actionRunCallback<PlayerControlActionCallback>(params),
        backgroundColor = backgroundColor,
        iconColor = iconColor,
        imageProvider = ImageProvider(iconRes),
        contentDescription = "Repeat",
        cornerRadius = cornerRadius
    )
}
