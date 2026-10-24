package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class WeatherGeometryTest {
    @Test fun allTwentySourcesFitAvailableWidthWithNoEmptyVerticalBand() {
        val sources = javaClass.getResourceAsStream("/weather-artwork-sizes.tsv")!!
            .bufferedReader().use { it.readLines() }
        assertEquals(20, sources.size)
        for (line in sources) {
            val (name, w, h) = line.split('\t')
            val sw = w.toInt(); val sh = h.toInt()
            for (density in 1..4) for (screenDp in listOf(160, 320, 360, 600, 840)) {
                for (preferredDp in listOf(240, 320, 420)) {
                    val actual = WeatherGeometry.artworkSize(screenDp * density, preferredDp * density, sw, sh, density)
                    assertTrue(name, actual.width <= screenDp * density)
                    assertTrue(name, actual.width <= preferredDp * density)
                    val innerW = actual.width - 2 * density
                    val innerH = actual.height - 2 * density
                    // Only unavoidable pixel rounding is allowed; no fixed-height letterboxing.
                    val idealHeight = innerW.toDouble() * sh / sw
                    assertTrue("$name has excess space/cropping", abs(innerH - idealHeight) <= 0.5)
                    assertTrue(innerW > 0 && innerH > 0)
                }
            }
        }
    }
    @Test fun differentOriginalAspectRatiosProduceDifferentHeights() {
        val solar = WeatherGeometry.artworkSize(360, 320, 806, 423, 1)
        val wide = WeatherGeometry.artworkSize(360, 320, 814, 376, 1)
        assertTrue(solar.height > wide.height)
    }
}
