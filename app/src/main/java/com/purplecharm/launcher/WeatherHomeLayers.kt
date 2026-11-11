package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Approved HOME widget composition (not the expanded weather screen).
 *
 * 1. Celestial art: a single sun or moon image.
 * 2. Effects: optional overlays, never a second baked-in celestial image.
 * 3. Live readings: city, temperature, humidity, wind, UV.
 * 4. Bottom strip: hourly forecast.
 *
 * The original PNGs are kept in the repository during migration, but must
 * not be drawn together with independent weather effects.
 */
internal class WeatherHomeLayers(context: Context, private val art: ImageView) : FrameLayout(context) {
    private val effects = FrameLayout(context).apply {
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()
    private fun resizeSp(view: TextView, sp: Float) {
        val px = sp * resources.displayMetrics.scaledDensity
        if (kotlin.math.abs(view.textSize - px) > 0.5f) view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }
    private val readings = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 42f)
        includeFontPadding = false
        gravity = Gravity.CENTER
        maxLines = 1
        setShadowLayer(dp(3f).toFloat(), 0f, dp(1f).toFloat(), Color.rgb(91, 12, 139))
    }
    private val hourly = TextView(context).apply {
        setTextColor(Color.rgb(238, 201, 255))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        maxLines = 1
        setShadowLayer(dp(2f).toFloat(), 0f, 0f, Color.BLACK)
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val city = TextView(context).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = null
        includeFontPadding = false
        setShadowLayer(dp(2f).toFloat(), 0f, 0f, Color.BLACK)
    }
    private val condition = TextView(context).apply {
        setTextColor(Color.rgb(241, 204, 255))
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
        setShadowLayer(dp(2f).toFloat(), 0f, 0f, Color.BLACK)
    }
    private val lunarPhase = TextView(context).apply {
        setTextColor(Color.rgb(218, 165, 255))
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        includeFontPadding = false
        setShadowLayer(dp(2f).toFloat(), 0f, 0f, Color.BLACK)
    }
    private fun metricColumn(title: String): Pair<LinearLayout, TextView> {
        val value = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
        val label = TextView(context).apply {
            text = title
            setTextColor(Color.rgb(232, 172, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(label, LinearLayout.LayoutParams(-1, -2))
            addView(value, LinearLayout.LayoutParams(-1, -2))
        }
        return column to value
    }
    private val humidityColumn = metricColumn("Umidade")
    private val windColumn = metricColumn("Vento")
    private val uvColumn = metricColumn("UV")
    private val metrics = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        // Sem placa de fundo: preserva a arte original sob os indicadores.
        background = null
        setPadding(0, 0, 0, 0)
        addView(humidityColumn.first, LinearLayout.LayoutParams(0, -1, 1f))
        addView(windColumn.first, LinearLayout.LayoutParams(0, -1, 1.1f))
        addView(uvColumn.first, LinearLayout.LayoutParams(0, -1, 0.75f))
    }
    private val dataArea = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(readings, LinearLayout.LayoutParams(-1, 0, 1.35f))
        addView(city, LinearLayout.LayoutParams(-1, 0, 0.62f))
        addView(condition, LinearLayout.LayoutParams(-1, 0, 0.42f))
        addView(lunarPhase, LinearLayout.LayoutParams(-1, 0, 0.42f))
        addView(metrics, LinearLayout.LayoutParams(-1, 0, 0.60f))
    }
    init {
        clipChildren = true
        addView(art, LayoutParams(-1, -1))
        addView(effects, LayoutParams(-1, -1))
        // Approved composition: artwork left, live data right, hourly strip inside bottom.
        addView(dataArea, LayoutParams(-1, -1))
        addView(hourly, LayoutParams(-1, -2, Gravity.BOTTOM))
        effects.visibility = View.GONE
        readings.visibility = View.GONE
        hourly.visibility = View.GONE
    }
    // Position the data overlay during measurement, not only onSizeChanged.
    // Recreated home pages can be measured with the same size, so onSizeChanged
    // is not guaranteed to run before the first visible frame.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        // Normalized safe zones track the ornamental frame as the art scales.
        // Keep readings inside the right-hand panel and above the hourly band.
        val dataWidth = (width * 0.49f).toInt()
        val dataHeight = (height * 0.63f).toInt()
        val widthDp = width / resources.displayMetrics.density
        // Scale typography with the actual widget, not the phone manufacturer.
        val scale = WeatherWidgetTypography.scale(widthDp)
        resizeSp(readings, 42f * scale)
        // Ajusta o texto à largura real disponível, preservando o nome completo.
        val cityMaxPx = 14f * scale * resources.displayMetrics.scaledDensity
        val cityMinPx = 8f * scale * resources.displayMetrics.scaledDensity
        val cityAvailablePx = (dataWidth - dp(4f)).coerceAtLeast(1).toFloat()
        val label = city.text.toString()
        val measuredAtMax = city.paint.apply { textSize = cityMaxPx }.measureText(label)
        val fittedPx = if (measuredAtMax > cityAvailablePx && measuredAtMax > 0f)
            (cityMaxPx * cityAvailablePx / measuredAtMax).coerceAtLeast(cityMinPx)
            else cityMaxPx
        if (kotlin.math.abs(city.textSize - fittedPx) > 0.5f)
            city.setTextSize(TypedValue.COMPLEX_UNIT_PX, fittedPx)
        resizeSp(condition, 12f * scale)
        resizeSp(lunarPhase, 11f * scale)
        resizeSp(hourly, 12f * scale)
        listOf(humidityColumn, windColumn, uvColumn).forEach { (column, value) ->
            resizeSp(column.getChildAt(0) as TextView, 10f * scale)
            resizeSp(value, 12f * scale)
        }
        readings.paint.shader = LinearGradient(0f, 0f, 0f, readings.textSize * 1.3f,
            intArrayOf(Color.WHITE, Color.rgb(252, 186, 255), Color.rgb(177, 39, 238)),
            floatArrayOf(0f, 0.43f, 1f), Shader.TileMode.CLAMP)
        val params = dataArea.layoutParams as LayoutParams
        if (params.width != dataWidth || params.height != dataHeight ||
            params.gravity != (Gravity.RIGHT or Gravity.TOP)) {
            params.width = dataWidth
            params.height = dataHeight
            params.gravity = Gravity.RIGHT or Gravity.TOP
            params.topMargin = (height * 0.13f).toInt()
            params.rightMargin = (width * 0.035f).toInt()
            dataArea.layoutParams = params
        }
        val strip = hourly.layoutParams as LayoutParams
        val stripWidth = (width * 0.87f).toInt()
        val bottomInset = (height * 0.105f).toInt()
        if (strip.width != stripWidth || strip.bottomMargin != bottomInset || strip.gravity != (Gravity.BOTTOM or Gravity.RIGHT)) {
            strip.width = stripWidth
            strip.bottomMargin = bottomInset
            strip.gravity = Gravity.BOTTOM or Gravity.RIGHT
            hourly.layoutParams = strip
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun updateLiveData(readingsValue: String?, hourlyValue: String?) {
        setReadings(readingsValue)
        setHourlyForecast(hourlyValue)
    }

    fun setArt(resourceId: Int, label: String) {
        art.setImageResource(resourceId)
        art.contentDescription = label
    }

    fun setReadings(value: String?) {
        val lines = value.orEmpty().lines()
        val temperature = lines.getOrNull(0).orEmpty()
        val location = lines.getOrNull(1).orEmpty()
        readings.text = temperature
        city.text = if (location.isBlank()) "" else "⌖ $location"
        val humidity = lines.getOrNull(2)?.removePrefix("Umid. ") ?: "—"
        val wind = lines.getOrNull(3)?.removePrefix("Vento ") ?: "—"
        val uv = lines.getOrNull(4)?.removePrefix("UV ") ?: "—"
        val conditionValue = lines.getOrNull(5).orEmpty().trim()
        val lunarValue = lines.getOrNull(6).orEmpty().trim()
        condition.text = conditionValue
        lunarPhase.text = lunarValue
        condition.visibility = if (conditionValue.isEmpty()) View.GONE else View.VISIBLE
        lunarPhase.visibility = if (lunarValue.isEmpty()) View.GONE else View.VISIBLE
        humidityColumn.second.text = humidity
        windColumn.second.text = wind
        uvColumn.second.text = if (uv == "—" || uv.contains("—")) "—" else uv
        val visible = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        readings.visibility = visible
        city.visibility = visible
        metrics.visibility = visible
        if (visible == View.GONE) {
            condition.visibility = View.GONE
            lunarPhase.visibility = View.GONE
        }
    }

    fun setHourlyForecast(value: String?) {
        hourly.text = value.orEmpty().replace("  ", "     ")
        hourly.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    /** Effects are independent images; never place a second celestial body here. */
    fun setEffectResource(resourceId: Int?) {
        replaceEffect(resourceId?.let { id -> ImageView(context).apply {
            setImageResource(id)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        } })
        setEffectsEnabled(resourceId != null)
    }

    /** Only display a separately selected overlay. */
    fun setEffectsEnabled(enabled: Boolean) {
        effects.visibility = if (enabled) View.VISIBLE else View.GONE
    }

    fun replaceEffect(view: View?) {
        effects.removeAllViews()
        if (view != null) {
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            effects.addView(view, LayoutParams(-1, -1))
        }
    }
}

/** Pure responsive sizing policy, shared by layouts and JVM regression tests. */
internal object WeatherWidgetTypography {
    fun scale(widgetWidthDp: Float): Float = (widgetWidthDp / 340f).coerceIn(0.60f, 1.10f)
}
