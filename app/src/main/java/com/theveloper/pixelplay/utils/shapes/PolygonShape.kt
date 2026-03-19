package com.theveloper.pixelplay.utils.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin


/**
 * Shape describing Polygons
 *
 * Note: The shape draws within the minimum of provided width and height so can't be used to create stretched shape.
 *
 * @param sides number of sides.
 * @param rotation value between 0 - 360
 */
class PolygonShape(sides: Int, private val rotation: Float = 0f) : Shape {

    private companion object {
        const val TWO_PI = 2 * PI
    }

    private val sideCount = sides.coerceAtLeast(3)
    private val stepCount = TWO_PI / sideCount

    private val rotationDegree = (PI / 180) * rotation


    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = Outline.Generic(Path().apply {

        val r = min(size.height, size.width) * .5f

        val xCenter = size.width * .5f
        val yCenter = size.height * .5f

        var t = -rotationDegree
        val startX = r * cos(t)
        val startY = r * sin(t)
        moveTo((startX + xCenter).toFloat(), (startY + yCenter).toFloat())

        repeat(sideCount - 1) {
            t += stepCount
            val x = r * cos(t)
            val y = r * sin(t)
            lineTo((x + xCenter).toFloat(), (y + yCenter).toFloat())
        }
        close()
    })
}
