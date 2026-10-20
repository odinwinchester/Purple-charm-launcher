
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

    private lateinit var root: LinearLayout
    private lateinit var pages: ViewFlipper
    private lateinit var dots: TextView

    private var currentPage = 1
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f

    private val favorites by lazy {
        getSharedPreferences(
            "purple_charm_favorites",
            MODE_PRIVATE
        )
    }

    private lateinit var apps: List<ResolveInfo>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadApps()
        showHome()
    }

    private fun loadApps() {
        val query = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val pm = packageManager

        apps = pm.queryIntentActivities(
            query,
            PackageManager.MATCH_ALL
        ).filter {
            it.activityInfo.packageName != packageName
        }.sortedBy {
            it.loadLabel(pm).toString().lowercase()
        }
    }

    private fun selected(): Set<String> {
        return favorites.getStringSet(
            "apps",
            emptySet()
        ) ?: emptySet()
    }

    private fun keyFor(app: ResolveInfo): String {
        return app.activityInfo.packageName +
            "/" + app.activityInfo.name
    }

    private fun favoritesList(): List<ResolveInfo> {
        val saved = selected()
        return apps.filter {
            saved.contains(keyFor(it))
        }
    }

    private fun purpleBackground(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(36, 12, 55),
                Color.rgb(104, 39, 125),
                Color.rgb(25, 8, 43)
            )
        )
    }

    private fun title(
        value: String,
        size: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(8, 12, 8, 12)
        }
    }

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

        root.addView(
            title("Purple Charm", 32).apply {
                typeface = Typeface.create(
                    "serif",
                    Typeface.BOLD
                )
            }
        )

        root.addView(
            title("Meu universo roxo ✦", 14)
        )

        pages = ViewFlipper(this)

        pages.addView(homePage(0))
        pages.addView(homePage(1))
        pages.addView(homePage(2))

        root.addView(
            pages,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        currentPage = currentPage.coerceIn(0, 2)
        pages.displayedChild = currentPage

        dots = title("", 18)
        root.addView(dots)
        updateDots()

        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(4, 8, 4, 8)
            setBackgroundColor(
                Color.rgb(30, 15, 47)
            )
        }

        favoritesList().take(3).forEach { app ->
            dock.addView(
                appItem(app, false),
                LinearLayout.LayoutParams(
                    0,
                    88,
                    1f
                )
            )
        }

        val drawerButton = title("☷ Apps", 17).apply {
            setOnClickListener {
                showDrawer()
            }
        }

        dock.addView(
            drawerButton,
            LinearLayout.LayoutParams(
                0,
                88,
                1f
            )
        )

        root.addView(dock)
    }

    private fun homePage(index: Int): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(12, 16, 12, 4)
        }

        when (index) {
            0 -> {
                page.addView(
                    title("✦ Minha página", 23)
                )
                page.addView(
                    title(
                        "Deslize para os lados para navegar",
                        14
                    )
                )
                page.addView(
                    appGrid(apps.take(12), false)
                )
            }

            1 -> {
                page.addView(
                    title("✦ Favoritos", 23)
                )

                val pinned = favoritesList()

                if (pinned.isEmpty()) {
                    page.addView(
                        title(
                            "Abra a gaveta e segure um aplicativo para favoritar",
                            16
                        )
                    )
                } else {
                    page.addView(
                        appGrid(pinned, false)
                    )
                }
            }

            else -> {
                page.addView(
                    title("✦ Mais aplicativos", 23)
                )
                page.addView(
                    appGrid(
                        apps.drop(12).take(12),
                        false
                    )
                )
            }
        }

        return ScrollView(this).apply {
            isFillViewport = true
            addView(page)
        }
    }

    private fun updateDots() {
        dots.text = (0..2).joinToString("  ") {
            if (it == currentPage) "●" else "○"
        }
    }

    private fun changePage(next: Int) {
        val target = next.coerceIn(0, 2)

        if (target == currentPage) return

        if (target > currentPage) {
            pages.setInAnimation(
                this,
                android.R.anim.slide_in_left
            )
            pages.setOutAnimation(
                this,
                android.R.anim.fade_out
            )
        } else {
            pages.setInAnimation(
                this,
                android.R.anim.fade_in
            )
            pages.setOutAnimation(
                this,
                android.R.anim.slide_out_right
            )
        }

        currentPage = target
        pages.displayedChild = currentPage
        updateDots()
    }

    private fun showDrawer() {
        drawerOpen = true
        makeRoot()

        val header = title(
            "☷ Aplicativos ✦",
            23
        )

        header.setOnClickListener {
            showHome()
        }

        root.addView(header)

        root.addView(
            title(
                "Toque para abrir • segure para favoritar",
                13
            )
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        scroll.addView(
            appGrid(apps, true)
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val back = title(
            "⌂ Voltar à tela inicial",
            17
        )

        back.setOnClickListener {
            showHome()
        }

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
                    height =
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(
                        GridLayout.UNDEFINED,
                        1f
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
        val pm = packageManager

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(3, 10, 3, 12)
        }

        val icon = ImageView(this).apply {
            setImageDrawable(app.loadIcon(pm))
            scaleType =
                ImageView.ScaleType.FIT_CENTER
            layoutParams =
                LinearLayout.LayoutParams(56, 56)
        }

        item.addView(icon)

        val label = title(
            app.loadLabel(pm).toString(),
            10
        ).apply {
            maxLines = 2
            setPadding(1, 3, 1, 0)
        }

        item.addView(label)

        item.setOnClickListener {
            try {
                val intent = Intent().apply {
                    setClassName(
                        app.activityInfo.packageName,
                        app.activityInfo.name
                    )
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(
                    this,
                    "Não foi possível abrir o aplicativo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (allowFavorite) {
            item.setOnLongClickListener {
                val updated =
                    selected().toMutableSet()

                val key = keyFor(app)

                val added = if (
                    updated.contains(key)
                ) {
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
                    if (added) {
                        "Adicionado aos favoritos"
                    } else {
                        "Removido dos favoritos"
                    },
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }

        return item
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                val dy = event.y - downY

                if (!drawerOpen) {
                    if (
                        abs(dx) > 100 &&
                        abs(dx) > abs(dy) * 1.3f
                    ) {
                        changePage(
                            currentPage +
                                if (dx < 0) 1 else -1
                        )
                        return true
                    }

                    if (
                        dy < -130 &&
                        abs(dy) > abs(dx) * 1.3f
                    ) {
                        showDrawer()
                        return true
                    }
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) {
            showHome()
        } else if (currentPage != 1) {
            changePage(1)
        } else {
            super.onBackPressed()
        }
    }
}
