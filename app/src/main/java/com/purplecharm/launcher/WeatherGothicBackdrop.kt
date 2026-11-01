package com.purplecharm.launcher

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.PixelFormat
import kotlin.math.min

/**
 * Shared lightweight decorative continuation for all 26 weather artworks.
 * Drawn behind transparent weather panels; never modifies the original PNGs.
 * Day/night palettes are selected from the existing artwork catalog.
 */
internal class WeatherGothicBackdrop(private val isDay: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        paint.shader = LinearGradient(
            0f, 0f, w * 0.3f, h,
            if (isDay) intArrayOf(Color.rgb(35, 13, 64), Color.rgb(62, 26, 97), Color.rgb(18, 9, 43))
            else intArrayOf(Color.rgb(13, 7, 38), Color.rgb(40, 12, 75), Color.rgb(8, 6, 30)),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        // Small repeatable stars and filigree; deterministic, no allocations of bitmap resources.
        for (i in 0 until 72) {
            val x = (((i * 97 + 41) % 101) / 101f) * w
            val y = (((i * 53 + 17) % 103) / 103f) * h
            paint.color = if (i % 3 == 0) Color.argb(135, 217, 168, 255)
                else Color.argb(72, 185, 110, 240)
            canvas.drawCircle(x, y, if (i % 8 == 0) 2.1f else 0.9f, paint)
        }

        val mid = w / 2f
        val scale = min(w, h) / 390f
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f * scale
        paint.color = Color.argb(65, 188, 107, 240)
        // Gothic arches continue down the background, outside the original artwork.
        for (i in 0..4) {
            val cy = h * (0.28f + i * 0.19f)
            val span = w * (0.34f + (i % 2) * 0.08f)
            val arch = Path().apply {
                moveTo(mid - span, cy + 90f * scale)
                cubicTo(mid - span, cy - 22f * scale, mid - span * 0.3f, cy - 55f * scale, mid, cy - 115f * scale)
                cubicTo(mid + span * 0.3f, cy - 55f * scale, mid + span, cy - 22f * scale, mid + span, cy + 90f * scale)
            }
            canvas.drawPath(arch, paint)
        }
        // Static gothic skyline: pointed towers and a distant waterfall.
        // Vector artwork only; no animation, no modification of the 26 PNGs.
        paint.shader = null
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(150, 13, 7, 31)
        val groundY = h * 0.79f
        canvas.drawRect(0f, groundY, w, h, paint)
        fun tower(cx: Float, top: Float, width: Float, bottom: Float) {
            val roof = Path().apply {
                moveTo(cx - width * 0.64f, top + width * 0.85f)
                lineTo(cx, top - width * 0.55f)
                lineTo(cx + width * 0.64f, top + width * 0.85f)
                close()
            }
            canvas.drawPath(roof, paint)
            canvas.drawRect(cx - width * 0.42f, top + width * 0.7f,
                cx + width * 0.42f, bottom, paint)
        }
        tower(w * 0.18f, h * 0.33f, w * 0.14f, groundY)
        tower(w * 0.36f, h * 0.24f, w * 0.18f, groundY)
        tower(w * 0.57f, h * 0.30f, w * 0.16f, groundY)
        tower(w * 0.79f, h * 0.38f, w * 0.13f, groundY)
        paint.color = Color.argb(105, 182, 119, 238)
        for (i in 0..7) {
            val y = h * (0.58f + i * 0.036f)
            val drift = kotlin.math.sin(i * 0.8).toFloat() * w * 0.025f
            canvas.drawRoundRect(w * 0.63f + drift, y, w * 0.77f + drift,
                y + h * 0.023f, w * 0.014f, w * 0.014f, paint)
        }
        paint.style = Paint.Style.FILL
    }
    override fun setAlpha(alpha: Int) { /* Background is intentionally opaque. */ }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
