package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class AdditionalWeatherArtworkTest {
    private val names = setOf("sol_dia_chuvoso", "sol_dia_frio", "lua_noite_chuvosa", "lua_noite_fria",
        "eclipse_solar_frio", "eclipse_lunar_limpo")
    private val baseSun = setOf("sol_gotico_classico", "sol_gotico_cristal", "sol_gotico_nuvens", "sol_gotico_nascente")

    @Test fun approvedNewCelestialAssetsAndManualAlternativesHaveUniqueMappings() {
        val catalog = WeatherArtworkCatalog.all
        assertEquals(catalog.size, catalog.map { it.slug }.distinct().size)
        assertEquals(catalog.size, catalog.map { it.resource }.distinct().size)
        assertEquals(baseSun, catalog.filter { it.slug.startsWith("sol_gotico_") }.map { it.slug }.toSet())
        assertEquals(2, catalog.count { it.kind == WeatherArtworkCatalog.Kind.ECLIPSE })
        assertTrue(catalog.any { it.slug == "moldura_gotica_cristais_chuva" && it.kind == WeatherArtworkCatalog.Kind.SOLAR })
        assertTrue(catalog.any { it.slug == "lua_nova_gotica_orbe_lunar" })
        assertTrue(catalog.any { it.slug == "lua_crescente_gotica_emoldurada" })
        assertTrue(catalog.any { it.slug == "lua_cheia_rgba_transparente" })
        assertTrue(catalog.any { it.slug == "lua_minguante_gotica_cristal" })
    }

    @Test fun automaticSunUsesCompleteRainAndIndependentColdArtwork() {
        for (phase in 0..3) for (style in 0..3) {
            assertEquals("moldura_gotica_cristais_chuva",
                WeatherArtworkCatalog.select(true, style, 2, phase, null).slug)
            assertEquals("sol_gotico_cristal",
                WeatherArtworkCatalog.select(true, style, 3, phase, null).slug)
            assertEquals(listOf("sol_gotico_classico", "sol_gotico_cristal",
                "sol_gotico_nuvens", "sol_gotico_nascente")[style],
                WeatherArtworkCatalog.select(true, style, 0, phase, null).slug)
        }
    }

    @Test fun allFourLunarPhasesUseApprovedArtIndependentOfWeather() {
        val slugs = listOf("lua_nova_gotica_orbe_lunar", "lua_crescente_gotica_emoldurada",
            "lua_cheia_rgba_transparente", "lua_minguante_gotica_cristal")
        for (condition in 0..3) for (phase in 0..3) {
            assertEquals(slugs[phase], WeatherArtworkCatalog.select(false, 0, condition, phase, null).slug)
        }
    }

    @Test fun automaticSelectionNeverUsesEclipseOrManualRainMoon() {
        for (day in listOf(false, true)) for (sun in 0..3)
            for (condition in 0..3) for (phase in 0..3) for (auto in listOf(false, true)) {
                val art = WeatherArtworkCatalog.select(day, sun, condition, phase, null, auto)
                assertNotEquals(WeatherArtworkCatalog.Kind.ECLIPSE, art.kind)
                assertFalse(art.slug in setOf("lua_noite_chuvosa", "lua_noite_fria"))
            }
    }

    @Test fun manualChoiceOverridesAutomaticAndUnknownChoiceFallsBackToNewCatalog() {
        for (art in WeatherArtworkCatalog.all) for (condition in 0..3) {
            assertEquals(art, WeatherArtworkCatalog.select(true, 1, condition, 0, art.slug))
            assertEquals(art, WeatherArtworkCatalog.select(false, 1, condition, 3, art.slug))
        }
        assertEquals("sol_gotico_classico",
            WeatherArtworkCatalog.select(true, 0, 3, 0, null, automaticWeather = false).slug)
        assertEquals("moldura_gotica_cristais_chuva",
            WeatherArtworkCatalog.select(true, 0, 2, 0, "unknown_art").slug)
    }

    @Test fun sharedWeatherClassificationAndEffectsPreventDoubleWeather() {
        assertEquals(0, WeatherArtworkCatalog.condition(null, null))
        assertEquals(0, WeatherArtworkCatalog.condition(0, 23.0))
        assertEquals(1, WeatherArtworkCatalog.condition(3, 23.0))
        assertEquals(2, WeatherArtworkCatalog.condition(61, 23.0))
        assertEquals(2, WeatherArtworkCatalog.condition(95, 23.0))
        assertEquals(3, WeatherArtworkCatalog.condition(71, 23.0))
        assertEquals(3, WeatherArtworkCatalog.condition(61, 9.0))
        val sun = WeatherArtworkCatalog.all.first { it.slug == "sol_gotico_cristal" }
        val completeRain = WeatherArtworkCatalog.all.first { it.slug == "moldura_gotica_cristais_chuva" }
        val moon = WeatherArtworkCatalog.all.first { it.slug == "lua_cheia_rgba_transparente" }
        assertEquals(R.drawable.chuva_cristais_violeta_nuvens, WeatherArtworkCatalog.effectFor(sun, 2, true))
        assertEquals(R.drawable.sobreposicao_nuvens_geladas_roxas, WeatherArtworkCatalog.effectFor(sun, 3, true))
        assertEquals(R.drawable.nuvens_roxas_widget_rgba, WeatherArtworkCatalog.effectFor(moon, 1, true))
        assertNull(WeatherArtworkCatalog.effectFor(completeRain, 2, true))
        assertNull(WeatherArtworkCatalog.effectFor(sun, 2, false))
    }

    @Test fun additionalDimensionsFitOriginalBoundsWithoutChangingWorkspaceZones() {
        val data = javaClass.getResourceAsStream("/weather-additional-artwork-sizes.tsv")!!.bufferedReader().use { it.readLines() }
        assertEquals(6, data.size)
        for (line in data) {
            val (_, w, h) = line.split('\t')
            for (density in 1..3) for (width in listOf(280, 360, 640, 960)) for (span in 1..4) {
                val zones = HomeWorkspaceGeometry.zones(640 * density, 180 * density)
                val placement = WidgetResizeGeometry.place(width * density, zones.widgetHeight * 2 / 3,
                    4, span, 24 * density, w.toInt(), h.toInt(), density)
                assertTrue(placement.artHeight <= zones.widgetHeight * 2 / 3)
                assertTrue(placement.artWidth + 48 * density <= width * density || placement.artWidth == 0)
                if (placement.artWidth > 2 * density) assertEquals(
                    (placement.artWidth - 2 * density) * h.toDouble() / w.toInt(),
                    (placement.artHeight - 2 * density).toDouble(), 0.5)
                assertEquals(zones.iconsTop, HomeWorkspaceGeometry.zones(640 * density, 180 * density).iconsTop)
            }
        }
    }
    @Test fun eclipseNotesDenyConfirmationAndAlternativeNotesKeepPhaseSeparate() {
        for (art in WeatherArtworkCatalog.all) {
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
