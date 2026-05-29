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

    /** HuggingFace download URL builder for TheBloke-style GGUF repos */
    private fun hfBlake(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file"

    val all: List<LocalModelInfo> = listOf(
        // ======================================================================
        // LOW END DEVICES (512MB - 2GB RAM)
        // ======================================================================

        // -- Embeddings --
        LocalModelInfo(
            id = "allminilm_tiny", displayName = "Tiny Embeddings",
            description = "Ultra-light embedding model ~25MB. Great for similarity search.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("sentence-transformers/all-MiniLM-L6-v2", "onnx/model_quantized.onnx"),
            fileSizeBytes = 25_000_000, ramRequiredMb = 128,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX, isRecommended = true,
            tags = listOf("embedding", "tiny", "fast"), huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_tiny", displayName = "BGE Tiny Embeddings",
            description = "Small but powerful embeddings ~40MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("BAAI/bge-small-en-v1.5", "onnx/model_quantized.onnx"),
            fileSizeBytes = 40_000_000, ramRequiredMb = 256,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX, isRecommended = true,
            tags = listOf("embedding", "bge", "small"), huggingFaceRepo = "BAAI/bge-small-en-v1.5"
        ),

        // -- Text Generation --
        LocalModelInfo(
            id = "tinyllama_1b", displayName = "TinyLlama 1.1B",
            description = "Compact Llama-based model ~700MB. Excellent for mobile.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF", "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"),
            fileSizeBytes = 700_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "tiny", "llama", "recommended"), huggingFaceRepo = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF"
        ),
        LocalModelInfo(
            id = "phi2_q4", displayName = "Phi-2 (Q4)",
            description = "Microsoft's Phi-2 2.7B, Q4 ~1.6GB. Great reasoning for its size.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/phi-2-GGUF", "phi-2.Q4_K_M.gguf"),
            fileSizeBytes = 1_600_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "phi2", "microsoft"), huggingFaceRepo = "TheBloke/phi-2-GGUF"
        ),
        LocalModelInfo(
            id = "gemma_1.1_2b_q4", displayName = "Gemma 1.1 2B (Q4)",
            description = "Google Gemma 1.1 2B, Q4 ~1.2GB. Good general purpose.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Gemma-1.1-2B-it-GGUF", "gemma-1.1-2b-it.Q4_K_M.gguf"),
            fileSizeBytes = 1_200_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "gemma", "google"), huggingFaceRepo = "TheBloke/Gemma-1.1-2B-it-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_0.5b", displayName = "Qwen 2.5 0.5B (Q4)",
            description = "Alibaba's Qwen 2.5 0.5B, Q4 ~350MB. Fastest option.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-0.5B-Instruct-GGUF", "qwen2.5-0.5b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 350_000_000, ramRequiredMb = 256,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba", "fast"), huggingFaceRepo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_1.5b", displayName = "Qwen 2.5 1.5B (Q4)",
            description = "Alibaba's Qwen 2.5 1.5B, Q4 ~900MB. Good quality/size ratio.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-1.5B-Instruct-GGUF", "qwen2.5-1.5b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 900_000_000, ramRequiredMb = 768,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen", "alibaba"), huggingFaceRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "starcoder2_3b", displayName = "StarCoder2 3B (Q4)",
            description = "Code generation model 3B, Q4 ~1.8GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/StarCoder2-3B-GGUF", "starcoder2-3b.Q4_K_M.gguf"),
            fileSizeBytes = 1_800_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "starcoder"), huggingFaceRepo = "TheBloke/StarCoder2-3B-GGUF"
        ),

        // ======================================================================
        // MID RANGE DEVICES (2GB - 4GB RAM)
        // ======================================================================

        // -- Embeddings --
        LocalModelInfo(
            id = "allminilm", displayName = "MiniLM Embeddings",
            description = "Balanced embedding model ~45MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("sentence-transformers/all-MiniLM-L6-v2", "onnx/model.onnx"),
            fileSizeBytes = 45_000_000, ramRequiredMb = 256,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "balanced"), huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),
        LocalModelInfo(
            id = "bge_base", displayName = "BGE Base Embeddings",
            description = "Higher quality embeddings ~170MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("BAAI/bge-base-en-v1.5", "onnx/model.onnx"),
            fileSizeBytes = 170_000_000, ramRequiredMb = 512,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "quality"), huggingFaceRepo = "BAAI/bge-base-en-v1.5"
        ),

        // -- Text Generation --
        LocalModelInfo(
            id = "mistral_7b_q4", displayName = "Mistral 7B v0.2 (Q4)",
            description = "Mistral 7B v0.2 Instruct, Q4 ~4.1GB. Apache 2.0 license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Mistral-7B-Instruct-v0.2-GGUF", "mistral-7b-instruct-v0.2.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "mistral", "recommended"), huggingFaceRepo = "TheBloke/Mistral-7B-Instruct-v0.2-GGUF"
        ),
        LocalModelInfo(
            id = "openhermes_q4", displayName = "OpenHermes 2.5 7B (Q4)",
            description = "OpenHermes 2.5 Mistral 7B, Q4 ~4.1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/OpenHermes-2.5-Mistral-7B-GGUF", "openhermes-2.5-mistral-7b.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "mistral", "openhermes"), huggingFaceRepo = "TheBloke/OpenHermes-2.5-Mistral-7B-GGUF"
        ),
        LocalModelInfo(
            id = "dolphin_llama3_8b", displayName = "Dolphin 2.9 Llama 3 8B (Q4)",
            description = "Dolphin 2.9 Llama 3 8B, Q4 ~4.5GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/dolphin-2.9-llama3-8b-GGUF", "dolphin-2.9-llama3-8b.Q4_K_M.gguf"),
            fileSizeBytes = 4_500_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "dolphin", "llama", "uncensored"), huggingFaceRepo = "TheBloke/dolphin-2.9-llama3-8b-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_7b", displayName = "Qwen 2.5 7B (Q4)",
            description = "Alibaba's Qwen 2.5 7B, Q4 ~4.4GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-7B-Instruct-GGUF", "qwen2.5-7b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 4_400_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba"), huggingFaceRepo = "Qwen/Qwen2.5-7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "yi_1.5_9b", displayName = "Yi 1.5 9B (Q4)",
            description = "Yi 1.5 9B Chat, Q4 ~5.2GB. Apache 2.0 license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Yi-1.5-9B-Chat-GGUF", "yi-1.5-9b-chat.Q4_K_M.gguf"),
            fileSizeBytes = 5_200_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "yi", "01-ai"), huggingFaceRepo = "TheBloke/Yi-1.5-9B-Chat-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_coder_6.7b", displayName = "DeepSeek Coder 6.7B (Q4)",
            description = "DeepSeek Coder 6.7B Instruct, Q4 ~3.9GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/DeepSeek-Coder-6.7B-Instruct-GGUF", "deepseek-coder-6.7b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 3_900_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "deepseek", "coder"), huggingFaceRepo = "TheBloke/DeepSeek-Coder-6.7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "falcon2_11b", displayName = "Falcon 2 11B (Q4)",
            description = "TII Falcon 2 11B, Q4 ~6.1GB. Apache 2.0 license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Falcon2-11B-GGUF", "falcon2-11b.Q4_K_M.gguf"),
            fileSizeBytes = 6_100_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "falcon", "tii"), huggingFaceRepo = "TheBloke/Falcon2-11B-GGUF"
        ),

        // ======================================================================
        // HIGH END DEVICES (4GB - 8GB RAM)
        // ======================================================================

        LocalModelInfo(
            id = "bge_large", displayName = "BGE Large Embeddings",
            description = "High quality embeddings ~560MB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("BAAI/bge-large-en-v1.5", "onnx/model.onnx"),
            fileSizeBytes = 560_000_000, ramRequiredMb = 1024,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "bge", "large", "quality"), huggingFaceRepo = "BAAI/bge-large-en-v1.5"
        ),
        LocalModelInfo(
            id = "mixtral_8x7b_q4", displayName = "Mixtral 8x7B (Q4)",
            description = "Mistral's Mixtral 8x7B MoE, Q4 ~25GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Mixtral-8x7B-Instruct-v0.1-GGUF", "mixtral-8x7b-instruct-v0.1.Q4_K_M.gguf"),
            fileSizeBytes = 25_000_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "mixtral", "mistral", "large"), huggingFaceRepo = "TheBloke/Mixtral-8x7B-Instruct-v0.1-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_14b", displayName = "Qwen 2.5 14B (Q4)",
            description = "Alibaba's Qwen 2.5 14B, Q4 ~8.5GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-14B-Instruct-GGUF", "qwen2.5-14b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 8_500_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen", "alibaba", "quality"), huggingFaceRepo = "Qwen/Qwen2.5-14B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "nous_solar_10.7b", displayName = "Nous Hermes 2 SOLAR 10.7B (Q4)",
            description = "SOLAR 10.7B fine-tuned, Q4 ~6.1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Nous-Hermes-2-SOLAR-10.7B-GGUF", "nous-hermes-2-solar-10.7b.Q4_K_M.gguf"),
            fileSizeBytes = 6_100_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "nous", "solar"), huggingFaceRepo = "TheBloke/Nous-Hermes-2-SOLAR-10.7B-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_v2_lite", displayName = "DeepSeek V2 Lite (Q4)",
            description = "DeepSeek-V2-Lite-Chat, Q4 ~12GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/DeepSeek-V2-Lite-Chat-GGUF", "deepseek-v2-lite-chat.Q4_K_M.gguf"),
            fileSizeBytes = 12_000_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "deepseek", "v2"), huggingFaceRepo = "TheBloke/DeepSeek-V2-Lite-Chat-GGUF"
        ),

        // ======================================================================
        // FLAGSHIP DEVICES (8GB+ RAM)
        // ======================================================================

        LocalModelInfo(
            id = "qwen2.5_32b", displayName = "Qwen 2.5 32B (Q4)",
            description = "Alibaba's Qwen 2.5 32B, Q4 ~19GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-32B-Instruct-GGUF", "qwen2.5-32b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 19_000_000_000, ramRequiredMb = 12288,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba", "large"), huggingFaceRepo = "Qwen/Qwen2.5-32B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_72b", displayName = "Qwen 2.5 72B (Q4)",
            description = "Alibaba's Qwen 2.5 72B, Q4 ~42GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("Qwen/Qwen2.5-72B-Instruct-GGUF", "qwen2.5-72b-instruct-q4_k_m.gguf"),
            fileSizeBytes = 42_000_000_000, ramRequiredMb = 16384,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "alibaba", "large"), huggingFaceRepo = "Qwen/Qwen2.5-72B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "mixtral_8x22b_q4", displayName = "Mixtral 8x22B (Q4)",
            description = "Mistral's Mixtral 8x22B MoE, Q4 ~45GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Mixtral-8x22B-Instruct-v0.1-GGUF", "mixtral-8x22b-instruct-v0.1.Q4_K_M.gguf"),
            fileSizeBytes = 45_000_000_000, ramRequiredMb = 16384,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "mixtral", "mistral", "large"), huggingFaceRepo = "TheBloke/Mixtral-8x22B-Instruct-v0.1-GGUF"
        ),
        LocalModelInfo(
            id = "command_r_plus", displayName = "Command R+ (Q4)",
            description = "Cohere's Command R+ 104B, Q4 ~60GB. CC-BY-NC.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("TheBloke/Command-R-Plus-GGUF", "command-r-plus.Q4_K_M.gguf"),
            fileSizeBytes = 60_000_000_000, ramRequiredMb = 24576,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "cohere", "command", "large"), huggingFaceRepo = "TheBloke/Command-R-Plus-GGUF"
        ),

        // ======================================================================
        // ONNX / TFLITE (Cross-platform)
        // ======================================================================

        LocalModelInfo(
            id = "allminilm_onnx", displayName = "MiniLM ONNX",
            description = "Cross-platform embedding model ~90MB. Works on any device.",
            source = ModelSource.ONNX,
            downloadUrl = hfBlake("sentence-transformers/all-MiniLM-L6-v2", "onnx/model.onnx"),
            fileSizeBytes = 90_000_000, ramRequiredMb = 512,
            type = ModelType.EMBEDDING, format = ModelFormat.ONNX,
            tags = listOf("embedding", "onnx", "cross-platform"), huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2"
        ),

        // ======================================================================
        // USER IMPORT
        // ======================================================================

        LocalModelInfo(
            id = "user_imported", displayName = "Import Custom Model",
            description = "Import your own .onnx, .tflite, or .gguf model file.",
            source = ModelSource.USER_IMPORTED, downloadUrl = "", fileSizeBytes = 0, ramRequiredMb = 0,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.BIN, tags = listOf("custom", "import")
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
