package com.purplecharm.launcher

import java.time.Instant
import kotlin.math.floor

/**
 * Four visual categories for the approved lunar artwork.
 *
 * This is a mean-synodic-month approximation, not an astronomical ephemeris.
 * It must be validated against known lunar dates before it drives the UI.
 */
internal object LunarPhaseCalculator {
    enum class Phase { NEW, WAXING, FULL, WANING }

    private const val SYNODIC_MONTH_DAYS = 29.530588853
    // Known new moon: 2000-01-06 18:14 UTC.
    private val referenceNewMoon = Instant.parse("2000-01-06T18:14:00Z")

    fun phaseAt(instant: Instant): Phase {
        val elapsedDays = (instant.epochSecond - referenceNewMoon.epochSecond) / 86400.0
        val cycles = elapsedDays / SYNODIC_MONTH_DAYS
        val fraction = cycles - floor(cycles)
        return when {
            fraction < 0.125 || fraction >= 0.875 -> Phase.NEW
            fraction < 0.375 -> Phase.WAXING
            fraction < 0.625 -> Phase.FULL
            else -> Phase.WANING
        }
    }

    /** Index expected by WeatherArtworkCatalog: Nova, Crescente, Cheia, Minguante. */
    fun artworkIndexAt(instant: Instant): Int = when (phaseAt(instant)) {
        Phase.NEW -> 0
        Phase.WAXING -> 1
        Phase.FULL -> 2
        Phase.WANING -> 3
    }
}
