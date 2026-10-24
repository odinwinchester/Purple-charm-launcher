package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class AdditionalWeatherArtworkTest {
    private val names = setOf("sol_dia_chuvoso", "sol_dia_frio", "lua_noite_chuvosa", "lua_noite_fria",
        "eclipse_solar_frio", "eclipse_lunar_limpo")
    @Test fun allSixOwnerResourcesAreRecognizedAndClassified() {
        val added = WeatherArtworkCatalog.all.drop(20)
        assertEquals(names, added.map { it.slug }.toSet())
        assertEquals(6, added.map { it.resource }.distinct().size)
        assertEquals(2, added.count { it.kind == WeatherArtworkCatalog.Kind.SOLAR })
        assertEquals(2, added.count { it.kind == WeatherArtworkCatalog.Kind.LUNAR_ALTERNATIVE && it.panoramic })
        assertEquals(2, added.count { it.kind == WeatherArtworkCatalog.Kind.ECLIPSE })
    }
    @Test fun automaticSolarUsesNewRainAndColdArtWhileDryArtRemainsUnchanged() {
        for (phase in 0..3) for (oldSun in 0..3) {
            assertEquals("sol_dia_chuvoso", WeatherArtworkCatalog.select(true, oldSun, 2, phase, null).slug)
            assertEquals("sol_dia_frio", WeatherArtworkCatalog.select(true, oldSun, 3, phase, null).slug)
            assertEquals(WeatherArtworkCatalog.all[oldSun], WeatherArtworkCatalog.select(true, oldSun, 0, phase, null))
        }
    }
    @Test fun fourLunarPhasesAndFourWeatherGroupsKeepOriginalSixteenMappings() {
        for (condition in 0..3) for (phase in 0..3) {
            val result = WeatherArtworkCatalog.select(false, 0, condition, phase, null)
            assertEquals(WeatherArtworkCatalog.all[4 + condition * 4 + phase], result)
            assertEquals(WeatherArtworkCatalog.Kind.LUNAR_PHASE, result.kind)
        }
    }
    @Test fun noAutomaticCombinationCanSelectEclipseOrPhaseFreeMoon() {
        for (day in listOf(false, true)) for (sun in 0..3) for (group in 0..3) for (phase in 0..3) {
            for (weatherAuto in listOf(false, true)) {
                val selected = WeatherArtworkCatalog.select(day, sun, group, phase, null, weatherAuto)
                assertNotEquals(WeatherArtworkCatalog.Kind.ECLIPSE, selected.kind)
                assertNotEquals(WeatherArtworkCatalog.Kind.LUNAR_ALTERNATIVE, selected.kind)
            }
        }
    }
    @Test fun manualChoiceOverridesWeatherAndLegacySolarChoicesRemainValid() {
        for (name in names) for (group in 0..3) {
            assertEquals(name, WeatherArtworkCatalog.select(true, 1, group, 0, name).slug)
            assertEquals(name, WeatherArtworkCatalog.select(false, 1, group, 3, name).slug)
        }
        assertEquals("dia_quente", WeatherArtworkCatalog.select(true, 0, 3, 0, null, automaticWeather = false).slug)
        assertEquals("sol_dia_chuvoso", WeatherArtworkCatalog.select(true, 0, 2, 0, "unknown_art").slug)
    }
    @Test fun additionalDimensionsFitOriginalBoundsWithoutChangingWorkspaceZones() {
        val data = javaClass.getResourceAsStream("/weather-additional-artwork-sizes.tsv")!!.bufferedReader().use { it.readLines() }
        assertEquals(6, data.size)
        for (line in data) {
            val (_, w, h) = line.split('\t')
            for (density in 1..3) for (width in listOf(280, 360, 640, 960)) for (span in 1..4) {
                val zones = HomeWorkspaceGeometry.zones(640 * density)
                val placement = WidgetResizeGeometry.place(width * density, zones.widgetHeight * 2 / 3,
                    4, span, 24 * density, w.toInt(), h.toInt(), density)
                assertTrue(placement.artHeight <= zones.widgetHeight * 2 / 3)
                assertTrue(placement.artWidth + 48 * density <= width * density || placement.artWidth == 0)
                if (placement.artWidth > 2 * density) assertEquals(
                    (placement.artWidth - 2 * density) * h.toDouble() / w.toInt(),
                    (placement.artHeight - 2 * density).toDouble(), 0.5)
                assertEquals(zones.iconsTop, HomeWorkspaceGeometry.zones(640 * density).iconsTop)
            }
        }
    }
    @Test fun eclipseNotesDenyConfirmationAndAlternativeNotesKeepPhaseSeparate() {
        for (art in WeatherArtworkCatalog.all.drop(20)) {
            if (art.kind == WeatherArtworkCatalog.Kind.ECLIPSE)
                assertTrue(WeatherArtworkCatalog.note(art, 0).contains("não confirmados"))
            if (art.kind == WeatherArtworkCatalog.Kind.LUNAR_ALTERNATIVE) {
                val notes = (0..3).map { WeatherArtworkCatalog.note(art, it) }
                assertEquals(4, notes.toSet().size)
                assertTrue(notes.all { it.contains("alternativa") && it.contains("fase do componente") })
            }
        }
    }
}
