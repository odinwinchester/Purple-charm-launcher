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
    private fun event(action: Int, session: Any?, key: String = id, mime: String = "application/vnd.purplecharm.workspace-item"): DragEvent {
        val data = ClipData("test", arrayOf(mime), ClipData.Item(key))
        val method = DragEvent::class.java.declaredMethods.first {
            it.name == "obtain" && it.parameterTypes.firstOrNull() == Int::class.javaPrimitiveType &&
                it.parameterTypes.contains(ClipData::class.java)
        }
        method.isAccessible = true
        val arguments = method.parameterTypes.map { type ->
            when (type) {
                Int::class.javaPrimitiveType -> action
                Float::class.javaPrimitiveType -> 1f
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

}
