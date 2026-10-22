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
        val dockSlots: String?
    )

    fun capture(): Snapshot {
        val count = preferences.getInt("layout_page_count", 3).coerceIn(3, 8)
        return Snapshot(
            pageCount = count,
            pages = (0 until count).map { preferences.getString("layout_page_$it", null) },
            pageCells = (0 until count).map { preferences.getString("layout_page_cells_$it", null) },
            dock = preferences.getString("layout_dock", null),
            dockSlots = preferences.getString("layout_dock_slots", null)
        )
    }

    fun isValid(snapshot: Snapshot): Boolean {
        if (snapshot.pageCount !in 3..8 || snapshot.pages.size != snapshot.pageCount ||
            snapshot.pageCells.size != snapshot.pageCount) return false
        val pageItems = snapshot.pages.flatMap { raw ->
            raw?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        }
        if (pageItems.any { it.contains('|') } || pageItems.size != pageItems.distinct().size) return false
        if (snapshot.pageCells.any { raw ->
                raw != null && raw.split('|').filter { it.isNotEmpty() }.distinct().size !=
                    raw.split('|').count { it.isNotEmpty() }
            }) return false
        val dockItems = snapshot.dockSlots?.split('|')?.filter { it.isNotBlank() }
            ?: snapshot.dock?.split('|')?.filter { it.isNotBlank() }
            ?: emptyList()
        if (dockItems.size > 4 || dockItems.size != dockItems.distinct().size) return false
        return pageItems.none { it in dockItems }
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
        return edit.commit()
    }
}
