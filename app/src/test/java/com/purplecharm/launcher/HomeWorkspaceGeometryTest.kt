package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class HomeWorkspaceGeometryTest {
    @Test fun widgetNeverChangesIconViewportOrOverflowsWorkspace() {
        for (height in listOf(0, 1, 240, 400, 640, 960, 1600)) {
            val area = HomeWorkspaceGeometry.zones(height)
            assertEquals(area.widgetHeight, area.iconsTop)
            assertEquals(height, area.widgetHeight + area.iconsHeight)
            assertTrue(area.widgetHeight >= 0 && area.iconsHeight >= 0)
            for ((width, imageHeight) in listOf(806 to 423, 310 to 218, 814 to 376)) {
                for (preferred in listOf(240, 320, 420)) {
                    val image = WeatherGeometry.fitWithin(width, area.widgetHeight * 2 / 3,
                        preferred, width, imageHeight, 1)
                    assertTrue(image.height <= area.widgetHeight)
                    assertEquals(area.iconsTop, HomeWorkspaceGeometry.zones(height).iconsTop)
                }
            }
        }
    }
    @Test fun fitRespectsBothBoundsAndNeverDistortsSource() {
        for (width in listOf(120, 240, 360, 840)) for (height in listOf(40, 120, 300)) {
            val image = WeatherGeometry.fitWithin(width, height, width, 310, 218, 1)
            assertTrue(image.width <= width && image.height <= height)
            val ideal = (image.width - 2) * 218.0 / 310
            assertEquals(ideal, (image.height - 2).toDouble(), 0.5)
        }
    }
}
