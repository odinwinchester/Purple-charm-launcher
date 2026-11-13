package com.purplecharm.launcher

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconFrameNormalizationTest {
    @Test fun faintGlowDoesNotShrinkVisibleFrame() {
        val resources = RuntimeEnvironment.getApplication().resources
        val source = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        for (y in 80 until 176) for (x in 80 until 176) source.setPixel(x, y, Color.MAGENTA)
        source.setPixel(0, 0, Color.argb(2, 255, 0, 255))
        source.setPixel(255, 255, Color.argb(2, 255, 0, 255))
        val output = (IconVisualNormalizer.normalize(resources, BitmapDrawable(resources, source)) as BitmapDrawable).bitmap
        assertEquals(256, output.width)
        assertTrue(Color.alpha(output.getPixel(15, 128)) >= 64)
        assertTrue(Color.alpha(output.getPixel(240, 128)) >= 64)
        assertEquals(0, Color.alpha(output.getPixel(0, 128)))
        assertEquals(Color.MAGENTA, source.getPixel(80, 80))
    }
    @Test fun transparentGapAndAspectRatioArePreserved() {
        val resources = RuntimeEnvironment.getApplication().resources
        val source = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        for (y in 20 until 80) for (x in 20 until 180) {
            if (x < 30 || x >= 170 || y < 30 || y >= 70) source.setPixel(x, y, Color.MAGENTA)
        }
        val output = (IconVisualNormalizer.normalize(resources, BitmapDrawable(resources, source)) as BitmapDrawable).bitmap
        assertEquals(0, Color.alpha(output.getPixel(128, 128)))
        assertTrue(Color.alpha(output.getPixel(15, 128)) >= 64)
        assertEquals(0, Color.alpha(output.getPixel(128, 15)))
    }
}
