
package com.purplecharm.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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

    private val prefs by lazy {
        getSharedPreferences(
            "purple_charm_favorites",
            MODE_PRIVATE
        )
    }

    private val apps = mutableListOf<ResolveInfo>()

    private lateinit var root: LinearLayout
    private lateinit var pages: ViewFlipper
    private lateinit var dots: TextView

    private var pageIndex = 0
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f

    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(43, 13, 60)
        window.navigationBarColor = Color.rgb(31, 11, 46)

        loadApps()
        showHome()
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val found = packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_ALL
        )

        apps.clear()

        apps.addAll(
            found.filter {
                it.activityInfo.packageName != packageName
            }.sortedBy {
                it.loadLabel(packageManager)
                    .toString()
                    .lowercase()
            }
        )
    }

    private fun selected(): Set<String> {
        return prefs.getStringSet(
            "apps",
            emptySet()
        ) ?: emptySet()
    }

    private fun appKey(app: ResolveInfo): String {
        return app.activityInfo.packageName +
            "/" +
            app.activityInfo.name
    }

    private fun favorites(): List<ResolveInfo> {
        return apps.filter {
            selected().contains(appKey(it))
        }
    }

    private fun dp(value: Int): Int {
        return (
            value * resources.displayMetrics.density
        ).toInt()
    }

    private fun label(
        text: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(white)
            gravity = Gravity.CENTER

            if (bold) {
                typeface = Typeface.create(
                    "serif",
                    Typeface.BOLD
                )
            }
        }
    }

    private fun background(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(45, 14, 65),
                Color.rgb(109, 42, 126),
                Color.rgb(40, 12, 58)
            )
        )
    }

    private fun buildRoot() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = background()

            setPadding(
                dp(5),
                dp(7),
                dp(5),
                dp(4)
            )
        }

        setContentView(root)
    }

    private fun showHome() {
        drawerOpen = false
        buildRoot()

        root.addView(
            label("Purple Charm", 28f, true)
        )

        root.addView(
            label("Meu universo roxo ✦", 13f).apply {
                setPadding(0, 0, 0, dp(12))
            }
        )

        pages = ViewFlipper(this)

        for (index in 0..2) {
            pages.addView(homePage(index))
        }

        root.addView(
            pages,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        pageIndex = pageIndex.coerceIn(0, 2)
        pages.displayedChild = pageIndex

        dots = label("", 19f)
        root.addView(dots)

        updateDots()
        root.addView(dock())
    }

    private fun homePage(index: Int): View {

        // CORREÇÃO DO ERRO fillViewport
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(4),
                dp(12),
                dp(4),
                dp(8)
            )
        }

        when (index) {

            0 -> {
                content.addView(
                    label("✦ Minha página", 23f)
                )

                content.addView(
                    label(
                        "Deslize para navegar",
                        12f
                    ).apply {
                        setPadding(
                            0,
                            dp(5),
                            0,
                            dp(12)
                        )
                    }
                )

                content.addView(
                    appGrid(
                        apps.take(20),
                        false
                    )
                )
            }

            1 -> {
                content.addView(
                    label("✦ Favoritos", 23f)
                )

                val pinned = favorites()

                if (pinned.isEmpty()) {
                    content.addView(
                        label(
                            "Abra Apps e segure um ícone para favoritar",
                            13f
                        ).apply {
                            setPadding(
                                dp(8),
                                dp(18),
                                dp(8),
                                0
                            )
                        }
                    )
                } else {
                    content.addView(
                        appGrid(pinned, false)
                    )
                }
            }

            else -> {
                content.addView(
                    label(
                        "✦ Mais aplicativos",
                        23f
                    )
                )

                content.addView(
                    appGrid(
                        apps.drop(20).take(30),
                        false
                    )
                )
            }
        }

        scroll.addView(content)

        scroll.setOnTouchListener { _, event ->
            handleSwipe(event)
        }

        return scroll
    }

    private fun handleSwipe(
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

                if (
                    abs(dx) > dp(65) &&
                    abs(dx) > abs(dy) * 1.3f
                ) {
                    pageIndex = (
                        pageIndex +
                            if (dx < 0) 1 else -1
                    ).coerceIn(0, 2)

                    pages.displayedChild = pageIndex
                    updateDots()

                    return true
                }

                if (
                    dy < -dp(95) &&
                    abs(dy) > abs(dx) * 1.5f
                ) {
                    showDrawer()
                    return true
                }
            }
        }

        return false
    }

    private fun updateDots() {
        dots.text = (0..2).joinToString("   ") {
            if (it == pageIndex) "●" else "○"
        }
    }

    private fun dock(): View {

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            setPadding(
                dp(2),
                dp(7),
                dp(2),
                dp(7)
            )

            setBackgroundColor(
                Color.argb(145, 25, 9, 41)
            )
        }

        val pinned = favorites().take(4)

        for (slot in 0..4) {

            val child: View = when {

                slot == 2 -> {
                    label("▦\nApps", 14f).apply {
                        setOnClickListener {
                            showDrawer()
                        }
                    }
                }

                else -> {
                    val favIndex = if (slot < 2) {
                        slot
                    } else {
                        slot - 1
                    }

                    if (favIndex < pinned.size) {
                        appTile(
                            pinned[favIndex],
                            false,
                            compact = true
                        )
                    } else {
                        label("✧", 25f).apply {
                            setOnClickListener {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Segure um app na gaveta para adicioná-lo aos favoritos",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            }

            bar.addView(
                child,
                LinearLayout.LayoutParams(
                    0,
                    dp(62),
                    1f
                )
            )
        }

        return bar
    }

    private fun showDrawer() {
        drawerOpen = true
        loadApps()
        buildRoot()

        val header = label(
            "✦ Aplicativos ✦",
            24f
        )

        header.setOnClickListener {
            showHome()
        }

        root.addView(header)

        root.addView(
            label(
                "Toque para abrir • segure para favoritar",
                12f
            ).apply {
                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(12)
                )
            }
        )

        val scroll = ScrollView(this)

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

        root.addView(
            label(
                "⌂ Voltar à tela inicial",
                16f
            ).apply {
                setPadding(
                    0,
                    dp(10),
                    0,
                    dp(10)
                )

                setOnClickListener {
                    showHome()
                }
            }
        )
    }

    private fun appGrid(
        items: List<ResolveInfo>,
        allowFavorite: Boolean
    ): GridLayout {

        val grid = GridLayout(this).apply {

            // CINCO ÍCONES POR LINHA
            columnCount = 5

            setPadding(
                dp(1),
                dp(10),
                dp(1),
                dp(12)
            )
        }

        items.forEach { app ->

            grid.addView(
                appTile(
                    app,
                    allowFavorite,
                    compact = false
                ),
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

    private fun appTile(
        app: ResolveInfo,
        allowFavorite: Boolean,
        compact: Boolean
    ): View {

        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            setPadding(
                dp(2),
                dp(6),
                dp(2),
                dp(8)
            )
        }

        tile.addView(
            ImageView(this).apply {

                setImageDrawable(
                    app.loadIcon(packageManager)
                )

                scaleType =
                    ImageView.ScaleType.FIT_CENTER

                layoutParams =
                    LinearLayout.LayoutParams(
                        dp(if (compact) 33 else 42),
                        dp(if (compact) 33 else 42)
                    )
            }
        )

        if (!compact) {

            tile.addView(
                label(
                    app.loadLabel(
                        packageManager
                    ).toString(),
                    10f
                ).apply {
                    maxLines = 2
                    minLines = 2
                }
            )
        }

        tile.setOnClickListener {

            try {

                val launch = Intent().apply {

                    setClassName(
                        app.activityInfo.packageName,
                        app.activityInfo.name
                    )

                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                    )
                }

                startActivity(launch)

            } catch (_: Exception) {

                Toast.makeText(
                    this,
                    "Não foi possível abrir o aplicativo",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        if (allowFavorite) {

            tile.setOnLongClickListener {

                val key = appKey(app)

                val updated =
                    selected().toMutableSet()

                val added =
                    if (updated.contains(key)) {
                        updated.remove(key)
                        false
                    } else {
                        updated.add(key)
                        true
                    }

                prefs.edit()
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

        return tile
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        if (drawerOpen || pageIndex != 0) {

            pageIndex = 0
            showHome()

        } else {

            super.onBackPressed()
        }
    }
}
