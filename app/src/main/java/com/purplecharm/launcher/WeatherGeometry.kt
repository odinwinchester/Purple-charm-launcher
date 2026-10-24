package com.purplecharm.launcher

import kotlin.math.*

/** All calculations operate on the complete source rectangle, never a crop. */
internal object WeatherGeometry {
    data class ArtworkSize(val width: Int, val height: Int)
    fun artworkSize(availableWidth: Int, preferredWidth: Int, sourceWidth: Int,
                    sourceHeight: Int, border: Int): ArtworkSize {
        require(sourceWidth > 0 && sourceHeight > 0 && border >= 0)
        val width = minOf(availableWidth, preferredWidth).coerceAtLeast(2 * border + 1)
        val imageWidth = width - 2 * border
        return ArtworkSize(width, (imageWidth.toDouble() * sourceHeight / sourceWidth)
            .roundToInt().coerceAtLeast(1) + 2 * border)
    }
}

internal object WeatherLocationPolicy {
    const val INTERVAL_MS = 5 * 60_000L
    const val MOVEMENT_METERS = 2_000f
    const val MAX_CACHED_AGE_MS = 30 * 60_000L
    fun usable(latitude: Double, longitude: Double, accuracy: Float, ageMs: Long): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 && accuracy.isFinite() && accuracy in 0f..10_000f &&
            ageMs in 0..MAX_CACHED_AGE_MS

    fun moved(lat: Double?, lon: Double?, newLat: Double, newLon: Double): Boolean {
        if (lat == null || lon == null) return true
        val dLat = Math.toRadians(newLat - lat)
        val dLon = Math.toRadians(newLon - lon)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) *
            cos(Math.toRadians(newLat)) * sin(dLon / 2).pow(2)
        val meters = 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
        return meters >= MOVEMENT_METERS
    }
}
