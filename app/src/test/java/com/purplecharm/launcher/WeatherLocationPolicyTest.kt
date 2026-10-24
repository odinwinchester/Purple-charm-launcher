package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WeatherLocationPolicyTest {
    @Test fun rejectsStaleInvalidAndVeryInaccurateCachedFixes() {
        assertTrue(WeatherLocationPolicy.usable(-20.4, -54.6, 3000f, 60_000))
        assertFalse(WeatherLocationPolicy.usable(-20.4, -54.6, 3000f, 31 * 60_000))
        assertFalse(WeatherLocationPolicy.usable(-20.4, -54.6, 11_000f, 0))
        assertFalse(WeatherLocationPolicy.usable(Double.NaN, -54.6, 10f, 0))
        assertFalse(WeatherLocationPolicy.usable(-91.0, -54.6, 10f, 0))
        assertFalse(WeatherLocationPolicy.usable(-20.4, -54.6, 10f, -1))
    }
    @Test fun ignoresJitterButUpdatesAfterTravelAndInitialFix() {
        assertFalse(WeatherLocationPolicy.moved(-20.4, -54.6, -20.4001, -54.6001))
        assertTrue(WeatherLocationPolicy.moved(-20.4, -54.6, -20.45, -54.6))
        assertTrue(WeatherLocationPolicy.moved(null, null, -20.4, -54.6))
    }
    @Test fun crossingDateLineUsesShortestDistance() {
        assertFalse(WeatherLocationPolicy.moved(0.0, 179.999, 0.0, -179.999))
        assertTrue(WeatherLocationPolicy.moved(0.0, 179.0, 0.0, -179.0))
    }
}
