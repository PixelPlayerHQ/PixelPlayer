package com.theveloper.pixelplay.data.ai.local

import android.os.Build

/**
 * Represents a downloadable / importable local AI model.
 *
 * Size tiers are chosen dynamically based on available RAM so that
 * the user is only offered models the device can comfortably run.
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
)

enum class ModelSource(val displayName: String) {
    TFLITE("TensorFlow Lite"),
    LITERT("Google AI Edge (LiteRT)"),
    MLKIT("ML Kit"),
    HUGGING_FACE("Hugging Face Hub"),
    OLLAMA("Ollama Local"),
    ONNX("ONNX Runtime"),
    USER_IMPORTED("User Imported"),
}

enum class ModelType(val displayName: String) {
    MUSIC_RECOMMENDATION("Music Recommendation"),
    SENTIMENT("Mood & Sentiment"),
    GENRE_CLASSIFICATION("Genre Classification"),
    EMBEDDING("Audio Embedding"),
    GENERAL_CHAT("General Chat / Q&A"),
    TRANSLATION("Translation"),
}

enum class ModelFormat(val extension: String) {
    TFLITE("tflite"),
    ONNX("onnx"),
    BIN("bin"),
    GGUF("gguf"),
    LITERT("litert"),
}

/** Current download / readiness state for a model. */
sealed class ModelStatus {
    object NotDownloaded : ModelStatus()
    data class Downloading(val progressPercent: Int, val bytesDownloaded: Long) : ModelStatus()
    object Verifying : ModelStatus()
    object Ready : ModelStatus()
    data class Error(val message: String) : ModelStatus()
    object Imported : ModelStatus()
}

/** Checks device RAM and returns appropriate model size tier (MB). */
fun recommendedModelSizeMb(): Int {
    val totalRamMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
    return when {
        totalRamMb >= 3072 -> 500   // 3 GB+ → up to 500 MB model
        totalRamMb >= 1536 -> 150   // 1.5 GB → up to 150 MB
        else               -> 50    // low-end → up to 50 MB
    }
}

/** Curated catalogue of small, music-focused models suitable for on-device use. */
object LocalModelCatalog {

    val all: List<LocalModelInfo> = listOf(
        // Tiny models for low-end devices (< 50MB)
        // Note: These are placeholder URLs - in production, replace with actual downloadable model URLs
        LocalModelInfo(
            id = "music_recommender_mobilenet_tflite",
            displayName = "MusicNet Recommender (TFLite)",
            description = "Lightweight music taste model based on MobileNet embeddings. Works fully offline.",
            source = ModelSource.TFLITE,
            downloadUrl = "",  // Placeholder - requires actual model hosting
            fileSizeBytes = 6_432_000L,
            ramRequiredMb = 64,
            type = ModelType.MUSIC_RECOMMENDATION,
            format = ModelFormat.TFLITE,
            tags = listOf("fast", "offline", "recommendation"),
            isRecommended = true,
        ),
        LocalModelInfo(
            id = "tiny_genre_tflite",
            displayName = "Tiny Genre Classifier",
            description = "Ultra-light genre classification. ~4 MB, works on any device.",
            source = ModelSource.TFLITE,
            downloadUrl = "",  // Placeholder - requires actual model hosting
            fileSizeBytes = 4_200_000L,
            ramRequiredMb = 32,
            type = ModelType.GENRE_CLASSIFICATION,
            format = ModelFormat.TFLITE,
            tags = listOf("genre", "tiny", "fast"),
            isRecommended = true,
        ),
        LocalModelInfo(
            id = "mood_tiny_tflite",
            displayName = "Mood Analyzer Tiny",
            description = "Compact mood detection model. ~8 MB.",
            source = ModelSource.TFLITE,
            downloadUrl = "",  // Placeholder - requires actual model hosting
            fileSizeBytes = 8_000_000L,
            ramRequiredMb = 48,
            type = ModelType.SENTIMENT,
            format = ModelFormat.TFLITE,
            tags = listOf("mood", "tiny", "text"),
            isRecommended = true,
        ),

        // Small models (50-150MB)
        LocalModelInfo(
            id = "genre_classifier_tflite",
            displayName = "Genre Classifier Lite",
            description = "Classifies audio fingerprints into genre tags. ~12 MB, runs on any device.",
            source = ModelSource.TFLITE,
            downloadUrl = "",  // Placeholder
            fileSizeBytes = 12_300_000L,
            ramRequiredMb = 128,
            type = ModelType.GENRE_CLASSIFICATION,
            format = ModelFormat.TFLITE,
            tags = listOf("genre", "audio", "classification"),
        ),
        LocalModelInfo(
            id = "sentiment_mobileBERT_tflite",
            displayName = "Mood Analyzer (MobileBERT)",
            description = "Analyzes listening context and mood based on song metadata. ~25 MB.",
            source = ModelSource.TFLITE,
            downloadUrl = "",  // Placeholder
            fileSizeBytes = 25_600_000L,
            ramRequiredMb = 256,
            type = ModelType.SENTIMENT,
            format = ModelFormat.TFLITE,
            tags = listOf("mood", "bert", "text"),
        ),
        LocalModelInfo(
            id = "mini_lm_huggingface",
            displayName = "MiniLM (Hugging Face)",
            description = "Small but powerful embedding model for music recommendations. ~45 MB.",
            source = ModelSource.HUGGING_FACE,
            downloadUrl = "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx",
            fileSizeBytes = 45_000_000L,
            ramRequiredMb = 128,
            type = ModelType.EMBEDDING,
            format = ModelFormat.ONNX,
            huggingFaceRepo = "sentence-transformers/all-MiniLM-L6-v2",
            tags = listOf("embedding", "huggingface", "mini"),
            isRecommended = true,
        ),

        // Medium models (150-500MB)
        LocalModelInfo(
            id = "music_embedding_litert",
            displayName = "Music Embedding (Google LiteRT)",
            description = "Google AI Edge model for rich music embeddings. Requires 512 MB+ RAM.",
            source = ModelSource.LITERT,
            downloadUrl = "",  // Placeholder - LiteRT models need proper hosting
            fileSizeBytes = 148_000_000L,
            ramRequiredMb = 512,
            type = ModelType.EMBEDDING,
            format = ModelFormat.LITERT,
            tags = listOf("embedding", "google", "ai-edge"),
        ),
        LocalModelInfo(
            id = "distilbert_huggingface",
            displayName = "DistilBERT Base",
            description = "Distilled BERT for better text understanding. ~250 MB.",
            source = ModelSource.HUGGING_FACE,
            downloadUrl = "",  // Placeholder - ONNX model needs proper URL
            fileSizeBytes = 250_000_000L,
            ramRequiredMb = 512,
            type = ModelType.SENTIMENT,
            format = ModelFormat.ONNX,
            huggingFaceRepo = "distilbert/distilbert-base-uncased",
            tags = listOf("bert", "text", "sentiment"),
        ),

        // Larger models for powerful devices (500MB+)
        // Ollama models - these are downloaded via Ollama app, not direct URLs
        LocalModelInfo(
            id = "tinyllama_ollama",
            displayName = "TinyLlama Chat (Ollama)",
            description = "Small 1.1B parameter chat model via local Ollama server. Excellent for playlists Q&A.",
            source = ModelSource.OLLAMA,
            downloadUrl = "",  // Ollama manages its own downloads
            fileSizeBytes = 640_000_000L,
            ramRequiredMb = 1536,
            type = ModelType.GENERAL_CHAT,
            format = ModelFormat.GGUF,
            ollamaTag = "tinyllama",
            tags = listOf("chat", "ollama", "llm"),
        ),
        LocalModelInfo(
            id = "phi3_mini_huggingface",
            displayName = "Phi-3 Mini (Hugging Face)",
            description = "Microsoft Phi-3 Mini — tiny but capable chat model.",
            source = ModelSource.HUGGING_FACE,
            downloadUrl = "",  // Placeholder - GGUF model needs proper URL
            fileSizeBytes = 2_300_000_000L,
            ramRequiredMb = 3072,
            type = ModelType.GENERAL_CHAT,
            format = ModelFormat.GGUF,
            huggingFaceRepo = "microsoft/Phi-3-mini-4k-instruct-gguf",
            tags = listOf("phi3", "huggingface", "llm"),
        ),

        // User-imported model placeholder
        LocalModelInfo(
            id = "user_imported",
            displayName = "Custom Model (Import)",
            description = "Import your own model file (.tflite, .onnx, .gguf)",
            source = ModelSource.USER_IMPORTED,
            downloadUrl = "",
            fileSizeBytes = 0,
            ramRequiredMb = 0,
            type = ModelType.GENERAL_CHAT,
            format = ModelFormat.BIN,
            tags = listOf("custom", "import"),
        ),
    )

    /** Filter to models the device can likely run based on recommended size tier. */
    fun forDevice(): List<LocalModelInfo> {
        val maxMb = recommendedModelSizeMb()
        return all.filter { it.fileSizeBytes / (1024 * 1024) <= maxMb }
    }

    /** Get models sorted by size (smallest first) */
    fun sortedBySize(): List<LocalModelInfo> = all.sortedBy { it.fileSizeBytes }

    /** Get recommended models for the current device */
    fun recommended(): List<LocalModelInfo> = all.filter { it.isRecommended }

    /** Get models by source */
    fun bySource(source: ModelSource): List<LocalModelInfo> = all.filter { it.source == source }

    /** Get models by type */
    fun byType(type: ModelType): List<LocalModelInfo> = all.filter { it.type == type }

    /** Get models that have valid download URLs */
    fun downloadable(): List<LocalModelInfo> = all.filter { it.downloadUrl.isNotBlank() }

    /** Get Ollama-compatible models */
    fun ollamaModels(): List<LocalModelInfo> = all.filter { it.source == ModelSource.OLLAMA }
}