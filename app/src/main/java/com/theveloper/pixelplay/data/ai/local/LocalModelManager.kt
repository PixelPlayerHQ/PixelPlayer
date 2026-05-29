package com.theveloper.pixelplay.data.ai.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LocalModelManager"
private const val MODELS_DIR = "local_ai_models"

/**
 * Manages local AI models lifecycle on device.
 * Handles download, import, deletion, and inference.
 */
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

    suspend fun validateModelFile(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile(modelId)
        if (!file.exists()) return@withContext false
        val info = LocalModelCatalog.byId(modelId) ?: return@withContext true
        val name = file.name
        if (info.fileSizeBytes > 0) {
            val sizeOk = file.length() in (info.fileSizeBytes * 0.8).toLong()..(info.fileSizeBytes * 1.2).toLong()
            if (!sizeOk) { Timber.w("Model size mismatch: ${file.length()} vs expected ${info.fileSizeBytes}"); return@withContext false }
        }
        if (name.endsWith(".gguf")) {
            val bytes = file.inputStream().use { it.readNBytes(4) }
            if (bytes.size < 4 || bytes[0] != 'G'.code.toByte() || bytes[1] != 'G'.code.toByte()) {
                Timber.w("Invalid GGUF magic bytes"); return@withContext false
            }
        }
        true
    }

    // ======== Download Operations ========

    fun downloadModel(info: LocalModelInfo): Flow<ModelStatus> = flow {
        val file = modelFile(info.id)
        val tmp = File(modelsDir, "${info.id}.tmp")

        if (info.downloadUrl.isBlank()) { emit(ModelStatus.Error("No download URL available")); return@flow }
        if (file.exists()) { emit(ModelStatus.Ready); return@flow }
        // Resume interrupted download if tmp exists
        val resumeFrom = if (tmp.exists()) tmp.length() else 0L
        emit(ModelStatus.Downloading(0, resumeFrom))

        try {
            val conn = URL(info.downloadUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 120_000
            conn.setRequestProperty("User-Agent", "PixelPlayer/1.0")
            conn.instanceFollowRedirects = true
            if (resumeFrom > 0) conn.setRequestProperty("Range", "bytes=$resumeFrom-")
            conn.connect()

            val total = conn.contentLengthLong.let { if (it <= 0) -1L else it + resumeFrom }
            var downloaded = resumeFrom

            conn.inputStream.use { input ->
                FileOutputStream(tmp, true).use { output ->
                    val buf = ByteArray(32768)
                    var read: Int
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        downloaded += read
                        val progress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        emit(ModelStatus.Downloading(progress, downloaded))
                    }
                }
            }

            if (!tmp.renameTo(file)) {
                file.delete(); tmp.copyTo(file, overwrite = true); tmp.delete()
            }
            emit(ModelStatus.Ready)
            Timber.i("Downloaded model: ${info.id}")

        } catch (e: Exception) {
            Timber.e(e, "Download failed: ${info.id}")
            emit(ModelStatus.Error(e.message ?: "Download failed"))
        }
    }.flowOn(Dispatchers.IO)

    // ======== Import Operations ========

    suspend fun importModel(uri: Uri, modelId: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = modelFile(modelId)
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Cannot open URI")
            _statusMap.value = _statusMap.value.toMutableMap().also { it[modelId] = ModelStatus.Ready }
            Result.success(file)
        } catch (e: Exception) {
            Timber.e(e, "Import failed")
            Result.failure(e)
        }
    }

    // ======== Delete Operations ========

    suspend fun deleteModel(modelId: String): Boolean = withContext(Dispatchers.IO) {
        val file = modelFile(modelId)
        val deleted = file.delete()
        if (deleted) {
            _statusMap.value = _statusMap.value.toMutableMap().also { it.remove(modelId) }
            if (_activeModelId.value == modelId) _activeModelId.value = null
        }
        deleted
    }

    // ======== Model Selection ========

    fun setActiveModel(modelId: String?) {
        _activeModelId.value = modelId
    }

    // ======== Inference (placeholder – engine integration pending) ========

    suspend fun runInference(modelId: String, prompt: String): String? = withContext(Dispatchers.IO) {
        val file = modelFile(modelId)
        if (!file.exists()) { Timber.w("Model not installed: $modelId"); return@withContext null }
        Timber.d("Inference requested: $modelId (${file.length() / (1024 * 1024)} MB)")
        null
    }

    // ======== Private Helpers ========

    private fun setStatus(modelId: String, status: ModelStatus) {
        _statusMap.value = _statusMap.value.toMutableMap().also { it[modelId] = status }
    }
}