package com.purplecharm.launcher

import org.json.JSONArray
import org.json.JSONObject

/** Immutable edits: no original state is mutated while a drag is in progress. */
internal data class WorkspaceFolder(val name: String, val apps: List<String>)
internal data class WorkspaceItems(
    val pages: List<List<String>>, val dock: List<String>, val folders: Map<String, WorkspaceFolder>
) {
    fun valid(): Boolean {
        if (pages.size !in 3..8 || dock.size != 4 || pages.any { it.isEmpty() }) return false
        val roots = pages.flatten() + dock
        if (roots.any { it.contains('|') }) return false
        if (folders.any { (id, f) -> !isFolder(id) || f.name.isBlank() || f.name.length > 60 ||
                f.apps.any { it.isBlank() || isFolder(it) || it.contains('|') } || f.apps.distinct().size != f.apps.size }) return false
        if (roots.filter { isFolder(it) }.any { it !in folders }) return false
        return folders.keys.all { id -> roots.count { it == id } == 1 }
    }
    companion object {
        fun isFolder(key: String) = key.startsWith("folder:")
        fun encodeFolders(folders: Map<String, WorkspaceFolder>): String = JSONObject().apply {
            put("version", 1)
            put("folders", JSONObject().apply { folders.forEach { (id, folder) ->
                put(id, JSONObject().put("name", folder.name).put("apps", JSONArray(folder.apps)))
            } })
        }.toString()
        fun decodeFolders(raw: String?): Map<String, WorkspaceFolder> {
            if (raw == null) return emptyMap()
            val root = JSONObject(raw)
            require(root.getInt("version") == 1)
            val data = root.getJSONObject("folders")
            return data.keys().asSequence().associateWith { id ->
                val folder = data.getJSONObject(id)
                val apps = folder.getJSONArray("apps")
                WorkspaceFolder(folder.getString("name"), (0 until apps.length()).map { apps.getString(it) })
            }
        }
    }
}

internal object WorkspaceEdits {
    private data class Origin(val page: Int? = null, val cell: Int = -1, val dock: Int? = null,
                              val folder: String? = null)
    private fun origin(s: WorkspaceItems, key: String): Origin? {
        s.pages.forEachIndexed { page, cells -> cells.indexOf(key).takeIf { it >= 0 }?.let { return Origin(page, it) } }
        s.dock.indexOf(key).takeIf { it >= 0 }?.let { return Origin(dock = it) }
        s.folders.forEach { (id, f) -> f.apps.indexOf(key).takeIf { it >= 0 }?.let { return Origin(cell = it, folder = id) } }
        return null
    }
    private class Edit(s: WorkspaceItems) {
        private val populatedAtStart = s.folders.filterValues { it.apps.isNotEmpty() }.keys
        val pages = s.pages.map { it.toMutableList() }.toMutableList()
        val dock = s.dock.toMutableList()
        val folders = s.folders.toMutableMap()
        fun remove(key: String) {
            pages.forEach { cells -> cells.indices.filter { cells[it] == key }.forEach { cells[it] = "" } }
            dock.indices.filter { dock[it] == key }.forEach { dock[it] = "" }
            if (!WorkspaceItems.isFolder(key)) folders.keys.toList().forEach { id ->
                folders[id] = folders.getValue(id).let { it.copy(apps = it.apps.filterNot { app -> app == key }) }
            }
        }
        fun put(origin: Origin, key: String) {
            when {
                origin.page != null -> pages[origin.page][origin.cell] = key
                origin.dock != null -> dock[origin.dock] = key
                origin.folder != null -> folders[origin.folder]?.let { f ->
                    folders[origin.folder] = f.copy(apps = f.apps.toMutableList().apply { add(origin.cell.coerceIn(0, size), key) })
                }
            }
        }
        fun done(): WorkspaceItems? {
            // Prune only after the transaction: swaps/reorders may refill a temporarily empty folder.
            folders.filter { (id, folder) -> id in populatedAtStart && folder.apps.isEmpty() }.keys.toList().forEach { id ->
                remove(id)
                folders.remove(id)
            }
            return WorkspaceItems(pages.map { it.toList() }, dock.toList(), folders.toMap()).takeIf { it.valid() }
        }
    }
    fun page(s: WorkspaceItems, key: String, page: Int, cell: Int, group: Boolean = false,
             folderId: String = "folder:${java.util.UUID.randomUUID()}"): WorkspaceItems? {
        if (!s.valid() || page !in s.pages.indices || cell !in s.pages[page].indices || key.isBlank()) return null
        if (WorkspaceItems.isFolder(key) && key !in s.folders) return null
        val target = s.pages[page][cell]
        if (target == key) return s
        if (group && target.isNotBlank() && !WorkspaceItems.isFolder(key)) {
            if (WorkspaceItems.isFolder(target)) return intoFolder(s, key, target)
            if (!WorkspaceItems.isFolder(folderId) || folderId in s.folders) return null
            val e = Edit(s); e.remove(key); e.remove(target)
            e.folders[folderId] = WorkspaceFolder("Pasta", listOf(target, key))
            e.pages[page][cell] = folderId
            return e.done()
        }
        val source = origin(s, key)
        val e = Edit(s)
        if (target.isNotBlank()) {
            if (source != null) {
                // A folder cannot be inserted into an app folder through a swap.
                if (source.folder != null && WorkspaceItems.isFolder(target)) return null
                e.remove(key); e.remove(target); e.put(source, target)
            } else {
                val free = e.pages[page].indexOfFirst { it.isEmpty() }
                if (free < 0) return null
                e.remove(target); e.pages[page][free] = target
            }
        } else e.remove(key)
        e.pages[page][cell] = key
        return e.done()
    }
    fun dock(s: WorkspaceItems, key: String, slot: Int): WorkspaceItems? {
        if (!s.valid() || slot !in 0..3 || key.isBlank()) return null
        if (WorkspaceItems.isFolder(key) && key !in s.folders) return null
        val target = s.dock[slot]
        if (target == key) return s
        val source = origin(s, key)
        if (target.isNotEmpty() && source == null) return null
        if (source?.folder != null && WorkspaceItems.isFolder(target)) return null
        val e = Edit(s); e.remove(key)
        if (target.isNotEmpty()) { e.remove(target); e.put(source!!, target) }
        e.dock[slot] = key
        return e.done()
    }
    fun configureDock(s: WorkspaceItems, key: String, slot: Int): WorkspaceItems? {
        dock(s, key, slot)?.let { return it }
        if (!s.valid() || slot !in 0..3 || origin(s, key) != null) return null
        val previous = s.dock[slot]
        if (previous.isEmpty()) return null
        for (p in s.pages.indices) {
            val cell = s.pages[p].indexOf("")
            if (cell >= 0) return page(s, previous, p, cell)?.let { dock(it, key, slot) }
        }
        return null
    }
    fun intoFolder(s: WorkspaceItems, key: String, id: String, index: Int? = null): WorkspaceItems? {
        if (!s.valid() || key.isBlank() || WorkspaceItems.isFolder(key) || id !in s.folders) return null
        val old = s.folders.getValue(id)
        val destination = index ?: old.apps.size
        val e = Edit(s); e.remove(key)
        val f = e.folders.getValue(id)
        e.folders[id] = f.copy(apps = f.apps.toMutableList().apply { add(destination.coerceIn(0, size), key) })
        return e.done()
    }
    fun rename(s: WorkspaceItems, id: String, name: String): WorkspaceItems? {
        val trimmed = name.trim()
        if (!s.valid() || id !in s.folders || trimmed.isEmpty() || trimmed.length > 60) return null
        return s.copy(folders = s.folders + (id to s.folders.getValue(id).copy(name = trimmed)))
    }
    fun removeApp(s: WorkspaceItems, id: String, key: String): WorkspaceItems? {
        if (!s.valid() || key !in (s.folders[id]?.apps ?: emptyList())) return null
        val e = Edit(s)
        e.folders[id] = s.folders.getValue(id).copy(apps = s.folders.getValue(id).apps.filterNot { it == key })
        return e.done()
    }
    fun extract(s: WorkspaceItems, id: String, key: String, preferredPage: Int): WorkspaceItems? {
        if (key !in (s.folders[id]?.apps ?: emptyList())) return null
        val order = (listOf(preferredPage) + s.pages.indices.toList()).distinct().filter { it in s.pages.indices }
        for (p in order) s.pages[p].indexOfFirst { it.isEmpty() }.takeIf { it >= 0 }?.let { return page(s, key, p, it) }
        if (s.pages.size >= 8) return null
        val extra = s.copy(pages = s.pages + listOf(List(s.pages.first().size) { "" }))
        return page(extra, key, extra.pages.lastIndex, 0)
    }
    fun removeShortcut(s: WorkspaceItems, key: String): WorkspaceItems? {
        if (!s.valid()) return null
        // Removing a folder must never silently discard its apps.
        if (WorkspaceItems.isFolder(key)) return null
        val e = Edit(s); e.remove(key); return e.done()
    }
}

/** Begin/cancel perform no writes. A successful drop consumes the session exactly once. */
internal class WorkspaceDragSession(val key: String, val before: WorkspaceItems) {
    var finished = false
        private set
    fun commit(current: WorkspaceItems, proposed: WorkspaceItems?, persist: (WorkspaceItems) -> Boolean): Boolean {
        if (finished || current != before || proposed == null || !proposed.valid()) return false
        if (!persist(proposed)) return false
        finished = true
        return true
    }
    fun cancel() { finished = true }
}
