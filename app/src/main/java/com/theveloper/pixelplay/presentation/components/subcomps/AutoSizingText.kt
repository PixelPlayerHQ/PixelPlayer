package com.theveloper.pixelplay.presentation.components.subcomps

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ParagraphIntrinsics
import androidx.compose.ui.text.resolveDefaults
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AutoSizingTextToFill(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    minFontSize: TextUnit = 8.sp,
    fontFamily: FontFamily? = null,
    fontWeight: FontWeight? = null,
    maxFontSizeLimit: TextUnit = 100.sp, // Límite superior práctico para la búsqueda
    lineHeightRatio: Float = 1.2f, // Factor para el interlineado (e.g., 1.2f para un 20% más de espacio)
    maxLines: Int = Int.MAX_VALUE
) {
    // TextMeasurer se utiliza para medir el texto de forma eficiente.
    val textMeasurer = rememberTextMeasurer()
    // Density se necesita para convertir dp a px.
    val density = LocalDensity.current

    // Estado para el tamaño de fuente determinado.
    val currentFontSizeState = remember { mutableStateOf(minFontSize) }
    var currentFontSize by currentFontSizeState
    // Estado para saber si el cálculo está listo y podemos dibujar.
    var readyToDraw by remember { mutableStateOf(false) }

    // BoxWithConstraints nos da el maxWidth y maxHeight disponibles.
    BoxWithConstraints(modifier = modifier) {
        // Convertimos las restricciones de dp a píxeles una sola vez.
        val maxWidthPx = with(density) { maxWidth.toPx() }.toInt()
        val maxHeightPx = with(density) { maxHeight.toPx() }.toInt()

        // LaunchedEffect para recalcular cuando el texto, estilo, límites de fuente,
        // ratio de interlineado o el tamaño del contenedor cambien.
        LaunchedEffect(text, style, minFontSize, maxFontSizeLimit, lineHeightRatio, maxLines, maxWidthPx, maxHeightPx) {
            readyToDraw = false // Indicar que necesitamos recalcular.
            var bestFitFontSize = minFontSize // Empezamos asumiendo el mínimo.

            // Asegurarnos de que los límites para la búsqueda sean válidos.
            var lowerBoundSp = minFontSize.value
            var upperBoundSp = maxFontSizeLimit.value.coerceAtLeast(minFontSize.value)

            // Si el rango de búsqueda es inválido (e.g., min > max limit), usamos minFontSize.
            if (lowerBoundSp > upperBoundSp + 0.01f) {
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            }

            // 1. Comprobar si el texto con minFontSize (y su lineHeight correspondiente) ya se desborda.
            val minFontEffectiveLineHeight = minFontSize * lineHeightRatio
            val minFontEffectiveStyle = style.copy(
                fontSize = minFontSize,
                lineHeight = minFontEffectiveLineHeight
            )
            val minFontLayoutResult = textMeasurer.measure(
                text = AnnotatedString(text),
                style = minFontEffectiveStyle,
                overflow = TextOverflow.Clip, // Usamos Clip para la medición precisa.
                softWrap = true,
                maxLines = maxLines, // Permitir todas las líneas necesarias.
                constraints = Constraints(
                    maxWidth = maxWidthPx.coerceAtLeast(0), // Asegurar que no sea negativo.
                    maxHeight = maxHeightPx.coerceAtLeast(0) // Asegurar que no sea negativo.
                )
            )

            if (minFontLayoutResult.hasVisualOverflow) {
                // Incluso con minFontSize, el texto se desborda. Usaremos minFontSize y se truncará.
                currentFontSize = minFontSize
                readyToDraw = true
                return@LaunchedEffect
            } else {
                // minFontSize cabe, así que es nuestro "mejor ajuste" inicial.
                bestFitFontSize = minFontSize
            }

            // 2. Búsqueda binaria para encontrar el tamaño de fuente más grande que quepa.
            // Iteramos un número fijo de veces para asegurar convergencia.
            repeat(15) { // 15 iteraciones suelen ser suficientes para la precisión en sp.
                // Si la diferencia entre los límites es muy pequeña, hemos convergido.
                if (upperBoundSp - lowerBoundSp < 0.1f) {
                    currentFontSize = bestFitFontSize
                    readyToDraw = true
                    return@LaunchedEffect // Salimos del LaunchedEffect.
                }

                val midSp = (lowerBoundSp + upperBoundSp) / 2f
                val candidateFontSize = midSp.sp

                // Evitar medir tamaños más pequeños que nuestro mejor ajuste conocido, si ya pasamos por ellos.
                if (candidateFontSize.value < bestFitFontSize.value && candidateFontSize.value < midSp) {
                    lowerBoundSp = midSp + 0.01f // Continuar búsqueda en la mitad superior.
                    return@repeat // Saltar esta iteración de repeat.
                }

                // Calculamos el lineHeight dinámicamente basado en el candidateFontSize.
                val currentEffectiveLineHeight = candidateFontSize * lineHeightRatio
                val candidateStyle = style.copy(
                    fontSize = candidateFontSize,
                    lineHeight = currentEffectiveLineHeight
                )

                val layoutResult = textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = candidateStyle,
                    overflow = TextOverflow.Clip,
                    softWrap = true,
                    maxLines = maxLines,
                    constraints = Constraints(
                        maxWidth = maxWidthPx.coerceAtLeast(0),
                        maxHeight = maxHeightPx.coerceAtLeast(0)
                    )
                )

                if (layoutResult.hasVisualOverflow) {
                    // El tamaño candidato es demasiado grande (se desborda en alto o ancho).
                    upperBoundSp = midSp - 0.01f
                } else {
                    // El tamaño candidato cabe. Es nuestro nuevo "mejor ajuste".
                    // Intentaremos encontrar uno aún más grande.
                    bestFitFontSize = candidateFontSize
                    lowerBoundSp = midSp + 0.01f
                }
            }

            currentFontSize = bestFitFontSize
            readyToDraw = true
        }

        // Solo dibujamos el Text una vez que hemos determinado el tamaño de fuente.
        if (readyToDraw) {
            // Aplicamos el fontSize y el lineHeight calculados al Text final.
            val finalEffectiveLineHeight = currentFontSize * lineHeightRatio
            Text(
                text = text,
                modifier = Modifier, // El modifier del Text no necesita fillMaxSize aquí.
                style = style.copy(
                    fontSize = currentFontSize,
                    lineHeight = finalEffectiveLineHeight
                ),
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                overflow = TextOverflow.Ellipsis, // Trunca si, a pesar de todo, aún se desborda.
                softWrap = true,
                // El tamaño de fuente se eligió para que todas las líneas quepan en altura.
                maxLines = maxLines
            )
        }
    }
}