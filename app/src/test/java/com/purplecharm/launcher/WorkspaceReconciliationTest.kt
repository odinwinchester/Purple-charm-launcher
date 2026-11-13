package com.purplecharm.launcher

import org.junit.Assert.*
import org.junit.Test

class WorkspaceReconciliationTest {
    private val camp = "com.nianticlabs.campfire/com.nianticlabs.campfire.MainActivity"
    private val cnh = "com.digitra.digitraHB/com.digitra.digitraHB.MainActivity"
    private val updated = "com.digitra.digitraHB/NewActivity"
    private val valid = "example.valid/Main"
    private fun state(vararg keys: String) = WorkspaceItems(
        listOf(listOf("folder:test", valid, ""), listOf("","",""), listOf("","","")),
        listOf("","","",""), mapOf("folder:test" to WorkspaceFolder("Pasta", keys.toList()))
    )
    @Test fun removesGhostFolderAndKeepsOtherCells() {
        val result = WorkspaceEdits.reconcileComponents(state(camp), setOf(valid), setOf("com.nianticlabs.campfire"))!!
        assertTrue(result.folders.isEmpty())
        assertEquals(listOf("", valid, ""), result.pages[0])
        assertEquals(result, WorkspaceEdits.reconcileComponents(result, setOf(valid), setOf("com.nianticlabs.campfire")))
    }
    @Test fun recoversChangedActivity() {
        val result = WorkspaceEdits.reconcileComponents(state(cnh), setOf(updated, valid), emptySet())!!
        assertEquals(listOf(updated), result.folders.getValue("folder:test").apps)
    }
    @Test fun preservesValidMembersAndUnknownPackages() {
        val result = WorkspaceEdits.reconcileComponents(state(camp, cnh, valid), setOf(valid), setOf("com.nianticlabs.campfire"))!!
        assertEquals(listOf(cnh, valid), result.folders.getValue("folder:test").apps)
    }
    @Test fun deduplicatesRecoveredMembers() {
        val result = WorkspaceEdits.reconcileComponents(state(cnh, updated), setOf(updated, valid), emptySet())!!
        assertEquals(listOf(updated), result.folders.getValue("folder:test").apps)
    }
}
