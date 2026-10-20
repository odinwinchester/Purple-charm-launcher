package com.purplecharm.launcher

import android.app.Activity
import android.app.Dialog
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import java.io.IOException
import android.graphics.Camera
import android.graphics.Matrix
import android.view.animation.Animation
import android.view.animation.Transformation
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation
import android.view.animation.AnimationSet
import android.view.animation.DecelerateInterpolator
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowInsets
import android.os.Build
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

    private var dragging = false
    private var switchingPage = false
    private val homeCapacity = 40

    private fun savedPage(index: Int): MutableList<String> {
        val raw = prefs.getString("layout_page_$index", null)
        if (raw != null) return raw.split("|").filter { it.isNotBlank() }.toMutableList()
        val initial = when (index) {
            0 -> apps.take(20)
            1 -> favorites()
            else -> apps.drop(20).take(30)
        }.map { appKey(it) }
        return initial.toMutableList()
    }

    private fun savePage(index: Int, keys: List<String>) {
        prefs.edit().putString("layout_page_$index", keys.distinct().joinToString("|")).apply()
    }

    private fun savedDock(): MutableList<String> {
        val raw = prefs.getString("layout_dock", null)
        return if (raw == null) favorites().take(4).map { appKey(it) }.toMutableList()
        else raw.split("|").filter { it.isNotBlank() }.toMutableList()
    }

    private fun saveDock(keys: List<String>) {
        prefs.edit().putString("layout_dock", keys.take(4).joinToString("|")).apply()
    }

    private fun resolveKey(key: String): ResolveInfo? = apps.firstOrNull { appKey(it) == key }

    private fun startAppDrag(view: View, app: ResolveInfo): Boolean {
        val data = ClipData.newPlainText("purplecharm", appKey(app))
        dragging = view.startDragAndDrop(data, View.DragShadowBuilder(view), null, 0)
        return dragging
    }

    private fun draggedKey(event: DragEvent): String? {
        val clip = event.clipData ?: return null
        if (clip.itemCount == 0) return null
        val key = clip.getItemAt(0).text?.toString() ?: return null
        return key.takeIf { resolveKey(it) != null }
    }

    private fun acceptDrop(view: View, action: (String) -> Unit) {
        view.setOnDragListener { target, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    val ok = event.clipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true
                    if (ok) target.alpha = 0.8f
                    ok
                }
                DragEvent.ACTION_DRAG_ENTERED -> { target.alpha = 0.55f; true }
                DragEvent.ACTION_DRAG_EXITED -> { target.alpha = 0.8f; true }
                DragEvent.ACTION_DROP -> {
                    val key = draggedKey(event)
                    if (key != null) { action(key); true } else false
                }
                DragEvent.ACTION_DRAG_ENDED -> { target.alpha = 1f; dragging = false; true }
                else -> true
            }
        }
    }

    private fun placeOnPage(key: String, index: Int, position: Int? = null) {
        val list = savedPage(index)
        if (list.size >= homeCapacity && key !in list) {
            Toast.makeText(this, "Página cheia", Toast.LENGTH_SHORT).show()
            return
        }
        list.remove(key)
        val at = (position ?: list.size).coerceIn(0, list.size)
        list.add(at, key)
        for (other in 0..2) if (other != index) {
            val others = savedPage(other)
            if (others.remove(key)) savePage(other, others)
        }
        val slots = dockSlots()
        val dockAt = slots.indexOf(key)
        if (dockAt >= 0) {
            slots[dockAt] = ""
            saveDockSlots(slots)
        }
        savePage(index, list)
        pageIndex = index
        showHome()
    }

    // O dock usa quatro posições fixas, sem deslocar os outros ícones.
    private fun dockSlots(): MutableList<String> {
        val raw = prefs.getString("layout_dock_slots", null)
        if (raw != null) {
            return raw.split("|").take(4).toMutableList().apply {
                while (size < 4) add("")
            }
        }
        return savedDock().take(4).toMutableList().apply {
            while (size < 4) add("")
        }
    }

    private fun saveDockSlots(slots: List<String>) {
        prefs.edit().putString("layout_dock_slots", slots.take(4).joinToString("|")).apply()
    }

    private fun placeInDock(key: String, position: Int) {
        val slots = dockSlots()
        val destination = position.coerceIn(0, 3)
        val previous = slots[destination]
        val oldSlot = slots.indexOf(key)
        if (oldSlot >= 0) slots[oldSlot] = ""
        slots[destination] = key
        saveDockSlots(slots)
        for (page in 0..2) {
            val list = savedPage(page)
            if (list.remove(key)) savePage(page, list)
        }
        // O atalho substituído continua disponível na gaveta.
        if (previous.isNotEmpty() && previous != key && oldSlot < 0) {
            Toast.makeText(this, "Atalho anterior disponível na gaveta", Toast.LENGTH_SHORT).show()
        }
        showHome()
    }

    private fun removeShortcut(key: String) {
        var changed = false
        for (page in 0..2) {
            val list = savedPage(page)
            if (list.remove(key)) {
                savePage(page, list)
                changed = true
            }
        }
        val slots = dockSlots()
        val at = slots.indexOf(key)
        if (at >= 0) {
            slots[at] = ""
            saveDockSlots(slots)
            changed = true
        }
        if (changed) {
            Toast.makeText(this, "Atalho removido (aplicativo mantido)", Toast.LENGTH_SHORT).show()
            showHome()
        }
    }

    private val lilac = Color.rgb(236, 201, 255)
    private val wallpaperRequest = 2202
    private fun setting(key: String, default: String): String =
        prefs.getString("pc_" + key, default) ?: default

    private fun saveSetting(key: String, value: String) {
        prefs.edit().putString("pc_" + key, value).apply()
    }

    private fun chooseSetting(
        title: String, key: String,
        labels: Array<String>, values: Array<String>, default: String
    ) {
        val selectedIndex = values.indexOf(setting(key, default)).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, index ->
                saveSetting(key, values[index])
                dialog.dismiss()
                showHome()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Janela de configurações com o fundo neon independente da tela inicial.
    private fun settingsMenu() {
        val dialog = Dialog(this)
        val frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        val backdrop = ImageView(this).apply {
            setImageResource(R.drawable.wallpaper_neon)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        frame.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        frame.addView(View(this).apply { setBackgroundColor(Color.argb(145, 0, 0, 0)) },
            FrameLayout.LayoutParams(-1, -1))
        val scroll = ScrollView(this)
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }
        menu.addView(label("✦ Purple Charm • Configurações", 22f, true).apply {
            setPadding(dp(4), dp(10), dp(4), dp(24))
        })
        fun option(name: String, action: () -> Unit) {
            val item = glassButton(name, 15f).apply {
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener { dialog.dismiss(); action() }
            }
            menu.addView(item, LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(0, dp(5), 0, dp(5))
            })
        }
        option("✦ Deslizar para cima: " + gestureName(setting("swipe_up", "drawer"))) {
            gestureSetting("Deslizar para cima", "swipe_up", "drawer")
        }
        option("✦ Deslizar para baixo: " + gestureName(setting("swipe_down", "none"))) {
            gestureSetting("Deslizar para baixo", "swipe_down", "none")
        }
        option("✦ Colunas da gaveta: " + setting("columns", "5")) {
            chooseSetting("Colunas da gaveta", "columns",
                arrayOf("4 colunas", "5 colunas", "6 colunas"),
                arrayOf("4", "5", "6"), "5")
        }
        option("✦ Papéis de parede Crystal Eye") { wallpaperMenu() }
        option("✦ Aplicar Olho de Cristal na tela de bloqueio") { confirmLockWallpaper() }
        option("✦ Efeito de transição: " + transitionName(setting("transition", "cube"))) {
            chooseSetting("Transição entre páginas", "transition",
                arrayOf("Cubo 3D", "Giro 3D", "Zoom Crystal", "Deslizar", "Sem animação"),
                arrayOf("cube", "flip", "zoom", "slide", "none"), "cube")
        }
        option("✦ Restaurar gestos e grade") {
            AlertDialog.Builder(this).setTitle("Restaurar configurações?")
                .setMessage("Isso não altera seus wallpapers nem a organização.")
                .setPositiveButton("Restaurar") { _, _ ->
                    prefs.edit().remove("pc_swipe_up").remove("pc_swipe_down")
                        .remove("pc_columns").apply()
                    showHome()
                }.setNegativeButton("Cancelar", null).show()
        }
        option("✦ Restaurar organização dos ícones") {
            AlertDialog.Builder(this).setTitle("Restaurar organização?")
                .setMessage("As posições das páginas e do dock serão apagadas.")
                .setPositiveButton("Restaurar") { _, _ ->
                    prefs.edit().remove("layout_page_0").remove("layout_page_1")
                        .remove("layout_page_2").remove("layout_dock")
                        .remove("layout_dock_slots").apply()
                    showHome()
                }.setNegativeButton("Cancelar", null).show()
        }
        option("✕ Fechar") { }
        scroll.addView(menu)
        frame.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        dialog.setContentView(frame)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(-1, -1)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun transitionName(value: String): String = when (value) {
        "cube" -> "Cubo 3D"
        "flip" -> "Giro 3D"
        "zoom" -> "Zoom Crystal"
        "slide" -> "Deslizar"
        else -> "Sem animação"
    }

    private fun gestureName(value: String): String = when (value) {
        "drawer" -> "Abrir aplicativos"
        "settings" -> "Configurações"
        "wallpaper" -> "Papel de parede"
        "home" -> "Página inicial"
        else -> "Nenhuma ação"
    }

    private fun gestureSetting(title: String, key: String, default: String) {
        val values = arrayOf("drawer", "settings", "wallpaper", "home", "none")
        chooseSetting(title, key, values.map { gestureName(it) }.toTypedArray(), values, default)
    }

    private fun runGesture(action: String): Boolean {
        when (action) {
            "drawer" -> showDrawer()
            "settings" -> settingsMenu()
            "wallpaper" -> wallpaperMenu()
            "home" -> { pageIndex = 0; showHome() }
            else -> return false
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // O papel de parede ocupa toda a tela, mas os controles
        // respeitam as barras do Android.
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
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
        setTextColor(Color.WHITE)
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

    // CRYSTAL GLASS:
    // Apenas o quadrado do ícone tem vidro.
    // O espaço entre ícones fica transparente.
    private fun glassTile(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(60, 227, 174, 255),
                Color.argb(20, 127, 59, 173),
                Color.argb(38, 48, 20, 94)
            )
        ).apply {
            cornerRadius = dp(17).toFloat()
            setStroke(
                dp(1).coerceAtLeast(1),
                Color.argb(150, 241, 202, 255)
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
            setImageResource(R.drawable.purple_original_wallpaper)
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

        // Mantém o título abaixo da barra de status e os botões
        // acima da barra de navegação, independentemente do aparelho.
        frame.setOnApplyWindowInsetsListener { _, insets ->
            val topInset: Int
            val bottomInset: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(
                    WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()
                )
                topInset = bars.top
                bottomInset = bars.bottom
            } else {
                @Suppress("DEPRECATION")
                topInset = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottomInset = insets.systemWindowInsetBottom
            }
            content.setPadding(
                dp(9), topInset + dp(9),
                dp(9), bottomInset + dp(8)
            )
            insets
        }

        frame.addView(
            content,
            FrameLayout.LayoutParams(-1, -1)
        )

        setContentView(frame)
        return content
    }

    // A escolha do wallpaper do launcher não altera o wallpaper do sistema.
    private fun applyWallpaper() {
        when (prefs.getString("pc_wallpaper_choice", "floral")) {
            "eye_crystal" -> backgroundImage.setImageResource(R.drawable.purple_eye_crystal)
            "eye_royal" -> backgroundImage.setImageResource(R.drawable.purple_eye_royal)
            "gallery" -> {
                val saved = prefs.getString("wallpaper_uri", null)
                if (!saved.isNullOrEmpty()) {
                    try {
                        backgroundImage.setImageURI(Uri.parse(saved))
                        if (backgroundImage.drawable != null) return
                    } catch (_: Exception) { }
                }
                backgroundImage.setImageResource(R.drawable.purple_original_wallpaper)
            }
            else -> backgroundImage.setImageResource(R.drawable.purple_original_wallpaper)
        }
    }

    private fun chooseBuiltInWallpaper(value: String) {
        prefs.edit().putString("pc_wallpaper_choice", value).apply()
        applyWallpaper()
        Toast.makeText(this, "Wallpaper aplicado à tela inicial", Toast.LENGTH_SHORT).show()
    }

    private fun wallpaperMenu() {
        AlertDialog.Builder(this)
            .setTitle("✦ Papéis de parede Crystal Eye")
            .setItems(arrayOf(
                "Floral original (padrão)",
                "Olho de Cristal • primeira arte",
                "Olho Royal • segunda arte",
                "Escolher imagem da galeria",
                "Aplicar Olho de Cristal na tela de bloqueio"
            )) { _, choice ->
                when (choice) {
                    0 -> chooseBuiltInWallpaper("floral")
                    1 -> chooseBuiltInWallpaper("eye_crystal")
                    2 -> chooseBuiltInWallpaper("eye_royal")
                    3 -> {
                        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }
                        try { startActivityForResult(intent, wallpaperRequest) }
                        catch (_: Exception) {
                            Toast.makeText(this, "Não foi possível abrir a galeria", Toast.LENGTH_SHORT).show()
                        }
                    }
                    4 -> confirmLockWallpaper()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun confirmLockWallpaper() {
        AlertDialog.Builder(this)
            .setTitle("Olho de Cristal • tela de bloqueio")
            .setMessage("Aplicar a primeira arte do olho na tela de bloqueio do Android? " +
                "O sistema do POCO pode impedir ou substituir essa alteração.")
            .setPositiveButton("Aplicar") { _, _ -> applyLockWallpaper() }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun applyLockWallpaper() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Toast.makeText(this, "Seu Android não suporta esta opção diretamente", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val manager = WallpaperManager.getInstance(this)
            if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
                Toast.makeText(this, "Permissão de wallpaper indisponível neste aparelho", Toast.LENGTH_LONG).show()
                return
            }
            resources.openRawResource(R.drawable.purple_eye_crystal).use { stream ->
                manager.setStream(stream, null, true, WallpaperManager.FLAG_LOCK)
            }
            Toast.makeText(this, "Imagem enviada para a tela de bloqueio", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this,
                "O sistema não permitiu a alteração. Aplique a imagem nas configurações do POCO.",
                Toast.LENGTH_LONG).show()
        }
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
                throw IllegalArgumentException("Imagem inválida")
            }

            prefs.edit()
                .putString("wallpaper_uri", uri.toString())
                .putString("pc_wallpaper_choice", "gallery")
                .apply()

            Toast.makeText(
                this,
                "Papel de parede atualizado",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            applyWallpaper()
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

            name == "local" -> "local"

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
            label("Crystal Eye Edition • 2.9", 12f).apply {
                setTextColor(lilac)
                setPadding(0, dp(3), 0, 0)
            }
        )

        return panel
    }

    // Animações nativas: não exigem bibliotecas adicionais nem novos recursos.
    private class Rotation3D(
        private val startDegrees: Float,
        private val endDegrees: Float,
        private val pivotAtRight: Boolean
    ) : Animation() {
        private val camera = Camera()
        private var centerX = 0f
        private var centerY = 0f
        override fun initialize(width: Int, height: Int, parentWidth: Int, parentHeight: Int) {
            super.initialize(width, height, parentWidth, parentHeight)
            centerX = if (pivotAtRight) width.toFloat() else 0f
            centerY = height / 2f
        }
        override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
            val degrees = startDegrees + (endDegrees - startDegrees) * interpolatedTime
            val matrix: Matrix = t.matrix
            camera.save()
            camera.rotateY(degrees)
            camera.getMatrix(matrix)
            camera.restore()
            // Ajuste da perspectiva: o Android Camera trabalha em pixels de câmera.
            matrix.preTranslate(-centerX, -centerY)
            matrix.postTranslate(centerX, centerY)
        }
    }

    private fun pageAnimation(style: String, incoming: Boolean, forward: Boolean): Animation {
        val duration = 380L
        val animation: Animation = when (style) {
            "cube", "flip" -> {
                val angle = if (style == "cube") 88f else 72f
                val start = if (incoming) (if (forward) -angle else angle) else 0f
                val end = if (incoming) 0f else (if (forward) angle else -angle)
                val rightPivot = if (incoming) forward else !forward
                Rotation3D(start, end, rightPivot)
            }
            "zoom" -> AnimationSet(true).apply {
                val from = if (incoming) 0.72f else 1f
                val to = if (incoming) 1f else 1.15f
                addAnimation(ScaleAnimation(from, to, from, to,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f))
                addAnimation(AlphaAnimation(if (incoming) 0.2f else 1f,
                    if (incoming) 1f else 0f))
            }
            else -> TranslateAnimation(
                Animation.RELATIVE_TO_SELF, if (incoming) (if (forward) 1f else -1f) else 0f,
                Animation.RELATIVE_TO_SELF, if (incoming) 0f else (if (forward) -1f else 1f),
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
            )
        }
        animation.duration = duration
        animation.interpolator = DecelerateInterpolator(1.15f)
        animation.fillAfter = false
        return animation
    }

    private fun changePage(destination: Int) {
        if (drawerOpen || dragging || switchingPage || !::pages.isInitialized) return
        val target = destination.coerceIn(0, 2)
        if (target == pageIndex) return
        val style = setting("transition", "cube")
        val forward = target > pageIndex
        if (style == "none") {
            pages.inAnimation = null
            pages.outAnimation = null
        } else {
            pages.inAnimation = pageAnimation(style, true, forward)
            pages.outAnimation = pageAnimation(style, false, forward)
        }
        pageIndex = target
        pages.displayedChild = target
        updateDots()
        if (style != "none") {
            switchingPage = true
            pages.postDelayed({ switchingPage = false }, 410L)
        }
    }

    private fun showHome() {
        drawerOpen = false
        loadApps()

        val root = buildRoot()

        // Layout clássico: ícones e wallpaper têm prioridade visual.
        // O título permanece disponível nas configurações.

        pages = ViewFlipper(this)
        switchingPage = false

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

        root.addView(dock())
    }

    private fun homePage(index: Int): View {
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(6))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val titles = arrayOf("✦ Minha página", "✦ Favoritos", "✦ Mais aplicativos")
        // Sem o grande cabeçalho de vidro que não existe no tema original.
        // Solte nos botões para enviar o ícone a outra página.
        val pageTargets = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (destination in 0..2) {
            if (destination == index) continue
            val target = glassButton("⇢ Página ${destination + 1}", 12f)
            acceptDrop(target) { key -> placeOnPage(key, destination) }
            pageTargets.addView(target, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), dp(4), dp(2), dp(4))
            })
        }
        val list = savedPage(index)
        val grid = GridLayout(this).apply {
            columnCount = setting("columns", "5").toIntOrNull()?.coerceIn(4, 6) ?: 5
            setPadding(dp(1), dp(12), dp(1), dp(12))
        }
        list.forEachIndexed { position, key ->
            val app = resolveKey(key) ?: return@forEachIndexed
            val tile = appTile(app, false, false)
            acceptDrop(tile) { dropped -> placeOnPage(dropped, index, position) }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        content.addView(grid)
        val add = glassButton("＋ Solte aqui para adicionar à página ${index + 1}", 13f)
        acceptDrop(add) { key -> placeOnPage(key, index) }
        val remove = glassButton("× Solte aqui para remover o atalho", 12f)
        acceptDrop(remove) { key -> removeShortcut(key) }
        val tools = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tools.addView(pageTargets)
        tools.addView(add)
        tools.addView(remove)
        tools.visibility = View.GONE
        val toggle = label("✦ Editar área de trabalho", 12f).apply {
            setTextColor(lilac)
            setPadding(dp(6), dp(7), dp(6), dp(7))
            setOnClickListener {
                tools.visibility = if (tools.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            }
        }
        content.addView(toggle)
        content.addView(tools)
        scroll.addView(content)
        return scroll
    }

    private fun updateDots() {
        dots.text = (0..2).joinToString("     ") {
            if (it == pageIndex) "◉" else "○"
        }
    }

    private fun dock(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = resources.getDrawable(R.drawable.purple_original_dock, theme)
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        val keys = dockSlots()
        for (slot in 0..4) {
            val child: View = if (slot == 2) {
                ImageView(this).apply {
                    setImageResource(R.drawable.purple_original_apps)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setOnClickListener { showDrawer() }
                    setOnLongClickListener { settingsMenu(); true }
                }
            } else {
                val dockIndex = if (slot < 2) slot else slot - 1
                val app = keys.getOrNull(dockIndex)?.takeIf { it.isNotEmpty() }?.let { resolveKey(it) }
                val view = if (app != null) appTile(app, false, true)
                           else label("+", 22f).apply { setTextColor(lilac) }
                acceptDrop(view) { key -> placeInDock(key, dockIndex) }
                view
            }
            outer.addView(child, LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return outer
    }

    private fun showDrawer() {
        drawerOpen = true
        loadApps()

        val root = buildRoot()

        root.addView(label("Aplicativos", 19f, true).apply {
            setPadding(0, dp(6), 0, dp(6))
        })

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(0, dp(9), 0, dp(9))
        }

        // CRYSTAL GLASS:
        // Sem painel roxo escuro atrás da grade.
        // O wallpaper fica visível entre os ícones.
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = null
            setPadding(dp(3), dp(10), dp(3), dp(10))
        }

        // Destinos ficam acima da grade para não exigir rolar por todos os apps.
        for (page in 0..2) {
            val target = glassButton("＋ Solte aqui para adicionar à página ${page + 1}", 13f)
            acceptDrop(target) { key -> placeOnPage(key, page) }
            container.addView(target)
        }
        val dockTarget = glassButton("✧ Solte aqui para adicionar ao dock", 13f)
        acceptDrop(dockTarget) { key ->
            val free = dockSlots().indexOfFirst { it.isEmpty() }
            placeInDock(key, if (free >= 0) free else 3)
        }
        container.addView(dockTarget)
        val dockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (slot in 0..3) {
            val target = glassButton("Dock ${slot + 1}", 12f)
            acceptDrop(target) { key -> placeInDock(key, slot) }
            dockRow.addView(target, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), dp(4), dp(2), dp(4))
            })
        }
        container.addView(dockRow)
        container.addView(appGrid(apps, true))
        scroll.addView(container)

        root.addView(
            scroll,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(
            glassButton("⚙ Configurações", 13f).apply {
                setOnClickListener { settingsMenu() }
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
            columnCount = setting("columns", "5").toIntOrNull()?.coerceIn(4, 6) ?: 5
            background = null
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
            background = null
            setPadding(dp(2), dp(6), dp(2), dp(8))
        }

        val iconSize = if (compact) 35 else 43

        val iconFrame = FrameLayout(this).apply {
            background = resources.getDrawable(R.drawable.purple_original_iconback, theme)
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

        tile.setOnLongClickListener { startAppDrag(tile, app) }

        return tile
    }

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (!drawerOpen && !dragging) {
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
                        changePage(pageIndex + if (dx < 0) 1 else -1)
                        return true
                    }

                    if (
                        dy < -dp(95) &&
                        abs(dy) > abs(dx) * 1.5f
                    ) {
                        if (runGesture(setting("swipe_up", "drawer"))) return true
                    }

                    if (
                        dy > dp(95) &&
                        abs(dy) > abs(dx) * 1.5f
                    ) {
                        if (runGesture(setting("swipe_down", "none"))) return true
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
```
