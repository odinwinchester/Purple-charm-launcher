package com.purplecharm.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt

/** Shared presentation only: original assets and downloaded bytes are never changed. */
internal object IconVisualNormalizer {
    private const val SIZE = 256
    private const val TARGET = 0.94f

    fun normalize(resources: Resources, drawable: Drawable): Drawable {
        val source = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: SIZE
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: SIZE
            val scale = SIZE.toFloat() / maxOf(w, h)
            val raster = Bitmap.createBitmap(
                (w * scale).roundToInt().coerceAtLeast(1),
                (h * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val oldBounds = Rect(drawable.bounds)
            try {
                drawable.setBounds(0, 0, raster.width, raster.height)
                drawable.draw(Canvas(raster))
            } finally {
                drawable.bounds = oldBounds
            }
            raster
        }
        // A software raster also supports hardware-backed BitmapDrawables.
        val readable = if (android.os.Build.VERSION.SDK_INT >= 26 &&
            source.config == Bitmap.Config.HARDWARE) {
            source.copy(Bitmap.Config.ARGB_8888, false) ?: return drawable
        } else source
        val w = readable.width
        val h = readable.height
        val row = IntArray(w)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        // Include even faint alpha so gothic glow and edge details are not clipped.
        for (y in 0 until h) {
            readable.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) != 0) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        if (right < left || bottom < top) return drawable
        val visible = Rect(left, top, right + 1, bottom + 1)
        val scale = SIZE * TARGET / maxOf(visible.width(), visible.height())
        val dw = visible.width() * scale
        val dh = visible.height() * scale
        val result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val dest = RectF((SIZE - dw) / 2f, (SIZE - dh) / 2f,
            (SIZE + dw) / 2f, (SIZE + dh) / 2f)
        Canvas(result).drawBitmap(readable, visible, dest,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return BitmapDrawable(resources, result)
    }
}
