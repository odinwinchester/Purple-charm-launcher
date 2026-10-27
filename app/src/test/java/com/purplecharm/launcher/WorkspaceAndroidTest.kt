package com.purplecharm.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import android.view.DragEvent
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkspaceAndroidTest {
    private val id = "folder:test"
    private fun state() = WorkspaceItems(
        listOf(listOf(id) + List(19) { "" }, List(20) { "" }, List(20) { "" }),
        List(4) { "" }, mapOf(id to WorkspaceFolder("Pasta ☾", listOf("pkg/A", "pkg/B"))))
    private fun prefs(name: String) = RuntimeEnvironment.getApplication().getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun seed(): SharedPreferences = prefs("purple_charm_favorites").also {
        it.edit().clear().commit(); assertTrue(WorkspaceItemsStore(it).save(state()))
    }
    @Test fun atomicStoreReloadPreservesFoldersCellsDockAndUnrelatedAppearance() {
        val p = prefs("reload"); p.edit().clear().putString("pc_wallpaper_choice", "gallery").putString("weather", "keep").commit()
        val s = state(); assertTrue(WorkspaceItemsStore(p).save(s))
        val reloaded = WorkspaceItemsStore(p)
        assertEquals(s.folders, reloaded.folders())
        assertEquals(s.pages[0], p.getString("layout_page_cells_0", "")!!.split('|'))
        assertEquals(s.dock, p.getString("layout_dock_slots", "")!!.split('|'))
        assertEquals("gallery", p.getString("pc_wallpaper_choice", null)); assertEquals("keep", p.getString("weather", null))
        val snap = WorkspaceLayoutSafety(p).capture()
        assertTrue(reloaded.save(WorkspaceEdits.rename(s, id, "Novo")!!))
        assertTrue(WorkspaceLayoutSafety(p).restore(snap)); assertEquals(s.folders, reloaded.folders())
    }
    @Test fun failedDiskCommitRestoresPreEditLayoutIncludingFolderMetadata() {
        val p = prefs("failed"); p.edit().clear().commit(); val s = state()
        assertTrue(WorkspaceItemsStore(p).save(s)); val before = p.all.toMap()
        var fail = true
        val proxy = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            val value = method.invoke(p, *(args ?: emptyArray()))
            if (method.name != "edit") value else {
                val editor = value as SharedPreferences.Editor
                var wrapped: Any? = null
                wrapped = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, editMethod, editArgs ->
                    val result = editMethod.invoke(editor, *(editArgs ?: emptyArray()))
                    if (editMethod.name == "commit" && fail) { fail = false; false }
                    else if (result is SharedPreferences.Editor) wrapped else result
                }
                wrapped
            }
        } as SharedPreferences
        assertFalse(WorkspaceItemsStore(proxy).save(WorkspaceEdits.rename(s, id, "Não salvar")!!))
        assertEquals(before, p.all)
    }
    private fun set(activity: MainActivity, field: String, value: Any?) {
        MainActivity::class.java.getDeclaredField(field).apply { isAccessible = true }.set(activity, value)
    }
    private fun current(activity: MainActivity): WorkspaceItems = MainActivity::class.java.getDeclaredMethod("workspaceItems")
        .apply { isAccessible = true }.invoke(activity) as WorkspaceItems
    private fun event(action: Int, session: Any?, key: String = id, mime: String = "application/vnd.purplecharm.workspace-item", x: Float = 1f, y: Float = 1f): DragEvent {
        val data = ClipData("test", arrayOf(mime), ClipData.Item(key))
        val method = DragEvent::class.java.declaredMethods.first {
            it.name == "obtain" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType &&
                it.parameterTypes.contains(ClipData::class.java)
        }
        method.isAccessible = true
        var coordinate = 0
        val arguments = method.parameterTypes.map { type ->
            when (type) {
                Int::class.javaPrimitiveType -> action
                Float::class.javaPrimitiveType -> if (coordinate++ == 0) x else y
                Boolean::class.javaPrimitiveType -> false
                ClipData::class.java -> data
                ClipDescription::class.java -> data.description
                Any::class.java -> session
                else -> null // platform drag-permission binder, unused for an in-app drag
            }
        }.toTypedArray()
        return method.invoke(null, *arguments) as DragEvent
    }
    @Test fun actualCellDropMovesFolderOnceAndRejectsForeignPayload() {
        val p = seed()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val target = activity.window.decorView.findViewWithTag<View>("workspace_cell_0_1")!!
        val session = WorkspaceDragSession(id, current(activity))
        set(activity, "dragSession", session); set(activity, "dragging", true)
        assertFalse(target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, null, mime = ClipDescription.MIMETYPE_TEXT_PLAIN)))
        assertTrue(target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session)))
        assertTrue(target.dispatchDragEvent(event(DragEvent.ACTION_DROP, session)))
        assertFalse(target.dispatchDragEvent(event(DragEvent.ACTION_DROP, session)))
        val cells = p.getString("layout_page_cells_0", "")!!.split('|')
        assertEquals("", cells[0]); assertEquals(id, cells[1]); assertEquals(1, cells.count { it == id })
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
    @Test fun canceledActualDragLeavesPreferencesUnchangedAndFolderStillRendersAfterRecreation() {
        val p = seed()
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get()
        val before = p.all.toMap()
        val target = activity.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        assertTrue(target.isLongClickable)
        val session = WorkspaceDragSession(id, current(activity))
        set(activity, "dragSession", session); set(activity, "dragging", true)
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session))
        target.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before, p.all)
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(); activity = controller.get()
        val folder = activity.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        assertTrue(folder.contentDescription.toString().contains("Pasta ☾"))
        assertTrue(folder.performClick())
        controller.pause().stop().destroy()
    }
    @Test fun actualLongPressConsumesGestureAndBackCancelDoesNotWriteOrFinishActivity() {
        val p = seed()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val tile = activity.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!
        val before = p.all.toMap()
        assertTrue(tile.performLongClick())
        assertEquals(before, p.all)
        val session = WorkspaceDragSession(id, current(activity))
        set(activity, "dragSession", session); set(activity, "dragging", true); set(activity, "dragSource", tile)
        activity.onBackPressed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(session.finished); assertEquals(before, p.all)
        assertFalse(activity.isFinishing)
        controller.pause().stop().destroy()
    }

    @Test fun dismissingFolderDuringDragCancelsSessionWithoutRemovingMember() {
        val p = seed()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val before = p.all.toMap()
        activity.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!.performClick()
        val dialog = MainActivity::class.java.getDeclaredField("folderDialog").apply { isAccessible = true }
            .get(activity) as FolderSurface
        val member = dialog.content.findViewWithTag<View>("folder_member_0")!!
        val session = WorkspaceDragSession("pkg/A", current(activity))
        set(activity, "dragSession", session); set(activity, "dragging", true); set(activity, "dragSource", member)
        dialog.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(session.finished); assertFalse(dialog.isShowing)
        assertEquals(before, p.all)
        assertEquals(listOf("pkg/A", "pkg/B"), WorkspaceItemsStore(p).folders().getValue(id).apps)
        controller.pause().stop().destroy()
    }

    @Test fun reducingColumnsDoesNotTruncateFolderOrPreventFuturePersistence() {
        val p = seed()
        val s = state().copy(pages = listOf(List(29) { "" } + id, List(30) { "" }, List(30) { "" }))
        assertTrue(WorkspaceItemsStore(p).save(s))
        p.edit().putString("pc_columns", "6").commit()
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause().stop().destroy()
        p.edit().putString("pc_columns", "4").commit()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val reloaded = current(activity)
        assertEquals(id, reloaded.pages[0][29]); assertTrue(reloaded.valid())
        assertNotNull(activity.window.decorView.findViewWithTag<View>("workspace_cell_0_29"))
        assertTrue(WorkspaceItemsStore(p).save(WorkspaceEdits.rename(reloaded, id, "Ainda aqui")!!))
        assertEquals("Ainda aqui", WorkspaceItemsStore(p).folders().getValue(id).name)
        controller.pause().stop().destroy()
    }
    @Test fun explicitOrganizationResetDoesNotLeaveOrphanFoldersOrChangeWallpaper() {
        val p = seed()
        p.edit().putString("pc_wallpaper_choice", "gallery").putBoolean("gothic_moon_visible", true).commit()
        assertTrue(WorkspaceItemsStore(p).reset())
        assertTrue(WorkspaceItemsStore(p).folders().isEmpty())
        assertFalse(p.contains("layout_page_cells_0")); assertFalse(p.contains("layout_dock_slots"))
        assertEquals("gallery", p.getString("pc_wallpaper_choice", null))
        assertTrue(p.getBoolean("gothic_moon_visible", false))
    }

    private fun layout(activity: MainActivity) {
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
    }
    @Test fun stableSurfaceCrossesHiddenPagesAndCommitsMeasuredDestinationOnceAfterRecreation() {
        val p = seed()
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get(); layout(activity)
        val surface = activity.window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")!!
        val session = WorkspaceDragSession(id, current(activity))
        val before = p.getString("layout_page_cells_0", null)
        set(activity, "dragSession", session); set(activity, "dragging", true)
        assertTrue(surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session)))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = surface.width - 1f))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(349))
        assertEquals(0, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(activity))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1))
        assertEquals(1, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(activity))
        assertEquals(before, p.getString("layout_page_cells_0", null))
        // Return to the center stops repeated paging; target children never received DRAG_STARTED.
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = surface.width / 2f))
        layout(activity)
        val tile = activity.window.decorView.findViewWithTag<View>("workspace_cell_1_6")!!
        val at = IntArray(2); val base = IntArray(2)
        tile.getLocationOnScreen(at); surface.getLocationOnScreen(base)
        val x = at[0] - base[0] + tile.width / 2f; val y = at[1] - base[1] + tile.height / 2f
        assertTrue(surface.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, x = x, y = y)))
        assertFalse(surface.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, x = x, y = y)))
        assertEquals(id, current(activity).pages[1][6]); assertEquals("", current(activity).pages[0][0])
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
        controller = Robolectric.buildActivity(MainActivity::class.java).setup(); activity = controller.get()
        assertEquals(id, current(activity).pages[1][6])
        assertEquals(1, current(activity).pages.flatten().count { it == id })
        controller.pause().stop().destroy()
    }
    @Test fun edgeExitAndBackCancelStopTimersWithoutWritingOrDuplicating() {
        val p = seed(); val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        val before = p.all.toMap()
        val surface = a.window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")!!
        val session = WorkspaceDragSession(id, current(a))
        set(a, "dragSession", session); set(a, "dragging", true)
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = surface.width - 1f))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_EXITED, session))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(0, MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }.getInt(a))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = surface.width - 1f))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350))
        a.onBackPressed(); shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        assertTrue(session.finished); assertEquals(before, p.all)
        assertEquals(1, current(a).pages.flatten().count { it == id })
        controller.pause().stop().destroy()
    }
    @Test fun lastFolderMemberRemovalPersistsFreeCellAndPreservesOtherFolderAndAppearance() {
        val p = seed(); val s = state().copy(
            pages = listOf(listOf(id, "folder:other") + List(18) { "" }, List(20) { "" }, List(20) { "" }),
            folders = mapOf(id to WorkspaceFolder("Último", listOf("pkg/A")),
                "folder:other" to WorkspaceFolder("Outra", listOf("pkg/C"))))
        assertTrue(WorkspaceItemsStore(p).save(s))
        p.edit().putString("pc_wallpaper_choice", "gallery").commit()
        assertTrue(WorkspaceItemsStore(p).save(WorkspaceEdits.removeApp(s, id, "pkg/A")!!))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val reloaded = current(controller.get())
        assertEquals("", reloaded.pages[0][0]); assertFalse(id in reloaded.folders)
        assertEquals(s.folders["folder:other"], reloaded.folders["folder:other"])
        assertEquals(s.dock, reloaded.dock); assertEquals("gallery", p.getString("pc_wallpaper_choice", null))
        controller.pause().stop().destroy()
    }

    @Test fun alphabetUsesCompactCenteredBandAndContinuousWideTouchRail() {
        seed(); val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        @Suppress("UNCHECKED_CAST")
        val apps = MainActivity::class.java.getDeclaredField("apps").apply { isAccessible = true }.get(a) as MutableList<android.content.pm.ResolveInfo>
        apps.clear()
        for (letter in 'A'..'Z') repeat(10) { item -> apps.add(android.content.pm.ResolveInfo().apply {
            nonLocalizedLabel = "$letter app $item"
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = "test.$letter.$item"; name = "Main"
                applicationInfo = android.content.pm.ApplicationInfo(a.applicationInfo).apply { packageName = "test.$letter" }
            }
        }) }
        MainActivity::class.java.getDeclaredMethod("showDrawer").apply { isAccessible = true }.invoke(a)
        layout(a)
        val rail = a.window.decorView.findViewWithTag<android.widget.LinearLayout>("drawer_alphabet")!!
        val density = a.resources.displayMetrics.density
        assertEquals(26, rail.childCount); assertTrue(rail.width >= (32 * density).toInt())
        val first = rail.getChildAt(0); val last = rail.getChildAt(25)
        assertEquals(0, first.top)
        assertTrue(kotlin.math.abs(first.height * 26 - rail.height) <= 26)
        assertTrue(last.bottom <= rail.height)
        val event = android.view.MotionEvent.obtain(0, 1, android.view.MotionEvent.ACTION_DOWN,
            rail.width / 2f, last.top + last.height / 2f, 0)
        assertTrue(rail.dispatchTouchEvent(event)); event.recycle()
        val grid = a.window.decorView.findViewWithTag<DrawerAppGrid>("drawer_grid")!!
        val scroll = grid.parent.parent as android.widget.ScrollView
        assertTrue(scroll.scrollY > 0)
        val up = android.view.MotionEvent.obtain(0, 2, android.view.MotionEvent.ACTION_UP, 1f, 1f, 0)
        assertTrue(rail.dispatchTouchEvent(up)); up.recycle()
        controller.pause().stop().destroy()
    }

    @Test fun heldEdgeRepeatsWithinBoundsAndReversesWithoutRebuildingWorkspace() {
        val p = seed(); val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        val surface = a.window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")!!
        val session = WorkspaceDragSession(id, current(a)); val before = p.all.toMap()
        set(a, "dragSession", session); set(a, "dragging", true)
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = surface.width - 1f))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(2000))
        val field = MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }
        assertEquals(2, field.getInt(a))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = 1f))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350))
        assertEquals(1, field.getInt(a))
        // Handler scheduling is a minimum delay; rendering may advance the simulated clock.
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(700))
        assertEquals(0, field.getInt(a)); assertEquals(before, p.all)
        assertSame(surface, a.window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface"))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle()
        controller.pause().stop().destroy()
    }
    @Test fun widgetRegionAndForeignSurfaceDropsCannotMoveWorkspaceItems() {
        val p = seed(); val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        val surface = a.window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")!!
        val session = WorkspaceDragSession(id, current(a)); val before = p.all.toMap()
        set(a, "dragSession", session); set(a, "dragging", true)
        assertFalse(surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, null)))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session))
        assertFalse(surface.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, x = surface.width / 2f, y = 1f)))
        surface.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle(); assertEquals(before, p.all)
        controller.pause().stop().destroy()
    }

    @Test fun nativeRootRoutesDragToStableSurfaceAfterPageChange() {
        seed(); val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get(); layout(a)
        val root = a.window.decorView
        val surface = root.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")!!
        val session = WorkspaceDragSession(id, current(a))
        set(a, "dragSession", session); set(a, "dragging", true)
        val base = IntArray(2); val origin = IntArray(2)
        root.getLocationOnScreen(base); surface.getLocationOnScreen(origin)
        assertTrue(root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session)))
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session,
            x = origin[0] - base[0] + surface.width - 1f, y = origin[1] - base[1] + surface.height / 2f))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(350)); layout(a)
        val field = MainActivity::class.java.getDeclaredField("pageIndex").apply { isAccessible = true }
        assertEquals(1, field.getInt(a))
        val target = root.findViewWithTag<View>("workspace_cell_1_2")!!
        val at = IntArray(2); target.getLocationOnScreen(at)
        val x = at[0] - base[0] + target.width / 2f; val y = at[1] - base[1] + target.height / 2f
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, x = x, y = y))
        assertTrue(root.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, x = x, y = y)))
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(id, current(a).pages[1][2]); assertEquals("", current(a).pages[0][0])
        controller.pause().stop().destroy()
    }
    @Test fun removingLastMemberClosesOpenFolderAndReleasesDockSlot() {
        val p = seed(); val s = state().copy(
            pages = List(3) { List(20) { "" } }, dock = listOf(id, "", "", ""),
            folders = mapOf(id to WorkspaceFolder("Última", listOf("pkg/A"))))
        assertTrue(WorkspaceItemsStore(p).save(s))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = controller.get()
        MainActivity::class.java.getDeclaredMethod("showFolder", String::class.java).apply { isAccessible = true }.invoke(a, id)
        val dialog = MainActivity::class.java.getDeclaredField("folderDialog").apply { isAccessible = true }.get(a) as FolderSurface
        assertTrue(dialog.isShowing)
        val edited = WorkspaceEdits.removeApp(current(a), id, "pkg/A")!!
        assertEquals(true, MainActivity::class.java.getDeclaredMethod("saveWorkspaceEdit", WorkspaceItems::class.java)
            .apply { isAccessible = true }.invoke(a, edited))
        assertFalse(dialog.isShowing); assertTrue(current(a).folders.isEmpty())
        assertEquals(List(4) { "" }, current(a).dock)
        assertEquals("|||", p.getString("layout_dock_slots", null))
        controller.pause().stop().destroy()
    }

    private fun registerMember(a: MainActivity, key: String) {
        @Suppress("UNCHECKED_CAST")
        val apps = MainActivity::class.java.getDeclaredField("apps").apply { isAccessible = true }.get(a) as MutableList<android.content.pm.ResolveInfo>
        apps.add(android.content.pm.ResolveInfo().apply {
            nonLocalizedLabel = key
            activityInfo = android.content.pm.ActivityInfo().apply {
                packageName = key.substringBefore('/'); name = key.substringAfter('/')
                applicationInfo = android.content.pm.ApplicationInfo(a.applicationInfo).apply { packageName = key.substringBefore('/') }
            }
        })
    }
    @Test fun nativeDragLeavesSameWindowFolderAndCommitsHomeWithoutDuplicatingOrLosingOverflow() {
        for (count in listOf(1, 8)) {
            val p = seed(); val initial = state().copy(folders = mapOf(id to WorkspaceFolder("Teste", List(count) { "pkg/Member$it" })))
            assertTrue(WorkspaceItemsStore(p).save(initial))
            val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); registerMember(a, "pkg/Member0")
            a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!.performClick(); layout(a)
            val folder = MainActivity::class.java.getDeclaredField("folderDialog").apply { isAccessible = true }.get(a) as FolderSurface
            val source = folder.content.findViewWithTag<View>("folder_member_0")!!
            val session = WorkspaceDragSession("pkg/Member0", current(a))
            set(a, "dragSession", session); set(a, "dragging", true); set(a, "dragSource", source)
            val root = a.window.decorView
            assertTrue(root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session, key = session.key)))
            val target = root.findViewWithTag<View>("workspace_cell_0_1")!!
            val origin = IntArray(2); val base = IntArray(2); target.getLocationOnScreen(origin); root.getLocationOnScreen(base)
            val x = origin[0] - base[0] + target.width / 2f; val y = origin[1] - base[1] + target.height / 2f
            // First move outside the folder's panel, then onto the measured home cell.
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, key = session.key, x = 1f, y = 1f))
            assertTrue(folder.collapsed); assertTrue(source.isAttachedToWindow)
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, key = session.key, x = x, y = y))
            assertTrue(root.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, key = session.key, x = x, y = y)))
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session, key = session.key))
            shadowOf(Looper.getMainLooper()).idle()
            val after = current(a)
            assertEquals(session.key, after.pages[0][1]); assertFalse(folder.isShowing)
            if (count == 1) { assertFalse(id in after.folders); assertEquals("", after.pages[0][0]) }
            else assertEquals(count - 1, after.folders.getValue(id).apps.size)
            val all = after.pages.flatten() + after.dock + after.folders.values.flatMap { it.apps }
            assertEquals(1, all.count { it == session.key })
            c.pause().stop().destroy()
            val restarted = Robolectric.buildActivity(MainActivity::class.java).setup()
            assertEquals(after, current(restarted.get())); restarted.pause().stop().destroy()
        }
    }
    @Test fun canceledNativeFolderExitRestoresPanelAndEveryPreference() {
        val p = seed(); val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get()
        registerMember(a, "pkg/A")
        a.window.decorView.findViewWithTag<View>("workspace_cell_0_0")!!.performClick(); layout(a)
        val before = p.all.toMap()
        val folder = MainActivity::class.java.getDeclaredField("folderDialog").apply { isAccessible = true }.get(a) as FolderSurface
        val session = WorkspaceDragSession("pkg/A", current(a))
        set(a, "dragSession", session); set(a, "dragging", true)
        val root = a.window.decorView
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session, key = session.key))
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, key = session.key))
        assertTrue(folder.collapsed)
        root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session, key = session.key))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(folder.collapsed); assertTrue(folder.isShowing); assertEquals(before, p.all)
        c.pause().stop().destroy()
    }

    @Test fun nativeRootMovesApplicationIntoDockAndBackWithoutChangingOtherFolders() {
        val p = seed(); val key = "pkg/App0"
        val initial = state().copy(pages = listOf(listOf(id, key) + List(18) { "" }, List(20) { "" }, List(20) { "" }))
        assertTrue(WorkspaceItemsStore(p).save(initial))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); registerMember(a, key)
        fun drop(tag: String) {
            layout(a)
            val root = a.window.decorView; val target = root.findViewWithTag<View>(tag)!!
            val session = WorkspaceDragSession(key, current(a))
            set(a, "dragSession", session); set(a, "dragging", true); set(a, "dragDropCommitted", false)
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_STARTED, session, key = key))
            val at = IntArray(2); val base = IntArray(2); target.getLocationOnScreen(at); root.getLocationOnScreen(base)
            val x = at[0] - base[0] + target.width / 2f; val y = at[1] - base[1] + target.height / 2f
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_LOCATION, session, key = key, x = x, y = y))
            assertTrue(root.dispatchDragEvent(event(DragEvent.ACTION_DROP, session, key = key, x = x, y = y)))
            root.dispatchDragEvent(event(DragEvent.ACTION_DRAG_ENDED, session, key = key))
            shadowOf(Looper.getMainLooper()).idle()
        }
        drop("dock_slot_1")
        assertEquals(key, current(a).dock[1]); assertEquals("", current(a).pages[0][1])
        drop("workspace_cell_0_2")
        val after = current(a)
        assertEquals(key, after.pages[0][2]); assertEquals("", after.dock[1])
        assertEquals(initial.folders, after.folders)
        assertEquals(1, (after.pages.flatten() + after.dock).count { it == key })
        c.pause().stop().destroy()
    }
    @Test fun editorRemovesOnlySelectedHomeShortcutWithoutUninstallOrDeletingDockCopy() {
        val p = seed(); val key = "pkg/App0"
        val initial = state().copy(pages = listOf(listOf(id, key) + List(18) { "" }, List(20) { "" }, List(20) { "" }), dock = listOf(key, "", "", ""))
        assertTrue(WorkspaceItemsStore(p).save(initial))
        val c = Robolectric.buildActivity(MainActivity::class.java).setup(); val a = c.get(); registerMember(a, key)
        MainActivity::class.java.getDeclaredMethod("enterEditMode").apply { isAccessible = true }.invoke(a)
        fun choose(index: Int) {
            val dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(null, index, index.toLong())
        }
        choose(1); choose(0); choose(1); choose(1)
        val after = current(a)
        assertEquals("", after.pages[0][1]); assertEquals(key, after.dock[0]); assertEquals(initial.folders, after.folders)
        assertNull(shadowOf(a).nextStartedActivity)
        c.pause().stop().destroy()
    }

}
