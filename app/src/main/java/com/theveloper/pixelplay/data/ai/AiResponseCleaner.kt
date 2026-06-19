package com.theveloper.pixelplay.data.ai

object AiResponseCleaner {

    fun cleanJsonResponse(raw: String): String {
        var cleaned = raw
            .replace("```json", "")
            .replace("```kotlin", "")
            .replace("```", "")
            .trim()

        if (cleaned.startsWith("[")) {
            val end = findMatchingBracket(cleaned, 0)
            if (end > 0) cleaned = cleaned.substring(0, end + 1)
        } else if (cleaned.startsWith("{")) {
            val end = findMatchingBrace(cleaned, 0)
            if (end > 0) cleaned = cleaned.substring(0, end + 1)
        }

        return cleaned
    }

    fun cleanTextResponse(raw: String): String {
        return raw
            .replace("```text", "")
            .replace("```", "")
            .trim()
    }

    fun extractJsonArray(text: String): String? {
        for (i in text.indices) {
            if (text[i] == '[') {
                val end = findMatchingBracket(text, i)
                if (end > i) return text.substring(i, end + 1)
            }
        }
        return null
    }

    fun extractJsonObject(text: String): String? {
        for (i in text.indices) {
            if (text[i] == '{') {
                val end = findMatchingBrace(text, i)
                if (end > i) return text.substring(i, end + 1)
            }
        }
        return null
    }

    private fun findMatchingBracket(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) { escaped = false; continue }
            if (inString) {
                if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '\\' -> escaped = true
                '"' -> inString = true
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }

    private fun findMatchingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) { escaped = false; continue }
            if (inString) {
                if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '\\' -> escaped = true
                '"' -> inString = true
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return i }
            }
        }
        return -1
    }
}
