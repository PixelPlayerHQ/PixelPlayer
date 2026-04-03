package com.theveloper.pixelplay.data.gdrive

import android.net.Uri
import com.theveloper.pixelplay.data.stream.CloudStreamSecurity
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local HTTP proxy server for streaming Google Drive audio.
 *
 * Resolves `gdrive://{fileId}` URIs by proxying requests to the Drive REST API
 * with the required Authorization header. Follows the same architectural pattern
 * as [NeteaseStreamProxy] and [TelegramStreamProxy] using Ktor CIO.
 */
@Singleton
class GDriveStreamProxy @Inject constructor(
    private val repository: GDriveRepository,
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        val ALLOWED_REMOTE_HOST_SUFFIXES = setOf(
            "googleapis.com",
            "googleusercontent.com"
        )
    }

    private var server: ApplicationEngine? = null
    private var actualPort: Int = 0
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    fun isReady(): Boolean = actualPort > 0

    fun getProxyUrl(fileId: String): String {
        if (actualPort == 0) {
            Timber.w("GDriveStreamProxy: getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateGDriveFileId(fileId)) {
            Timber.w("GDriveStreamProxy: getProxyUrl rejected invalid fileId")
            return ""
        }
        return "http://127.0.0.1:$actualPort/gdrive/$fileId"
    }

    /**
     * Parse a `gdrive://` URI and return the proxy URL.
     * Returns null if the URI is not a valid GDrive URI.
     */
    fun resolveGDriveUri(uriString: String): String? {
        val uri = Uri.parse(uriString)
        if (uri.scheme != "gdrive") return null
        val fileId = uri.host ?: return null
        if (!CloudStreamSecurity.validateGDriveFileId(fileId)) return null
        return getProxyUrl(fileId)
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                Timber.d("GDriveStreamProxy started on port $actualPort")
            } catch (e: CancellationException) {
                Timber.d("GDriveStreamProxy start cancelled")
            } catch (e: Exception) {
                Timber.e(e, "Failed to start GDriveStreamProxy")
            }
        }
    }

    fun stop() {
        startJob?.cancel()
        startJob = null
        proxyScope.coroutineContext.cancelChildren()
        server?.stop(1000, 2000)
        server = null
        actualPort = 0
        Timber.d("GDriveStreamProxy stopped")
    }

    private fun createServer(port: Int): ApplicationEngine {
        return embeddedServer(
            CIO,
            host = "127.0.0.1",
            port = port,
            configure = {
                // Keep behavior consistent with the other local proxies during quick restarts.
                reuseAddress = true
            }
        ) {
            routing {
                get("/gdrive/{fileId}") {
                    val fileId = call.parameters["fileId"]
                    if (fileId.isNullOrBlank() || !CloudStreamSecurity.validateGDriveFileId(fileId)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
                        return@get
                    }

                    try {
                        val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
                        if (!rangeValidation.isValid) {
                            call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
                            return@get
                        }

                        val streamUrl = repository.getStreamUrl(fileId)
                        if (!CloudStreamSecurity.isSafeRemoteStreamUrl(
                                url = streamUrl,
                                allowedHostSuffixes = ALLOWED_REMOTE_HOST_SUFFIXES
                            )
                        ) {
                            call.respond(HttpStatusCode.BadGateway, "Rejected upstream stream URL")
                            return@get
                        }

                        val authHeader = repository.getAuthHeader()

                        if (authHeader.isBlank() || authHeader == "Bearer ") {
                            call.respond(HttpStatusCode.Unauthorized, "No auth token")
                            return@get
                        }

                        // Build the request to Google Drive
                        val requestBuilder = Request.Builder()
                            .url(streamUrl)
                            .header("Authorization", authHeader)

                        rangeValidation.normalizedHeader?.let {
                            requestBuilder.header("Range", it)
                        }

                        var response = withContext(Dispatchers.IO) {
                            okHttpClient.newCall(requestBuilder.build()).execute()
                        }

                        // If 401, try refreshing the token and retry once
                        if (response.code == 401) {
                            response.close()
                            Timber.d("GDriveStreamProxy: 401 received, refreshing token...")
                            val refreshResult = repository.refreshAccessToken()
                            if (refreshResult.isSuccess) {
                                val newAuthHeader = repository.getAuthHeader()
                                if (newAuthHeader.isBlank() || newAuthHeader == "Bearer ") {
                                    call.respond(HttpStatusCode.Unauthorized, "Token refresh failed")
                                    return@get
                                }
                                val retryRequest = Request.Builder()
                                    .url(streamUrl)
                                    .header("Authorization", newAuthHeader)
                                rangeValidation.normalizedHeader?.let {
                                    retryRequest.header("Range", it)
                                }
                                response = withContext(Dispatchers.IO) {
                                    okHttpClient.newCall(retryRequest.build()).execute()
                                }
                            }
                        }

                        response.use { upstream ->
                            if (upstream.code != 200 && upstream.code != 206) {
                                call.respond(
                                    CloudStreamSecurity.mapUpstreamStatusToProxyStatus(upstream.code),
                                    "Upstream stream request failed"
                                )
                                return@get
                            }

                            val body = upstream.body

                            val contentTypeHeader = upstream.header("Content-Type")
                            if (!CloudStreamSecurity.isSupportedAudioContentType(contentTypeHeader)) {
                                call.respond(HttpStatusCode.BadGateway, "Unsupported stream content type")
                                return@get
                            }

                            val contentLength = upstream.header("Content-Length")
                            if (!CloudStreamSecurity.isAcceptableContentLength(contentLength)) {
                                call.respond(HttpStatusCode(413, "Payload Too Large"), "Stream content too large")
                                return@get
                            }

                            val contentRange = upstream.header("Content-Range")
                            val acceptRanges = upstream.header("Accept-Ranges")
                            val responseContentType = contentTypeHeader
                                ?.substringBefore(';')
                                ?.trim()
                                ?.let { raw -> runCatching { ContentType.parse(raw) }.getOrNull() }
                                ?: ContentType.Audio.Any

                            if (upstream.code == 206) {
                                call.response.status(HttpStatusCode.PartialContent)
                            } else {
                                call.response.status(HttpStatusCode.OK)
                            }
                            call.response.header("Accept-Ranges", acceptRanges ?: "bytes")
                            contentLength?.let { call.response.header("Content-Length", it) }
                            contentRange?.let { call.response.header("Content-Range", it) }

                            call.respondBytesWriter(contentType = responseContentType) {
                                withContext(Dispatchers.IO) {
                                    body.byteStream().use { input ->
                                        val buffer = ByteArray(64 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            writeFully(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        val msg = e.toString()
                        if (msg.contains("ChannelWriteException") ||
                            msg.contains("ClosedChannelException") ||
                            msg.contains("Broken pipe") ||
                            msg.contains("JobCancellationException")
                        ) {
                            // Client disconnected, normal behavior
                        } else {
                            Timber.e(e, "Error streaming GDrive file $fileId")
                        }
                    }
                }
            }
        }
    }
}
