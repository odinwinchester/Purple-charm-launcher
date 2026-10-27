package com.purplecharm.launcher

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

/** NOAA fractional-year equations. UTC + longitude makes the result independent of device timezone. */
internal object SolarDaylight {
    fun isDay(now: Long, latitude: Double?, longitude: Double?): Boolean? {
        if (latitude == null || longitude == null || !latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        val utc = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC)
        val hour = utc.hour + utc.minute / 60.0 + utc.second / 3600.0
        val gamma = 2 * PI / utc.toLocalDate().lengthOfYear() * (utc.dayOfYear - 1 + (hour - 12) / 24)
        val equation = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) -
            0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val declination = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) -
            0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) -
            0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val minutes = ((hour * 60 + equation + 4 * longitude) % 1440 + 1440) % 1440
        val angle = Math.toRadians(minutes / 4 - 180)
        val lat = Math.toRadians(latitude)
        val cosine = sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(angle)
        return cosine >= cos(Math.toRadians(90.833))
    }
}
