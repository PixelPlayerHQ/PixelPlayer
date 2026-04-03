package com.theveloper.pixelplay.data.service

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Size
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class CoilBitmapLoader(private val context: Context, private val scope: CoroutineScope) : BitmapLoader {

    companion object {
        // Large enough for lock screen / media surfaces, but bounded so we never hand
        // unbounded original artwork to MediaSession/SystemUI IPC.
        private const val MAX_NOTIFICATION_ARTWORK_SIZE_PX = 1024
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        return loadBitmapInternal(uri)
    }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return loadBitmapInternal(data)
    }

    private fun loadBitmapInternal(data: Any): ListenableFuture<Bitmap> {
        val future = SettableFuture.create<Bitmap>()

        scope.launch {
            try {
                val request = ImageRequest.Builder(context)
                    .data(data)
                    // Preserve enough resolution for media surfaces while preventing huge
                    // album art from destabilizing notification/SystemUI rendering.
                    .size(MAX_NOTIFICATION_ARTWORK_SIZE_PX, MAX_NOTIFICATION_ARTWORK_SIZE_PX)
                    .precision(Precision.INEXACT)
                    .allowHardware(false) // Bitmap must not be hardware for MediaSession
                    // Disable memory cache so Coil does not hold a second reference to this
                    // bitmap. Without this, Coil may recycle the cached copy while Media3
                    // still uses it for MediaSession IPC ("Can't copy a recycled bitmap").
                    // Disk cache is kept so repeated loads are still fast.
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
                
                val result = context.imageLoader.execute(request)
                val drawable = result.drawable
                
                if (drawable != null) {
                    // toBitmap() now returns a bitmap owned exclusively by us (not in any
                    // Coil cache), so Media3 can use and recycle it freely.
                    val bitmap = drawable.toBitmap()
                    future.set(bitmap)
                } else {
                    future.setException(IllegalStateException("Coil returned null drawable for data: $data"))
                }
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return true // Coil supports most image types
    }
}
