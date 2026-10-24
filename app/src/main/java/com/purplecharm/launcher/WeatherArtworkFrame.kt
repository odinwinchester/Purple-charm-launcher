package com.purplecharm.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.roundToInt

/** One external border; no overlay, clipping, tint, shadow or painted background. */
internal class WeatherArtworkFrame(context: Context, image: ImageView, size: Int) : FrameLayout(context) {
    private val border = resources.displayMetrics.density.roundToInt().coerceAtLeast(1)
    private val preferredWidth = (intArrayOf(240, 320, 420)[size.coerceIn(0, 2)] *
        resources.displayMetrics.density).roundToInt()
    var resizedWidth: Int? = null
    private val sourceWidth = image.drawable.intrinsicWidth
    private val sourceHeight = image.drawable.intrinsicHeight
    init {
        setPadding(border, border, border, border)
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            setStroke(border, Color.argb(85, 153, 95, 205))
        }
        clipChildren = true
        clipToPadding = true
        addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED)
            preferredWidth else MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED)
            Int.MAX_VALUE else MeasureSpec.getSize(heightMeasureSpec)
        val target = WeatherGeometry.fitWithin(available, maxHeight, resizedWidth ?: preferredWidth, sourceWidth, sourceHeight, border)
        super.onMeasure(MeasureSpec.makeMeasureSpec(target.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(target.height, MeasureSpec.EXACTLY))
    }
}
