package com.purplecharm.launcher

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

internal object SmokeUi {
    fun panel(context: Context) = GradientDrawable().apply {
        val density = context.resources.displayMetrics.density
        setColor(Color.argb(218, 14, 9, 20)); cornerRadius = 18 * density
        setStroke(density.toInt().coerceAtLeast(1), Color.argb(110, 171, 107, 213))
    }
    fun style(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(panel(dialog.context))
        fun text(view: View) {
            if (view is TextView) view.setTextColor(Color.WHITE)
            if (view is ViewGroup) for (i in 0 until view.childCount) text(view.getChildAt(i))
        }
        dialog.window?.decorView?.let { text(it) }
        listOf(-1, -2, -3).forEach { dialog.getButton(it)?.setTextColor(Color.rgb(198, 153, 235)) }
        dialog.listView?.setSelector(android.graphics.drawable.ColorDrawable(Color.argb(75, 139, 67, 184)))
    }
}
internal class SmokeDialogBuilder(context: Context) : AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert) {
    override fun create(): AlertDialog = super.create().also { dialog ->
        dialog.window?.setBackgroundDrawable(SmokeUi.panel(context))
        dialog.setOnShowListener { SmokeUi.style(dialog) }
    }
}
