package com.purplecharm.launcher

import android.content.Context
import android.view.DragEvent
import android.widget.FrameLayout

/** A stable drag target across ViewFlipper pages, including previously hidden children. */
internal class WorkspaceDragLayout(context: Context) : FrameLayout(context) {
    var owns: (DragEvent) -> Boolean = { false }
    var canNavigate: () -> Boolean = { true }
    var navigate: (Int) -> Boolean = { false }
    var drop: (DragEvent) -> Boolean = { false }
    var dropAt: ((DragEvent, Float, Float) -> Boolean)? = null
    var ended: () -> Unit = {}
    private var direction = 0
    private val turn = object : Runnable {
        override fun run() {
            if (direction != 0 && canNavigate() && navigate(direction)) postDelayed(this, 650L)
        }
    }
    private fun hover(x: Float) {
        val edge = 32 * resources.displayMetrics.density
        val next = when { x < edge -> -1; x > width - edge -> 1; else -> 0 }
        if (next == direction) return
        stopHover()
        direction = next
        if (next != 0) postDelayed(turn, 350L)
    }
    fun stopHover() { removeCallbacks(turn); direction = 0 }
    override fun dispatchDragEvent(event: DragEvent): Boolean = route(event, event.x, event.y)
    fun route(event: DragEvent, x: Float, y: Float): Boolean {
        if (!owns(event)) return false
        return when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> { stopHover(); true }
            DragEvent.ACTION_DRAG_ENTERED, DragEvent.ACTION_DRAG_LOCATION -> { hover(x); true }
            DragEvent.ACTION_DRAG_EXITED -> { stopHover(); true }
            DragEvent.ACTION_DROP -> { stopHover(); dropAt?.invoke(event, x, y) ?: drop(event) }
            DragEvent.ACTION_DRAG_ENDED -> { stopHover(); ended(); true }
            else -> true
        }
    }
    override fun onDetachedFromWindow() { stopHover(); super.onDetachedFromWindow() }
}
