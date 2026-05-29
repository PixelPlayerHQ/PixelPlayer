package com.theveloper.pixelplay.data.ai

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enhanced AI logging system for debugging and analytics.
 * Stores detailed logs of AI operations, prompts, and responses.
 */
@Singleton
class AiLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val LOG_DIR = "ai_logs"
        private const val LOG_FILE = "ai_operations.log"
        private const val MAX_LOG_SIZE_MB = 10
        private const val MAX_LOG_FILES = 5
        private const val PREFS_NAME = "ai_logger_settings"
        private const val KEY_DEBUG_MODE = "ai_debug_mode"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val logDir: File
        get() = File(context.filesDir, LOG_DIR).also { it.mkdirs() }

    private val currentLogFile: File
        get() = File(logDir, LOG_FILE)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    // Cache debug mode to avoid calling suspend function repeatedly
    private var cachedDebugMode: Boolean? = null
    private var lastCacheTime = 0L
    private val cacheValidDuration = 5000L // 5 seconds

    fun log(vararg fields: Pair<String, Any?>) {
        if (!shouldLogSync()) return
        val line = buildString {
            append("[${dateFormat.format(Date())}]")
            fields.forEach { (k, v) -> if (v != null) append(" | $k=$v") }
            append("\n")
        }
        writeToLog(line)
    }

    /**
     * Gets recent log entries for display in settings.
     */
    fun getRecentLogs(lineCount: Int = 50): List<String> {
        return try {
            if (!currentLogFile.exists()) return emptyList()
            currentLogFile.readLines().takeLast(lineCount)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Gets log file size in MB.
     */
    fun getLogSizeMb(): Double {
        return if (currentLogFile.exists()) {
            currentLogFile.length().toDouble() / (1024 * 1024)
        } else 0.0
    }

    /**
     * Clears all AI logs.
     */
    fun clearLogs() {
        logDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Exports logs to a shareable file.
     */
    fun exportLogs(): File? {
        return try {
            val exportFile = File(logDir, "ai_logs_export_${System.currentTimeMillis()}.txt")
            val logs = getRecentLogs(500)
            exportFile.writeText(logs.joinToString("\n"))
            exportFile
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Synchronous version of shouldLog that caches the result.
     * This avoids calling suspend functions from non-suspend contexts.
     */
    private fun shouldLogSync(): Boolean {
        // Check cache
        val now = System.currentTimeMillis()
        if (cachedDebugMode != null && (now - lastCacheTime) < cacheValidDuration) {
            return cachedDebugMode == true
        }

        // Refresh cache using local SharedPreferences
        cachedDebugMode = prefs.getBoolean(KEY_DEBUG_MODE, false)
        lastCacheTime = now

        return cachedDebugMode == true
    }

    /**
     * Asynchronous version for coroutine contexts.
     */
    private suspend fun shouldLog(): Boolean {
        return prefs.getBoolean(KEY_DEBUG_MODE, false)
    }

    private fun writeToLog(line: String) {
        try {
            rotateLogsIfNeeded()

            FileWriter(currentLogFile, true).use { fw ->
                PrintWriter(fw).use { writer ->
                    writer.write(line)
                }
            }
        } catch (e: Exception) {
            // Silently fail - logging should never crash the app
        }
    }

    private fun rotateLogsIfNeeded() {
        if (currentLogFile.length() > MAX_LOG_SIZE_MB * 1024 * 1024) {
            // Archive current log
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val archived = File(logDir, "ai_operations_$timestamp.log")
            currentLogFile.renameTo(archived)

            // Delete old archives
            logDir.listFiles()
                ?.filter { it.name.startsWith("ai_operations_") && it.name.endsWith(".log") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(MAX_LOG_FILES)
                ?.forEach { it.delete() }
        }
    }
}