package com.theveloper.pixelplay.presentation.components

import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSheetLogicTest {

    @Test
    fun sanitizeSyncedWords_removesLeadingTags_preventsOverlap() {
        val words = listOf(
            SyncedWord(time = 0, word = "v1:"),
            SyncedWord(time = 120, word = "Hello"),
            SyncedWord(time = 240, word = "world")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(listOf("Hello", "world"), sanitized.map { it.word })
        assertEquals(listOf(120, 240), sanitized.map { it.time })
    }

    @Test
    fun highlightSnapOffsetPx_alignsLineWithHighlightZone() {
        val viewportHeight = 960
        val itemSize = 120
        val highlightOffsetPx = 80f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)

        val expectedCenter = viewportHeight / 2f - highlightOffsetPx
        assertEquals(expectedCenter - itemSize / 2f, offset.toFloat(), 0.5f)
    }

    @Test
    fun highlightSnapOffsetPx_clampsWithinViewportForEndOfList() {
        val viewportHeight = 420
        val itemSize = 320
        val highlightOffsetPx = -160f

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx)
        val center = offset + itemSize / 2f

        assertTrue(center <= viewportHeight.toFloat())
        assertTrue(center >= itemSize / 2f)
    }

    @Test
    fun highlightSnapOffsetPx_handlesOversizedItems() {
        val viewportHeight = 200
        val itemSize = 260

        val offset = highlightSnapOffsetPx(viewportHeight, itemSize, highlightOffsetPx = 60f)

        assertEquals(0, offset)
    }

    @Test
    fun calculateHighlightMetrics_reservesBottomSpace() {
        val metrics = calculateHighlightMetrics(
            containerHeight = 480.dp,
            highlightZoneFraction = 0.22f,
            highlightOffset = 48.dp
        )

        assertTrue(metrics.bottomPadding > 0.dp)
        assertTrue(metrics.topPadding >= 0.dp)
        assertTrue(metrics.zoneHeight > 0.dp)
    }

    @Test
    fun sanitizeLyricLineText_stripsLrcTimestampTags() {
        val raw = "[00:26.42][01:12.34] Three in the morning, I ain't slept all weekend"

        val sanitized = sanitizeLyricLineText(raw)

        assertEquals("Three in the morning, I ain't slept all weekend", sanitized)
    }

    @Test
    fun normalizeWordEndTime_guaranteesForwardRangeWhenTimestampsMatch() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 2_000L,
            nextWordTimeMs = 2_000L,
            lineEndTimeMs = 2_800L
        )

        assertEquals(2_001L, endTime)
    }

    @Test
    fun normalizeWordEndTime_clampsToLineEnd() {
        val endTime = normalizeWordEndTime(
            currentWordTimeMs = 5_000L,
            nextWordTimeMs = 8_000L,
            lineEndTimeMs = 5_400L
        )

        assertEquals(5_400L, endTime)
    }

    @Test
    fun resolveLineEndTimeMs_extendsPastNextLineWhenLastWordStartsLater() {
        val line = SyncedLine(
            time = 1_000,
            line = "abc",
            words = listOf(
                SyncedWord(1_000, "a"),
                SyncedWord(1_600, "b"),
                SyncedWord(2_000, "c")
            )
        )

        val lineEnd = resolveLineEndTimeMs(line, nextLineStartMs = 2_000)

        assertEquals(2_001L, lineEnd)
    }

    @Test
    fun sanitizeSyncedWords_mergesWhitespaceOnlyTokensIntoPreviousWord() {
        val words = listOf(
            SyncedWord(time = 1000, word = "To"),
            SyncedWord(time = 1200, word = " "),
            SyncedWord(time = 1400, word = "fall")
        )

        val sanitized = sanitizeSyncedWords(words)

        assertEquals(2, sanitized.size)
        assertEquals("To ", sanitized[0].word)
        assertEquals("fall", sanitized[1].word)
    }

    @Test
    fun resolveHighlightedWordIndex_picksLastVisibleWordAtLineEnd() {
        val words = listOf(
            SyncedWord(time = 10_000, word = "fall"),
            SyncedWord(time = 10_250, word = "in"),
            SyncedWord(time = 10_500, word = "love")
        )

        val idx = resolveHighlightedWordIndex(
            words = words,
            positionMs = 10_900,
            lineStartTimeMs = 10_000,
            lineEndTimeMs = 11_000
        )

        assertEquals(2, idx)
    }

    @Test
    fun resolveSeekPositionMs_subtractsPositiveLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(11_250L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_addsNegativeLyricsOffset() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 12_000L,
            lyricsSyncOffsetMs = -750
        )

        assertEquals(12_750L, seekPosition)
    }

    @Test
    fun resolveSeekPositionMs_clampsToZeroWhenOffsetWouldGoNegative() {
        val seekPosition = resolveSeekPositionMs(
            lineTimeMs = 300L,
            lyricsSyncOffsetMs = 750
        )

        assertEquals(0L, seekPosition)
    }
}
