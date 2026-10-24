package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WorkspaceItemsTest {
    private val a = "pkg/A"; private val b = "pkg/B"; private val c = "pkg/C"
    private fun state(first: List<String> = listOf(a, b, "", ""), pages: Int = 3) =
        WorkspaceItems(listOf(first) + List(pages - 1) { List(first.size) { "" } }, List(4) { "" }, emptyMap())
    private fun apps(s: WorkspaceItems) = (s.pages.flatten() + s.dock).filter { it.isNotEmpty() && !WorkspaceItems.isFolder(it) } + s.folders.values.flatMap { it.apps }
    private fun grouped() = WorkspaceEdits.page(state(), a, 0, 1, true, "folder:test")!!

    @Test fun moveToEmptyCellKeepsEveryOtherCellAndOriginalUnchanged() {
        val before = state()
        val after = WorkspaceEdits.page(before, a, 0, 3)!!
        assertEquals(listOf("", b, "", a), after.pages[0])
        assertEquals(listOf(a, b, "", ""), before.pages[0])
    }
    @Test fun swapAcrossFullPagesUsesOriginalSlotInsteadOfLosingDisplacedApp() {
        val s = state(listOf(a, b)).copy(pages = listOf(listOf(a, b), listOf(c, "pkg/D"), listOf("", "")))
        val after = WorkspaceEdits.page(s, a, 1, 0)!!
        assertEquals(c, after.pages[0][0]); assertEquals(a, after.pages[1][0])
        assertEquals(apps(s).sorted(), apps(after).sorted())
    }
    @Test fun groupingWorksOnFullPageAndConsumesBothShortcutsOnlyAfterDrop() {
        val before = state(listOf(a, b))
        val after = WorkspaceEdits.page(before, a, 0, 1, true, "folder:test")!!
        assertEquals(listOf("", "folder:test"), after.pages[0])
        assertEquals(listOf(b, a), after.folders.getValue("folder:test").apps)
        assertEquals(listOf(a, b), before.pages[0]); assertTrue(after.valid())
    }
    @Test fun droppingIntoExistingFolderDoesNotDuplicateOrNest() {
        val s = grouped()
        val after = WorkspaceEdits.intoFolder(s, c, "folder:test")!!
        val again = WorkspaceEdits.intoFolder(after, c, "folder:test")!!
        assertEquals(1, apps(again).count { it == c })
        assertNull(WorkspaceEdits.intoFolder(again, "folder:test", "folder:test"))
    }
    @Test fun externalMoveToFullPageIsRejectedWithoutChangingAnything() {
        val s = state(listOf(a, b))
        assertNull(WorkspaceEdits.page(s, c, 0, 0))
        assertEquals(listOf(a, b), s.pages[0])
    }
    @Test fun folderCanBeRenamedAndMovedWithoutChangingContents() {
        val s = WorkspaceEdits.rename(grouped(), "folder:test", "  Jogos ☾  ")!!
        val moved = WorkspaceEdits.page(s, "folder:test", 2, 2)!!
        assertEquals("Jogos ☾", moved.folders.getValue("folder:test").name)
        assertEquals(listOf(b, a), moved.folders.getValue("folder:test").apps)
        assertEquals("folder:test", moved.pages[2][2])
        assertNull(WorkspaceEdits.rename(moved, "folder:test", " "))
    }
    @Test fun addRemoveAndReorderPreserveOtherMembersAndPruneEmptyFolder() {
        val added = WorkspaceEdits.intoFolder(grouped(), c, "folder:test")!!
        val reordered = WorkspaceEdits.intoFolder(added, c, "folder:test", 0)!!
        assertEquals(listOf(c, b, a), reordered.folders.getValue("folder:test").apps)
        val removed = WorkspaceEdits.removeApp(reordered, "folder:test", b)!!
        assertEquals(listOf(c, a), removed.folders.getValue("folder:test").apps)
        val empty = listOf(c, a).fold(removed) { s, key -> WorkspaceEdits.removeApp(s, "folder:test", key)!! }
        assertTrue(empty.valid()); assertFalse("folder:test" in empty.folders)
        assertFalse("folder:test" in empty.pages.flatten())
    }
    @Test fun extractingFindsFreeCellWithoutRearrangingOthers() {
        val s = grouped()
        val after = WorkspaceEdits.extract(s, "folder:test", a, 1)!!
        assertEquals(a, after.pages[1][0]); assertEquals(listOf(b), after.folders.getValue("folder:test").apps)
        assertEquals(s.pages[0], after.pages[0])
    }
    @Test fun extractingCanAddPageButRejectsWhenEightPagesAreFull() {
        val s = grouped()
        val filled = s.copy(pages = s.pages.mapIndexed { p, cells -> cells.mapIndexed { i, key -> key.ifEmpty { "p$p/$i" } } })
        val after = WorkspaceEdits.extract(filled, "folder:test", a, 0)!!
        assertEquals(4, after.pages.size); assertEquals(a, after.pages.last()[0])
        val full = filled.copy(pages = filled.pages + List(5) { p -> List(4) { i -> "extra$p/$i" } })
        assertNull(WorkspaceEdits.extract(full, "folder:test", a, 0))
    }
    @Test fun folderCodecKeepsNamesOrderAndEmptyFoldersAcrossRestart() {
        val s = WorkspaceEdits.rename(grouped(), "folder:test", "Vendas | ☀")!!
        assertEquals(s.folders, WorkspaceItems.decodeFolders(WorkspaceItems.encodeFolders(s.folders)))
        assertEquals(emptyMap<String, WorkspaceFolder>(), WorkspaceItems.decodeFolders(null))
    }
    @Test fun canceledOrFailedDragWritesNothingAndNeverMutatesOrigin() {
        val s = state(); val session = WorkspaceDragSession(a, s)
        val proposed = WorkspaceEdits.page(s, a, 0, 3)!!
        var calls = 0
        assertFalse(session.commit(s, proposed) { calls++; false })
        assertEquals(1, calls); assertFalse(session.finished)
        session.cancel()
        assertFalse(session.commit(s, proposed) { calls++; true })
        assertEquals(1, calls); assertEquals(listOf(a, b, "", ""), s.pages[0])
    }
    @Test fun duplicateDropAndConcurrentLayoutChangeAreRejected() {
        val s = state(); val session = WorkspaceDragSession(a, s)
        val proposed = WorkspaceEdits.page(s, a, 0, 3)!!
        assertFalse(session.commit(proposed, proposed) { error("must not write changed layout") })
        var calls = 0
        assertTrue(session.commit(s, proposed) { calls++; true })
        assertFalse(session.commit(s, proposed) { calls++; true })
        assertEquals(1, calls)
    }
    @Test fun dockSwapAndExtractionFromFolderPreserveEveryApp() {
        val s = grouped().copy(dock = listOf(c, "", "", ""))
        val moved = WorkspaceEdits.dock(s, a, 0)!!
        assertEquals(a, moved.dock[0]); assertEquals(listOf(b, c), moved.folders.getValue("folder:test").apps)
        assertEquals(apps(s).sorted(), apps(moved).sorted())
        val folderDock = WorkspaceEdits.dock(s, "folder:test", 1)!!
        assertEquals("folder:test", folderDock.dock[1]); assertTrue(folderDock.valid())
    }
    @Test fun orphanFolderAndUnsafeDeletionCannotBeCommitted() {
        val s = grouped()
        assertNull(WorkspaceEdits.removeShortcut(s, "folder:test"))
        assertFalse(s.copy(folders = emptyMap()).valid())
        assertFalse(s.copy(folders = s.folders + ("folder:orphan" to WorkspaceFolder("X", emptyList()))).valid())
    }
    @Test fun repeatedMovesAndFolderEditsKeepAllAppsExactlyOnce() {
        var s = WorkspaceEdits.intoFolder(grouped(), c, "folder:test")!!
        repeat(40) { i ->
            s = WorkspaceEdits.extract(s, "folder:test", a, i % 3)!!
            s = WorkspaceEdits.intoFolder(s, a, "folder:test", i % 3)!!
            s = WorkspaceEdits.page(s, "folder:test", i % 3, i % 4)!!
            assertEquals(listOf(a, b, c).sorted(), apps(s).sorted())
            assertTrue(s.valid())
        }
    }
    @Test fun lastMemberExtractionAndTransferRemoveOnlyEmptiedFolder() {
        val s = WorkspaceEdits.removeApp(grouped(), "folder:test", b)!!
        val extracted = WorkspaceEdits.extract(s, "folder:test", a, 2)!!
        assertTrue(extracted.folders.isEmpty()); assertEquals(a, extracted.pages[2][0])
        assertFalse("folder:test" in extracted.pages.flatten())
        val other = s.copy(pages = s.pages.mapIndexed { i, cells -> if (i == 1) listOf("folder:other") + cells.drop(1) else cells },
            folders = s.folders + ("folder:other" to WorkspaceFolder("Outra", listOf(c))))
        val moved = WorkspaceEdits.intoFolder(other, a, "folder:other")!!
        assertFalse("folder:test" in moved.folders); assertEquals(listOf(c, a), moved.folders.getValue("folder:other").apps)
        assertEquals(apps(other).sorted(), apps(moved).sorted())
    }
    @Test fun singleMemberReorderAndSwapDoNotPrematurelyDeleteFolder() {
        val s = WorkspaceEdits.removeApp(grouped(), "folder:test", b)!!.copy(dock = listOf(c, "", "", ""))
        assertEquals(s, WorkspaceEdits.intoFolder(s, a, "folder:test", 0))
        val swapped = WorkspaceEdits.dock(s, a, 0)!!
        assertEquals(listOf(c), swapped.folders.getValue("folder:test").apps)
        assertEquals(a, swapped.dock[0]); assertEquals(apps(s).sorted(), apps(swapped).sorted())
    }

    @Test fun pruningDoesNotRemoveUnrelatedLegacyEmptyFolder() {
        val s = WorkspaceEdits.removeApp(grouped(), "folder:test", b)!!
        val withLegacy = s.copy(pages = s.pages.mapIndexed { i, cells -> if (i == 1) listOf("folder:legacy") + cells.drop(1) else cells },
            folders = s.folders + ("folder:legacy" to WorkspaceFolder("Existente", emptyList())))
        val after = WorkspaceEdits.removeApp(withLegacy, "folder:test", a)!!
        assertFalse("folder:test" in after.folders)
        assertEquals(withLegacy.folders["folder:legacy"], after.folders["folder:legacy"])
        assertEquals(withLegacy.pages[1], after.pages[1])
    }

}
