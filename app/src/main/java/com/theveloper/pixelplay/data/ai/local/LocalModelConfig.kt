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
    data class Downloading(
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val speedBytesPerSec: Long = 0L
    ) : ModelStatus() {
        val etaSeconds: Long get() =
            if (speedBytesPerSec > 0L && totalBytes > 0L) (totalBytes - bytesDownloaded) / speedBytesPerSec else 0L
    }
    data class Pending(val reason: String = "Waiting...") : ModelStatus()
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

    private fun hfBlake(repo: String, file: String) = "https://huggingface.co/$repo/resolve/main/$file"

    private fun qwenGGUF(model: String) = hfBlake("Qwen/Qwen2.5-${model}B-Instruct-GGUF", "qwen2.5-${model}b-instruct-q4_k_m.gguf")

    private fun qwenCoderGGUF(model: String) = hfBlake("Qwen/Qwen2.5-Coder-${model}B-Instruct-GGUF", "qwen2.5-coder-${model}b-instruct-q4_k_m.gguf")

    val all: List<LocalModelInfo> = listOf(
        // ======================================================================
        // LOW END DEVICES (512MB - 2GB RAM)
        // ======================================================================

        // -- Embeddings (low-end) --
        LocalModelInfo(
            id = "allminilm_tiny", displayName = "Tiny Embeddings",
            description = "Ultra-light embedding ~25MB. Great for similarity search.",
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

        // -- Chat/Generation (≤1.5B, Q4) --
        LocalModelInfo(
            id = "qwen2.5_0.5b", displayName = "Qwen 2.5 0.5B (Q4)",
            description = "Fastest option ~350MB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenGGUF("0.5"), fileSizeBytes = 350_000_000, ramRequiredMb = 256,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "qwen", "fast"), huggingFaceRepo = "Qwen/Qwen2.5-0.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "smollm2_1.7b", displayName = "SmolLM2 1.7B (Q4)",
            description = "HuggingFace SmolLM2 ~1GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/SmolLM2-1.7B-Instruct-GGUF", "smollm2-1.7b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 1_000_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "smollm", "huggingface"), huggingFaceRepo = "bartowski/SmolLM2-1.7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_1.5b", displayName = "Qwen 2.5 1.5B (Q4)",
            description = "Good quality/size ratio ~900MB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenGGUF("1.5"), fileSizeBytes = 900_000_000, ramRequiredMb = 768,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen"), huggingFaceRepo = "Qwen/Qwen2.5-1.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_coder_1.5b", displayName = "Qwen 2.5 Coder 1.5B (Q4)",
            description = "Code-optimized 1.5B ~900MB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenCoderGGUF("1.5"), fileSizeBytes = 900_000_000, ramRequiredMb = 768,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "qwen"), huggingFaceRepo = "Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "tinyllama_1b", displayName = "TinyLlama 1.1B",
            description = "Compact Llama-based ~700MB. Great for mobile.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/TinyLlama-1.1B-Chat-v1.0-GGUF", "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"),
            fileSizeBytes = 700_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "tiny", "llama"), huggingFaceRepo = "bartowski/TinyLlama-1.1B-Chat-v1.0-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_coder_1.3b", displayName = "DeepSeek Coder 1.3B (Q4)",
            description = "Compact code model ~800MB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/DeepSeek-Coder-1.3B-Instruct-GGUF", "deepseek-coder-1.3b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 800_000_000, ramRequiredMb = 512,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "deepseek"), huggingFaceRepo = "bartowski/DeepSeek-Coder-1.3B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "stablelm2_1.6b", displayName = "StableLM 2 1.6B (Q4)",
            description = "Stability AI's efficient model ~1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/stablelm-2-1_6b-zephyr-GGUF", "stablelm-2-1_6b-zephyr.Q4_K_M.gguf"),
            fileSizeBytes = 1_000_000_000, ramRequiredMb = 768,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "stable", "stability"), huggingFaceRepo = "bartowski/stablelm-2-1_6b-zephyr-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_r1_distill_1.5b", displayName = "DeepSeek R1 Distill 1.5B (Q4)",
            description = "DeepSeek R1 reasoning distilled to Qwen 1.5B ~900MB. MIT.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF", "deepseek-r1-distill-qwen-1.5b.Q4_K_M.gguf"),
            fileSizeBytes = 900_000_000, ramRequiredMb = 768,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "deepseek", "reasoning"), huggingFaceRepo = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF"
        ),

        // ======================================================================
        // MID RANGE LOW (2 - 3GB RAM)
        // ======================================================================

        LocalModelInfo(
            id = "qwen2.5_coder_0.5b", displayName = "Qwen 2.5 Coder 0.5B (Q4)",
            description = "Tiny code model ~350MB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenCoderGGUF("0.5"), fileSizeBytes = 350_000_000, ramRequiredMb = 256,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "qwen", "tiny"), huggingFaceRepo = "Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "phi2_q4", displayName = "Phi-2 (Q4)",
            description = "Microsoft Phi-2 2.7B ~1.6GB. Great reasoning.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/phi-2-GGUF", "phi-2.Q4_K_M.gguf"),
            fileSizeBytes = 1_600_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "phi2"), huggingFaceRepo = "bartowski/phi-2-GGUF"
        ),
        LocalModelInfo(
            id = "gemma_1.1_2b_q4", displayName = "Gemma 1.1 2B (Q4)",
            description = "Google Gemma 1.1 2B ~1.2GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Gemma-1.1-2B-it-GGUF", "gemma-1.1-2b-it.Q4_K_M.gguf"),
            fileSizeBytes = 1_200_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "gemma"), huggingFaceRepo = "bartowski/Gemma-1.1-2B-it-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_3b", displayName = "Qwen 2.5 3B (Q4)",
            description = "Balanced 3B model ~1.8GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenGGUF("3"), fileSizeBytes = 1_800_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen", "balanced"), huggingFaceRepo = "Qwen/Qwen2.5-3B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "llama3.2_3b", displayName = "Llama 3.2 3B (Q4)",
            description = "Meta Llama 3.2 3B ~1.8GB. Llama 3.2 license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Llama-3.2-3B-Instruct-GGUF", "llama-3.2-3b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 1_800_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "llama"), huggingFaceRepo = "bartowski/Llama-3.2-3B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "starcoder2_3b", displayName = "StarCoder2 3B (Q4)",
            description = "Code gen 3B ~1.8GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/StarCoder2-3B-GGUF", "starcoder2-3b.Q4_K_M.gguf"),
            fileSizeBytes = 1_800_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "starcoder"), huggingFaceRepo = "bartowski/StarCoder2-3B-GGUF"
        ),
        LocalModelInfo(
            id = "phi3_mini_q4", displayName = "Phi-3 Mini 3.8B (Q4)",
            description = "Microsoft Phi-3 Mini 3.8B ~2.3GB. MIT via bartowski.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Phi-3-mini-4k-instruct-GGUF", "phi-3-mini-4k-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 2_300_000_000, ramRequiredMb = 1536,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3"), huggingFaceRepo = "bartowski/Phi-3-mini-4k-instruct-GGUF"
        ),
        LocalModelInfo(
            id = "phi3.5_mini", displayName = "Phi-3.5 Mini 3.8B (Q4)",
            description = "Microsoft Phi-3.5 Mini ~2.3GB. MIT via bartowski.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Phi-3.5-mini-instruct-GGUF", "phi-3.5-mini-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 2_300_000_000, ramRequiredMb = 1536,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "phi3"), huggingFaceRepo = "bartowski/Phi-3.5-mini-instruct-GGUF"
        ),
        LocalModelInfo(
            id = "granite3_2b", displayName = "Granite 3.0 2B (Q4)",
            description = "IBM Granite 3.0 2B ~1.3GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Granite-3.0-2B-Instruct-GGUF", "granite-3.0-2b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 1_300_000_000, ramRequiredMb = 1024,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "granite", "ibm"), huggingFaceRepo = "bartowski/Granite-3.0-2B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "zephyr_3b", displayName = "Zephyr 3B (Q4)",
            description = "HuggingFace Zephyr 3B ~1.8GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Zephyr-3B-GGUF", "zephyr-3b.Q4_K_M.gguf"),
            fileSizeBytes = 1_800_000_000, ramRequiredMb = 1536,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "zephyr", "huggingface"), huggingFaceRepo = "bartowski/Zephyr-3B-GGUF"
        ),

        // ======================================================================
        // MID RANGE DEVICES (3GB - 4GB RAM)
        // ======================================================================

        // -- Embeddings (mid-range) --
        LocalModelInfo(
            id = "allminilm", displayName = "MiniLM Embeddings",
            description = "Balanced embedding ~45MB.",
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

        // -- Text Generation (7B-9B) --
        LocalModelInfo(
            id = "mistral_7b_v0.2", displayName = "Mistral 7B v0.2 (Q4)",
            description = "Mistral 7B v0.2 Instruct ~4.1GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Mistral-7B-Instruct-v0.2-GGUF", "mistral-7b-instruct-v0.2.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "mistral", "recommended"), huggingFaceRepo = "bartowski/Mistral-7B-Instruct-v0.2-GGUF"
        ),
        LocalModelInfo(
            id = "mistral_7b_v0.3", displayName = "Mistral 7B v0.3 (Q4)",
            description = "Mistral 7B v0.3 Instruct ~4.1GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Mistral-7B-Instruct-v0.3-GGUF", "mistral-7b-instruct-v0.3.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "mistral"), huggingFaceRepo = "bartowski/Mistral-7B-Instruct-v0.3-GGUF"
        ),
        LocalModelInfo(
            id = "openhermes_7b", displayName = "OpenHermes 2.5 7B (Q4)",
            description = "Fine-tuned Mistral 7B ~4.1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/OpenHermes-2.5-Mistral-7B-GGUF", "openhermes-2.5-mistral-7b.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "mistral", "openhermes"), huggingFaceRepo = "bartowski/OpenHermes-2.5-Mistral-7B-GGUF"
        ),
        LocalModelInfo(
            id = "openchat_7b", displayName = "OpenChat 3.5 7B (Q4)",
            description = "OpenChat 3.5 ~4.1GB. Apache 2.0 license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/OpenChat-3.5-0106-GGUF", "openchat-3.5-0106.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "openchat"), huggingFaceRepo = "bartowski/OpenChat-3.5-0106-GGUF"
        ),
        LocalModelInfo(
            id = "dolphin_llama3_8b", displayName = "Dolphin 2.9 Llama 3 8B (Q4)",
            description = "Dolphin 2.9 ~4.5GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/dolphin-2.9-llama3-8b-GGUF", "dolphin-2.9-llama3-8b.Q4_K_M.gguf"),
            fileSizeBytes = 4_500_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "dolphin"), huggingFaceRepo = "bartowski/dolphin-2.9-llama3-8b-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_7b", displayName = "Qwen 2.5 7B (Q4)",
            description = "Qwen 2.5 7B ~4.4GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenGGUF("7"), fileSizeBytes = 4_400_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen"), huggingFaceRepo = "Qwen/Qwen2.5-7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_r1_distill_7b", displayName = "DeepSeek R1 Distill 7B (Q4)",
            description = "DeepSeek R1 reasoning distilled to Qwen 7B ~4.4GB. MIT.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF", "deepseek-r1-distill-qwen-7b.Q4_K_M.gguf"),
            fileSizeBytes = 4_400_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "deepseek", "reasoning"), huggingFaceRepo = "bartowski/DeepSeek-R1-Distill-Qwen-7B-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_coder_6.7b", displayName = "DeepSeek Coder 6.7B (Q4)",
            description = "DeepSeek Coder 6.7B ~3.9GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/DeepSeek-Coder-6.7B-Instruct-GGUF", "deepseek-coder-6.7b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 3_900_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "deepseek"), huggingFaceRepo = "bartowski/DeepSeek-Coder-6.7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_coder_7b", displayName = "Qwen 2.5 Coder 7B (Q4)",
            description = "Qwen code model ~4.4GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenCoderGGUF("7"), fileSizeBytes = 4_400_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "qwen"), huggingFaceRepo = "Qwen/Qwen2.5-Coder-7B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "yi_1.5_6b", displayName = "Yi 1.5 6B (Q4)",
            description = "Yi 1.5 6B Chat ~3.5GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Yi-1.5-6B-Chat-GGUF", "yi-1.5-6b-chat.Q4_K_M.gguf"),
            fileSizeBytes = 3_500_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "yi"), huggingFaceRepo = "bartowski/Yi-1.5-6B-Chat-GGUF"
        ),
        LocalModelInfo(
            id = "yi_1.5_9b", displayName = "Yi 1.5 9B (Q4)",
            description = "Yi 1.5 9B Chat ~5.2GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Yi-1.5-9B-Chat-GGUF", "yi-1.5-9b-chat.Q4_K_M.gguf"),
            fileSizeBytes = 5_200_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "yi"), huggingFaceRepo = "bartowski/Yi-1.5-9B-Chat-GGUF"
        ),
        LocalModelInfo(
            id = "falcon2_11b", displayName = "Falcon 2 11B (Q4)",
            description = "TII Falcon 2 11B ~6.1GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Falcon2-11B-GGUF", "falcon2-11b.Q4_K_M.gguf"),
            fileSizeBytes = 6_100_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "falcon"), huggingFaceRepo = "bartowski/Falcon2-11B-GGUF"
        ),
        LocalModelInfo(
            id = "stablelm2_12b", displayName = "StableLM 2 12B (Q4)",
            description = "Stability AI's 12B model ~7GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/stablelm-2-12b-chat-GGUF", "stablelm-2-12b-chat.Q4_K_M.gguf"),
            fileSizeBytes = 7_000_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "stable", "stability"), huggingFaceRepo = "bartowski/stablelm-2-12b-chat-GGUF"
        ),
        LocalModelInfo(
            id = "starcoder2_7b", displayName = "StarCoder2 7B (Q4)",
            description = "Code gen 7B ~4.1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/StarCoder2-7B-GGUF", "starcoder2-7b.Q4_K_M.gguf"),
            fileSizeBytes = 4_100_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "starcoder"), huggingFaceRepo = "bartowski/StarCoder2-7B-GGUF"
        ),
        LocalModelInfo(
            id = "phi3_small_7b", displayName = "Phi-3 Small 7B (Q4)",
            description = "Microsoft Phi-3 Small 7B ~4.2GB. MIT via bartowski.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Phi-3-small-8k-instruct-GGUF", "phi-3-small-8k-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 4_200_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "phi3"), huggingFaceRepo = "bartowski/Phi-3-small-8k-instruct-GGUF"
        ),
        LocalModelInfo(
            id = "gemma_1.1_7b_q4", displayName = "Gemma 1.1 7B (Q4)",
            description = "Google Gemma 1.1 7B ~4.3GB.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Gemma-1.1-7B-it-GGUF", "gemma-1.1-7b-it.Q4_K_M.gguf"),
            fileSizeBytes = 4_300_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "gemma"), huggingFaceRepo = "bartowski/Gemma-1.1-7B-it-GGUF"
        ),
        LocalModelInfo(
            id = "granite3_8b", displayName = "Granite 3.0 8B (Q4)",
            description = "IBM Granite 3.0 8B ~4.5GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Granite-3.0-8B-Instruct-GGUF", "granite-3.0-8b-instruct.Q4_K_M.gguf"),
            fileSizeBytes = 4_500_000_000, ramRequiredMb = 3072,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "granite", "ibm"), huggingFaceRepo = "bartowski/Granite-3.0-8B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "mistral_nemo_12b", displayName = "Mistral Nemo 12B (Q4)",
            description = "Mistral AI & NVIDIA Nemo 12B ~7GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Mistral-Nemo-Instruct-2407-GGUF", "mistral-nemo-instruct-2407.Q4_K_M.gguf"),
            fileSizeBytes = 7_000_000_000, ramRequiredMb = 4096,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "mistral", "nemo"), huggingFaceRepo = "bartowski/Mistral-Nemo-Instruct-2407-GGUF"
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
            id = "qwen2.5_14b", displayName = "Qwen 2.5 14B (Q4)",
            description = "Qwen 2.5 14B ~8.5GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenGGUF("14"), fileSizeBytes = 8_500_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF, isRecommended = true,
            tags = listOf("chat", "qwen", "quality"), huggingFaceRepo = "Qwen/Qwen2.5-14B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "qwen2.5_coder_14b", displayName = "Qwen 2.5 Coder 14B (Q4)",
            description = "Qwen code model 14B ~8.5GB. Apache 2.0.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = qwenCoderGGUF("14"), fileSizeBytes = 8_500_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "qwen", "large"), huggingFaceRepo = "Qwen/Qwen2.5-Coder-14B-Instruct-GGUF"
        ),
        LocalModelInfo(
            id = "nous_solar_10.7b", displayName = "Nous Hermes 2 SOLAR 10.7B (Q4)",
            description = "SOLAR 10.7B finetune ~6.1GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/Nous-Hermes-2-SOLAR-10.7B-GGUF", "nous-hermes-2-solar-10.7b.Q4_K_M.gguf"),
            fileSizeBytes = 6_100_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "nous"), huggingFaceRepo = "bartowski/Nous-Hermes-2-SOLAR-10.7B-GGUF"
        ),
        LocalModelInfo(
            id = "deepseek_v2_lite", displayName = "DeepSeek V2 Lite (Q4)",
            description = "DeepSeek V2 Lite 16B ~12GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/DeepSeek-V2-Lite-Chat-GGUF", "deepseek-v2-lite-chat.Q4_K_M.gguf"),
            fileSizeBytes = 12_000_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("chat", "deepseek", "v2"), huggingFaceRepo = "bartowski/DeepSeek-V2-Lite-Chat-GGUF"
        ),
        LocalModelInfo(
            id = "starcoder2_15b", displayName = "StarCoder2 15B (Q4)",
            description = "Code gen 15B ~8.7GB. MIT license.",
            source = ModelSource.HUGGINGFACE,
            downloadUrl = hfBlake("bartowski/StarCoder2-15B-GGUF", "starcoder2-15b.Q4_K_M.gguf"),
            fileSizeBytes = 8_700_000_000, ramRequiredMb = 6144,
            type = ModelType.TEXT_GENERATION, format = ModelFormat.GGUF,
            tags = listOf("code", "starcoder", "large"), huggingFaceRepo = "bartowski/StarCoder2-15B-GGUF"
        ),

        // ======================================================================
        // ONNX / TFLITE (Cross-platform)
        // ======================================================================

        LocalModelInfo(
            id = "allminilm_onnx", displayName = "MiniLM ONNX",
            description = "Cross-platform embedding ~90MB. Works on any device.",
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
