package com.purplecharm.launcher

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PersistedPackageCleanupAndroidTest {
    @Test fun startupRemovesGhostButPreservesInstalledUnavailableFolderAcrossRestart() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        installWorkspaceTestPackage("example.installed")
        val ghost = "folder:ghost"
        val keep = "folder:keep"
        val state = WorkspaceItems(
            listOf(listOf(ghost, keep) + List(18) { "" }, List(20) { "" }, List(20) { "" }),
            List(4) { "" },
            mapOf(ghost to WorkspaceFolder("Ghost", listOf("com.nianticlabs.campfire/Main")),
                keep to WorkspaceFolder("Keep", listOf("example.installed/Main")))
        )
        assertTrue(WorkspaceItemsStore(prefs).save(state))
        repeat(2) {
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            val folders = WorkspaceItemsStore(prefs).folders()
            assertFalse(folders.containsKey(ghost))
            assertEquals(state.folders[keep], folders[keep])
            val cells = prefs.getString("layout_page_cells_0", "")!!.split('|')
            assertEquals("", cells[0])
            assertEquals(keep, cells[1])
            controller.pause().stop().destroy()
        }
    }
}
