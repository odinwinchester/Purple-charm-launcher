package com.purplecharm.launcher

import android.app.Activity
import android.graphics.Color
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
 * Lightweight expanded forecast. Original PNGs are loaded unchanged.
 * The violet glass uses 5% fill alpha (95% transparency), without blur.
 */
class ExpandedWeatherActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("purple_charm_favorites", MODE_PRIVATE) }
    private val weather by lazy { MetWeatherClient(prefs) }
    private val violet = Color.rgb(143, 65, 181)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun glass(): GradientDrawable = GradientDrawable().apply {
        setColor(Color.argb(13, 143, 65, 181))
        cornerRadius = dp(18).toFloat()
        setStroke(dp(1), Color.argb(190, 193, 146, 239))
    }
    private fun text(value: String, size: Float, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(Color.WHITE)
            setShadowLayer(dp(3).toFloat(), 0f, dp(1).toFloat(), Color.BLACK)
            if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
    private fun card(root: LinearLayout, title: String, content: String) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass()
            setPadding(dp(4), dp(3), dp(4), dp(3))
        }
        panel.addView(text(title, 16f, true))
        panel.addView(text(content, 14f))
        root.addView(panel, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(14), dp(7), dp(14), dp(7))
        })
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(30, 13, 47)
        window.navigationBarColor = Color.rgb(30, 13, 47)
        val art = WeatherArtworkCatalog.all.firstOrNull {
            it.slug == intent.getStringExtra("art_slug")
        } ?: WeatherArtworkCatalog.all.first()
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(20, 10, 34)) }
        val background = ImageView(this).apply {
            setImageResource(art.resource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = art.label
            alpha = 1f
        }
        root.addView(background, FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18), 0, dp(26))
        }
        val current = weather.current()
        val place = intent.getStringExtra("place")?.takeIf { it.isNotBlank() } ?: "Localização selecionada"
        val headline = current?.let { "${it.temperature.roundToInt()}°C • ${it.description}" }
            ?: "Previsão indisponível"
        card(column, "✦ Purple Charm • Clima", "$place\n$headline\nArte: ${art.label}")
        val details = if (current == null) "Escolha uma cidade no widget ou aguarde a atualização do MET Norway."
            else buildString {
                append("Umidade: ${current.humidity?.roundToInt()?.let { "$it%" } ?: "indisponível"}")
                append("\nVento: ${current.wind?.let { "${(it * 3.6).roundToInt()} km/h" } ?: "indisponível"}")
                append("\nPressão e índice UV: indisponíveis nesta versão")
            }
        card(column, "Condições atuais", details)
        val formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
        val upcoming = weather.upcoming()
        val hourly = upcoming.take(12).joinToString("\n") {
            "${formatter.format(Instant.ofEpochMilli(it.time))}  •  ${it.temperature.roundToInt()}°C  •  ${it.description}"
        }.ifEmpty { "Previsão horária indisponível" }
        card(column, "Próximas horas • MET Norway", hourly)
        card(column, "Próximos cinco dias", "Em preparação: os dados diários ainda não estão integrados. Não exibir valores fictícios.")
        card(column, "Avisos oficiais • INMET", "Integração em preparação • alertas indisponíveis")
        card(column, "Fonte e observações",
            "MET Norway • dados previstos, não medições locais.\n" +
            "Os números impressos na arte original são ilustrativos.\n" +
            "Arte preservada integralmente • vidro violeta com 95% de transparência.")
        scroll.addView(column)
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }
}
