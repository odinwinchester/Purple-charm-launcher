package com.purplecharm.launcher

import android.content.SharedPreferences

/** Reuses legacy page/cell/dock keys; folder metadata joins the same atomic editor. */
internal class WorkspaceItemsStore(private val prefs: SharedPreferences) {
    fun folders(): Map<String, WorkspaceFolder> = WorkspaceItems.decodeFolders(prefs.getString("workspace_folders_v1", null))
    /** Called only by the existing explicit, confirmed organization-reset action. */
    fun reset(): Boolean {
        val safety = WorkspaceLayoutSafety(prefs)
        val before = safety.capture()
        val edit = prefs.edit()
        for (index in 0 until 8) edit.remove("layout_page_$index").remove("layout_page_cells_$index")
        edit.remove("layout_page_count").remove("layout_dock").remove("layout_dock_slots")
            .remove("workspace_folders_v1")
        if (edit.commit()) return true
        safety.restore(before)
        return false
    }
    fun save(state: WorkspaceItems): Boolean {
        if (!state.valid()) return false
        val safety = WorkspaceLayoutSafety(prefs)
        val before = safety.capture()
        val edit = prefs.edit()
        var changed = false
        fun putString(key: String, value: String) {
            if (prefs.getString(key, null) != value) {
                edit.putString(key, value)
                changed = true
            }
        }
        fun remove(key: String) {
            if (prefs.contains(key)) {
                edit.remove(key)
                changed = true
            }
        }
        if (prefs.getInt("layout_page_count", -1) != state.pages.size) {
            edit.putInt("layout_page_count", state.pages.size)
            changed = true
        }
        putString("workspace_folders_v1", WorkspaceItems.encodeFolders(state.folders))
        state.pages.forEachIndexed { index, cells ->
            putString("layout_page_$index", cells.filter { it.isNotEmpty() }.distinct().joinToString("|"))
            putString("layout_page_cells_$index", cells.joinToString("|"))
        }
        for (index in state.pages.size until 8) {
            remove("layout_page_$index")
            remove("layout_page_cells_$index")
        }
        putString("layout_dock_slots", state.dock.joinToString("|"))
        putString("layout_dock", state.dock.filter { it.isNotEmpty() }.joinToString("|"))
        // Keep synchronous atomic persistence and rollback for real edits.
        if (!changed) return true
        if (edit.commit()) return true
        // SharedPreferences may update its in-memory map even when a disk write fails.
        // Restore the pre-edit layout; unrelated theme/weather settings are untouched.
        safety.restore(before)
        return false
    }
}
