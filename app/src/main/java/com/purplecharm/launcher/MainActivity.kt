
package com.purplecharm.launcher

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(36, 12, 55),
                Color.rgb(104, 39, 125),
                Color.rgb(25, 8, 43)
            )
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(20, 50, 20, 20)
            this.background = background
        }

        val title = TextView(this).apply {
            text = "Purple Charm"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        }

        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Meu universo roxo ✦"
            textSize = 15f
            setTextColor(Color.rgb(235, 190, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 32)
        }

        root.addView(subtitle)

        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(4, 12, 4, 12)
        }

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        ).sortedBy {
            it.loadLabel(pm).toString().lowercase()
        }

        for (app in apps) {
            val packageName = app.activityInfo.packageName
            if (packageName == this.packageName) continue

            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(5, 12, 5, 12)
            }

            val icon = ImageView(this).apply {
                setImageDrawable(app.loadIcon(pm))
                layoutParams = LinearLayout.LayoutParams(60, 60)
            }

            val label = TextView(this).apply {
                text = app.loadLabel(pm).toString()
                textSize = 10f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                maxLines = 2
            }

            item.addView(icon)
            item.addView(label)

            item.setOnClickListener {
                try {
                    val launch = Intent().apply {
                        setClassName(
                            packageName,
                            app.activityInfo.name
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(launch)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        this,
                        "Não foi possível abrir o aplicativo",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }

            grid.addView(
                item,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                    )
                }
            )
        }

        scroll.addView(grid)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(root)
    }
}
