package com.purplecharm.launcher

import kotlin.math.ceil
import kotlin.math.roundToInt

internal object WidgetResizeGeometry {
    data class Placement(val span: Int, val column: Int, val cellWidth: Double,
                         val artWidth: Int, val artHeight: Int, val occupiedRows: Int)
    fun place(width: Int, height: Int, columns: Int, requested: Int, handle: Int,
              sourceWidth: Int, sourceHeight: Int, border: Int): Placement {
        require(columns > 0 && sourceWidth > 0 && sourceHeight > 0)
        val cell = width.coerceAtLeast(0).toDouble() / columns
        fun art(span: Int): WeatherGeometry.ArtworkSize {
            val w = (span * cell).toInt() - 2 * handle
            return WeatherGeometry.fitWithin(w.coerceAtLeast(0), height, w.coerceAtLeast(0),
                sourceWidth, sourceHeight, border)
        }
        val possible = (1..columns).filter { span ->
            val wanted = (span * cell).toInt() - 2 * handle
            val actual = art(span)
            actual.width == wanted && actual.width > 0 && actual.height <= height
        }
        val span = possible.lastOrNull { it <= requested.coerceIn(1, columns) }
            ?: possible.firstOrNull() ?: 1
        val size = art(span)
        return Placement(span, (columns - span) / 2, cell, size.width, size.height,
            if (height <= 0) 0 else ceil(size.height / (height / 3.0)).toInt().coerceIn(0, 3))
    }
    fun snap(initialSpan: Int, deltaPx: Float, cellWidth: Double, columns: Int): Int =
        if (cellWidth <= 0) initialSpan.coerceIn(1, columns)
        else (initialSpan + deltaPx / cellWidth).roundToInt().coerceIn(1, columns)
}
