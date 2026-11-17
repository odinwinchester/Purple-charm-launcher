package com.purplecharm.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PurpleUpdatesActivityTest {
    @Test
    fun screenShowsInstalledVersionAndManualCheckWithoutOfferingUnverifiedInstall() {
        val activity = Robolectric.buildActivity(PurpleUpdatesActivity::class.java).setup().get()
        try {
            val root = activity.window.decorView
            assertNotNull(root.findViewWithTag<View>("purple_update_check"))
            val install = root.findViewWithTag<View>("purple_update_install")
            assertNotNull(install)
            assertEquals(View.GONE, install.visibility)
            val labels = collectText(root as ViewGroup)
            assertTrue(labels.any { it.contains("Atualizações") })
            assertTrue(labels.any { it.startsWith("Instalada:") })
        } finally {
            activity.finish()
        }
    }

    @Test
    fun installActionRequiresNewerVersionAndFreshVerification() {
        assertTrue(PurpleUpdateUiState.canInstall(12, 13, true))
        assertFalse(PurpleUpdateUiState.canInstall(13, 13, true))
        assertFalse(PurpleUpdateUiState.canInstall(12, 13, false))
    }

    @Test
    fun downloadStateProvidesProgressAndReadyMessages() {
        assertEquals(
            "Baixando atualização: 50%.",
            PurpleUpdateUiState.message("downloading", "14", 50, 100, false)
        )
        assertTrue(PurpleUpdateUiState.message("ready", "14", 100, 100, true).contains("pronta para instalar"))
    }

    @Test
    fun installerRevalidationChecksTheFullSha256() {
        val file = File.createTempFile("purple-update-test", ".apk")
        try {
            file.writeText("abc")
            val digest = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            assertTrue(PurpleUpdateApkSecurity.matchesDigest(file, digest))
            assertFalse(PurpleUpdateApkSecurity.matchesDigest(file, "0".repeat(64)))
        } finally {
            file.delete()
        }
    }

    private fun collectText(group: ViewGroup): List<String> = buildList {
        for (index in 0 until group.childCount) {
            when (val child = group.getChildAt(index)) {
                is TextView -> add(child.text.toString())
                is ViewGroup -> addAll(collectText(child))
            }
        }
    }
}
