package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
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
    private val readings = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 31f
        includeFontPadding = false
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
        gravity = Gravity.CENTER
        setPadding((8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt(), 0)
    }
    private val hourly = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        maxLines = 1
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val city = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 13f
        gravity = Gravity.CENTER
        maxLines = 1
    }
    private val metrics = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setPadding(4, 4, 4, 4)
        setBackgroundColor(Color.argb(150, 22, 5, 35))
    }
    private val dataArea = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        addView(readings, LinearLayout.LayoutParams(-1, 0, 1.5f))
        addView(city, LinearLayout.LayoutParams(-1, 0, 0.55f))
        addView(metrics, LinearLayout.LayoutParams(-1, 0, 0.9f))
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
        val dataHeight = (height * 0.70f).toInt()
        val params = dataArea.layoutParams as LayoutParams
        if (params.width != dataWidth || params.height != dataHeight ||
            params.gravity != (Gravity.RIGHT or Gravity.TOP)) {
            params.width = dataWidth
            params.height = dataHeight
            params.gravity = Gravity.RIGHT or Gravity.TOP
            params.topMargin = (height * 0.12f).toInt()
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
        city.text = location
        val humidity = lines.getOrNull(2)?.removePrefix("Umid. ") ?: "—"
        val wind = lines.getOrNull(3)?.removePrefix("Vento ") ?: "—"
        val uv = lines.getOrNull(4)?.removePrefix("UV ") ?: "—"
        metrics.text = "Umidade     Vento     UV\\n$humidity     $wind     $uv".replace("\\n", "\n")
        val visible = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
        readings.visibility = visible
        city.visibility = visible
        metrics.visibility = visible
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
