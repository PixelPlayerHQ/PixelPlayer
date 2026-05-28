package com.theveloper.pixelplay.data.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardware survey for AI capabilities.
 * Determines what AI models and features the device can support.
 */
@Singleton
class AiDeviceCapabilities @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class DeviceCapabilities(
        val totalRamMb: Long,
        val availableRamMb: Long,
        val cpuCores: Int,
        val cpuArchitecture: String,
        val is64Bit: Boolean,
        val supportsTflite: Boolean,
        val supportsGpuInference: Boolean,
        val supportsNnapi: Boolean,
        val gpuRenderer: String?,
        val recommendedModelSizeMb: Int,
        val supportsStreaming: Boolean,
        val recommendedProviders: List<String>,
        val osVersion: Int,
        val sdkVersion: Int,
        val deviceModel: String,
        val manufacturer: String
    )

    fun getCapabilities(): DeviceCapabilities {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availableRamMb = memInfo.availMem / (1024 * 1024)

        val cpuCores = Runtime.getRuntime().availableProcessors()
        val cpuArch = System.getProperty("os.arch") ?: "unknown"
        val is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()

        val recommendedSizeMb = when {
            totalRamMb >= 6144 -> 1000
            totalRamMb >= 3072 -> 500
            totalRamMb >= 2048 -> 256
            totalRamMb >= 1536 -> 150
            totalRamMb >= 1024 -> 100
            else -> 50
        }

        val recommendedProviders = buildList {
            // Everyone can use local models
            add("LOCAL")
            // Everyone can use Ollama if installed
            add("OLLAMA")
            // Add cloud providers based on API availability
            if (hasNetwork()) {
                add("GEMINI")
                add("OPENAI")
                add("ANTHROPIC")
            }
        }

        return DeviceCapabilities(
            totalRamMb = totalRamMb,
            availableRamMb = availableRamMb,
            cpuCores = cpuCores,
            cpuArchitecture = cpuArch,
            is64Bit = is64Bit,
            supportsTflite = true, // Check at runtime with try-catch
            supportsGpuInference = checkGpuSupport(),
            supportsNnapi = checkNnapiSupport(),
            gpuRenderer = getGpuRenderer(),
            recommendedModelSizeMb = recommendedSizeMb,
            supportsStreaming = availableRamMb > 512,
            recommendedProviders = recommendedProviders,
            osVersion = Build.VERSION.SDK_INT,
            sdkVersion = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER
        )
    }

    fun canRunModel(modelSizeMb: Int): Boolean {
        val caps = getCapabilities()
        // Require 2x model size in available RAM
        return caps.availableRamMb >= (modelSizeMb * 2)
    }

    fun getRecommendedModelTypes(): List<String> {
        val caps = getCapabilities()
        return buildList {
            // Everyone can do basic recommendations
            add("GENRE_CLASSIFICATION")
            if (caps.availableRamMb >= 256) {
                add("SENTIMENT")
            }
            if (caps.availableRamMb >= 512) {
                add("EMBEDDING")
            }
            if (caps.availableRamMb >= 1536 && caps.cpuCores >= 4) {
                add("GENERAL_CHAT")
            }
        }
    }

    private fun hasNetwork(): Boolean {
        return try {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            connectivity.activeNetwork != null
        } catch (e: Exception) {
            false
        }
    }

    private fun checkGpuSupport(): Boolean {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.isLowRamDevice == false
        } catch (e: Exception) {
            false
        }
    }

    private fun checkNnapiSupport(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    private fun getGpuRenderer(): String? {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val config = activityManager.deviceConfigurationInfo
            config?.glEsVersion
        } catch (e: Exception) {
            null
        }
    }

    fun getSummary(): String {
        val caps = getCapabilities()
        return buildString {
            append("Device: ${caps.manufacturer} ${caps.deviceModel}\n")
            append("RAM: ${caps.totalRamMb}MB total, ${caps.availableRamMb}MB available\n")
            append("CPU: ${caps.cpuCores} cores, ${caps.cpuArchitecture}\n")
            append("64-bit: ${caps.is64Bit}\n")
            append("GPU: ${caps.gpuRenderer ?: "unknown"}\n")
            append("NNAPI: ${caps.supportsNnapi}\n")
            append("Recommended model size: ${caps.recommendedModelSizeMb}MB\n")
            append("Recommended providers: ${caps.recommendedProviders.joinToString(", ")}")
        }
    }
}