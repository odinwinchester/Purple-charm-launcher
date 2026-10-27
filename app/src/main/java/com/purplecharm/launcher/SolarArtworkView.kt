package com.purplecharm.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import kotlin.math.cos
import kotlin.math.sin

/** Non-destructive light overlays. Does not redraw faces or transform the original PNG. */
internal class SolarArtworkView(context: Context, private val style: Int) : ImageView(context) {
    private val light = Paint(Paint.ANTI_ALIAS_FLAG)
    private val started = SystemClock.uptimeMillis()
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (style < 0 || !android.animation.ValueAnimator.areAnimatorsEnabled() || !isShown || windowVisibility != View.VISIBLE) return
        val source = drawable ?: return
        val scale = minOf((width - paddingLeft - paddingRight).toFloat() / source.intrinsicWidth,
            (height - paddingTop - paddingBottom).toFloat() / source.intrinsicHeight)
        val w = source.intrinsicWidth * scale
        val h = source.intrinsicHeight * scale
        val left = (width - w) / 2f
        val top = (height - h) / 2f
        val t = (SystemClock.uptimeMillis() - started) / 1000.0
        // Keep light on the illustrated left portion, clear of sample weather lettering.
        val cx = left + w * 0.24f
        val cy = top + h * 0.45f
        canvas.save()
        canvas.clipRect(left, top, left + w * 0.45f, top + h * 0.86f)
        for (i in 0..7) {
            val angle = i * Math.PI / 4 - t * (if (style == 1) 0.12 else 0.04)
            val pulse = ((sin(t * 1.5 + i) + 1) / 2).toFloat()
            light.color = Color.argb((pulse * 90).toInt(), if (style == 1) 220 else 190, 110, 255)
            val radius = w * (if (style == 3) 0.17f else 0.14f)
            val x = cx + cos(angle).toFloat() * radius
            val y = cy + sin(angle).toFloat() * radius * 0.8f
            if (style == 2) {
                canvas.drawOval(x - w * 0.05f, y - h * 0.012f, x + w * 0.05f, y + h * 0.012f, light)
            } else {
                canvas.drawCircle(x, y, w * 0.003f * (1 + pulse), light)
            }
        }
        canvas.restore()
        postInvalidateDelayed(50L)
    }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && style >= 0) invalidate()
    }
}
