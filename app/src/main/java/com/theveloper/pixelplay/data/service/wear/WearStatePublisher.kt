package com.theveloper.pixelplay.data.service.wear

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import androidx.core.graphics.ColorUtils
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.theveloper.pixelplay.data.model.PlayerInfo
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearPlayerState
import com.theveloper.pixelplay.shared.WearThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Publishes player state to the Wear Data Layer so the watch app can display it.
 *
 * Album art is sent as a bounded-size JPEG Asset for full-screen quality on watch.
 */
@Singleton
class WearStatePublisher @Inject constructor(
    private val application: Application,
) {
    private val dataClient by lazy { Wearable.getDataClient(application) }
    private val contentResolver by lazy { application.contentResolver }
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "WearStatePublisher"
        private const val ART_MAX_DIMENSION = 2048 // px, near-original quality on watch screens
        private const val ART_QUALITY = 99 // JPEG quality
        private const val MAX_DIRECT_ASSET_BYTES = 10_000_000 // keep full assets when practical
        private const val MAX_URI_BYTES = 12_000_000 // hard cap for URI direct reads
    }

    /**
     * Publish the current player state to Wear Data Layer.
     * Converts PlayerInfo -> WearPlayerState (lightweight DTO) and sends as DataItem.
     *
     * @param songId The current media item's ID
     * @param playerInfo The full player info from MusicService
     */
    fun publishState(songId: String?, playerInfo: PlayerInfo) {
        scope.launch {
            try {
                publishStateInternal(songId, playerInfo)
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to publish state to Wear Data Layer")
            }
        }
    }

    /**
     * Clear state from the Data Layer (e.g. when service is destroyed).
     */
    fun clearState() {
        scope.launch {
            try {
                val request = PutDataMapRequest.create(WearDataPaths.PLAYER_STATE).apply {
                    dataMap.putString(WearDataPaths.KEY_STATE_JSON, "")
                    dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()

                dataClient.putDataItem(request)
                Timber.tag(TAG).d("Cleared Wear player state")
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to clear Wear state")
            }
        }
    }

    private suspend fun publishStateInternal(songId: String?, playerInfo: PlayerInfo) {
        val volumeLevel = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        val wearState = WearPlayerState(
            songId = songId.orEmpty(),
            songTitle = playerInfo.songTitle,
            artistName = playerInfo.artistName,
            albumName = "", // Album name not in PlayerInfo; will be enriched in future phases
            isPlaying = playerInfo.isPlaying,
            currentPositionMs = playerInfo.currentPositionMs,
            totalDurationMs = playerInfo.totalDurationMs,
            isFavorite = playerInfo.isFavorite,
            isShuffleEnabled = playerInfo.isShuffleEnabled,
            repeatMode = playerInfo.repeatMode,
            volumeLevel = volumeLevel,
            volumeMax = volumeMax,
            themePalette = buildWearThemePalette(playerInfo),
        )

        val stateJson = json.encodeToString(wearState)

        val request = PutDataMapRequest.create(WearDataPaths.PLAYER_STATE).apply {
            dataMap.putString(WearDataPaths.KEY_STATE_JSON, stateJson)
            dataMap.putLong(WearDataPaths.KEY_TIMESTAMP, System.currentTimeMillis())

            // Attach album art as Asset if available
            val wearArtBytes = resolveArtworkBytesForWear(playerInfo)
            val artAsset = createAlbumArtAsset(wearArtBytes)
            if (artAsset != null) {
                dataMap.putAsset(WearDataPaths.KEY_ALBUM_ART, artAsset)
            } else {
                dataMap.remove(WearDataPaths.KEY_ALBUM_ART)
            }
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request)
        Timber.tag(TAG).d("Published state to Wear: ${wearState.songTitle} (playing=${wearState.isPlaying})")
    }

    private fun resolveArtworkBytesForWear(playerInfo: PlayerInfo): ByteArray? {
        val uriString = playerInfo.albumArtUri
        if (!uriString.isNullOrBlank()) {
            readBytesFromUriCapped(uriString, MAX_URI_BYTES)?.let { return it }
            renderUriArtworkForWear(uriString)?.let { return it }
        }
        return playerInfo.albumArtBitmapData
    }

    private fun readBytesFromUriCapped(uriString: String, maxBytes: Int): ByteArray? {
        return try {
            val uri = Uri.parse(uriString)
            contentResolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(16 * 1024)
                val output = ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) {
                        Timber.tag(TAG).d(
                            "Artwork URI too large for direct transfer (%d bytes): %s",
                            total,
                            uriString
                        )
                        return null
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray().takeIf { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read artwork bytes from URI: %s", uriString)
            null
        }
    }

    /**
     * Fallback path when a URI asset can't be transferred as-is (e.g. too large).
     * Decodes from URI and re-encodes with high quality at bounded dimensions.
     */
    private fun renderUriArtworkForWear(uriString: String): ByteArray? {
        return try {
            val uri = Uri.parse(uriString)
            val bounded = decodeBoundedBitmapFromUri(uri, ART_MAX_DIMENSION) ?: return null
            val output = ByteArrayOutputStream()
            bounded.compress(Bitmap.CompressFormat.JPEG, ART_QUALITY, output)
            bounded.recycle()
            output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed URI artwork fallback render: %s", uriString)
            null
        }
    }

    /**
     * Compress album art to a JPEG suitable for full-screen watch display.
     * Uses bounded downscale to preserve sharpness while keeping payload reasonable.
     */
    private fun createAlbumArtAsset(artBitmapData: ByteArray?): Asset? {
        if (artBitmapData == null || artBitmapData.isEmpty()) return null

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(artBitmapData, 0, artBitmapData.size, bounds)
            val srcWidth = bounds.outWidth
            val srcHeight = bounds.outHeight
            val srcMax = max(srcWidth, srcHeight)
            if (
                srcWidth > 0 &&
                srcHeight > 0 &&
                srcMax <= ART_MAX_DIMENSION &&
                artBitmapData.size <= MAX_DIRECT_ASSET_BYTES
            ) {
                // Preserve original bytes when already suitable; avoids second lossy pass.
                return Asset.createFromBytes(artBitmapData)
            }

            val scaled = decodeBoundedBitmap(artBitmapData, ART_MAX_DIMENSION)
                ?: return null

            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, ART_QUALITY, stream)
            scaled.recycle()

            Asset.createFromBytes(stream.toByteArray())
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to create album art asset")
            null
        }
    }

    private fun decodeBoundedBitmap(data: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
            inMutable = false
        }
        val decoded = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOptions) ?: return null

        val decodedMax = max(decoded.width, decoded.height)
        if (decodedMax <= maxDimension) {
            return decoded
        }

        val scale = maxDimension.toFloat() / decodedMax.toFloat()
        val targetWidth = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (resized !== decoded) {
            decoded.recycle()
        }
        return resized
    }

    private fun decodeBoundedBitmapFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        val srcWidth = bounds.outWidth
        val srcHeight = bounds.outHeight
        if (srcWidth <= 0 || srcHeight <= 0) return null

        var sampleSize = 1
        while (
            (srcWidth / sampleSize) > maxDimension * 2 ||
            (srcHeight / sampleSize) > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inJustDecodeBounds = false
            inMutable = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        val decoded = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val decodedMax = max(decoded.width, decoded.height)
        if (decodedMax <= maxDimension) return decoded

        val scale = maxDimension.toFloat() / decodedMax.toFloat()
        val targetWidth = (decoded.width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (decoded.height * scale).roundToInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(decoded, targetWidth, targetHeight, true)
        if (resized !== decoded) decoded.recycle()
        return resized
    }

    /**
     * Builds a watch-oriented palette from phone-side theme colors already computed by MusicService.
     * This avoids re-extracting colors from album art on watch and keeps both UIs aligned.
     */
    private fun buildWearThemePalette(playerInfo: PlayerInfo): WearThemePalette? {
        playerInfo.wearThemePalette?.let { return it }

        val colors = playerInfo.themeColors ?: return null

        val surfaceContainer = colors.darkSurfaceContainer
        val surfaceContainerLowest = colors.darkSurfaceContainerLowest.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.BLACK, 0.24f)
        val surfaceContainerLow = colors.darkSurfaceContainerLow.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.08f)
        val surfaceContainerHigh = colors.darkSurfaceContainerHigh.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.16f)
        val surfaceContainerHighest = colors.darkSurfaceContainerHighest.takeIf { it != 0 }
            ?: ColorUtils.blendARGB(surfaceContainer, AndroidColor.WHITE, 0.24f)
        val title = colors.darkTitle
        val artist = colors.darkArtist
        val playContainer = colors.darkPlayPauseBackground
        val playContent = colors.darkPlayPauseIcon
        val secondaryContainer = colors.darkPrevNextBackground
        val secondaryContent = colors.darkPrevNextIcon

        val gradientTop = ColorUtils.blendARGB(surfaceContainerHigh, playContainer, 0.24f)
        val gradientMiddle = ColorUtils.blendARGB(surfaceContainer, AndroidColor.BLACK, 0.48f)
        val gradientBottom = ColorUtils.blendARGB(surfaceContainerLowest, AndroidColor.BLACK, 0.78f)

        val disabledContainer = surfaceContainerHighest
        val chipContainer = ColorUtils.blendARGB(secondaryContainer, surfaceContainerLow, 0.36f)

        return WearThemePalette(
            gradientTopArgb = gradientTop,
            gradientMiddleArgb = gradientMiddle,
            gradientBottomArgb = gradientBottom,
            surfaceContainerLowestArgb = surfaceContainerLowest,
            surfaceContainerLowArgb = surfaceContainerLow,
            surfaceContainerArgb = surfaceContainer,
            surfaceContainerHighArgb = surfaceContainerHigh,
            surfaceContainerHighestArgb = surfaceContainerHighest,
            textPrimaryArgb = ensureReadable(preferredColor = title, backgroundColor = gradientMiddle),
            textSecondaryArgb = ensureReadable(preferredColor = artist, backgroundColor = gradientBottom),
            textErrorArgb = 0xFFFFB8C7.toInt(),
            controlContainerArgb = playContainer,
            controlContentArgb = ensureReadable(preferredColor = playContent, backgroundColor = playContainer),
            controlDisabledContainerArgb = disabledContainer,
            controlDisabledContentArgb = ensureReadable(
                preferredColor = artist,
                backgroundColor = disabledContainer
            ),
            transportContainerArgb = secondaryContainer,
            transportContentArgb = ensureReadable(
                preferredColor = secondaryContent,
                backgroundColor = secondaryContainer,
            ),
            chipContainerArgb = chipContainer,
            chipContentArgb = ensureReadable(preferredColor = secondaryContent, backgroundColor = chipContainer),
            favoriteActiveArgb = shiftHue(playContainer, 34f),
            shuffleActiveArgb = shiftHue(playContainer, -72f),
            repeatActiveArgb = shiftHue(playContainer, -22f),
        )
    }

    private fun shiftHue(color: Int, hueShift: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[0] = (hsl[0] + hueShift + 360f) % 360f
        hsl[1] = (hsl[1] * 1.18f).coerceIn(0.42f, 0.92f)
        hsl[2] = (hsl[2] + 0.08f).coerceIn(0.34f, 0.78f)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun ensureReadable(preferredColor: Int, backgroundColor: Int): Int {
        val opaqueBackground = if (AndroidColor.alpha(backgroundColor) >= 255) {
            backgroundColor
        } else {
            ColorUtils.compositeColors(backgroundColor, AndroidColor.BLACK)
        }
        val preferredContrast = ColorUtils.calculateContrast(preferredColor, opaqueBackground)
        if (preferredContrast >= 3.0) return preferredColor

        val light = 0xFFF6F2FF.toInt()
        val dark = 0xFF17141E.toInt()
        val lightContrast = ColorUtils.calculateContrast(light, opaqueBackground)
        val darkContrast = ColorUtils.calculateContrast(dark, opaqueBackground)
        return if (lightContrast >= darkContrast) light else dark
    }
}
