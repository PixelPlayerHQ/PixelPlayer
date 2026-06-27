package com.theveloper.pixelplay.data.service.cast

import java.nio.charset.StandardCharsets
import java.util.Locale

internal object IsoBmffAudioCodecDetector {
    private const val BOX_HEADER_SIZE = 8
    private const val EXTENDED_BOX_HEADER_SIZE = 16
    private val containerBoxes = setOf("moov", "trak", "mdia", "minf", "stbl")

    fun detectAudioCodec(bytes: ByteArray): String? {
        if (bytes.size < BOX_HEADER_SIZE) return null

        forEachBox(bytes, 0, bytes.size) { box ->
            if (box.type == "moov") {
                detectInMoov(bytes, box.contentStart, box.end)?.let { return it }
            }
            true
        }
        return null
    }

    private fun detectInMoov(bytes: ByteArray, start: Int, end: Int): String? {
        forEachBox(bytes, start, end) { box ->
            if (box.type == "trak") {
                detectInTrak(bytes, box.contentStart, box.end)?.let { return it }
            }
            true
        }
        return null
    }

    private fun detectInTrak(bytes: ByteArray, start: Int, end: Int): String? {
        forEachBox(bytes, start, end) { box ->
            if (box.type == "mdia") {
                val handler = findHandlerType(bytes, box.contentStart, box.end)
                if (handler == "soun") {
                    findSampleDescriptionCodec(bytes, box.contentStart, box.end)?.let { return it }
                }
            }
            true
        }
        return null
    }

    private fun findHandlerType(bytes: ByteArray, start: Int, end: Int): String? {
        forEachBox(bytes, start, end) { box ->
            if (box.type == "hdlr") {
                val handlerOffset = box.contentStart + 8
                if (handlerOffset + 4 <= box.end) {
                    return ascii(bytes, handlerOffset, 4)
                }
            }
            true
        }
        return null
    }

    private fun findSampleDescriptionCodec(bytes: ByteArray, start: Int, end: Int): String? {
        forEachBox(bytes, start, end) { box ->
            when {
                box.type == "stsd" -> parseStsd(bytes, box.contentStart, box.end)?.let { return it }
                box.type in containerBoxes -> {
                    findSampleDescriptionCodec(bytes, box.contentStart, box.end)?.let { return it }
                }
            }
            true
        }
        return null
    }

    private fun parseStsd(bytes: ByteArray, start: Int, end: Int): String? {
        if (start + 8 > end) return null
        val entryCount = readUInt32(bytes, start + 4)?.coerceAtMost(32) ?: return null
        var offset = start + 8
        repeat(entryCount.toInt()) {
            val box = readBox(bytes, offset, end) ?: return null
            sampleEntryTypeToMime(box.type)?.let { return it }
            offset = box.end
        }
        return null
    }

    private fun sampleEntryTypeToMime(type: String): String? {
        return when (type.lowercase(Locale.ROOT)) {
            "alac" -> "audio/alac"
            "mp4a" -> "audio/mp4a-latm"
            "ac-3" -> "audio/ac3"
            "ec-3" -> "audio/eac3"
            "ac-4" -> "audio/ac4"
            "flac" -> "audio/flac"
            "opus" -> "audio/opus"
            else -> null
        }
    }

    private inline fun forEachBox(
        bytes: ByteArray,
        start: Int,
        end: Int,
        block: (Box) -> Boolean
    ) {
        var offset = start.coerceAtLeast(0)
        val safeEnd = end.coerceAtMost(bytes.size)
        while (offset + BOX_HEADER_SIZE <= safeEnd) {
            val box = readBox(bytes, offset, safeEnd) ?: break
            if (!block(box)) return
            if (box.end <= offset) break
            offset = box.end
        }
    }

    private fun readBox(bytes: ByteArray, offset: Int, parentEnd: Int): Box? {
        if (offset + BOX_HEADER_SIZE > parentEnd || offset + BOX_HEADER_SIZE > bytes.size) return null
        val size32 = readUInt32(bytes, offset) ?: return null
        val type = ascii(bytes, offset + 4, 4) ?: return null
        val headerSize: Int
        val rawSize: Long

        when (size32) {
            0L -> {
                headerSize = BOX_HEADER_SIZE
                rawSize = (parentEnd - offset).toLong()
            }
            1L -> {
                if (offset + EXTENDED_BOX_HEADER_SIZE > parentEnd || offset + EXTENDED_BOX_HEADER_SIZE > bytes.size) {
                    return null
                }
                headerSize = EXTENDED_BOX_HEADER_SIZE
                rawSize = readUInt64(bytes, offset + 8) ?: return null
            }
            else -> {
                headerSize = BOX_HEADER_SIZE
                rawSize = size32
            }
        }

        if (rawSize < headerSize) return null
        val declaredEnd = offset.toLong() + rawSize
        val safeEnd = declaredEnd.coerceAtMost(parentEnd.toLong()).coerceAtMost(bytes.size.toLong()).toInt()
        val contentStart = offset + headerSize
        if (contentStart > safeEnd) return null
        return Box(type = type, contentStart = contentStart, end = safeEnd)
    }

    private fun readUInt32(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        return ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt64(bytes: ByteArray, offset: Int): Long? {
        if (offset < 0 || offset + 8 > bytes.size) return null
        var value = 0L
        for (i in 0 until 8) {
            value = (value shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return if (value >= 0L) value else null
    }

    private fun ascii(bytes: ByteArray, offset: Int, length: Int): String? {
        if (offset < 0 || length <= 0 || offset + length > bytes.size) return null
        return String(bytes, offset, length, StandardCharsets.US_ASCII)
    }

    private data class Box(
        val type: String,
        val contentStart: Int,
        val end: Int
    )
}
