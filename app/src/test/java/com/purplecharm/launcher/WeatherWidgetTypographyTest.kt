package com.purplecharm.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherWidgetTypographyTest {
    @Test fun narrowWidgetKeepsLegibleMinimum() {
        assertEquals(0.60f, WeatherWidgetTypography.scale(150f), 0.001f)
        assertEquals(25.2f, 42f * WeatherWidgetTypography.scale(150f), 0.001f)
    }

    @Test fun referenceWidthKeepsApprovedTypography() {
        assertEquals(1f, WeatherWidgetTypography.scale(340f), 0.001f)
        assertEquals(42f, 42f * WeatherWidgetTypography.scale(340f), 0.001f)
    }

    @Test fun largeWidgetHasUpperBoundAndScaleIsMonotonic() {
        assertEquals(1.10f, WeatherWidgetTypography.scale(600f), 0.001f)
        val widths = listOf(150f, 200f, 280f, 340f, 400f, 600f)
        assertTrue(widths.zipWithNext().all { (a, b) ->
            WeatherWidgetTypography.scale(a) <= WeatherWidgetTypography.scale(b)
        })
    }
}
