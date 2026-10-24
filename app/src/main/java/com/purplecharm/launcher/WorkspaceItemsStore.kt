package com.purplecharm.launcher

import android.content.SharedPreferences

/** Reuses legacy page/cell/dock keys; folder metadata joins the same atomic editor. */
internal class WorkspaceItemsStore(private val prefs: SharedPreferences) {
    fun folders(): Map<String, WorkspaceFolder> = WorkspaceItems.decodeFolders(prefs.getString("workspace_folders_v1", null))
    fun save(state: WorkspaceItems): Boolean {
        if (!state.valid()) return false
        val safety = WorkspaceLayoutSafety(prefs)
        val before = safety.capture()
        val edit = prefs.edit().putInt("layout_page_count", state.pages.size)
            .putString("workspace_folders_v1", WorkspaceItems.encodeFolders(state.folders))
        state.pages.forEachIndexed { index, cells ->
            edit.putString("layout_page_$index", cells.filter { it.isNotEmpty() }.distinct().joinToString("|"))
            edit.putString("layout_page_cells_$index", cells.joinToString("|"))
        }
        for (index in state.pages.size until 8) {
            edit.remove("layout_page_$index").remove("layout_page_cells_$index")
        }
        edit.putString("layout_dock_slots", state.dock.joinToString("|"))
            .putString("layout_dock", state.dock.filter { it.isNotEmpty() }.joinToString("|"))
        if (edit.commit()) return true
        // SharedPreferences may update its in-memory map even when a disk write fails.
        // Restore the pre-edit layout; unrelated theme/weather settings are untouched.
        safety.restore(before)
        return false
    }
}
