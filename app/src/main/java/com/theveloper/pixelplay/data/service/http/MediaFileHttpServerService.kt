package com.theveloper.pixelplay.data.service.http

import android.app.Service
import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import com.theveloper.pixelplay.data.model.Song
import com.theveloper.pixelplay.data.repository.MusicRepository
import com.theveloper.pixelplay.utils.AlbumArtUtils
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.plugins.origin
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.nio.channels.ClosedChannelException
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class MediaFileHttpServerService : Service() {

    @Inject
    lateinit var musicRepository: MusicRepository

    private var server: ApplicationEngine? = null
    @Volatile
    private var startInProgress = false
    private val serverStartLock = Any()
    @Volatile
    private var castDeviceIpHint: String? = null
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val castHttpLogTag = "CastHttpServer"
    private val signatureMimeCache = mutableMapOf<String, String?>()
    private val httpDateFormatter: DateTimeFormatter =
        DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC)
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

        internal fun configureCastSessionAccess(
            allowedSongIds: Collection<String>,
            castDeviceIpHint: String?
        ): CastAccessPolicy {
            val updatedPolicy = CastSessionSecurity.buildAccessPolicy(
                existingToken = castAccessPolicy.authToken,
                allowedSongIds = allowedSongIds,
                castDeviceIpHint = castDeviceIpHint
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
            val channelName = "Cast Media Server"
            
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
                .setContentTitle("Casting to device")
                .setContentText("Serving media to Cast device")
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
            lastFailureMessage = "${throwable.javaClass.simpleName}: ${throwable.message ?: "Unknown"}"
            Timber.e(throwable, "Failed to enter foreground mode for cast HTTP server")
            stopSelf()
        }
    }

    private fun startServer() {
        synchronized(serverStartLock) {
            if (server?.application?.isActive == true || startInProgress) {
                return
            }
            startInProgress = true
            isServerStarting = true
        }

        serviceScope.launch {
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

                server = embeddedServer(
                    CIO,
                    port = serverPort,
                    host = "0.0.0.0",
                    configure = {
                        // Keep Ktor's bind behavior consistent with our port probe and reduce
                        // false "Address already in use" failures on quick Cast server restarts.
                        reuseAddress = true
                    }
                ) {
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
                                    Log.e("PX_CAST_HTTP", "GET /song unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound, "Song not found")
                                    return@get
                                }

                                try {
                                    val uri = song.contentUriString.toUri()
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
                                    Log.i(
                                        "PX_CAST_HTTP",
                                        "GET /song songId=${song.id} source=${source.sourceLabel} range=$rangeHeader size=${source.fileSize} type=$contentType"
                                    )
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
                                        Log.w(
                                            "PX_CAST_HTTP",
                                            "GET /song client_closed songId=${song.id} uri=${song.contentUriString} error=${e.javaClass.simpleName}"
                                        )
                                        return@get
                                    }
                                    Timber.tag(castHttpLogTag).e(
                                        e,
                                        "GET /song exception. songId=%s uri=%s",
                                        song.id,
                                        song.contentUriString
                                    )
                                    Log.e("PX_CAST_HTTP", "GET /song exception songId=${song.id} uri=${song.contentUriString}", e)
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
                                    Log.e("PX_CAST_HTTP", "HEAD /song unresolved songId=$songId")
                                    call.respond(HttpStatusCode.NotFound)
                                    return@head
                                }

                                try {
                                    val uri = song.contentUriString.toUri()
                                    val contentType = resolveAudioContentType(resolvePreferredAudioMimeType(song, uri))
                                    val source = resolveAudioStreamSource(song, uri)

                                    if (source == null) {
                                    Timber.tag(castHttpLogTag).w(
                                        "HEAD /song failed to resolve source. songId=%s uri=%s path=%s",
                                        song.id,
                                        song.contentUriString,
                                        song.path
                                    )
                                    Log.w("PX_CAST_HTTP", "HEAD /song no source songId=${song.id} uri=${song.contentUriString}")
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
                                        Log.w("PX_CAST_HTTP", "HEAD /song client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@head
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "HEAD /song exception. songId=%s", song.id)
                                    Log.e("PX_CAST_HTTP", "HEAD /song exception songId=${song.id}", e)
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
                                    Log.e("PX_CAST_HTTP", "GET /art unresolved songId=$songId")
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
                                    Log.i(
                                        "PX_CAST_HTTP",
                                        "GET /art songId=${song.id} source=${artSource.sourceLabel} len=${artSource.contentLength} type=${artSource.contentType}"
                                    )
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
                                        Log.w("PX_CAST_HTTP", "GET /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@get
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "GET /art exception. songId=%s", song.id)
                                    Log.e("PX_CAST_HTTP", "GET /art exception songId=${song.id}", e)
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
                                    Log.e("PX_CAST_HTTP", "HEAD /art unresolved songId=$songId")
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
                                        Log.w("PX_CAST_HTTP", "HEAD /art client_closed songId=${song.id} error=${e.javaClass.simpleName}")
                                        return@head
                                    }
                                    Timber.tag(castHttpLogTag).e(e, "HEAD /art exception. songId=%s", song.id)
                                    Log.e("PX_CAST_HTTP", "HEAD /art exception songId=${song.id}", e)
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
                Timber.e(e, "Failed to start HTTP cast server")
                lastFailureReason = FailureReason.START_EXCEPTION
                lastFailureMessage = "${e.javaClass.simpleName}: ${e.message ?: "Unknown"}"
                isServerRunning = false
                serverAddress = null
                stopSelf()
            } finally {
                synchronized(serverStartLock) {
                    startInProgress = false
                    isServerStarting = false
                }
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
        Log.w("PX_CAST_HTTP", "song_resolver repo_miss songId=$songId")

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
                Log.i(
                    "PX_CAST_HTTP",
                    "song_resolver media_store_hit songId=$songId mime=${resolved.mimeType} path=${resolved.path}"
                )
            } else {
                Log.e("PX_CAST_HTTP", "song_resolver media_store_miss songId=$songId")
            }
        }.onFailure { throwable ->
            Timber.tag(castHttpLogTag).e(throwable, "MediaStore fallback failed for songId=%s", songId)
            Log.e("PX_CAST_HTTP", "song_resolver media_store_error songId=$songId", throwable)
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
                Timber.tag(castHttpLogTag).d(throwable, "Album art URI probe failed. songId=%s", song.id)
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
        val signatureMimeType = detectAudioMimeTypeBySignature(song, uri)
        val providerMimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
        val fallbackMimeType = song.mimeType ?: providerMimeType ?: resolveAudioMimeTypeFromPath(song.path)
        val normalizedFallback = listOfNotNull(song.mimeType, providerMimeType, resolveAudioMimeTypeFromPath(song.path))
            .firstNotNullOfOrNull { normalizeCastAudioMimeType(it) }
        if (signatureMimeType != null && signatureMimeType != normalizedFallback) {
            Log.w(
                "PX_CAST_HTTP",
                "MIME mismatch songId=${song.id} fallback=$normalizedFallback signature=$signatureMimeType"
            )
        }
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
                Log.w("PX_CAST_HTTP", "Invalid range header: $rangeHeader")
                respond(HttpStatusCode.BadRequest, "Invalid range")
                return
            }

            val range = ranges.first()
            val start = when (range) {
                is io.ktor.http.ContentRange.Bounded -> range.from
                is io.ktor.http.ContentRange.TailFrom -> range.from
                is io.ktor.http.ContentRange.Suffix -> fileSize - range.lastCount
                else -> 0L
            }
            val end = when (range) {
                is io.ktor.http.ContentRange.Bounded -> range.to
                is io.ktor.http.ContentRange.TailFrom -> fileSize - 1
                is io.ktor.http.ContentRange.Suffix -> fileSize - 1
                else -> fileSize - 1
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
                Log.w(
                    "PX_CAST_HTTP",
                    "Unsatisfiable range header=$rangeHeader start=$clampedStart end=$clampedEnd size=$fileSize"
                )
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

    private fun Throwable.isClientAbortDuringResponse(): Boolean {
        return generateSequence(this) { it.cause }.any { cause ->
            cause is ClosedChannelException ||
                cause is EOFException ||
                (cause is SocketException && (
                    cause.message?.contains("Connection reset", ignoreCase = true) == true ||
                        cause.message?.contains("Broken pipe", ignoreCase = true) == true ||
                        cause.message?.contains("Socket closed", ignoreCase = true) == true
                    )) ||
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
