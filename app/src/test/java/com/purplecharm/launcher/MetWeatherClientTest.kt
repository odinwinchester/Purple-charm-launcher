package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class MetWeatherClientTest {
    @Test fun snowThunderKeepsSnowArtwork() {
        assertEquals(71, MetSymbols.code("snowshowersandthunder_night"))
        assertEquals(71, MetSymbols.code("sleetandthunder_day"))
        assertEquals(95, MetSymbols.code("rainandthunder_day"))
        assertEquals(-1, MetSymbols.code("unknown"))
    }
    @Test fun partialCloudsAndFairRemainDistinct() {
        assertEquals(2, MetSymbols.code("partlycloudy_night"))
        assertEquals(1, MetSymbols.code("fair_day"))
        assertEquals(3, MetSymbols.code("cloudy"))
    }
    private fun point(time: String, temp: Int, period: String = "next_1_hours") = """
        {"time":"$time","data":{"instant":{"details":{"air_temperature":$temp}},
        "$period":{"summary":{"symbol_code":"fair_night"}}}}
    """.trimIndent()
    @Test fun cachedSeriesAdvancesToCurrentHourAndUsesSixHourFallback() {
        val body = """{"properties":{"timeseries":[${point("2026-10-24T00:00:00Z", 10)},${point("2026-10-24T01:00:00Z", 20, "next_6_hours")}]}}"""
        val parsed = MetWeatherClient.parse(body, Instant.parse("2026-10-24T01:30:00Z").toEpochMilli())!!
        assertEquals(20.0, parsed.temperature, 0.0)
        assertEquals(false, parsed.isDay)
        assertNull(parsed.humidity)
        assertNull(parsed.wind)
    }
    @Test fun invalidPayloadDoesNotInventWeather() {
        assertNull(MetWeatherClient.parse("{}", 0))
        assertNull(MetWeatherClient.parse("not json", 0))
    }
    @Test fun neutralSymbolDoesNotInventDaylight() {
        assertNull(WeatherForecast(0, 20.0, null, null, "cloudy").isDay)
    }
    @Test fun oldOfflinePointAndNonCelsiusPayloadCannotBePresentedAsCurrentCelsius() {
        val body = """{"properties":{"timeseries":[${point("2026-10-24T00:00:00Z", 50)}]}}"""
        assertNull(MetWeatherClient.parse(body, Instant.parse("2026-10-25T00:00:00Z").toEpochMilli()))
        val kelvin = org.json.JSONObject(body).apply {
            getJSONObject("properties").put("meta", org.json.JSONObject("""{"units":{"air_temperature":"kelvin"}}"""))
        }.toString()
        assertNull(MetWeatherClient.parse(kelvin, Instant.parse("2026-10-24T00:30:00Z").toEpochMilli()))
    }

    @Test fun upcomingUsesUtcInstantsAcrossLocalMidnightAndLimitsTheList() {
        val now = Instant.parse("2026-10-25T02:30:00Z").toEpochMilli()
        val points = (0..30).joinToString(",") { point(Instant.ofEpochMilli(now - 30 * 60_000L + it * 60 * 60_000L).toString(), it) }
        val body = """{"properties":{"timeseries":[$points]}}"""
        val result = MetWeatherClient.parseUpcoming(body, now)
        assertEquals(12, result.size)
        assertEquals(Instant.parse("2026-10-25T03:00:00Z").toEpochMilli(), result.first().time)
        val local = Instant.ofEpochMilli(result.first().time).atZone(java.time.ZoneId.of("America/Campo_Grande"))
        assertEquals("2026-10-24", local.toLocalDate().toString())
        assertEquals(23, local.hour)
        assertEquals(1.0, result.first().temperature, 0.0)
    }
    @Test fun upcomingKeepsRealIntervalsWithoutInventingMissingHours() {
        val now = Instant.parse("2026-10-24T00:30:00Z").toEpochMilli()
        val body = """{"properties":{"timeseries":[${point("2026-10-24T00:00:00Z", 10)},${point("2026-10-24T06:00:00Z", 16)},${point("2026-10-25T12:00:00Z", 99)}]}}"""
        val result = MetWeatherClient.parseUpcoming(body, now)
        assertEquals(1, result.size)
        assertEquals(16.0, result.single().temperature, 0.0)
        assertEquals(Instant.parse("2026-10-24T06:00:00Z").toEpochMilli(), result.single().time)
        assertTrue(MetWeatherClient.parseUpcoming(body, now + 48 * 60 * 60_000L).isEmpty())
    }
    @Test fun upcomingRejectsInvalidOrNonCelsiusData() {
        assertTrue(MetWeatherClient.parseUpcoming("not json", 0).isEmpty())
        val body = """{"properties":{"meta":{"units":{"air_temperature":"kelvin"}},"timeseries":[${point("2026-10-24T00:00:00Z", 273)},${point("2026-10-24T01:00:00Z", 274)}]}}"""
        assertTrue(MetWeatherClient.parseUpcoming(body, Instant.parse("2026-10-24T00:30:00Z").toEpochMilli()).isEmpty())
    }

}
