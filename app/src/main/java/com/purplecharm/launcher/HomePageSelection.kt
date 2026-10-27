package com.purplecharm.launcher

import android.content.SharedPreferences

/** Persists which existing workspace page should receive Home/Back navigation. */
internal object HomePageSelection {
    const val KEY = "layout_home_page"

    fun read(preferences: SharedPreferences, pageCount: Int): Int =
        preferences.getInt(KEY, 0).coerceIn(0, pageCount.coerceAtLeast(1) - 1)

    fun write(preferences: SharedPreferences, page: Int, pageCount: Int): Boolean {
        if (page !in 0 until pageCount) return false
        return preferences.edit().putInt(KEY, page).commit()
    }

    /** Remap the preferred index after a page is removed and later pages shift left. */
    fun afterRemoval(homePage: Int, removedPage: Int, oldPageCount: Int): Int {
        val newCount = (oldPageCount - 1).coerceAtLeast(1)
        val shifted = when {
            homePage > removedPage -> homePage - 1
            homePage == removedPage -> removedPage.coerceAtMost(newCount - 1)
            else -> homePage
        }
        return shifted.coerceIn(0, newCount - 1)
    }
}
