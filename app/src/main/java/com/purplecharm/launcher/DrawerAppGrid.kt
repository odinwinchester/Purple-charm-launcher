package com.purplecharm.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.math.ceil

/** One sequential grid for every category; measured names determine a uniform row height. */
internal class DrawerAppGrid(context: Context) : ViewGroup(context) {
    var columns = 1
        private set
    var rowHeight = 0
        private set
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val density = resources.displayMetrics.density
        columns = (width / (68 * density).toInt().coerceAtLeast(1)).coerceIn(1, 5)
        val cell = (width / columns).coerceAtLeast(1)
        rowHeight = (82 * density).toInt()
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            rowHeight = maxOf(rowHeight, getChildAt(i).measuredHeight)
        }
        for (i in 0 until childCount) getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY))
        setMeasuredDimension(width, ceil(childCount.toDouble() / columns).toInt() * rowHeight)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val x = i % columns * width / columns
            val right = (i % columns + 1) * width / columns
            val y = i / columns * rowHeight
            getChildAt(i).layout(x, y, right, y + rowHeight)
        }
    }
    override fun generateDefaultLayoutParams() = LayoutParams(-1, -2)
}
