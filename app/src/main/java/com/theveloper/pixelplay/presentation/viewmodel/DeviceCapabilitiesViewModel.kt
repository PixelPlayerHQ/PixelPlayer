package com.theveloper.pixelplay.presentation.viewmodel

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.theveloper.pixelplay.data.database.DeviceCapabilitySongRow
import com.theveloper.pixelplay.data.database.MusicDao
import com.theveloper.pixelplay.data.database.SourceType
import com.theveloper.pixelplay.data.diagnostics.AdvancedPerformanceDiagnostics
import com.theveloper.pixelplay.data.preferences.UserPreferencesRepository
import com.theveloper.pixelplay.data.service.player.ActiveDecoderInfo
import com.theveloper.pixelplay.data.service.player.DualPlayerEngine
import com.theveloper.pixelplay.data.service.player.HiFiCapabilityChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CodecInfo(
    val name: String,
    val supportedTypes: List<String>,
    val isHardwareAccelerated: Boolean,
    val maxSupportedInstances: Int
)

data class AudioOutputInfo(
    val name: String,
    val category: AudioOutputCategory
)

enum class AudioOutputCategory {
    BuiltIn,
    Bluetooth,
    Usb,
    Wired,
    Cast,
    Other
}

data class AudioCapabilities(
    val outputSampleRate: Int,
    val outputFramesPerBuffer: Int,
    val isLowLatencySupported: Boolean,
    val isProAudioSupported: Boolean,
    val isPcmFloatSupported: Boolean,
    val offloadSupportedFormats: List<String>,
    val outputRoutes: List<AudioOutputInfo>,
    val supportedCodecs: List<CodecInfo>
)

data class FormatSupportInfo(
    val label: String,
    val mimeType: String,
    val isDecoderAvailable: Boolean,
    val isHardwareAccelerated: Boolean,
    val isOffloadSupported: Boolean,
    val librarySongCount: Int
)

data class LocalMusicStorageSummary(
    val localSongCount: Int,
    val cloudSongCount: Int,
    val knownLocalFileCount: Int,
    val unavailableLocalFileCount: Int,
    val localMusicBytes: Long,
    val deviceAvailableBytes: Long,
    val deviceTotalBytes: Long
) {
    val deviceUsedBytes: Long
        get() = (deviceTotalBytes - deviceAvailableBytes).coerceAtLeast(0L)

    val localMusicStorageFraction: Float
        get() = if (deviceTotalBytes <= 0L) {
            0f
        } else {
            (localMusicBytes.toDouble() / deviceTotalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }

    val deviceUsedFraction: Float
        get() = if (deviceTotalBytes <= 0L) {
            0f
        } else {
            (deviceUsedBytes.toDouble() / deviceTotalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
}

data class PlaybackCompatibilitySummary(
    val supportedLibrarySongCount: Int,
    val unsupportedLibrarySongCount: Int,
    val unknownFormatSongCount: Int,
    val unsupportedFormats: List<String>,
    val localHiResSongCount: Int,
    val resampledLocalSongCount: Int,
    val maxLocalSampleRate: Int?,
    val maxLocalBitrate: Int?
)

data class MemorySummary(
    val availableRamBytes: Long,
    val totalRamBytes: Long,
    val memoryClassMb: Int,
    val isLowRamDevice: Boolean,
    val isSystemLowMemory: Boolean
)

data class ExoPlayerInfo(
    val version: String,
    val renderers: String,
    val decoderCounters: String
)

data class DeviceCapabilitiesState(
    val deviceInfo: Map<String, String> = emptyMap(),
    val audioCapabilities: AudioCapabilities? = null,
    val exoPlayerInfo: ExoPlayerInfo? = null,
    val storageSummary: LocalMusicStorageSummary? = null,
    val playbackCompatibility: PlaybackCompatibilitySummary? = null,
    val formatSupport: List<FormatSupportInfo> = emptyList(),
    val memorySummary: MemorySummary? = null,
    val decoderInfo: ActiveDecoderInfo? = null,
    val isLoading: Boolean = true,
    val isGeneratingReport: Boolean = false,
    val advancedDiagnosticsEnabled: Boolean = false,
    val advancedDiagnosticsExpiresAtEpochMs: Long? = null,
    val performanceReport: String? = null
)

private data class AudioFormatCandidate(
    val label: String,
    val mimeType: String,
    val offloadEncoding: Int?
)

@HiltViewModel
class DeviceCapabilitiesViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val engine: DualPlayerEngine,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val musicDao: MusicDao,
    private val reportCollector: com.theveloper.pixelplay.data.diagnostics.DebugPerformanceReportCollector
) : ViewModel() {

    private val _state = MutableStateFlow(DeviceCapabilitiesState())
    val state = _state.asStateFlow()

    init {
        observeAdvancedDiagnostics()
        loadCapabilities()
    }

    /**
     * Builds the shareable diagnostic performance report on a background dispatcher
     * and publishes it to [DeviceCapabilitiesState.performanceReport]. The payload
     * combines the human-readable text with a machine-readable JSON appendix.
     */
    fun generatePerformanceReport() {
        if (_state.value.isGeneratingReport) return
        _state.value = _state.value.copy(isGeneratingReport = true)
        viewModelScope.launch {
            val text = try {
                val report = reportCollector.generate()
                buildString {
                    append(report.toPlainText())
                    append("\n\n--- JSON ---\n")
                    append(report.toJson())
                }
            } catch (e: Exception) {
                "Failed to generate performance report: ${e.message}"
            }
            _state.value = _state.value.copy(
                isGeneratingReport = false,
                performanceReport = text
            )
        }
    }

    fun setAdvancedPerformanceDiagnosticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setAdvancedPerformanceDiagnosticsEnabled(enabled)
        }
    }

    fun markLagNow() {
        AdvancedPerformanceDiagnostics.markLagNow(note = "Marked from Device capabilities screen")
    }

    private fun observeAdvancedDiagnostics() {
        viewModelScope.launch {
            userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
            userPreferencesRepository.advancedPerformanceDiagnosticsSettingsFlow.collect { settings ->
                val active = settings.isActive()
                if (!active && settings.enabled) {
                    userPreferencesRepository.disableExpiredAdvancedPerformanceDiagnostics()
                }
                _state.value = _state.value.copy(
                    advancedDiagnosticsEnabled = active,
                    advancedDiagnosticsExpiresAtEpochMs = settings.expiresAtEpochMs.takeIf { active }
                )
            }
        }
    }

    private fun loadCapabilities() {
        viewModelScope.launch {
            val exoInfo = getExoPlayerInfo()
            val loadedState = withContext(Dispatchers.IO) {
                val deviceInfo = getDeviceInfo()
                val audioCaps = getAudioCapabilities()
                val libraryRows = musicDao.getDeviceCapabilitySongRows()
                val storage = getLocalMusicStorageSummary(libraryRows)
                val playback = getPlaybackCompatibilitySummary(libraryRows, audioCaps)
                val formatSupport = getFormatSupport(libraryRows, audioCaps)
                val memorySummary = getMemorySummary()
                val decoderInfo = engine.activeDecoderInfo.value

                val current = _state.value
                DeviceCapabilitiesState(
                    deviceInfo = deviceInfo,
                    audioCapabilities = audioCaps,
                    exoPlayerInfo = exoInfo,
                    storageSummary = storage,
                    playbackCompatibility = playback,
                    formatSupport = formatSupport,
                    memorySummary = memorySummary,
                    decoderInfo = decoderInfo,
                    advancedDiagnosticsEnabled = current.advancedDiagnosticsEnabled,
                    advancedDiagnosticsExpiresAtEpochMs = current.advancedDiagnosticsExpiresAtEpochMs,
                    performanceReport = current.performanceReport,
                    isLoading = false
                )
            }

            _state.value = loadedState
        }
    }

    private fun getDeviceInfo(): Map<String, String> {
        return mapOf(
            "Manufacturer" to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            "Model" to Build.MODEL,
            "Brand" to Build.BRAND.replaceFirstChar { it.uppercase() },
            "Device" to Build.DEVICE,
            "Android Version" to Build.VERSION.RELEASE,
            "SDK Version" to Build.VERSION.SDK_INT.toString(),
            "Hardware" to Build.HARDWARE
        )
    }

    private fun getAudioCapabilities(): AudioCapabilities {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull() ?: 44_100
        val framesPerBuffer = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)?.toIntOrNull() ?: 256
        val packageManager = context.packageManager
        val hasLowLatency = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val hasProAudio = packageManager.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)
        val offloadSupportedFormats = getOffloadSupportedFormats()
        val supportedCodecs = getSupportedAudioCodecs()

        return AudioCapabilities(
            outputSampleRate = sampleRate,
            outputFramesPerBuffer = framesPerBuffer,
            isLowLatencySupported = hasLowLatency,
            isProAudioSupported = hasProAudio,
            isPcmFloatSupported = HiFiCapabilityChecker.isSupported(),
            offloadSupportedFormats = offloadSupportedFormats,
            outputRoutes = getOutputRoutes(audioManager),
            supportedCodecs = supportedCodecs
        )
    }

    private fun getSupportedAudioCodecs(): List<CodecInfo> {
        val codecList = android.media.MediaCodecList(android.media.MediaCodecList.ALL_CODECS)
        val codecs = mutableListOf<CodecInfo>()
        val isSamsung = Build.MANUFACTURER.lowercase(Locale.US) == "samsung"

        for (codecInfo in codecList.codecInfos) {
            if (codecInfo.isEncoder) continue

            val types = codecInfo.supportedTypes
                .filter { it.startsWith("audio/") }
                .map { normalizeMimeType(it) }
                .distinct()
            if (types.isEmpty()) continue

            // On many Samsung devices, c2.sec.* codecs are high-performance hardware paths,
            // but the platform doesn't always flag them as hardwareAccelerated in the manifest.
            // We force report them as hardware in the UI if the name starts with c2.sec.
            val isHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                codecInfo.isHardwareAccelerated || (isSamsung && codecInfo.name.startsWith("c2.sec."))
            } else {
                isSamsung && codecInfo.name.startsWith("c2.sec.")
            }

            val instances = try {
                codecInfo.getCapabilitiesForType(codecInfo.supportedTypes.first { it.startsWith("audio/") })
                    .maxSupportedInstances
            } catch (e: Exception) {
                Timber.w(e, "Failed to get codec capabilities for %s", codecInfo.name)
                -1
            }

            codecs.add(
                CodecInfo(
                    name = codecInfo.name,
                    supportedTypes = types,
                    isHardwareAccelerated = isHardware,
                    maxSupportedInstances = instances
                )
            )
        }
        return codecs.sortedBy { it.name }
    }

    private fun getLocalMusicStorageSummary(rows: List<DeviceCapabilitySongRow>): LocalMusicStorageSummary {
        val localRows = rows.filter { it.sourceType == SourceType.LOCAL }
        val cloudCount = rows.count { it.sourceType != SourceType.LOCAL }
        var totalBytes = 0L
        var knownFiles = 0
        var unavailableFiles = 0

        localRows.forEach { row ->
            val bytes = resolveLocalFileSize(row)
            if (bytes > 0L) {
                totalBytes += bytes
                knownFiles += 1
            } else {
                unavailableFiles += 1
            }
        }

        val storageStats = getDeviceStorageStats()

        return LocalMusicStorageSummary(
            localSongCount = localRows.size,
            cloudSongCount = cloudCount,
            knownLocalFileCount = knownFiles,
            unavailableLocalFileCount = unavailableFiles,
            localMusicBytes = totalBytes,
            deviceAvailableBytes = storageStats.first,
            deviceTotalBytes = storageStats.second
        )
    }

    private fun getPlaybackCompatibilitySummary(
        rows: List<DeviceCapabilitySongRow>,
        audioCapabilities: AudioCapabilities
    ): PlaybackCompatibilitySummary {
        val supportedTypes = audioCapabilities.supportedCodecs.flatMap { it.supportedTypes }.toSet()
        val localRows = rows.filter { it.sourceType == SourceType.LOCAL }
        var supportedCount = 0
        var unsupportedCount = 0
        var unknownCount = 0
        val unsupportedFormats = linkedSetOf<String>()

        rows.forEach { row ->
            val mimeType = row.mimeType?.let(::normalizeMimeType)
            when {
                mimeType.isNullOrBlank() -> unknownCount += 1
                isMimeTypeSupported(mimeType, supportedTypes) -> supportedCount += 1
                else -> {
                    unsupportedCount += 1
                    unsupportedFormats += mimeType
                }
            }
        }

        val localSampleRates = localRows.mapNotNull { it.sampleRate }.filter { it > 0 }
        val maxSampleRate = localSampleRates.maxOrNull()
        val maxBitrate = localRows.mapNotNull { it.bitrate }.filter { it > 0 }.maxOrNull()
        val outputRate = audioCapabilities.outputSampleRate.coerceAtLeast(1)
        val hiResSongCount = localSampleRates.count { it > 48_000 }
        val resampledSongCount = localSampleRates.count { it > outputRate }

        return PlaybackCompatibilitySummary(
            supportedLibrarySongCount = supportedCount,
            unsupportedLibrarySongCount = unsupportedCount,
            unknownFormatSongCount = unknownCount,
            unsupportedFormats = unsupportedFormats.toList(),
            localHiResSongCount = hiResSongCount,
            resampledLocalSongCount = resampledSongCount,
            maxLocalSampleRate = maxSampleRate,
            maxLocalBitrate = maxBitrate
        )
    }

    private fun getFormatSupport(
        rows: List<DeviceCapabilitySongRow>,
        audioCapabilities: AudioCapabilities
    ): List<FormatSupportInfo> {
        val supportedTypes = audioCapabilities.supportedCodecs.flatMap { it.supportedTypes }.toSet()
        val hardwareTypes = audioCapabilities.supportedCodecs
            .filter { it.isHardwareAccelerated }
            .flatMap { it.supportedTypes }
            .toSet()
        val offloadFormats = audioCapabilities.offloadSupportedFormats.toSet()
        val libraryCountsByMime = rows
            .mapNotNull { it.mimeType?.let(::normalizeMimeType) }
            .groupingBy { it }
            .eachCount()

        return audioFormatCandidates().map { candidate ->
            val acceptedMimes = compatibleMimeTypes(candidate.mimeType)
            FormatSupportInfo(
                label = candidate.label,
                mimeType = candidate.mimeType,
                isDecoderAvailable = acceptedMimes.any { isMimeTypeSupported(it, supportedTypes) },
                isHardwareAccelerated = acceptedMimes.any { isMimeTypeSupported(it, hardwareTypes) },
                isOffloadSupported = candidate.label in offloadFormats,
                librarySongCount = acceptedMimes.sumOf { libraryCountsByMime[it] ?: 0 }
            )
        }
    }

    private fun getMemorySummary(): MemorySummary {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return MemorySummary(
            availableRamBytes = memoryInfo.availMem,
            totalRamBytes = memoryInfo.totalMem,
            memoryClassMb = activityManager.memoryClass,
            isLowRamDevice = activityManager.isLowRamDevice,
            isSystemLowMemory = memoryInfo.lowMemory
        )
    }

    private fun getOutputRoutes(audioManager: AudioManager): List<AudioOutputInfo> {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .map { device ->
                AudioOutputInfo(
                    name = device.productName?.toString()?.takeIf { it.isNotBlank() }
                        ?: device.type.toAudioOutputCategory().name,
                    category = device.type.toAudioOutputCategory()
                )
            }
            .distinctBy { it.category to it.name.lowercase(Locale.US) }
            .sortedBy { it.category.ordinal }
    }

    private fun getOffloadSupportedFormats(): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val attributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        return audioFormatCandidates()
            .mapNotNull { candidate ->
                val encoding = candidate.offloadEncoding ?: return@mapNotNull null
                val isSupported = runCatching {
                    val audioFormat = AudioFormat.Builder()
                        .setEncoding(encoding)
                        .setSampleRate(44_100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                    AudioManager.isOffloadedPlaybackSupported(audioFormat, attributes)
                }.getOrDefault(false)

                if (isSupported) candidate.label else null
            }
    }

    private fun getDeviceStorageStats(): Pair<Long, Long> {
        return runCatching {
            val statFs = StatFs(Environment.getExternalStorageDirectory().path)
            statFs.availableBytes to statFs.totalBytes
        }.getOrElse { 0L to 0L }
    }

    private fun resolveLocalFileSize(row: DeviceCapabilitySongRow): Long {
        val pathSize = row.filePath
            .takeIf { it.isNotBlank() }
            ?.let { path ->
                runCatching {
                    val file = File(path)
                    if (file.exists() && file.isFile) file.length() else 0L
                }.getOrDefault(0L)
            }
            ?: 0L

        if (pathSize > 0L) return pathSize

        val contentSize = resolveContentUriSize(row.contentUriString)
        if (contentSize > 0L) return contentSize

        return estimateFileSizeFromMetadata(row)
    }

    private fun resolveContentUriSize(contentUriString: String): Long {
        val uri = runCatching { Uri.parse(contentUriString) }.getOrNull() ?: return 0L
        if (uri.scheme != "content") return 0L

        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun estimateFileSizeFromMetadata(row: DeviceCapabilitySongRow): Long {
        val bitrate = row.bitrate?.takeIf { it > 0 }?.toLong() ?: return 0L
        val durationMs = row.duration.takeIf { it > 0 } ?: return 0L
        return (bitrate * durationMs / 8_000L).coerceAtLeast(0L)
    }

    @OptIn(UnstableApi::class)
    private fun getExoPlayerInfo(): ExoPlayerInfo {
        val player = engine.masterPlayer
        val version = androidx.media3.common.MediaLibraryInfo.VERSION
        val exoPlayer = player as? androidx.media3.exoplayer.ExoPlayer
        val renderers = "${exoPlayer?.rendererCount ?: 0}"

        return ExoPlayerInfo(
            version = version,
            renderers = renderers,
            decoderCounters = "N/A"
        )
    }
}

private fun Int.toAudioOutputCategory(): AudioOutputCategory {
    return when (this) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioOutputCategory.BuiltIn
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioOutputCategory.Bluetooth
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputCategory.Usb
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioOutputCategory.Wired
        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutputCategory.Cast
        else -> AudioOutputCategory.Other
    }
}

private fun audioFormatCandidates(): List<AudioFormatCandidate> {
    return buildList {
        add(AudioFormatCandidate("MP3", "audio/mpeg", AudioFormat.ENCODING_MP3))
        add(AudioFormatCandidate("AAC", "audio/mp4a-latm", AudioFormat.ENCODING_AAC_LC))
        add(AudioFormatCandidate("FLAC", "audio/flac", null))
        add(AudioFormatCandidate("Vorbis", "audio/vorbis", null))
        add(AudioFormatCandidate("WAV", "audio/wav", AudioFormat.ENCODING_PCM_16BIT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(AudioFormatCandidate("Opus", "audio/opus", AudioFormat.ENCODING_OPUS))
        } else {
            add(AudioFormatCandidate("Opus", "audio/opus", null))
        }
        add(AudioFormatCandidate("ALAC", "audio/alac", null))
        add(AudioFormatCandidate("AIFF", "audio/x-aiff", null))
        add(AudioFormatCandidate("AC3", "audio/ac3", null))
        add(AudioFormatCandidate("DTS", "audio/vnd.dts", null))
        add(AudioFormatCandidate("AMR-NB", "audio/3gpp", null))
        add(AudioFormatCandidate("AMR-WB", "audio/amr-wb", null))
        add(AudioFormatCandidate("WMA", "audio/x-ms-wma", null))
        add(AudioFormatCandidate("EVRC", "audio/evrc", null))
        add(AudioFormatCandidate("QCELP", "audio/qcelp", null))
        add(AudioFormatCandidate("IMA-ADPCM", "audio/x-ima-adpcm", null))
    }
}

private fun normalizeMimeType(mimeType: String): String {
    return when (val mime = mimeType.lowercase(Locale.US).substringBefore(";").trim()) {
        "audio/mp3", "audio/x-mp3", "audio/mpeg3" -> "audio/mpeg"
        "audio/x-wav", "audio/wave", "audio/vnd.wave" -> "audio/wav"
        "audio/aac", "audio/x-aac", "audio/m4a", "audio/mp4", "audio/aacp" -> "audio/mp4a-latm"
        "audio/x-flac" -> "audio/flac"
        "audio/ogg", "audio/x-vorbis", "application/ogg" -> "audio/vorbis"
        "audio/x-ms-wma", "audio/wma" -> "audio/x-ms-wma"
        "audio/x-aiff", "audio/aiff", "audio/aif", "audio/x-aifc" -> "audio/x-aiff"
        "audio/ac3", "audio/eac3", "audio/eac3-joc" -> "audio/ac3"
        "audio/vnd.dts", "audio/vnd.dts.hd" -> "audio/vnd.dts"
        "audio/3gpp", "audio/amr" -> "audio/3gpp"
        "audio/amr-wb" -> "audio/amr-wb"
        "audio/evrc", "audio/x-evrc" -> "audio/evrc"
        "audio/qcelp", "audio/x-qcelp" -> "audio/qcelp"
        "audio/x-ima-adpcm", "audio/ima-adpcm" -> "audio/x-ima-adpcm"
        else -> mime
    }
}

private fun compatibleMimeTypes(mimeType: String): Set<String> {
    val normalized = normalizeMimeType(mimeType)
    return when (normalized) {
        "audio/mpeg" -> setOf("audio/mpeg", "audio/mp3", "audio/x-mp3", "audio/mpeg3")
        "audio/mp4a-latm" -> setOf("audio/mp4a-latm", "audio/aac", "audio/x-aac", "audio/m4a", "audio/mp4", "audio/aacp")
        "audio/flac" -> setOf("audio/flac", "audio/x-flac")
        "audio/wav" -> setOf("audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave", "audio/raw")
        "audio/vorbis" -> setOf("audio/vorbis", "audio/ogg", "audio/x-vorbis", "application/ogg")
        "audio/x-ms-wma" -> setOf("audio/x-ms-wma", "audio/wma")
        "audio/x-aiff" -> setOf("audio/x-aiff", "audio/aiff", "audio/aif", "audio/x-aifc")
        "audio/ac3" -> setOf("audio/ac3", "audio/eac3", "audio/eac3-joc")
        "audio/vnd.dts" -> setOf("audio/vnd.dts", "audio/vnd.dts.hd")
        "audio/3gpp" -> setOf("audio/3gpp", "audio/amr")
        "audio/amr-wb" -> setOf("audio/amr-wb")
        "audio/evrc" -> setOf("audio/evrc", "audio/x-evrc")
        "audio/qcelp" -> setOf("audio/qcelp", "audio/x-qcelp")
        "audio/x-ima-adpcm" -> setOf("audio/x-ima-adpcm", "audio/ima-adpcm", "audio/raw")
        else -> setOf(normalized)
    }
}

private fun isMimeTypeSupported(
    mimeType: String,
    supportedTypes: Set<String>
): Boolean {
    return compatibleMimeTypes(mimeType).any { normalizeMimeType(it) in supportedTypes }
}
