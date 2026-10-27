package com.purplecharm.launcher

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

/** Same-window presentation: hiding never detaches the active native drag source. */
internal class FolderSurface(context: Context, private val host: FrameLayout, val content: View,
    private val onCancel: () -> Unit, private val onDismiss: () -> Unit) : FrameLayout(context) {
    var isShowing = false
        private set
    var collapsed = false
        private set
    init {
        val density = resources.displayMetrics.density
        val width = minOf(resources.displayMetrics.widthPixels - (32 * density).toInt(), (360 * density).toInt())
        addView(content, LayoutParams(width, -2, Gravity.CENTER))
        setOnClickListener { cancel() }
        content.isClickable = true
    }
    fun show() { host.addView(this, LayoutParams(-1, -1)); isShowing = true }
    fun collapse() { collapsed = true; visibility = GONE }
    fun restore() { collapsed = false; visibility = VISIBLE }
    fun containsScreenPoint(x: Int, y: Int): Boolean = Rect().let { content.getGlobalVisibleRect(it) && it.contains(x, y) }
    fun cancel() { onCancel(); dismiss() }
    fun dismiss() {
        if (!isShowing) return
        isShowing = false; host.removeView(this); onDismiss()
    }
}
