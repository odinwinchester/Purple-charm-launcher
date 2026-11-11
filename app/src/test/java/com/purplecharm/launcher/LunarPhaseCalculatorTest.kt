package com.purplecharm.launcher

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LunarPhaseCalculatorTest {
    @Test fun knownNewMoonReference() {
        val date = Instant.parse("2000-01-06T18:14:00Z")
        assertEquals(LunarPhaseCalculator.Phase.NEW, LunarPhaseCalculator.phaseAt(date))
        assertEquals(0, LunarPhaseCalculator.artworkIndexAt(date))
    }

    @Test fun lunarCycleApproximateQuarters() {
        val start = Instant.parse("2000-01-06T18:14:00Z")
        val day = 86_400L
        assertEquals(LunarPhaseCalculator.Phase.WAXING, LunarPhaseCalculator.phaseAt(start.plusSeconds(7 * day)))
        assertEquals(LunarPhaseCalculator.Phase.FULL, LunarPhaseCalculator.phaseAt(start.plusSeconds(14 * day)))
        assertEquals(LunarPhaseCalculator.Phase.WANING, LunarPhaseCalculator.phaseAt(start.plusSeconds(22 * day)))
        assertEquals(LunarPhaseCalculator.Phase.NEW, LunarPhaseCalculator.phaseAt(start.plusSeconds(29 * day)))
    }
}
