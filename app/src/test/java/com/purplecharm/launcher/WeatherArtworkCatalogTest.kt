package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WeatherArtworkCatalogTest {
    @Test fun exposesExactlyTwentyDistinctExistingImagesIncludingAllWetColdMoons() {
        val catalog = WeatherArtworkCatalog.all
        assertEquals(20, catalog.size)
        assertEquals(20, catalog.map { it.slug }.distinct().size)
        assertEquals(20, catalog.map { it.resource }.distinct().size)
        for (condition in 0..3) for (phase in 0..3) {
            val art = WeatherArtworkCatalog.select(false, 0, condition, phase, null)
            assertEquals(4 + condition * 4 + phase, catalog.indexOf(art))
        }
        assertEquals(4, catalog.count { "noite_chuvosa" in it.slug })
        assertEquals(4, catalog.count { "noite_fria_neve" in it.slug })
        assertFalse(catalog.any { it.slug == "dia_chuvoso" || it.slug == "dia_frio" })
    }
    @Test fun manualCustomizationIsOptionalAndUnknownSelectionReturnsToAutomatic() {
        val manual = WeatherArtworkCatalog.all.last()
        assertEquals(manual, WeatherArtworkCatalog.select(true, 1, 0, 0, manual.slug))
        assertEquals("dia_limpo", WeatherArtworkCatalog.select(true, 1, 0, 0, null).slug)
        assertEquals("dia_limpo", WeatherArtworkCatalog.select(true, 1, 0, 0, "removed_art").slug)
    }
}
