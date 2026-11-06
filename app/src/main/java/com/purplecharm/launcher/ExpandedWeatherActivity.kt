package com.purplecharm.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Approved violet-glass weather layout. The artwork is a separate, uncropped hero,
 * never a full-screen wallpaper underneath text. No fictitious weather values.
 */
class ExpandedWeatherActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("purple_charm_favorites", MODE_PRIVATE) }
    private val weather by lazy { MetWeatherClient(prefs) }
    private val graphics by lazy { GraphicResourceCache(resources) }
    private val weatherLocation by lazy {
        WeatherLocationController(this, prefs) { coordinatesChanged ->
            if (coordinatesChanged) weather.clearForecast()
            weather.refresh { runOnUiThread { if (!isFinishing && !isDestroyed) render() } }
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        }
    }
    private var page = "home"
    private var smoked = true
    private var previewSlug: String? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    // Fully cut-out panels: violet outline only, no opaque fill.
    private fun glass(): GradientDrawable = GradientDrawable().apply {
        // Fumê: black with 10 percentage points less transparency (alpha 220 -> 246).
        // Vazado: keep the existing appearance unchanged.
        setColor(if (smoked) Color.argb(246, 0, 0, 0)
            else Color.argb(130, 12, 5, 27))
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), Color.argb(190, 193, 146, 239))
    }

    private fun text(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(7), dp(10), dp(7))
        }

    private fun card(root: LinearLayout, title: String, content: String, onOpen: (() -> Unit)? = null) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            setPadding(dp(5), dp(4), dp(5), dp(4))
        }
        panel.addView(text(title, 17f, true))
        panel.addView(text(content, 14f))
        if (onOpen != null) {
            panel.isClickable = true
            panel.setOnClickListener { onOpen() }
            panel.contentDescription = "$title. Toque para abrir detalhes."
        }
        root.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(6), dp(12), dp(6))
        })
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page == "home") super.onBackPressed() else { page = "home"; render() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)
        window.decorView.setBackgroundColor(Color.rgb(12, 6, 29))
        window.statusBarColor = Color.rgb(20, 10, 34)
        window.navigationBarColor = Color.rgb(20, 10, 34)
        smoked = prefs.getBoolean("weather_smoked_glass", true)
        render()
        // First visit: ask through Android's own permission dialog. The user may
        // deny it and still choose a city manually from the location options.
        if (!prefs.contains("gothic_weather_location_mode")) {
            weatherLocation.showOptions()
        }
    }

    override fun onStart() {
        super.onStart()
        weatherLocation.start()
        weather.refresh(onChanged = {
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        })
    }

    override fun onStop() {
        weatherLocation.stop()
        super.onStop()
    }

    override fun onDestroy() {
        weatherLocation.close()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        weatherLocation.onPermissionResult(requestCode)
    }

    private fun liveArtwork(): WeatherArtworkCatalog.Art {
        val forecast = weather.current()
        val latitude = prefs.getString("gothic_weather_lat", null)?.toDoubleOrNull()
        val longitude = prefs.getString("gothic_weather_lon", null)?.toDoubleOrNull()
        val daylight = SolarDaylight.isDay(System.currentTimeMillis(), latitude, longitude)
            ?: forecast?.isDay ?: true
        val rainy = forecast?.code == 61 || forecast?.code == 95
        val cold = forecast != null && (forecast.temperature <= 12 || forecast.code == 71)
        val cloudy = forecast?.code == 2 || forecast?.code == 3 || forecast?.code == 45
        val condition = when {
            cold -> 3
            rainy -> 2
            cloudy -> 1
            else -> 0
        }
        val sunStyle = when {
            condition != 0 -> 2
            (forecast?.temperature ?: 0.0) >= 30 -> 0
            else -> 1
        }
        val phase = intArrayOf(1, 2, 3, 0)[prefs.getInt("gothic_moon_phase", 0).coerceIn(0, 3)]
        return WeatherArtworkCatalog.select(daylight, sunStyle, condition, phase, null)
    }

    private fun render() {
        val art = WeatherArtworkCatalog.all.firstOrNull { it.slug == previewSlug } ?: liveArtwork()
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 6, 29))

        }
        // Layer 1: approved static portrait castle art, behind every weather panel.
        // CENTER_CROP fills the screen without distortion; the center castle stays visible.
        root.addView(ImageView(this).apply {
            setImageResource(R.drawable.purple_charm_camada1_castelo)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, -1))

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(28))
            setBackgroundColor(Color.TRANSPARENT)
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val back = text("‹  Voltar", 16f, true).apply {
            setOnClickListener { if (page == "home") finish() else { page = "home"; render() } }
            contentDescription = "Voltar ao Purple Charm"
        }
        top.addView(back)
        top.addView(text(if (page == "home") "✦ Purple Charm • Clima" else when(page) {
            "daily" -> "Previsão para cinco dias"
            "hourly" -> "Previsão horária"
            "alerts" -> "Avisos oficiais • INMET"
            else -> "Condições atuais"
        }, 18f, true),
            LinearLayout.LayoutParams(0, -2, 1f))
        column.addView(top)

        // One compact style switch; no manual weather scenarios in the live forecast.
        // The switch follows the first artwork panel below.

        val current = weather.current()
        val place = prefs.getString("gothic_weather_city", null)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("place")?.takeIf {
            it.isNotBlank() && it != "Local não definido"
        } ?: "Localização não definida"
        val headline = current?.let { "${it.temperature.roundToInt()}°C • ${it.description}" }
            ?: "Previsão indisponível"
        val conditions = "$place\n$headline\n" +
            if (current == null) "Configure a localização no widget para receber dados reais."
            else "Umidade: ${current.humidity?.roundToInt()?.let { "$it%" } ?: "indisponível"}" +
                "   •   Vento: ${current.wind?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "indisponível"}"

        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        val upcoming = weather.upcoming()
        val hourly = upcoming.take(12).joinToString("\n") {
            "${formatter.format(Instant.ofEpochMilli(it.time))}   ${it.temperature.roundToInt()}°C   ${it.description}"
        }.ifEmpty { "Previsão horária indisponível. Configure a localização para consultar o MET Norway." }
        val daily = weather.upcomingDays().groupBy {
            Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate()
        }.entries.take(5).joinToString("\n") { (date, points) ->
            val low = points.minOf { it.temperature }.roundToInt()
            val high = points.maxOf { it.temperature }.roundToInt()
            "${date.dayOfMonth}/${date.monthValue}   ${low}° / ${high}°C   ${points.first().description}"
        }.ifEmpty { "Previsão de cinco dias indisponível. Aguardando dados reais do MET Norway." }
        fun number(value: Double?, suffix: String, multiplier: Double = 1.0): String =
            value?.let { "${(it * multiplier).roundToInt()}$suffix" } ?: "indisponível"
        val nextRain = upcoming.firstOrNull { it.rainProbability != null || it.precipitationMm != null }
        val rain = "Probabilidade: " + number(nextRain?.rainProbability, "%") +
            "\nPrecipitação prevista: " + number(nextRain?.precipitationMm, " mm") +
            "\nReferência: próximo período disponível • MET Norway"
        val windDetails = "Velocidade: " + number(current?.wind, " km/h", 3.6) +
            "\nDireção de origem: " + number(current?.windDirection, "°") +
            "\nRajadas: " + number(current?.gust, " km/h", 3.6) +
            "\nSensação térmica: indisponível na integração atual"
        val atmosphere = "Pressão atmosférica: " + number(current?.pressure, " hPa") +
            "\nPonto de orvalho: " + number(current?.dewPoint, "°C") +
            "\nUmidade relativa: " + number(current?.humidity, "%") +
            "\nVisibilidade: " + number(current?.visibilityMeters, " m")
        val uv = current?.uvClearSky?.let { value ->
            "Índice UV (previsão para céu limpo): ${String.format(Locale.US, "%.1f", value)} • MET Norway"
        } ?: "Índice UV: indisponível nesta previsão"
        val air = uv +
            "\nQualidade do ar (AQI): fonte para o Brasil ainda não integrada" +
            "\nUV previsto para céu limpo não representa medição local."
        val alerts = "Consulta oficial ao INMET ainda não integrada. Não é possível afirmar que não existem alertas."
        fun open(destination: String) { page = destination; render() }
        when (page) {
            "home" -> {
                // Layer 1 is exclusively the backdrop. Original widget PNGs are
                // never embedded inside the Layer 2 glass panels.
                card(column, "Condições atuais • MET Norway  ›", conditions) {
                    open("conditions")
                }
                val styleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                }
                fun styleOption(label: String, selected: Boolean, value: Boolean): TextView =
                    text((if (selected) "✓ " else "○ ") + label, 14f, selected).apply {
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            setColor(if (selected) Color.argb(205, 73, 37, 108)
                                else Color.argb(70, 35, 17, 63))
                            cornerRadius = dp(14).toFloat()
                            setStroke(dp(if (selected) 2 else 1),
                                Color.argb(if (selected) 240 else 125, 202, 150, 246))
                        }
                        isClickable = true
                        isFocusable = true
                        contentDescription = "Selecionar estilo " + label
                        setOnClickListener {
                            if (smoked != value) {
                                smoked = value
                                prefs.edit().putBoolean("weather_smoked_glass", value).apply()
                                render()
                            }
                        }
                    }
                styleRow.addView(styleOption("Fumê", smoked, true),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
                styleRow.addView(styleOption("Vazado", !smoked, false),
                    LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
                column.addView(styleRow, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(dp(12), dp(6), dp(12), dp(6))
                })
                val hourlyPanel = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = glass()
                    setPadding(dp(8), dp(7), dp(8), dp(7))
                }
                val hourlyTitle = text("◷  Previsão horária • MET Norway  ›", 17f, true)
                hourlyPanel.addView(hourlyTitle)
                val hourlyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                if (upcoming.isEmpty()) {
                    hourlyRow.addView(text("Previsão indisponível", 13f))
                } else {
                    upcoming.take(4).forEach { point ->
                        val symbol = when (point.code) {
                            61 -> "☂"
                            95 -> "ϟ"
                            3, 2, 1 -> "☁"
                            else -> "☀"
                        }
                        val hour = formatter.format(Instant.ofEpochMilli(point.time))
                        val cell = text("$hour\n$symbol\n${point.temperature.roundToInt()}°", 14f, true).apply {
                            gravity = Gravity.CENTER
                        }
                        hourlyRow.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                    }
                }
                hourlyPanel.addView(hourlyRow)
                hourlyPanel.setOnClickListener { open("hourly") }
                column.addView(hourlyPanel, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(dp(12), dp(6), dp(12), dp(6))
                })
                card(column, "Previsão para cinco dias  ›", daily) { open("daily") }
                card(column, "Avisos oficiais • INMET  ›", alerts) { open("alerts") }
                card(column, "Índice UV e qualidade do ar", air)
                card(column, "Probabilidade e volume de chuva", rain)
                card(column, "Vento e sensação térmica", windDetails)
                card(column, "Detalhes da atmosfera", atmosphere)
                card(column, "Localização • alterar ou permitir acesso  ›", weatherLocation.summary) { weatherLocation.showOptions() }

            }
            "hourly" -> card(column, "Próximas horas • MET Norway", hourly)
            "daily" -> card(column, "Temperaturas mínimas e máximas previstas • MET Norway", daily)
            "alerts" -> card(column, "Detalhes dos avisos oficiais", alerts)
            else -> card(column, "Condições atuais", conditions)
        }
        scroll.addView(column)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}
