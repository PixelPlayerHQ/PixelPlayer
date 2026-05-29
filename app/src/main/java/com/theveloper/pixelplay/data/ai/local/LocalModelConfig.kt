package com.theveloper.pixelplay.data.ai.local

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
    val minAndroidVersion: Int = 24
)

enum class ModelSource { TFLITE, HUGGINGFACE, ONNX, USER_IMPORTED }
enum class ModelType { EMBEDDING, TEXT_GENERATION, SENTIMENT, CLASSIFICATION }
enum class ModelFormat(val extension: String) { TFLITE("tflite"), ONNX("onnx"), GGUF("gguf"), BIN("bin") }

sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progress: Int, val downloaded: Long) : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
    object Importing : ModelStatus()
}

enum class DeviceTier(val minRamMb: Int, val maxRamMb: Int, val displayName: String) {
    LOW_END(512, 2048, "Low End (2GB RAM)"),
    MID_RANGE(2048, 4096, "Mid Range (2-4GB RAM)"),
    HIGH_END(4096, 8192, "High End (4-8GB RAM)"),
    FLAGSHIP(8192, Int.MAX_VALUE, "Flagship (8GB+ RAM)")
}

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
            fileSizeBytes = 25_000_000, ramRequiredMb = 128,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "tiny", "fast"), isRecommended = true,
            huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_tiny",
            displayName = "BGE Tiny",
            description = "Small but powerful embeddings. ~40MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main/onnx/model_quantized.onnx",
            fileSizeBytes = 40_000_000, ramRequiredMb = 256,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "small"), isRecommended = true,
            huggingFaceRepo = "BAAI/bge-small-en-v1.5"
        ),
        LocalModelInfo(
            id = "phi3_quantized",
            displayName = "Phi-3 Mini (Quantized)",
            description = "Microsoft's efficient Phi-3, quantized for mobile. ~400MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            fileSizeBytes = 400_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3", "microsoft"), isRecommended = true,
            huggingFaceRepo = "microsoft/Phi-3-mini-4k-instruct-gguf"
        ),
        LocalModelInfo(
            id = "tinyllama_1b",
            displayName = "TinyLlama 1.1B",
            description = "Compact Llama-based model. ~700MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
            fileSizeBytes = 700_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "tiny", "llama"), isRecommended = true,
            huggingFaceRepo = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF"
        ),
        LocalModelInfo(
            id = "gemma_2b_q4",
            displayName = "Gemma 2B (Q4)",
            description = "Google's Gemma 2B, Q4 quantized. ~1.2GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-q4.gguf",
            fileSizeBytes = 1_200_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google", "recommended"), isRecommended = true,
            huggingFaceRepo = "google/gemma-2b-it"
        ),

        // ===== MID RANGE DEVICES (2GB - 4GB RAM) =====
        LocalModelInfo(
            id = "allminilm",
            displayName = "MiniLM Embeddings",
            description = "Balanced embedding model. ~45MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx",
            fileSizeBytes = 45_000_000, ramRequiredMb = 256,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "balanced"),
            huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_base",
            displayName = "BGE Base",
            description = "Better quality embeddings. ~170MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-base-en-v1.5/resolve/main/onnx/model.onnx",
            fileSizeBytes = 170_000_000, ramRequiredMb = 512,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "quality"),
            huggingFaceRepo = "BAAI/bge-base-en-v1.5"
        ),
        LocalModelInfo(
            id = "gemma_2b",
            displayName = "Gemma 2B",
            description = "Google's Gemma 2B instruction model. ~1.5GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-2b-it/resolve/main/gemma-2b-it-q4.gguf",
            fileSizeBytes = 1_500_000_000, ramRequiredMb = 2048,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google"), isRecommended = true,
            huggingFaceRepo = "google/gemma-2b-it"
        ),
        LocalModelInfo(
            id = "gemma_1.1_2b",
            displayName = "Gemma 1.1 2B",
            description = "Google's updated Gemma 1.1 2B. ~1.6GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-1.1-2b-it/resolve/main/gemma-1.1-2b-it-q4.gguf",
            fileSizeBytes = 1_600_000_000, ramRequiredMb = 2048,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google"),
            huggingFaceRepo = "google/gemma-1.1-2b-it"
        ),
        LocalModelInfo(
            id = "phi3_medium_q4",
            displayName = "Phi-3 Medium (Q4)",
            description = "Microsoft's Phi-3 Medium 14B, Q4. ~8GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-medium-4k-instruct-gguf/resolve/main/Phi-3-medium-4k-instruct-q4.gguf",
            fileSizeBytes = 8_000_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3", "microsoft", "large"),
            huggingFaceRepo = "microsoft/Phi-3-medium-4k-instruct-gguf"
        ),
        LocalModelInfo(
            id = "llama3_8b_quantized",
            displayName = "Llama 3 8B (Quantized)",
            description = "Meta's Llama 3 8B, quantized. ~4.5GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3-8B-Instruct-Q4_K_M/resolve/main/Llama-3-8B-Instruct-Q4_K_M.gguf",
            fileSizeBytes = 4_500_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "llama", "meta"),
            huggingFaceRepo = "meta-llama/Llama-3-8B-Instruct-Q4_K_M"
        ),
        LocalModelInfo(
            id = "mistral_7b_q4",
            displayName = "Mistral 7B (Q4)",
            description = "Mistral 7B Instruct, Q4 quantized. ~4.1GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.3-GGUF/resolve/main/Mistral-7B-Instruct-v0.3.Q4_K_M.gguf",
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "mistral", "mistralai"),
            huggingFaceRepo = "TheBloke/Mistral-7B-Instruct-v0.3-GGUF"
        ),

        // ===== HIGH END DEVICES (4GB - 8GB RAM) =====
        LocalModelInfo(
            id = "bge_large",
            displayName = "BGE Large",
            description = "High quality embeddings. ~560MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/BAAI/bge-large-en-v1.5/resolve/main/onnx/model.onnx",
            fileSizeBytes = 560_000_000, ramRequiredMb = 1024,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "large", "quality"),
            huggingFaceRepo = "BAAI/bge-large-en-v1.5"
        ),
        LocalModelInfo(
            id = "gemma_2_9b",
            displayName = "Gemma 2 9B",
            description = "Google's Gemma 2 9B. ~5.5GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-2-9b-it/resolve/main/gemma-2-9b-it-q4.gguf",
            fileSizeBytes = 5_500_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google", "quality"),
            huggingFaceRepo = "google/gemma-2-9b-it"
        ),
        LocalModelInfo(
            id = "qwen2_7b",
            displayName = "Qwen 2 7B",
            description = "Alibaba's Qwen 2 7B. ~4GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-7B-Instruct-GGUF/resolve/main/qwen2-7b-instruct-q4_k_m.gguf",
            fileSizeBytes = 4_000_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba"),
            huggingFaceRepo = "Qwen/Qwen2-7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "phi3_14b",
            displayName = "Phi-3 Medium 14B",
            description = "Microsoft's Phi-3 Medium 14B. ~7.8GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/microsoft/Phi-3-medium-128k-instruct-gguf/resolve/main/Phi-3-medium-128k-instruct-q4.gguf",
            fileSizeBytes = 7_800_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3", "microsoft", "large"),
            huggingFaceRepo = "microsoft/Phi-3-medium-128k-instruct-gguf"
        ),

        // ===== FLAGSHIP DEVICES (8GB+ RAM) =====
        LocalModelInfo(
            id = "gemma_2_27b",
            displayName = "Gemma 2 27B",
            description = "Google's Gemma 2 27B. ~16GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/google/gemma-2-27b-it/resolve/main/gemma-2-27b-it-q4.gguf",
            fileSizeBytes = 16_000_000_000, ramRequiredMb = 8192,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma", "google", "large"),
            huggingFaceRepo = "google/gemma-2-27b-it"
        ),
        LocalModelInfo(
            id = "llama3_70b",
            displayName = "Llama 3 70B (Quantized)",
            description = "Meta's Llama 3 70B. ~40GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/meta-llama/Llama-3-70B-Instruct-Q5_K_M/resolve/main/Llama-3-70B-Instruct-Q5_K_M.gguf",
            fileSizeBytes = 40_000_000_000, ramRequiredMb = 8192,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "llama", "large", "meta"),
            huggingFaceRepo = "meta-llama/Llama-3-70B-Instruct-Q5_K_M"
        ),
        LocalModelInfo(
            id = "mixtral_8x22b",
            displayName = "Mixtral 8x22B (Q4)",
            description = "Mistral's Mixtral 8x22B MoE, Q4. ~45GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/TheBloke/Mixtral-8x22B-Instruct-v0.1-GGUF/resolve/main/mixtral-8x22b-instruct-v0.1.Q4_K_M.gguf",
            fileSizeBytes = 45_000_000_000, ramRequiredMb = 12288,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "mixtral", "mistral", "large"),
            huggingFaceRepo = "TheBloke/Mixtral-8x22B-Instruct-v0.1-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2_72b",
            displayName = "Qwen 2 72B (Q4)",
            description = "Alibaba's Qwen 2 72B, Q4. ~42GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = "https://huggingface.co/Qwen/Qwen2-72B-Instruct-GGUF/resolve/main/qwen2-72b-instruct-q4_k_m.gguf",
            fileSizeBytes = 42_000_000_000, ramRequiredMb = 12288,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba", "large"),
            huggingFaceRepo = "Qwen/Qwen2-72B-Instruct-GGUF"
        ),

        // ===== USER IMPORT =====
        LocalModelInfo(
            id = "user_imported", displayName = "Import Custom Model",
            description = "Import your own .onnx, .tflite, or .gguf model file.",
            source = ModelSource.USER_IMPORTED, downloadUrl = "",
            fileSizeBytes = 0, ramRequiredMb = 0,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.BIN,
            tags = listOf("custom", "import")
        )
    )

    fun forCurrentDevice(): List<LocalModelInfo> {
        val tier = deviceTier()
        return all.filter { it.ramRequiredMb <= tier.maxRamMb }
    }

    fun recommended(): List<LocalModelInfo> = forCurrentDevice().filter { it.isRecommended }
    fun embeddingModels(): List<LocalModelInfo> = all.filter { it.type == ModelType.EMBEDDING }
    fun textModels(): List<LocalModelInfo> = all.filter { it.type == ModelType.TEXT_GENERATION }
    fun downloadable(): List<LocalModelInfo> = all.filter { it.source != ModelSource.USER_IMPORTED && it.downloadUrl.isNotBlank() }
    fun byId(id: String): LocalModelInfo? = all.find { it.id == id }
}
