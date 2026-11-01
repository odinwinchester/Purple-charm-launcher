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
        setColor(if (smoked) Color.argb(170, 52, 20, 80) else Color.TRANSPARENT)
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
            background = WeatherGothicBackdrop(art.kind == WeatherArtworkCatalog.Kind.SOLAR || (art.kind == WeatherArtworkCatalog.Kind.ECLIPSE && art.slug.contains("solar")))
        }
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

        if (page == "home") {
            val controls = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(4), dp(12), dp(4))
            }
            fun chip(label: String, action: () -> Unit): TextView = text(label, 12f, true).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.argb(185, 68, 24, 104))
                    cornerRadius = dp(22).toFloat()
                    setStroke(dp(1), Color.rgb(207, 137, 255))
                }
                setOnClickListener { action() }
                setPadding(dp(9), dp(10), dp(9), dp(10))
            }
            fun strip(vararg entries: Pair<String, () -> Unit>) {
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                }
                entries.forEach { (label, action) ->
                    row.addView(chip(label, action), LinearLayout.LayoutParams(0, -2, 1f).apply {
                        setMargins(dp(3), dp(3), dp(3), dp(3))
                    })
                }
                controls.addView(row)
            }
            fun preview(slug: String?) { previewSlug = slug; render() }
            strip(
                "☀ Dia" to { preview("dia_limpo") },
                "☾ Noite" to { preview("lua_03_lua_cheia_noite_limpa") },
                "☂ Chuva" to { preview("sol_dia_chuvoso") }
            )
            strip(
                "ϟ Tempestade" to { preview("sol_dia_chuvoso") },
                "↻ Automático" to { preview(null) },
                "◈ Estilo" to {
                    smoked = !smoked
                    prefs.edit().putBoolean("weather_smoked_glass", smoked).apply()
                    render()
                }
            )
            strip(
                (if (smoked) "◈ Violet Glass • fumê" else "◇ Crystal Clear • vazado") to {
                    smoked = !smoked
                    prefs.edit().putBoolean("weather_smoked_glass", smoked).apply()
                    render()
                }
            )
            column.addView(controls)
        }

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
        val alerts = "Consulta oficial ao INMET ainda não integrada. Não é possível afirmar que não existem alertas."
        fun open(destination: String) { page = destination; render() }
        when (page) {
            "home" -> {
                val feature = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = glass()
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val featureText = text("CONDIÇÕES ATUAIS\n" +
                    (current?.let { "${it.temperature.roundToInt()}°C" } ?: "—°C") +
                    "\n$place\n" +
                    (current?.description ?: "Aguardando previsão") +
                    "\nUmidade: " + (current?.humidity?.roundToInt()?.let { "$it%" } ?: "—") +
                    "  •  Vento: " + (current?.wind?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "—"), 15f)
                feature.addView(featureText, LinearLayout.LayoutParams(0, -2, 3f))
                val artImage = ImageView(this).apply {
                    setImageDrawable(graphics.load(art.resource, dp(120), dp(120)))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = "Arte meteorológica: ${art.label}"
                }
                feature.addView(artImage, LinearLayout.LayoutParams(0, dp(125), 2f))
                feature.setOnClickListener { open("conditions") }
                column.addView(feature, LinearLayout.LayoutParams(-1, -2).apply {
                    setMargins(dp(12), dp(6), dp(12), dp(6))
                })
                card(column, "Previsão horária • MET Norway  ›", hourly.lineSequence().take(4).joinToString("\n")) { open("hourly") }
                card(column, "Previsão para cinco dias  ›", daily) { open("daily") }
                card(column, "Avisos oficiais • INMET  ›", alerts) { open("alerts") }
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
        // Existing MET client refreshes asynchronously; rebuild only after completion.
        weather.refresh(onChanged = {
            runOnUiThread { if (!isFinishing && !isDestroyed) render() }
        })
    }
}
