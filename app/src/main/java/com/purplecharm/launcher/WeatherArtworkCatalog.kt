package com.purplecharm.launcher

internal object WeatherArtworkCatalog {
    enum class Kind { SOLAR, LUNAR_PHASE, LUNAR_ALTERNATIVE, ECLIPSE }
    data class Art(val slug: String, val label: String, val resource: Int,
                   val kind: Kind = Kind.LUNAR_PHASE, val panoramic: Boolean = false)
    val all = listOf(
        Art("dia_quente", "Dia Quente", R.drawable.dia_quente, Kind.SOLAR),
        Art("dia_limpo", "Dia Limpo", R.drawable.dia_limpo, Kind.SOLAR),
        Art("dia_nublado", "Dia Nublado", R.drawable.dia_nublado, Kind.SOLAR),
        Art("nascer_do_sol", "Nascer do Sol", R.drawable.nascer_do_sol, Kind.SOLAR),
        Art("lua_01_lua_nova_noite_limpa", "Lua Nova • Noite limpa", R.drawable.lua_01_lua_nova_noite_limpa),
        Art("lua_02_lua_crescente_noite_limpa", "Lua Crescente • Noite limpa", R.drawable.lua_02_lua_crescente_noite_limpa),
        Art("lua_03_lua_cheia_noite_limpa", "Lua Cheia • Noite limpa", R.drawable.lua_03_lua_cheia_noite_limpa),
        Art("lua_04_lua_minguante_noite_limpa", "Lua Minguante • Noite limpa", R.drawable.lua_04_lua_minguante_noite_limpa),
        Art("lua_05_lua_nova_noite_nublada", "Lua Nova • Noite nublada", R.drawable.lua_05_lua_nova_noite_nublada),
        Art("lua_06_lua_crescente_noite_nublada", "Lua Crescente • Noite nublada", R.drawable.lua_06_lua_crescente_noite_nublada),
        Art("lua_07_lua_cheia_noite_nublada", "Lua Cheia • Noite nublada", R.drawable.lua_07_lua_cheia_noite_nublada),
        Art("lua_08_lua_minguante_noite_nublada", "Lua Minguante • Noite nublada", R.drawable.lua_08_lua_minguante_noite_nublada),
        Art("lua_09_lua_nova_noite_chuvosa", "Lua Nova • Noite chuvosa", R.drawable.lua_09_lua_nova_noite_chuvosa),
        Art("lua_10_lua_crescente_noite_chuvosa", "Lua Crescente • Noite chuvosa", R.drawable.lua_10_lua_crescente_noite_chuvosa),
        Art("lua_11_lua_cheia_noite_chuvosa", "Lua Cheia • Noite chuvosa", R.drawable.lua_11_lua_cheia_noite_chuvosa),
        Art("lua_12_lua_minguante_noite_chuvosa", "Lua Minguante • Noite chuvosa", R.drawable.lua_12_lua_minguante_noite_chuvosa),
        Art("lua_13_lua_nova_noite_fria_neve", "Lua Nova • Noite fria / neve", R.drawable.lua_13_lua_nova_noite_fria_neve),
        Art("lua_14_lua_crescente_noite_fria_neve", "Lua Crescente • Noite fria / neve", R.drawable.lua_14_lua_crescente_noite_fria_neve),
        Art("lua_15_lua_cheia_noite_fria_neve", "Lua Cheia • Noite fria / neve", R.drawable.lua_15_lua_cheia_noite_fria_neve),
        Art("lua_16_lua_minguante_noite_fria_neve", "Lua Minguante • Noite fria / neve", R.drawable.lua_16_lua_minguante_noite_fria_neve),
        Art("sol_dia_chuvoso", "Sol • Dia Chuvoso", R.drawable.sol_dia_chuvoso, Kind.SOLAR),
        Art("sol_dia_frio", "Sol • Dia Frio", R.drawable.sol_dia_frio, Kind.SOLAR),
        Art("lua_noite_chuvosa", "Lua • Noite Chuvosa (alternativa manual)", R.drawable.lua_noite_chuvosa, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("lua_noite_fria", "Lua • Noite Fria (alternativa manual)", R.drawable.lua_noite_fria, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("eclipse_solar_frio", "Eclipse Solar • Frio (manual, evento não confirmado)", R.drawable.eclipse_solar_frio, Kind.ECLIPSE),
        Art("eclipse_lunar_limpo", "Eclipse Lunar • Limpo (manual, evento não confirmado)", R.drawable.eclipse_lunar_limpo, Kind.ECLIPSE)
    )
    fun select(isSun: Boolean, sunIndex: Int, condition: Int, phase: Int, manual: String?,
               automaticWeather: Boolean = true): Art {
        all.firstOrNull { it.slug == manual }?.let { return it }
        // Climate can select the new solar art, never an eclipse or phase-free lunar alternative.
        if (isSun && automaticWeather) {
            if (condition == 2) return all.first { it.slug == "sol_dia_chuvoso" }
            if (condition == 3) return all.first { it.slug == "sol_dia_frio" }
        }
        // The first 20 entries and all four phase mappings remain unchanged.
        return all[if (isSun) sunIndex.coerceIn(0, 3) else 4 + condition.coerceIn(0, 3) * 4 + phase.coerceIn(0, 3)]
    }
    fun note(art: Art, phase: Int): String = when (art.kind) {
        Kind.ECLIPSE -> "Arte de eclipse • seleção manual; evento e visibilidade não confirmados"
        Kind.LUNAR_ALTERNATIVE -> "Arte lunar alternativa • fase do componente: " +
            listOf("Nova", "Crescente", "Cheia", "Minguante")[phase.coerceIn(0, 3)]
        else -> "Arte preservada • valores impressos ilustrativos"
    }
}
