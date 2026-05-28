package com.theveloper.pixelplay.data.service.player

import android.content.ContentResolver
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaQueueItem
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.api.PendingResult
import com.google.android.gms.common.images.WebImage
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.network.NetworkTimeouts
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

    companion object {
        private const val MIME_NONE = ""
        private const val ISO_BMFF_CODEC_PROBE_BYTES = 1024 * 1024
        private val extractorMimeCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val retrieverMimeCache = java.util.concurrent.ConcurrentHashMap<String, String>()
        private val signatureMimeCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    }

    private val remoteMediaClient: RemoteMediaClient? = castSession.remoteMediaClient
    private val queueLoadTimeoutMs = NetworkTimeouts.CAST_QUEUE_LOAD_MS
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
                        Timber.w(
                            "Cast InvalidRequest command=%s status=%d msg=%s",
                            queuedCommand.name,
                            result.status.statusCode,
                            result.status.statusMessage
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

            // Run I/O-heavy codec probing on a background thread so the UI stays responsive.
            // Cast SDK calls (queueLoad, pause, requestStatus) must happen on Main, so we
            // post back via the timeoutHandler (which targets the Main looper).
            Thread {
                Thread.currentThread().name = "cast-queue-probe"
                try {
                    val forcedMimeBySongId = mutableMapOf<String, String>()

                    // Probe songs that could be transcode candidates.
                    // The server transcodes ALAC or FLAC → AAC ADTS (audio/aac).
                    for (index in songs.indices) {
                        val song = songs[index]
                        val ext = song.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        val mimeL = song.mimeType?.lowercase(Locale.ROOT)

                        // Optimize: FLAC is easily identified without extractor calls
                        val isFlac = ext == "flac" || mimeL == "audio/flac" || mimeL == "audio/x-flac"
                        if (isFlac) {
                            val flacDecoderAvailable = isFlacTranscodeSupported()
                            val forcedMime = if (flacDecoderAvailable) "audio/aac" else "audio/flac"
                            forcedMimeBySongId[song.id] = forcedMime
                            Log.i(
                                "PX_CAST_QLOAD",
                                "flac_direct songId=${song.id} forcedMime=$forcedMime decoderAvailable=$flacDecoderAvailable nonce=$queueLoadNonce"
                            )
                            continue
                        }

                        // We need to probe songs that the server will transcode (ALAC → AAC).
                        val isMaybeTranscodeCandidate =
                            ext.let { it == "m4a" || it == "m4b" || it == "mp4" || it == "3gp" || it == "3gpp" } ||
                            mimeL?.let { it.contains("mp4") || it.contains("m4a") || it == "audio/alac" } == true

                        if (!isMaybeTranscodeCandidate && song.id != startSong?.id) {
                            // Not a transcode-candidate file and not the start song — skip the probe.
                            continue
                        }

                        // Sliding window: only probe if cached, start song, or within window
                        val cachedExtractorMime = extractorMimeCache[song.id]
                        val rawExtractorMime = if (cachedExtractorMime != null) {
                            if (cachedExtractorMime == MIME_NONE) null else cachedExtractorMime
                        } else if (song.id == startSong?.id || index in (safeStartIndex - 2)..(safeStartIndex + 8)) {
                            detectAudioMimeTypeViaExtractor(song)
                        } else {
                            null
                        }

                        // Ogg containers are more reliable on Cast when the codec is explicit.
                        if (rawExtractorMime == "audio/opus" || rawExtractorMime == "audio/vorbis") {
                            val forcedMime = CastAudioMimeUtils.toCastSupportedMimeTypeOrNull(rawExtractorMime)
                            if (forcedMime != null) {
                                forcedMimeBySongId[song.id] = forcedMime
                                Log.i(
                                    "PX_CAST_QLOAD",
                                    "ogg_codec_probe songId=${song.id} rawCodec=$rawExtractorMime forcedMime=$forcedMime nonce=$queueLoadNonce"
                                )
                            }
                            continue
                        }

                        // Only override the MIME when the server will transcode (ALAC or FLAC → AAC ADTS).
                        if (rawExtractorMime == "audio/alac") {
                            val alacDecoderAvailable = isAlacTranscodeSupported()
                            val forcedMime = if (alacDecoderAvailable) "audio/aac" else "audio/mp4"
                            forcedMimeBySongId[song.id] = forcedMime
                            Timber.tag(castLogTag).i(
                                "alac_probe songId=%s rawCodec=audio/alac forcedMime=%s decoderAvailable=%s nonce=%s",
                                song.id, forcedMime, alacDecoderAvailable, queueLoadNonce
                            )
                            continue
                        }

                        if (rawExtractorMime == "audio/flac") {
                            val flacDecoderAvailable = isFlacTranscodeSupported()
                            val forcedMime = if (flacDecoderAvailable) "audio/aac" else "audio/flac"
                            forcedMimeBySongId[song.id] = forcedMime
                            Timber.tag(castLogTag).i(
                                "flac_probe songId=%s rawCodec=audio/flac forcedMime=%s decoderAvailable=%s nonce=%s",
                                song.id, forcedMime, flacDecoderAvailable, queueLoadNonce
                            )
                            continue
                        }

                        if (song.id == startSong?.id) {
                            val retrieverMime = detectAudioMimeTypeViaMetadataRetriever(song)?.toCastSupportedMimeTypeOrNull()
                            val signatureMime = if (retrieverMime == null) {
                                detectAudioMimeTypeBySignature(song)?.toCastSupportedMimeTypeOrNull()
                            } else null
                            val forcedMime = retrieverMime ?: signatureMime
                            if (forcedMime != null) {
                                forcedMimeBySongId[song.id] = forcedMime
                            }
                            Timber.tag(castLogTag).i(
                                "start_probe songId=%s songMime=%s rawExtractorMime=%s retrieverMime=%s signatureMime=%s forcedMime=%s nonce=%s",
                                song.id, song.mimeType, rawExtractorMime, retrieverMime, signatureMime, forcedMime, queueLoadNonce
                            )
                        }
                    }

                    // Post back to Main thread — Cast SDK requires Main for queueLoad.
                    timeoutHandler.post {
                        try {
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
                            logQueueDiagnostics(
                                songs = songs,
                                startIndex = safeStartIndex,
                                serverAddress = serverAddress,
                                authToken = authToken,
                                queueLoadNonce = queueLoadNonce,
                                forcedMimeBySongId = forcedMimeBySongId
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
                } catch (e: Exception) {
                    Timber.tag(castLogTag).e(e, "queueLoad probe threw exception (size=%d startIndex=%d)", songs.size, startIndex)
                    timeoutHandler.post {
                        if (!callbackFired) {
                            callbackFired = true
                            onComplete(false, "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}")
                        }
                    }
                }
            }.start()
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
        val streamDuration = durationHintMs.takeIf { it > 0L } ?: MediaInfo.UNKNOWN_DURATION
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
            "ogg", "oga" -> CastAudioMimeUtils.AUDIO_OGG
            "opus" -> CastAudioMimeUtils.AUDIO_OGG_OPUS
            "weba", "webm" -> "audio/webm"
            "amr" -> "audio/amr"
            else -> null
        }

        val rawMimeCandidates = listOf(metadataMimeType, resolverMimeType, extensionMimeType)
        val normalizedCandidate = rawMimeCandidates
            .filterNotNull()
            .firstNotNullOfOrNull { candidate -> candidate.toCastSupportedMimeTypeOrNull() }
            ?: "audio/mpeg"

        if (CastAudioMimeUtils.baseMimeType(normalizedCandidate) == CastAudioMimeUtils.AUDIO_OGG) {
            val metadataOggContentType = CastAudioMimeUtils.resolveOggContentType(
                rawMimeCandidates = rawMimeCandidates,
                extension = extension,
                headerBytes = null
            )
            if (metadataOggContentType != null &&
                CastAudioMimeUtils.isExactOggContentType(metadataOggContentType)
            ) {
                return metadataOggContentType
            }
            return CastAudioMimeUtils.resolveOggContentType(
                rawMimeCandidates = rawMimeCandidates,
                extension = extension,
                headerBytes = readAudioSignature(this)
            ) ?: metadataOggContentType ?: normalizedCandidate
        }

        // Container formats from metadata are reliable. Signature detection (framed sync-word scan)
        // can produce false positives on container binary data (e.g. 0xFF bytes inside MP4 moov atom).
        // Only use signature to disambiguate truly ambiguous metadata (audio/mpeg or audio/aac).
        val isContainerFormat = normalizedCandidate != "audio/mpeg" && normalizedCandidate != "audio/aac"
        if (isContainerFormat) {
            return normalizedCandidate
        }

        // Metadata is ambiguous — consult signature detection to resolve.
        val signatureMimeType = detectAudioMimeTypeBySignature(this)
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
        return CastAudioMimeUtils.toCastSupportedMimeTypeOrNull(this)
    }

    fun canSeekCurrentItem(): Boolean {
        val status = remoteMediaClient?.mediaStatus ?: return true
        val currentItemId = status.currentItemId
        val currentItem = status.getQueueItemById(currentItemId)
        val mediaContentType = currentItem?.media?.contentType
        val customMimeType = currentItem
            ?.customData
            ?.optString("mimeType")
            ?.takeIf { it.isNotBlank() }

        return !CastAudioMimeUtils.isCastSeekUnstableContentType(mediaContentType) &&
            !CastAudioMimeUtils.isCastSeekUnstableContentType(customMimeType)
    }

    fun seek(position: Long): Boolean {
        if (!canSeekCurrentItem()) {
            pendingSeekPositionMs = null
            seekDispatchRunnable?.let { commandHandler.removeCallbacks(it) }
            seekDispatchRunnable = null
            val status = remoteMediaClient?.mediaStatus
            val currentItem = status?.getQueueItemById(status.currentItemId)
            Timber.tag(castLogTag).w(
                "Blocked Cast seek for unstable Ogg stream. itemId=%s contentType=%s customMime=%s",
                status?.currentItemId,
                currentItem?.media?.contentType,
                currentItem?.customData?.optString("mimeType")
            )
            remoteMediaClient?.requestStatus()
            return false
        }

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
        return true
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
        val cached = signatureMimeCache[song.id]
        if (cached != null) {
            return if (cached == MIME_NONE) null else cached
        }
        val bytes = readAudioSignature(song) ?: run {
            signatureMimeCache[song.id] = MIME_NONE
            return null
        }

        val id3PayloadOffset = parseId3PayloadOffset(bytes)
        val detected = detectMimeAtOffset(bytes, id3PayloadOffset)
            ?: detectMimeAtOffset(bytes, 0)
            ?: detectFramedAudioMime(bytes, id3PayloadOffset)
            ?: detectFramedAudioMime(bytes, 0)
        signatureMimeCache[song.id] = detected ?: MIME_NONE
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
        // ISO Base Media File Format (MP4/M4A/M4B): check for 'ftyp' box at bytes 4-7.
        // Requires at least offset+8 bytes to safely access offset+4..offset+7.
        if (remaining >= 12 && offset + 8 <= bytes.size &&
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
        val cached = extractorMimeCache[song.id]
        if (cached != null) {
            return if (cached == MIME_NONE) null else cached
        }
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        val result = runCatching {
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
                    val trackFormat = extractor.getTrackFormat(trackIndex)
                    var trackMime = trackFormat.getString(MediaFormat.KEY_MIME)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?.lowercase(Locale.ROOT)
                    if (trackMime != null && trackMime.startsWith("audio/")) {
                        if (trackMime == "audio/mp4a-latm" || trackMime == "audio/eac3" || trackMime == "audio/ac3") {
                            val isM4a = song.path.endsWith(".m4a", true)
                            val isExplicitAlacMetadata = song.mimeType?.contains("alac", true) == true
                            val isoBmffCodec = if (isM4a) detectIsoBmffAudioCodec(song) else null
                            val hasCsd0 = (trackMime == "audio/eac3" || trackMime == "audio/ac3") &&
                                runCatching { (trackFormat.getByteBuffer("csd-0")?.remaining() ?: 0) > 0 }
                                    .getOrDefault(false)

                            val isImpossibleCodecInM4a = isM4a &&
                                (trackMime == "audio/eac3" || trackMime == "audio/ac3") &&
                                hasCsd0 &&
                                isoBmffCodec != "audio/ac3" &&
                                isoBmffCodec != "audio/eac3"

                            if (isExplicitAlacMetadata || isoBmffCodec == "audio/alac" || isImpossibleCodecInM4a) {
                                trackMime = "audio/alac"
                            } else if (isM4a) {
                                val mmr = MediaMetadataRetriever()
                                runCatching {
                                    resolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                        mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                    }
                                    val mmrMime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase(Locale.ROOT)
                                    if (mmrMime == "audio/alac") {
                                        trackMime = "audio/alac"
                                    }
                                }.also { runCatching { mmr.release() } }
                            }
                        }
                        if (trackMime == "audio/raw") {
                            val mimeL = song.mimeType?.lowercase(Locale.ROOT)
                            val isFlac = song.path.endsWith(".flac", true) ||
                                mimeL == "audio/flac" || mimeL == "audio/x-flac"
                            if (isFlac) {
                                trackMime = "audio/flac"
                            }
                        }
                        return@runCatching trackMime
                    }
                }
                null
            } finally {
                runCatching { extractor.release() }
            }
        }.getOrNull()
        
        extractorMimeCache[song.id] = result ?: MIME_NONE
        return result
    }

    private fun detectIsoBmffAudioCodec(song: Song): String? {
        return readAudioSignature(
            song = song,
            maxBytes = ISO_BMFF_CODEC_PROBE_BYTES
        )?.let(IsoBmffAudioCodecDetector::detectAudioCodec)
    }

    private fun isMimeTypeDecoderSupported(mimeType: String): Boolean {
        return runCatching {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (info in list.codecInfos) {
                if (info.isEncoder) continue
                val types = runCatching { info.supportedTypes }.getOrNull() ?: continue
                if (types.any { it.equals(mimeType, ignoreCase = true) }) return true
            }
            false
        }.getOrDefault(false)
    }

    /**
     * Returns true if an ALAC MediaCodec decoder that supports this song's exact format
     * (sample rate + channel count) is available on this device.
     *
     * NOTE: QTI/Qualcomm ALAC decoders are NOT excluded here. Those decoders are only
     * problematic for direct audio playback routing; the HTTP server uses them solely as
     * a decode source (decode → PCM → AAC), so they are safe for transcoding.  Excluding
     * them here would cause CastPlayer to announce audio/mp4 while the server transcodes
     * to audio/aac, producing a MIME mismatch that makes Cast reject the item.
     */
    /**
     * Checks if a working ALAC decoder is available.
     * At this point, CastPlayer has already determined the track is ALAC via probe (detectAudioMimeTypeViaExtractor),
     * so it just needs to confirm decoder existence.
     */
    private fun isAlacTranscodeSupported(): Boolean {
        if (isFfmpegAlacTranscodeSupported()) return true
        return isMimeTypeDecoderSupported("audio/alac")
    }

    /**
     * Checks if a working FLAC decoder is available.
     */
    private fun isFlacTranscodeSupported(): Boolean {
        return isMimeTypeDecoderSupported("audio/flac")
    }

    private fun isFfmpegAlacTranscodeSupported(): Boolean {
        return runCatching {
            FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_ALAC)
        }.getOrDefault(false)
    }

    private fun detectAudioMimeTypeViaMetadataRetriever(song: Song): String? {
        val cached = retrieverMimeCache[song.id]
        if (cached != null) {
            return if (cached == MIME_NONE) null else cached
        }
        val resolver = contentResolver ?: return null
        val uri = song.contentUriString.toUri()
        val retriever = MediaMetadataRetriever()
        val result = runCatching {
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
        retrieverMimeCache[song.id] = result ?: MIME_NONE
        return result
    }

    private fun logQueueDiagnostics(
        songs: List<Song>,
        startIndex: Int,
        serverAddress: String,
        authToken: String?,
        queueLoadNonce: String,
        forcedMimeBySongId: Map<String, String> = emptyMap()
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
                // Use the forced MIME that was actually sent to the Cast queue; fall back to
                // the container-level resolution only for diagnostic display.
                val sentMime = forcedMimeBySongId[song.id] ?: song.resolveCastContentType()
                val streamRevision = song.buildCastStreamRevision(sentMime, queueLoadNonce)
                val likelySupported = CastAudioMimeUtils.baseMimeType(sentMime) in setOf(
                    "audio/mpeg",
                    "audio/aac",
                    "audio/mp4",
                    "audio/flac",
                    "audio/wav",
                    "audio/ogg",
                    "audio/webm",
                    "audio/amr"
                )
                val streamDurationSent = song.duration.coerceAtLeast(0L)
                    .takeIf { it > 0L }
                    ?: MediaInfo.UNKNOWN_DURATION
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
                    "queueItem[%d] id=%s mimeRaw=%s mimeSent=%s mimeForced=%s supported=%s durationHintMs=%d streamDurationSentMs=%d mediaUrl=%s artUrl=%s",
                    index,
                    song.id,
                    song.mimeType,
                    sentMime,
                    forcedMimeBySongId.containsKey(song.id),
                    likelySupported,
                    song.duration,
                    streamDurationSent,
                    mediaUrl,
                    artUrl
                )
                Log.i(
                    "PX_CAST_QLOAD",
                    "item index=$index songId=${song.id} mimeRaw=${song.mimeType} mimeSent=$sentMime mimeForced=${forcedMimeBySongId.containsKey(song.id)} durationHintMs=${song.duration.coerceAtLeast(0L)} streamDurationSentMs=$streamDurationSent"
                )
            }
    }
}
