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
    val wind: Double?, val symbol: String
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
            .remove("gothic_weather_expires").remove("gothic_weather_last_modified").apply()
    }

    fun current(now: Long = System.currentTimeMillis()): WeatherForecast? {
        if (location() != prefs.getString("met_forecast_location", null)) return null
        return cachedBody?.let { parse(it, now) }
    }

    fun refresh(onChanged: () -> Unit) {
        val key = location() ?: return
        val now = System.currentTimeMillis()
        if (now < retryAfter || (key == prefs.getString("met_forecast_location", null) &&
                now < prefs.getLong("gothic_weather_expires", 0L))) return
        if (!loading.compareAndSet(false, true)) return
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL("https://api.met.no/weatherapi/locationforecast/2.0/compact?$key")
                    .openConnection() as HttpURLConnection
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("User-Agent", "PurpleCharmLauncher/0.7.0 (github.com/odinwinchester/Purple-charm-launcher)")
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
                        if (location() == key) prefs.edit().putLong("gothic_weather_expires", expires).apply()
                    }
                } else if (status == 200 || status == 203) {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    require(parse(body, System.currentTimeMillis()) != null) { "Invalid forecast" }
                    synchronized(this) {
                        // Discard an old request if the user changed or disabled the location.
                        if (location() == key) {
                            cachedBody = body
                            val editor = prefs.edit().putString("met_forecast_json", body)
                                .putString("met_forecast_location", key)
                                .putLong("gothic_weather_expires", expires)
                                .remove("gothic_weather_last_modified")
                            connection.getHeaderField("Last-Modified")?.let {
                                editor.putString("gothic_weather_last_modified", it)
                            }
                            editor.apply()
                        }
                    }
                    onChanged()
                } else {
                    val retry = connection.getHeaderField("Retry-After")?.toLongOrNull()
                    if (location() == key) retryAfter = System.currentTimeMillis() + (retry ?: 900L).coerceAtLeast(60L) * 1000L
                }
            } catch (_: Exception) {
                if (location() == key) retryAfter = System.currentTimeMillis() + 15 * 60_000L
            } finally {
                connection?.disconnect()
                loading.set(false)
            }
        }.start()
    }

    companion object {
        internal fun parse(body: String, now: Long): WeatherForecast? = try {
            val series = JSONObject(body).getJSONObject("properties").getJSONArray("timeseries")
            var selected: JSONObject? = null
            for (i in 0 until series.length()) {
                val point = series.getJSONObject(i)
                val time = Instant.parse(point.getString("time")).toEpochMilli()
                if (time <= now) selected = point else break
            }
            selected?.let { point ->
                val data = point.getJSONObject("data")
                val instant = data.getJSONObject("instant").getJSONObject("details")
                val symbol = listOf("next_1_hours", "next_6_hours", "next_12_hours")
                    .firstNotNullOfOrNull { period ->
                        data.optJSONObject(period)?.optJSONObject("summary")
                            ?.optString("symbol_code")?.takeIf { it.isNotEmpty() }
                    } ?: ""
                val temperature = instant.getDouble("air_temperature")
                require(temperature.isFinite())
                WeatherForecast(Instant.parse(point.getString("time")).toEpochMilli(), temperature,
                    instant.optDouble("relative_humidity").takeIf { it.isFinite() },
                    instant.optDouble("wind_speed").takeIf { it.isFinite() }, symbol)
            }
        } catch (_: Exception) { null }
    }
}
