package com.purplecharm.launcher

import android.app.Dialog
import android.content.Context
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ConsolidationAndroidTest {
    private fun prefs() = RuntimeEnvironment.getApplication().getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)
    private fun seed(count: Int = 2) {
        val p = prefs(); p.edit().clear().commit()
        assertTrue(WorkspaceItemsStore(p).save(WorkspaceItems(
            listOf(listOf("folder:test") + List(19) { "" }, List(20) { "" }, List(20) { "" }),
            List(4) { "" }, mapOf("folder:test" to WorkspaceFolder("Preservar", List(count) { "pkg/App$it" })))))
    }
    private fun call(a: MainActivity, name: String) = MainActivity::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(a)
    private fun field(a: MainActivity, name: String): Any? = MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)
    private fun views(v: View): List<View> = listOf(v) + if (v is ViewGroup)
        (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
    private fun layout(a: MainActivity) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
    }
    @Test fun smokedOptionsKeepsThemeLauncherAndSystemOnlyInBottomMenu() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        call(a, "settingsMenu"); val dialog = field(a, "optionsDialog") as Dialog
        val panel = dialog.window!!.decorView.findViewWithTag<LinearLayout>("smoked_options")!!
        val color = (panel.background as GradientDrawable).color!!.defaultColor
        assertTrue(Color.alpha(color) in 1..254); assertTrue(Color.red(color) < 30)
        assertEquals(3, panel.childCount)
        assertFalse(views(panel).filterIsInstance<TextView>().any { it.text.toString().contains("Configurações do sistema") })
        dialog.dismiss()
        call(a, "showClassicCustomizationMenu")
        val bottom = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val system = views(bottom.window!!.decorView).filterIsInstance<TextView>().first { it.text.toString() == "Sistema" }
        (system.parent as View).performClick()
        assertEquals(android.provider.Settings.ACTION_SETTINGS, shadowOf(a).nextStartedActivity.action)
        c.pause().stop().destroy()
    }
    @Test fun themeAndLauncherCommandsOpenTheirExistingCategories() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        for ((index, expected) in listOf(0 to "✦ Tema", 1 to "✦ Launcher")) {
            call(a, "settingsMenu"); val dialog = field(a, "optionsDialog") as Dialog
            dialog.window!!.decorView.findViewWithTag<View>("settings_command_$index")!!.performClick()
            val opened = org.robolectric.shadows.ShadowDialog.getLatestDialog()
            assertTrue(opened.isShowing)
            assertTrue(views(opened.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == expected })
            opened.dismiss()
        }
        c.pause().stop().destroy()
    }
    @Test fun folderViewportIsThreeColumnsTwoRowsWithoutDroppingOverflowMembers() {
        for (count in listOf(1, 3, 6, 9)) {
            seed(count); val before = prefs().getString("workspace_folders_v1", null)
            val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
            MainActivity::class.java.getDeclaredMethod("showFolder", String::class.java).apply { isAccessible = true }.invoke(a, "folder:test")
            val dialog = field(a, "folderDialog") as FolderSurface
            val panel = dialog.content as LinearLayout
            val grid = panel.findViewWithTag<GridLayout>("folder_grid")!!
            assertEquals(3, grid.columnCount); assertEquals(count, grid.childCount)
            assertEquals(Color.TRANSPARENT, (panel.background as GradientDrawable).color!!.defaultColor)
            val viewport = grid.parent as ScrollView
            val rows = ((count + 2) / 3).coerceIn(1, 2)
            assertEquals((rows * 88 * a.resources.displayMetrics.density).toInt(), viewport.layoutParams.height)
            assertEquals(before, prefs().getString("workspace_folders_v1", null))
            dialog.dismiss(); c.pause().stop().destroy()
        }
    }
    @Test fun folderHandoffIsArmedExplicitlyAndCancelKeepsAllMembers() {
        seed(8); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val before = prefs().all.toMap()
        MainActivity::class.java.getDeclaredMethod("showFolder", String::class.java).apply { isAccessible = true }.invoke(a, "folder:test")
        val dialog = field(a, "folderDialog") as FolderSurface
        assertEquals(false, field(a, "folderHomeDragArmed"))
        dialog.content.findViewWithTag<View>("folder_drag_home")!!.performClick()
        assertEquals(true, field(a, "folderHomeDragArmed"))
        dialog.cancel(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, prefs().all)
        assertEquals(8, WorkspaceItemsStore(prefs()).folders().getValue("folder:test").apps.size)
        c.pause().stop().destroy()
    }
    @Test fun unchangedWeatherReusesViewAndChangedArtReplacesOnlyWidget() {
        seed(); prefs().edit().putString("gothic_manual_art", "dia_limpo").commit()
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); layout(a)
        val old = field(a, "celestialView")
        val surface = a.window.decorView.findViewWithTag<View>("workspace_drag_surface")
        val cell = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")
        repeat(3) { call(a, "refreshCelestialView") }
        assertSame(old, field(a, "celestialView"))
        prefs().edit().putString("gothic_manual_art", "sol_dia_frio").commit()
        call(a, "refreshCelestialView")
        assertNotSame(old, field(a, "celestialView")); assertSame(surface, a.window.decorView.findViewWithTag<View>("workspace_drag_surface"))
        assertSame(cell, a.window.decorView.findViewWithTag<View>("workspace_cell_0_0"))
        c.pause().stop().destroy()
    }
    @Test fun cachePreservesNativeWeatherDimensionsSharesPixelsAndDoesNotRecycleOnEviction() {
        val r = RuntimeEnvironment.getApplication().resources
        val cache = GraphicResourceCache(r)
        val first = cache.load(R.drawable.lua_noite_fria)
        val again = cache.load(R.drawable.lua_noite_fria)
        assertNotSame(first, again); assertSame(first.bitmap, again.bitmap)
        assertEquals(1942, first.intrinsicWidth); assertEquals(413, first.intrinsicHeight)
        assertTrue(cache.bytes() <= 16 * 1024 * 1024)
        cache.clear(); assertEquals(0, cache.bytes()); assertFalse(first.bitmap.isRecycled)
        val tiny = GraphicResourceCache(r, 1024)
        tiny.load(R.drawable.lua_noite_fria); assertEquals(0, tiny.bytes())
    }
    @Test fun wallpaperDecoderAvoidsDensityUpscalingAndSamplesOnlyWhenLargeEnough() {
        val r = RuntimeEnvironment.getApplication().resources; val cache = GraphicResourceCache(r)
        val full = cache.load(R.drawable.wallpaper_ghost_fire, 1080, 1920)
        assertEquals(1440, full.bitmap.width); assertEquals(2520, full.bitmap.height)
        val thumb = cache.load(R.drawable.wallpaper_ghost_fire, 200, 300)
        assertTrue(thumb.bitmap.width in 200..400); assertTrue(thumb.bitmap.height >= 300)
        assertTrue(cache.bytes() <= 16 * 1024 * 1024)
    }
    @Test fun memoryTrimClearsCachesWithoutInvalidatingDisplayedArtwork() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        val cache = field(a, "graphicCache\$delegate") as Lazy<*>
        val graphics = cache.value as GraphicResourceCache
        val drawable = (field(a, "backgroundImage") as android.widget.ImageView).drawable as BitmapDrawable
        a.onTrimMemory(80)
        assertEquals(0, graphics.bytes()); assertFalse(drawable.bitmap.isRecycled)
        c.pause().stop().destroy()
    }
    private fun fake(a: MainActivity, pkg: String, activity: String = "Main") = ResolveInfo().apply {
        nonLocalizedLabel = "Aplicativo"
        activityInfo = android.content.pm.ActivityInfo().apply {
            packageName = pkg; name = activity
            applicationInfo = android.content.pm.ApplicationInfo(a.applicationInfo).apply { packageName = pkg }
        }
    }
    @Test fun compactDrawerGridHasUniformRowsNoSectionPaddingAndOriginalFrames() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        prefs().edit().putString("pc_fallback_skin", "ghost").commit()
        val items = List(8) { fake(a, "unmapped.$it") }
        val method = MainActivity::class.java.getDeclaredMethod("appGrid", List::class.java,
            Boolean::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val grid = method.invoke(a, items, true, 5) as GridLayout
        assertEquals(0, grid.paddingTop); assertEquals(0, grid.paddingBottom)
        assertEquals(8, grid.childCount)
        for (i in 0 until grid.childCount) {
            val tile = grid.getChildAt(i) as ViewGroup
            assertEquals((88 * a.resources.displayMetrics.density).toInt(), tile.layoutParams.height)
            val frame = tile.getChildAt(0) as android.widget.FrameLayout
            assertNotNull(frame.background)
            val icon = frame.getChildAt(0) as android.widget.ImageView
            assertFalse(icon.drawable is android.graphics.drawable.LayerDrawable)
            val name = tile.getChildAt(1) as TextView
            assertEquals(2, name.maxLines); assertEquals(2, name.minLines)
        }
        val icons = field(a, "iconCache") as android.util.LruCache<*, *>
        assertEquals(4 * 1024 * 1024, icons.maxSize())
        assertTrue(icons.size() <= icons.maxSize())
        a.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertEquals(0, icons.size())
        c.pause().stop().destroy()
    }
    @Test fun folderHandoffCollapsesWindowAndLeavesDataUntouchedUntilDrop() {
        seed(2); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); layout(a)
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.add(fake(a, "pkg", "App0"))
        MainActivity::class.java.getDeclaredMethod("showFolder", String::class.java).apply { isAccessible = true }.invoke(a, "folder:test")
        val dialog = field(a, "folderDialog") as FolderSurface
        layout(a) // Native DragShadowBuilder requires measured source bounds.
        val before = prefs().all.toMap()
        dialog.content.findViewWithTag<View>("folder_drag_home")!!.performClick()
        assertTrue(dialog.content.findViewWithTag<View>("folder_member_0")!!.performLongClick())
        assertTrue(dialog.collapsed)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, prefs().all)
        assertEquals(false, field(a, "pendingDrawerDrag"))
        a.onBackPressed(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, prefs().all)
        assertEquals(2, WorkspaceItemsStore(prefs()).folders().getValue("folder:test").apps.size)
        c.pause().stop().destroy()
    }

    @Test fun drawerCategoriesUseOnePackedGridWithNonoverlappingHeaderAndSearch() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.clear()
        for (i in 0 until 47) apps.add(fake(a, if (i % 2 == 0) "chat.whatsapp$i" else "other.$i").apply {
            nonLocalizedLabel = "${('A'.code + i % 26).toChar()} app $i"
        })
        call(a, "showDrawer"); layout(a)
        fun grid() = a.window.decorView.findViewWithTag<DrawerAppGrid>("drawer_grid")!!
        assertEquals(47, grid().childCount)
        val header = a.window.decorView.findViewWithTag<View>("drawer_header")!!
        val search = a.window.decorView.findViewWithTag<View>("drawer_search")!!
        val bounds = android.graphics.Rect(); header.getGlobalVisibleRect(bounds)
        val searchBounds = android.graphics.Rect(); search.getGlobalVisibleRect(searchBounds)
        assertTrue(bounds.bottom < searchBounds.top)
        val category = views(header).filterIsInstance<TextView>().first { it.text.toString() == "Comunicação" }
        category.performClick(); layout(a)
        assertEquals(24, grid().childCount)
        val before = grid().getChildAt(0)
        val viewport = grid().parent.parent as ScrollView
        viewport.scrollTo(0, 100)
        assertSame(before, grid().getChildAt(0))
        val sameCategory = views(header).filterIsInstance<TextView>().first { it.text.toString() == "Comunicação" }
        sameCategory.performClick()
        assertSame(before, grid().getChildAt(0))
        c.pause().stop().destroy()
    }
    @Test fun retroImporterRemovedButExistingArtworkAndThemeControlsRemain() {
        seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        assertThrows(android.content.pm.PackageManager.NameNotFoundException::class.java) {
            a.packageManager.getActivityInfo(android.content.ComponentName(a.packageName, "com.purplecharm.launcher.RetroImporterActivity"), 0)
        }
        call(a, "settingsMenu")
        val options = field(a, "optionsDialog") as Dialog
        options.window!!.decorView.findViewWithTag<View>("settings_command_0")!!.performClick()
        val theme = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        val texts = views(theme.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
        assertFalse(texts.any { it.contains("Retro 0.4") || it.contains("IMPORTAR APK") })
        assertTrue(texts.any { it.contains("Papéis de parede") })
        theme.dismiss(); c.pause().stop().destroy()
    }

}
