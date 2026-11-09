package com.purplecharm.launcher

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * Tela única de indicações. Os ícones definitivos e links verificados serão
 * adicionados depois. Não atribuir status de parceria sem confirmação.
 */
internal object GanheComNosScreen {
    private val recommended = listOf("TTcoin", "BixBerry", "UpRock")

    fun show(activity: Activity) {
        val scale = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * scale + 0.5f).toInt()
        fun text(value: String, size: Float, color: Int = Color.WHITE): TextView =
            TextView(activity).apply {
                this.text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }
        fun background(stroke: Boolean = false) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(39, 20, 65), Color.rgb(13, 12, 28))
        ).apply {
            cornerRadius = dp(20).toFloat()
            if (stroke) setStroke(dp(1), Color.rgb(150, 91, 230))
        }
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            this.background = background(true)
        }
        root.addView(text("Ganhe com Nós", 22f))
        root.addView(text("Aplicativos e oportunidades escolhidos por você", 12f, Color.LTGRAY))
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(content)
        }
        fun section(title: String, names: List<String>, official: Boolean) {
            val heading = text(title, 17f).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, dp(10))
            }
            content.addView(heading)
            val rows = (names.size + 2) / 3
            repeat(rows) { rowIndex ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                repeat(3) { column ->
                    val index = rowIndex * 3 + column
                    val name = names.getOrNull(index)
                    val cell = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(3), dp(8), dp(3), dp(8))
                    }
                    val circle = text(if (official) "＋" else name?.take(1) ?: "", 25f).apply {
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(Color.rgb(55, 106, 202), Color.rgb(105, 37, 174))
                        ).apply {
                            shape = GradientDrawable.OVAL
                            if (official) setStroke(dp(2), Color.rgb(176, 116, 247))
                        }
                    }
                    cell.addView(circle, LinearLayout.LayoutParams(dp(62), dp(62)))
                    cell.addView(text(name ?: "Disponível", 11f), LinearLayout.LayoutParams(-1, dp(32)))
                    if (!official && name != null) {
                        cell.setOnClickListener {
                            Toast.makeText(activity, "Link de ${name} aguardando validação", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(cell, LinearLayout.LayoutParams(0, dp(110), 1f))
                }
                content.addView(row)
            }
        }
        section("Parceiros oficiais", List(6) { "Disponível" }, true)
        section("Recomendados", recommended, false)
        content.addView(text("Indicações opcionais. Links serão ativados somente após verificação.", 11f, Color.LTGRAY))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("Fechar", 15f).apply {
            setPadding(dp(8), dp(12), dp(8), dp(8))
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((activity.resources.displayMetrics.widthPixels - dp(24)).coerceAtMost(dp(440)), (activity.resources.displayMetrics.heightPixels * 0.78f).toInt())
        }
    }
}
