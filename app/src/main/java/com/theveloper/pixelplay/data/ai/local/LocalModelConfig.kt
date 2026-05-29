package com.theveloper.pixelplay.data.ai.local

/**
 * Configuration for local AI models that can run on-device.
 * Organized by device capability tiers.
 */
data class LocalModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val source: ModelSource,
    val downloadUrl: String,
    val fileSizeBytes: Long,
    val ramRequiredMb: Int,
    val type: ModelType,
    val format: ModelFormat,
    val tags: List<String> = emptyList(),
    val isRecommended: Boolean = false,
    val huggingFaceRepo: String? = null,
    val ollamaTag: String? = null,
    val minAndroidVersion: Int = 24  // Minimum Android version required
)

enum class ModelSource {
    TFLITE,      // TensorFlow Lite models
    HUGGINGFACE, // Hugging Face models (ONNX format)
    ONNX,        // ONNX Runtime models
    USER_IMPORTED // User imported custom models
}

enum class ModelType {
    EMBEDDING,      // For song similarity/recommendations
    TEXT_GENERATION, // For chat/playlist generation
    SENTIMENT,      // For mood analysis
    CLASSIFICATION  // For genre classification
}

enum class ModelFormat(val extension: String) {
    TFLITE("tflite"),
    ONNX("onnx"),
    GGUF("gguf"),
    BIN("bin")
}

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progress: Int, val downloaded: Long) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
    object Importing : ModelStatus()
}

/**
 * Device RAM tiers for model recommendations
 */
enum class DeviceTier(val minRamMb: Int, val maxRamMb: Int, val displayName: String) {
    LOW_END(512, 2048, "Low End (2GB RAM)"),
    MID_RANGE(2048, 4096, "Mid Range (2-4GB RAM)"),
    HIGH_END(4096, 8192, "High End (4-8GB RAM)"),
    FLAGSHIP(8192, Int.MAX_VALUE, "Flagship (8GB+ RAM)")
}

/**
 * Local model catalog with models for all device types
 */
object LocalModelCatalog {

    private fun deviceTier(): DeviceTier {
        val totalRam = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        return when {
            totalRam >= 8192 -> DeviceTier.FLAGSHIP
            totalRam >= 4096 -> DeviceTier.HIGH_END
            totalRam >= 2048 -> DeviceTier.MID_RANGE
            else -> DeviceTier.LOW_END
        }
    }

    val all: List<LocalModelInfo> = listOf(
        // ===== LOW END DEVICES (512MB - 2GB RAM) =====
        LocalModelInfo(
            id = "allminilm_tiny",
            displayName = "Tiny Embeddings",
            description = "Ultra-light embedding model for basic similarity. ~25MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
            fileSizeBytes = 25_000_000,
            ramRequiredMb = 128,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            tags = listOf("embedding", "tiny", "fast"),
            isRecommended = true,
            huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_tiny",
            displayName = "BGE Tiny",
            description = "Small but powerful embeddings. ~40MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main/onnx/model_quantized.onnx",
            fileSizeBytes = 40_000_000,
            ramRequiredMb = 256,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "small"),
            isRecommended = true,
            huggingFaceRepo = "BAAI/bge-small-en-v1.5"
        ),
        LocalModelInfo(
            id = "phi3_quantized",
            displayName = "Phi-3 Mini (Quantized)",
            description = "Microsoft's efficient Phi-3, quantized for mobile. ~400MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            fileSizeBytes = 400_000_000,
            ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3", "microsoft"),
            isRecommended = true,
            huggingFaceRepo = "microsoft/Phi-3-mini-4k-instruct-gguf"
        ),

        // ===== MID RANGE DEVICES (2GB - 4GB RAM) =====
        LocalModelInfo(
            id = "allminilm",
            displayName = "MiniLM Embeddings",
            description = "Balanced embedding model. ~45MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
            fileSizeBytes = 45_000_000,
            ramRequiredMb = 256,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            tags = listOf("embedding", "balanced"),
            huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_base",
            displayName = "BGE Base",
            description = "Better quality embeddings. ~170MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-base-en-v1.5/resolve/main/onnx/model.onnx",
            fileSizeBytes = 170_000_000,
            ramRequiredMb = 512,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "quality"),
            huggingFaceRepo = "BAAI/bge-base-en-v1.5"
        ),
        LocalModelInfo(
            id = "gemma_2b",
            displayName = "Gemma 2B",
            description = "Google's Gemma 2B instruction model. ~1.5GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-q4.gguf",
            fileSizeBytes = 1_500_000_000,
            ramRequiredMb = 2048,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google"),
            huggingFaceRepo = "google/gemma-2b-it"
        ),
        LocalModelInfo(
            id = "llama3_8b_quantized",
            displayName = "Llama 3 8B (Quantized)",
            description = "Meta's Llama 3 8B, quantized. ~4.5GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3-8B-Instruct-Q4_K_M/resolve/main/Llama-3-8B-Instruct-Q4_K_M.gguf",
            fileSizeBytes = 4_500_000_000,
            ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.GGUF,
            tags = listOf("chat", "llama", "meta"),
            huggingFaceRepo = "meta-llama/Llama-3-8B-Instruct-Q4_K_M"
        ),

        // ===== HIGH END DEVICES (4GB - 8GB RAM) =====
        LocalModelInfo(
            id = "bge_large",
            displayName = "BGE Large",
            description = "High quality embeddings. ~560MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-large-en-v1.5/resolve/main/onnx/model.onnx",
            fileSizeBytes = 560_000_000,
            ramRequiredMb = 1024,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "large", "quality"),
            huggingFaceRepo = "BAAI/bge-large-en-v1.5"
        ),
        LocalModelInfo(
            id = "qwen2_72b",
            displayName = "Qwen 2 7B",
            description = "Alibaba's Qwen 2 7B. ~4GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-7B-Instruct-GGUF/Qwen2-7B-Instruct-Q4_K_M.gguf",
            fileSizeBytes = 4_000_000_000,
            ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba"),
            huggingFaceRepo = "Qwen/Qwen2-7B-Instruct-GGUF"
        ),

        // ===== FLAGSHIP DEVICES (8GB+ RAM) =====
        LocalModelInfo(
            id = "llama3_70b",
            displayName = "Llama 3 70B (Quantized)",
            description = "Meta's Llama 3 70B. ~40GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3-70B-Instruct-Q5_K_M/resolve/main/Llama-3-70B-Instruct-Q5_K_M.gguf",
            fileSizeBytes = 40_000_000_000,
            ramRequiredMb = 8192,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.GGUF,
            tags = listOf("chat", "llama", "large", "meta"),
            huggingFaceRepo = "meta-llama/Llama-3-70B-Instruct-Q5_K_M"
        ),

        // ===== USER IMPORT =====
        LocalModelInfo(
            id = "user_imported",
            displayName = "Import Custom Model",
            description = "Import your own .onnx, .tflite, or .gguf model file.",
            source = ModelSource.USER_IMPORTED,
            downloadUrl = "",
            fileSizeBytes = 0,
            ramRequiredMb = 0,
            type = ModelType.TEXT_GENERATION,
            format = ModelFormat.BIN,
            tags = listOf("custom", "import")
        )
    )

    /** Get models suitable for current device */
    fun forCurrentDevice(): List<LocalModelInfo> {
        val tier = deviceTier()
        return all.filter { it.ramRequiredMb <= tier.maxRamMb }
    }

    /** Get recommended models for current device */
    fun recommended(): List<LocalModelInfo> = forCurrentDevice().filter { it.isRecommended }

    /** Get embedding models only */
    fun embeddingModels(): List<LocalModelInfo> = all.filter { it.type == ModelType.EMBEDDING }

    /** Get text generation models only */
    fun textModels(): List<LocalModelInfo> = all.filter { it.type == ModelType.TEXT_GENERATION }

    /** Get downloadable models (not user imported) */
    fun downloadable(): List<LocalModelInfo> = all.filter { it.source != ModelSource.USER_IMPORTED && it.downloadUrl.isNotBlank() }

    /** Get model by ID */
    fun byId(id: String): LocalModelInfo? = all.find { it.id == id }
}