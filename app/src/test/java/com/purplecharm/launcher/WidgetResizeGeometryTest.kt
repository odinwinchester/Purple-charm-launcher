package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WidgetResizeGeometryTest {
    @Test fun allArtResizesStayInsideZoneAndOnColumnBoundaries() {
        val rows = javaClass.getResourceAsStream("/weather-artwork-sizes.tsv")!!.bufferedReader().use { it.readLines() }
        for (line in rows) {
            val (_, w, h) = line.split('\t')
            for (columns in 4..6) for (width in listOf(280, 360, 640, 960)) {
                for (height in listOf(70, 140, 240)) for (span in 1..columns) {
                    val p = WidgetResizeGeometry.place(width, height, columns, span, 24, w.toInt(), h.toInt(), 1)
                    assertTrue(p.artWidth >= 0 && p.artHeight >= 0)
                    assertTrue(p.artHeight <= height)
                    assertTrue(p.column >= 0 && p.column + p.span <= columns)
                    assertTrue(p.artWidth + 48 <= width || p.artWidth == 0)
                    if (p.artWidth > 2) assertEquals((p.artWidth - 2) * h.toDouble() / w.toInt(), (p.artHeight - 2).toDouble(), 0.5)
                    assertTrue(p.occupiedRows in 0..3)
                }
            }
        }
    }
    @Test fun snappingClampsAndRespondsToEitherHandleDirection() {
        assertEquals(4, WidgetResizeGeometry.snap(3, 90f, 90.0, 4))
        assertEquals(2, WidgetResizeGeometry.snap(3, -90f, 90.0, 4))
        assertEquals(1, WidgetResizeGeometry.snap(3, -9000f, 90.0, 4))
        assertEquals(4, WidgetResizeGeometry.snap(3, 9000f, 90.0, 4))
    }
}
