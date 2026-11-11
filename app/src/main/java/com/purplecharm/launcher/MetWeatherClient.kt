package com.purplecharm.launcher

import android.content.SharedPreferences
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

internal data class WeatherForecast(
    val time: Long, val temperature: Double, val humidity: Double?,
    val wind: Double?, val symbol: String,
    val precipitationMm: Double? = null, val rainProbability: Double? = null,
    val windDirection: Double? = null, val gust: Double? = null,
    val pressure: Double? = null, val dewPoint: Double? = null,
    val visibilityMeters: Double? = null,
    val uvClearSky: Double? = null
) {
    val code: Int get() = MetSymbols.code(symbol)
    val isDay: Boolean? get() = when {
        symbol.endsWith("_night") -> false
        symbol.endsWith("_day") -> true
        else -> null // Neutral/polar symbols carry no day/night information.
    }
    val description: String get() = when (code) {
        71 -> "Neve / neve e chuva"
        95 -> "Trovoadas"
        61 -> "Chuva"
        45 -> "Nevoeiro"
        3 -> "Nublado"
        2 -> "Parcialmente nublado"
        1 -> "Poucas nuvens"
        0 -> "Céu limpo"
        else -> "Condição indisponível"
    }
}

internal object MetSymbols {
    fun code(symbol: String): Int = when {
        "snow" in symbol || "sleet" in symbol -> 71
        "thunder" in symbol -> 95
        "rain" in symbol -> 61
        "fog" in symbol -> 45
        symbol.startsWith("partlycloudy") -> 2
        symbol.startsWith("cloudy") -> 3
        symbol.startsWith("fair") -> 1
        symbol.startsWith("clearsky") -> 0
        else -> -1
    }
}

/** Foreground-only caller; cached series advances to the current forecast hour offline. */
internal class MetWeatherClient(private val prefs: SharedPreferences) {
    private val loading = AtomicBoolean(false)
    @Volatile private var retryAfter = 0L
    @Volatile private var cachedBody: String? = prefs.getString("met_forecast_json", null)
    private fun location(): String? {
        val lat = prefs.getString("gothic_weather_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("gothic_weather_lon", null)?.toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return String.format(Locale.US, "lat=%.4f&lon=%.4f", lat, lon)
    }

    @Synchronized fun clearForecast() {
        cachedBody = null
        retryAfter = 0L
        prefs.edit().remove("met_forecast_json").remove("met_forecast_location")
            .remove("gothic_weather_expires").remove("gothic_weather_last_modified").remove("met_forecast_received").remove("met_weather_error").apply()
    }

    fun current(now: Long = System.currentTimeMillis()): WeatherForecast? {
        if (location() == null || location() != prefs.getString("met_forecast_location", null)) return null
        return cachedBody?.let { parse(it, now) }
    }

    /** Last actual cached point, explicitly marked stale by callers; never a live reading. */
    fun lastKnown(now: Long = System.currentTimeMillis()): WeatherForecast? {
        if (location() == null || location() != prefs.getString("met_forecast_location", null)) return null
        return cachedBody?.let { parseLastKnown(it, now) }
    }

    fun upcoming(now: Long = System.currentTimeMillis()): List<WeatherForecast> {
        if (location() == null || location() != prefs.getString("met_forecast_location", null)) return emptyList()
        return cachedBody?.let { parseUpcoming(it, now) } ?: emptyList()
    }

    /** Forecast points across the next five calendar days, independently of the hourly panel. */
    fun upcomingDays(now: Long = System.currentTimeMillis()): List<WeatherForecast> {
        if (location() == null || location() != prefs.getString("met_forecast_location", null)) return emptyList()
        return cachedBody?.let { parseUpcomingDays(it, now) } ?: emptyList()
    }

    fun refresh(force: Boolean = false, onChanged: () -> Unit): Boolean {
        val key = location() ?: return false
        val now = System.currentTimeMillis()
        if (now < retryAfter || (!force && key == prefs.getString("met_forecast_location", null) &&
                now < prefs.getLong("gothic_weather_expires", 0L))) return false
        if (!loading.compareAndSet(false, true)) return false
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?$key")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "PurpleCharmLauncher/0.7.2 (github.com/odinwinchester/Purple-charm-launcher)")
                connection.setRequestProperty("Accept", "application/json")
                if (key == prefs.getString("met_forecast_location", null) && cachedBody != null) {
                    prefs.getString("gothic_weather_last_modified", null)?.let {
                        connection.setRequestProperty("If-Modified-Since", it)
                    }
                }
                val status = connection.responseCode
                val expires = connection.getHeaderFieldDate("Expires", 0L)
                    .coerceAtLeast(System.currentTimeMillis() + 60_000L)
                if (status == 304 && cachedBody != null) {
                    synchronized(this) {
                        if (location() == key) prefs.edit().putLong("gothic_weather_expires", expires).putLong("met_forecast_received", System.currentTimeMillis()).remove("met_weather_error").apply()
                    }
                } else if (status == 200 || status == 203) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    require(parse(body, System.currentTimeMillis()) != null) { "Invalid forecast" }
                    synchronized(this) {
                        // Discard an old request if the user changed or disabled the location.
                        if (location() == key) {
                            cachedBody = body
                            val editor = prefs.edit().putString("met_forecast_json", body)
                                .putLong("met_forecast_received", System.currentTimeMillis())
                                .putString("met_forecast_location", key)
                                .putLong("gothic_weather_expires", expires)
                                .remove("gothic_weather_last_modified").remove("met_weather_error")
                            connection.getHeaderField("Last-Modified")?.let {
                                editor.putString("gothic_weather_last_modified", it)
                            }
                            editor.apply()
                        }
                    }
                } else {
                    val retry = connection.getHeaderField("Retry-After")?.toLongOrNull()
                    if (location() == key) {
                        retryAfter = System.currentTimeMillis() + (retry ?: 900L).coerceAtLeast(60L) * 1000L
                        prefs.edit().putString("met_weather_error", "HTTP $status • mantendo somente previsão ainda válida").apply()
                    }
                }
            } catch (_: Exception) {
                if (location() == key) {
                    retryAfter = System.currentTimeMillis() + 15 * 60_000L
                    prefs.edit().putString("met_weather_error", "Rede ou resposta indisponível • retentativa automática em 15 minutos").apply()
                }
            } finally {
                connection?.disconnect()
                loading.set(false)
                onChanged()
            }
        }.start()
        return true
    }

    companion object {
        private fun series(body: String): org.json.JSONArray {
            val properties = JSONObject(body).getJSONObject("properties")
            val unit = properties.optJSONObject("meta")?.optJSONObject("units")?.optString("air_temperature")
            require(unit.isNullOrEmpty() || unit == "celsius")
            return properties.getJSONArray("timeseries")
        }

        private fun decodePoint(point: JSONObject): WeatherForecast {
            val data = point.getJSONObject("data")
            val instant = data.getJSONObject("instant").getJSONObject("details")
            val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours")
                .firstNotNullOfOrNull { period -> data.optJSONObject(period)?.optJSONObject("summary")
                    ?.optString("symbol_code")?.takeIf { it.isNotEmpty() } } ?: ""
            val temperature = instant.getDouble("air_temperature")
            require(temperature.isFinite())
            // Keep precipitation amount and probability within the same forecast interval.
            val rainDetails = listOf("next_1_hours", "next_6_hours", "next_12_hours")
                .firstNotNullOfOrNull { period ->
                    data.optJSONObject(period)?.optJSONObject("details")?.takeIf {
                        it.optDouble("precipitation_amount", Double.NaN).isFinite() ||
                            it.optDouble("probability_of_precipitation", Double.NaN).isFinite()
                    }
                }
            val precipitation = rainDetails?.optDouble("precipitation_amount", Double.NaN)
                ?.takeIf { it.isFinite() && it >= 0.0 }
            val probability = rainDetails?.optDouble("probability_of_precipitation", Double.NaN)
                ?.takeIf { it.isFinite() && it in 0.0..100.0 }
            fun detail(key: String) = instant.optDouble(key, Double.NaN).takeIf { it.isFinite() }
            return WeatherForecast(
                Instant.parse(point.getString("time")).toEpochMilli(), temperature,
                detail("relative_humidity"), detail("wind_speed"), symbol,
                precipitation, probability, detail("wind_from_direction"),
                detail("wind_speed_of_gust"), detail("air_pressure_at_sea_level"),
                detail("dew_point_temperature"), detail("visibility"),
                detail("ultraviolet_index_clear_sky")?.takeIf { it >= 0.0 }
            )
        }

        internal fun parseUpcomingDays(body: String, now: Long): List<WeatherForecast> = try {
            val series = series(body)
            val end = now + 6L * 24 * 60 * 60_000L
            val result = mutableListOf<WeatherForecast>()
            for (i in 0 until series.length()) {
                val item = series.getJSONObject(i)
                val time = Instant.parse(item.getString("time")).toEpochMilli()
                if (time > end) break
                if (time >= now) runCatching { decodePoint(item) }.getOrNull()?.let(result::add)
            }
            result
        } catch (_: Exception) { emptyList() }

        internal fun parseUpcoming(body: String, now: Long): List<WeatherForecast> = try {
            // Future forecast points remain usable even when the current point has expired.
            val series = series(body)
            val result = mutableListOf<WeatherForecast>()
            for (i in 0 until series.length()) {
                val item = series.getJSONObject(i)
                val time = Instant.parse(item.getString("time")).toEpochMilli()
                if (time > now + 24 * 60 * 60_000L || result.size == 12) break
                if (time > now) runCatching { decodePoint(item) }.getOrNull()?.let { result.add(it) }
            }
            result
        } catch (_: Exception) { emptyList() }

        /** Historical fallback: at most 24 hours old, never future or wrong-unit data. */
        internal fun parseLastKnown(body: String, now: Long): WeatherForecast? = try {
            val points = series(body)
            var selected: JSONObject? = null
            for (i in 0 until points.length()) {
                val item = points.getJSONObject(i)
                if (Instant.parse(item.getString("time")).toEpochMilli() <= now) selected = item else break
            }
            selected?.let {
                val age = now - Instant.parse(it.getString("time")).toEpochMilli()
                if (age in 0..(24L * 60 * 60_000L)) decodePoint(it) else null
            }
        } catch (_: Exception) { null }

        internal fun parse(body: String, now: Long): WeatherForecast? = try {
            val series = series(body)
            var selected: JSONObject? = null
            for (i in 0 until series.length()) {
                val point = series.getJSONObject(i)
                val time = Instant.parse(point.getString("time")).toEpochMilli()
                if (time <= now) selected = point else break
            }
            selected?.let { point ->
                val timestamp = Instant.parse(point.getString("time")).toEpochMilli()
                // Do not label an old last point as current after an offline cache has expired.
                require(now - timestamp in 0..(2 * 60 * 60_000L))
                decodePoint(point)
            }
        } catch (_: Exception) { null }
    }
}
