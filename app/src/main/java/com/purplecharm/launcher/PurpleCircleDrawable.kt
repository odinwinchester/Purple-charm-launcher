package com.purplecharm.launcher

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.graphics.PorterDuff
import android.graphics.Bitmap.Config
import kotlin.math.max

/**
 * Circular launcher icon drawable with a simple icon-swap animation.
 *
 * Adapted from OpenLauncher CircleDrawable.java (Apache License 2.0).
 * Original source:
 * https://github.com/OpenLauncherTeam/openlauncher/blob/master/app/src/main/java/com/benny/openlauncher/viewutil/CircleDrawable.java
 *
 * Changes: Kotlin port, no OpenLauncher Tool dependency, independent bitmap conversion,
 * bounds-aware rendering and corrected animation termination.
 */
class PurpleCircleDrawable(
    icon: Drawable,
    private val iconTint: Int,
    backgroundColor: Int,
    backgroundAlpha: Int = 220,
    private val paddingPx: Int = 6
) : Drawable() {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        alpha = backgroundAlpha.coerceIn(0, 255)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var currentIcon: Bitmap = bitmapOf(icon)
    private var oldIcon: Bitmap? = null
    private var progress = 1f

    private fun bitmapOf(source: Drawable): Bitmap {
        val w = max(1, source.intrinsicWidth)
        val h = max(1, source.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(w, h, Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val copy = source.constantState?.newDrawable()?.mutate() ?: source.mutate()
        copy.setColorFilter(iconTint, PorterDuff.Mode.SRC_ATOP)
        copy.setBounds(0, 0, w, h)
        copy.draw(canvas)
        return bitmap
    }

    fun setIcon(icon: Drawable) {
        oldIcon = currentIcon
        currentIcon = bitmapOf(icon)
        progress = 0f
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = minOf(b.width(), b.height()) / 2f
        canvas.drawCircle(cx, cy, radius, bgPaint)

        val previous = oldIcon
        if (previous != null && progress < 1f) {
            drawIcon(canvas, previous, cx, cy, (1f - progress).coerceAtLeast(0.01f))
            drawIcon(canvas, currentIcon, cx, cy, progress.coerceAtLeast(0.01f))
            progress = (progress + 0.12f).coerceAtMost(1f)
            if (progress >= 1f) oldIcon = null
            invalidateSelf()
        } else {
            drawIcon(canvas, currentIcon, cx, cy, 1f)
        }
    }

    private fun drawIcon(canvas: Canvas, bitmap: Bitmap, cx: Float, cy: Float, scale: Float) {
        val side = (minOf(bounds.width(), bounds.height()) - 2 * paddingPx).coerceAtLeast(1).toFloat()
        val fit = minOf(side / bitmap.width, side / bitmap.height) * scale
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(fit, fit)
        canvas.drawBitmap(bitmap, -bitmap.width / 2f, -bitmap.height / 2f, iconPaint)
        canvas.restore()
    }

    override fun getIntrinsicWidth(): Int = currentIcon.width + paddingPx * 2
    override fun getIntrinsicHeight(): Int = currentIcon.height + paddingPx * 2
    override fun setAlpha(alpha: Int) { iconPaint.alpha = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { iconPaint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Android Drawable opacity API")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
