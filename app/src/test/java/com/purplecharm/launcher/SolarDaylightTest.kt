package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class SolarDaylightTest {
    private fun time(s: String) = Instant.parse(s).toEpochMilli()
    @Test fun campoGrandeUsesLocationAtNightEvenWhenForecastSymbolWouldSayDay() {
        assertEquals(false, SolarDaylight.isDay(time("2026-10-25T02:00:00Z"), -20.47, -54.62))
        assertEquals(true, SolarDaylight.isDay(time("2026-10-25T16:00:00Z"), -20.47, -54.62))
    }
    @Test fun changingDeviceTimezoneDoesNotChangeInstantOrDaylight() {
        val old = TimeZone.getDefault()
        try {
            for (zone in listOf("Pacific/Auckland", "America/Campo_Grande", "Asia/Tokyo", "UTC")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                assertEquals(false, SolarDaylight.isDay(time("2026-10-25T02:00:00Z"), -20.47, -54.62))
            }
        } finally { TimeZone.setDefault(old) }
    }
    @Test fun distantLongitudesHaveOppositePeriodsAcrossDateLine() {
        val now = time("2026-03-20T00:00:00Z")
        assertEquals(false, SolarDaylight.isDay(now, 0.0, 0.0))
        assertEquals(true, SolarDaylight.isDay(now, 0.0, 179.9))
    }
    @Test fun polarDayAndPolarNightDoNotNeedFakeSunriseTimes() {
        assertEquals(true, SolarDaylight.isDay(time("2026-06-21T00:00:00Z"), 89.0, 0.0))
        assertEquals(false, SolarDaylight.isDay(time("2026-12-21T12:00:00Z"), 89.0, 0.0))
    }
    @Test fun missingAndInvalidCoordinatesAreExplicitlyUnknown() {
        assertNull(SolarDaylight.isDay(0, null, null))
        assertNull(SolarDaylight.isDay(0, Double.NaN, 0.0))
        assertNull(SolarDaylight.isDay(0, 91.0, 0.0))
    }
    @Test fun sunriseAndSunsetTransitionAroundEquinoxAtGreenwich() {
        assertEquals(false, SolarDaylight.isDay(time("2026-03-20T05:30:00Z"), 0.0, 0.0))
        assertEquals(true, SolarDaylight.isDay(time("2026-03-20T07:00:00Z"), 0.0, 0.0))
        assertEquals(true, SolarDaylight.isDay(time("2026-03-20T17:00:00Z"), 0.0, 0.0))
        assertEquals(false, SolarDaylight.isDay(time("2026-03-20T19:00:00Z"), 0.0, 0.0))
    }
}
