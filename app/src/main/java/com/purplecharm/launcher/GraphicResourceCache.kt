package com.purplecharm.launcher

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache

/** Bounded bitmap ownership. Eviction never recycles a bitmap still displayed by a view. */
internal class GraphicResourceCache(private val resources: Resources, private val limit: Int = 16 * 1024 * 1024) {
    private val cache = object : LruCache<String, Bitmap>(limit) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    fun load(id: Int, width: Int = 0, height: Int = 0): BitmapDrawable {
        val key = "$id:$width:$height"
        val bitmap = cache.get(key) ?: run {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resources.openRawResource(id).use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            if (width > 0 && height > 0) while (bounds.outWidth / (sample * 2) >= width && bounds.outHeight / (sample * 2) >= height) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample; inScaled = false }
            val decoded = resources.openRawResource(id).use { BitmapFactory.decodeStream(it, null, options) }
                ?: error("Cannot decode drawable $id")
            if (decoded.allocationByteCount <= limit) cache.put(key, decoded)
            decoded
        }
        // Each view owns a drawable; only the immutable pixel storage is shared.
        return BitmapDrawable(resources, bitmap).apply { setTargetDensity(bitmap.density) }
    }
    fun clear() = cache.evictAll()
    internal fun bytes() = cache.size()
}
