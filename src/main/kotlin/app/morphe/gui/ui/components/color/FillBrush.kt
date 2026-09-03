/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.color

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import app.morphe.gui.data.model.GradientType
import app.morphe.gui.data.model.MorpheFill
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

fun MorpheFill.toBrush(size: Size): Brush? = when (this) {
    is MorpheFill.Solid -> SolidColorBrush(Color(argb))
    is MorpheFill.Image -> null
    is MorpheFill.Gradient -> {
        val ordered = stops.sortedBy { it.position }
        val colorStops = ordered.map { it.position.coerceIn(0f, 1f) to Color(it.argb) }.toTypedArray()
        when {
            colorStops.isEmpty() -> SolidColorBrush(Color.Transparent)
            colorStops.size == 1 -> SolidColorBrush(colorStops[0].second)
            type == GradientType.RADIAL -> Brush.radialGradient(
                colorStops = colorStops,
                center = size.center,
                radius = maxOf(size.width, size.height) * 0.75f,
            )
            type == GradientType.CONIC -> Brush.sweepGradient(
                colorStops = colorStops,
                center = size.center,
            )
            else -> {
                val (start, end) = linearEndpoints(angleDeg, size)
                Brush.linearGradient(colorStops = colorStops, start = start, end = end)
            }
        }
    }
}

private fun SolidColorBrush(color: Color): Brush = Brush.linearGradient(listOf(color, color))

private fun linearEndpoints(angleDeg: Float, size: Size): Pair<Offset, Offset> {
    val radians = Math.toRadians(angleDeg.toDouble())
    val dx = cos(radians).toFloat()
    val dy = sin(radians).toFloat()
    val half = (abs(dx) * size.width + abs(dy) * size.height) / 2f
    val center = size.center
    return Offset(center.x - dx * half, center.y - dy * half) to
        Offset(center.x + dx * half, center.y + dy * half)
}

private val Size.center: Offset get() = Offset(width / 2f, height / 2f)
