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
internal class WeatherHomeLayers(context: Context) : FrameLayout(context) {
    private val art = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = true
    }
    private val effects = FrameLayout(context).apply {
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val readings = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 12f
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
        gravity = Gravity.CENTER
    }
    private val hourly = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setBackgroundColor(Color.argb(165, 16, 7, 30))
    }
    private val bottom = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.BOTTOM
        addView(readings, LinearLayout.LayoutParams(-1, -2))
        addView(hourly, LinearLayout.LayoutParams(-1, -2))
    }

    init {
        clipChildren = true
        addView(art, LayoutParams(-1, -1))
        addView(effects, LayoutParams(-1, -1))
        addView(bottom, LayoutParams(-1, -1))
        effects.visibility = View.GONE
        readings.visibility = View.GONE
        hourly.visibility = View.GONE
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

    /** Only enable once independent effect assets and collision rules are validated. */
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
