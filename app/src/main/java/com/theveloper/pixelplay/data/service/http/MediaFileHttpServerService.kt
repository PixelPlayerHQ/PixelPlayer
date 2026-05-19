@file:Suppress("DEPRECATION")
package com.theveloper.pixelplay.data.service.http

import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.Decoder
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoderOutputBuffer
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.origin
import io.ktor.server.routing.routing
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondOutputStream
import io.ktor.server.routing.get
import io.ktor.server.routing.head
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.BindException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ClosedChannelException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.max

@AndroidEntryPoint
class MediaFileHttpServerService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    @Volatile
    private var startInProgress = false
    private val serverStartLock = Any()
    @Volatile
    private var castDeviceIpHint: String? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val castHttpLogTag = "CastHttpServer"
    private val signatureMimeCache = mutableMapOf<String, String?>()
    // Cache for the actual codec info (codec MIME, sample rate, channels) to avoid re-probing.
    private val codecInfoCache = mutableMapOf<String, AudioCodecInfo?>()
    private val httpDateFormatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)

    // -------------------------------------------------------------------------
    // Transcode temp-file cache
    //
    // Transcoded (ALAC→AAC, FLAC→AAC) streams are written to a per-song temp
    // file so that Cast can issue byte-range requests (seeks) against a file
    // with a known Content-Length.  The state machine per song id:
    //
    //   null                  → not yet started, first request initiates transcode
    //   TranscodeEntry(done=false, latch) → transcode in-progress, subsequent
    //                           requestors wait on latch before reading
    //   TranscodeEntry(done=true)         → temp file complete, serve directly
    // -------------------------------------------------------------------------
    private data class TranscodeEntry(
        val tempFile: File,
        /** True once the transcode goroutine has written the last byte and closed the file. */
        @Volatile var done: Boolean = false,
        /** True when transcode failed (temp file may be partial). */
        @Volatile var failed: Boolean = false,
        /** Other requests wait on this until done|failed is set. */
        val latch: CountDownLatch = CountDownLatch(1)
    )
    private val transcodeCache = ConcurrentHashMap<String, TranscodeEntry>()

    private val transparentPng1x1: ByteArray by lazy {
        byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, 0xC4.toByte(),
            0x89.toByte(), 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, 0x9C.toByte(), 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, 0xB4.toByte(), 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(),
            0x42, 0x60, 0x82.toByte()
        )
    }

    companion object {
        const val ACTION_START_SERVER = "ACTION_START_SERVER"
        const val ACTION_STOP_SERVER = "ACTION_STOP_SERVER"
        const val EXTRA_CAST_DEVICE_IP = "EXTRA_CAST_DEVICE_IP"
        @Volatile
        var isServerRunning = false
        @Volatile
        var isServerStarting = false
        @Volatile
        var serverAddress: String? = null
        @Volatile
        var serverHostAddress: String? = null
        @Volatile
        var serverPrefixLength: Int = -1
        @Volatile
        var lastFailureReason: FailureReason? = null
        @Volatile
        var lastFailureMessage: String? = null
        @Volatile
        private var castAccessPolicy: CastAccessPolicy = CastAccessPolicy.EMPTY
        private const val SERVER_START_PORT_RETRY_LIMIT = 3

        internal fun configureCastSessionAccess(
            allowedSongIds: Collection<String>,
            castDeviceIpHint: String?
        ): CastAccessPolicy {
            val updatedPolicy = CastSessionSecurity.buildAccessPolicy(
                existingToken = castAccessPolicy.authToken,
                allowedSongIds = allowedSongIds,
                castDeviceIpHint = castDeviceIpHint,
                // Always whitelist the server's own LAN IP so that on-device services
                // (widget updates, notification art) that connect to http://serverIp:PORT/...
                // via the LAN interface are authorized, even when the allowlist is enforced.
                serverOwnIp = serverHostAddress
            )
            castAccessPolicy = updatedPolicy
            return updatedPolicy
        }

        internal fun currentCastAccessPolicy(): CastAccessPolicy = castAccessPolicy

        internal fun clearCastSessionAccess() {
            castAccessPolicy = CastAccessPolicy.EMPTY
        }
    }

    enum class FailureReason {
        NO_NETWORK_ADDRESS,
        FOREGROUND_START_EXCEPTION,
        START_EXCEPTION
    }

    private data class AudioStreamSource(
        val sourceLabel: String,
        val fileSize: Long,
        val lastModifiedEpochMs: Long?,
        val inputStreamFactory: () -> InputStream
    )

    private data class AudioCodecInfo(
        val codecMime: String,
        val sampleRate: Int,
        val channelCount: Int,
        val trackIndex: Int
    )

    private class CountingOutputStream(
        private val delegate: OutputStream
    ) : OutputStream() {
        var bytesWritten: Long = 0L
            private set

        override fun write(b: Int) {
            delegate.write(b)
            bytesWritten += 1L
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            delegate.write(b, off, len)
            bytesWritten += len.toLong()
        }

        override fun flush() {
            delegate.flush()
        }
    }

    private data class ArtStreamSource(
        val sourceLabel: String,
        val contentType: ContentType,
        val contentLength: Long?,
        val lastModifiedEpochMs: Long?,
        val inputStreamFactory: () -> InputStream
    )

    private data class LocalAddressCandidate(
        val hostAddress: String,
        val address: Inet4Address,
        val prefixLength: Int,
        val isActiveNetwork: Boolean,
        val isValidated: Boolean,
        val hasInternet: Boolean
    )

    private data class AddressSelection(
        val hostAddress: String,
        val prefixLength: Int,
        val matchedCastSubnet: Boolean
    )

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_SERVER -> {
                castDeviceIpHint = null
                stopSelf()
            }
            ACTION_START_SERVER, null -> {
                castDeviceIpHint = intent
                    ?.getStringExtra(EXTRA_CAST_DEVICE_IP)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                // Ensure we are in foreground immediately if started this way.
                startForegroundService()
                startServer()
            }
            else -> {
                Timber.w("Ignoring unknown media server action: %s", intent.action)
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        runCatching {
            val channelId = "pixelplay_cast_server"
            val channelName = getString(R.string.cast_server_channel_name)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    channelName,
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                manager.createNotificationChannel(channel)
            }

            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setContentTitle(getString(R.string.cast_server_notification_title))
                .setContentText(getString(R.string.cast_server_notification_text))
                .setSmallIcon(android.R.drawable.ic_menu_upload) // Placeholder, ideally use app icon
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .build()

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(
                    1002, 
                    notification, 
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(1002, notification)
            }
        }.onFailure { throwable ->
            lastFailureReason = FailureReason.FOREGROUND_START_EXCEPTION
            lastFailureMessage = getString(
                R.string.cast_server_foreground_error,
                throwable.javaClass.simpleName,
                throwable.message ?: getString(R.string.error_unknown),
            )
            Timber.e(throwable, "Failed to enter foreground mode for cast HTTP server")
            stopSelf()
        }
    }

    private fun startServer(retryAttempt: Int = 0) {
        synchronized(serverStartLock) {
            if (server?.application?.isActive == true || startInProgress) {
                return
            }
            startInProgress = true
            isServerStarting = true
        }

        serviceScope.launch {
            var shouldRetryAfterBindFailure = false
            try {
                lastFailureReason = null
                lastFailureMessage = null
                isServerRunning = false

                val addressSelection = selectIpAddress(
                    context = applicationContext,
                    castDeviceIpHint = castDeviceIpHint
                )
                if (addressSelection == null) {
                    Timber.w("No suitable IP address found; cannot start HTTP server")
                    lastFailureReason = FailureReason.NO_NETWORK_ADDRESS
                    lastFailureMessage = buildString {
                        append("No LAN IPv4 address available")
                        castDeviceIpHint?.let { append(" (castDeviceIp=$it)") }
                    }
                    stopSelf()
                    return@launch
                }
                val serverPort = resolveServerPort(preferredPort = 8080)
                if (serverPort != 8080) {
                    Timber.tag(castHttpLogTag).w(
                        "Port 8080 is already in use. Falling back to port %d for Cast HTTP server.",
                        serverPort
                    )
                }
                serverHostAddress = addressSelection.hostAddress
                serverPrefixLength = addressSelection.prefixLength
                serverAddress = "http://${addressSelection.hostAddress}:$serverPort"
                Timber.tag(castHttpLogTag).i(
                    "Selected cast server host=%s prefix=%d castHint=%s matchedCastSubnet=%s",
                    addressSelection.hostAddress,
                    addressSelection.prefixLength,
                    castDeviceIpHint,
                    addressSelection.matchedCastSubnet
                )
                lastFailureReason = null
                lastFailureMessage = null

                // Bind explicitly to the selected LAN interface instead of 0.0.0.0
                // so the server is not reachable on other interfaces (tethering
                // hotspot, VPN, etc.) that may carry untrusted clients.
                server = embeddedServer(CIO, port = serverPort, host = addressSelection.hostAddress) {
                    routing {
                            get("/health") {
                                if (!call.ensureLoopbackHealthRequest()) return@get
                                call.respond(HttpStatusCode.OK, "ok")
                            }
                            head("/health") {
                                if (!call.ensureLoopbackHealthRequest()) return@head
                                call.respond(HttpStatusCode.OK)
                            }
                            get("/song/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                                    return@get
                                }
                                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@get

                                val song = resolveSongForServing(songId)
                                if (song == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "GET /song unresolved songId=%s (repository+MediaStore fallback miss)",
                                        songId
                                    )
                                    Timber.tag("PX_CAST_HTTP")
                                        .e("GET /song unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound, "Song not found")
                                    return@get
                                }

                                try {
                                    val uri = song.contentUriString.toUri()
                                    val codecInfo = detectAudioCodecViaExtractor(song, uri)
                                    val isAlac = codecInfo?.codecMime == "audio/alac"
                                    // Only transcode if ALAC AND a working decoder is available.
                                    // On devices where c2.qti.alac.sw.decoder reports
                                    // "Unsupported mediaType audio/alac" in AudioCapabilities,
                                    // findDecoderForFormat returns null and we fall back to
                                    // serving the raw M4A file as audio/mp4 instead.
                                    val canTranscode = isAlac && isAlacTranscodeSupported(checkNotNull(codecInfo))

                                    if (canTranscode) {
                                        // ALAC codec inside M4A — Cast DMR cannot play ALAC.
                                        // Serve via temp-file transcode cache so Cast can seek.
                                        val c = checkNotNull(codecInfo)
                                        Timber.tag(castHttpLogTag).i(
                                            "GET /song ALAC→AAC transcode-cache songId=%s sr=%d ch=%d",
                                            song.id, c.sampleRate, c.channelCount
                                        )
                                        Timber.tag("PX_CAST_HTTP")
                                            .i("GET /song transcode_alac songId=${song.id} sr=${c.sampleRate} ch=${c.channelCount}")

                                        call.respondTranscodedWithCache(
                                            song = song, codecInfo = c, uri = uri,
                                            rangeHeader = call.request.headers[HttpHeaders.Range]
                                        )
                                        return@get
                                    }

                                    if (isAlac && !canTranscode) {
                                        // ALAC decoder unavailable on this device — serve the raw
                                        // M4A container. Newer Cast devices support ALAC natively.
                                        val c = checkNotNull(codecInfo)
                                        Timber.tag(castHttpLogTag).w(
                                            "GET /song ALAC decoder unavailable songId=%s sr=%d, serving raw M4A",
                                            song.id, c.sampleRate
                                        )
                                        Timber.tag("PX_CAST_HTTP")
                                            .w("GET /song alac_fallback_raw songId=${song.id} sr=${c.sampleRate}")

                                        val rangeHeader = call.request.headers[HttpHeaders.Range]
                                        val source = resolveAudioStreamSource(song, uri)
                                        if (source == null) {
                                            call.respond(HttpStatusCode.NotFound, "File not found")
                                            return@get
                                        }
                                        source.lastModifiedEpochMs?.let { lastModified ->
                                            call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                        }
                                        call.respondWithAudioStream(
                                            contentType = ContentType.parse("audio/mp4"),
                                            fileSize = source.fileSize,
                                            rangeHeader = rangeHeader
                                        ) { source.inputStreamFactory() }
                                        return@get
                                    }

                                    // FLAC: Cast DMR supports FLAC natively but its duration
                                    // estimate is wrong (VBR + missing seektable → reported duration
                                    // differs from actual), so seeking overshoots and lands near EOF
                                    // causing an involuntary track skip. Transcode to AAC-ADTS so
                                    // Cast gets a CBR stream it can seek accurately.
                                    val isFlac = codecInfo?.codecMime == "audio/flac"
                                    if (isFlac && isFlacTranscodeSupported(codecInfo)) {
                                        Timber.tag(castHttpLogTag).i(
                                            "GET /song FLAC→AAC transcode-cache songId=%s sr=%d ch=%d",
                                            song.id, codecInfo.sampleRate, codecInfo.channelCount
                                        )
                                        Timber.tag("PX_CAST_HTTP")
                                            .i("GET /song transcode_flac songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                                        call.respondTranscodedWithCache(
                                            song = song, codecInfo = codecInfo, uri = uri,
                                            rangeHeader = call.request.headers[HttpHeaders.Range]
                                        )
                                        return@get
                                    }

                                    // AC3/EAC3: Cast DMR cannot play Dolby audio. Transcode to AAC
                                    // when a decoder is available (Snapdragon Dolby decoder).
                                    val isAc3 = codecInfo?.codecMime == "audio/ac3" || codecInfo?.codecMime == "audio/eac3"
                                    if (isAc3 && isAc3TranscodeSupported(codecInfo)) {
                                        Timber.tag(castHttpLogTag).i(
                                            "GET /song AC3/EAC3→AAC transcode-cache songId=%s sr=%d ch=%d",
                                            song.id, codecInfo.sampleRate, codecInfo.channelCount
                                        )
                                        Timber.tag("PX_CAST_HTTP")
                                            .i("GET /song transcode_ac3 songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                                        call.respondTranscodedWithCache(
                                            song = song, codecInfo = codecInfo, uri = uri,
                                            rangeHeader = call.request.headers[HttpHeaders.Range]
                                        )
                                        return@get
                                    }

                                    val contentType = resolveAudioContentType(resolvePreferredAudioMimeType(song, uri))
                                    val rangeHeader = call.request.headers[HttpHeaders.Range]
                                    val source = resolveAudioStreamSource(song, uri)

                                    if (source == null) {
                                        Timber.tag(castHttpLogTag).w(
                                            "GET /song failed to resolve source. songId=%s uri=%s path=%s mime=%s",
                                            song.id,
                                            song.contentUriString,
                                            song.path,
                                            song.mimeType
                                        )
                                        call.respond(HttpStatusCode.NotFound, "File not found")
                                        return@get
                                    }

                                    Timber.tag(castHttpLogTag).i(
                                        "GET /song songId=%s source=%s range=%s size=%d type=%s",
                                        song.id,
                                        source.sourceLabel,
                                        rangeHeader,
                                        source.fileSize,
                                        contentType
                                    )
                                    Timber.tag("PX_CAST_HTTP")
                                        .i("GET /song songId=${song.id} source=${source.sourceLabel} range=$rangeHeader size=${source.fileSize} type=$contentType")
                                    source.lastModifiedEpochMs?.let { lastModified ->
                                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                    }

                                    call.respondWithAudioStream(
                                        contentType = contentType,
                                        fileSize = source.fileSize,
                                        rangeHeader = rangeHeader
                                    ) {
                                        source.inputStreamFactory()
                                    }
                                } catch (e: Exception) {
                                    if (e.isClientAbortDuringResponse()) {
                                        Timber.tag(castHttpLogTag).d(
                                            "GET /song client disconnected. songId=%s uri=%s",
                                            song.id,
                                            song.contentUriString
                                        )
                                        Timber.tag("PX_CAST_HTTP")
                                            .w("GET /song client_closed songId=${song.id} uri=${song.contentUriString} error=${e.javaClass.simpleName}")
                                        return@get
                                    }
                                    Timber.tag(castHttpLogTag).e(
                                        e,
                                        "GET /song exception. songId=%s uri=%s",
                                        song.id,
                                        song.contentUriString
                                    )
                                    Timber.tag("PX_CAST_HTTP").e(
                                        e,
                                        "GET /song exception songId=${song.id} uri=${song.contentUriString}"
                                    )
                                    call.respond(HttpStatusCode.InternalServerError, "Error serving file: ${e.message}")
                                }
                            }
                            head("/song/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest)
                                    return@head
                                }
                                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@head

                                val song = resolveSongForServing(songId)
                                if (song == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "HEAD /song unresolved songId=%s (repository+MediaStore fallback miss)",
                                        songId
                                    )
                                    Timber.tag("PX_CAST_HTTP")
                                        .e("HEAD /song unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound)
                                    return@head
                                }

                                try {
                                    val uri = song.contentUriString.toUri()
                                    val codecInfo = detectAudioCodecViaExtractor(song, uri)
                                    val isAlac = codecInfo?.codecMime == "audio/alac"
                                    val canTranscode = isAlac && isAlacTranscodeSupported(checkNotNull(codecInfo))

                                    if (canTranscode) {
                                        // ALAC: serve Content-Length from cache if temp file is ready.
                                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                                        val entry = transcodeCache[song.id]
                                        if (entry != null && entry.done && entry.tempFile.exists()) {
                                            val tempSize = entry.tempFile.length()
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                                            Timber.tag(castHttpLogTag).d("HEAD /song ALAC songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                                        } else {
                                            Timber.tag(castHttpLogTag).d("HEAD /song ALAC songId=%s -> audio/aac (no cache yet)", song.id)
                                        }
                                        call.respond(HttpStatusCode.OK)
                                        return@head
                                    }

                                    if (isAlac && !canTranscode) {
                                        // ALAC decoder unavailable — raw M4A fallback
                                        call.response.header(HttpHeaders.ContentType, "audio/mp4")
                                        Timber.tag(castHttpLogTag).d("HEAD /song ALAC fallback songId=%s -> audio/mp4 (decoder unavailable)", song.id)
                                        call.respond(HttpStatusCode.OK)
                                        return@head
                                    }

                                    // FLAC: transcoded to AAC-ADTS for reliable Cast seeking.
                                    val isFlac = codecInfo?.codecMime == "audio/flac"
                                    if (isFlac && isFlacTranscodeSupported(codecInfo)) {
                                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                                        val entry = transcodeCache[song.id]
                                        if (entry != null && entry.done && entry.tempFile.exists()) {
                                            val tempSize = entry.tempFile.length()
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                                            Timber.tag(castHttpLogTag).d("HEAD /song FLAC songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                                        } else {
                                            Timber.tag(castHttpLogTag).d("HEAD /song FLAC songId=%s -> audio/aac (no cache yet)", song.id)
                                        }
                                        call.respond(HttpStatusCode.OK)
                                        return@head
                                    }

                                    // AC3/EAC3: transcoded to AAC for Cast.
                                    val isAc3 = codecInfo?.codecMime == "audio/ac3" || codecInfo?.codecMime == "audio/eac3"
                                    if (isAc3 && isAc3TranscodeSupported(codecInfo)) {
                                        call.response.header(HttpHeaders.ContentType, "audio/aac")
                                        val entry = transcodeCache[song.id]
                                        if (entry != null && entry.done && entry.tempFile.exists()) {
                                            val tempSize = entry.tempFile.length()
                                            call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                            call.response.header(HttpHeaders.ContentLength, tempSize.toString())
                                            Timber.tag(castHttpLogTag).d("HEAD /song AC3 songId=%s -> audio/aac size=%d (cached)", song.id, tempSize)
                                        } else {
                                            Timber.tag(castHttpLogTag).d("HEAD /song AC3 songId=%s -> audio/aac (no cache yet)", song.id)
                                        }
                                        call.respond(HttpStatusCode.OK)
                                        return@head
                                    }

                                    val contentType = resolveAudioContentType(resolvePreferredAudioMimeType(song, uri))
                                    val source = resolveAudioStreamSource(song, uri)

                                    if (source == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "HEAD /song failed to resolve source. songId=%s uri=%s path=%s",
                                        song.id,
                                        song.contentUriString,
                                        song.path
                                    )
                                        Timber.tag("PX_CAST_HTTP")
                                            .w("HEAD /song no source songId=${song.id} uri=${song.contentUriString}")
                                    call.respond(HttpStatusCode.NotFound)
                                    return@head
                                }

                                    call.response.header(HttpHeaders.ContentType, contentType.toString())
                                    if (source.fileSize > 0) {
                                        call.response.header(HttpHeaders.AcceptRanges, "bytes")
                                        call.response.header(HttpHeaders.ContentLength, source.fileSize.toString())
                                    }
                                    source.lastModifiedEpochMs?.let { lastModified ->
                                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                    }
                                    Timber.tag(castHttpLogTag).d(
                                        "HEAD /song songId=%s source=%s size=%d type=%s",
                                        song.id,
                                        source.sourceLabel,
                                        source.fileSize,
                                        contentType
                                    )
                                    call.respond(HttpStatusCode.OK)
                                } catch (e: Exception) {
                                    if (e.isClientAbortDuringResponse()) {
                                        Timber.tag(castHttpLogTag).d("HEAD /song client disconnected. songId=%s", song.id)
                                        Timber.tag("PX_CAST_HTTP")
                                            .w("HEAD /song client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@head
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "HEAD /song exception. songId=%s", song.id)
                                    Timber.tag("PX_CAST_HTTP")
                                        .e(e, "HEAD /song exception songId=${song.id}")
                                    call.respond(HttpStatusCode.InternalServerError)
                                }
                            }
                            get("/art/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest, "Song ID is missing")
                                    return@get
                                }
                                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@get

                                val song = resolveSongForServing(songId)
                                if (song == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "GET /art unresolved songId=%s (repository+MediaStore fallback miss)",
                                        songId
                                    )
                                    Timber.tag("PX_CAST_HTTP")
                                        .e("GET /art unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound, "Song not found")
                                    return@get
                                }

                                try {
                                    val artSource = resolveArtStreamSource(song)
                                    Timber.tag(castHttpLogTag).i(
                                        "GET /art songId=%s source=%s length=%s type=%s",
                                        song.id,
                                        artSource.sourceLabel,
                                        artSource.contentLength,
                                        artSource.contentType
                                    )
                                    Timber.tag("PX_CAST_HTTP")
                                        .i("GET /art songId=${song.id} source=${artSource.sourceLabel} len=${artSource.contentLength} type=${artSource.contentType}")
                                    artSource.lastModifiedEpochMs?.let { lastModified ->
                                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                    }
                                    call.respondOutputStream(artSource.contentType) {
                                        artSource.inputStreamFactory().use { inputStream ->
                                            inputStream.copyTo(this)
                                        }
                                    }
                                } catch (e: Exception) {
                                    if (e.isClientAbortDuringResponse()) {
                                        Timber.tag(castHttpLogTag).d("GET /art client disconnected. songId=%s", song.id)
                                        Timber.tag("PX_CAST_HTTP").w("GET /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@get
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "GET /art exception. songId=%s", song.id)
                                    Timber.tag("PX_CAST_HTTP").e(e, "GET /art exception songId=${song.id}")
                                    val fallbackSource = placeholderArtSource()
                                    call.respondOutputStream(fallbackSource.contentType) {
                                        fallbackSource.inputStreamFactory().use { inputStream ->
                                            inputStream.copyTo(this)
                                        }
                                    }
                                }
                            }
                            head("/art/{songId}") {
                                val songId = call.parameters["songId"]
                                if (songId == null) {
                                    call.respond(HttpStatusCode.BadRequest)
                                    return@head
                                }
                                if (!call.ensureAuthorizedCastMediaRequest(songId)) return@head

                                val song = resolveSongForServing(songId)
                                if (song == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "HEAD /art unresolved songId=%s (repository+MediaStore fallback miss)",
                                        songId
                                    )
                                    Timber.tag("PX_CAST_HTTP").e("HEAD /art unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound)
                                    return@head
                                }

                                try {
                                    val artSource = resolveArtStreamSource(song)
                                    call.response.header(HttpHeaders.ContentType, artSource.contentType.toString())
                                    if (artSource.contentLength != null && artSource.contentLength > 0L) {
                                        call.response.header(HttpHeaders.ContentLength, artSource.contentLength.toString())
                                    }
                                    artSource.lastModifiedEpochMs?.let { lastModified ->
                                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                    }
                                    Timber.tag(castHttpLogTag).d(
                                        "HEAD /art songId=%s source=%s length=%s type=%s",
                                        song.id,
                                        artSource.sourceLabel,
                                        artSource.contentLength,
                                        artSource.contentType
                                    )
                                    call.respond(HttpStatusCode.OK)
                                } catch (e: Exception) {
                                    if (e.isClientAbortDuringResponse()) {
                                        Timber.tag(castHttpLogTag).d("HEAD /art client disconnected. songId=%s", song.id)
                                        Timber.tag("PX_CAST_HTTP").w("HEAD /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@head
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "HEAD /art exception. songId=%s", song.id)
                                    Timber.tag("PX_CAST_HTTP").e(e, "HEAD /art exception songId=${song.id}")
                                    val fallbackSource = placeholderArtSource()
                                    call.response.header(HttpHeaders.ContentType, fallbackSource.contentType.toString())
                                    fallbackSource.contentLength?.let { length ->
                                        call.response.header(HttpHeaders.ContentLength, length.toString())
                                    }
                                    fallbackSource.lastModifiedEpochMs?.let { lastModified ->
                                        call.response.header(HttpHeaders.LastModified, formatHttpDate(lastModified))
                                    }
                                    call.respond(HttpStatusCode.OK)
                                }
                            }
                        }
                }.start(wait = false)
                isServerRunning = true
            } catch (e: Exception) {
                isServerRunning = false
                serverAddress = null
                serverHostAddress = null
                serverPrefixLength = -1
                if (e.isAddressAlreadyInUse() && retryAttempt < SERVER_START_PORT_RETRY_LIMIT) {
                    shouldRetryAfterBindFailure = true
                    Timber.tag(castHttpLogTag).w(
                        e,
                        "Cast HTTP server port bind failed; retrying startup (%d/%d).",
                        retryAttempt + 1,
                        SERVER_START_PORT_RETRY_LIMIT
                    )
                } else {
                    Timber.e(e, "Failed to start HTTP cast server")
                    lastFailureReason = FailureReason.START_EXCEPTION
                    lastFailureMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}"
                    stopSelf()
                }
            } finally {
                synchronized(serverStartLock) {
                    startInProgress = false
                    isServerStarting = false
                }
            }

            if (shouldRetryAfterBindFailure) {
                startServer(retryAttempt + 1)
            }
        }
    }

    private fun resolveServerPort(preferredPort: Int): Int {
        val startPort = preferredPort.coerceIn(1, 65535)
        val candidatePorts = sequence {
            yield(startPort)
            val upperBound = (startPort + 20).coerceAtMost(65535)
            for (port in (startPort + 1)..upperBound) {
                yield(port)
            }
        }

        candidatePorts.firstOrNull { isPortAvailable(it) }?.let { return it }

        return runCatching {
            ServerSocket(0).use { socket ->
                max(socket.localPort, 1)
            }
        }.getOrDefault(startPort)
    }

    private fun isPortAvailable(port: Int): Boolean {
        if (port !in 1..65535) return false
        return runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = true
                socket.bind(InetSocketAddress("0.0.0.0", port))
                true
            }
        }.getOrDefault(false)
    }

    private fun Throwable.isAddressAlreadyInUse(): Boolean {
        return generateSequence(this) { it.cause }.any { cause ->
            cause is BindException ||
                cause.message?.contains("Address already in use", ignoreCase = true) == true ||
                cause.message?.contains("EADDRINUSE", ignoreCase = true) == true
        }
    }

    @Suppress("DEPRECATION")
    private fun selectIpAddress(context: Context, castDeviceIpHint: String?): AddressSelection? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork

        val candidates = mutableListOf<LocalAddressCandidate>()
        for (network in connectivityManager.allNetworks) {
            val caps = connectivityManager.getNetworkCapabilities(network) ?: continue
            if (!caps.isLocalLanTransport()) continue
            val linkProps = connectivityManager.getLinkProperties(network) ?: continue
            val isActiveNetwork = network == activeNetwork

            for (linkAddress in linkProps.linkAddresses) {
                val ipv4 = linkAddress.address as? Inet4Address ?: continue
                if (ipv4.isLoopbackAddress || ipv4.isLinkLocalAddress) continue
                val hostAddress = ipv4.hostAddress ?: continue
                candidates += LocalAddressCandidate(
                    hostAddress = hostAddress,
                    address = ipv4,
                    prefixLength = linkAddress.prefixLength.coerceIn(0, 32),
                    isActiveNetwork = isActiveNetwork,
                    isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
                    hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                )
            }
        }

        if (candidates.isEmpty()) return null

        val castAddress = parseIpv4Address(castDeviceIpHint)
        if (castAddress != null) {
            val subnetMatches = candidates
                .filter { candidate ->
                    isSameSubnet(candidate.address, castAddress, candidate.prefixLength)
                }
                .sortedByBestCandidate()
            if (subnetMatches.isNotEmpty()) {
                val selected = subnetMatches.first()
                return AddressSelection(
                    hostAddress = selected.hostAddress,
                    prefixLength = selected.prefixLength,
                    matchedCastSubnet = true
                )
            }
            Timber.tag(castHttpLogTag).w(
                "No LAN interface matched Cast subnet for castDeviceIp=%s; falling back to best LAN interface.",
                castDeviceIpHint
            )
        }

        val selected = candidates
            .sortedByBestCandidate()
            .firstOrNull()
            ?: return null

        return AddressSelection(
            hostAddress = selected.hostAddress,
            prefixLength = selected.prefixLength,
            matchedCastSubnet = false
        )
    }

    private fun List<LocalAddressCandidate>.sortedByBestCandidate(): List<LocalAddressCandidate> {
        return sortedWith(
            compareByDescending<LocalAddressCandidate> { it.isActiveNetwork }
                .thenByDescending { it.isValidated }
                .thenByDescending { it.hasInternet }
                .thenByDescending { it.prefixLength }
                .thenByDescending { it.hostAddress }
        )
    }

    private fun parseIpv4Address(rawAddress: String?): Inet4Address? {
        val normalized = rawAddress?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = runCatching { InetAddress.getByName(normalized) }.getOrNull() ?: return null
        return parsed as? Inet4Address
    }

    private fun isSameSubnet(localAddress: Inet4Address, remoteAddress: Inet4Address, prefixLength: Int): Boolean {
        val clampedPrefix = prefixLength.coerceIn(0, 32)
        if (clampedPrefix == 0) return true
        val localInt = localAddress.toIntAddress()
        val remoteInt = remoteAddress.toIntAddress()
        val mask = if (clampedPrefix == 32) {
            -1
        } else {
            (-1 shl (32 - clampedPrefix))
        }
        return (localInt and mask) == (remoteInt and mask)
    }

    private fun Inet4Address.toIntAddress(): Int {
        val bytes = address
        return ((bytes[0].toInt() and 0xFF) shl 24) or
            ((bytes[1].toInt() and 0xFF) shl 16) or
            ((bytes[2].toInt() and 0xFF) shl 8) or
            (bytes[3].toInt() and 0xFF)
    }

    private fun NetworkCapabilities.isLocalLanTransport(): Boolean {
        return hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private suspend fun ApplicationCall.ensureLoopbackHealthRequest(): Boolean {
        val remoteAddress = request.origin.remoteHost
        if (CastSessionSecurity.isLoopbackAddress(remoteAddress)) {
            return true
        }
        Timber.tag(castHttpLogTag).w("Rejected Cast health request from non-loopback client=%s", remoteAddress)
        respond(HttpStatusCode.Forbidden, "Forbidden")
        return false
    }

    private suspend fun ApplicationCall.ensureAuthorizedCastMediaRequest(songId: String): Boolean {
        val remoteAddress = request.origin.remoteHost
        val policy = currentCastAccessPolicy()

        if (!CastSessionSecurity.isAuthorizedClientAddress(remoteAddress, policy)) {
            Timber.tag(castHttpLogTag).w(
                "Rejected Cast media request from unauthorized client=%s songId=%s",
                remoteAddress,
                songId
            )
            respond(HttpStatusCode.Forbidden, "Forbidden")
            return false
        }

        val providedToken = request.queryParameters[CastSessionSecurity.AUTH_QUERY_PARAMETER]
        if (!CastSessionSecurity.isAuthorizedSongRequest(providedToken, songId, policy)) {
            val hasValidToken = !policy.authToken.isNullOrBlank() && providedToken == policy.authToken
            if (!hasValidToken) {
                Timber.tag(castHttpLogTag).w(
                    "Rejected Cast media request with invalid token client=%s songId=%s",
                    remoteAddress,
                    songId
                )
                respond(HttpStatusCode.Unauthorized, "Unauthorized")
            } else {
                Timber.tag(castHttpLogTag).w(
                    "Rejected Cast media request for non-whitelisted song client=%s songId=%s",
                    remoteAddress,
                    songId
                )
                respond(HttpStatusCode.NotFound, "Song not found")
            }
            return false
        }

        return true
    }

    private fun resolveAudioStreamSource(song: Song, uri: Uri): AudioStreamSource? {
        val fallbackFile = song.path
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.exists() && it.isFile && it.canRead() }
        val sizeFromProvider = queryContentLengthFromProvider(uri)
        val songLastModified = resolveSongLastModifiedEpochMs(song, fallbackFile)

        var hasAssetDescriptor = false
        var assetDescriptorSize = -1L
        runCatching {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                hasAssetDescriptor = true
                assetDescriptorSize = afd.length
                    .takeIf { it > 0L }
                    ?: afd.declaredLength.takeIf { it > 0L }
                    ?: -1L
            }
        }.onFailure { throwable ->
            Timber.tag(castHttpLogTag).d(throwable, "openAssetFileDescriptor failed. songId=%s", song.id)
        }
        if (hasAssetDescriptor) {
            val resolvedSize = when {
                assetDescriptorSize > 0L -> assetDescriptorSize
                sizeFromProvider > 0L -> sizeFromProvider
                fallbackFile != null -> fallbackFile.length()
                else -> -1L
            }
            return AudioStreamSource(
                sourceLabel = "asset_fd",
                fileSize = resolvedSize,
                lastModifiedEpochMs = songLastModified
            ) {
                contentResolver.openAssetFileDescriptor(uri, "r")?.createInputStream()
                    ?: throw IllegalStateException("AssetFileDescriptor unavailable for uri=$uri songId=${song.id}")
            }
        }

        var hasFileDescriptor = false
        var fdSize = -1L
        runCatching {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                hasFileDescriptor = true
                fdSize = pfd.statSize.takeIf { it > 0L } ?: -1L
            }
        }.onFailure { throwable ->
            Timber.tag(castHttpLogTag).d(throwable, "openFileDescriptor failed. songId=%s", song.id)
        }
        if (hasFileDescriptor) {
            val resolvedSize = when {
                fdSize > 0L -> fdSize
                sizeFromProvider > 0L -> sizeFromProvider
                fallbackFile != null -> fallbackFile.length()
                else -> -1L
            }
            return AudioStreamSource(
                sourceLabel = "parcel_fd",
                fileSize = resolvedSize,
                lastModifiedEpochMs = songLastModified
            ) {
                val pfd = contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalStateException("ParcelFileDescriptor unavailable for uri=$uri songId=${song.id}")
                ParcelFileDescriptor.AutoCloseInputStream(pfd)
            }
        }

        val hasInputStream = runCatching {
            contentResolver.openInputStream(uri)?.use { true } ?: false
        }.onFailure { throwable ->
            Timber.tag(castHttpLogTag).d(throwable, "openInputStream probe failed. songId=%s", song.id)
        }.getOrDefault(false)
        if (hasInputStream) {
            return AudioStreamSource(
                sourceLabel = "content_stream",
                fileSize = when {
                    sizeFromProvider > 0L -> sizeFromProvider
                    fallbackFile != null -> fallbackFile.length()
                    else -> -1L
                },
                lastModifiedEpochMs = songLastModified
            ) {
                contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("InputStream unavailable for uri=$uri songId=${song.id}")
            }
        }

        if (fallbackFile != null) {
            return AudioStreamSource(
                sourceLabel = "file_path",
                fileSize = fallbackFile.length(),
                lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, fallbackFile)
            ) {
                FileInputStream(fallbackFile)
            }
        }
        return null
    }

    private suspend fun resolveSongForServing(songId: String): Song? {
        val repositorySong = musicRepository.getSong(songId).firstOrNull()
        if (repositorySong != null) {
            return repositorySong
        }

        val id = songId.toLongOrNull() ?: return null
        Timber.tag(castHttpLogTag).w(
            "Song not found in repository. Falling back to MediaStore query for songId=%s",
            songId
        )
        Timber.tag("PX_CAST_HTTP").w("song_resolver repo_miss songId=$songId")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val selectionArgs = arrayOf(id.toString())

        return runCatching {
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }

                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val artistIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeTypeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)
                val albumArtistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

                val songIdLong = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                val path = cursor.getString(dataCol).orEmpty()
                val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songIdLong)
                val albumArtUri = AlbumArtUtils.getAlbumArtUri(
                    appContext = this,
                    path = path,
                    songId = songIdLong,
                    forceRefresh = false
                )

                Song(
                    id = songIdLong.toString(),
                    title = cursor.getString(titleCol).orEmpty(),
                    artist = cursor.getString(artistCol).orEmpty(),
                    artistId = cursor.getLong(artistIdCol),
                    album = cursor.getString(albumCol).orEmpty(),
                    albumId = albumId,
                    albumArtist = if (albumArtistCol >= 0) cursor.getString(albumArtistCol) else null,
                    path = path,
                    contentUriString = contentUri.toString(),
                    albumArtUriString = albumArtUri,
                    duration = cursor.getLong(durationCol),
                    trackNumber = cursor.getInt(trackCol),
                    year = cursor.getInt(yearCol),
                    dateAdded = cursor.getLong(dateAddedCol),
                    dateModified = cursor.getLong(dateModifiedCol),
                    mimeType = if (mimeTypeCol >= 0) cursor.getString(mimeTypeCol) else null,
                    bitrate = null,
                    sampleRate = null
                )
            }
        }.onSuccess { resolved ->
            if (resolved != null) {
                Timber.tag("PX_CAST_HTTP")
                    .i("song_resolver media_store_hit songId=$songId mime=${resolved.mimeType} path=${resolved.path}")
            } else {
                Timber.tag("PX_CAST_HTTP").e("song_resolver media_store_miss songId=$songId")
            }
        }.onFailure { throwable ->
            Timber.tag(castHttpLogTag).e(throwable, "MediaStore fallback failed for songId=%s", songId)
            Timber.tag("PX_CAST_HTTP")
                .e(throwable, "song_resolver media_store_error songId=$songId")
        }.getOrNull()
    }

    private fun resolveArtStreamSource(song: Song): ArtStreamSource {
        val albumArtUri = song.albumArtUriString
            ?.takeIf { it.isNotBlank() }
            ?.toUri()

        if (albumArtUri != null) {
            val uriMimeType = runCatching { contentResolver.getType(albumArtUri) }.getOrNull()
            val contentType = resolveImageContentType(
                mimeType = uriMimeType,
                uriPath = albumArtUri.path
            )
            val contentLength = runCatching {
                contentResolver.openAssetFileDescriptor(albumArtUri, "r")?.use { afd ->
                    afd.length.takeIf { it > 0L } ?: afd.declaredLength.takeIf { it > 0L }
                }
            }.getOrNull()
            val canOpenStream = runCatching {
                contentResolver.openInputStream(albumArtUri)?.use { true } ?: false
            }.onFailure { throwable ->
                if (throwable is java.io.FileNotFoundException && throwable.message?.contains("No content provider") == true) {
                    Timber.tag(castHttpLogTag).d("Album art URI probe expected fail (No content provider). songId=%s", song.id)
                } else {
                    Timber.tag(castHttpLogTag).d(throwable, "Album art URI probe failed. songId=%s", song.id)
                }
            }.getOrDefault(false)

            if (canOpenStream) {
                return ArtStreamSource(
                    sourceLabel = "album_art_uri",
                    contentType = contentType,
                    contentLength = contentLength,
                    lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, null)
                ) {
                    contentResolver.openInputStream(albumArtUri)
                        ?: throw IllegalStateException("Unable to open albumArt uri=$albumArtUri songId=${song.id}")
                }
            }
        }

        val embeddedArt = extractEmbeddedAlbumArt(song)
        if (embeddedArt != null && embeddedArt.isNotEmpty()) {
            return ArtStreamSource(
                sourceLabel = "embedded_picture",
                contentType = resolveEmbeddedImageContentType(embeddedArt),
                contentLength = embeddedArt.size.toLong(),
                lastModifiedEpochMs = resolveSongLastModifiedEpochMs(song, null)
            ) {
                ByteArrayInputStream(embeddedArt)
            }
        }

        return placeholderArtSource()
    }

    private fun extractEmbeddedAlbumArt(song: Song): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = song.contentUriString.toUri()
            val sourceConfigured = runCatching {
                retriever.setDataSource(this, uri)
                true
            }.getOrElse {
                val path = song.path.takeIf { value -> value.isNotBlank() } ?: return null
                runCatching {
                    retriever.setDataSource(path)
                    true
                }.getOrDefault(false)
            }
            if (!sourceConfigured) {
                null
            } else {
                retriever.embeddedPicture
            }
        } catch (e: Exception) {
            Timber.tag(castHttpLogTag).d(e, "Embedded album art extraction failed. songId=%s", song.id)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun resolveEmbeddedImageContentType(bytes: ByteArray): ContentType {
        return when {
            bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte() -> ContentType.Image.JPEG

            bytes.size >= 8 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte() -> ContentType.Image.PNG

            bytes.size >= 6 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() -> ContentType.Image.GIF

            else -> ContentType.Image.JPEG
        }
    }

    private fun placeholderArtSource(): ArtStreamSource {
        return ArtStreamSource(
            sourceLabel = "placeholder_png",
            contentType = ContentType.Image.PNG,
            contentLength = transparentPng1x1.size.toLong(),
            lastModifiedEpochMs = null
        ) {
            ByteArrayInputStream(transparentPng1x1)
        }
    }

    private fun queryContentLengthFromProvider(uri: Uri): Long {
        return runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                val sizeColumnIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeColumnIndex == -1 || !cursor.moveToFirst()) {
                    return@use -1L
                }
                cursor.getLong(sizeColumnIndex)
            } ?: -1L
        }.getOrDefault(-1L)
    }

    private fun resolveSongLastModifiedEpochMs(song: Song, fallbackFile: File?): Long? {
        val fileTimestamp = fallbackFile?.lastModified()?.takeIf { it > 0L }
        val songTimestamp = normalizeEpochMillis(song.dateModified)
            ?: normalizeEpochMillis(song.dateAdded)
        return fileTimestamp ?: songTimestamp
    }

    private fun normalizeEpochMillis(value: Long): Long? {
        if (value <= 0L) return null
        return if (value < 10_000_000_000L) value * 1000L else value
    }

    private fun formatHttpDate(epochMs: Long): String {
        return httpDateFormatter.format(Instant.ofEpochMilli(epochMs))
    }

    private fun resolvePreferredAudioMimeType(song: Song, uri: Uri): String? {
        val providerMimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        val normalizedFallback = listOfNotNull(song.mimeType, providerMimeType, resolveAudioMimeTypeFromPath(song.path))
            .firstNotNullOfOrNull { normalizeCastAudioMimeType(it) }

        // Container formats identified by metadata/extension are reliable — do not override them
        // with signature detection, which can produce false positives (e.g. 0xFF sync words inside
        // an MP4 moov atom are misread as AAC/MPEG framing). Only use signature to upgrade truly
        // ambiguous metadata types (audio/mpeg, audio/aac) or when metadata is absent.
        val isContainerFormat = normalizedFallback != null &&
            normalizedFallback != "audio/mpeg" &&
            normalizedFallback != "audio/aac"

        if (isContainerFormat) {
            // Metadata says it's a container (mp4, flac, wav, ogg, webm…). Trust it.
            // Only override if signature found a *different* container magic at file level.
            val signatureMimeType = detectAudioMimeTypeBySignature(song, uri)
            val signatureIsContainer = signatureMimeType != null &&
                signatureMimeType != "audio/mpeg" &&
                signatureMimeType != "audio/aac"
            if (signatureIsContainer && signatureMimeType != normalizedFallback) {
                Timber.tag("PX_CAST_HTTP")
                    .w("MIME container-mismatch songId=${song.id} meta=$normalizedFallback signature=$signatureMimeType — trusting meta")
            }
            // Always prefer the container type from metadata over signature for container formats.
            return normalizedFallback
        }

        // Metadata is ambiguous (audio/mpeg or audio/aac) or absent — use signature to resolve.
        val signatureMimeType = detectAudioMimeTypeBySignature(song, uri)
        if (signatureMimeType != null && signatureMimeType != normalizedFallback) {
            Timber.tag("PX_CAST_HTTP")
                .w("MIME mismatch songId=${song.id} fallback=$normalizedFallback signature=$signatureMimeType — using signature")
        }
        val fallbackMimeType = song.mimeType ?: providerMimeType ?: resolveAudioMimeTypeFromPath(song.path)
        return signatureMimeType ?: normalizedFallback ?: fallbackMimeType
    }

    private fun normalizeCastAudioMimeType(rawMimeType: String): String? {
        val normalized = rawMimeType
            .trim()
            .substringBefore(';')
            .lowercase(Locale.ROOT)
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
            "audio/x-hx-aac-adts",
            // ALAC codec in M4A: server transcodes to AAC ADTS, normalize as audio/aac
            "audio/alac" -> "audio/aac"

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

            // AIFF is not natively supported by Cast. Map to audio/mpeg as best-effort fallback.
            "audio/aiff",
            "audio/x-aiff",
            "audio/aif" -> "audio/mpeg"

            else -> null
        }
    }

    private fun detectAudioMimeTypeBySignature(song: Song, uri: Uri): String? {
        signatureMimeCache[song.id]?.let { return it }
        val bytes = readAudioSignature(song = song, uri = uri) ?: run {
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

    private fun readAudioSignature(song: Song, uri: Uri, maxBytes: Int = 16 * 1024): ByteArray? {
        val uriBytes = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
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
            ?.takeIf { it.exists() && it.isFile && it.canRead() }
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
            val layerBits = (b1 ushr 1) and 0x03
            if (layerBits == 0) return "audio/aac"
            if (layerBits in 1..3) return "audio/mpeg"
        }
        return null
    }

    private fun resolveAudioContentType(mimeType: String?): ContentType {
        val normalized = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
            ?: return ContentType.Audio.MPEG

        return runCatching { ContentType.parse(normalized) }
            .getOrElse { ContentType.Audio.MPEG }
    }

    private fun resolveAudioMimeTypeFromPath(path: String?): String? {
        val extension = path
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return when (extension) {
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "m4a", "m4b", "m4p", "mp4", "3gp", "3gpp", "3ga" -> "audio/mp4"
            "wav" -> "audio/wav"
            "aif", "aiff", "aifc" -> "audio/aiff"
            "ogg", "oga", "opus" -> "audio/ogg"
            "weba" -> "audio/webm"
            "wma" -> "audio/x-ms-wma"
            else -> null
        }
    }

    private fun resolveImageContentType(mimeType: String?, uriPath: String?): ContentType {
        val normalizedMime = mimeType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.lowercase(Locale.ROOT)
        if (!normalizedMime.isNullOrBlank()) {
            return runCatching { ContentType.parse(normalizedMime) }
                .getOrElse { ContentType.Image.JPEG }
        }

        val extension = uriPath
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }
        return when (extension) {
            "jpg", "jpeg" -> ContentType.Image.JPEG
            "png" -> ContentType.Image.PNG
            "webp" -> runCatching { ContentType.parse("image/webp") }.getOrElse { ContentType.Image.JPEG }
            "gif" -> ContentType.Image.GIF
            "bmp" -> runCatching { ContentType.parse("image/bmp") }.getOrElse { ContentType.Image.JPEG }
            "heic", "heif" -> runCatching { ContentType.parse("image/heif") }.getOrElse { ContentType.Image.JPEG }
            else -> ContentType.Image.JPEG
        }
    }

    private suspend fun ApplicationCall.respondWithAudioStream(
        contentType: ContentType,
        fileSize: Long,
        rangeHeader: String?,
        inputStreamFactory: () -> InputStream
    ) {
        if (rangeHeader != null && fileSize > 0) {
            val rangesSpecifier = io.ktor.http.parseRangesSpecifier(rangeHeader)
            val ranges = rangesSpecifier?.ranges

            if (ranges.isNullOrEmpty()) {
                Timber.tag(castHttpLogTag).w("Invalid range header: %s", rangeHeader)
                Timber.tag("PX_CAST_HTTP").w("Invalid range header: $rangeHeader")
                respond(HttpStatusCode.BadRequest, "Invalid range")
                return
            }

            val range = ranges.first()
            val start = when (range) {
                is io.ktor.http.ContentRange.Bounded -> range.from
                is io.ktor.http.ContentRange.TailFrom -> range.from
                is io.ktor.http.ContentRange.Suffix -> fileSize - range.lastCount
            }
            val end = when (range) {
                is io.ktor.http.ContentRange.Bounded -> range.to
                is io.ktor.http.ContentRange.TailFrom -> fileSize - 1
                is io.ktor.http.ContentRange.Suffix -> fileSize - 1
            }

            val clampedStart = start.coerceAtLeast(0L)
            val clampedEnd = end.coerceAtMost(fileSize - 1)
            val length = clampedEnd - clampedStart + 1

            if (length <= 0) {
                Timber.tag(castHttpLogTag).w(
                    "Unsatisfiable range. header=%s start=%d end=%d size=%d",
                    rangeHeader,
                    clampedStart,
                    clampedEnd,
                    fileSize
                )
                Timber.tag("PX_CAST_HTTP")
                    .w("Unsatisfiable range header=$rangeHeader start=$clampedStart end=$clampedEnd size=$fileSize")
                respond(HttpStatusCode.RequestedRangeNotSatisfiable, "Range not satisfiable")
                return
            }

            response.header(HttpHeaders.ContentRange, "bytes $clampedStart-$clampedEnd/$fileSize")
            response.header(HttpHeaders.AcceptRanges, "bytes")
            response.header(HttpHeaders.ContentLength, length.toString())

            respondOutputStream(contentType, HttpStatusCode.PartialContent) {
                inputStreamFactory().use { inputStream ->
                    if (!skipFully(inputStream, clampedStart)) {
                        return@use
                    }
                    copyLimited(inputStream, this, length)
                }
            }
            return
        }

        if (fileSize > 0) {
            response.header(HttpHeaders.AcceptRanges, "bytes")
            response.header(HttpHeaders.ContentLength, fileSize.toString())
        }

        respondOutputStream(contentType) {
            inputStreamFactory().use { inputStream ->
                inputStream.copyTo(this)
            }
        }
    }

    private fun skipFully(inputStream: InputStream, bytesToSkip: Long): Boolean {
        var remaining = bytesToSkip
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
                continue
            }
            if (inputStream.read() == -1) {
                return false
            }
            remaining--
        }
        return true
    }

    private fun copyLimited(inputStream: InputStream, outputStream: OutputStream, length: Long) {
        var remaining = length
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (remaining > 0) {
            val read = inputStream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read == -1) break
            outputStream.write(buffer, 0, read)
            remaining -= read.toLong()
        }
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
     * Returns true if a working MediaCodec decoder for the given ALAC [codecInfo] is available
     * on this device.
     *
     * NOTE: We do NOT filter out QTI/Qualcomm decoders here.  Those decoders have reported
     * instability when used for *device audio playback*, but in our pipeline we only use the
     * decoder as a source (decode → PCM → AAC encode) and never route audio to the output
     * stack.
     */
    private fun isAlacTranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/alac") return false
        if (isFfmpegAlacTranscodeSupported()) return true
        return isMimeTypeDecoderSupported("audio/alac")
    }

    /**
     * Returns true if a working MediaCodec decoder for FLAC is available on this device.
     */
    private fun isFlacTranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/flac") return false
        return isMimeTypeDecoderSupported("audio/flac")
    }

    /**
     * Returns true if AC3/EAC3 can be transcoded to AAC on this device.
     * Tries FFmpeg first (compiled-in, reliable), then falls back to checking
     * for a MediaCodec AC3 decoder (Qualcomm/Dolby on some Snapdragon devices).
     */
    private fun isAc3TranscodeSupported(codecInfo: AudioCodecInfo): Boolean {
        if (codecInfo.codecMime != "audio/ac3" && codecInfo.codecMime != "audio/eac3") return false
        if (isFfmpegAc3TranscodeSupported(codecInfo.codecMime)) return true
        return isMimeTypeDecoderSupported(codecInfo.codecMime)
    }

    /**
     * Detects the actual audio codec inside a container (e.g. audio/alac vs audio/mp4 for ALAC-in-M4A).
     * Results are cached to avoid repeated MediaExtractor operations per song.
     */
    private fun detectAudioCodecViaExtractor(song: Song, uri: Uri): AudioCodecInfo? {
        if (codecInfoCache.contains(song.id)) return codecInfoCache[song.id]
        val extractor = MediaExtractor()
        val result = runCatching {
            val opened = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val len = afd.length.takeIf { it > 0 } ?: afd.declaredLength.takeIf { it > 0 }
                    if (len != null) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, len)
                    else extractor.setDataSource(afd.fileDescriptor)
                } != null
            }.getOrElse { false } || runCatching {
                song.path.takeIf { it.isNotBlank() }?.let { extractor.setDataSource(it) }
                true
            }.getOrElse { false }
            if (!opened) return@runCatching null
            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                var mime = fmt.getString(MediaFormat.KEY_MIME)?.trim()?.lowercase(Locale.ROOT) ?: continue
                if (!mime.startsWith("audio/")) continue

                // Fix ALAC mislabeled by missing OEM box definitions.
                // EAC3 (audio/eac3) and AC3 (audio/ac3) are common misidentifications for ALAC boxes on some Samsung devices,
                // alongside audio/mp4a-latm.
                if (mime == "audio/mp4a-latm" || mime == "audio/eac3" || mime == "audio/ac3") {
                    val isM4a = song.path.endsWith(".m4a", true)
                    val isExplicitAlacMetadata = song.mimeType?.contains("alac", true) == true
                    // High-bitrate heuristic only applies to audio/mp4a-latm misidentifications.
                    // AC3/EAC3 files can legitimately have high bitrates, so excluding them prevents
                    // genuine Dolby files from being mis-reclassified as ALAC.
                    val hasHighBitrate = mime == "audio/mp4a-latm" &&
                        (runCatching { fmt.getInteger(MediaFormat.KEY_BIT_RATE) }.getOrNull() ?: 0) > 600_000

                    // EAC3/AC3 inside an .m4a is mislabeled ALAC ONLY when CSD-0 is present.
                    // The Samsung OEM bug shows audio/ac3 but the MediaFormat still carries the
                    // ALACSpecificConfig as csd-0. Genuine AC3/EAC3 content has no csd-0 at all.
                    val hasCsd0 = (mime == "audio/eac3" || mime == "audio/ac3") &&
                        runCatching { (fmt.getByteBuffer("csd-0")?.remaining() ?: 0) > 0 }.getOrDefault(false)
                    val isImpossibleCodecInM4a = isM4a && (mime == "audio/eac3" || mime == "audio/ac3") && hasCsd0

                    if (isExplicitAlacMetadata || isImpossibleCodecInM4a || (isM4a && hasHighBitrate)) {
                        mime = "audio/alac"
                    } else if (isM4a) {
                        val mmr = android.media.MediaMetadataRetriever()
                        runCatching {
                            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                                mmr.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }
                            val mmrMime = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.lowercase(Locale.ROOT)
                            if (mmrMime == "audio/alac") {
                                mime = "audio/alac"
                            }
                        }.also { runCatching { mmr.release() } }
                    }
                }

                // MediaExtractor sometimes exposes the decoded PCM track of a FLAC file
                // as "audio/raw" instead of the container format "audio/flac".  Recover
                // the true format from the file extension / mime metadata so the FLAC → AAC
                // transcode path is correctly triggered.
                if (mime == "audio/raw") {
                    val mimeL = song.mimeType?.lowercase(Locale.ROOT)
                    val isFlac = song.path.endsWith(".flac", true) ||
                        mimeL == "audio/flac" || mimeL == "audio/x-flac"
                    if (isFlac) {
                        mime = "audio/flac"
                    }
                }

                val sr = runCatching { fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrNull() ?: continue
                val ch = runCatching { fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrNull() ?: continue
                return@runCatching AudioCodecInfo(mime, sr, ch, i)
            }
            null
        }.getOrNull().also { runCatching { extractor.release() } }
        codecInfoCache[song.id] = result
        return result
    }

    // -------------------------------------------------------------------------
    // Transcode cache helpers
    // -------------------------------------------------------------------------

    /**
     * Responds to a GET request for a transcoded song (ALAC→AAC or FLAC→AAC).
     *
     * Strategy:
     *  - If a completed temp file exists in [transcodeCache], serve it immediately
     *    with full Range/206/Content-Length/Accept-Ranges support (seek-safe).
     *  - If a transcode is already in-progress, wait for it then serve the file
     *    (or fall back to streaming if it failed).
     *  - If no entry exists yet, create one and start a background transcode:
     *    • First request: transcode while simultaneously piping data to the
     *      HTTP response ("tee" pattern so playback starts immediately).
     *    • Data is written to the temp file; once finished the entry is marked done.
     *    • Future seek requests (Range: bytes=X-) read directly from the temp file.
     */
    private suspend fun ApplicationCall.respondTranscodedWithCache(
        song: Song,
        codecInfo: AudioCodecInfo,
        uri: Uri,
        rangeHeader: String?
    ) {
        val songId = song.id
        val aacContentType = ContentType.parse("audio/aac")

        // Fast path: temp file already complete → serve with Range support.
        val existing = transcodeCache[songId]
        if (existing != null && existing.done && !existing.failed && existing.tempFile.exists()) {
            Timber.tag(castHttpLogTag).d(
                "transcode-cache HIT songId=%s size=%d range=%s",
                songId, existing.tempFile.length(), rangeHeader
            )
            respondWithAudioStream(
                contentType = aacContentType,
                fileSize = existing.tempFile.length(),
                rangeHeader = rangeHeader
            ) { FileInputStream(existing.tempFile) }
            return
        }

        // If a transcode is already running, wait for it.
        if (existing != null && !existing.done) {
            Timber.tag(castHttpLogTag).d(
                "transcode-cache WAIT songId=%s range=%s", songId, rangeHeader
            )
            // Wait with a generous timeout (10 min for very long songs).
            withContext(Dispatchers.IO) {
                existing.latch.await(10, TimeUnit.MINUTES)
            }
            if (existing.done && !existing.failed && existing.tempFile.exists()) {
                respondWithAudioStream(
                    contentType = aacContentType,
                    fileSize = existing.tempFile.length(),
                    rangeHeader = rangeHeader
                ) { FileInputStream(existing.tempFile) }
                return
            }
            // Transcode failed: fall back to raw on-the-fly streaming (no seek support).
            Timber.tag(castHttpLogTag).w(
                "transcode-cache FAILED-WAIT songId=%s, falling back to on-the-fly", songId
            )
            respondOutputStream(aacContentType) {
                transcodeToAacAdts(codecInfo, song, uri, this)
            }
            return
        }

        // No entry yet — we are the first request. Create the entry and start transcoding.
        val tempFile = File(cacheDir, "cast_transcode_${songId}.aac")
        // Remove stale failed temp file if present.
        runCatching { if (tempFile.exists()) tempFile.delete() }

        val entry = TranscodeEntry(tempFile = tempFile)
        transcodeCache[songId] = entry

        Timber.tag(castHttpLogTag).d(
            "transcode-cache MISS songId=%s, starting tee transcode", songId
        )
        Timber.tag("PX_CAST_HTTP").i("transcode_cache_start songId=$songId codec=${codecInfo.codecMime}")

        // If a Range header was sent on the very first request (unlikely but defensive),
        // we can't tee and serve from offset 0. Wait is the cleanest option — start
        // transcode in a background job and serve the whole file once done.
        if (rangeHeader != null && !rangeHeader.startsWith("bytes=0-")) {
            // Launch background transcode, wait until done then serve.
            serviceScope.launch {
                runCatching {
                    FileOutputStream(tempFile).use { fos ->
                        transcodeToAacAdts(codecInfo, song, uri, fos)
                    }
                    entry.done = true
                }.onFailure { t ->
                    entry.failed = true
                    Timber.tag(castHttpLogTag).e(t, "transcode-cache bg transcode failed songId=%s", songId)
                    runCatching { tempFile.delete() }
                }.also {
                    entry.latch.countDown()
                }
            }
            // Wait for completion.
            withContext(Dispatchers.IO) {
                entry.latch.await(10, TimeUnit.MINUTES)
            }
            if (entry.done && tempFile.exists()) {
                respondWithAudioStream(
                    contentType = aacContentType,
                    fileSize = tempFile.length(),
                    rangeHeader = rangeHeader
                ) { FileInputStream(tempFile) }
            } else {
                respond(HttpStatusCode.InternalServerError, "Transcode failed")
            }
            return
        }

        // Normal first request (no Range / Range:bytes=0-): tee transcode output to both
        // the HTTP response stream and the temp file simultaneously.
        // respondOutputStream uses an extension-function receiver: `this` IS the OutputStream.
        respondOutputStream(aacContentType) {
            val responseStream: OutputStream = this
            val transcodeError = runCatching {
                FileOutputStream(tempFile).use { fileOut ->
                    val tee = TeeOutputStream(responseStream, fileOut)
                    transcodeToAacAdts(codecInfo, song, uri, tee)
                }
                entry.done = true
                Timber.tag("PX_CAST_HTTP").i("transcode_cache_done songId=$songId size=${tempFile.length()}")
            }.exceptionOrNull()

            if (transcodeError != null) {
                entry.failed = true
                runCatching { tempFile.delete() }
                if (!transcodeError.isClientAbortDuringResponse()) {
                    Timber.tag(castHttpLogTag).e(transcodeError, "transcode-cache tee failed songId=%s", songId)
                    Timber.tag("PX_CAST_HTTP")
                        .e(transcodeError, "transcode_cache_error songId=$songId")
                }
            }
            entry.latch.countDown()
        }
    }

    /** Cleans up all transcode temp files (called on service destroy). */
    private fun cleanupAllTranscodeTempFiles() {
        transcodeCache.values.forEach { entry ->
            runCatching { entry.tempFile.delete() }
        }
        transcodeCache.clear()
        Timber.tag("PX_CAST_HTTP").i("transcode_cache_cleanup done")
    }

    /**
     * Removes the cached transcode for [songId] and deletes the temp file.
     * Call this when the Cast session changes (song skip, session end).
     */
    fun evictTranscodeCache(songId: String) {
        transcodeCache.remove(songId)?.let { entry ->
            runCatching { entry.tempFile.delete() }
            Timber.tag(castHttpLogTag).d("transcode-cache evicted songId=%s", songId)
        }
    }

    // -------------------------------------------------------------------------
    // TeeOutputStream: writes every byte to two delegates simultaneously.
    // -------------------------------------------------------------------------
    private class TeeOutputStream(
        private val primary: OutputStream,
        private val secondary: OutputStream
    ) : OutputStream() {
        override fun write(b: Int) {
            primary.write(b)
            secondary.write(b)
        }
        override fun write(b: ByteArray, off: Int, len: Int) {
            primary.write(b, off, len)
            secondary.write(b, off, len)
        }
        override fun flush() {
            primary.flush()
            secondary.flush()
        }
        // Do NOT close delegates here — caller controls their lifecycles.
    }

    /**
     * Transcodes audio (primarily ALAC/FLAC) to raw ADTS-framed AAC-LC using Android's MediaCodec.
     * The output stream receives a continuous sequence of 7-byte ADTS headers + AAC frames,
     * making it a valid audio/aac bitstream that the Cast Default Media Receiver can play.
     */
    private fun transcodeToAacAdts(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val countingOutput = CountingOutputStream(outputStream)
        val shouldTryFfmpeg = when (codecInfo.codecMime) {
            "audio/alac" -> isFfmpegAlacTranscodeSupported()
            "audio/ac3", "audio/eac3" -> isFfmpegAc3TranscodeSupported(codecInfo.codecMime)
            else -> false
        }
        if (shouldTryFfmpeg) {
            val codecLabel = codecInfo.codecMime.substringAfter('/').uppercase()
            val ffmpegFailure = runCatching {
                Timber.tag(castHttpLogTag).i(
                    "transcode %s→AAC using FFmpeg decoder songId=%s sr=%d ch=%d",
                    codecLabel, song.id, codecInfo.sampleRate, codecInfo.channelCount
                )
                Timber.tag("PX_CAST_HTTP")
                    .i("transcode_ffmpeg codec=${codecInfo.codecMime} songId=${song.id} sr=${codecInfo.sampleRate} ch=${codecInfo.channelCount}")
                transcodeToAacAdtsViaFfmpeg(codecInfo, song, uri, countingOutput)
            }.exceptionOrNull()

            if (ffmpegFailure == null) {
                return
            }

            if (!ffmpegFailure.isClientAbortDuringResponse()) {
                Timber.tag(castHttpLogTag).w(
                    ffmpegFailure,
                    "FFmpeg %s transcode failed, falling back to MediaCodec songId=%s",
                    codecLabel, song.id
                )
            }

            if (countingOutput.bytesWritten > 0L) {
                throw ffmpegFailure
            }
        }

        transcodeToAacAdtsViaMediaCodec(codecInfo, song, uri, countingOutput)
    }

    private fun transcodeToAacAdtsViaMediaCodec(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        val tid = android.os.Process.myTid()
        val originalPriority = android.os.Process.getThreadPriority(tid)
        try {
            val opened = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val len = afd.length.takeIf { it > 0 } ?: afd.declaredLength.takeIf { it > 0 }
                    if (len != null) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, len)
                    else extractor.setDataSource(afd.fileDescriptor)
                } != null
            }.getOrElse { false } || runCatching {
                song.path.takeIf { it.isNotBlank() }?.let { extractor.setDataSource(it) }
                true
            }.getOrElse { false }
            if (!opened) {
                Timber.tag(castHttpLogTag).e("transcode: failed to open source songId=%s", song.id)
                return
            }

            val inputFormat = extractor.getTrackFormat(codecInfo.trackIndex)
            extractor.selectTrack(codecInfo.trackIndex)

            // Use the specific codec name returned by findDecoderForFormat; if that
            // specific decoder fails to configure (rare on some Qualcomm devices) or is null 
            // (common for high-sample-rate ALAC/FLAC formats due to strict OS config limits),
            // strictly fall back to createDecoderByType which lets the system pick any available decoder.
            val decoderName = runCatching {
                MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(inputFormat)
            }.getOrNull()

            if (decoderName != null) {
                decoder = runCatching {
                    MediaCodec.createByCodecName(decoderName).also { dec ->
                        dec.configure(inputFormat, null, null, 0)
                        dec.start()
                    }
                }.getOrElse { namedDecoderError ->
                    Timber.tag(castHttpLogTag).w(
                        namedDecoderError,
                        "transcodeToAacAdts: named decoder '%s' failed, retrying with createDecoderByType for %s songId=%s",
                        decoderName, codecInfo.codecMime, song.id
                    )
                    null
                }
            }

            if (decoder == null) {
                // Important: We must patch the inputFormat's MIME type to match precisely the codec we
                // are asking for, as the format could have had "audio/mp4a-latm" (which we safely overrode)
                inputFormat.setString(MediaFormat.KEY_MIME, codecInfo.codecMime)
                decoder = runCatching {
                    MediaCodec.createDecoderByType(codecInfo.codecMime).also { dec ->
                        dec.configure(inputFormat, null, null, 0)
                        dec.start()
                    }
                }.getOrElse { fallbackError ->
                    Timber.tag(castHttpLogTag).e(
                        fallbackError,
                        "transcodeToAacAdts: fallback decoder failed for %s songId=%s",
                        codecInfo.codecMime, song.id
                    )
                    Timber.tag("PX_CAST_HTTP")
                        .e("transcode_no_decoder songId=${song.id} codec=${codecInfo.codecMime}")
                    return
                }
            }


            val encChannels = minOf(codecInfo.channelCount, 2)
            val bitrate = when {
                encChannels >= 2 && codecInfo.sampleRate >= 44100 -> 256_000
                encChannels >= 2 -> 192_000
                else -> 128_000
            }
            val encFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm", codecInfo.sampleRate, encChannels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            val encoderInfo = MediaCodec.BufferInfo()
            val TIMEOUT_US = 20_000L
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            var srcDone = false
            var decDone = false
            var encDone = false

            // Pre-allocate buffers to eliminate GC storms in real-time loop.
            var pcmBuffer = ByteArray(32768)
            var downmixBuffer = ByteArray(32768)
            var actualDecoderChannels = codecInfo.channelCount

            while (!encDone) {
                // Feed compressed packets from extractor into decoder
                if (!srcDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)
                        if (buf != null) {
                            val n = extractor.readSampleData(buf, 0)
                            if (n < 0) {
                                decoder.queueInputBuffer(inIdx, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                srcDone = true
                            } else {
                                decoder.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                // Pull decoded PCM from decoder and push into encoder
                if (!decDone) {
                    val outIdx = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        runCatching {
                            val newFormat = decoder.outputFormat
                            if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                                actualDecoderChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            }
                        }
                    } else if (outIdx >= 0) {
                        val isEos = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        val pcm = if (bufferInfo.size > 0) decoder.getOutputBuffer(outIdx) else null
                        
                        if (pcm != null) {
                            pcm.position(bufferInfo.offset)
                            pcm.limit(bufferInfo.offset + bufferInfo.size)
                        }
                        
                        var remaining = bufferInfo.size
                        var pts = bufferInfo.presentationTimeUs
                        val bytesPerSampleFrame = 2 * actualDecoderChannels
                        
                        val isEosEmpty = isEos && remaining == 0
                        while (remaining > 0 || isEosEmpty) {
                            val encInIdx = encoder.dequeueInputBuffer(TIMEOUT_US)
                            if (encInIdx < 0) {
                                if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                    encDone = true
                                    break
                                }
                                continue
                            }
                            
                            val encBuf = encoder.getInputBuffer(encInIdx) ?: break
                            encBuf.clear()
                            
                            val frameBytesIn = bytesPerSampleFrame.takeIf { it > 0 } ?: (actualDecoderChannels * 2)
                            val frameBytesOut = encChannels * 2
                            val rawCapacityFrames = if (frameBytesOut > 0) encBuf.capacity() / frameBytesOut else 1024
                            val maxOutCapacityFrames = maxOf(rawCapacityFrames, 1)
                            val maxInputBytes = maxOutCapacityFrames * frameBytesIn

                            val rawToWrite = if (isEosEmpty) 0 else minOf(remaining, maxInputBytes)
                            val toWrite = if (frameBytesIn > 0) (rawToWrite / frameBytesIn) * frameBytesIn else rawToWrite
                            var encoderBytesAssigned = toWrite
                            if (toWrite > 0 && pcm != null) {
                                if (pcmBuffer.size < toWrite) {
                                    pcmBuffer = ByteArray(toWrite)
                                }
                                pcm.get(pcmBuffer, 0, toWrite)
                                
                                if (actualDecoderChannels > 2) {
                                    val inChannels = actualDecoderChannels
                                    val outChannels = encChannels
                                    val frameBytesIn = inChannels * 2
                                    val frameBytesOut = outChannels * 2
                                    val numFrames = toWrite / frameBytesIn
                                    val outSize = numFrames * frameBytesOut
                                    
                                    if (downmixBuffer.size < outSize) {
                                        downmixBuffer = ByteArray(outSize)
                                    }
                                    
                                    val inBuf = ByteBuffer.wrap(pcmBuffer, 0, toWrite).order(
                                        ByteOrder.nativeOrder()).asShortBuffer()
                                    val outBuf = ByteBuffer.wrap(downmixBuffer, 0, outSize).order(ByteOrder.nativeOrder()).asShortBuffer()
                                    
                                    for (i in 0 until numFrames) {
                                        if (inChannels == 6) {
                                            val c0 = inBuf.get()
                                            val c1 = inBuf.get()
                                            val c2 = inBuf.get()
                                            val lfe = inBuf.get()
                                            val c4 = inBuf.get()
                                            val c5 = inBuf.get()
                                            val left = (c0 * 0.5f + c2 * 0.35f + c4 * 0.35f).toInt()
                                            val right = (c1 * 0.5f + c2 * 0.35f + c5 * 0.35f).toInt()
                                            outBuf.put(left.coerceIn(-32768, 32767).toShort())
                                            outBuf.put(right.coerceIn(-32768, 32767).toShort())
                                        } else {
                                            outBuf.put(inBuf.get())
                                            outBuf.put(inBuf.get())
                                            for (c in 2 until inChannels) {
                                                inBuf.get()
                                            }
                                        }
                                    }
                                    encBuf.put(downmixBuffer, 0, outSize)
                                    encoderBytesAssigned = outSize
                                } else {
                                    encBuf.put(pcmBuffer, 0, toWrite)
                                    encoderBytesAssigned = toWrite
                                }
                            }
                            
                            val eos = isEos && remaining <= toWrite
                            encoder.queueInputBuffer(
                                encInIdx, 0, encoderBytesAssigned, pts,
                                if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                            )
                            
                            if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                encDone = true
                                break
                            }
                            
                            if (bytesPerSampleFrame > 0 && toWrite > 0) {
                                pts += (toWrite.toLong() * 1_000_000L / (bytesPerSampleFrame * codecInfo.sampleRate))
                            }
                            remaining -= toWrite
                            if (isEosEmpty) break
                        }
                        
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (isEos) decDone = true
                    }
                }

                // Drain encoder output
                if (!encDone) {
                    if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                        encDone = true
                    }
                }
            }
            outputStream.flush()
        } catch (e: Exception) {
            if (!e.isClientAbortDuringResponse()) {
                Timber.tag(castHttpLogTag).e(e, "transcode %s→AAC error songId=%s", codecInfo.codecMime, song.id)
                Timber.tag("PX_CAST_HTTP").e(e, "transcode_ffmpeg_error codec=${codecInfo.codecMime} songId=${song.id}")
            }
        } finally {
            // Always restore the thread priority so pooled IO threads aren't permanently
            // degraded — Ktor reuses threads and a background-priority thread causes
            // subsequent raw-file requests to time out with "Broken pipe".
            runCatching { android.os.Process.setThreadPriority(originalPriority) }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { decoder?.stop(); decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun transcodeToAacAdtsViaFfmpeg(
        codecInfo: AudioCodecInfo,
        song: Song,
        uri: Uri,
        outputStream: OutputStream
    ) {
        val extractor = MediaExtractor()
        var decoder: Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>? = null
        var encoder: MediaCodec? = null
        val tid = android.os.Process.myTid()
        val originalPriority = android.os.Process.getThreadPriority(tid)
        try {
            val opened = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    val len = afd.length.takeIf { it > 0 } ?: afd.declaredLength.takeIf { it > 0 }
                    if (len != null) extractor.setDataSource(afd.fileDescriptor, afd.startOffset, len)
                    else extractor.setDataSource(afd.fileDescriptor)
                } != null
            }.getOrElse { false } || runCatching {
                song.path.takeIf { it.isNotBlank() }?.let { extractor.setDataSource(it) }
                true
            }.getOrElse { false }
            if (!opened) {
                error("FFmpeg transcode: failed to open source songId=${song.id}")
            }

            val inputFormat = extractor.getTrackFormat(codecInfo.trackIndex)
            inputFormat.setString(MediaFormat.KEY_MIME, codecInfo.codecMime)
            extractor.selectTrack(codecInfo.trackIndex)
            decoder = when (codecInfo.codecMime) {
                "audio/alac" -> createFfmpegAlacDecoder(inputFormat, codecInfo)
                "audio/ac3", "audio/eac3" -> createFfmpegAc3Decoder(inputFormat, codecInfo)
                else -> error("FFmpeg transcode: unsupported codec ${codecInfo.codecMime}")
            }

            val encChannels = minOf(codecInfo.channelCount, 2)
            val bitrate = when {
                encChannels >= 2 && codecInfo.sampleRate >= 44100 -> 256_000
                encChannels >= 2 -> 192_000
                else -> 128_000
            }
            val encFormat = MediaFormat.createAudioFormat(
                "audio/mp4a-latm",
                codecInfo.sampleRate,
                encChannels
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm")
            encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            val encoderInfo = MediaCodec.BufferInfo()
            val timeoutUs = 20_000L
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            val bytesPerSampleFrame = 2 * codecInfo.channelCount
            var srcDone = false
            var decDone = false
            var encDone = false
            var encoderEosQueued = false
            
            var pcmBuffer = ByteArray(32768)
            var downmixBuffer = ByteArray(32768)

            while (!encDone) {
                if (!srcDone) {
                    val inputBuffer = decoder.dequeueInputBuffer()
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val expectedSize = extractor.sampleSize
                            .takeIf { it in 1..Int.MAX_VALUE.toLong() }
                            ?.toInt()
                            ?: runCatching {
                                inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                            }.getOrDefault(16_384)

                        inputBuffer.ensureSpaceForWrite(expectedSize)
                        val sampleData = inputBuffer.data
                            ?: error("FFmpeg transcode: missing decoder input buffer")
                        sampleData.clear()

                        val bytesRead = extractor.readSampleData(sampleData, 0)
                        if (bytesRead < 0) {
                            inputBuffer.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                            inputBuffer.timeUs = 0L
                            decoder.queueInputBuffer(inputBuffer)
                            srcDone = true
                        } else {
                            sampleData.position(bytesRead)
                            inputBuffer.timeUs = extractor.sampleTime
                            inputBuffer.flip()
                            decoder.queueInputBuffer(inputBuffer)
                            extractor.advance()
                        }
                    }
                }

                if (!decDone) {
                    var decoderOutput = decoder.dequeueOutputBuffer()
                    while (decoderOutput != null) {
                        val isEos = decoderOutput.isEndOfStream()
                        val pcm = decoderOutput.data?.duplicate()?.apply {
                            position(0)
                            limit(decoderOutput.data?.limit() ?: 0)
                        }

                        if (!decoderOutput.shouldBeSkipped && pcm != null && pcm.hasRemaining()) {
                            var remaining = pcm.remaining()
                            var pts = decoderOutput.timeUs
                            while (remaining > 0) {
                                val encInIdx = encoder.dequeueInputBuffer(timeoutUs)
                                if (encInIdx < 0) {
                                    if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                        encDone = true
                                        break
                                    }
                                    continue
                                }
                                
                                val encBuf = encoder.getInputBuffer(encInIdx) ?: break
                                encBuf.clear()
                                val frameBytesInAlign = codecInfo.channelCount * 2
                                val frameBytesOut = encChannels * 2
                                val rawCapacityFrames = if (frameBytesOut > 0) encBuf.capacity() / frameBytesOut else 1024
                                val maxOutCapacityFrames = maxOf(rawCapacityFrames, 1)
                                val maxInputBytes = maxOutCapacityFrames * frameBytesInAlign
                                
                                val rawToWrite = minOf(remaining, maxInputBytes)
                                val toWrite = if (frameBytesInAlign > 0) (rawToWrite / frameBytesInAlign) * frameBytesInAlign else rawToWrite
                                var encoderBytesAssigned = toWrite
                                
                                if (pcmBuffer.size < toWrite) {
                                    pcmBuffer = ByteArray(toWrite)
                                }
                                pcm.get(pcmBuffer, 0, toWrite)
                                
                                if (codecInfo.channelCount > 2) {
                                    val inChannels = codecInfo.channelCount
                                    val outChannels = encChannels
                                    val frameBytesIn = inChannels * 2
                                    val frameBytesOut = outChannels * 2
                                    val numFrames = toWrite / frameBytesIn
                                    val outSize = numFrames * frameBytesOut
                                    
                                    if (downmixBuffer.size < outSize) {
                                        downmixBuffer = ByteArray(outSize)
                                    }
                                    
                                    val inBuf = ByteBuffer.wrap(pcmBuffer, 0, toWrite).order(ByteOrder.nativeOrder()).asShortBuffer()
                                    val outBuf = ByteBuffer.wrap(downmixBuffer, 0, outSize).order(ByteOrder.nativeOrder()).asShortBuffer()
                                    
                                    for (i in 0 until numFrames) {
                                        if (inChannels == 6) {
                                            val c0 = inBuf.get()
                                            val c1 = inBuf.get()
                                            val c2 = inBuf.get()
                                            val lfe = inBuf.get()
                                            val c4 = inBuf.get()
                                            val c5 = inBuf.get()
                                            val left = (c0 * 0.5f + c2 * 0.35f + c4 * 0.35f).toInt()
                                            val right = (c1 * 0.5f + c2 * 0.35f + c5 * 0.35f).toInt()
                                            outBuf.put(left.coerceIn(-32768, 32767).toShort())
                                            outBuf.put(right.coerceIn(-32768, 32767).toShort())
                                        } else {
                                            outBuf.put(inBuf.get())
                                            outBuf.put(inBuf.get())
                                            for (c in 2 until inChannels) {
                                                inBuf.get()
                                            }
                                        }
                                    }
                                    encBuf.put(downmixBuffer, 0, outSize)
                                    encoderBytesAssigned = outSize
                                } else {
                                    encBuf.put(pcmBuffer, 0, toWrite)
                                    encoderBytesAssigned = toWrite
                                }
                                val eos = isEos && remaining <= toWrite
                                encoder.queueInputBuffer(
                                    encInIdx,
                                    0,
                                    encoderBytesAssigned,
                                    pts,
                                    if (eos) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
                                )
                                
                                if (drainEncoderToAdts(encoder, encoderInfo, codecInfo.sampleRate, encChannels, outputStream)) {
                                    encDone = true
                                    break
                                }
                                
                                if (eos) {
                                    encoderEosQueued = true
                                }
                                if (bytesPerSampleFrame > 0) {
                                    pts += toWrite.toLong() * 1_000_000L /
                                        (bytesPerSampleFrame * codecInfo.sampleRate)
                                }
                                remaining -= toWrite
                                if (drainEncoderToAdts(
                                        encoder,
                                        encoderInfo,
                                        codecInfo.sampleRate,
                                        encChannels,
                                        outputStream
                                    )
                                ) {
                                    encDone = true
                                }
                            }
                        } else if (isEos && !encoderEosQueued) {
                            queueEncoderEndOfStream(encoder, encoderInfo, decoderOutput.timeUs, outputStream, codecInfo)
                            encoderEosQueued = true
                        }

                        decoderOutput.release()
                        if (isEos) {
                            decDone = true
                        }
                        if (encDone) {
                            break
                        }
                        decoderOutput = decoder.dequeueOutputBuffer()
                    }
                }

                if (srcDone && decDone && !encoderEosQueued) {
                    queueEncoderEndOfStream(encoder, encoderInfo, 0L, outputStream, codecInfo)
                    encoderEosQueued = true
                }

                if (!encDone) {
                    if (drainEncoderToAdts(
                            encoder,
                            encoderInfo,
                            codecInfo.sampleRate,
                            codecInfo.channelCount,
                            outputStream
                        )
                    ) {
                        encDone = true
                    }
                }
            }
            outputStream.flush()
        } finally {
            runCatching { android.os.Process.setThreadPriority(originalPriority) }
            runCatching { encoder?.stop(); encoder?.release() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun createFfmpegAlacDecoder(
        inputFormat: MediaFormat,
        codecInfo: AudioCodecInfo
    ): Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *> {
        val initData = extractCodecSpecificData(inputFormat)
        require(initData.isNotEmpty()) {
            "FFmpeg ALAC transcode requires codec initialization data"
        }

        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_ALAC)
            .setChannelCount(codecInfo.channelCount)
            .setSampleRate(codecInfo.sampleRate)
            .setInitializationData(initData)
            .setMaxInputSize(
                runCatching { inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                    .getOrDefault(64 * 1024)
            )
            .build()

        val decoderClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder")
        val constructor = decoderClass.getDeclaredConstructor(
            Format::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            format,
            16,
            16,
            format.maxInputSize.coerceAtLeast(16_384),
            false
        ) as Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>
    }

    /**
     * Creates an FFmpeg decoder for AC3/EAC3.
     * Unlike ALAC, AC3 is self-framing and requires no codec initialization data (no CSD-0).
     */
    @OptIn(UnstableApi::class)
    private fun createFfmpegAc3Decoder(
        inputFormat: MediaFormat,
        codecInfo: AudioCodecInfo
    ): Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *> {
        val format = Format.Builder()
            .setSampleMimeType(codecInfo.codecMime)
            .setChannelCount(codecInfo.channelCount)
            .setSampleRate(codecInfo.sampleRate)
            .setMaxInputSize(
                runCatching { inputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) }
                    .getOrDefault(64 * 1024)
            )
            .build()

        val decoderClass = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder")
        val constructor = decoderClass.getDeclaredConstructor(
            Format::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        return constructor.newInstance(
            format,
            16,
            16,
            format.maxInputSize.coerceAtLeast(16_384),
            false
        ) as Decoder<DecoderInputBuffer, SimpleDecoderOutputBuffer, *>
    }

    private fun extractCodecSpecificData(format: MediaFormat): List<ByteArray> {
        val initData = mutableListOf<ByteArray>()
        var index = 0
        while (true) {
            val csd = runCatching { format.getByteBuffer("csd-$index") }.getOrNull() ?: break
            val bytes = csd.toByteArray()
            if (bytes.isNotEmpty()) {
                initData += bytes
            }
            index += 1
        }
        return initData
    }

    private fun queueEncoderEndOfStream(
        encoder: MediaCodec,
        encoderInfo: MediaCodec.BufferInfo,
        ptsUs: Long,
        outputStream: OutputStream,
        codecInfo: AudioCodecInfo
    ) {
        repeat(4) {
            val encInIdx = encoder.dequeueInputBuffer(20_000L)
            if (encInIdx >= 0) {
                encoder.queueInputBuffer(
                    encInIdx,
                    0,
                    0,
                    ptsUs,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                return
            }
            drainEncoderToAdts(
                encoder,
                encoderInfo,
                codecInfo.sampleRate,
                codecInfo.channelCount,
                outputStream
            )
        }
        error("Failed to queue AAC encoder EOS")
    }

    @OptIn(UnstableApi::class)
    private fun isFfmpegAlacTranscodeSupported(): Boolean {
        return runCatching {
            FfmpegLibrary.supportsFormat(MimeTypes.AUDIO_ALAC)
        }.getOrDefault(false)
    }

    @OptIn(UnstableApi::class)
    private fun isFfmpegAc3TranscodeSupported(mimeType: String): Boolean {
        return runCatching {
            FfmpegLibrary.supportsFormat(mimeType)
        }.getOrDefault(false)
    }

    // isKnownUnstableAlacDecoder is intentionally NOT used in the transcode path.
    // It only applies when routing ALAC audio directly to device output (playback),
    // which we never do here — we decode to PCM and re-encode to AAC.

    private fun ByteBuffer.toByteArray(): ByteArray {
        val duplicate = duplicate()
        duplicate.position(0)
        val bytes = ByteArray(duplicate.remaining())
        duplicate.get(bytes)
        return bytes
    }

    /** Drains all available encoder output, wrapping each AAC frame with an ADTS header. Returns true when EOS. */
    private fun drainEncoderToAdts(
        encoder: MediaCodec,
        info: MediaCodec.BufferInfo,
        sampleRate: Int,
        channels: Int,
        out: OutputStream
    ): Boolean {
        var eos = false
        var idx = encoder.dequeueOutputBuffer(info, 0)
        while (idx >= 0 || idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                idx = encoder.dequeueOutputBuffer(info, 0)
                continue
            }
            val isEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
            val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            if (!isConfig && info.size > 0) {
                val buf = encoder.getOutputBuffer(idx)
                if (buf != null) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    writeAdtsHeader(out, sampleRate, channels, info.size)
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    out.write(bytes)
                }
            }
            encoder.releaseOutputBuffer(idx, false)
            if (isEos) { eos = true; break }
            idx = encoder.dequeueOutputBuffer(info, 0)
        }
        return eos
    }

    /**
     * Writes a 7-byte ADTS header for a single AAC-LC frame.
     * ADTS = Audio Data Transport Stream — allows AAC frames to be streamed without MP4 container.
     */
    private fun writeAdtsHeader(out: OutputStream, sampleRate: Int, channels: Int, aacFrameSize: Int) {
        val freqIdx = when (sampleRate) {
            96000 -> 0; 88200 -> 1; 64000 -> 2; 48000 -> 3
            44100 -> 4; 32000 -> 5; 24000 -> 6; 22050 -> 7
            16000 -> 8; 12000 -> 9; 11025 -> 10; 8000 -> 11
            7350 -> 12; else -> 4
        }
        val frameLen = aacFrameSize + 7 // total = payload + 7-byte header
        // Profile = AAC-LC (object type 2 → stored as 2-1 = 1 → bits 01)
        // Byte layout per ADTS spec (ISO 13818-7):
        //  [sync:12][id:1][layer:2][protection_absent:1]
        //  [profile_obj_type:2][samp_freq_idx:4][private:1][channel_cfg:3][orig:1][copy:1][home:1]
        //  [copyright_id_bit:1][copyright_id_start:1][frame_length:13][buffer_fullness:11][num_raw_blocks:2]
        out.write(
            byteArrayOf(
                0xFF.toByte(),
                0xF1.toByte(),  // MPEG-4, layer=0, no CRC
                (0x40 or (freqIdx shl 2) or (channels shr 2)).toByte(),
                ((channels and 0x3) shl 6 or (frameLen shr 11)).toByte(),
                (frameLen shr 3 and 0xFF).toByte(),
                ((frameLen and 0x7) shl 5 or 0x1F).toByte(),    // buf_fullness bits 10..6 = 0x1F
                0xFC.toByte()   // buf_fullness bits 5..0 = 0x3F, num_raw_blocks = 0
            )
        )
    }

    private fun Throwable.isClientAbortDuringResponse(): Boolean {
        return generateSequence(this) { it.cause }.any { cause ->
            cause is ClosedChannelException ||
                cause is EOFException ||
                (cause is SocketException && (
                    cause.message?.contains("Connection reset", ignoreCase = true) == true ||
                        cause.message?.contains("Broken pipe", ignoreCase = true) == true ||
                        cause.message?.contains("Socket closed", ignoreCase = true) == true
                    )) ||
                // Ktor CIO throws plain IOException (not SocketException) for broken pipe on NIO channels
                (cause is IOException && cause.message?.contains("Broken pipe", ignoreCase = true) == true) ||
                cause.javaClass.name.contains("ChannelWriteException")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServerRunning = false
        isServerStarting = false
        serverAddress = null
        serverHostAddress = null
        serverPrefixLength = -1
        castDeviceIpHint = null
        clearCastSessionAccess()
        synchronized(serverStartLock) {
            startInProgress = false
        }

        // Cleanup all transcode temp files.
        cleanupAllTranscodeTempFiles()

        // P0-1: Cancel serviceScope to avoid coroutine leaks after service is destroyed.
        serviceJob.cancel()

        val serverInstance = server
        server = null

        // Stop server in a background thread to avoid blocking the Main Thread
        Thread {
            try {
                // Grace period 100ms, timeout 2000ms
                serverInstance?.stop(100, 2000)
                Timber.d("MediaFileHttpServerService: Ktor server stopped")
            } catch (e: Exception) {
                Timber.e(e, "MediaFileHttpServerService: Error stopping Ktor server")
            }
        }.start()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
