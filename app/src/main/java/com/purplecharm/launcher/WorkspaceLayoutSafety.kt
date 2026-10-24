package com.purplecharm.launcher

import android.content.SharedPreferences

/**
 * Non-destructive workspace safety helpers.
 *
 * This class is intentionally independent of MainActivity. Integrate only after
 * gesture/drop handling has been reviewed and tested.
 */
internal class WorkspaceLayoutSafety(private val preferences: SharedPreferences) {
    data class Snapshot(
        val pageCount: Int,
        val pages: List<String?>,
        val pageCells: List<String?>,
        val dock: String?,
        val dockSlots: String?,
        val folders: String? = null
    )

    fun capture(): Snapshot {
        val count = preferences.getInt("layout_page_count", 3).coerceIn(3, 8)
        return Snapshot(
            pageCount = count,
            pages = (0 until count).map { preferences.getString("layout_page_$it", null) },
            pageCells = (0 until count).map { preferences.getString("layout_page_cells_$it", null) },
            dock = preferences.getString("layout_dock", null),
            dockSlots = preferences.getString("layout_dock_slots", null),
            folders = preferences.getString("workspace_folders_v1", null)
        )
    }

    fun isValid(snapshot: Snapshot): Boolean {
        if (snapshot.pageCount !in 3..8 || snapshot.pages.size != snapshot.pageCount ||
            snapshot.pageCells.size != snapshot.pageCount) return false
        // Legacy installations may intentionally repeat an app on multiple pages.
        // Validate structure without dropping those existing shortcuts during recovery.
        return try {
            val folders = WorkspaceItems.decodeFolders(snapshot.folders)
            val roots = snapshot.pages.flatMap { it?.split('|')?.filter(String::isNotBlank) ?: emptyList() } +
                (snapshot.dockSlots ?: snapshot.dock)?.split('|').orEmpty().filter(String::isNotBlank)
            roots.filter { WorkspaceItems.isFolder(it) }.all { it in folders } &&
                folders.values.all { it.name.isNotBlank() && it.apps.distinct().size == it.apps.size &&
                    it.apps.none(WorkspaceItems::isFolder) }
        } catch (_: Exception) { false }
    }

    /**
     * Restores the complete persisted layout in one synchronous transaction.
     * Does not delete unrelated preferences, wallpapers or theme settings.
     */
    fun restore(snapshot: Snapshot): Boolean {
        if (!isValid(snapshot)) return false
        val edit = preferences.edit().putInt("layout_page_count", snapshot.pageCount)
        for (i in 0 until 8) {
            val value = snapshot.pages.getOrNull(i)
            if (value == null) edit.remove("layout_page_$i")
            else edit.putString("layout_page_$i", value)
            val cells = snapshot.pageCells.getOrNull(i)
            if (cells == null) edit.remove("layout_page_cells_$i")
            else edit.putString("layout_page_cells_$i", cells)
        }
        if (snapshot.dock == null) edit.remove("layout_dock")
        else edit.putString("layout_dock", snapshot.dock)
        if (snapshot.dockSlots == null) edit.remove("layout_dock_slots")
        else edit.putString("layout_dock_slots", snapshot.dockSlots)
        if (snapshot.folders == null) edit.remove("workspace_folders_v1")
        else edit.putString("workspace_folders_v1", snapshot.folders)
        return edit.commit()
    }
}
