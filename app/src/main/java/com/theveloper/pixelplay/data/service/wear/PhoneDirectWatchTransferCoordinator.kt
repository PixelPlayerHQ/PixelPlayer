package com.theveloper.pixelplay.data.service.wear

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.core.net.toUri
import com.google.android.gms.wearable.Wearable
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.preferences.AlbumArtPaletteStyle
import com.theveloper.pixelplay.data.preferences.ThemePreferencesRepository
import com.theveloper.pixelplay.data.preferences.ThemePreference
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.presentation.viewmodel.ColorSchemeProcessor
import com.theveloper.pixelplay.shared.WearDataPaths
import com.theveloper.pixelplay.shared.WearThemePalette
import com.theveloper.pixelplay.shared.WearTransferMetadata
import com.theveloper.pixelplay.shared.WearTransferProgress
import javax.inject.Singleton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class PhoneDirectWatchTransferCoordinator @Inject constructor(
    private val application: Application,
    private val musicRepository: MusicRepository,
    private val themePreferencesRepository: ThemePreferencesRepository,
    private val colorSchemeProcessor: ColorSchemeProcessor,
    private val transferStateStore: PhoneWatchTransferStateStore,
    private val transferCancellationStore: PhoneWatchTransferCancellationStore,
) {
    private val contentResolver by lazy { application.contentResolver }
    private val messageClient by lazy { Wearable.getMessageClient(application) }
    private val channelClient by lazy { Wearable.getChannelClient(application) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val albumPaletteSeedCache = ConcurrentHashMap<Long, Int>()
    private val albumArtworkTransferCache = ConcurrentHashMap<Long, ByteArray>()

    fun startTransferToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
    ) {
        scope.launch {
            performTransfer(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
            )
        }
    }

    private suspend fun performTransfer(
        nodeId: String,
        requestId: String,
        songId: String,
    ) {
        var openedInputStream: InputStream? = null
        try {
            val song = musicRepository.getSongsByIds(listOf(songId)).first().firstOrNull()
            if (song == null) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = songId,
                    errorMessage = "Song not found",
                )
                return
            }

            if (!isSongTransferEligible(song)) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    errorMessage = "Song must be downloaded locally on phone before saving to watch",
                )
                return
            }

            val fileInputStream = openSongFile(song)
            if (fileInputStream == null) {
                sendTransferMetadataError(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    errorMessage = "Cannot read audio file",
                )
                return
            }
            openedInputStream = fileInputStream

            val fileSize = getSongFileSize(song)
            val paletteSeedArgb = resolvePaletteSeedArgb(song)
            val transferThemePalette = resolveTransferThemePalette(song)
            val transferArtworkBytes = resolveTransferArtworkBytes(song)

            val metadata = WearTransferMetadata(
                requestId = requestId,
                songId = song.id,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                albumId = song.albumId,
                duration = song.duration,
                mimeType = song.mimeType ?: "audio/mpeg",
                fileSize = fileSize,
                bitrate = song.bitrate ?: 0,
                sampleRate = song.sampleRate ?: 0,
                isFavorite = song.isFavorite,
                paletteSeedArgb = paletteSeedArgb,
                themePalette = transferThemePalette,
            )
            transferStateStore.markMetadata(
                requestId = requestId,
                songId = song.id,
                songTitle = song.title,
                totalBytes = fileSize,
            )
            if (transferCancellationStore.consumeCancellation(requestId)) {
                sendTransferProgress(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }

            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_METADATA,
                json.encodeToString(metadata).toByteArray(Charsets.UTF_8),
            ).await()

            // Give the watch a brief window to reject duplicates before the audio stream starts.
            delay(METADATA_GUARD_DELAY_MS)
            val duplicateRejected = transferStateStore.transfers.value[requestId]?.let { transfer ->
                transfer.status == WearTransferProgress.STATUS_FAILED &&
                    transfer.error == WearTransferProgress.ERROR_ALREADY_ON_WATCH
            } == true
            if (duplicateRejected) {
                return
            }

            if (transferArtworkBytes != null) {
                runCatching {
                    streamArtworkToWatch(
                        nodeId = nodeId,
                        requestId = requestId,
                        songId = song.id,
                        artworkBytes = transferArtworkBytes,
                    )
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Artwork transfer failed for songId=%s", song.id)
                }
            }

            if (transferCancellationStore.consumeCancellation(requestId)) {
                sendTransferProgress(
                    nodeId = nodeId,
                    requestId = requestId,
                    songId = song.id,
                    bytesTransferred = 0L,
                    totalBytes = fileSize,
                    status = WearTransferProgress.STATUS_CANCELLED,
                )
                return
            }

            streamFileToWatch(
                nodeId = nodeId,
                requestId = requestId,
                songId = song.id,
                inputStream = fileInputStream,
                fileSize = fileSize,
            )
            openedInputStream = null
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Direct transfer failed for songId=%s", songId)
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = 0L,
                status = WearTransferProgress.STATUS_FAILED,
                error = error.message,
            )
            runCatching { openedInputStream?.close() }
        }
    }

    private fun isSongTransferEligible(song: Song): Boolean {
        val contentUri = song.contentUriString
        if (
            contentUri.startsWith("telegram://") ||
            contentUri.startsWith("netease://") ||
            contentUri.startsWith("gdrive://")
        ) {
            return false
        }

        val localFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let { File(it) }
        if (localFile != null && localFile.isFile && localFile.canRead() && localFile.length() > 0L) {
            return true
        }

        val uri = runCatching { song.contentUriString.toUri() }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme == "file") {
            val uriFile = uri.path?.let { File(it) }
            return uriFile != null && uriFile.isFile && uriFile.canRead() && uriFile.length() > 0L
        }
        if (scheme != "content") return false

        return try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                afd.length != 0L
            } ?: false
        } catch (_: Exception) {
            false
        }
    }

    private fun openSongFile(song: Song): InputStream? {
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                file.inputStream()
            } else {
                contentResolver.openInputStream(song.contentUriString.toUri())
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to open song file: %s", song.path)
            runCatching { contentResolver.openInputStream(song.contentUriString.toUri()) }.getOrNull()
        }
    }

    private fun getSongFileSize(song: Song): Long {
        val file = File(song.path)
        if (file.exists()) return file.length()

        return try {
            contentResolver.openAssetFileDescriptor(song.contentUriString.toUri(), "r")?.use {
                it.length
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun resolvePaletteSeedArgb(song: Song): Int? {
        if (song.albumId > 0L) {
            albumPaletteSeedCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmap(song) ?: return null
        return try {
            extractSeedColorArgb(bitmap)?.also { seed ->
                if (song.albumId > 0L) {
                    albumPaletteSeedCache[song.albumId] = seed
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun resolveTransferArtworkBytes(song: Song): ByteArray? {
        if (song.albumId > 0L) {
            albumArtworkTransferCache[song.albumId]?.let { return it }
        }

        val bitmap = loadSongAlbumArtBitmapForTransfer(song) ?: return null
        return try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, TRANSFER_ARTWORK_QUALITY, stream)
            val bytes = stream.toByteArray()
            if (bytes.isEmpty() || bytes.size > TRANSFER_ARTWORK_MAX_BYTES) {
                null
            } else {
                if (song.albumId > 0L) {
                    albumArtworkTransferCache[song.albumId] = bytes
                }
                bytes
            }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to encode transfer artwork for songId=%s", song.id)
            null
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun resolveTransferThemePalette(song: Song): WearThemePalette? {
        val playerTheme = themePreferencesRepository.playerThemePreferenceFlow.first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && playerTheme == ThemePreference.DYNAMIC) {
            return buildWearThemePalette(dynamicDarkColorScheme(application))
        }

        val artUriString = song.albumArtUriString?.takeIf { it.isNotBlank() } ?: return null
        val paletteStyle = AlbumArtPaletteStyle.fromStorageKey(
            themePreferencesRepository.albumArtPaletteStyleFlow.first().storageKey
        )
        val schemePair = colorSchemeProcessor.getOrGenerateColorScheme(artUriString, paletteStyle)
            ?: return null
        return buildWearThemePalette(schemePair.dark)
    }

    private fun loadSongAlbumArtBitmapForTransfer(song: Song): Bitmap? {
        val fromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString -> decodeBoundedBitmapFromUri(uriString, TRANSFER_ARTWORK_MAX_DIMENSION) }
        if (fromUri != null) return fromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(application, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            decodeBoundedBitmap(embedded, TRANSFER_ARTWORK_MAX_DIMENSION)
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to load transfer artwork for songId=%s", song.id)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun decodeBoundedBitmapFromUri(uriString: String, maxDimension: Int): Bitmap? {
        val uri = uriString.toUri()
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
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
        }

        return contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun decodeBoundedBitmap(data: ByteArray, maxDimension: Int): Bitmap? {
        if (data.isEmpty()) return null

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

        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = false
            },
        )
    }

    private fun loadSongAlbumArtBitmap(song: Song): Bitmap? {
        val artFromUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.let { uriString ->
                runCatching {
                    contentResolver.openInputStream(uriString.toUri())?.use { input ->
                        BitmapFactory.decodeStream(
                            input,
                            null,
                            BitmapFactory.Options().apply {
                                inPreferredConfig = Bitmap.Config.RGB_565
                                inSampleSize = 4
                            },
                        )
                    }
                }.getOrNull()
            }
        if (artFromUri != null) return artFromUri

        val retriever = MediaMetadataRetriever()
        return try {
            val file = File(song.path)
            if (file.exists() && file.canRead()) {
                retriever.setDataSource(song.path)
            } else {
                retriever.setDataSource(application, song.contentUriString.toUri())
            }
            val embedded = retriever.embeddedPicture ?: return null
            BitmapFactory.decodeByteArray(
                embedded,
                0,
                embedded.size,
                BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                    inSampleSize = 2
                },
            )
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Failed to extract album art for palette seed: songId=%s", song.id)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun extractSeedColorArgb(bitmap: Bitmap): Int? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val step = (minOf(bitmap.width, bitmap.height) / 24).coerceAtLeast(1)
        var redSum = 0L
        var greenSum = 0L
        var blueSum = 0L
        var count = 0L

        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) >= 28) {
                    val red = Color.red(pixel)
                    val green = Color.green(pixel)
                    val blue = Color.blue(pixel)
                    if (red + green + blue > 36) {
                        redSum += red
                        greenSum += green
                        blueSum += blue
                        count++
                    }
                }
                x += step
            }
            y += step
        }

        if (count == 0L) return null
        return Color.rgb(
            (redSum / count).toInt(),
            (greenSum / count).toInt(),
            (blueSum / count).toInt(),
        )
    }

    private suspend fun streamFileToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        inputStream: InputStream,
        fileSize: Long,
    ) {
        if (transferCancellationStore.consumeCancellation(requestId)) {
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_CANCELLED,
            )
            return
        }
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_CHANNEL).await()

        try {
            val outputStream = channelClient.getOutputStream(channel).await()
            val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
            outputStream.write(ByteBuffer.allocate(4).putInt(requestIdBytes.size).array())
            outputStream.write(requestIdBytes)

            val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
            var totalSent = 0L
            var lastProgressUpdate = 0L

            inputStream.use { input ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (transferCancellationStore.consumeCancellation(requestId)) {
                        sendTransferProgress(
                            nodeId = nodeId,
                            requestId = requestId,
                            songId = songId,
                            bytesTransferred = totalSent,
                            totalBytes = fileSize,
                            status = WearTransferProgress.STATUS_CANCELLED,
                        )
                        return
                    }

                    outputStream.write(buffer, 0, bytesRead)
                    totalSent += bytesRead

                    if (totalSent - lastProgressUpdate >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                        sendTransferProgress(
                            nodeId = nodeId,
                            requestId = requestId,
                            songId = songId,
                            bytesTransferred = totalSent,
                            totalBytes = fileSize,
                            status = WearTransferProgress.STATUS_TRANSFERRING,
                        )
                        lastProgressUpdate = totalSent
                    }
                }
            }

            outputStream.flush()
            outputStream.close()

            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = fileSize,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_COMPLETED,
            )
        } catch (error: Exception) {
            Timber.tag(TAG).e(error, "Failed to stream file to watch")
            sendTransferProgress(
                nodeId = nodeId,
                requestId = requestId,
                songId = songId,
                bytesTransferred = 0L,
                totalBytes = fileSize,
                status = WearTransferProgress.STATUS_FAILED,
                error = error.message,
            )
        } finally {
            runCatching { channelClient.close(channel).await() }
            transferCancellationStore.clear(requestId)
        }
    }

    private suspend fun streamArtworkToWatch(
        nodeId: String,
        requestId: String,
        songId: String,
        artworkBytes: ByteArray,
    ) {
        if (artworkBytes.isEmpty()) return
        val channel = channelClient.openChannel(nodeId, WearDataPaths.TRANSFER_ARTWORK_CHANNEL).await()
        try {
            val outputStream = channelClient.getOutputStream(channel).await()
            val requestIdBytes = requestId.toByteArray(Charsets.UTF_8)
            val songIdBytes = songId.toByteArray(Charsets.UTF_8)

            outputStream.write(ByteBuffer.allocate(4).putInt(requestIdBytes.size).array())
            outputStream.write(requestIdBytes)
            outputStream.write(ByteBuffer.allocate(4).putInt(songIdBytes.size).array())
            outputStream.write(songIdBytes)
            outputStream.write(artworkBytes)
            outputStream.flush()
            outputStream.close()
        } finally {
            runCatching { channelClient.close(channel).await() }
        }
    }

    private suspend fun sendTransferMetadataError(
        nodeId: String,
        requestId: String,
        songId: String,
        errorMessage: String,
    ) {
        val metadata = WearTransferMetadata(
            requestId = requestId,
            songId = songId,
            title = "",
            artist = "",
            album = "",
            albumId = 0L,
            duration = 0L,
            mimeType = "",
            fileSize = 0L,
            bitrate = 0,
            sampleRate = 0,
            isFavorite = false,
            error = errorMessage,
        )
        runCatching {
            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_METADATA,
                json.encodeToString(metadata).toByteArray(Charsets.UTF_8),
            ).await()
        }.onFailure { error ->
            Timber.tag(TAG).e(error, "Failed to send transfer error metadata")
        }

        sendTransferProgress(
            nodeId = nodeId,
            requestId = requestId,
            songId = songId,
            bytesTransferred = 0L,
            totalBytes = 0L,
            status = WearTransferProgress.STATUS_FAILED,
            error = errorMessage,
        )
    }

    private suspend fun sendTransferProgress(
        nodeId: String,
        requestId: String,
        songId: String,
        bytesTransferred: Long,
        totalBytes: Long,
        status: String,
        error: String? = null,
    ) {
        transferStateStore.markProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        if (status == WearTransferProgress.STATUS_COMPLETED) {
            transferStateStore.markSongPresentOnWatch(
                nodeId = nodeId,
                songId = songId,
            )
        }
        val progress = WearTransferProgress(
            requestId = requestId,
            songId = songId,
            bytesTransferred = bytesTransferred,
            totalBytes = totalBytes,
            status = status,
            error = error,
        )
        runCatching {
            messageClient.sendMessage(
                nodeId,
                WearDataPaths.TRANSFER_PROGRESS,
                json.encodeToString(progress).toByteArray(Charsets.UTF_8),
            ).await()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to send transfer progress")
        }
    }

    private companion object {
        const val TAG = "PhoneDirectTransfer"
        const val TRANSFER_CHUNK_SIZE = 8192
        const val PROGRESS_UPDATE_INTERVAL_BYTES = 65536L
        const val TRANSFER_ARTWORK_MAX_DIMENSION = 1024
        const val TRANSFER_ARTWORK_QUALITY = 95
        const val TRANSFER_ARTWORK_MAX_BYTES = 1_500_000
        const val METADATA_GUARD_DELAY_MS = 250L
    }
}
