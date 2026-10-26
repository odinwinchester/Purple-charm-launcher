package com.purplecharm.launcher

import android.app.Dialog
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock
import java.util.concurrent.TimeUnit
import org.robolectric.annotation.Config

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkMission2RegressionTest {
    private fun prefs() = RuntimeEnvironment.getApplication()
        .getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)

    private fun registerReadableWallpaper(uri: Uri) {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            output.toByteArray()
        }
        bitmap.recycle()
        shadowOf(RuntimeEnvironment.getApplication().contentResolver)
            .registerInputStreamSupplier(uri) { ByteArrayInputStream(bytes) }
    }

    private fun call(a: MainActivity, name: String, vararg types: Class<*>, args: Array<out Any?> = emptyArray()): Any? =
        MainActivity::class.java.getDeclaredMethod(name, *types).apply { isAccessible = true }.invoke(a, *args)

    private fun field(a: MainActivity, name: String): Any? =
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.get(a)

    private fun set(a: MainActivity, name: String, value: Any?) =
        MainActivity::class.java.getDeclaredField(name).apply { isAccessible = true }.set(a, value)

    private fun fake(a: MainActivity, label: String = "Chrome", packageName: String = "test.purple.app"): ResolveInfo = ResolveInfo().apply {
        nonLocalizedLabel = label
        activityInfo = ActivityInfo().apply {
            this.packageName = packageName
            name = "Main"
            applicationInfo = ApplicationInfo().apply { this.packageName = packageName }
        }
    }

    private fun seedApp(key: String = "test.purple.app/Main", preserveOtherCopies: Boolean = false) {
        prefs().edit().clear().commit()
        val folderId = "folder:duplicate"
        val cells = List(20) { index -> when (index) {
            0 -> key
            1 -> if (preserveOtherCopies) folderId else ""
            else -> ""
        } }
        assertTrue(WorkspaceItemsStore(prefs()).save(
            WorkspaceItems(
                listOf(cells, List(20) { "" }, List(20) { "" }),
                List(4) { index -> if (preserveOtherCopies && index == 0) key else "" },
                if (preserveOtherCopies) mapOf(folderId to WorkspaceFolder("Outra cópia", listOf(key))) else emptyMap()
            )
        ))
    }

    private fun seedAppOnFirstTwoPages(key: String = "test.purple.app/Main") {
        prefs().edit().clear().commit()
        val appCell = List(20) { index -> if (index == 0) key else "" }
        val emptyPage = List(20) { "" }
        assertTrue(WorkspaceItemsStore(prefs()).save(
            WorkspaceItems(listOf(appCell, appCell, emptyPage), List(4) { "" }, emptyMap())
        ))
    }

    private fun layout(a: MainActivity, width: Int = 1080, height: Int = 1920) {
        val root = a.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, width, height)
    }

    private fun send(a: MainActivity, action: Int, x: Float, y: Float, time: Long) {
        val event = MotionEvent.obtain(0, time, action, x, y, 0)
        try { a.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun homeAppLongPressShowsContextMenuWithoutStartingDragAndRemovalOnlyRemovesShortcut() {
        seedApp(preserveOtherCopies = true)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        val app = fake(a)
        apps.add(app)
        call(a, "showHome")
        val tile = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        assertTrue(tile.performLongClick())
        assertFalse(field(a, "dragging") as Boolean)
        val menu = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        val labels = (0 until menu.listView.adapter.count).map { menu.listView.adapter.getItem(it).toString() }
        assertEquals(listOf("Mover para outra página", "Mover por arraste / criar pasta", "Remover atalho da tela inicial"), labels)
        menu.listView.performItemClick(null, 2, menu.listView.adapter.getItemId(2))
        val confirmation = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue(shadowOf(confirmation).message.toString().contains("continuará instalado"))
        confirmation.getButton(-1).performClick()
        shadowOf(RuntimeEnvironment.getApplication().mainLooper).idle()
        val afterRemoval = call(a, "workspaceItems") as WorkspaceItems
        assertFalse(afterRemoval.pages.flatten().contains("test.purple.app/Main"))
        assertEquals("test.purple.app/Main", afterRemoval.dock[0])
        assertEquals(listOf("test.purple.app/Main"), afterRemoval.folders.getValue("folder:duplicate").apps)
        assertNull(shadowOf(a).nextStartedActivity)
        controller.pause().stop().destroy()
    }

    @Test fun pageOnlyRemovalRejectsWrongCellAndPreservesMatchingDockAndFolderCopies() {
        val key = "pkg/Test"
        val folderId = "folder:copy"
        val state = WorkspaceItems(
            listOf(listOf(key, "", "", "") + List(16) { "" }, List(20) { "" }, List(20) { "" }),
            listOf(key, "", "", ""), mapOf(folderId to WorkspaceFolder("Preservar", listOf(key)))
        )
        val copy = state.copy(pages = listOf(listOf(key, folderId, "", "") + List(16) { "" }, state.pages[1], state.pages[2]))
        assertNull(WorkspaceEdits.removeFromPage(copy, key, 0, 1))
        val updated = WorkspaceEdits.removeFromPage(copy, key, 0, 0)!!
        assertEquals("", updated.pages[0][0])
        assertEquals(key, updated.dock[0])
        assertEquals(listOf(key), updated.folders.getValue(folderId).apps)
    }

    @Test fun longPressMoveMenuHasExplicitDragAndPageFlows() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.add(fake(a))
        call(a, "showHome")
        val tile = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        tile.performLongClick()
        val menu = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals("Mover para outra página", menu.listView.adapter.getItem(0))
        assertEquals("Mover por arraste / criar pasta", menu.listView.adapter.getItem(1))
        assertFalse(field(a, "dragging") as Boolean)
        menu.listView.performItemClick(null, 0, menu.listView.adapter.getItemId(0))
        val pages = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(2, pages.listView.adapter.count)
        pages.listView.performItemClick(null, 0, pages.listView.adapter.getItemId(0))
        val afterMove = call(a, "workspaceItems") as WorkspaceItems
        assertEquals("test.purple.app/Main", afterMove.pages[1][0])
        assertFalse(afterMove.pages[0].contains("test.purple.app/Main"))
        controller.pause().stop().destroy()
    }

    @Test fun dragStartsOnlyAfterExplicitContextMenuChoiceAndCancelPreservesWorkspace() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.add(fake(a))
        call(a, "showHome"); layout(a)
        val before = prefs().all.toMap()
        val tile = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        tile.performLongClick()
        assertFalse(field(a, "dragging") as Boolean)
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().listView
            .performItemClick(null, 1, 1L)
        assertTrue(field(a, "dragging") as Boolean)
        assertNotNull(field(a, "dragSession"))
        assertEquals(before, prefs().all)
        a.onBackPressed()
        org.robolectric.Shadows.shadowOf(org.robolectric.RuntimeEnvironment.getApplication().mainLooper).idle()
        assertFalse(field(a, "dragging") as Boolean)
        assertEquals(before, prefs().all)
        controller.pause().stop().destroy()
    }

    @Test fun swipeClassifierRejectsDiagonalsAndSelectsOnlyOneAxis() {
        assertEquals(WorkspaceSwipe.HORIZONTAL, WorkspaceGestureClassifier.classify(-150f, 40f, 65f))
        assertEquals(WorkspaceSwipe.VERTICAL, WorkspaceGestureClassifier.classify(20f, -120f, 65f))
        assertEquals(WorkspaceSwipe.NONE, WorkspaceGestureClassifier.classify(110f, 90f, 65f))
        assertEquals(WorkspaceSwipe.NONE, WorkspaceGestureClassifier.classify(60f, 0f, 65f))
    }

    @Test fun horizontalSwipeChangesExactlyOnePageAndDiagonalDoesNothing() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        send(a, MotionEvent.ACTION_DOWN, 200f, 400f, 1)
        send(a, MotionEvent.ACTION_MOVE, 70f, 420f, 30)
        send(a, MotionEvent.ACTION_UP, 70f, 420f, 60)
        assertEquals(1, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        send(a, MotionEvent.ACTION_DOWN, 900f, 800f, 100)
        send(a, MotionEvent.ACTION_MOVE, 1000f, 890f, 130)
        send(a, MotionEvent.ACTION_UP, 1000f, 890f, 160)
        assertEquals(1, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertNull(field(a, "optionsDialog"))
        controller.pause().stop().destroy()
    }

    @Test fun horizontalSwipeInBothDirectionsConsumesReleaseWithoutClickingOrOpeningMenus() {
        seedAppOnFirstTwoPages()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.add(fake(a))
        call(a, "showHome"); layout(a)

        val first = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        val firstPosition = IntArray(2); first.getLocationOnScreen(firstPosition)
        val x = firstPosition[0] + first.width / 2f
        val y = firstPosition[1] + first.height / 2f
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_MOVE, x - 140, y + 4, 30)
        send(a, MotionEvent.ACTION_UP, x - 140, y + 4, 60)
        assertEquals(1, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertNull(shadowOf(a).nextStartedActivity)
        assertNull(field(a, "optionsDialog"))

        // Exercise the ACTION_UP-only recognition path in the reverse direction.
        val second = a.window.decorView.findViewWithTag<View>("workspace_cell_1_0")!!
        val secondPosition = IntArray(2); second.getLocationOnScreen(secondPosition)
        val sx = secondPosition[0] + second.width / 2f
        val sy = secondPosition[1] + second.height / 2f
        send(a, MotionEvent.ACTION_DOWN, sx, sy, 100)
        send(a, MotionEvent.ACTION_UP, sx + 140, sy + 4, 160)
        assertEquals(0, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertNull(shadowOf(a).nextStartedActivity)
        assertNull(field(a, "optionsDialog"))
        controller.pause().stop().destroy()
    }

    @Test fun normalTapOnDestinationPageStillOpensItsAppAfterSwipeRelease() {
        seedAppOnFirstTwoPages()
        // This assertion concerns touch handling after navigation, independent of ViewFlipper animation timing.
        prefs().edit().putString("pc_transition", "none").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.add(fake(a))
        call(a, "showHome"); layout(a)
        val source = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        val sourcePos = IntArray(2); source.getLocationInWindow(sourcePos)
        val x = sourcePos[0] + source.width / 2f; val y = sourcePos[1] + source.height / 2f
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_MOVE, x - 140, y, 30)
        send(a, MotionEvent.ACTION_UP, x - 140, y, 60)
        assertNull(shadowOf(a).nextStartedActivity)
        val destination = a.window.decorView.findViewWithTag<View>("workspace_cell_1_0")!!
        val destPos = IntArray(2); destination.getLocationInWindow(destPos)
        val tx = destPos[0] + destination.width / 2f; val ty = destPos[1] + destination.height / 2f
        send(a, MotionEvent.ACTION_DOWN, tx, ty, 100)
        send(a, MotionEvent.ACTION_UP, tx, ty, 110)
        assertEquals("test.purple.app", shadowOf(a).nextStartedActivity?.component?.packageName)
        controller.pause().stop().destroy()
    }

    @Test fun horizontalSwipeBreaksPendingDoubleTapSequenceWithoutOpeningMenuOnNextTap() {
        seedApp()
        prefs().edit().putString("pc_double_tap", "settings").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); call(a, "showHome"); layout(a)
        val empty = a.window.decorView.findViewWithTag<View>("workspace_cell_0_5")!!
        val pos = IntArray(2); empty.getLocationOnScreen(pos)
        val x = pos[0] + empty.width / 2f; val y = pos[1] + empty.height / 2f
        ShadowSystemClock.advanceBy(1000, TimeUnit.MILLISECONDS)
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_UP, x, y, 10)
        assertNull(field(a, "optionsDialog"))
        ShadowSystemClock.advanceBy(100, TimeUnit.MILLISECONDS)

        send(a, MotionEvent.ACTION_DOWN, x, y, 20)
        send(a, MotionEvent.ACTION_MOVE, x - 140, y + 4, 40)
        send(a, MotionEvent.ACTION_UP, x - 140, y + 4, 60)
        assertEquals(1, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertEquals(0L, field(a, "lastTapAt"))
        assertNull(field(a, "optionsDialog"))

        send(a, MotionEvent.ACTION_DOWN, x, y, 70)
        send(a, MotionEvent.ACTION_UP, x, y, 80)
        assertNull(field(a, "optionsDialog"))
        controller.pause().stop().destroy()
    }

    @Test fun configuredDoubleTapStillRunsWithoutAnInterveningSwipe() {
        seedApp()
        prefs().edit().putString("pc_double_tap", "settings").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); call(a, "showHome"); layout(a)
        val empty = a.window.decorView.findViewWithTag<View>("workspace_cell_0_5")!!
        val pos = IntArray(2); empty.getLocationOnScreen(pos)
        val x = pos[0] + empty.width / 2f; val y = pos[1] + empty.height / 2f
        ShadowSystemClock.advanceBy(1000, TimeUnit.MILLISECONDS)
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_UP, x, y, 10)
        ShadowSystemClock.advanceBy(100, TimeUnit.MILLISECONDS)
        send(a, MotionEvent.ACTION_DOWN, x, y, 20)
        send(a, MotionEvent.ACTION_UP, x, y, 30)
        assertTrue((field(a, "optionsDialog") as Dialog).isShowing)
        controller.pause().stop().destroy()
    }

    @Test fun horizontalSwipeDoesNotStealAnActiveWeatherWidgetResizeGesture() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); call(a, "showHome"); layout(a)
        val editor = field(a, "widgetEditor") as WeatherResizeLayout
        editor.beginEditing()
        assertTrue(field(a, "widgetResizeActive") as Boolean)
        val bounds = android.graphics.Rect()
        assertTrue(editor.getGlobalVisibleRect(bounds))
        val x = bounds.centerX().toFloat(); val y = bounds.centerY().toFloat()
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_MOVE, x + 130, y + 2, 30)
        send(a, MotionEvent.ACTION_UP, x + 130, y + 2, 60)
        assertEquals(0, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertTrue(editor.isEditing)
        assertTrue(field(a, "widgetResizeActive") as Boolean)
        editor.endEditing()
        controller.pause().stop().destroy()
    }

    @Test fun configuredVerticalGestureWorksFromAnIconAndEmptyAreaWithoutASecondAction() {
        seedApp(); prefs().edit().putString("pc_swipe_up", "settings").commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        val tile = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        val location = IntArray(2); tile.getLocationOnScreen(location)
        val x = location[0] + tile.width / 2f; val y = location[1] + tile.height / 2f
        send(a, MotionEvent.ACTION_DOWN, x, y, 1)
        send(a, MotionEvent.ACTION_MOVE, x + 2, y - 130, 30)
        send(a, MotionEvent.ACTION_UP, x + 2, y - 130, 60)
        assertTrue((field(a, "optionsDialog") as Dialog).isShowing)
        (field(a, "optionsDialog") as Dialog).dismiss()
        val width = a.window.decorView.width.toFloat()
        send(a, MotionEvent.ACTION_DOWN, width - 4, 800f, 100)
        send(a, MotionEvent.ACTION_MOVE, width - 4, 660f, 130)
        send(a, MotionEvent.ACTION_UP, width - 4, 660f, 160)
        assertTrue((field(a, "optionsDialog") as Dialog).isShowing)
        assertEquals(0, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        controller.pause().stop().destroy()
    }

    @Test fun preferredHomePagePersistsBackNavigationAndPageDeletionRemapping() {
        seedApp()
        assertTrue(HomePageSelection.write(prefs(), 2, 3))
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var a = controller.get()
        assertEquals(2, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        call(a, "changePage", Int::class.javaPrimitiveType!!, args = arrayOf(0))
        a.onBackPressed()
        assertEquals(2, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(); a = controller.get()
        assertEquals(2, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        assertEquals(1, HomePageSelection.afterRemoval(2, 0, 4))
        assertEquals(2, HomePageSelection.afterRemoval(2, 2, 4))
        assertEquals(0, HomePageSelection.afterRemoval(0, 0, 4))
        controller.pause().stop().destroy()
    }

    @Test fun pageManagerCanMakeEveryExistingPageThePersistentHomePage() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "managePages")
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertEquals(5, dialog.listView.adapter.count)
        dialog.listView.performItemClick(null, 3, dialog.listView.adapter.getItemId(3))
        assertEquals(2, HomePageSelection.read(prefs(), 3))
        assertEquals(2, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        controller.pause().stop().destroy()
    }

    @Test fun deletingAnEmptyPageRemapsHomeWeatherAndExternalWidgetPagesAtomically() {
        prefs().edit().clear().commit()
        val pages = List(4) { List(20) { "" } }
        assertTrue(WorkspaceItemsStore(prefs()).save(WorkspaceItems(pages, List(4) { "" }, emptyMap())))
        assertTrue(HomePageSelection.write(prefs(), 3, 4))
        prefs().edit().putInt("weather_widget_page", 3).commit()
        assertTrue(HostedWidgetStore(prefs()).save(listOf(HostedWidget(77, "provider.test/Widget", 3, 1, 100, 0))))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "changePage", Int::class.javaPrimitiveType!!, args = arrayOf(1))
        call(a, "managePages")
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, 5, dialog.listView.adapter.getItemId(5))
        assertEquals(3, prefs().getInt("layout_page_count", -1))
        assertEquals(2, HomePageSelection.read(prefs(), 3))
        assertEquals(2, prefs().getInt("weather_widget_page", -1))
        assertEquals(2, HostedWidgetStore(prefs()).read().single().page)
        controller.pause().stop().destroy()
    }

    @Test fun pageWithVisibleWeatherOrHostedWidgetCannotBeDeleted() {
        prefs().edit().clear().commit()
        assertTrue(WorkspaceItemsStore(prefs()).save(WorkspaceItems(
            List(3) { List(20) { "" } }, List(4) { "" }, emptyMap()
        )))
        assertTrue(HostedWidgetStore(prefs()).save(listOf(HostedWidget(78, "provider.test/Widget", 1, 1, 100, 0))))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "changePage", Int::class.javaPrimitiveType!!, args = arrayOf(1))
        call(a, "managePages")
        val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        dialog.listView.performItemClick(null, 4, dialog.listView.adapter.getItemId(4))
        assertEquals(3, prefs().getInt("layout_page_count", -1))
        assertEquals(1, HostedWidgetStore(prefs()).read().single().page)
        controller.pause().stop().destroy()
    }

    @Test fun homeIconGridGrowsForScaledLabelsWithoutClippingTileChildrenOrDock() {
        seedApp("com.android.chrome/Main")
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        a.resources.configuration.fontScale = 1.5f
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        apps.removeAll { it.activityInfo.packageName == "com.android.chrome" && it.activityInfo.name == "Main" }
        apps.add(fake(a, "An Extremely Long Purple Charm Application Name", "com.android.chrome"))
        call(a, "showHome"); layout(a)
        val tile = a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        assertTrue(tile.height >= (71 * a.resources.displayMetrics.density).toInt())
        val icon = (tile as ViewGroup).getChildAt(0) as ViewGroup
        val image = icon.getChildAt(0) as ImageView
        assertEquals(ImageView.ScaleType.FIT_CENTER, image.scaleType)
        val name = (tile as ViewGroup).getChildAt(1) as TextView
        assertEquals(3, name.maxLines)
        assertEquals("An Extremely Long Purple Charm Application Name", name.text.toString())
        assertTrue(name.bottom <= tile.height)
        val dock = a.window.decorView.findViewWithTag<View>("dock_drawer")!!
        val tileLocation = IntArray(2); val dockLocation = IntArray(2)
        tile.getLocationOnScreen(tileLocation); dock.getLocationOnScreen(dockLocation)
        assertTrue(tileLocation[1] + tile.height <= dockLocation[1])
        controller.pause().stop().destroy()
    }

    @Test fun drawerReusesItsRenderedTreeAndAppCatalogUntilPackageChanges() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "showDrawer")
        val firstHost = field(a, "cachedDrawerHost")
        assertNotNull(firstHost)
        @Suppress("UNCHECKED_CAST")
        val apps = field(a, "apps") as MutableList<ResolveInfo>
        val extra = fake(a, "Cached catalog marker"); apps.add(extra)
        call(a, "loadApps")
        assertTrue(apps.any { it.activityInfo.packageName == "test.purple.app" })
        call(a, "showHome"); call(a, "showDrawer")
        assertSame(firstHost, field(a, "homeHost"))
        val receiver = field(a, "packageChanges") as BroadcastReceiver
        receiver.onReceive(a, Intent(Intent.ACTION_PACKAGE_ADDED).setData(android.net.Uri.parse("package:other.app")))
        assertNotSame(firstHost, field(a, "cachedDrawerHost"))
        controller.pause().stop().destroy()
    }

    @Test fun royalEyeWallpaperIsListedPreviewedAppliedAndPersisted() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val options = field(a, "wallpaperOptions") as List<Triple<String, String, Int>>
        assertEquals(setOf("floral", "eye_crystal", "eye_royal", "ghost_fire"), options.map { it.second }.toSet())
        val royal = options.first { it.second == "eye_royal" }
        call(a, "wallpaperPreview", String::class.java, java.lang.Integer::class.java,
            Uri::class.java, args = arrayOf(royal.second, royal.third, null))
        val preview = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(preview.window!!.decorView.findViewWithTag<View>("wallpaper_preview_apply"))
        preview.window!!.decorView.findViewWithTag<View>("wallpaper_preview_apply")!!.performClick()
        val destination = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        destination.window!!.decorView.findViewWithTag<View>("wallpaper_destination_1")!!.performClick()
        assertEquals("eye_royal", prefs().getString("pc_wallpaper_choice", null))
        assertNotNull((field(a, "backgroundImage") as ImageView).drawable)
        controller.pause().stop().destroy()
    }

    @Test fun previouslySelectedGalleryWallpaperRemainsAvailableAndItsUriIsPreserved() {
        seedApp()
        val uri = Uri.parse("content://purple-test/wallpaper")
        registerReadableWallpaper(uri)
        prefs().edit().putString("wallpaper_uri", uri.toString()).commit()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "wallpaperMenu")
        val menu = org.robolectric.shadows.ShadowDialog.getLatestDialog()
        assertNotNull(menu.window!!.decorView.findViewWithTag<View>("wallpaper_option_gallery"))
        call(a, "applyWallpaperDestination", String::class.java, java.lang.Integer::class.java,
            android.net.Uri::class.java, Int::class.javaPrimitiveType!!,
            args = arrayOf("gallery", null, uri, 1))
        assertEquals(uri.toString(), prefs().getString("wallpaper_uri", null))
        assertEquals("gallery", prefs().getString("pc_wallpaper_choice", null))
        controller.pause().stop().destroy()
    }

    @Test fun launcherAndAndroidSettingsRemainAccessibleFromHomeEditor() {
        seedApp()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        call(a, "enterEditMode")
        val editor = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
        assertTrue((0 until editor.listView.adapter.count).map { editor.listView.adapter.getItem(it).toString() }
            .containsAll(listOf("Configurações", "Configurações do Android")))
        editor.listView.performItemClick(null, 5, editor.listView.adapter.getItemId(5))
        assertEquals(android.provider.Settings.ACTION_SETTINGS, shadowOf(a).nextStartedActivity.action)
        controller.pause().stop().destroy()
    }
}
