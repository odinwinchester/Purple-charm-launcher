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
        textSize = 12f
        includeFontPadding = false
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
        gravity = Gravity.TOP or Gravity.START
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
    // Position the data overlay during measurement, not only onSizeChanged.
    // Recreated home pages can be measured with the same size, so onSizeChanged
    // is not guaranteed to run before the first visible frame.
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        // Normalized safe zones track the ornamental frame as the art scales.
        // Keep readings inside the right-hand panel and above the hourly band.
        val dataWidth = (width * 0.43f).toInt()
        val dataHeight = (height * 0.58f).toInt()
        val params = dataArea.layoutParams as LayoutParams
        if (params.width != dataWidth || params.height != dataHeight ||
            params.gravity != (Gravity.RIGHT or Gravity.CENTER_VERTICAL)) {
            params.width = dataWidth
            params.height = dataHeight
            params.gravity = Gravity.RIGHT or Gravity.CENTER_VERTICAL
            dataArea.layoutParams = params
        }
        val strip = hourly.layoutParams as LayoutParams
        val stripWidth = (width * 0.72f).toInt()
        val bottomInset = (height * 0.045f).toInt()
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
        val display = value.orEmpty()
        val styled = SpannableString(display)
        // The first line contains the live temperature; subsequent lines are
        // the city and secondary readings. Preserve fallback text unchanged.
        val firstBreak = display.indexOf('\n')
        if (firstBreak > 0 && display.substring(0, firstBreak).contains('°')) {
            styled.setSpan(RelativeSizeSpan(2.0f), 0, firstBreak, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            styled.setSpan(ForegroundColorSpan(Color.rgb(211, 132, 255)), 0, firstBreak, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            styled.setSpan(StyleSpan(Typeface.BOLD), 0, firstBreak, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val nextBreak = display.indexOf('\n', firstBreak + 1)
            val cityEnd = if (nextBreak < 0) display.length else nextBreak
            if (cityEnd > firstBreak + 1) {
                styled.setSpan(StyleSpan(Typeface.BOLD), firstBreak + 1, cityEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        readings.text = styled
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
