
package com.purplecharm.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    private lateinit var backgroundImage: ImageView
    private lateinit var pages: ViewFlipper
    private lateinit var dots: TextView

    private var pageIndex = 0
    private var drawerOpen = false
    private var downX = 0f
    private var downY = 0f

    private val white = Color.WHITE
    private val lilac = Color.rgb(236, 201, 255)
    private val wallpaperRequest = 2202

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        loadApps()
        showHome()
    }

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)

        @Suppress("DEPRECATION")
        val found = packageManager.queryIntentActivities(
            intent, PackageManager.MATCH_ALL
        )

        apps.clear()
        apps.addAll(
            found.filter {
                it.activityInfo.packageName != packageName
            }.sortedBy {
                it.loadLabel(packageManager)
                    .toString().lowercase(Locale.ROOT)
            }
        )
    }

    private fun appKey(app: ResolveInfo): String =
        app.activityInfo.packageName + "/" +
            app.activityInfo.name

    private fun selected(): Set<String> =
        prefs.getStringSet("apps", emptySet()) ?: emptySet()

    private fun favorites(): List<ResolveInfo> {
        val saved = selected()
        return apps.filter { appKey(it) in saved }
    }

    private fun label(
        value: String,
        size: Float,
        bold: Boolean = false
    ): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(white)
        gravity = Gravity.CENTER
        if (bold) {
            typeface = Typeface.create("serif", Typeface.BOLD)
        }
    }

    private fun glass(
        radius: Int = 24,
        strong: Boolean = false
    ): GradientDrawable {
        val colors = if (strong) {
            intArrayOf(
                Color.argb(205, 111, 65, 150),
                Color.argb(190, 55, 22, 92),
                Color.argb(210, 34, 13, 63)
            )
        } else {
            intArrayOf(
                Color.argb(145, 182, 112, 220),
                Color.argb(95, 91, 42, 139),
                Color.argb(145, 43, 17, 83)
            )
        }

        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR, colors
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(
                dp(1).coerceAtLeast(1),
                Color.argb(165, 235, 189, 255)
            )
        }
    }

    private fun glassTile(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(105, 227, 174, 255),
                Color.argb(45, 127, 59, 173),
                Color.argb(75, 48, 20, 94)
            )
        ).apply {
            cornerRadius = dp(17).toFloat()
            setStroke(
                dp(1).coerceAtLeast(1),
                Color.argb(100, 241, 202, 255)
            )
        }

    private fun glassButton(
        value: String,
        size: Float = 16f
    ): TextView = label(value, size).apply {
        background = glass(18)
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun buildRoot(): LinearLayout {
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.BLACK)

        backgroundImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.wallpaper_neon)
        }

        frame.addView(
            backgroundImage,
            FrameLayout.LayoutParams(-1, -1)
        )

        applyWallpaper()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(9), dp(9), dp(9), dp(8))
        }

        frame.addView(
            content,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(frame)
        return content
    }

    private fun applyWallpaper() {
        val saved = prefs.getString("wallpaper_uri", null)

        if (saved.isNullOrEmpty()) {
            backgroundImage.setImageResource(
                R.drawable.wallpaper_neon
            )
            return
        }

        try {
            backgroundImage.setImageURI(Uri.parse(saved))
            if (backgroundImage.drawable == null) {
                backgroundImage.setImageResource(
                    R.drawable.wallpaper_neon
                )
            }
        } catch (_: Exception) {
            backgroundImage.setImageResource(
                R.drawable.wallpaper_neon
            )
        }
    }

    private fun wallpaperMenu() {
        AlertDialog.Builder(this)
            .setTitle("✦ Papel de parede")
            .setItems(
                arrayOf(
                    "Escolher imagem da galeria",
                    "Restaurar fundo neon"
                )
            ) { _, choice ->
                if (choice == 0) {
                    val intent = Intent(
                        Intent.ACTION_OPEN_DOCUMENT
                    ).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "image/*"
                        addFlags(
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        addFlags(
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        )
                    }

                    try {
                        startActivityForResult(
                            intent, wallpaperRequest
                        )
                    } catch (_: Exception) {
                        Toast.makeText(
                            this,
                            "Não foi possível abrir a galeria",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    prefs.edit()
                        .remove("wallpaper_uri")
                        .apply()
                    applyWallpaper()
                    Toast.makeText(
                        this,
                        "Fundo neon restaurado",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode, resultCode, data
        )

        if (
            requestCode != wallpaperRequest ||
            resultCode != RESULT_OK
        ) return

        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            backgroundImage.setImageURI(uri)

            if (backgroundImage.drawable == null) {
                throw IllegalArgumentException(
                    "Imagem inválida"
                )
            }

            prefs.edit()
                .putString("wallpaper_uri", uri.toString())
                .apply()

            Toast.makeText(
                this,
                "Papel de parede atualizado",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Não foi possível aplicar a imagem",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(
            value.lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace(
            Regex("\\p{InCombiningDiacriticalMarks}+"),
            ""
        )

    private fun customIcon(app: ResolveInfo): Drawable {
        val pkg = app.activityInfo.packageName
            .lowercase(Locale.ROOT)

        val name = normalize(
            app.loadLabel(packageManager).toString()
        )

        val iconName = when {

            pkg == "com.android.chrome" ||
            "chrome" in pkg ||
            name == "chrome" -> "chrome"

            "dropbox" in pkg ||
            "dropbox" in name -> "dropbox"

            "evernote" in pkg ||
            "evernote" in name -> "evernote"

            pkg == "jp.naver.line.android" ||
            name == "line" -> "line"

            pkg == "com.facebook.orca" ||
            "messenger" in name -> "orca"

            "whatsapp" in pkg ||
            "whatsapp" in name -> "whatsapp"

            "instagram" in pkg ||
            "instagram" in name -> "instagram"

            "facebook" in pkg ||
            "facebook" in name -> "facebook"

            "youtube" in pkg ||
            "youtube" in name -> "icon_youtube"

            "gmail" in pkg ||
            "gmail" in name -> "gmail"

            "google.android.apps.maps" in pkg ||
            name == "maps" ||
            name == "mapas" -> "maps"

            "google.android.apps.docs" in pkg ||
            name == "drive" ||
            name == "google drive" ||
            name == "onedrive" -> "googledrive"

            "googlequicksearchbox" in pkg ||
            name == "google" ||
            name == "pesquisa google" -> "googlesearch"

            "navigation" in name ||
            "navegacao" in name -> "navigation"

            "latitude" in name -> "latitude"

            "brave" in name ||
            "browser" in name ||
            "navegador" in name ||
            "firefox" in name -> "browser"

            "calculadora" in name ||
            "calculator" in name -> "calculator"

            "agenda" in name ||
            "calendario" in name ||
            "calendar" in name -> "calendar"

            "camera" in name -> "camera"

            "relogio" in name ||
            "clock" in name -> "clock"

            "contatos" in name ||
            "contacts" in name -> "contacts"

            "downloads" in name -> "downloads"

            "telefone" in name ||
            "phone" in name -> "phone"

            "configuracoes" in name ||
            "settings" in name -> "settings"

            "mensagens" in name ||
            "messages" in name -> "messaging"

            "musica" in name ||
            "music" in name -> "music"

            "fotos" in name ||
            "galeria" in name ||
            "gallery" in name -> "gallery"

            "email" in name -> "email"

            "pinterest" in pkg ||
            "pinterest" in name -> "pinterest"

            "skype" in name -> "skype"

            "twitter" in pkg ||
            "twitter" in name ||
            name == "x" -> "twitter"

            "foursquare" in name -> "foursquare"

            "flipboard" in name -> "flipboard"

            "mplayer" in name -> "mplayer"

            "soundhound" in name -> "soundhound"

            "gravador" in name ||
            "sound recorder" in name -> "soundrecorder"

            "play store" in name ||
            "market" in name -> "market"

            "local" == name -> "local"

            else -> ""
        }

        if (iconName.isNotEmpty()) {
            val id = resources.getIdentifier(
                iconName, "drawable", packageName
            )

            if (id != 0) {
                try {
                    @Suppress("DEPRECATION")
                    return resources.getDrawable(id)
                } catch (_: Exception) {
                }
            }
        }

        return app.loadIcon(packageManager)
    }

    private fun sectionHeader(
        heading: String,
        subtitle: String
    ): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass(22)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        panel.addView(label(heading, 22f))

        panel.addView(
            label(subtitle, 12f).apply {
                setTextColor(lilac)
                setPadding(0, dp(4), 0, 0)
            }
        )

        return panel
    }

    private fun titlePanel(): View {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = glass(25)
            setPadding(dp(8), dp(12), dp(8), dp(12))
        }

        panel.addView(
            label("✦ Purple Charm ✦", 25f, true)
        )

        panel.addView(
            label("Neon Glass Edition 2.2", 12f).apply {
                setTextColor(lilac)
                setPadding(0, dp(3), 0, 0)
            }
        )

        return panel
    }

    private fun showHome() {
        drawerOpen = false
        loadApps()

        val root = buildRoot()

        root.addView(titlePanel())

        pages = ViewFlipper(this)

        for (index in 0..2) {
            pages.addView(homePage(index))
        }

        root.addView(
            pages,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        pageIndex = pageIndex.coerceIn(0, 2)
        pages.displayedChild = pageIndex

        dots = label("", 19f).apply {
            setTextColor(lilac)
            setPadding(0, dp(4), 0, dp(5))
        }

        root.addView(dots)
        updateDots()

        root.addView(
            glassButton("⚙ Fundo", 13f).apply {
                setOnClickListener { wallpaperMenu() }
            }
        )

        root.addView(dock())
    }

    private fun homePage(index: Int): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(6))
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        when (index) {
            0 -> {
                content.addView(
                    sectionHeader(
                        "✦ Minha página",
                        "Deslize para navegar"
                    )
                )
                content.addView(
                    appGrid(apps.take(20), false)
                )
            }

            1 -> {
                content.addView(
                    sectionHeader(
                        "✦ Favoritos",
                        "Seus aplicativos especiais"
                    )
                )

                val pinned = favorites()

                if (pinned.isEmpty()) {
                    content.addView(
                        glassButton(
                            "Abra Apps e segure um ícone para favoritar",
                            13f
                        ).apply {
                            setPadding(
                                dp(10), dp(20),
                                dp(10), dp(20)
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
                    sectionHeader(
                        "✦ Mais aplicativos",
                        "Seu universo de aplicativos"
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
        return scroll
    }

    private fun updateDots() {
        dots.text = (0..2).joinToString("    ") {
            if (it == pageIndex) "●" else "○"
        }
    }

    private fun dock(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = glass(26, true)
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }

        val pinned = favorites().take(4)

        for (slot in 0..4) {
            val child: View = if (slot == 2) {
                glassButton("▦\nApps", 13f).apply {
                    setOnClickListener {
                        showDrawer()
                    }
                }
            } else {
                val favIndex =
                    if (slot < 2) slot else slot - 1

                if (favIndex < pinned.size) {
                    appTile(
                        pinned[favIndex], false, true
                    )
                } else {
                    glassButton("✧", 23f).apply {
                        setOnClickListener {
                            Toast.makeText(
                                this@MainActivity,
                                "Segure um aplicativo na gaveta para favoritar",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            outer.addView(
                child,
                LinearLayout.LayoutParams(
                    0, dp(61), 1f
                ).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
            )
        }

        return outer
    }

    private fun showDrawer() {
        drawerOpen = true
        loadApps()

        val root = buildRoot()

        root.addView(
            sectionHeader(
                "✦ Aplicativos ✦",
                "Toque para abrir • segure para favoritar"
            )
        )

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(0, dp(9), 0, dp(9))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(24, true)
            setPadding(dp(3), dp(10), dp(3), dp(10))
        }

        container.addView(appGrid(apps, true))
        scroll.addView(container)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(
            glassButton("⚙ Fundo", 13f).apply {
                setOnClickListener { wallpaperMenu() }
            }
        )

        root.addView(
            glassButton(
                "⌂ Voltar à tela inicial", 15f
            ).apply {
                setOnClickListener {
                    pageIndex = 0
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
            setPadding(dp(1), dp(12), dp(1), dp(12))
        }

        items.forEach { app ->
            grid.addView(
                appTile(app, allowFavorite, false),
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

    private fun appTile(
        app: ResolveInfo,
        allowFavorite: Boolean,
        compact: Boolean
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(6), dp(2), dp(8))
        }

        val iconSize = if (compact) 35 else 43

        val iconFrame = FrameLayout(this).apply {
            background = glassTile()
            elevation = dp(2).toFloat()
        }

        val icon = ImageView(this).apply {
            setImageDrawable(customIcon(app))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        iconFrame.addView(
            icon,
            FrameLayout.LayoutParams(
                dp(iconSize),
                dp(iconSize),
                Gravity.CENTER
            )
        )

        tile.addView(
            iconFrame,
            LinearLayout.LayoutParams(
                dp(iconSize + 10),
                dp(iconSize + 10)
            )
        )

        if (!compact) {
            tile.addView(
                label(
                    app.loadLabel(packageManager).toString(),
                    10f
                ).apply {
                    maxLines = 2
                    minLines = 2
                    setPadding(0, dp(6), 0, 0)
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

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
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
