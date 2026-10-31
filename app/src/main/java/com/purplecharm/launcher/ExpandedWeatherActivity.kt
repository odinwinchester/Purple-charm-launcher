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
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    // Approved 95% transparency: alpha 13/255, with a visible violet outline.
    private fun glass(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.TRANSPARENT)
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
        window.statusBarColor = Color.rgb(20, 10, 34)
        window.navigationBarColor = Color.rgb(20, 10, 34)
        render()
        // First visit: ask through Android's own permission dialog. The user may
        // deny it and still choose a city manually from the location options.
        if (!prefs.contains("gothic_weather_location_mode")) {
            weatherLocation.requestAutomaticLocation()
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

    private fun render() {
        val art = WeatherArtworkCatalog.all.firstOrNull {
            it.slug == intent.getStringExtra("art_slug")
        } ?: WeatherArtworkCatalog.all.first()
        val root = FrameLayout(this).apply {
            background = WeatherGothicBackdrop(art.kind == WeatherArtworkCatalog.Kind.SOLAR || (art.kind == WeatherArtworkCatalog.Kind.ECLIPSE && art.slug.contains("solar")))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(28))
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

        // Hero image is in the document flow and cannot overlap forecast cards.
        val hero = ImageView(this).apply {
            setImageDrawable(graphics.load(
                art.resource, resources.displayMetrics.widthPixels,
                (resources.displayMetrics.heightPixels * 0.45f).roundToInt()
            ))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Arte original: ${art.label}"
        }
        if (page == "home") column.addView(hero, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(4), dp(12), dp(4))
        })

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
        val daily = weather.daily().entries.joinToString("\n") { (date, points) ->
            val low = points.minOf { it.temperature }.roundToInt()
            val high = points.maxOf { it.temperature }.roundToInt()
            "${date.dayOfMonth}/${date.monthValue}   ${low}° / ${high}°C   ${points.first().description}"
        }.ifEmpty { "Previsão de cinco dias indisponível. Aguardando dados reais do MET Norway." }
        val alerts = "Consulta oficial ao INMET ainda não integrada. Não é possível afirmar que não existem alertas."
        fun open(destination: String) { page = destination; render() }
        when (page) {
            "home" -> {
                card(column, "Localização • alterar ou permitir acesso  ›", weatherLocation.summary) { weatherLocation.showOptions() }
                card(column, "Condições atuais", conditions) { open("conditions") }
                card(column, "Previsão horária • MET Norway  ›", hourly.lineSequence().take(4).joinToString("\n")) { open("hourly") }
                card(column, "Previsão para cinco dias  ›", daily) { open("daily") }
                card(column, "Avisos oficiais • INMET  ›", alerts) { open("alerts") }

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
