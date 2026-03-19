package com.theveloper.pixelplay.utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.data.model.Lyrics
import com.theveloper.pixelplay.data.model.SyncedLine
import com.theveloper.pixelplay.data.model.SyncedWord
import kotlinx.coroutines.flow.Flow
import java.lang.Character
import java.util.regex.Pattern
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

object LyricsUtils {

    private val LRC_LINE_REGEX = Pattern.compile("^\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})](.*)$")
    private val LRC_WORD_REGEX = Pattern.compile("<(\\d{2}):(\\d{2})[.:](\\d{2,3})>([^<]*)")
    private val LRC_WORD_TAG_REGEX = Regex("<\\d{2}:\\d{2}[.:]\\d{2,3}>")
    private val LRC_WORD_SPLIT_REGEX = Regex("(?=<\\d{2}:\\d{2}[.:]\\d{2,3}>)")
    private val LRC_TIMESTAMP_TAG_REGEX = Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]")

    /**
     * Parsea un String que contiene una letra en formato LRC o texto plano.
     * @param lyricsText El texto de la letra a procesar.
     * @return Un objeto Lyrics con las listas 'plain' o 'synced' pobladas.
     */
    fun parseLyrics(lyricsText: String?): Lyrics {
        if (lyricsText.isNullOrEmpty()) {
            return Lyrics(plain = emptyList(), synced = emptyList())
        }

        val syncedLines = mutableListOf<SyncedLine>()
        val plainLines = mutableListOf<String>()
        var isSynced = false

        lyricsText.lines().forEach { rawLine ->
            val line = sanitizeLrcLine(rawLine)
            if (line.isEmpty()) return@forEach

            val lineMatcher = LRC_LINE_REGEX.matcher(line)
            if (lineMatcher.matches()) {
                isSynced = true
                val minutes = lineMatcher.group(1)?.toLong() ?: 0
                val seconds = lineMatcher.group(2)?.toLong() ?: 0
                val fraction = lineMatcher.group(3)?.toLong() ?: 0
                val textWithTags = stripFormatCharacters(lineMatcher.group(4)?.trim() ?: "")
                val text = stripLrcTimestamps(textWithTags)

                val millis = if (lineMatcher.group(3)?.length == 2) fraction * 10 else fraction
                val lineTimestamp = minutes * 60 * 1000 + seconds * 1000 + millis

                // Enhanced word-by-word parsing
                if (text.contains(LRC_WORD_TAG_REGEX)) {
                    val words = mutableListOf<SyncedWord>()
                    val parts = text.split(LRC_WORD_SPLIT_REGEX)
                    val displayText = LRC_WORD_TAG_REGEX.replace(text, "")

                    for (part in parts) {
                        if (part.isEmpty()) continue
                        val wordMatcher = LRC_WORD_REGEX.matcher(part)
                        if (wordMatcher.find()) {
                            val wordMinutes = wordMatcher.group(1)?.toLong() ?: 0
                            val wordSeconds = wordMatcher.group(2)?.toLong() ?: 0
                            val wordFraction = wordMatcher.group(3)?.toLong() ?: 0
                            val wordText = stripFormatCharacters(wordMatcher.group(4) ?: "")
                            val timedWordText = wordText
                                .substringBefore('\n')
                                .substringBefore('\r')
                                .substringBefore("\\n")
                                .substringBefore("\\r")
                            val wordMillis = if (wordMatcher.group(3)?.length == 2) wordFraction * 10 else wordFraction
                            val wordTimestamp = wordMinutes * 60 * 1000 + wordSeconds * 1000 + wordMillis
                            if (timedWordText.isNotEmpty()) {
                                words.add(SyncedWord(wordTimestamp.toInt(), timedWordText))
                            }
                        } else {
                            // Preserve only leading untagged text as a timed word.
                            // Trailing untagged chunks (e.g. inline translations) should remain visible in line text
                            // but must not steal word highlight timing.
                            if (words.isEmpty()) {
                                val leading = stripFormatCharacters(part)
                                if (leading.isNotEmpty()) {
                                    words.add(SyncedWord(lineTimestamp.toInt(), leading))
                                }
                            }
                        }
                    }

                    if (words.isNotEmpty()) {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText, words))
                    } else {
                        syncedLines.add(SyncedLine(lineTimestamp.toInt(), displayText))
                    }
                } else {
                    syncedLines.add(SyncedLine(lineTimestamp.toInt(), text))
                }
            } else {
                // línea SIN timestamp
                val stripped = stripLrcTimestamps(stripFormatCharacters(line))
                // Si ya detectamos que el archivo tiene sincronización y ya existe
                // al menos una SyncedLine, tratamos esta línea como continuación
                // de la anterior
                if (isSynced && syncedLines.isNotEmpty()) {
                    val last = syncedLines.removeAt(syncedLines.lastIndex)
                    // Mantenemos el texto previo y añadimos la nueva línea con un salto de línea.
                    val mergedLineText = if (last.line.isEmpty()) {
                        stripped
                    } else {
                        last.line + "\n" + stripped
                    }
                    // Conservamos la lista de palabras sincronizadas si existía.
                    val merged = if (last.words?.isNotEmpty() == true) {
                        SyncedLine(last.time, mergedLineText, last.words)
                    } else {
                        SyncedLine(last.time, mergedLineText)
                    }

                    syncedLines.add(merged)
                } else {
                    // Si no hay sincronización en el archivo, es texto plano
                    plainLines.add(stripped)
                }
            }
        }

        return if (isSynced && syncedLines.isNotEmpty()) {
            val sortedSyncedLines = syncedLines.sortedBy { it.time }
            val pairedLines = pairTranslationLines(sortedSyncedLines)
            val plainVersion = pairedLines.map { it.line }
            Lyrics(synced = pairedLines, plain = plainVersion)
        } else {
            Lyrics(plain = plainLines)
        }
    }

    /**
     * Pairs consecutive synced lines that share the same timestamp.
     * The second line is treated as a translation of the first.
     * Only pairs one translation per original — a third line at the same timestamp stays separate.
     */
    internal fun pairTranslationLines(lines: List<SyncedLine>): List<SyncedLine> {
        if (lines.size < 2) return lines
        val result = mutableListOf<SyncedLine>()
        var i = 0
        while (i < lines.size) {
            val current = lines[i]
            val next = lines.getOrNull(i + 1)
            if (next != null && next.time == current.time && current.translation == null && current.line.isNotBlank() && next.line.isNotBlank()) {
                result.add(current.copy(translation = next.line))
                i += 2 // skip the translation line
            } else {
                result.add(current)
                i++
            }
        }
        return result
    }

    internal fun stripLrcTimestamps(value: String): String {
        if (value.isEmpty()) return value
        val withoutTags = LRC_TIMESTAMP_TAG_REGEX.replace(value, "")
        return withoutTags.trimStart()
    }

    /**
     * Converts synced lyrics to LRC format string.
     * Each line is formatted as [mm:ss.xx]text
     * @param syncedLines The list of synced lines to convert.
     * @return A string in LRC format.
     */
    fun syncedToLrcString(syncedLines: List<SyncedLine>): String {
        return syncedLines.sortedBy { it.time }.flatMap { line ->
            val totalMs = line.time
            val minutes = totalMs / 60000
            val seconds = (totalMs % 60000) / 1000
            val hundredths = (totalMs % 1000) / 10
            val timestamp = "[%02d:%02d.%02d]".format(minutes, seconds, hundredths)
            buildList {
                add("$timestamp${line.line}")
                if (!line.translation.isNullOrBlank()) {
                    add("$timestamp${line.translation}")
                }
            }
        }.joinToString("\n")
    }

    /**
     * Converts plain lyrics (list of lines) to a plain text string.
     * @param plainLines The list of plain text lines.
     * @return A string with each line separated by newline.
     */
    fun plainToString(plainLines: List<String>): String {
        return plainLines.joinToString("\n")
    }

    /**
     * Converts Lyrics object to LRC or plain text format based on available data.
     * Prefers synced lyrics if available.
     * @param lyrics The Lyrics object to convert.
     * @param preferSynced Whether to prefer synced lyrics over plain. Default true.
     * @return A string representation of the lyrics.
     */
    fun toLrcString(lyrics: Lyrics, preferSynced: Boolean = true): String {
        return if (preferSynced && !lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else if (!lyrics.plain.isNullOrEmpty()) {
            plainToString(lyrics.plain)
        } else if (!lyrics.synced.isNullOrEmpty()) {
            syncedToLrcString(lyrics.synced)
        } else {
            ""
        }
    }
}

private fun sanitizeLrcLine(rawLine: String): String {
    if (rawLine.isEmpty()) return rawLine

    val withoutTerminators = rawLine
        .trimEnd('\r', '\n')
        .filterNot { char ->
            Character.getType(char).toByte() == Character.FORMAT ||
                (Character.isISOControl(char) && char != '\t')
        }
        .trimEnd('\uFEFF')

    val trimmedPrefix = withoutTerminators.trimStart { it.isWhitespace() }
    val firstBracket = trimmedPrefix.indexOf('[')
    return if (firstBracket > 0) {
        trimmedPrefix.substring(firstBracket)
    } else {
        trimmedPrefix
    }
}

private fun stripFormatCharacters(value: String): String {
    val cleaned = value.filterNot { char ->
        Character.getType(char).toByte() == Character.FORMAT ||
            (Character.isISOControl(char) && char != '\t')
    }

    return when (cleaned) {
        "\"", "'" -> ""
        else -> cleaned
    }
}

@Composable
fun ProviderText(
    providerText: String,
    uri: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    accentColor: Color? = null
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = accentColor ?: MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    val annotatedString = buildAnnotatedString {
        withStyle(style = SpanStyle(color = textColor)) {
            append(providerText)
        }
        pushStringAnnotation(tag = "URL", annotation = uri)
        withStyle(style = SpanStyle(color = linkColor)) {
            append(" LRCLIB")
        }
        pop()
    }

    val baseStyle = MaterialTheme.typography.bodySmall
    val finalStyle = textAlign?.let { baseStyle.copy(textAlign = it) } ?: baseStyle
    
    ClickableText(
        text = annotatedString,
        onClick = { offset ->
            annotatedString.getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    uriHandler.openUri(annotation.item)
                }
        },
        style = finalStyle,
        modifier = modifier
    )
}

/**
 * Un composable que muestra una línea de burbujas animadas que se transforman
 * en notas musicales cuando suben y vuelven a ser círculos cuando bajan.
 *
 * @param positionFlow Un flujo que emite la posición de reproducción actual.
 * @param time El tiempo de inicio para que estas burbujas sean visibles.
 * @param color El color base para las burbujas y las notas.
 * @param nextTime El tiempo final para que estas burbujas sean visibles.
 * @param modifier El modificador a aplicar a este layout.
 */
@Composable
fun BubblesLine(
    positionFlow: Flow<Long>,
    time: Int,
    color: Color,
    nextTime: Int,
    modifier: Modifier = Modifier,
) {
    val position by positionFlow.collectAsStateWithLifecycle(initialValue = 0L)
    val isCurrent = position in time until nextTime
    val transition = rememberInfiniteTransition(label = "bubbles_transition")

    // Animación ralentizada para apreciar mejor el efecto.
    val animatedValue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "bubble_animation_progress"
    )

    var show by remember { mutableStateOf(false) }
    LaunchedEffect(isCurrent) {
        show = isCurrent
    }

    if (show) {
        val density = LocalDensity.current
        // Círculos más pequeños para acentuar la animación de escala.
        val bubbleRadius = remember(density) { with(density) { 4.dp.toPx() } }

        val (morphableCircle, morphableNote) = remember(bubbleRadius) {
            val circleNodes = createCirclePathNodes(radius = bubbleRadius)
            val noteNodes = createVectorNotePathNodes(targetSize = bubbleRadius * 2.5f)

            makePathsCompatible(circleNodes, noteNodes)
            circleNodes to noteNodes
        }

        Canvas(modifier = modifier.size(64.dp, 48.dp)) {
            val bubbleCount = 3
            val bubbleColor = color.copy(alpha = 0.7f)

            for (i in 0 until bubbleCount) {
                val progress = (animatedValue + i * (1f / bubbleCount)) % 1f
                val yOffset = sin(progress * 2 * PI).toFloat() * 8.dp.toPx()

                val morphProgress = when {
                    progress in 0f..0.25f -> progress / 0.25f
                    progress in 0.25f..0.5f -> 1.0f - (progress - 0.25f) / 0.25f
                    else -> 0f
                }.toFloat().coerceIn(0f, 1f)

                // La animación de escalado ahora es más pronunciada.
                val scale = lerpFloat(1.0f, 1.4f, morphProgress)

                // Se calcula un desplazamiento horizontal dinámico que se activa con el morphing.
                val xOffsetCorrection = lerpFloat(0f, bubbleRadius * 1.8f, morphProgress)

                val morphedPath = lerpPath(
                    start = morphableCircle,
                    stop = morphableNote,
                    fraction = morphProgress
                ).toPath()

                // Se posiciona el contenedor de la animación en su columna.
                translate(left = (size.width / (bubbleCount + 1)) * (i + 1)) {
                    // Se aplica el desplazamiento vertical (onda) y la corrección horizontal.
                    val drawOffset = Offset(x = xOffsetCorrection, y = size.height / 2 + yOffset)

                    translate(left = drawOffset.x, top = drawOffset.y) {
                        // Se aplica la transformación de escala antes de dibujar.
                        scale(scale = scale, pivot = Offset.Zero) {
                            drawPath(
                                path = morphedPath,
                                color = bubbleColor
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- Lógica de Path Morphing ---

private fun lerpPath(start: List<PathNode>, stop: List<PathNode>, fraction: Float): List<PathNode> {
    return start.mapIndexed { index, startNode ->
        val stopNode = stop[index]
        when (startNode) {
            is PathNode.MoveTo -> {
                val stopMoveTo = stopNode as PathNode.MoveTo
                PathNode.MoveTo(
                    lerpFloat(startNode.x, stopMoveTo.x, fraction),
                    lerpFloat(startNode.y, stopMoveTo.y, fraction)
                )
            }
            is PathNode.CurveTo -> {
                val stopCurveTo = stopNode as PathNode.CurveTo
                PathNode.CurveTo(
                    lerpFloat(startNode.x1, stopCurveTo.x1, fraction),
                    lerpFloat(startNode.y1, stopCurveTo.y1, fraction),
                    lerpFloat(startNode.x2, stopCurveTo.x2, fraction),
                    lerpFloat(startNode.y2, stopCurveTo.y2, fraction),
                    lerpFloat(startNode.x3, stopCurveTo.x3, fraction),
                    lerpFloat(startNode.y3, stopCurveTo.y3, fraction)
                )
            }
            else -> startNode
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

private fun List<PathNode>.toPath(): Path = Path().apply {
    this@toPath.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> moveTo(node.x, node.y)
            is PathNode.LineTo -> lineTo(node.x, node.y)
            is PathNode.CurveTo -> cubicTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
            is PathNode.Close -> close()
            else -> {}
        }
    }
}

private fun makePathsCompatible(nodes1: MutableList<PathNode>, nodes2: MutableList<PathNode>): Pair<MutableList<PathNode>, MutableList<PathNode>> {
    while (nodes1.size < nodes2.size) {
        nodes1.add(nodes1.size - 1, nodes1[nodes1.size - 2])
    }
    while (nodes2.size < nodes1.size) {
        nodes2.add(nodes2.size - 1, nodes2[nodes2.size - 2])
    }
    return nodes1 to nodes2
}

private fun createVectorNotePathNodes(targetSize: Float): MutableList<PathNode> {
    val pathData = "M239.5,1.9c-4.6,1.1 -8.7,3.6 -12.2,7.3 -6.7,6.9 -6.3,-2.5 -6.3,151.9 0,76.9 -0.3,140 -0.7,140.2 -0.5,0.3 -4.2,-0.9 -8.3,-2.5 -48.1,-19.3 -102.8,-8.3 -138.6,27.7 -35.8,36.1 -41.4,85.7 -13.6,120.7 18.6,23.4 52.8,37.4 86.2,35.3 34.8,-2.1 65.8,-16 89.5,-39.9 14.5,-14.6 24.9,-31.9 30.7,-50.6l2.3,-7.5 0.2,-133c0.2,-73.2 0.5,-133.6 0.8,-134.2 0.8,-2.4 62,28.5 84.3,42.4 22.4,14.1 34.1,30.4 37.2,51.9 2.4,16.5 -2.2,34.5 -13,50.9 -6,9.1 -7,12.1 -4.8,14.3 2.2,2.2 5.3,1.2 13.8,-4.5 26.4,-17.9 45.6,-48 50,-78.2 1.9,-12.9 0.8,-34.3 -2.4,-46.1 -8.7,-31.7 -30.4,-58 -64.1,-77.8 -64.3,-37.9 -116,-67.3 -119.6,-68.1 -5,-1.2 -7.1,-1.2 -11.4,-0.2z"
    val parser = PathParser().parsePathString(pathData)

    val groupScale = 0.253f
    val bounds = Path().apply { parser.toPath(this) }.getBounds()
    val maxDimension = max(bounds.width, bounds.height)
    val scale = if (maxDimension > 0f) targetSize / (maxDimension * groupScale) else 1f

    val matrix = Matrix()
    matrix.translate(x = -bounds.left, y = -bounds.top)
    matrix.scale(x = groupScale * scale, y = groupScale * scale)
    val finalWidth = bounds.width * groupScale * scale
    val finalHeight = bounds.height * groupScale * scale

    // Se centra el path en su origen (0,0) sin correcciones estáticas.
    matrix.translate(x = -finalWidth / 2f, y = -finalHeight / 2f)

    return parser.toNodes().toAbsolute().transform(matrix).toCurvesOnly()
}

private fun createCirclePathNodes(radius: Float): MutableList<PathNode> {
    val kappa = 0.552284749831f
    val rk = radius * kappa
    return mutableListOf(
        PathNode.MoveTo(0f, -radius),
        PathNode.CurveTo(rk, -radius, radius, -rk, radius, 0f),
        PathNode.CurveTo(radius, rk, rk, radius, 0f, radius),
        PathNode.CurveTo(-rk, radius, -radius, rk, -radius, 0f),
        PathNode.CurveTo(-radius, -rk, -rk, -radius, 0f, -radius),
        PathNode.Close
    )
}

// --- Funciones de Extensión para PathNode ---

private fun List<PathNode>.toAbsolute(): MutableList<PathNode> {
    val absoluteNodes = mutableListOf<PathNode>()
    var currentX = 0f
    var currentY = 0f
    this.forEach { node ->
        when (node) {
            is PathNode.MoveTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeMoveTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.MoveTo(currentX, currentY)) }
            is PathNode.LineTo -> { currentX = node.x; currentY = node.y; absoluteNodes.add(node) }
            is PathNode.RelativeLineTo -> { currentX += node.dx; currentY += node.dy; absoluteNodes.add(PathNode.LineTo(currentX, currentY)) }
            is PathNode.CurveTo -> { currentX = node.x3; currentY = node.y3; absoluteNodes.add(node) }
            is PathNode.RelativeCurveTo -> {
                absoluteNodes.add(PathNode.CurveTo(currentX + node.dx1, currentY + node.dy1, currentX + node.dx2, currentY + node.dy2, currentX + node.dx3, currentY + node.dy3))
                currentX += node.dx3; currentY += node.dy3
            }
            is PathNode.Close -> absoluteNodes.add(node)
            else -> {}
        }
    }
    return absoluteNodes
}

private fun MutableList<PathNode>.toCurvesOnly(): MutableList<PathNode> {
    val curveNodes = mutableListOf<PathNode>()
    var lastX = 0f
    var lastY = 0f

    this.forEach { node ->
        when(node) {
            is PathNode.MoveTo -> { curveNodes.add(node); lastX = node.x; lastY = node.y }
            is PathNode.LineTo -> { curveNodes.add(PathNode.CurveTo(lastX, lastY, node.x, node.y, node.x, node.y)); lastX = node.x; lastY = node.y }
            is PathNode.CurveTo -> { curveNodes.add(node); lastX = node.x3; lastY = node.y3 }
            is PathNode.Close -> curveNodes.add(node)
            else -> {}
        }
    }
    return curveNodes
}

private fun List<PathNode>.transform(matrix: Matrix): MutableList<PathNode> {
    return this.map { node ->
        when (node) {
            is PathNode.MoveTo -> PathNode.MoveTo(matrix.map(Offset(node.x, node.y)).x, matrix.map(Offset(node.x, node.y)).y)
            is PathNode.LineTo -> PathNode.LineTo(matrix.map(Offset(node.x, node.y)).x, matrix.map(Offset(node.x, node.y)).y)
            is PathNode.CurveTo -> {
                val p1 = matrix.map(Offset(node.x1, node.y1))
                val p2 = matrix.map(Offset(node.x2, node.y2))
                val p3 = matrix.map(Offset(node.x3, node.y3))
                PathNode.CurveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
            }
            else -> node
        }
    }.toMutableList()
}
