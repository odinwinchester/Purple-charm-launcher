package com.purplecharm.launcher

internal object WeatherArtworkCatalog {
    enum class Kind { SOLAR, LUNAR_PHASE, LUNAR_ALTERNATIVE, ECLIPSE }
    data class Art(val slug: String, val label: String, val resource: Int,
                   val kind: Kind = Kind.LUNAR_PHASE, val panoramic: Boolean = false)
    val all = listOf(
        // New approved solar layer assets, separate from weather overlays.
        Art("sol_gotico_classico", "Sol Gótico Clássico", R.drawable.sol_gotico_classico, Kind.SOLAR),
        Art("sol_gotico_cristal", "Sol Gótico de Cristal", R.drawable.sol_gotico_cristal, Kind.SOLAR),
        Art("sol_gotico_nuvens", "Sol Gótico entre Nuvens", R.drawable.sol_gotico_nuvens, Kind.SOLAR),
        Art("sol_gotico_nascente", "Sol Gótico Nascente", R.drawable.sol_gotico_nascente, Kind.SOLAR),
        Art("moldura_gotica_cristais_chuva", "Sol Dia Chuvoso (arte completa)", R.drawable.moldura_gotica_cristais_chuva, Kind.SOLAR, panoramic = true),
        Art("lua_minguante_gotica_cristal", "Lua Minguante Gótica de Cristal", R.drawable.lua_minguante_gotica_cristal, Kind.LUNAR_ALTERNATIVE),
        Art("lua_nova_gotica_orbe_lunar", "Lua Nova Gótica com Orbe Lunar", R.drawable.lua_nova_gotica_orbe_lunar, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("lua_crescente_gotica_emoldurada", "Lua Crescente Gótica com Moldura", R.drawable.lua_crescente_gotica_emoldurada, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("lua_cheia_rgba_transparente", "Lua Cheia (camada independente)", R.drawable.lua_cheia_rgba_transparente, Kind.LUNAR_ALTERNATIVE),
        Art("lua_noite_chuvosa", "Lua • Noite Chuvosa (alternativa manual)", R.drawable.lua_noite_chuvosa, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("lua_noite_fria", "Lua • Noite Fria (alternativa manual)", R.drawable.lua_noite_fria, Kind.LUNAR_ALTERNATIVE, panoramic = true),
        Art("eclipse_solar_frio", "Eclipse Solar • Frio (manual, evento não confirmado)", R.drawable.eclipse_solar_frio, Kind.ECLIPSE),
        Art("eclipse_lunar_limpo", "Eclipse Lunar • Limpo (manual, evento não confirmado)", R.drawable.eclipse_lunar_limpo, Kind.ECLIPSE)
    )
    private val solarBase = listOf("sol_gotico_classico", "sol_gotico_cristal", "sol_gotico_nuvens", "sol_gotico_nascente")
    private val lunarBase = listOf("lua_nova_gotica_orbe_lunar", "lua_crescente_gotica_emoldurada",
        "lua_cheia_rgba_transparente", "lua_minguante_gotica_cristal")
    private val retiredSolar = mapOf(
        "dia_limpo" to "sol_gotico_classico", "dia_nublado" to "sol_gotico_nuvens",
        "dia_chuvoso" to "moldura_gotica_cristais_chuva", "dia_frio" to "sol_gotico_cristal",
        "sol_dia_chuvoso" to "moldura_gotica_cristais_chuva", "sol_dia_frio" to "sol_gotico_cristal"
    )
    /** Read legacy preferences without reintroducing the retired 16-art catalog. */
    fun resolveManual(slug: String?): String? {
        if (slug == null) return null
        if (all.any { it.slug == slug }) return slug
        retiredSolar[slug]?.let { return it }
        val oldMoon = Regex("^lua_(0[1-9]|1[0-6])_lua_").find(slug)
        if (oldMoon != null) {
            val index = oldMoon.groupValues[1].toInt() - 1
            return lunarBase[index % lunarBase.size]
        }
        return null
    }
    private val bySlug by lazy { all.associateBy { it.slug } }

    /** Consistent condition categories shared by both weather screens. */
    fun condition(code: Int?, temperature: Double?): Int {
        if (code == null || temperature == null) return 0
        return when {
            code in 71..77 || code in 85..86 || temperature <= 12.0 -> 3
            code in 51..67 || code in 80..82 || code in 95..99 -> 2
            code in 2..3 || code == 45 -> 1
            else -> 0
        }
    }

    fun supportsOverlay(art: Art): Boolean =
        art.slug.startsWith("sol_gotico_") ||
            art.slug == "lua_cheia_rgba_transparente" ||
            art.slug == "lua_minguante_gotica_cristal"

    fun effectFor(art: Art, condition: Int, available: Boolean): Int? =
        if (!available || !supportsOverlay(art)) null else when (condition) {
            1 -> R.drawable.nuvens_roxas_widget_rgba
            2 -> R.drawable.chuva_cristais_violeta_nuvens
            3 -> R.drawable.sobreposicao_nuvens_geladas_roxas
            else -> null
        }


    fun select(isSun: Boolean, sunIndex: Int, condition: Int, phase: Int, manual: String?,
               automaticWeather: Boolean = true): Art {
        resolveManual(manual)?.let(bySlug::get)?.let { return it }
        val slug = if (isSun) {
            when {
                automaticWeather && condition == 2 -> "moldura_gotica_cristais_chuva"
                automaticWeather && condition == 3 -> "sol_gotico_cristal"
                else -> solarBase[sunIndex.coerceIn(solarBase.indices)]
            }
        } else {
            lunarBase[phase.coerceIn(lunarBase.indices)]
        }
        return requireNotNull(bySlug[slug]) { "Missing weather art mapping: $slug" }
    }
    fun note(art: Art, phase: Int): String = when (art.kind) {
        Kind.ECLIPSE -> "Arte de eclipse • seleção manual; evento e visibilidade não confirmados"
        Kind.LUNAR_ALTERNATIVE -> "Arte lunar alternativa • fase do componente: " +
            listOf("Nova", "Crescente", "Cheia", "Minguante")[phase.coerceIn(0, 3)]
        else -> "Arte meteorológica • dados reais exibidos separadamente"
    }
}
