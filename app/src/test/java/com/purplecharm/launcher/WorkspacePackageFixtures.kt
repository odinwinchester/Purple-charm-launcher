package com.purplecharm.launcher

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** Persisted shortcuts in UI tests represent installed packages, even before a fake activity is injected. */
internal fun installWorkspaceTestPackage(pkg: String) {
    val manager = RuntimeEnvironment.getApplication().packageManager
    val info = PackageInfo().apply {
        packageName = pkg
        applicationInfo = ApplicationInfo().apply {
            packageName = pkg
            flags = ApplicationInfo.FLAG_INSTALLED
        }
    }
    shadowOf(manager).installPackage(info)
}
