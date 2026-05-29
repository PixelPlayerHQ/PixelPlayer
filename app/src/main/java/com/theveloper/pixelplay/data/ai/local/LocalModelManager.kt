package com.theveloper.pixelplay.data.ai.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "LocalModelManager"
private const val MODELS_DIR = "local_ai_models"
private const val TIMEOUT_CONNECT = 15_000
private const val TIMEOUT_READ = 60_000
private const val BUFFER_SIZE = 65536
private const val MAX_RETRIES = 3
private const val GGUF_MAGIC_0: Byte = 'G'.code.toByte()
private const val GGUF_MAGIC_1: Byte = 'G'.code.toByte()

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val modelsDir: File
        get() = File(context.filesDir, MODELS_DIR).also { it.mkdirs() }

    private val _statusMap = MutableStateFlow<Map<String, ModelStatus>>(emptyMap())
    val statusMap: StateFlow<Map<String, ModelStatus>> = _statusMap.asStateFlow()

    private val _activeModelId = MutableStateFlow<String?>(null)
    val activeModelId: StateFlow<String?> = _activeModelId.asStateFlow()

    private val downloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeDownloads = mutableMapOf<String, Job>()
    private val activeConnections = mutableMapOf<String, HttpURLConnection>()

    // ======== Query Operations ========

    fun getInstalledModels(): List<File> = modelsDir.listFiles()
        ?.filter { it.isFile && it.length() > 1000 }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    fun isInstalled(modelId: String): Boolean = modelFile(modelId).exists()

    fun modelFile(modelId: String): File = File(modelsDir, modelId)

    fun getModelStatus(modelId: String): ModelStatus = _statusMap.value[modelId]
        ?: if (isInstalled(modelId)) ModelStatus.Ready else ModelStatus.NotDownloaded

    fun getModelSize(modelId: String): Long = modelFile(modelId).let { if (it.exists()) it.length() else 0 }

    suspend fun validateModelFile(modelId: String): ValidationResult = withContext(Dispatchers.IO) {
        val file = modelFile(modelId)
        if (!file.exists()) return@withContext ValidationResult.Missing
        val info = LocalModelCatalog.byId(modelId)
        if (info != null && info.fileSizeBytes > 0) {
            val sizeOk = file.length() in (info.fileSizeBytes * 0.8).toLong()..(info.fileSizeBytes * 1.2).toLong()
            if (!sizeOk) return@withContext ValidationResult.SizeMismatch(file.length(), info.fileSizeBytes)
        }
        if (file.name.endsWith(".gguf")) {
            val bytes = file.inputStream().use { it.readNBytes(4) }
            if (bytes.size < 4 || bytes[0] != GGUF_MAGIC_0 || bytes[1] != GGUF_MAGIC_1) {
                return@withContext ValidationResult.Corrupted("Invalid GGUF magic bytes")
            }
        }
        ValidationResult.Ok
    }

    sealed class ValidationResult {
        object Ok : ValidationResult()
        object Missing : ValidationResult()
        data class SizeMismatch(val actual: Long, val expected: Long) : ValidationResult()
        data class Corrupted(val detail: String) : ValidationResult()
    }

    // ======== Download Operations ========

    fun downloadModel(info: LocalModelInfo) {
        if (info.downloadUrl.isBlank()) {
            _statusMap.update { it + (info.id to ModelStatus.Error("No download URL available")) }
            return
        }
        if (modelFile(info.id).exists()) {
            _statusMap.update { it + (info.id to ModelStatus.Ready) }
            return
        }
        if (activeDownloads[info.id]?.isActive == true) return

        val job = downloadScope.launch {
            downloadWithRetry(info)
        }
        activeDownloads[info.id] = job
    }

    private suspend fun downloadWithRetry(info: LocalModelInfo, attempt: Int = 1) {
        _statusMap.update { it + (info.id to ModelStatus.Downloading(0, 0, info.fileSizeBytes)) }
        try {
            performDownload(info)
            activeDownloads.remove(info.id)
            activeConnections.remove(info.id)
        } catch (e: CancellationException) {
            Timber.i("Download cancelled: ${info.id}")
            cleanupTmp(info.id)
            _statusMap.update { it + (info.id to ModelStatus.NotDownloaded) }
            activeDownloads.remove(info.id)
            activeConnections.remove(info.id)
            throw e
        } catch (e: Exception) {
            activeConnections.remove(info.id)
            val retryable = isRetryable(e)
            if (retryable && attempt < MAX_RETRIES) {
                val delayMs = (1L shl (attempt + 1)) * 1000L
                Timber.w("Download attempt $attempt failed for ${info.id}, retrying in ${delayMs}ms: ${e.message}")
                _statusMap.update {
                    it + (info.id to ModelStatus.Downloading(
                        0, getTmpSize(info.id), info.fileSizeBytes, 0
                    ))
                }
                delay(delayMs)
                downloadWithRetry(info, attempt + 1)
            } else {
                Timber.e(e, "Download failed after $attempt attempts: ${info.id}")
                _statusMap.update {
                    it + (info.id to ModelStatus.Error(classifyError(e, attempt)))
                }
                activeDownloads.remove(info.id)
            }
        }
    }

    private suspend fun performDownload(info: LocalModelInfo) {
        val file = modelFile(info.id)
        val tmp = File(modelsDir, "${info.id}.tmp")
        val resumeFrom = if (tmp.exists()) tmp.length() else 0L
        var downloaded = resumeFrom
        val startTime = System.nanoTime()

        val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
        activeConnections[info.id] = conn
        conn.connectTimeout = TIMEOUT_CONNECT
        conn.readTimeout = TIMEOUT_READ
        conn.setRequestProperty("User-Agent", "PixelPlayer/1.0")
        conn.instanceFollowRedirects = true
        if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
        conn.connect()

        val total = conn.contentLengthLong.let { if (it <= 0) -1L else it + resumeFrom }
        val actualTotal = if (total > 0) total else info.fileSizeBytes

        conn.inputStream.use { input ->
            FileOutputStream(tmp, resumeFrom > 0).use { output ->
                val buf = ByteArray(BUFFER_SIZE)
                var read: Int
                while (input.read(buf).also { read = it } != -1) {
                    ensureActive()
                    output.write(buf, 0, read)
                    downloaded += read
                    val elapsed = (System.nanoTime() - startTime) / 1_000_000_000L
                    val speed = if (elapsed > 0) downloaded / elapsed else 0L
                    val progress = if (actualTotal > 0) ((downloaded * 100) / actualTotal).toInt().coerceIn(0, 100) else 0
                    _statusMap.update {
                        it + (info.id to ModelStatus.Downloading(progress, downloaded, actualTotal, speed))
                    }
                }
            }
        }

        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        _statusMap.update { it + (info.id to ModelStatus.Ready) }
        Timber.i("Downloaded model: ${info.id} (${downloaded / (1024 * 1024)} MB)")
    }

    fun cancelDownload(modelId: String) {
        activeConnections[modelId]?.disconnect()
        activeDownloads[modelId]?.cancel()
    }

    // ======== Import Operations ========

    suspend fun importModel(uri: Uri, modelId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = modelFile(modelId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot open URI")
            _statusMap.update { it + (modelId to ModelStatus.Ready) }
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Import failed")
            Result.failure(e)
        }
    }

    // ======== Delete Operations ========

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        cancelDownload(modelId)
        val file = modelFile(modelId)
        val deleted = file.delete()
        cleanupTmp(modelId)
        if (deleted) {
            _statusMap.update { it - modelId }
            if (_activeModelId.value == modelId) _activeModelId.value = null
        }
        deleted
    }

    // ======== Model Selection ========

    fun seedStatus(modelId: String, status: ModelStatus) {
        _statusMap.update { it + (modelId to status) }
    }

    fun setActiveModel(modelId: String?) {
        _activeModelId.value = modelId
    }

    // ======== Cleanup ========

    fun cleanupScope() {
        downloadScope.cancel()
    }

    // ======== Inference (placeholder – engine integration pending) ========

    suspend fun runInference(modelId: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val file = modelFile(modelId)
        if (!file.exists()) { Timber.w("Model not installed: $modelId"); return@withContext null }
        val validation = validateModelFile(modelId)
        if (validation !is ValidationResult.Ok) {
            Timber.w("Model validation failed for $modelId: $validation")
            return@withContext null
        }
        Timber.d("Inference requested: $modelId (${file.length() / (1024 * 1024)} MB)")
        null
    }

    // ======== Private Helpers ========

    private fun cleanupTmp(modelId: String) {
        File(modelsDir, "${modelId}.tmp").delete()
    }

    private fun getTmpSize(modelId: String): Long {
        val tmp = File(modelsDir, "${modelId}.tmp")
        return if (tmp.exists()) tmp.length() else 0L
    }

    private fun isRetryable(e: Exception): Boolean = when (e) {
        is SocketTimeoutException, is UnknownHostException -> true
        is IOException -> e.message?.contains("timed out", ignoreCase = true) == true
            || e.message?.contains("reset", ignoreCase = true) == true
            || e.message?.contains("refused", ignoreCase = true) == true
        else -> false
    }

    private fun classifyError(e: Exception, attempt: Int): String = when (e) {
        is SocketTimeoutException -> "Connection timed out. Check your network."
        is UnknownHostException -> "Cannot reach server. Check your internet connection."
        is IOException -> {
            when {
                e.message?.contains("Unable to resolve host") == true -> "DNS resolution failed. Check network."
                e.message?.contains("Permission denied") == true -> "Storage permission required."
                e.message?.contains("No space") == true -> "Not enough storage space."
                else -> "Network error: ${e.message ?: "Unknown"}"
            }
        }
        else -> if (attempt >= MAX_RETRIES) "Download failed after $attempt attempts: ${e.message ?: "Unknown error"}"
        else e.message ?: "Download failed"
    }
}
