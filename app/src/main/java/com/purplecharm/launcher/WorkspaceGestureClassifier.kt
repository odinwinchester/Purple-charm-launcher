package com.purplecharm.launcher

/** Classifies a completed/claimed workspace swipe into at most one configured axis. */
internal enum class WorkspaceSwipe { NONE, HORIZONTAL, VERTICAL }

internal object WorkspaceGestureClassifier {
    fun classify(dx: Float, dy: Float, threshold: Float, dominance: Float = 1.5f): WorkspaceSwipe {
        val horizontal = kotlin.math.abs(dx)
        val vertical = kotlin.math.abs(dy)
        return when {
            horizontal > threshold && horizontal >= vertical * dominance -> WorkspaceSwipe.HORIZONTAL
            vertical > threshold && vertical >= horizontal * dominance -> WorkspaceSwipe.VERTICAL
            else -> WorkspaceSwipe.NONE
        }
    }
}
