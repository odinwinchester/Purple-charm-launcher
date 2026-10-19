
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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import kotlin.math.abs

class MainActivity : Activity() {
    private val favorites by lazy {
        getSharedPreferences("purple_charm_favorites", MODE_PRIVATE)
    }
    private lateinit var apps: List<ResolveInfo>
    private lateinit var pm: PackageManager
    private lateinit var root: LinearLayout
    private lateinit var pages: ViewFlipper
    private lateinit var dots: TextView
    private var currentPage = 1
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pm = packageManager
        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        apps = pm.queryIntentActivities(query, PackageManager.MATCH_ALL)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
        showHome()
    }

    private fun purpleBackground() = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.rgb(36, 12, 55),
            Color.rgb(104, 39, 125),
            Color.rgb(25, 8, 43)
        )
    )

    private fun title(text: String, size: Float = 18f) =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }

    private fun keyFor(app: ResolveInfo) =
        app.activityInfo.packageName + "/" + app.activityInfo.name

    private fun selected(): Set<String> =
        favorites.getStringSet("apps", emptySet()) ?: emptySet()

    private fun favoritesList() =
        apps.filter { selected().contains(keyFor(it)) }

    private fun makeRoot() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = purpleBackground()
        }
        setContentView(root)
    }

    private fun showHome() {
        drawerOpen = false
        makeRoot()

        root.addView(title("Purple Charm", 32f).apply {
            typeface = Typeface.create("serif", Typeface.BOLD)
        })
        root.addView(title("Meu universo roxo ✦", 14f))

        pages = ViewFlipper(this)
        pages.addView(homePage(0))
        pages.addView(homePage(1))
        pages.addView(homePage(2))
        root.addView(
            pages,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )
        currentPage = 1
        pages.displayedChild = currentPage

        dots = title("", 18f)
        root.addView(dots)
        updateDots()

        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            setPadding(4, 8, 4, 8)
            setBackgroundColor(Color.argb(110, 18, 4, 35))
        }

        favoritesList().take(3).forEach {
            dock.addView(
                appItem(it, true),
                LinearLayout.LayoutParams(0, 86, 1f)
            )
        }

        val drawerButton = title("▦\nApps", 17f).apply {
            setOnClickListener { showDrawer() }
        }
        dock.addView(
            drawerButton,
            LinearLayout.LayoutParams(0, 86, 1f)
        )
        root.addView(dock)
    }

    private fun homePage(index: Int): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(12, 16, 12, 4)
        }

        when (index) {
            0 -> {
                page.addView(title("✦ Minha página", 23f))
                page.addView(
                    title("Deslize para os lados para navegar", 14f)
                )
                page.addView(appGrid(apps.take(12), false))
            }
            1 -> {
                page.addView(title("✦ Favoritos", 23f))
                page.addView(
                    title("Segure um aplicativo na gaveta para favoritar", 13f)
                )
                val pinned = favoritesList()
                if (pinned.isEmpty()) {
                    page.addView(
                        title("Abra a gaveta e escolha seus favoritos", 16f)
                    )
                } else {
                    page.addView(appGrid(pinned, false))
                }
            }
            else -> {
                page.addView(title("✦ Mais aplicativos", 23f))
                page.addView(
                    appGrid(apps.drop(12).take(12), false)
                )
            }
        }

        val scroll = ScrollView(this)
        scroll.fillViewport = true
        scroll.addView(page)
        scroll.setOnTouchListener { _, event -> swipe(event) }
        return scroll
    }

    private fun swipe(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY

                if (abs(dx) > 75 && abs(dx) > abs(dy) * 1.2f) {
                    currentPage = (
                        currentPage + if (dx < 0) 1 else -1
                    ).coerceIn(0, 2)
                    pages.displayedChild = currentPage
                    updateDots()
                    return true
                }

                if (dy < -110 && abs(dy) > abs(dx)) {
                    showDrawer()
                    return true
                }
            }
        }
        return false
    }

    private fun updateDots() {
        dots.text = (0..2).joinToString("   ") {
            if (it == currentPage) "●" else "○"
        }
    }

    private fun showDrawer() {
        drawerOpen = true
        makeRoot()

        val header = title("⌄  Aplicativos  ✦", 23f)
        header.setOnClickListener { showHome() }
        root.addView(header)

        root.addView(
            title("Toque para abrir • segure para favoritar", 13f)
        )

        val scroll = ScrollView(this)
        scroll.addView(appGrid(apps, true))
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        )

        val back = title("⌄  Voltar à tela inicial", 17f)
        back.setOnClickListener { showHome() }
        root.addView(back)
    }

    private fun appGrid(
        items: List<ResolveInfo>,
        allowFavorite: Boolean
    ): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = 4
            setPadding(4, 8, 4, 12)
        }

        items.forEach { app ->
            grid.addView(
                appItem(app, allowFavorite),
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED, 1f
                    )
                }
            )
        }
        return grid
    }

    private fun appItem(
        app: ResolveInfo,
        allowFavorite: Boolean
    ): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(3, 10, 3, 12)
        }

        item.addView(ImageView(this).apply {
            setImageDrawable(app.loadIcon(pm))
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(56, 56)
        })

        item.addView(
            title(app.loadLabel(pm).toString(), 10f).apply {
                maxLines = 2
                setPadding(1, 3, 1, 0)
            }
        )

        item.setOnClickListener {
            try {
                startActivity(Intent().apply {
                    setClassName(
                        app.activityInfo.packageName,
                        app.activityInfo.name
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Não foi possível abrir o aplicativo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (allowFavorite) {
            item.setOnLongClickListener {
                val updated = selected().toMutableSet()
                val key = keyFor(app)

                val added = if (updated.contains(key)) {
                    updated.remove(key)
                    false
                } else {
                    updated.add(key)
                    true
                }

                favorites.edit()
                    .putStringSet("apps", updated)
                    .apply()

                Toast.makeText(
                    this,
                    if (added) "Adicionado aos favoritos"
                    else "Removido dos favoritos",
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }
        return item
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) {
            showHome()
        } else if (currentPage != 1) {
            currentPage = 1
            pages.displayedChild = 1
            updateDots()
        } else {
            super.onBackPressed()
        }
    }
}
