package com.purplecharm.launcher

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
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
import android.widget.Toast

class MainActivity : Activity() {
    private val favorites by lazy { getSharedPreferences("purple_charm_favorites", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showHome()
    }

    private fun showHome() {
        val background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(36, 12, 55), Color.rgb(104, 39, 125), Color.rgb(25, 8, 43))
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(12, 32, 12, 8)
            this.background = background
        }
        root.addView(TextView(this).apply {
            text = "Purple Charm"
            textSize = 30f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Meu universo roxo ✦"
            textSize = 15f
            setTextColor(Color.rgb(235, 190, 255))
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 18)
        })

        val pm = packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val selected = favorites.getStringSet("apps", emptySet()) ?: emptySet()
        val pinned = apps.filter { selected.contains(keyFor(it)) }
        if (pinned.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "✦ Favoritos  •  segure para remover"
                textSize = 16f
                setTextColor(Color.WHITE)
                setPadding(12, 0, 0, 10)
            })
            val favoriteGrid = createGrid()
            pinned.forEach { addApp(favoriteGrid, it, pm) }
            root.addView(favoriteGrid)
        }

        root.addView(TextView(this).apply {
            text = "Aplicativos  •  segure para favoritar"
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(12, 12, 0, 10)
        })
        val scroll = ScrollView(this)
        val grid = createGrid()
        apps.forEach { addApp(grid, it, pm) }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun createGrid(): GridLayout = GridLayout(this).apply {
        columnCount = 4
        setPadding(4, 4, 4, 12)
    }

    private fun keyFor(app: ResolveInfo): String =
        app.activityInfo.packageName + "/" + app.activityInfo.name

    private fun addApp(grid: GridLayout, app: ResolveInfo, pm: PackageManager) {
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
                    setClassName(app.activityInfo.packageName, app.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(launch)
            } catch (e: Exception) {
                Toast.makeText(this, "Não foi possível abrir o aplicativo", Toast.LENGTH_SHORT).show()
            }
        }
        item.setOnLongClickListener {
            val key = keyFor(app)
            val updated = (favorites.getStringSet("apps", emptySet()) ?: emptySet()).toMutableSet()
            val added = if (updated.contains(key)) {
                updated.remove(key)
                false
            } else {
                updated.add(key)
                true
            }
            favorites.edit().putStringSet("apps", updated).apply()
            Toast.makeText(this, if (added) "Adicionado aos favoritos" else "Removido dos favoritos", Toast.LENGTH_SHORT).show()
            showHome()
            true
        }
        grid.addView(item, GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        })
    }
}
