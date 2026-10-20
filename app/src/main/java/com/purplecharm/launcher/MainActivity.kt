
package com.purplecharm.launcher

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private val prefs by lazy {
        getSharedPreferences("purple_charm_favorites", MODE_PRIVATE)
    }

    private val apps = mutableListOf<ResolveInfo>()
    private lateinit var root: LinearLayout
    private lateinit var pages: ViewFlipper
    private lateinit var dots: TextView

    private var pageIndex = 0
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.rgb(43, 13, 60)
        window.navigationBarColor = Color.rgb(31, 11, 46)

        loadApps()
        showHome()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

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
                it.loadLabel(packageManager).toString()
                    .lowercase(Locale.ROOT)
            }
        )
    }

    private fun appKey(app: ResolveInfo): String =
        app.activityInfo.packageName + "/" +
            app.activityInfo.name

    private fun selected(): Set<String> =
        prefs.getStringSet("apps", emptySet()) ?: emptySet()

    private fun favorites(): List<ResolveInfo> =
        apps.filter { selected().contains(appKey(it)) }

    private fun label(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(Color.WHITE)
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
            setPadding(dp(5), dp(7), dp(5), dp(4))
        }

        setContentView(root)
    }

    private fun normalize(value: String): String {
        val text = Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        )

        return text.replace(
            Regex("\\p{InCombiningDiacriticalMarks}+"),
            ""
        )
    }

    private fun customIcon(app: ResolveInfo): Drawable {
        val pkg = app.activityInfo.packageName
            .lowercase(Locale.ROOT)

        val name = normalize(
            app.loadLabel(packageManager).toString()
        )

        val iconName = when {
            "whatsapp" in pkg || "whatsapp" in name ->
                "whatsapp"

            "instagram" in pkg || "instagram" in name ->
                "instagram"

            "facebook" in pkg || "facebook" in name ->
                "facebook"

            "youtube" in pkg || "youtube" in name ->
                "icon_youtube"

            "gmail" in pkg || "gmail" in name ->
                "gmail"

            "maps" in pkg || "mapas" in name ->
                "maps"

            "docs" in pkg && "google" in pkg ->
                "googledrive"

            "drive" in name ->
                "googledrive"

            "chrome" in name ||
            "brave" in name ||
            "browser" in name ||
            "navegador" in name ->
                "browser"

            "calculadora" in name ||
            "calculator" in name ->
                "calculator"

            "agenda" in name ||
            "calendario" in name ->
                "calendar"

            "camera" in name ->
                "camera"

            "relogio" in name ||
            "clock" in name ->
                "clock"

            "contatos" in name ||
            "contacts" in name ->
                "contacts"

            "downloads" in name ->
                "downloads"

            "telefone" in name ||
            "phone" in name ->
                "phone"

            "configuracoes" in name ||
            "settings" in name ->
                "settings"

            "mensagens" in name ||
            "messages" in name ->
                "messaging"

            "musica" in name ||
            "music" in name ->
                "music"

            "fotos" in name ||
            "galeria" in name ||
            "gallery" in name ->
                "gallery"

            "email" in name ->
                "email"

            "pinterest" in name ->
                "pinterest"

            "skype" in name ->
                "skype"

            "twitter" in name ->
                "twitter"

            "foursquare" in name ->
                "foursquare"

            "flipboard" in name ->
                "flipboard"

            "mplayer" in name ->
                "mplayer"

            "soundhound" in name ->
                "soundhound"

            "gravador" in name ->
                "soundrecorder"

            "market" in name ||
            "play store" in name ->
                "market"

            else -> ""
        }

        if (iconName.isNotEmpty()) {
            val id = resources.getIdentifier(
                iconName,
                "drawable",
                packageName
            )

            if (id != 0) {
                try {
                    @Suppress("DEPRECATION")
                    val icon = resources.getDrawable(id)
                    return icon
                } catch (_: Exception) {
                    // Usa o icone normal como alternativa.
                }
            }
        }

        return app.loadIcon(packageManager)
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
        val scroll = ScrollView(this).apply {
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(12), dp(4), dp(8))
        }

        when (index) {
            0 -> {
                content.addView(
                    label("✦ Minha página", 23f)
                )

                content.addView(
                    label("Deslize para navegar", 12f).apply {
                        setPadding(0, dp(5), 0, dp(12))
                    }
                )

                content.addView(
                    appGrid(apps.take(20), false)
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
                                dp(8), dp(18), dp(8), 0
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
                    label("✦ Mais aplicativos", 23f)
                )

                content.addView(
                    appGrid(apps.drop(20).take(30), false)
                )
            }
        }

        scroll.addView(content)
        return scroll
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
            setPadding(dp(2), dp(7), dp(2), dp(7))
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
                    val favIndex =
                        if (slot < 2) slot else slot - 1

                    if (favIndex < pinned.size) {
                        appTile(
                            pinned[favIndex],
                            false,
                            true
                        )
                    } else {
                        label("✧", 25f).apply {
                            setOnClickListener {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Segure um app na gaveta para adicionar aos favoritos",
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

        val header = label("✦ Aplicativos ✦", 24f)
        header.setOnClickListener { showHome() }
        root.addView(header)

        root.addView(
            label(
                "Toque para abrir • segure para favoritar",
                12f
            ).apply {
                setPadding(0, dp(5), 0, dp(12))
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
            label("⌂ Voltar à tela inicial", 16f).apply {
                setPadding(0, dp(10), 0, dp(10))
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
            columnCount = 5
            setPadding(
                dp(1), dp(10), dp(1), dp(12)
            )
        }

        items.forEach { app ->
            grid.addView(
                appTile(app, allowFavorite, false),
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
                dp(2), dp(6), dp(2), dp(8)
            )
        }

        tile.addView(
            ImageView(this).apply {
                setImageDrawable(customIcon(app))

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
                    app.loadLabel(packageManager)
                        .toString(),
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
                val updated = selected().toMutableSet()

                val added = if (updated.contains(key)) {
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
                    if (added)
                        "Adicionado aos favoritos"
                    else
                        "Removido dos favoritos",
                    Toast.LENGTH_SHORT
                ).show()

                true
            }
        }

        return tile
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!drawerOpen) {
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
        }

        return super.dispatchTouchEvent(event)
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
