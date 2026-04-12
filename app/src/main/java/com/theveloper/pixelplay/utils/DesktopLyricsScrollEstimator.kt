package com.theveloper.pixelplay.utils

import kotlin.math.max

object DesktopLyricsScrollEstimator {

    data class ScrollPlan(
        val shouldScroll: Boolean,
        val holdStartMs: Long,
        val scrollDurationMs: Long,
        val holdEndMs: Long,
        val travelPx: Float,
    )

    fun estimateTextWeight(text: String): Float {
        var total = 0f
        text.forEach { ch ->
            total += when {
                ch == ' ' -> 0.2f
                ch.code in 0x4E00..0x9FFF -> 1f
                ch.code in 0x3000..0x303F -> 1f
                ch.code >= 128 -> 1f
                else -> 0.5f
            }
        }
        return total
    }

    fun buildPlan(
        textWidthPx: Float,
        containerWidthPx: Float,
        lineDurationMs: Long,
        minScrollableDurationMs: Long = 1_400L,
    ): ScrollPlan {
        val travel = (textWidthPx - containerWidthPx).coerceAtLeast(0f)
        if (travel <= 1f || lineDurationMs < minScrollableDurationMs) {
            return ScrollPlan(
                shouldScroll = false,
                holdStartMs = lineDurationMs,
                scrollDurationMs = 0L,
                holdEndMs = 0L,
                travelPx = 0f,
            )
        }

        val holdStart = max((lineDurationMs * 0.10f).toLong(), 150L)
        val holdEnd = max((lineDurationMs * 0.18f).toLong(), 220L)
        val scrollDuration = (lineDurationMs - holdStart - holdEnd).coerceAtLeast(250L)

        return ScrollPlan(
            shouldScroll = true,
            holdStartMs = holdStart,
            scrollDurationMs = scrollDuration,
            holdEndMs = holdEnd,
            travelPx = travel,
        )
    }

    fun effectiveViewportWidthPx(
        containerWidthPx: Float,
        paddingStartPx: Float,
        paddingEndPx: Float,
    ): Float {
        val contentWidth = containerWidthPx - paddingStartPx.coerceAtLeast(0f) - paddingEndPx.coerceAtLeast(0f)
        return contentWidth.coerceAtLeast(0f)
    }
}
