package com.theveloper.pixelplay.presentation.screens.search.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

object GenreTypography {

    data class TitlePresentation(
        val firstLine: String,
        val secondLine: String?,
        val style: TextStyle,
        val secondLineWidthFraction: Float
    )

    @OptIn(ExperimentalTextApi::class)
    fun resolveTitlePresentation(
        genreId: String,
        genreName: String,
        isGridView: Boolean,
        cardWidthPx: Int,
        horizontalPaddingPx: Int,
        textMeasurer: TextMeasurer
    ): TitlePresentation {
        val normalizedName = genreName.trim().replace(Regex("\\s+"), " ")
        val profile = GenreTitleProfile.from(normalizedName)
        val hash = genreId.hashCode().toLong().absoluteValue
        val fullLineWidthPx = (cardWidthPx - (horizontalPaddingPx * 2)).coerceAtLeast(0)
        val secondLineWidthFraction = secondLineWidthFraction(profile, isGridView)
        val secondLineWidthPx = (fullLineWidthPx * secondLineWidthFraction).roundToInt().coerceAtLeast(0)
        val words = normalizedName.split(" ").filter { it.isNotBlank() }
        val styleCandidates = buildStyleCandidates(hash = hash, profile = profile, isGridView = isGridView)

        styleCandidates.forEach { style ->
            if (fitsSingleLine(normalizedName, style, fullLineWidthPx, textMeasurer)) {
                return TitlePresentation(
                    firstLine = normalizedName,
                    secondLine = null,
                    style = style,
                    secondLineWidthFraction = secondLineWidthFraction
                )
            }

            if (words.size > 1) {
                val bestBreak = findBestBreak(
                    words = words,
                    style = style,
                    firstLineWidthPx = fullLineWidthPx,
                    secondLineWidthPx = secondLineWidthPx,
                    textMeasurer = textMeasurer,
                    allowSecondLineOverflow = false
                )

                if (bestBreak != null) {
                    return TitlePresentation(
                        firstLine = bestBreak.firstLine,
                        secondLine = bestBreak.secondLine,
                        style = style,
                        secondLineWidthFraction = secondLineWidthFraction
                    )
                }
            }
        }

        val fallbackStyle = styleCandidates.last()
        val fallbackBreak = if (words.size > 1) {
            findBestBreak(
                words = words,
                style = fallbackStyle,
                firstLineWidthPx = fullLineWidthPx,
                secondLineWidthPx = secondLineWidthPx,
                textMeasurer = textMeasurer,
                allowSecondLineOverflow = true
            )
        } else {
            null
        }

        return TitlePresentation(
            firstLine = fallbackBreak?.firstLine ?: normalizedName,
            secondLine = fallbackBreak?.secondLine,
            style = fallbackStyle,
            secondLineWidthFraction = secondLineWidthFraction
        )
    }

    @OptIn(ExperimentalTextApi::class)
    fun getGenreStyle(genreId: String, genreName: String): TextStyle {
        val normalizedName = genreName.trim().replace(Regex("\\s+"), " ")
        val profile = GenreTitleProfile.from(normalizedName)
        val hash = genreId.hashCode().toLong().absoluteValue
        return buildStyleCandidates(hash = hash, profile = profile, isGridView = true).first()
    }

    @OptIn(ExperimentalTextApi::class)
    private fun fitsSingleLine(
        text: String,
        style: TextStyle,
        maxWidthPx: Int,
        textMeasurer: TextMeasurer
    ): Boolean {
        val layoutResult = textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxWidthPx)
        )
        return !layoutResult.hasVisualOverflow && layoutResult.lineCount == 1
    }

    @OptIn(ExperimentalTextApi::class)
    private fun findBestBreak(
        words: List<String>,
        style: TextStyle,
        firstLineWidthPx: Int,
        secondLineWidthPx: Int,
        textMeasurer: TextMeasurer,
        allowSecondLineOverflow: Boolean
    ): BreakCandidate? {
        return (1 until words.size)
            .mapNotNull { breakIndex ->
                val firstLine = words.take(breakIndex).joinToString(" ")
                val secondLine = words.drop(breakIndex).joinToString(" ")

                val firstLayout = textMeasurer.measure(
                    text = AnnotatedString(firstLine),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = firstLineWidthPx)
                )

                if (firstLayout.hasVisualOverflow || firstLayout.lineCount != 1) {
                    return@mapNotNull null
                }

                val secondLayout = textMeasurer.measure(
                    text = AnnotatedString(secondLine),
                    style = style,
                    overflow = TextOverflow.Clip,
                    softWrap = false,
                    maxLines = 1,
                    constraints = Constraints(maxWidth = secondLineWidthPx)
                )

                if (!allowSecondLineOverflow && (secondLayout.hasVisualOverflow || secondLayout.lineCount != 1)) {
                    return@mapNotNull null
                }

                BreakCandidate(
                    firstLine = firstLine,
                    secondLine = secondLine,
                    score = breakScore(
                        firstLine = firstLine,
                        secondLine = secondLine,
                        firstLayout = firstLayout,
                        secondLayout = secondLayout,
                        firstLineWidthPx = firstLineWidthPx,
                        secondLineWidthPx = secondLineWidthPx,
                        secondLineOverflowAllowed = allowSecondLineOverflow
                    )
                )
            }
            .maxByOrNull(BreakCandidate::score)
    }

    private fun breakScore(
        firstLine: String,
        secondLine: String,
        firstLayout: TextLayoutResult,
        secondLayout: TextLayoutResult,
        firstLineWidthPx: Int,
        secondLineWidthPx: Int,
        secondLineOverflowAllowed: Boolean
    ): Float {
        val firstUsage = firstLayout.size.width.toFloat() / firstLineWidthPx.coerceAtLeast(1)
        val secondUsage = secondLayout.size.width.toFloat() / secondLineWidthPx.coerceAtLeast(1)
        val firstLineBonus = firstUsage * 1.8f
        val secondLineBonus = secondUsage.coerceAtMost(1f) * 0.8f
        val orphanPenalty = listOf(firstLine, secondLine).count { it.length <= 3 } * 0.35f
        val tinyLastWordPenalty = secondLine
            .substringAfterLast(' ', secondLine)
            .takeIf { it.length <= 2 }
            ?.let { 0.25f }
            ?: 0f
        val overflowPenalty = if (secondLineOverflowAllowed && secondLayout.hasVisualOverflow) 0.45f else 0f
        return firstLineBonus + secondLineBonus - orphanPenalty - tinyLastWordPenalty - overflowPenalty
    }

    private fun secondLineWidthFraction(
        profile: GenreTitleProfile,
        isGridView: Boolean
    ): Float {
        return when {
            isGridView && profile.wordCount >= 3 -> 0.56f
            isGridView -> 0.52f
            profile.wordCount >= 3 -> 0.66f
            else -> 0.62f
        }
    }

    @OptIn(ExperimentalTextApi::class)
    private fun buildStyleCandidates(
        hash: Long,
        profile: GenreTitleProfile,
        isGridView: Boolean
    ): List<TextStyle> {
        val baseFontSize = when {
            isGridView && profile.isVeryDense -> 24f
            isGridView && profile.isDense -> 26f
            isGridView && profile.isCompact -> 30f
            isGridView -> 28f
            profile.isVeryDense -> 24f
            profile.isDense -> 27f
            else -> 30f
        }

        val expressiveWidth = when {
            profile.isCompact -> 146f
            profile.isDense -> 126f
            else -> 136f
        } + axisJitter(hash, divisor = 5L, span = 32f)

        val balancedWidth = when {
            profile.isVeryDense -> 104f
            profile.isDense -> 114f
            else -> 124f
        } + axisJitter(hash, divisor = 7L, span = 24f)

        val compactWidth = when {
            profile.longestWord >= 14 -> 84f
            profile.isVeryDense -> 90f
            else -> 98f
        } + axisJitter(hash, divisor = 11L, span = 18f)

        val heavyWeight = 650 + axisJitter(hash, divisor = 13L, span = 180f).roundToInt()
        val mediumWeight = 610 + axisJitter(hash, divisor = 17L, span = 150f).roundToInt()
        val compactWeight = 580 + axisJitter(hash, divisor = 19L, span = 120f).roundToInt()

        val slantExpressive = slantVariant(hash, divisor = 23L, options = floatArrayOf(0f, -4f, -8f, -12f))
        val slantBalanced = slantVariant(hash, divisor = 29L, options = floatArrayOf(0f, -3f, -6f, -9f))
        val slantCompact = slantVariant(hash, divisor = 31L, options = floatArrayOf(0f, -2f, -4f, -6f))

        val rondExpressive = 58f + axisJitter(hash, divisor = 37L, span = 72f)
        val rondBalanced = 44f + axisJitter(hash, divisor = 41L, span = 54f)
        val rondCompact = 36f + axisJitter(hash, divisor = 43L, span = 44f)

        val gradeExpressive = axisJitter(hash, divisor = 47L, span = 120f).roundToInt()
        val gradeBalanced = axisJitter(hash, divisor = 53L, span = 90f).roundToInt()
        val gradeCompact = axisJitter(hash, divisor = 59L, span = 70f).roundToInt()

        val xtraBase = 510f + axisJitter(hash, divisor = 61L, span = 42f)
        val yopqBase = 88f + axisJitter(hash, divisor = 67L, span = 22f)
        val ytlcBase = 500f + axisJitter(hash, divisor = 71L, span = 34f)

        return listOf(
            expressiveStyle(
                fontSize = baseFontSize + if (profile.isCompact) 1.5f else 0.5f,
                weight = heavyWeight.coerceIn(560, 820),
                width = expressiveWidth.coerceIn(102f, 166f),
                slant = slantExpressive,
                grade = gradeExpressive.coerceIn(-60, 120),
                rond = rondExpressive.coerceIn(18f, 120f),
                xtra = (xtraBase + 10f).coerceIn(490f, 560f),
                yopq = (yopqBase + 4f).coerceIn(76f, 110f),
                ytlc = (ytlcBase + 8f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize,
                weight = mediumWeight.coerceIn(540, 780),
                width = (balancedWidth + 10f).coerceIn(96f, 152f),
                slant = slantExpressive,
                grade = (gradeExpressive / 2).coerceIn(-50, 100),
                rond = (rondExpressive + 12f).coerceIn(24f, 126f),
                xtra = (xtraBase + 4f).coerceIn(490f, 560f),
                yopq = (yopqBase + 2f).coerceIn(76f, 110f),
                ytlc = ytlcBase.coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 0.5f,
                weight = heavyWeight.coerceIn(560, 820),
                width = balancedWidth.coerceIn(92f, 144f),
                slant = slantBalanced,
                grade = gradeBalanced.coerceIn(-50, 90),
                rond = rondBalanced.coerceIn(18f, 108f),
                xtra = xtraBase.coerceIn(490f, 560f),
                yopq = yopqBase.coerceIn(76f, 110f),
                ytlc = ytlcBase.coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 1.5f,
                weight = mediumWeight.coerceIn(540, 780),
                width = (balancedWidth - 10f).coerceIn(88f, 136f),
                slant = slantBalanced,
                grade = (gradeBalanced / 2).coerceIn(-40, 80),
                rond = (rondBalanced + 10f).coerceIn(24f, 112f),
                xtra = (xtraBase - 4f).coerceIn(490f, 560f),
                yopq = yopqBase.coerceIn(76f, 110f),
                ytlc = (ytlcBase - 4f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 2.5f,
                weight = compactWeight.coerceIn(520, 740),
                width = compactWidth.coerceIn(78f, 120f),
                slant = slantCompact,
                grade = gradeCompact.coerceIn(-35, 65),
                rond = rondCompact.coerceIn(18f, 96f),
                xtra = (xtraBase - 8f).coerceIn(490f, 560f),
                yopq = (yopqBase - 4f).coerceIn(76f, 110f),
                ytlc = (ytlcBase - 8f).coerceIn(474f, 528f)
            ),
            expressiveStyle(
                fontSize = baseFontSize - 4f,
                weight = (compactWeight - 30).coerceIn(500, 700),
                width = (compactWidth - 12f).coerceIn(72f, 108f),
                slant = slantCompact,
                grade = (gradeCompact / 2).coerceIn(-30, 50),
                rond = (rondCompact + 8f).coerceIn(24f, 102f),
                xtra = (xtraBase - 12f).coerceIn(490f, 560f),
                yopq = (yopqBase - 6f).coerceIn(76f, 110f),
                ytlc = (ytlcBase - 10f).coerceIn(474f, 528f)
            )
        )
    }

    @OptIn(ExperimentalTextApi::class)
    private fun expressiveStyle(
        fontSize: Float,
        weight: Int,
        width: Float,
        slant: Float,
        grade: Int,
        rond: Float,
        xtra: Float,
        yopq: Float,
        ytlc: Float
    ): TextStyle {
        return TextStyle(
            fontFamily = FontFamily(
                Font(
                    resId = R.font.gflex_variable,
                    variationSettings = FontVariation.Settings(
                        FontVariation.weight(weight),
                        FontVariation.width(width),
                        FontVariation.slant(slant),
                        FontVariation.grade(grade),
                        FontVariation.Setting("ROND", rond),
                        FontVariation.Setting("XTRA", xtra),
                        FontVariation.Setting("YOPQ", yopq),
                        FontVariation.Setting("YTLC", ytlc)
                    )
                )
            ),
            fontWeight = FontWeight(weight),
            fontSize = fontSize.sp,
            lineHeight = (fontSize * 0.92f).sp,
            letterSpacing = when {
                width >= 136f -> (-0.7).sp
                width >= 118f -> (-0.45).sp
                else -> (-0.18).sp
            }
        )
    }

    private fun slantVariant(hash: Long, divisor: Long, options: FloatArray): Float {
        val index = ((hash / divisor) % options.size).toInt()
        return options[index]
    }

    private fun axisJitter(hash: Long, divisor: Long, span: Float): Float {
        val normalized = ((hash / divisor) % 1000).toFloat() / 999f
        return (normalized - 0.5f) * span
    }

    private data class BreakCandidate(
        val firstLine: String,
        val secondLine: String,
        val score: Float
    )

    private data class GenreTitleProfile(
        val totalChars: Int,
        val wordCount: Int,
        val longestWord: Int
    ) {
        val isCompact: Boolean = totalChars <= 8 && wordCount <= 2 && longestWord <= 8
        val isDense: Boolean = totalChars >= 14 || wordCount >= 3 || longestWord >= 10
        val isVeryDense: Boolean = totalChars >= 18 || wordCount >= 4 || longestWord >= 13

        companion object {
            fun from(text: String): GenreTitleProfile {
                val words = text.split(" ").filter { it.isNotBlank() }
                return GenreTitleProfile(
                    totalChars = text.length,
                    wordCount = words.size,
                    longestWord = words.maxOfOrNull { it.length } ?: text.length
                )
            }
        }
    }
}
