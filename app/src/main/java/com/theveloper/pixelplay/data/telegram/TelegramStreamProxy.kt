package com.theveloper.pixelplay.data.telegram

import com.theveloper.pixelplay.utils.LogUtils
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
import kotlin.math.min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import java.net.ServerSocket
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelegramStreamProxy @Inject constructor(
    private val telegramRepository: TelegramRepository
) {
    private var server: ApplicationEngine? = null
    private val proxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startJob: Job? = null

    private fun createServer(port: Int): ApplicationEngine {
        return embeddedServer(
            CIO,
            host = "127.0.0.1",
            port = port,
            configure = {
                // Fast proxy restarts can otherwise fail if the previous socket is still draining.
                reuseAddress = true
            }
        ) {
            routing {
                get("/stream/{fileId}") {
                    val fileId = call.parameters["fileId"]?.toIntOrNull()
                    if (fileId == null || !CloudStreamSecurity.validateTelegramFileId(fileId)) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid File ID")
                        return@get
                    }

                    LogUtils.d("StreamProxy", "Request for fileId: $fileId")
                    
                    // Wait for TDLib to be ready before attempting download
                    // This fixes playback failures after app restart
                    if (!telegramRepository.isReady()) {
                        LogUtils.w("StreamProxy", "TDLib not ready, waiting...")
                        val ready = telegramRepository.awaitReady(10_000L) // 10 second timeout
                        if (!ready) {
                            LogUtils.e("StreamProxy", null, "TDLib not ready after timeout")
                            call.respond(HttpStatusCode.ServiceUnavailable, "Telegram client not ready")
                            return@get
                        }
                        LogUtils.d("StreamProxy", "TDLib ready, proceeding with request")
                    }
                    
                    // 1. Ensure download is started/active
                    var fileInfo = telegramRepository.downloadFile(fileId, 1)
                    
                    // 2. Wait for path to be assigned (TDLib might take a moment to allocate the file path)
                    var pathWaitCount = 0
                    while (fileInfo?.local?.path.isNullOrEmpty() && pathWaitCount < 50) { // Wait up to 2.5s
                        delay(50)
                        fileInfo = telegramRepository.getFile(fileId)
                        pathWaitCount++
                    }

                    if (fileInfo?.local?.path.isNullOrEmpty()) {
                        LogUtils.e("StreamProxy", null, "downloadFile returned null/empty path for fileId: $fileId after waiting")
                        call.respond(HttpStatusCode.InternalServerError, "Could not get file path")
                        return@get
                    }
                    
                    val path = fileInfo!!.local.path
                    var expectedSize = fileInfo.expectedSize

                    // Optional hint from caller; only trusted within sane limits.
                    val knownSize = call.parameters["size"]?.toLongOrNull()?.takeIf {
                        it in 1..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES
                    } ?: 0L
                    if (knownSize > 0 && expectedSize <= 0L) {
                        expectedSize = knownSize
                    }

                    val file = File(path)

                    // Wait for file to be created by TDLib
                    var waitCount = 0
                    // Wait up to 15 seconds (300 * 50ms) for file to appear
                    while (!file.exists() && waitCount < 300) {
                         delay(50)
                         waitCount++
                         if (waitCount % 20 == 0) {
                             LogUtils.d("StreamProxy", "Waiting for file creation: $path ($waitCount/300)")
                         }
                    }
                    
                    if (!file.exists()) {
                         LogUtils.e("StreamProxy", null, "File not created by TDLib after 15s timeout: $path")
                         call.respond(HttpStatusCode.InternalServerError, "File not created by TDLib")
                         return@get
                    }

                    if (!file.isFile) {
                        call.respond(HttpStatusCode.NotFound, "Invalid file")
                        return@get
                    }

                    if (expectedSize <= 0L) {
                        expectedSize = file.length()
                    }
                    if (expectedSize > CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES) {
                        call.respond(HttpStatusCode(413, "Payload Too Large"), "File too large for proxy streaming")
                        return@get
                    }

                    // Range Handling
                    val rangeValidation = CloudStreamSecurity.validateRangeHeader(call.request.headers["Range"])
                    if (!rangeValidation.isValid) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid range header")
                        return@get
                    }

                    val isRangeRequest = rangeValidation.normalizedHeader != null
                    var start = 0L
                    var end = if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1

                    if (isRangeRequest) {
                        if (rangeValidation.isSuffixRange) {
                            if (expectedSize <= 0L) {
                                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Suffix range requires known size")
                                return@get
                            }
                            val suffixLength = rangeValidation.endInclusive ?: 0L
                            if (suffixLength <= 0L) {
                                call.respond(HttpStatusCode(416, "Range Not Satisfiable"), "Invalid suffix range")
                                return@get
                            }
                            start = (expectedSize - suffixLength).coerceAtLeast(0L)
                            end = expectedSize - 1
                        } else {
                            start = rangeValidation.startInclusive ?: 0L
                            end = rangeValidation.endInclusive ?: if (expectedSize > 0) expectedSize - 1 else Long.MAX_VALUE - 1
                        }
                    }

                    // Cap end at expectedSize if known
                    if (expectedSize > 0 && end >= expectedSize) {
                        end = expectedSize - 1
                    }

                    if (expectedSize > 0 && start >= expectedSize) {
                        call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                        return@get
                    }

                    if (start > end || start < 0 || end < 0) {
                         call.respond(HttpStatusCode(416, "Range Not Satisfiable"))
                         return@get
                    }

                    val contentLength = end - start + 1

                    call.response.header("Accept-Ranges", "bytes")
                    if (isRangeRequest) {
                        if (expectedSize > 0) {
                            call.response.header("Content-Range", "bytes $start-$end/$expectedSize")
                        }
                        call.response.header("Content-Length", contentLength.toString())
                        call.response.status(HttpStatusCode.PartialContent)
                    } else if (expectedSize > 0) {
                        call.response.header("Content-Length", expectedSize.toString())
                        call.response.status(HttpStatusCode.OK)
                    }

                    // Stream the file
                    call.respondBytesWriter(contentType = ContentType.Audio.Any) {
                        val raf = RandomAccessFile(file, "r")
                        try {
                            var currentPos = start
                            val buffer = ByteArray(64 * 1024) // Increased to 64KB for smoother streaming
                            var noDataCount = 0
                            
                            raf.seek(currentPos)
                            
                            var cachedDownloadedPrefixSize = fileInfo?.local?.downloadedPrefixSize?.toLong() ?: 0L

                            while (true) {
                                // 1. Check if we've reached the end of the requested range
                                val remaining = end - currentPos + 1
                                if (remaining <= 0) break

                                // 2. Check strict limit based on valid downloaded bytes
                                if (currentPos >= cachedDownloadedPrefixSize) {
                                    // We reached the limit of what we know is downloaded. Refresh info.
                                    val updatedInfo = telegramRepository.getFile(fileId)
                                    cachedDownloadedPrefixSize = updatedInfo?.local?.downloadedPrefixSize?.toLong() ?: 0L

                                    // If still no new data, wait or check completion
                                    if (currentPos >= cachedDownloadedPrefixSize) {
                                        if (updatedInfo?.local?.isDownloadingCompleted == true) {
                                            // Download completed. If we are at/past expectation, we are done.
                                            // If size is different than expected, we still stop because we can't get more.
                                            break
                                        }
                                        
                                        // Verify cancellation/failure
                                        if (updatedInfo?.local?.isDownloadingCompleted == false && !updatedInfo.local.canBeDownloaded) {
                                             break // Failed/Cancelled
                                        }
                                        
                                        delay(50) // Wait for more data
                                        continue
                                    }
                                }

                                // 3. Determine safe read amount
                                // Read min of: buffer size, remaining in range, remaining valid bytes
                                val remainingValid = cachedDownloadedPrefixSize - currentPos
                                val toRead = min(buffer.size.toLong(), min(remaining, remainingValid)).toInt()
                                
                                val read = raf.read(buffer, 0, toRead)
                                if (read > 0) {
                                    writeFully(buffer, 0, read)
                                    currentPos += read
                                    noDataCount = 0
                                } else {
                                    // Should not happen if logic matches, but safety check
                                    delay(10)
                                }
                            }
                        } catch (e: Exception) {
                            // Check for common specific errors to avoid noise
                            val msg = e.toString()
                            if (msg.contains("ChannelWriteException") || 
                                msg.contains("ClosedChannelException") || 
                                msg.contains("Broken pipe") ||
                                msg.contains("WriteTimeoutException") ||
                                msg.contains("JobCancellationException")) {
                                 // Client disconnected, normal behavior
                            } else {
                                 LogUtils.e("StreamProxy", e, "Streaming error")
                            }
                        } finally {
                            raf.close()
                        }
                    }
                }
            }
        }
    }

    private var actualPort: Int = 0

    fun startIfNeeded() {
        if (isReady() || startJob?.isActive == true) return
        start()
    }

    fun start() {
        startJob?.cancel()
        startJob = proxyScope.launch {
            try {
                // Pre-resolve a free port since CIO doesn't support port 0 with resolvedConnectors()
                val freePort = ServerSocket(0).use { it.localPort }
                val createdServer = createServer(freePort)
                createdServer.start(wait = false)
                server = createdServer
                actualPort = freePort
                LogUtils.d("StreamProxy", "Started on port $actualPort")
            } catch (e: CancellationException) {
                LogUtils.d("StreamProxy", "Start cancelled")
            } catch (e: Exception) {
                LogUtils.e("StreamProxy", e, "Failed to start server")
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
        LogUtils.d("StreamProxy", "Stopped")
    }
    
    fun getProxyUrl(fileId: Int, knownSize: Long = 0): String {
        if (actualPort == 0) {
            LogUtils.w("StreamProxy", "getProxyUrl called but actualPort is 0")
            return ""
        }
        if (!CloudStreamSecurity.validateTelegramFileId(fileId)) {
            LogUtils.w("StreamProxy", "getProxyUrl rejected invalid fileId: $fileId")
            return ""
        }
        val safeKnownSize = knownSize.takeIf { it in 0..CloudStreamSecurity.MAX_STREAM_CONTENT_LENGTH_BYTES } ?: 0L
        val url = "http://127.0.0.1:$actualPort/stream/$fileId?size=$safeKnownSize"
        LogUtils.d("StreamProxy", "Generated Proxy URL: $url")
        return url
    }
    
    /**
     * Quick check if the proxy server is ready (port is bound).
     */
    fun isReady(): Boolean = actualPort > 0
    
    /**
     * Suspends until the proxy server is ready (port bound).
     * @param timeoutMs Maximum time to wait
     * @return true if ready, false if timed out
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000L): Boolean {
        if (isReady()) return true
        
        val stepMs = 50L
        var elapsed = 0L
        while (elapsed < timeoutMs) {
            if (isReady()) {
                LogUtils.d("StreamProxy", "awaitReady: Server ready after ${elapsed}ms")
                return true
            }
            delay(stepMs)
            elapsed += stepMs
        }
        LogUtils.e("StreamProxy", null, "awaitReady: Timeout after ${timeoutMs}ms")
        return false
    }

    suspend fun ensureReady(timeoutMs: Long = 10_000L): Boolean {
        startIfNeeded()
        return awaitReady(timeoutMs)
    }
    
}
