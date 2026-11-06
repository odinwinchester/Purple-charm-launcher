package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WeatherArtworkCatalogTest {
    @Test fun activeCatalogContainsOnlyLayeredArtworkAndManualExceptions() {
        val catalog = WeatherArtworkCatalog.all
        assertEquals(13, catalog.size)
        assertEquals(catalog.size, catalog.map { it.slug }.toSet().size)
        assertEquals(catalog.size, catalog.map { it.resource }.toSet().size)
        assertFalse(catalog.any { it.slug.matches(Regex("lua_\\d{2}_.*")) })
        for (phase in 0..3) {
            val art = WeatherArtworkCatalog.select(false, 0, 2, phase, null)
            assertEquals(listOf("lua_nova_gotica_orbe_lunar", "lua_crescente_gotica_emoldurada",
                "lua_cheia_rgba_transparente", "lua_minguante_gotica_cristal")[phase], art.slug)
        }
    }

    @Test fun retiredManualPreferencesMigrateToCurrentArtWithoutBreakingUserChoice() {
        for (phase in 0..3) for (condition in 0..3) {
            val index = 1 + condition * 4 + phase
            val oldSlug = "lua_${index.toString().padStart(2, '0')}_lua_" +
                listOf("nova", "crescente", "cheia", "minguante")[phase] + "_" +
                listOf("noite_limpa", "noite_nublada", "noite_chuvosa", "noite_fria_neve")[condition]
            assertEquals(WeatherArtworkCatalog.select(false, 0, 0, phase, null).slug,
                WeatherArtworkCatalog.resolveManual(oldSlug))
        }
        assertEquals("moldura_gotica_cristais_chuva", WeatherArtworkCatalog.resolveManual("sol_dia_chuvoso"))
        assertEquals("sol_gotico_cristal", WeatherArtworkCatalog.resolveManual("sol_dia_frio"))
        assertEquals("sol_gotico_classico", WeatherArtworkCatalog.select(true, 0, 0, 0, "unknown_art").slug)
    }
}
