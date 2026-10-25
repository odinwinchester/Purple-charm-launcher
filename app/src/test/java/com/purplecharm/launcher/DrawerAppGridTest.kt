package com.purplecharm.launcher

import android.view.View
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DrawerAppGridTest {
    @Test fun everyWidthPacksRowsSequentiallyWithAtMostFiveColumns() {
        val app = RuntimeEnvironment.getApplication()
        for (dp in listOf(180, 280, 360, 480)) {
            val grid = DrawerAppGrid(app)
            repeat(17) { i -> grid.addView(TextView(app).apply { text = "Nome longo aplicativo $i"; minLines = 2; maxLines = 2 }) }
            val w = (dp * app.resources.displayMetrics.density).toInt()
            grid.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            grid.layout(0, 0, w, grid.measuredHeight)
            assertTrue(grid.columns in 1..5)
            for (i in 0 until grid.childCount) {
                val child = grid.getChildAt(i)
                assertEquals(i / grid.columns * grid.rowHeight, child.top)
                assertEquals(i % grid.columns * w / grid.columns, child.left)
                assertTrue(child.right <= w); assertEquals(grid.rowHeight, child.height)
            }
            assertEquals(((17 + grid.columns - 1) / grid.columns) * grid.rowHeight, grid.height)
        }
    }
    @Test fun rowHeightFitsMeasuredNamesInsteadOfClippingFixedHeight() {
        val app = RuntimeEnvironment.getApplication(); val grid = DrawerAppGrid(app)
        val text = TextView(app).apply { text = "Nome muito longo"; textSize = 30f; minLines = 2; maxLines = 2 }
        grid.addView(text)
        val width = (360 * app.resources.displayMetrics.density).toInt()
        grid.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        assertTrue(grid.rowHeight >= text.lineHeight * 2)
    }
}
