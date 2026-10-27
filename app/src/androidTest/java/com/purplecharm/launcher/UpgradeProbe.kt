package com.purplecharm.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle

/** CI-only probe: never packaged in the launcher APK. */
class UpgradeProbe : Instrumentation() {
    private var phase = "verify"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); phase = arguments?.getString("phase") ?: "verify"; start() }
    override fun onStart() {
        try {
            val prefs = targetContext.getSharedPreferences("purple_charm_favorites", Context.MODE_PRIVATE)
            val folders = """{"version":1,"folders":{"folder:upgrade":{"name":"Preservar","apps":["probe/Activity"]}}}"""
            if (phase == "write") {
                check(prefs.edit().putString("upgrade_test_marker", "same-key-preserved")
                    .putString("workspace_folders_v1", folders)
                    .putInt("layout_page_count", 3)
                    .putString("layout_page_cells_0", (listOf("folder:upgrade") + List(19) { "" }).joinToString("|"))
                    .putString("layout_dock_slots", "probe/Activity|||")
                    .putString("gothic_manual_art", "dia_limpo").commit())
            }
            check(prefs.getString("upgrade_test_marker", null) == "same-key-preserved")
            check(prefs.getString("workspace_folders_v1", null) == folders)
            check(prefs.getString("layout_dock_slots", null) == "probe/Activity|||")
            check(prefs.getString("gothic_manual_art", null) == "dia_limpo")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", "UPGRADE_PROBE_OK:$phase\n") })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply { putString("stream", "UPGRADE_PROBE_FAILED:${error.javaClass.simpleName}\n") })
        }
    }
}
