package com.purplecharm.launcher

import android.app.AlertDialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AdditionalWeatherAndroidTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)
    private fun seed() = prefs().also { p ->
        p.edit().clear().commit()
        assertTrue(WorkspaceItemsStore(p).save(WorkspaceItems(
            listOf(listOf("folder:preserve") + List(19) { "" }, List(20) { "" }, List(20) { "" }),
            List(4) { "" }, mapOf("folder:preserve" to WorkspaceFolder("Preservar", listOf("pkg/A", "pkg/B"))))))
    }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun settle(activity: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val metrics = activity.resources.displayMetrics
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
    private fun art(activity: MainActivity, slug: String): ImageView {
        val label = WeatherArtworkCatalog.all.first { it.slug == slug }.label
        return views(activity.window.decorView).filterIsInstance<ImageView>().first { it.contentDescription == label }
    }
    private fun menu(activity: MainActivity) {
        MainActivity::class.java.getDeclaredMethod("moonWidgetOptions").apply { isAccessible = true }.invoke(activity)
    }
    private fun click(dialog: AlertDialog, index: Int) {
        dialog.listView.performItemClick(null, index, index.toLong())
        shadowOf(Looper.getMainLooper()).idle()
    }
    @Test fun androidDecoderRecognizesAllTwentySixFullSizeResources() {
        val app = RuntimeEnvironment.getApplication()
        val dimensions = listOf("/weather-artwork-sizes.tsv", "/weather-additional-artwork-sizes.tsv").flatMap { file ->
            javaClass.getResourceAsStream(file)!!.bufferedReader().use { it.readLines() }
        }.associate { line -> val (name, w, h) = line.split('\t'); name to (w.toInt() to h.toInt()) }
        assertEquals(26, dimensions.size)
        for (item in WeatherArtworkCatalog.all) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(app.resources, item.resource, options)
            assertEquals(item.slug, dimensions.getValue(item.slug).first, options.outWidth)
            assertEquals(item.slug, dimensions.getValue(item.slug).second, options.outHeight)
        }
    }
    private fun cached(symbol: String, temperature: Int) {
        val p = prefs()
        val timestamp = Instant.ofEpochMilli(System.currentTimeMillis() - 60_000L).toString()
        val json = """{"properties":{"timeseries":[{"time":"$timestamp","data":{"instant":{"details":{"air_temperature":$temperature}},"next_1_hours":{"summary":{"symbol_code":"$symbol"}}}}]}}"""
        val utc = java.time.Instant.ofEpochMilli(System.currentTimeMillis()).atZone(java.time.ZoneOffset.UTC)
        val longitude = 180.0 - (utc.hour + utc.minute / 60.0) * 15.0 // near local solar noon
        val key = String.format(java.util.Locale.US, "lat=%.4f&lon=%.4f", 0.0, longitude)
        p.edit().putString("gothic_weather_location_mode", "city").putString("gothic_weather_lat", "0")
            .putString("gothic_weather_lon", longitude.toString()).putString("met_forecast_location", key)
            .putString("met_forecast_json", json).putLong("gothic_weather_expires", System.currentTimeMillis() + 3_600_000L).commit()
    }
    @Test fun activitySelectsRainColdAndSnowFromMetCacheWithoutRealNetwork() {
        for ((symbol, temp, expected) in listOf(Triple("rain_day", 20, "sol_dia_chuvoso"),
            Triple("clearsky_day", -3, "sol_dia_frio"), Triple("snowandthunder_day", 1, "sol_dia_frio"))) {
            seed(); cached(symbol, temp)
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            assertNotNull(art(controller.get(), expected))
            controller.pause().stop().destroy()
        }
    }
    @Test fun manualMenuSelectsAllSixAndPersistsThroughRecreationWithoutMovingIconsOrFolders() {
        val p = seed()
        val folderBytes = p.getString("workspace_folders_v1", null)
        val cells = p.getString("layout_page_cells_0", null)
        val dock = p.getString("layout_dock_slots", null)
        p.edit().putString("pc_wallpaper_choice", "floral").commit()
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get()
        settle(activity)
        val originalTop = Rect().also {
            activity.window.decorView.findViewWithTag<ScrollView>("home_icons_scroll")!!.getGlobalVisibleRect(it)
        }.top
        for (item in WeatherArtworkCatalog.all.drop(20)) {
            menu(activity); click(ShadowAlertDialog.getLatestAlertDialog(), 1)
            val chooser = ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(26, chooser.listView.adapter.count)
            click(chooser, WeatherArtworkCatalog.all.indexOf(item))
            assertEquals(item.slug, p.getString("gothic_manual_art", null))
            assertNotNull(art(activity, item.slug))
            settle(activity)
            val now = Rect().also { activity.window.decorView.findViewWithTag<ScrollView>("home_icons_scroll")!!.getGlobalVisibleRect(it) }
            assertEquals(originalTop, now.top)
            assertEquals(folderBytes, p.getString("workspace_folders_v1", null)); assertEquals(cells, p.getString("layout_page_cells_0", null))
            assertEquals(dock, p.getString("layout_dock_slots", null)); assertEquals("floral", p.getString("pc_wallpaper_choice", null))
            controller.pause().stop().destroy()
            controller = Robolectric.buildActivity(MainActivity::class.java).setup(); activity = controller.get()
            assertNotNull(art(activity, item.slug))
        }
        menu(activity); click(ShadowAlertDialog.getLatestAlertDialog(), 2)
        assertFalse(p.contains("gothic_manual_art"))
        assertEquals(0, p.getInt("gothic_celestial_mode", -1))
        assertEquals(folderBytes, p.getString("workspace_folders_v1", null))
        controller.pause().stop().destroy()
    }
    @Test fun panoramicArtStartsFullWidthAndItsResizePersistsWithoutChangingOriginalGlobalSize() {
        val p = seed()
        p.edit().putString("gothic_manual_art", "lua_noite_chuvosa")
            .putInt("gothic_widget_span", 2).putString("pc_columns", "4").commit()
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get()
        var editor = views(activity.window.decorView).filterIsInstance<WeatherResizeLayout>().first()
        val spanField = WeatherResizeLayout::class.java.getDeclaredField("span").apply { isAccessible = true }
        assertEquals(4, spanField.getInt(editor))
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt(); val height = (160 * density).toInt()
        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        editor.layout(0, 0, width, height); editor.beginEditing()
        val handle = WeatherResizeLayout::class.java.getDeclaredField("rightHandle").apply { isAccessible = true }.get(editor) as View
        val time = SystemClock.uptimeMillis()
        for ((action, x) in listOf(MotionEvent.ACTION_DOWN to 12f, MotionEvent.ACTION_MOVE to (12f - width / 4f), MotionEvent.ACTION_UP to (12f - width / 4f))) {
            val event = MotionEvent.obtain(time, time + action, action, x, 12f, 0)
            handle.dispatchTouchEvent(event); event.recycle()
        }
        editor.endEditing()
        assertEquals(3, p.getInt("gothic_widget_span_lua_noite_chuvosa", -1))
        assertEquals(2, p.getInt("gothic_widget_span", -1))
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(); activity = controller.get()
        editor = views(activity.window.decorView).filterIsInstance<WeatherResizeLayout>().first()
        assertEquals(3, spanField.getInt(editor))
        controller.pause().stop().destroy()
    }
    @Test fun originalArtAlsoCommitsContainerWidthInsteadOfHandleWidth() {
        val p = seed()
        p.edit().putString("gothic_manual_art", "dia_limpo").putInt("gothic_widget_span", 4)
            .putString("pc_columns", "4").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val editor = views(activity.window.decorView).filterIsInstance<WeatherResizeLayout>().first()
        val density = activity.resources.displayMetrics.density
        val width = (360 * density).toInt(); val height = (250 * density).toInt()
        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        editor.layout(0, 0, width, height); editor.beginEditing()
        val handle = WeatherResizeLayout::class.java.getDeclaredField("rightHandle").apply { isAccessible = true }.get(editor) as View
        val time = SystemClock.uptimeMillis()
        for ((action, x) in listOf(MotionEvent.ACTION_DOWN to 12f, MotionEvent.ACTION_MOVE to (12f - width / 4f), MotionEvent.ACTION_UP to (12f - width / 4f))) {
            val event = MotionEvent.obtain(time, time + action, action, x, 12f, 0)
            handle.dispatchTouchEvent(event); event.recycle()
        }
        editor.endEditing()
        assertEquals(3, p.getInt("gothic_widget_span", -1))
        assertFalse(p.contains("gothic_widget_span_lua_noite_chuvosa"))
        controller.pause().stop().destroy()
    }

    @Test fun homeHasOnlyArtworkAndDetailsRetainLiveForecastLocationAndAttribution() {
        val p = seed(); cached("rain_day", 20)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); settle(a)
        val panel = MainActivity::class.java.getDeclaredField("celestialView").apply { isAccessible = true }.get(a) as View
        assertFalse(views(panel).filterIsInstance<android.widget.TextView>().any { it.text.toString().contains("MET Norway") || it.text.toString().contains("ilustrativos") || it.text.toString().contains("°C") })
        assertNotNull(art(a, "sol_dia_chuvoso"))
        val location = p.getString("gothic_weather_lat", null)
        menu(a); click(ShadowAlertDialog.getLatestAlertDialog(), 6)
        val details = ShadowAlertDialog.getLatestAlertDialog()
        val message = details.findViewById<android.widget.TextView>(android.R.id.message).text.toString()
        assertTrue(message.contains("20°C")); assertTrue(message.contains("MET Norway"))
        assertTrue(message.contains("ilustrativos")); assertEquals(location, p.getString("gothic_weather_lat", null))
        details.dismiss(); controller.pause().stop().destroy()
    }

}
