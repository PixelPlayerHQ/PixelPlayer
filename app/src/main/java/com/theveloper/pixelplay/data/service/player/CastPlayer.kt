package com.theveloper.pixelplay.data.service.player

import android.content.ContentResolver
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.service.http.CastSessionSecurity
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.ArrayDeque
import java.io.File
import java.io.FileInputStream

class CastPlayer(
    private val castSession: CastSession,
    private val contentResolver: ContentResolver? = null
) {

    private val remoteMediaClient: RemoteMediaClient? = castSession.remoteMediaClient
    private val queueLoadTimeoutMs = 25000L
    private val commandTimeoutMs = 3500L
    private val commandRetryDelayMs = 220L
    private val minCommandSpacingMs = 120L
    private val seekDebounceMs = 140L

    private val commandHandler = Handler(Looper.getMainLooper())
    private val commandQueue = ArrayDeque<QueuedCommand>()
    private var isCommandInFlight = false
    private var lastCommandSentAtMs = 0L
    private var pendingSeekPositionMs: Long? = null
    private var seekDispatchRunnable: Runnable? = null
    private val castLogTag = "CastPlayer"
    private val signatureMimeCache = mutableMapOf<String, String?>()

    private data class QueuedCommand(
        val name: String,
        val retryAttempts: Int,
        val action: (RemoteMediaClient) -> PendingResult<RemoteMediaClient.MediaChannelResult>?
    )

    private fun enqueueRemoteCommand(
        name: String,
        retryAttempts: Int = 1,
        action: (RemoteMediaClient) -> PendingResult<RemoteMediaClient.MediaChannelResult>?
    ) {
        synchronized(commandQueue) {
            commandQueue.addLast(
                QueuedCommand(
                    name = name,
                    retryAttempts = retryAttempts,
                    action = action
                )
            )
            maybeDispatchNextCommandLocked()
        }
    }

    private fun maybeDispatchNextCommandLocked() {
        if (isCommandInFlight || commandQueue.isEmpty()) return
        val nextCommand = commandQueue.removeFirst()
        isCommandInFlight = true

        val now = SystemClock.elapsedRealtime()
        val delayMs = (lastCommandSentAtMs + minCommandSpacingMs - now).coerceAtLeast(0L)
        commandHandler.postDelayed({ executeQueuedCommand(nextCommand) }, delayMs)
    }

    private fun executeQueuedCommand(
        queuedCommand: QueuedCommand,
        retriesLeft: Int = queuedCommand.retryAttempts
    ) {
        val client = remoteMediaClient
        if (client == null) {
            Timber.w("Dropping cast command '%s': remote client unavailable", queuedCommand.name)
            finishQueuedCommand()
            return
        }

        lastCommandSentAtMs = SystemClock.elapsedRealtime()
        var completed = false

        fun complete(requestStatus: Boolean = true) {
            if (completed) return
            completed = true
            if (requestStatus) {
                client.requestStatus()
            }
            finishQueuedCommand()
        }

        val timeoutRunnable = Runnable {
            if (completed) return@Runnable
            Timber.w("Cast command timed out: %s", queuedCommand.name)
            if (retriesLeft > 0) {
                commandHandler.postDelayed(
                    { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                    commandRetryDelayMs
                )
                return@Runnable
            }
            complete(requestStatus = true)
        }
        commandHandler.postDelayed(timeoutRunnable, commandTimeoutMs)

        try {
            val pendingResult = queuedCommand.action(client)
            if (pendingResult == null) {
                commandHandler.removeCallbacks(timeoutRunnable)
                complete(requestStatus = true)
                return
            }

            pendingResult.setResultCallback { result ->
                if (completed) return@setResultCallback
                commandHandler.removeCallbacks(timeoutRunnable)

                if (!result.status.isSuccess && retriesLeft > 0) {
                    val isInvalidRequest = result.status.statusMessage
                        ?.contains("Invalid Request", ignoreCase = true) == true
                    if (isInvalidRequest) {
                        Log.e(
                            "PX_CAST_CMD",
                            "Invalid Request command=${queuedCommand.name} status=${result.status.statusCode} msg=${result.status.statusMessage}"
                        )
                    }
                    if (isInvalidRequest) {
                        Timber.w(
                            "Cast command invalid request: %s (%s/%d)",
                            queuedCommand.name,
                            result.status.statusMessage,
                            result.status.statusCode
                        )
                        complete(requestStatus = true)
                        return@setResultCallback
                    }
                    Timber.w(
                        "Cast command failed (%s/%d). Retrying %s",
                        result.status.statusMessage,
                        result.status.statusCode,
                        queuedCommand.name
                    )
                    commandHandler.postDelayed(
                        { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                        commandRetryDelayMs
                    )
                    return@setResultCallback
                }

                if (!result.status.isSuccess) {
                    Log.e(
                        "PX_CAST_CMD",
                        "Command failed command=${queuedCommand.name} status=${result.status.statusCode} msg=${result.status.statusMessage}"
                    )
                    Timber.w(
                        "Cast command failed: %s (%s/%d)",
                        queuedCommand.name,
                        result.status.statusMessage,
                        result.status.statusCode
                    )
                }
                complete(requestStatus = true)
            }
        } catch (e: Exception) {
            commandHandler.removeCallbacks(timeoutRunnable)
            if (retriesLeft > 0) {
                Timber.w(e, "Cast command threw; retrying %s", queuedCommand.name)
                commandHandler.postDelayed(
                    { executeQueuedCommand(queuedCommand, retriesLeft - 1) },
                    commandRetryDelayMs
                )
                return
            }
            Timber.e(e, "Cast command threw: %s", queuedCommand.name)
            complete(requestStatus = true)
        }
    }

    private fun finishQueuedCommand() {
        synchronized(commandQueue) {
            isCommandInFlight = false
            maybeDispatchNextCommandLocked()
        }
    }

    private fun clearCommandPipeline() {
        seekDispatchRunnable?.let { commandHandler.removeCallbacks(it) }
        seekDispatchRunnable = null
        pendingSeekPositionMs = null
        synchronized(commandQueue) {
            commandQueue.clear()
            isCommandInFlight = false
        }
        commandHandler.removeCallbacksAndMessages(null)
    }

    /**
     * Load a queue of songs onto the Cast device.
     * Includes a timeout to prevent stuck "Connecting..." states.
     */
    fun loadQueue(
        songs: List<Song>,
        startIndex: Int,
        startPosition: Long,
        repeatMode: Int,
        serverAddress: String,
        authToken: String?,
        autoPlay: Boolean,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val client = remoteMediaClient
        if (client == null) {
            onComplete(false, "RemoteMediaClient is null")
            return
        }

        clearCommandPipeline()

        // Track whether callback has been fired to prevent double-calling
        var callbackFired = false
        val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val timeoutRunnable = Runnable {
            if (!callbackFired) {
                callbackFired = true
                Timber.e("Cast loadQueue timed out after %d ms", queueLoadTimeoutMs)
                onComplete(false, "Timed out after ${queueLoadTimeoutMs}ms")
            }
        }

        try {
            val safeStartIndex = startIndex.coerceIn(0, (songs.size - 1).coerceAtLeast(0))
            val startSong = songs.getOrNull(safeStartIndex)
            val queueLoadNonce = SystemClock.elapsedRealtime().toString(36)
            val forcedMimeBySongId = mutableMapOf<String, String>()
            startSong?.let { song ->
                val extractorMime = detectAudioMimeTypeViaExtractor(song)?.toCastSupportedMimeTypeOrNull()
                val retrieverMime = detectAudioMimeTypeViaMetadataRetriever(song)?.toCastSupportedMimeTypeOrNull()
                val signatureMime = detectAudioMimeTypeBySignature(song)
                val forcedMime = (extractorMime ?: retrieverMime ?: signatureMime)?.toCastSupportedMimeTypeOrNull()
                if (forcedMime != null) {
                    forcedMimeBySongId[song.id] = forcedMime
                }
                val resolverMime = contentResolver
                    ?.let { resolver -> runCatching { resolver.getType(song.contentUriString.toUri()) }.getOrNull() }
                Log.i(
                    "PX_CAST_QLOAD",
                    "start_probe songId=${song.id} songMime=${song.mimeType} resolverMime=$resolverMime extractorMime=$extractorMime retrieverMime=$retrieverMime signatureMime=$signatureMime forcedMime=$forcedMime nonce=$queueLoadNonce"
                )
            }
            val mediaItems = songs.map { song ->
                song.toMediaQueueItem(
                    serverAddress = serverAddress,
                    authToken = authToken,
                    forcedMimeType = forcedMimeBySongId[song.id],
                    queueLoadNonce = queueLoadNonce
                )
            }.toTypedArray()

            Timber.tag(castLogTag).i(
                "queueLoad start size=%d startIndex=%d startSongId=%s autoPlay=%s server=%s",
                songs.size,
                safeStartIndex,
                startSong?.id,
                autoPlay,
                serverAddress
            )
            Log.i(
                "PX_CAST_QLOAD",
                "start size=${songs.size} startIndex=$safeStartIndex songId=${startSong?.id} autoPlay=$autoPlay nonce=$queueLoadNonce"
            )
            logQueueDiagnostics(
                songs = songs,
                startIndex = safeStartIndex,
                serverAddress = serverAddress,
                authToken = authToken,
                queueLoadNonce = queueLoadNonce
            )

            timeoutHandler.postDelayed(timeoutRunnable, queueLoadTimeoutMs)

            client.queueLoad(
                mediaItems,
                safeStartIndex,
                repeatMode,
                startPosition,
                null
            ).setResultCallback { result ->
                // Cancel timeout since we got a response
                timeoutHandler.removeCallbacks(timeoutRunnable)
                
                if (callbackFired) {
                    // Timeout already fired, ignore this late callback
                    Timber.w("Cast loadQueue result received after timeout, ignoring")
                    return@setResultCallback
                }
                callbackFired = true
                
                if (result.status.isSuccess) {
                    Timber.tag(castLogTag).i(
                        "queueLoad success statusCode=%d message=%s",
                        result.status.statusCode,
                        result.status.statusMessage
                    )
                    Log.i(
                        "PX_CAST_QLOAD",
                        "success status=${result.status.statusCode} msg=${result.status.statusMessage}"
                    )
                    if (!autoPlay) {
                        // queueLoad typically starts playback by default; explicitly pause when caller requests no autoplay.
                        client.pause()
                    }
                    // Immediately acknowledge success and request a status update to avoid UI stalls.
                    onComplete(true, null)
                    client.requestStatus()
                } else {
                    val failureDetail = "status=${result.status.statusCode} msg=${result.status.statusMessage ?: "unknown"}"
                    Timber.tag(castLogTag).e(
                        "queueLoad failed statusCode=%d message=%s startSongId=%s size=%d",
                        result.status.statusCode,
                        result.status.statusMessage,
                        startSong?.id,
                        songs.size
                    )
                    Log.e(
                        "PX_CAST_QLOAD",
                        "failed status=${result.status.statusCode} msg=${result.status.statusMessage} songId=${startSong?.id} size=${songs.size}"
                    )
                    onComplete(false, failureDetail)
                }
            }
        } catch (e: Exception) {
            timeoutHandler.removeCallbacks(timeoutRunnable)
            Timber.tag(castLogTag).e(e, "queueLoad threw exception (size=%d startIndex=%d)", songs.size, startIndex)
            if (!callbackFired) {
                callbackFired = true
                onComplete(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
            }
        }
    }

    private fun Song.toMediaQueueItem(
        serverAddress: String,
        authToken: String?,
        forcedMimeType: String? = null,
        queueLoadNonce: String? = null
    ): MediaQueueItem {
        val contentType = forcedMimeType ?: resolveCastContentType()
        val durationHintMs = this.duration.coerceAtLeast(0L)
        // Some library entries report inaccurate duration metadata. Let Cast infer duration
        // from the stream to avoid false "track ended" auto-skips on problematic files.
        val streamDuration = MediaInfo.UNKNOWN_DURATION
        val streamRevision = buildCastStreamRevision(contentType, queueLoadNonce)

        val mediaMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK)
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, this.title)
        mediaMetadata.putString(MediaMetadata.KEY_ARTIST, this.displayArtist)
        val artUrl = CastSessionSecurity.buildArtUrl(
            serverAddress = serverAddress,
            songId = this.id,
            streamRevision = streamRevision,
            authToken = authToken
        )
        mediaMetadata.addImage(WebImage(Uri.parse(artUrl)))

        val mediaUrl = CastSessionSecurity.buildSongUrl(
            serverAddress = serverAddress,
            songId = this.id,
            streamRevision = streamRevision,
            authToken = authToken
        )
        val mediaInfo = MediaInfo.Builder(mediaUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setStreamDuration(streamDuration)
            .setMetadata(mediaMetadata)
            .build()

        return MediaQueueItem.Builder(mediaInfo)
            .setCustomData(
                JSONObject()
                    .put("songId", this.id)
                    .put("mimeType", contentType)
                    .put("durationHintMs", durationHintMs)
                    .put("streamDurationSentMs", streamDuration)
                    .put("mimeForced", forcedMimeType != null)
                    .put("streamRevision", streamRevision)
            )
            .build()
    }

    private fun Song.buildCastStreamRevision(contentType: String, queueLoadNonce: String?): String {
        val stableToken = listOf(
            id,
            contentType.lowercase(Locale.ROOT),
            mimeType?.trim().orEmpty().lowercase(Locale.ROOT),
            duration.coerceAtLeast(0L).toString(),
            dateModified.coerceAtLeast(0L).toString(),
            dateAdded.coerceAtLeast(0L).toString(),
            path.substringAfterLast('/').lowercase(Locale.ROOT),
            queueLoadNonce.orEmpty()
        ).joinToString("|")
        return stableToken.hashCode().toUInt().toString(16)
    }

    private fun Song.resolveCastContentType(): String {
        val metadataMimeType = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)

        val resolverMimeType = contentResolver
            ?.let { resolver ->
                runCatching { resolver.getType(contentUriString.toUri()) }.getOrNull()
            }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)

        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val extensionMimeType = when (extension) {
            "mp3", "mpeg" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a", "m4b", "m4p", "mp4", "3gp", "3gpp", "3ga" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "oga" -> "audio/ogg"
            "opus" -> "audio/ogg"
            "weba", "webm" -> "audio/webm"
            "amr" -> "audio/amr"
            else -> null
        }

        val normalizedCandidate = listOfNotNull(metadataMimeType, resolverMimeType, extensionMimeType)
            .firstNotNullOfOrNull { candidate -> candidate.toCastSupportedMimeTypeOrNull() }
            ?: "audio/mpeg"

        val shouldInspectSignature =
            normalizedCandidate == "audio/mpeg" ||
                normalizedCandidate == "audio/aac" ||
                metadataMimeType == null
        val signatureMimeType = if (shouldInspectSignature) detectAudioMimeTypeBySignature(this) else null
        if (signatureMimeType != null && signatureMimeType != normalizedCandidate) {
            Timber.tag(castLogTag).w(
                "MIME mismatch for songId=%s title=%s meta=%s resolver=%s ext=%s signature=%s -> using signature",
                id,
                title,
                metadataMimeType,
                resolverMimeType,
                extensionMimeType,
                signatureMimeType
            )
            return signatureMimeType
        }

        return normalizedCandidate
    }

    private fun String.toCastSupportedMimeTypeOrNull(): String? {
        val normalized = this.trim().substringBefore(';').lowercase(Locale.ROOT)
        return when (normalized) {
            "audio/mpeg",
            "audio/mp3",
            "audio/mpeg3",
            "audio/x-mpeg" -> "audio/mpeg"

            "audio/flac",
            "audio/x-flac" -> "audio/flac"

            "audio/aac",
            "audio/aacp",
            "audio/adts",
            "audio/vnd.dlna.adts",
            "audio/mp4a-latm",
            "audio/aac-latm",
            "audio/x-aac",
            "audio/x-hx-aac-adts" -> "audio/aac"

            "audio/mp4",
            "audio/x-m4a",
            "audio/m4a",
            "audio/3gpp",
            "audio/3gp" -> "audio/mp4"

            "audio/wav",
            "audio/x-wav",
            "audio/wave" -> "audio/wav"

            "audio/ogg",
            "audio/oga",
            "audio/opus",
            "application/ogg" -> "audio/ogg"

            "audio/webm" -> "audio/webm"

            "audio/amr",
            "audio/amr-wb",
            "audio/l16",
            "audio/l24" -> normalized

            else -> null
        }
    }

    fun seek(position: Long) {
        val targetPosition = position.coerceAtLeast(0L)
        pendingSeekPositionMs = targetPosition
        seekDispatchRunnable?.let { commandHandler.removeCallbacks(it) }

        val runnable = Runnable {
            val finalPosition = pendingSeekPositionMs ?: return@Runnable
            pendingSeekPositionMs = null
            enqueueRemoteCommand(
                name = "seek($finalPosition)",
                retryAttempts = 0
            ) { client ->
                val seekOptions = MediaSeekOptions.Builder()
                    .setPosition(finalPosition)
                    .build()
                client.seek(seekOptions)
            }
        }
        seekDispatchRunnable = runnable
        commandHandler.postDelayed(runnable, seekDebounceMs)
    }

    fun play() {
        enqueueRemoteCommand(name = "play", retryAttempts = 1) { client ->
            client.play()
        }
    }

    fun pause() {
        enqueueRemoteCommand(name = "pause", retryAttempts = 1) { client ->
            client.pause()
        }
    }

    fun next() {
        enqueueRemoteCommand(name = "next", retryAttempts = 1) { client ->
            val status = client.mediaStatus
            val queueItems = status?.queueItems ?: emptyList()
            val currentItemId = status?.currentItemId
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            val nextItem = if (currentIndex >= 0) queueItems.getOrNull(currentIndex + 1) else null
            if (nextItem != null) {
                client.queueJumpToItem(nextItem.itemId, 0L, null)
            } else {
                client.queueNext(null)
            }
        }
    }

    fun previous() {
        enqueueRemoteCommand(name = "previous", retryAttempts = 1) { client ->
            val status = client.mediaStatus
            val queueItems = status?.queueItems ?: emptyList()
            val currentItemId = status?.currentItemId
            val currentIndex = queueItems.indexOfFirst { it.itemId == currentItemId }
            val previousItem = if (currentIndex > 0) queueItems.getOrNull(currentIndex - 1) else null
            if (previousItem != null) {
                client.queueJumpToItem(previousItem.itemId, 0L, null)
            } else {
                client.queuePrev(null)
            }
        }
    }

    fun jumpToItem(itemId: Int, position: Long) {
        enqueueRemoteCommand(name = "jumpToItem($itemId)", retryAttempts = 0) { client ->
            client.queueJumpToItem(itemId, position.coerceAtLeast(0L), null)
        }
    }

    fun setRepeatMode(repeatMode: Int) {
        enqueueRemoteCommand(name = "setRepeatMode($repeatMode)", retryAttempts = 0) { client ->
            client.queueSetRepeatMode(repeatMode, null)
        }
    }

    fun release() {
        clearCommandPipeline()
    }

    private fun detectAudioMimeTypeBySignature(song: Song): String? {
        signatureMimeCache[song.id]?.let { return it }
        val bytes = readAudioSignature(song) ?: run {
            signatureMimeCache[song.id] = null
            return null
        }

        val id3PayloadOffset = parseId3PayloadOffset(bytes)
        val detected = detectMimeAtOffset(bytes, id3PayloadOffset)
            ?: detectMimeAtOffset(bytes, 0)
            ?: detectFramedAudioMime(bytes, id3PayloadOffset)
            ?: detectFramedAudioMime(bytes, 0)
        signatureMimeCache[song.id] = detected
        return detected
    }

    private fun readAudioSignature(song: Song, maxBytes: Int = 16 * 1024): ByteArray? {
        val uriBytes = runCatching {
            val uri = song.contentUriString.toUri()
            contentResolver
                ?.openInputStream(uri)
                ?.use { input ->
                    val buffer = ByteArray(maxBytes)
                    val read = input.read(buffer)
                    if (read <= 0) null else buffer.copyOf(read)
                }
        }.getOrNull()
        if (uriBytes != null && uriBytes.isNotEmpty()) {
            return uriBytes
        }

        val file = song.path
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.canRead() }
            ?: return null

        return runCatching {
            FileInputStream(file).use { input ->
                val buffer = ByteArray(maxBytes)
                val read = input.read(buffer)
                if (read <= 0) null else buffer.copyOf(read)
            }
        }.getOrNull()
    }

    private fun parseId3PayloadOffset(bytes: ByteArray): Int {
        if (bytes.size < 10) return 0
        if (bytes[0] != 'I'.code.toByte() || bytes[1] != 'D'.code.toByte() || bytes[2] != '3'.code.toByte()) {
            return 0
        }
        val flags = bytes[5].toInt() and 0xFF
        val hasFooter = (flags and 0x10) != 0
        val tagSize = ((bytes[6].toInt() and 0x7F) shl 21) or
            ((bytes[7].toInt() and 0x7F) shl 14) or
            ((bytes[8].toInt() and 0x7F) shl 7) or
            (bytes[9].toInt() and 0x7F)
        val totalTagBytes = 10 + tagSize + if (hasFooter) 10 else 0
        return totalTagBytes.coerceIn(0, bytes.size)
    }

    private fun detectMimeAtOffset(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= bytes.size) return null
        val remaining = bytes.size - offset
        if (remaining >= 4 &&
            bytes[offset] == 'f'.code.toByte() &&
            bytes[offset + 1] == 'L'.code.toByte() &&
            bytes[offset + 2] == 'a'.code.toByte() &&
            bytes[offset + 3] == 'C'.code.toByte()
        ) {
            return "audio/flac"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'O'.code.toByte() &&
            bytes[offset + 1] == 'g'.code.toByte() &&
            bytes[offset + 2] == 'g'.code.toByte() &&
            bytes[offset + 3] == 'S'.code.toByte()
        ) {
            return "audio/ogg"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'R'.code.toByte() &&
            bytes[offset + 1] == 'I'.code.toByte() &&
            bytes[offset + 2] == 'F'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte() &&
            bytes[offset + 8] == 'W'.code.toByte() &&
            bytes[offset + 9] == 'A'.code.toByte() &&
            bytes[offset + 10] == 'V'.code.toByte() &&
            bytes[offset + 11] == 'E'.code.toByte()
        ) {
            return "audio/wav"
        }
        if (remaining >= 12 &&
            bytes[offset] == 'F'.code.toByte() &&
            bytes[offset + 1] == 'O'.code.toByte() &&
            bytes[offset + 2] == 'R'.code.toByte() &&
            bytes[offset + 3] == 'M'.code.toByte() &&
            bytes[offset + 8] == 'A'.code.toByte() &&
            bytes[offset + 9] == 'I'.code.toByte() &&
            bytes[offset + 10] == 'F'.code.toByte() &&
            bytes[offset + 11] == 'F'.code.toByte()
        ) {
            return "audio/aiff"
        }
        if (remaining >= 12 &&
            bytes[offset + 4] == 'f'.code.toByte() &&
            bytes[offset + 5] == 't'.code.toByte() &&
            bytes[offset + 6] == 'y'.code.toByte() &&
            bytes[offset + 7] == 'p'.code.toByte()
        ) {
            return "audio/mp4"
        }
        if (remaining >= 4 &&
            bytes[offset] == 'A'.code.toByte() &&
            bytes[offset + 1] == 'D'.code.toByte() &&
            bytes[offset + 2] == 'I'.code.toByte() &&
            bytes[offset + 3] == 'F'.code.toByte()
        ) {
            return "audio/aac"
        }
        return null
    }

    private fun detectFramedAudioMime(bytes: ByteArray, startOffset: Int): String? {
        if (bytes.size < 2) return null
        val start = startOffset.coerceIn(0, bytes.lastIndex)
        for (index in start until bytes.size - 1) {
            val b0 = bytes[index].toInt() and 0xFF
            val b1 = bytes[index + 1].toInt() and 0xFF
            if (b0 != 0xFF || (b1 and 0xF0) != 0xF0) continue
            // MPEG audio headers encode layer != 00. AAC ADTS always uses layer == 00.
            val layerBits = (b1 ushr 1) and 0x03
            if (layerBits == 0) return "audio/aac"
            if (layerBits in 1..3) return "audio/mpeg"
        }
        return null
    }

    private fun detectAudioMimeTypeViaExtractor(song: Song): String? {
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        return runCatching {
            val extractor = MediaExtractor()
            try {
                val sourceAttached = runCatching {
                    resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                        val length = afd.length
                            .takeIf { it > 0L }
                            ?: afd.declaredLength.takeIf { it > 0L }
                        if (length != null) {
                            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, length)
                        } else {
                            extractor.setDataSource(afd.fileDescriptor)
                        }
                    } != null
                }.getOrElse { false } || runCatching {
                    resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        extractor.setDataSource(pfd.fileDescriptor)
                    } != null
                }.getOrElse { false }

                if (!sourceAttached) {
                    return@runCatching null
                }

                for (trackIndex in 0 until extractor.trackCount) {
                    val trackMime = extractor.getTrackFormat(trackIndex)
                        .getString(MediaFormat.KEY_MIME)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.lowercase(Locale.ROOT)
                    if (trackMime != null && trackMime.startsWith("audio/")) {
                        return@runCatching trackMime
                    }
                }
                null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()
    }

    private fun detectAudioMimeTypeViaMetadataRetriever(song: Song): String? {
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        val retriever = MediaMetadataRetriever()
        return runCatching {
            val sourceConfigured = runCatching {
                resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val length = afd.length
                        .takeIf { it > 0L }
                        ?: afd.declaredLength.takeIf { it > 0L }
                    if (length != null) {
                        retriever.setDataSource(afd.fileDescriptor, afd.startOffset, length)
                    } else {
                        retriever.setDataSource(afd.fileDescriptor)
                    }
                } != null
            }.getOrElse { false } || runCatching {
                retriever.setDataSource(song.path)
                true
            }.getOrElse { false }

            if (!sourceConfigured) {
                return@runCatching null
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?.lowercase(Locale.ROOT)
        }.getOrNull().also {
            runCatching { retriever.release() }
        }
    }

    private fun logQueueDiagnostics(
        songs: List<Song>,
        startIndex: Int,
        serverAddress: String,
        authToken: String?,
        queueLoadNonce: String
    ) {
        if (songs.isEmpty()) return

        val interestingIndexes = linkedSetOf<Int>().apply {
            add(0)
            add(startIndex)
            add((startIndex - 1).coerceAtLeast(0))
            add((startIndex + 1).coerceAtMost(songs.lastIndex))
            add(songs.lastIndex)
        }

        interestingIndexes
            .filter { it in songs.indices }
            .sorted()
            .forEach { index ->
                val song = songs[index]
                val resolvedType = song.resolveCastContentType()
                val streamRevision = song.buildCastStreamRevision(resolvedType, queueLoadNonce)
                val likelySupported = resolvedType in setOf(
                    "audio/mpeg",
                    "audio/aac",
                    "audio/mp4",
                    "audio/flac",
                    "audio/wav",
                    "audio/ogg",
                    "audio/webm",
                    "audio/amr"
                )
                val mediaUrl = CastSessionSecurity.redactAuthToken(
                    CastSessionSecurity.buildSongUrl(
                        serverAddress = serverAddress,
                        songId = song.id,
                        streamRevision = streamRevision,
                        authToken = authToken
                    )
                )
                val artUrl = CastSessionSecurity.redactAuthToken(
                    CastSessionSecurity.buildArtUrl(
                        serverAddress = serverAddress,
                        songId = song.id,
                        streamRevision = streamRevision,
                        authToken = authToken
                    )
                )
                Timber.tag(castLogTag).d(
                    "queueItem[%d] id=%s mimeRaw=%s mimeResolved=%s supported=%s durationHintMs=%d streamDurationSentMs=%d mediaUrl=%s artUrl=%s",
                    index,
                    song.id,
                    song.mimeType,
                    resolvedType,
                    likelySupported,
                    song.duration,
                    MediaInfo.UNKNOWN_DURATION,
                    mediaUrl,
                    artUrl
                )
                Log.i(
                    "PX_CAST_QLOAD",
                    "item index=$index songId=${song.id} mimeRaw=${song.mimeType} mime=$resolvedType durationHintMs=${song.duration.coerceAtLeast(0L)} streamDurationSentMs=${MediaInfo.UNKNOWN_DURATION}"
                )
            }
    }
}
