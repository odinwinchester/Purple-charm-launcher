package com.purplecharm.launcher

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CosmicHorrorAndroidTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)
    private fun call(a: MainActivity, name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    private fun background(a: MainActivity) = MainActivity::class.java.getDeclaredField("backgroundImage").apply { isAccessible = true }.get(a) as ImageView
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    @Test fun originalResourceDecodesWithoutDensityInflationWithinExistingCacheBudget() {
        val cache = GraphicResourceCache(RuntimeEnvironment.getApplication().resources)
        val first = cache.load(R.drawable.cosmic_horror_azathoth, 1080, 2400)
        val again = cache.load(R.drawable.cosmic_horror_azathoth, 1080, 2400)
        assertEquals(941, first.bitmap.width); assertEquals(1672, first.bitmap.height)
        assertSame(first.bitmap, again.bitmap)
        assertTrue(first.bitmap.allocationByteCount <= 941 * 1672 * 4)
        assertTrue(cache.bytes() <= 16 * 1024 * 1024)
    }
    @Test fun collectionMenuPreservesPreviousChoicesAndAppliesAzathothThroughExistingFlow() {
        val p = prefs(); p.edit().clear().putString("pc_wallpaper_choice", "floral").commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val before = p.all.toMap()
        call(a, "wallpaperMenu")
        val chooser = ShadowDialog.getLatestDialog()
        assertTrue(views(chooser.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == "✦ Cosmic Horror" })
        for (key in listOf("floral", "eye_crystal", "ghost_fire", "cosmic_azathoth"))
            assertNotNull(chooser.window!!.decorView.findViewWithTag<View>("wallpaper_$key"))
        val image = chooser.window!!.decorView.findViewWithTag<ImageView>("wallpaper_cosmic_azathoth")!!
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
        assertEquals(before, p.all)
        image.performClick()
        val preview = ShadowDialog.getLatestDialog()
        val full = views(preview.window!!.decorView).filterIsInstance<ImageView>().first()
        assertEquals(ImageView.ScaleType.FIT_CENTER, full.scaleType)
        full.performClick()
        val destination = ShadowDialog.getLatestDialog()
        views(destination.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString() == "⌂ Tela inicial" }.performClick()
        assertEquals("cosmic_azathoth", p.getString("pc_wallpaper_choice", null))
        assertEquals(ImageView.ScaleType.FIT_CENTER, background(a).scaleType)
        assertEquals(before.filterKeys { it != "pc_wallpaper_choice" }, p.all.filterKeys { it != "pc_wallpaper_choice" })
        chooser.dismiss(); preview.dismiss(); c.pause().stop().destroy()
    }
    @Test fun selectionSurvivesRestartAndPreviousWallpaperKeepsItsDisplayPolicy() {
        val p = prefs(); p.edit().clear().putString("pc_wallpaper_choice", "cosmic_azathoth").commit()
        repeat(2) {
            val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
            assertEquals(ImageView.ScaleType.FIT_CENTER, background(a).scaleType)
            val bitmap = (background(a).drawable as BitmapDrawable).bitmap
            assertEquals(941, bitmap.width); assertEquals(1672, bitmap.height)
            c.pause().stop().destroy()
        }
        p.edit().putString("pc_wallpaper_choice", "ghost_fire").commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        assertEquals(ImageView.ScaleType.CENTER_CROP, background(c.get()).scaleType)
        c.pause().stop().destroy()
    }
}
