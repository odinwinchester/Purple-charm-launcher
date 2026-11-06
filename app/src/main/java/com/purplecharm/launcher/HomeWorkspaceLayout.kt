package com.purplecharm.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup

internal object HomeWorkspaceGeometry {
    data class Zones(val widgetHeight: Int, val iconsTop: Int, val iconsHeight: Int)
    // Respect the user-selected dp height (converted to px by the caller), clamped to the workspace.
    fun zones(availableHeight: Int, requestedWidgetHeight: Int): Zones {
        val total = availableHeight.coerceAtLeast(0)
        val widget = requestedWidgetHeight.coerceIn(0, total)
        return Zones(widget, widget, total - widget)
    }
}

/** Both viewports are bounded by the workspace, above page indicators and dock. */
internal class HomeWorkspaceLayout(context: Context, widget: View, icons: View, private val widgetHeightPx: Int) : ViewGroup(context) {
    init {
        clipChildren = true
        clipToPadding = true
        addView(widget, LayoutParams(-1, -1))
        addView(icons, LayoutParams(-1, -1))
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val zones = HomeWorkspaceGeometry.zones(h, widgetHeightPx)
        setMeasuredDimension(w, h)
        getChildAt(0).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(zones.widgetHeight, MeasureSpec.EXACTLY))
        getChildAt(1).measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(zones.iconsHeight, MeasureSpec.EXACTLY))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val zones = HomeWorkspaceGeometry.zones(height, widgetHeightPx)
        getChildAt(0).layout(0, 0, width, zones.widgetHeight)
        getChildAt(1).layout(0, zones.iconsTop, width, height)
    }
}
