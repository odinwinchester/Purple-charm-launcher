package com.purplecharm.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
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
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetMissionAndroidTest {
    private fun prefs(name: String = "purple_charm_favorites") = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun seed() { prefs().edit().clear().commit() }
    private fun field(a: MainActivity, key: String) = MainActivity::class.java.getDeclaredField(key).apply { isAccessible = true }.get(a)
    private fun call(a: MainActivity, key: String) = MainActivity::class.java.getDeclaredMethod(key).apply { isAccessible = true }.invoke(a)
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun layout(a: MainActivity, width: Int = 1080, height: Int = 1920) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }
    private fun send(a: MainActivity, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(0, time, action, x, y, 0)
        try { a.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun provider() = AppWidgetProviderInfo().apply { provider = ComponentName("test.clock", "Provider"); label = "Clock"; minWidth = 60; minHeight = 80; initialLayout = android.R.layout.simple_list_item_1 }
    @Test fun hourlyDetailsScrollAndPreserveUtcConversionAndLocationCacheIsolation() {
        seed()
        val now = System.currentTimeMillis()
        val base = now / 3_600_000L * 3_600_000L
        val points = (0..12).joinToString(",") { i ->
            """{"time":"${java.time.Instant.ofEpochMilli(base + i * 3_600_000L)}","data":{"instant":{"details":{"air_temperature":${20 + i}}}}}"""
        }
        prefs().edit().putString("gothic_weather_lat", "-20.4").putString("gothic_weather_lon", "-54.6")
            .putString("met_forecast_location", "lat=-20.4000&lon=-54.6000")
            .putString("met_forecast_json", """{"properties":{"timeseries":[$points]}}""")
            .putLong("gothic_weather_expires", now + 3_600_000L).commit()
        val previousZone = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Campo_Grande"))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            call(c.get(), "weatherDetails")
            val dialog = ShadowAlertDialog.getLatestAlertDialog()
            val scroll = dialog.window!!.decorView.findViewWithTag<android.widget.ScrollView>("weather_details_scroll")!!
            val text = scroll.findViewWithTag<TextView>("weather_hourly_details")!!
            assertEquals(Int.MAX_VALUE, text.maxLines)
            assertTrue(text.text.toString().contains("Próximas horas"))
            assertTrue(text.text.toString().contains("-04:00"))
            assertTrue(text.text.toString().contains("32°C"))
            dialog.dismiss()
            prefs().edit().putString("gothic_weather_lon", "-45").commit()
            val client = MetWeatherClient(prefs())
            assertNull(client.current(now)); assertTrue(client.upcoming(now).isEmpty())
        } finally {
            c.pause().stop().destroy()
            java.util.TimeZone.setDefault(previousZone)
        }
    }
    @Test fun emptyCellLongPressOpensSmokedEditorWithoutChangingLayout() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val before = prefs().all.toMap()
        val cell = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        assertTrue(cell.performLongClick())
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        val entries = (0 until dialog.listView.adapter.count).map { dialog.listView.adapter.getItem(it).toString() }
        assertTrue("Widgets" in entries); assertTrue("Configurar dock" in entries); assertTrue("Wallpapers" in entries)
        assertEquals(before, prefs().all)
        dialog.dismiss(); c.pause().stop().destroy()
    }
    @Test fun dockSlotsRemainConfiguredAndDrawerIsTheOnlyStateWithoutTheFixedBar() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val initialDock = a.window.decorView.findViewWithTag<View>("home_dock")
        assertNotNull(initialDock)
        assertEquals(View.VISIBLE, initialDock!!.visibility)
        for (i in 0..3) assertNotNull(a.window.decorView.findViewWithTag<View>("dock_slot_$i"))
        call(a, "dockConfiguration")
        val menu = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(4, menu.listView.adapter.count); menu.dismiss()
        a.window.decorView.findViewWithTag<View>("dock_drawer")!!.performClick()
        assertEquals(true, field(a, "drawerOpen")); assertNotNull(a.window.decorView.findViewWithTag<View>("drawer_grid"))
        assertNull(a.window.decorView.findViewWithTag<View>("home_dock"))
        a.onBackPressed()
        val restoredDock = a.window.decorView.findViewWithTag<View>("home_dock")
        assertNotNull(restoredDock)
        assertEquals(View.VISIBLE, restoredDock!!.visibility)
        c.pause().stop().destroy()
    }

    @Test fun centralDockButtonOpensDrawerFromAnyHomePage() {
        seed()
        assertTrue(HomePageSelection.write(prefs(), 1, 3))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val pageIndex = MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }
        assertEquals(1, pageIndex.getInt(a))
        a.window.decorView.findViewWithTag<View>("dock_drawer")!!.performClick()
        assertTrue(field(a, "drawerOpen") as Boolean)
        assertNotNull(a.window.decorView.findViewWithTag<View>("drawer_grid"))
        assertNull(a.window.decorView.findViewWithTag<View>("home_dock"))
        a.onBackPressed()
        assertFalse(field(a, "drawerOpen") as Boolean)
        MainActivity::class.java.getDeclaredMethod("changePage", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(a, 0)
        assertEquals(0, pageIndex.getInt(a))
        a.window.decorView.findViewWithTag<View>("dock_drawer")!!.performClick()
        assertTrue(field(a, "drawerOpen") as Boolean)
        assertNotNull(a.window.decorView.findViewWithTag<View>("drawer_grid"))
        a.onBackPressed()
        c.pause().stop().destroy()
    }

    @Test fun homeAndBackPageNavigationReuseTheExistingWorkspaceViews() {
        seed(); assertTrue(HomePageSelection.write(prefs(), 1, 3))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val dock = a.window.decorView.findViewWithTag<View>("home_dock")!!
        val workspace = a.window.decorView.findViewWithTag<View>("workspace_drag_surface")!!
        val pages = field(a, "pages")
        val pageIndex = MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }
        val changePage = MainActivity::class.java.getDeclaredMethod("changePage", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val runHome = MainActivity::class.java.getDeclaredMethod("runGesture", String::class.java)
            .apply { isAccessible = true }
        changePage.invoke(a, 0)
        assertTrue(runHome.invoke(a, "home") as Boolean)
        assertEquals(1, pageIndex.getInt(a))
        assertSame(dock, a.window.decorView.findViewWithTag<View>("home_dock"))
        assertSame(workspace, a.window.decorView.findViewWithTag<View>("workspace_drag_surface"))
        assertSame(pages, field(a, "pages"))

        changePage.invoke(a, 2)
        a.onBackPressed()
        assertEquals(1, pageIndex.getInt(a))
        assertSame(dock, a.window.decorView.findViewWithTag<View>("home_dock"))
        assertSame(workspace, a.window.decorView.findViewWithTag<View>("workspace_drag_surface"))
        c.pause().stop().destroy()
    }

    @Test fun approvedDefaultSwipesOpenOptionsUpAndDrawerDownWithinWorkspace() {
        seed()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); layout(a)
        val workspace = a.window.decorView.findViewWithTag<View>("workspace_drag_surface")!!
        val bounds = android.graphics.Rect(); assertTrue(workspace.getGlobalVisibleRect(android.graphics.Rect()))
        val location = IntArray(2); workspace.getLocationInWindow(location)
        bounds.set(location[0], location[1], location[0] + workspace.width, location[1] + workspace.height)
        val x = bounds.centerX().toFloat(); val bottom = bounds.bottom - 16f
        send(a, MotionEvent.ACTION_DOWN, x, bottom, 1)
        send(a, MotionEvent.ACTION_MOVE, x + 2f, bottom - 140f, 30)
        send(a, MotionEvent.ACTION_UP, x + 2f, bottom - 140f, 60)
        val options = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val optionLabels = views(options.window!!.decorView).filterIsInstance<TextView>().mapNotNull { it.text?.toString() }
        assertTrue("Organizar" in optionLabels)
        assertTrue("Papel de parede" in optionLabels)
        assertTrue("Configurações" in optionLabels)
        assertFalse(field(a, "drawerOpen") as Boolean)
        assertFalse(prefs().contains("pc_swipe_up"))
        options.dismiss()

        val top = bounds.top + 20f
        send(a, MotionEvent.ACTION_DOWN, x, top, 100)
        send(a, MotionEvent.ACTION_MOVE, x + 2f, top + 140f, 130)
        send(a, MotionEvent.ACTION_UP, x + 2f, top + 140f, 160)
        assertTrue(field(a, "drawerOpen") as Boolean)
        assertNull(a.window.decorView.findViewWithTag<View>("home_dock"))
        assertFalse(prefs().contains("pc_swipe_down"))
        a.onBackPressed()
        assertNotNull(a.window.decorView.findViewWithTag<View>("home_dock"))
        c.pause().stop().destroy()
    }

    @Test fun gesturePreferencesPersistAndResetToApprovedDefaults() {
        seed()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val save = MainActivity::class.java.getDeclaredMethod("saveSetting", String::class.java, String::class.java).apply { isAccessible = true }
        save.invoke(a, "swipe_up", "wallpaper"); save.invoke(a, "swipe_down", "search")
        val read = MainActivity::class.java.getDeclaredMethod("setting", String::class.java, String::class.java).apply { isAccessible = true }
        assertEquals("wallpaper", read.invoke(a, "swipe_up", "options"))
        assertEquals("search", read.invoke(a, "swipe_down", "drawer"))

        MainActivity::class.java.getDeclaredMethod("settingsCategory", String::class.java).apply { isAccessible = true }.invoke(a, "Launcher")
        val settings = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        views(settings.window!!.decorView).filterIsInstance<TextView>()
            .first { it.text?.toString()?.contains("Restaurar gestos e grade") == true }.performClick()
        val confirmation = ShadowAlertDialog.getLatestAlertDialog()
        confirmation.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()
        assertFalse(prefs().contains("pc_swipe_up"))
        assertFalse(prefs().contains("pc_swipe_down"))
        assertEquals("options", read.invoke(a, "swipe_up", "options"))
        assertEquals("drawer", read.invoke(a, "swipe_down", "drawer"))
        c.pause().stop().destroy()
    }

    @Test fun dockStaysAttachedAndVisibleAcrossEveryHomePageChange() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val originalDock = a.window.decorView.findViewWithTag<View>("home_dock")
        assertNotNull(originalDock)
        val changePage = MainActivity::class.java.getDeclaredMethod("changePage", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        for (page in 0..2) {
            changePage.invoke(a, page)
            val dock = a.window.decorView.findViewWithTag<View>("home_dock")
            assertSame("dock should not be rebuilt while changing pages", originalDock, dock)
            assertEquals(View.VISIBLE, dock!!.visibility)
        }
        c.pause().stop().destroy()
    }

    @Test fun weatherWidgetTapOpensDetailsLongPressEntersResizeAndOptionsRemainExplicit() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val editor = field(a, "widgetEditor") as WeatherResizeLayout
        assertTrue(editor.performClick())
        val expandedIntent = shadowOf(a).nextStartedActivity
        assertNotNull("widget tap should launch expanded weather", expandedIntent)
        assertEquals(ExpandedWeatherActivity::class.java.name, expandedIntent.component?.className)
        assertNotNull("selected artwork should be passed to expanded view", expandedIntent.getStringExtra("art_slug"))

        assertTrue(editor.performLongClick())
        assertTrue(editor.isEditing)
        val left = WeatherResizeLayout::class.java.getDeclaredField("leftHandle").apply { isAccessible = true }.get(editor) as View
        val right = WeatherResizeLayout::class.java.getDeclaredField("rightHandle").apply { isAccessible = true }.get(editor) as View
        assertEquals(View.VISIBLE, left.visibility)
        assertEquals(View.VISIBLE, right.visibility)
        editor.endEditing()

        call(a, "widgetCenter")
        val center = ShadowAlertDialog.getLatestAlertDialog()
        center.listView.performItemClick(null, 0, 0L)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        val labels = (0 until options.listView.adapter.count).map { options.listView.adapter.getItem(it).toString() }
        assertTrue("weather settings stay available from the explicit widget action", "Localização e clima" in labels)
        assertTrue("manual art options stay available", "Personalizar arte (opcional)" in labels)
        c.pause().stop().destroy()
    }
    @Test fun widgetCenterExposesExclusiveAndOfficialAndroidChoices() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        call(a, "widgetCenter"); val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(3, dialog.listView.adapter.count)
        assertTrue(dialog.listView.adapter.getItem(1).toString().contains("Android"))
        dialog.dismiss(); c.pause().stop().destroy()
    }
    @Test fun bindingDenialDeletesPendingIdAndLeavesExistingPreferencesUntouched() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val p = prefs("bindDenied"); p.edit().clear().putString("wallpaper", "keep").commit()
        val manager = AppWidgetManager.getInstance(a); val info = provider()
        shadowOf(manager).addInstalledProvider(info)
        shadowOf(manager).setAllowedToBindAppWidgets(false)
        val controller = AndroidWidgetController(a, p, { 3 }, { 5 }, {})
        val host = AndroidWidgetController::class.java.getDeclaredField("host").apply { isAccessible = true }.get(controller) as android.appwidget.AppWidgetHost
        shadowOf(host).setAppWidgetIdToAllocate(101)
        controller.add(info)
        val pending = p.getInt("widget_pending_id", 0); assertTrue(pending > 0)
        val request = shadowOf(a).nextStartedActivity
        assertEquals(AppWidgetManager.ACTION_APPWIDGET_BIND, request.action)
        assertEquals(pending, request.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
        assertTrue(controller.onResult(AndroidWidgetController.BIND_REQUEST, Activity.RESULT_CANCELED, null))
        assertEquals(0, p.getInt("widget_pending_id", 0)); assertTrue(HostedWidgetStore(p).read().isEmpty())
        assertEquals("keep", p.getString("wallpaper", null))
        controller.close(); c.pause().stop().destroy()
    }
    @Test fun boundIdPersistsOnceAndCancellationCannotDeleteAlreadyStoredInstance() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val p = prefs("bound"); p.edit().clear().commit()
        val manager = AppWidgetManager.getInstance(a); val info = provider()
        shadowOf(manager).addInstalledProvider(info); shadowOf(manager).setAllowedToBindAppWidgets(true)
        var changes = 0
        val controller = AndroidWidgetController(a, p, { 3 }, { 5 }, { changes++ })
        val host = AndroidWidgetController::class.java.getDeclaredField("host").apply { isAccessible = true }.get(controller) as android.appwidget.AppWidgetHost
        shadowOf(host).setAppWidgetIdToAllocate(102)
        controller.add(info)
        val saved = HostedWidgetStore(p).read(); assertEquals(1, saved.size); assertEquals(1, changes)
        assertEquals(1, saved[0].page); assertEquals(info.provider.flattenToString(), saved[0].provider)
        controller.onResult(AndroidWidgetController.BIND_REQUEST, Activity.RESULT_OK, null)
        assertEquals(saved, HostedWidgetStore(p).read())
        p.edit().putInt("widget_pending_id", saved[0].id).commit(); controller.cancelPending()
        assertNotNull(manager.getAppWidgetInfo(saved[0].id)); assertEquals(saved, HostedWidgetStore(p).read())
        controller.close(); c.pause().stop().destroy()
    }
    @Test fun corruptHostedLayoutIsNotOverwrittenByNewAddition() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val p = prefs("badHost"); p.edit().clear().putString("hosted_widgets_v1", "corrupt").commit()
        val before = p.all.toMap(); val controller = AndroidWidgetController(a, p, { 3 }, { 5 }, {})
        controller.add(provider()); assertEquals(before, p.all)
        controller.close(); c.pause().stop().destroy()
    }
    @Test fun allTwentySixArtworksFitIncludingTheirBottomBorderAtDifferentViewportSizes() {
        val app = RuntimeEnvironment.getApplication()
        for (art in WeatherArtworkCatalog.all) for ((w, h) in listOf(1080 to 300, 720 to 130, 1920 to 180)) {
            val image = ImageView(app).apply { setImageResource(art.resource); scaleType = ImageView.ScaleType.FIT_CENTER }
            val editor = WeatherResizeLayout(app, image, 5, 5, {}, {}, {})
            editor.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            editor.layout(0, 0, w, h)
            val frame = editor.getChildAt(0)
            assertTrue(art.slug, frame.left >= 0 && frame.top >= 0 && frame.right <= w && frame.bottom <= h)
            assertTrue(art.slug, frame.width > 0 && frame.height > 0)
        }
    }
    @Test fun providerResizeFlagsAreRespectedAndSizePersists() {
        val c = Robolectric.buildActivity(Activity::class.java).setup(); val a = c.get()
        val p = prefs("resizeHost"); p.edit().clear().commit()
        val manager = AppWidgetManager.getInstance(a); val info = provider()
        shadowOf(manager).addInstalledProvider(info); shadowOf(manager).setAllowedToBindAppWidgets(true)
        val controller = AndroidWidgetController(a, p, { 3 }, { 5 }, {})
        val host = AndroidWidgetController::class.java.getDeclaredField("host").apply { isAccessible = true }.get(controller) as android.appwidget.AppWidgetHost
        shadowOf(host).setAppWidgetIdToAllocate(103); controller.add(info)
        val original = HostedWidgetStore(p).read().single()
        val resize = AndroidWidgetController::class.java.getDeclaredMethod("resize", HostedWidget::class.java).apply { isAccessible = true }
        resize.invoke(controller, original)
        assertEquals(original, HostedWidgetStore(p).read().single())
        info.resizeMode = AppWidgetProviderInfo.RESIZE_HORIZONTAL
        resize.invoke(controller, original)
        val options = ShadowAlertDialog.getLatestAlertDialog()
        for (i in 0 until options.listView.adapter.count) assertTrue(options.listView.adapter.getItem(i).toString().startsWith("Largura:"))
        val last = options.listView.adapter.count - 1
        options.listView.performItemClick(null, last, last.toLong())
        val updated = HostedWidgetStore(p).read().single()
        assertEquals(5, updated.span); assertEquals(original.heightDp, updated.heightDp)
        assertNotNull(controller.pageView(1))
        controller.close(); c.pause().stop().destroy()
    }
    @Test fun failedHostedStoreCommitRestoresPreviousWidgetLayout() {
        val p = prefs("hostDiskFailure"); p.edit().clear().commit()
        val store = HostedWidgetStore(p)
        val original = listOf(HostedWidget(7, "clock/Provider", 1, 2, 120, 0))
        assertTrue(store.save(original)); val before = p.all.toMap()
        var fail = true
        val proxy = java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences::class.java.classLoader, arrayOf(android.content.SharedPreferences::class.java)) { _, method, args ->
            val value = method.invoke(p, *(args ?: emptyArray()))
            if (method.name != "edit") value else {
                val editor = value as android.content.SharedPreferences.Editor
                var wrapper: Any? = null
                wrapper = java.lang.reflect.Proxy.newProxyInstance(android.content.SharedPreferences.Editor::class.java.classLoader, arrayOf(android.content.SharedPreferences.Editor::class.java)) { _, m, a ->
                    val result = m.invoke(editor, *(a ?: emptyArray()))
                    if (m.name == "commit" && fail) { fail = false; false } else if (result is android.content.SharedPreferences.Editor) wrapper else result
                }
                wrapper
            }
        } as android.content.SharedPreferences
        assertFalse(HostedWidgetStore(proxy).save(original.map { it.copy(page = 2) }))
        assertEquals(before, p.all)
    }

    @Test fun frameworkHostViewIsCreatedReusedAndKeptOutsideIconViewport() {
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val p = prefs("hostView"); p.edit().clear().commit()
        val manager = AppWidgetManager.getInstance(a)
        val info = provider().apply { provider = ComponentName(a.packageName, "WidgetProbeProvider") }
        val metadata = android.content.pm.ActivityInfo().apply {
            packageName = a.packageName; name = "WidgetProbeProvider"; applicationInfo = a.applicationInfo
        }
        AppWidgetProviderInfo::class.java.getDeclaredField("providerInfo").apply { isAccessible = true }.set(info, metadata)
        shadowOf(manager).addInstalledProvider(info); shadowOf(manager).setAllowedToBindAppWidgets(true)
        val controller = AndroidWidgetController(a, p, { 3 }, { 5 }, {})
        val host = AndroidWidgetController::class.java.getDeclaredField("host").apply { isAccessible = true }.get(controller) as android.appwidget.AppWidgetHost
        shadowOf(host).setAppWidgetIdToAllocate(104); controller.add(info)
        val firstPage = controller.pageView(1)!!
        val hosted = views(firstPage).filterIsInstance<android.appwidget.AppWidgetHostView>().single()
        assertEquals(104, hosted.appWidgetId)
        val secondPage = controller.pageView(1)!!
        assertSame(hosted, views(secondPage).filterIsInstance<android.appwidget.AppWidgetHostView>().single())
        controller.start(); assertTrue(shadowOf(host).isListening)
        controller.stop(); assertFalse(shadowOf(host).isListening)
        controller.releaseHiddenViews(); assertNull(hosted.parent)
        assertEquals(104, HostedWidgetStore(p).read().single().id)
        controller.start(); assertEquals(104, HostedWidgetStore(p).read().single().id)
        controller.close(); c.pause().stop().destroy()
    }
    @Test fun forecastIsNeverBorrowedFromAnotherCityOrKeptAfterItsLastPointExpires() {
        val p = prefs("metCity"); p.edit().clear().commit()
        val now = System.currentTimeMillis()
        val timestamp = java.time.Instant.ofEpochMilli(now - 60_000).toString()
        val body = """{"properties":{"timeseries":[{"time":"$timestamp","data":{"instant":{"details":{"air_temperature":23.25}},"next_1_hours":{"summary":{"symbol_code":"cloudy"}}}}]}}"""
        p.edit().putString("gothic_weather_lat", "0").putString("gothic_weather_lon", "0")
            .putString("met_forecast_location", "lat=1.0000&lon=1.0000").putString("met_forecast_json", body).commit()
        val client = MetWeatherClient(p)
        assertNull(client.current(now))
        p.edit().putString("met_forecast_location", "lat=0.0000&lon=0.0000").commit()
        assertEquals(23.25, client.current(now)!!.temperature, 0.0)
        assertNull(client.current(now + 3 * 60 * 60_000L))
        p.edit().putString("gothic_weather_lat", "5").commit()
        assertNull(client.current(now))
    }

}
