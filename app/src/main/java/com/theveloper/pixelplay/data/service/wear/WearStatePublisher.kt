package com.theveloper.pixelplay.data.service.wear

import android.app.Application
import android.content.Context
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
import com.theveloper.pixelplay.utils.AlbumArtUtils
import com.theveloper.pixelplay.utils.ArtworkTransportSanitizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

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
    private val audioManager by lazy {
        application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val TAG = "WearStatePublisher"
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
            queueRevision = playerInfo.wearQueueRevision,
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
            val uri = Uri.parse(uriString)
            val scheme = uri.scheme?.lowercase()
            when {
                com.theveloper.pixelplay.utils.LocalArtworkUri.isLocalArtworkUri(uriString) ||
                    scheme == "content" ||
                    scheme == "file" ||
                    scheme == "android.resource" -> {
                    AlbumArtUtils.openArtworkInputStream(application, uri)?.use { input ->
                        readBytesCapped(input, ArtworkTransportSanitizer.WEAR_CONFIG.sourceBytesLimit)
                            ?.let { bytes ->
                                ArtworkTransportSanitizer.sanitizeEncodedBytes(
                                    data = bytes,
                                    config = ArtworkTransportSanitizer.WEAR_CONFIG,
                                )
                            }
                    }?.let { return it }
                }
                scheme == "http" || scheme == "https" -> {
                    downloadAndSanitizeRemoteArtwork(uriString)?.let { return it }
                }
            }
        }
        return ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = playerInfo.albumArtBitmapData,
            config = ArtworkTransportSanitizer.WEAR_CONFIG,
        )
    }

    /**
     * Compress album art to a JPEG suitable for full-screen watch display.
     * Uses bounded downscale to preserve sharpness while keeping payload reasonable.
     */
    private fun createAlbumArtAsset(artBitmapData: ByteArray?): Asset? {
        val boundedBytes = ArtworkTransportSanitizer.sanitizeEncodedBytes(
            data = artBitmapData,
            config = ArtworkTransportSanitizer.WEAR_CONFIG,
        ) ?: return null
        return try {
            Asset.createFromBytes(boundedBytes)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to create album art asset")
            null
        }
    }

    private fun downloadAndSanitizeRemoteArtwork(uriString: String): ByteArray? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(uriString).openConnection() as? HttpURLConnection)
                ?: return null
            connection.connectTimeout = 4_000
            connection.readTimeout = 6_000
            connection.instanceFollowRedirects = true
            connection.doInput = true
            connection.inputStream.use { input ->
                readBytesCapped(input, ArtworkTransportSanitizer.WEAR_CONFIG.sourceBytesLimit)
                    ?.let { bytes ->
                        ArtworkTransportSanitizer.sanitizeEncodedBytes(
                            data = bytes,
                            config = ArtworkTransportSanitizer.WEAR_CONFIG,
                        )
                    }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to download remote artwork for Wear: %s", uriString)
            null
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBytesCapped(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val buffer = ByteArray(16 * 1024)
        val output = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                Timber.tag(TAG).d("Artwork source exceeded hard limit (%d bytes)", total)
                return null
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray().takeIf { it.isNotEmpty() }
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
