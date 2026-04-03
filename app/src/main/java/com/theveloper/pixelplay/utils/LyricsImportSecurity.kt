package com.theveloper.pixelplay.utils

import com.theveloper.pixelplay.data.model.Lyrics
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

data class ValidatedLyricsImport(
    val sanitizedContent: String,
    val parsedLyrics: Lyrics
)

enum class LyricsImportFailureReason {
    UNSUPPORTED_EXTENSION,
    UNSUPPORTED_MIME_TYPE,
    FILE_TOO_LARGE,
    EMPTY_CONTENT,
    INVALID_ENCODING,
    INVALID_LYRICS_CONTENT
}

sealed interface LyricsImportValidationResult {
    data class Valid(val value: ValidatedLyricsImport) : LyricsImportValidationResult
    data class Invalid(val reason: LyricsImportFailureReason) : LyricsImportValidationResult
}

object LyricsImportSecurity {
    const val MAX_LYRICS_FILE_BYTES = 256 * 1024
    const val MAX_LYRICS_TEXT_CHARS = 50_000

    private enum class LyricsDocumentFormat(
        val extension: String,
        val allowedMimeTypes: Set<String>
    ) {
        LRC(
            extension = "lrc",
            allowedMimeTypes = setOf(
                "text/plain",
                "text/x-lrc",
                "application/octet-stream",
                "application/x-subrip",
                "application/lrc",
                "application/x-lrc"
            )
        ),
        TTML(
            extension = "ttml",
            allowedMimeTypes = setOf(
                "application/ttml+xml",
                "application/xml",
                "text/xml",
                "text/plain",
                "application/octet-stream"
            )
        )
    }

    fun pickerMimeTypes(): Array<String> =
        LyricsDocumentFormat.entries
            .flatMap { it.allowedMimeTypes }
            .distinct()
            .toTypedArray()

    fun supportedFileExtensions(): List<String> =
        LyricsDocumentFormat.entries.map { it.extension }

    fun validateImportedLyricsFile(
        fileName: String?,
        mimeType: String?,
        inputStream: InputStream,
        reportedSizeBytes: Long? = null
    ): LyricsImportValidationResult {
        val format = resolveDocumentFormat(fileName)
            ?: return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.UNSUPPORTED_EXTENSION)

        if (!hasSupportedMimeType(format, mimeType)) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.UNSUPPORTED_MIME_TYPE)
        }
        if (reportedSizeBytes != null && reportedSizeBytes > MAX_LYRICS_FILE_BYTES) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
        }

        val payload = readBytesWithLimit(inputStream, MAX_LYRICS_FILE_BYTES + 1)
            ?: return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)

        return validatePayload(payload, format)
    }

    fun validateLocalLyricsFile(file: File): LyricsImportValidationResult {
        val format = resolveDocumentFormat(file.name)
            ?: return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.UNSUPPORTED_EXTENSION)

        if (!file.exists() || !file.canRead()) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
        }
        if (file.length() > MAX_LYRICS_FILE_BYTES) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
        }

        return runCatching {
            file.inputStream().buffered().use { input ->
                val payload = readBytesWithLimit(input, MAX_LYRICS_FILE_BYTES + 1)
                    ?: return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
                validatePayload(payload, format)
            }
        }.getOrElse {
            LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_ENCODING)
        }
    }

    fun validateImportedLrcContent(rawText: String): LyricsImportValidationResult {
        val sanitized = sanitizeImportedLyrics(rawText)
        if (sanitized.isBlank()) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
        }
        if (sanitized.length > MAX_LYRICS_TEXT_CHARS) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
        }

        val parsed = LyricsUtils.parseLyrics(sanitized)
        if (parsed.synced.isNullOrEmpty()) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_LYRICS_CONTENT)
        }

        return LyricsImportValidationResult.Valid(
            ValidatedLyricsImport(
                sanitizedContent = sanitized,
                parsedLyrics = parsed
            )
        )
    }

    fun messageFor(reason: LyricsImportFailureReason): String {
        return when (reason) {
            LyricsImportFailureReason.UNSUPPORTED_EXTENSION ->
                "Only synced .lrc and .ttml lyrics files are supported."
            LyricsImportFailureReason.UNSUPPORTED_MIME_TYPE ->
                "The selected file type is not a supported lyrics file."
            LyricsImportFailureReason.FILE_TOO_LARGE ->
                "Lyrics file is too large."
            LyricsImportFailureReason.EMPTY_CONTENT ->
                "Lyrics file is empty."
            LyricsImportFailureReason.INVALID_ENCODING ->
                "Lyrics file could not be decoded safely."
            LyricsImportFailureReason.INVALID_LYRICS_CONTENT ->
                "File does not contain valid synced lyrics."
        }
    }

    private fun validatePayload(
        payload: ByteArray,
        format: LyricsDocumentFormat
    ): LyricsImportValidationResult {
        if (payload.isEmpty()) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.EMPTY_CONTENT)
        }
        if (payload.size > MAX_LYRICS_FILE_BYTES) {
            return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.FILE_TOO_LARGE)
        }

        val decoded = decodeText(payload)
            ?: return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_ENCODING)

        for (normalized in normalizationCandidates(decoded, format)) {
            val validation = validateImportedLrcContent(normalized)
            if (validation is LyricsImportValidationResult.Valid) {
                return validation
            }
        }

        return LyricsImportValidationResult.Invalid(LyricsImportFailureReason.INVALID_LYRICS_CONTENT)
    }

    private fun normalizationCandidates(
        decoded: String,
        format: LyricsDocumentFormat
    ): List<String> {
        val candidates = when (format) {
            LyricsDocumentFormat.LRC -> listOfNotNull(
                sanitizeImportedLyrics(decoded),
                TtmlLyricsParser.parseToEnhancedLrc(decoded)?.let(::sanitizeImportedLyrics)
            )
            LyricsDocumentFormat.TTML -> listOfNotNull(
                TtmlLyricsParser.parseToEnhancedLrc(decoded)?.let(::sanitizeImportedLyrics),
                sanitizeImportedLyrics(decoded)
            )
        }

        return candidates.distinct().filter { it.isNotBlank() }
    }

    private fun resolveDocumentFormat(fileName: String?): LyricsDocumentFormat? {
        val normalized = fileName
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase()
            ?: return null
        return LyricsDocumentFormat.entries.firstOrNull { it.extension == normalized }
    }

    private fun hasSupportedMimeType(
        format: LyricsDocumentFormat,
        mimeType: String?
    ): Boolean {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            .orEmpty()

        if (normalized.isBlank()) return true
        return normalized in format.allowedMimeTypes
    }

    private fun readBytesWithLimit(inputStream: InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(DEFAULT_BUFFER_SIZE, maxBytes))
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0

        while (true) {
            val read = inputStream.read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                return null
            }
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }

    private fun decodeText(payload: ByteArray): String? {
        val (charset, offset) = when {
            payload.startsWithUtf8Bom() -> Charsets.UTF_8 to 3
            payload.startsWithUtf16LeBom() -> Charsets.UTF_16LE to 2
            payload.startsWithUtf16BeBom() -> Charsets.UTF_16BE to 2
            else -> Charsets.UTF_8 to 0
        }

        return try {
            charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(payload, offset, payload.size - offset))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }

    private fun sanitizeImportedLyrics(rawText: String): String {
        return rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map(::sanitizeLyricsLineForStorage)
            .joinToString("\n")
            .trim()
    }

    private fun sanitizeLyricsLineForStorage(rawLine: String): String {
        return rawLine
            .trimEnd('\uFEFF')
            .filterNot { char ->
                Character.getType(char).toByte() == Character.FORMAT ||
                    (Character.isISOControl(char) && char != '\t')
            }
    }

    private fun ByteArray.startsWithUtf8Bom(): Boolean =
        size >= 3 &&
            this[0] == 0xEF.toByte() &&
            this[1] == 0xBB.toByte() &&
            this[2] == 0xBF.toByte()

    private fun ByteArray.startsWithUtf16LeBom(): Boolean =
        size >= 2 &&
            this[0] == 0xFF.toByte() &&
            this[1] == 0xFE.toByte()

    private fun ByteArray.startsWithUtf16BeBom(): Boolean =
        size >= 2 &&
            this[0] == 0xFE.toByte() &&
            this[1] == 0xFF.toByte()
}
