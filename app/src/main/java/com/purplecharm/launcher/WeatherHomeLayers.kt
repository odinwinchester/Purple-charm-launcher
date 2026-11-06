package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
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
        textSize = 12f
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
        gravity = Gravity.CENTER_VERTICAL or Gravity.START
        setPadding((8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt(), 0)
    }
    private val hourly = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setBackgroundColor(Color.TRANSPARENT)
    }
    private val dataArea = FrameLayout(context).apply {
        addView(readings, LayoutParams(-1, -1))
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
    override fun onSizeChanged(width: Int, height: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(width, height, oldw, oldh)
        val dataWidth = (width * 0.53f).toInt()
        val dataHeight = (height * 0.73f).toInt()
        dataArea.layoutParams = (dataArea.layoutParams as LayoutParams).apply {
            this.width = dataWidth
            this.height = dataHeight
            gravity = Gravity.RIGHT or Gravity.TOP
        }
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
        readings.text = value.orEmpty()
        readings.visibility = if (value.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    fun setHourlyForecast(value: String?) {
        hourly.text = value.orEmpty()
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
