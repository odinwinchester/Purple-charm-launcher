package com.purplecharm.launcher

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.app.Activity
import android.app.Dialog
import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
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
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.InsetDrawable
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
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.BroadcastReceiver
import android.widget.Toast
import android.widget.ViewFlipper
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity(), SensorEventListener {
    private var editMode = false
    private var lastShakeAt = 0L
    private var undoPage: Pair<Int, List<String>>? = null
    private var undoWorkspaceSnapshot: WorkspaceLayoutSafety.Snapshot? = null
    private var pendingWorkspaceRefresh = false
    private var workspaceRefreshScheduled = false
    private var dragDropCommitted = false
    private var dragOrigin: Pair<Int, List<String>>? = null
    private var dragSession: WorkspaceDragSession? = null
    private var dragSource: View? = null
    private val workspaceStore by lazy { WorkspaceItemsStore(prefs) }
    private val workspaceMime = "application/vnd.purplecharm.workspace-item"
    private var folderDialog: FolderSurface? = null
    private lateinit var homeHost: FrameLayout
    private var cachedDrawerHost: FrameLayout? = null
    private var openFolderId: String? = null
    private var pendingDrawerDrag = false
    private val graphicCache by lazy { GraphicResourceCache(resources) }
    private data class CachedIcon(val state: Drawable.ConstantState, val bytes: Int)
    private val iconCache = object : android.util.LruCache<String, CachedIcon>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: CachedIcon) = value.bytes
    }
    private fun iconBytes(icon: Drawable): Int = when (icon) {
        is android.graphics.drawable.BitmapDrawable -> icon.bitmap.allocationByteCount
        is LayerDrawable -> (0 until icon.numberOfLayers).sumOf { iconBytes(icon.getDrawable(it)).toLong() }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        else -> (icon.intrinsicWidth.coerceAtLeast(1).toLong() * icon.intrinsicHeight.coerceAtLeast(1) * 4)
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }.coerceAtLeast(1)
    private val labelCache = android.util.LruCache<String, String>(128)
    private var optionsDialog: Dialog? = null
    private var lastCelestialKey: String? = null
    private fun appLabel(app: ResolveInfo): String = labelCache.get(appKey(app)) ?:
        app.loadLabel(packageManager).toString().also { labelCache.put(appKey(app), it) }
    private fun packagedImage(id: Int, wallpaper: Boolean = false): Drawable = if (wallpaper)
        graphicCache.load(id, resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        else graphicCache.load(id)


    private fun workspaceItems(): WorkspaceItems = WorkspaceItems(
        (0 until pageCount()).map { pageCells(it).toList() }, dockSlots().toList(), workspaceStore.folders())
    private fun knownItem(key: String): Boolean = resolveKey(key) != null ||
        (WorkspaceItems.isFolder(key) && runCatching { key in workspaceStore.folders() }.getOrDefault(false))

    private fun saveWorkspaceEdit(proposed: WorkspaceItems?): Boolean {
        val current = runCatching { workspaceItems() }.getOrNull()
        if (current == null || proposed == null) {
            Toast.makeText(this, "Não foi possível concluir sem alterar outros atalhos", Toast.LENGTH_SHORT).show()
            return false
        }
        val before = WorkspaceLayoutSafety(prefs).capture()
        val saved = if (dragging) dragSession?.commit(current, proposed, workspaceStore::save) == true
            else workspaceStore.save(proposed)
        if (!saved) {
            Toast.makeText(this, "Operação não salva; origem preservada", Toast.LENGTH_SHORT).show()
            return false
        }
        undoWorkspaceSnapshot = before
        if (dragging) dragDropCommitted = true
        refreshWorkspaceSafely()
        return true
    }
    private val sensorManager by lazy { getSystemService(SENSOR_SERVICE) as SensorManager }
    private var accelerometer: Sensor? = null

    private fun enterEditMode() {
        if (editMode || drawerOpen || dragging) return
        editMode = true
        val dialog = SmokeDialogBuilder(this).setTitle("✦ Editar tela inicial")
            .setItems(arrayOf("Wallpapers", "Ícones e organização", "Widgets", "Configurar dock", "Configurações", "Configurações do Android", "Concluir")) { _, choice ->
                when (choice) {
                    0 -> wallpaperMenu()
                    1 -> shortcutEditor()
                    2 -> widgetCenter()
                    3 -> dockConfiguration()
                    4 -> settingsMenu()
                    5 -> openSystemSettings()
                }
            }.create()
        dialog.setOnDismissListener { editMode = false }
        dialog.show()
    }
    private fun shortcutEditor() {
        val keys = pageCells(pageIndex).filter { it.isNotEmpty() }
        SmokeDialogBuilder(this).setTitle("Ícones • Página ${pageIndex + 1}")
            .setItems(arrayOf("Mover ou remover um atalho", "Arrastar entre páginas / criar pastas", "Gerenciar páginas")) { _, action ->
                when (action) {
                    0 -> SmokeDialogBuilder(this).setTitle("Selecionar atalho")
                        .setItems(keys.map { key -> resolveKey(key)?.let { appLabel(it) } ?: workspaceStore.folders()[key]?.name ?: key }.toTypedArray()) { _, index ->
                            val key = keys[index]
                            SmokeDialogBuilder(this).setTitle("Atalho selecionado")
                                .setItems(arrayOf("Mover para outra página", "Remover apenas este atalho")) { _, op ->
                                    if (op == 1) {
                                        if (WorkspaceItems.isFolder(key)) showFolder(key)
                                        else {
                                            val state = workspaceItems()
                                            val next = state.copy(pages = state.pages.mapIndexed { p, cells -> if (p == pageIndex) cells.map { if (it == key) "" else it } else cells })
                                            saveWorkspaceEdit(next)
                                        }
                                    } else moveWorkspaceItemPageMenu(key, resolveKey(key)?.let(::appLabel) ?: workspaceStore.folders()[key]?.name ?: key)
                                }.show()
                        }.show()
                    1 -> Toast.makeText(this, "Toque longo no atalho e escolha ‘Mover por arraste / criar pasta’. Solte sobre outro ícone para criar uma pasta; Voltar cancela.", Toast.LENGTH_LONG).show()
                    2 -> managePages()
                }
            }.show()
    }

    private fun leaveEditMode() {
        editMode = false
        sensorManager.unregisterListener(this)
        showHome()
    }

    private fun alphabetizeCurrentPage() {
        if (!editMode || drawerOpen) return
        val index = pageIndex
        val before = savedPage(index)
        val after = before.sortedWith(compareBy<String> {
            normalize(resolveKey(it)?.loadLabel(packageManager)?.toString() ?: it)
        }.thenBy { it })
        if (after == before) {
            Toast.makeText(this, "Esta página já está em ordem alfabética", Toast.LENGTH_SHORT).show()
            return
        }
        undoPage = index to before.toList()
        savePage(index, after)
        showHome()
        Toast.makeText(this, "Página organizada de A a Z", Toast.LENGTH_SHORT).show()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!editMode || drawerOpen || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        // Recurso desativado por padrão; exige aprovação explícita para reativar.
        if (!prefs.getBoolean("shake_sort_enabled", false)) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = kotlin.math.sqrt(x*x + y*y + z*z)
        val now = android.os.SystemClock.uptimeMillis()
        if (magnitude > 22f && now - lastShakeAt > 1800L) {
            lastShakeAt = now
            alphabetizeCurrentPage()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
        weatherHandler.removeCallbacks(weatherTick)
        weatherLocation.stop()
        if (dragging) cancelWorkspaceDrag()
        widgetEditor?.endEditing()
    }
    override fun onResume() {
        super.onResume()
        weatherHandler.removeCallbacks(weatherTick)
        weatherLocation.start()
        weatherHandler.post(weatherTick)
    }


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
    private var lastTapAt = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var swipeConsumed = false
    private var pendingVerticalSwipe = false
    private var workspaceGestureCandidate = false

    private var dragging = false
    private var switchingPage = false
    // Cinco posições visuais no dock (quatro atalhos + gaveta central).
    // A grade da área editável e da gaveta começa com as mesmas cinco colunas.
    private val defaultHorizontalIcons = 4
    private fun horizontalIconColumns(): Int =
        setting("columns", defaultHorizontalIcons.toString())
            .toIntOrNull()?.coerceIn(4, 6) ?: defaultHorizontalIcons

    // Grade inicial padrão 4 x 5; mantém preferências e atalhos já salvos.
    private fun homeCapacity(): Int = horizontalIconColumns() * 5
    private fun homeTileHeight(): Int {
        val fontScale = resources.configuration.fontScale.coerceAtLeast(1f)
        // Frame, vertical padding and up to three scaled text lines with breathing room.
        return dp((71f + kotlin.math.ceil(38f * fontScale)).toInt())
    }
    private val maxPages = 8

    private fun pageCount(): Int = prefs.getInt("layout_page_count", 3).coerceIn(3, maxPages)

    private fun managePages() {
        val homePage = HomePageSelection.read(prefs, pageCount())
        val options = arrayOf("＋ Adicionar página") +
            (0 until pageCount()).map { index ->
                (if (index == homePage) "✓ " else "") + "Definir Página ${index + 1} como principal"
            } + "－ Excluir página atual (se vazia)"
        SmokeDialogBuilder(this)
            .setTitle("✦ Páginas Purple Charm • ${pageCount()} de $maxPages")
            .setItems(options) { _, which ->
                if (which == 0) {
                    if (pageCount() >= maxPages) {
                        Toast.makeText(this, "Limite de $maxPages páginas", Toast.LENGTH_SHORT).show()
                    } else {
                        val newPage = pageCount()
                        prefs.edit().putInt("layout_page_count", newPage + 1)
                            .putString("layout_page_$newPage", "")
                            .remove("layout_page_cells_$newPage").apply()
                        pageIndex = newPage
                        showHome()
                    }
                } else if (which <= pageCount()) {
                    val selectedPage = which - 1
                    if (HomePageSelection.write(prefs, selectedPage, pageCount())) {
                        pageIndex = selectedPage
                        showHome()
                    } else {
                        Toast.makeText(this, "Não foi possível salvar a página principal", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    if (pageCount() <= 3) {
                        Toast.makeText(this, "Mantenha pelo menos 3 páginas", Toast.LENGTH_SHORT).show()
                    } else if (savedPage(pageIndex).isNotEmpty()) {
                        Toast.makeText(this, "Remova os atalhos desta página antes de excluir", Toast.LENGTH_LONG).show()
                    } else {
                        val count = pageCount()
                        val removedPage = pageIndex
                        val weatherPage = prefs.getInt("weather_widget_page", 0).coerceIn(0, count - 1)
                        val externalRaw = prefs.getString("hosted_widgets_v1", null)
                        val externalWidgets = try {
                            HostedWidgetStore.decode(externalRaw)
                        } catch (_: Exception) {
                            Toast.makeText(this, "Widgets Android ilegíveis; página preservada", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        if ((prefs.getBoolean("gothic_moon_visible", true) && weatherPage == removedPage) ||
                            externalWidgets.any { it.page == removedPage }) {
                            Toast.makeText(this, "Mova ou oculte os widgets desta página antes de excluí-la", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        val edit = prefs.edit()
                        for (i in pageIndex until count - 1) {
                            edit.putString("layout_page_$i", savedPage(i + 1).joinToString("|"))
                            val moved = prefs.getString("layout_page_cells_${i + 1}", null)
                            if (moved == null) edit.remove("layout_page_cells_$i")
                            else edit.putString("layout_page_cells_$i", moved)
                        }
                        val newHomePage = HomePageSelection.afterRemoval(
                            HomePageSelection.read(prefs, count), removedPage, count
                        )
                        edit.remove("layout_page_cells_${count - 1}")
                        edit.remove("layout_page_${count - 1}")
                            .putInt("layout_page_count", count - 1)
                            .putInt(HomePageSelection.KEY, newHomePage)
                            .putInt("weather_widget_page", HomePageSelection.afterRemoval(weatherPage, removedPage, count))
                        if (externalRaw != null) {
                            edit.putString("hosted_widgets_v1", HostedWidgetStore.encode(externalWidgets.map {
                                it.copy(page = if (it.page > removedPage) it.page - 1 else it.page)
                            }))
                        }
                        if (!edit.commit()) {
                            Toast.makeText(this, "Não foi possível salvar a remoção; dados preservados", Toast.LENGTH_LONG).show()
                            return@setItems
                        }
                        pageIndex = newHomePage
                        showHome()
                    }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun savedPage(index: Int): MutableList<String> {
        val raw = prefs.getString("layout_page_$index", null)
        if (raw != null) return raw.split("|").filter { it.isNotBlank() }.toMutableList()
        val initial = when (index) {
            0 -> apps.take(homeCapacity())
            1 -> favorites()
            2 -> apps.drop(homeCapacity()).take(homeCapacity())
            else -> emptyList()
        }.map { appKey(it) }
        return initial.toMutableList()
    }

    private fun pageCells(index: Int): MutableList<String> {
        val keys = savedPage(index).distinct()
        val stored = prefs.getString("layout_page_cells_$index", null)?.split("|")
        // A narrower grid must not truncate persisted shortcuts or folder references.
        val cells = MutableList(maxOf(homeCapacity(), stored?.size ?: 0, keys.size)) { "" }
        stored?.forEachIndexed { slot, key ->
                if (key.isNotEmpty() && key in keys && key !in cells) cells[slot] = key
            }
        keys.forEach { key ->
            if (key !in cells) {
                val free = cells.indexOfFirst { it.isEmpty() }
                if (free >= 0) cells[free] = key
            }
        }
        return cells
    }

    private fun savePage(index: Int, keys: List<String>) {
        val unique = keys.distinct()
        val cells = pageCells(index).map { if (it in unique) it else "" }.toMutableList()
        unique.forEach { key ->
            if (key !in cells) {
                val free = cells.indexOfFirst { it.isEmpty() }
                if (free >= 0) cells[free] = key
            }
        }
        prefs.edit().putString("layout_page_$index", unique.joinToString("|"))
            .putString("layout_page_cells_$index", cells.joinToString("|")).apply()
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

    private fun startAppDrag(view: View, app: ResolveInfo): Boolean = startItemDrag(view, appKey(app))

    private fun startItemDrag(view: View, key: String, shadow: View.DragShadowBuilder = View.DragShadowBuilder(view)): Boolean {
        if (dragging || !knownItem(key) || !view.isAttachedToWindow) return false
        val state = runCatching { workspaceItems() }.getOrNull()?.takeIf { it.valid() } ?: return false
        widgetEditor?.endEditing()
        val session = WorkspaceDragSession(key, state)
        dragSession = session
        dragSource = view
        dragDropCommitted = false
        dragFinishScheduled = false
        dragOrigin = pageIndex to savedPage(pageIndex).toList()
        // Drag-start callbacks can be dispatched synchronously by Android.
        dragging = true
        view.parent?.requestDisallowInterceptTouchEvent(true)
        val data = ClipData("Purple Charm", arrayOf(workspaceMime), ClipData.Item(key))
        val started = try { view.startDragAndDrop(data, shadow, session, 0) } catch (_: Exception) { false }
        if (!started) {
            session.cancel(); dragSession = null; dragging = false; dragOrigin = null
            view.parent?.requestDisallowInterceptTouchEvent(false)
            dragSource = null
        } else view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        return started
    }

    private fun ownsDrag(event: DragEvent): Boolean = dragging && dragSession != null &&
        event.localState === dragSession && event.clipDescription?.hasMimeType(workspaceMime) == true

    private fun draggedKey(event: DragEvent): String? {
        if (!ownsDrag(event)) return null
        val clip = event.clipData ?: return null
        if (clip.itemCount != 1) return null
        val key = clip.getItemAt(0).text?.toString() ?: return null
        return key.takeIf { it == dragSession?.key && knownItem(it) }
    }

    private fun acceptDrop(view: View, action: (String) -> Boolean) =
        acceptDropAt(view) { key, _ -> action(key) }

    private fun acceptDropAt(view: View, action: (String, DragEvent) -> Boolean) {
        view.setOnDragListener { target, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> ownsDrag(event).also { if (it) target.alpha = 0.8f }
                DragEvent.ACTION_DRAG_ENTERED -> { if (ownsDrag(event)) target.alpha = 0.55f; ownsDrag(event) }
                DragEvent.ACTION_DRAG_EXITED -> { target.alpha = 0.8f; ownsDrag(event) }
                DragEvent.ACTION_DROP -> draggedKey(event)?.let { key ->
                    if (dragDropCommitted) false else action(key, event)
                } ?: false
                DragEvent.ACTION_DRAG_ENDED -> {
                    target.alpha = 1f
                    if (event.localState === dragSession) finishWorkspaceDrag()
                    true
                }
                else -> ownsDrag(event)
            }
        }
    }

    private var dragFinishScheduled = false

    private fun finishWorkspaceDrag() {
        // ACTION_DRAG_ENDED chega a vários alvos. Finalize uma única vez,
        // depois que todos os listeners terminarem de processar o evento.
        if (!dragging || dragFinishScheduled) return
        dragFinishScheduled = true
        pages.post {
            dragFinishScheduled = false
            if (!dragging) return@post
            completeWorkspaceDrag()
        }
    }

    private fun cancelWorkspaceDrag() {
        window.decorView.findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")?.stopHover()
        dragSession?.cancel()
        runCatching { dragSource?.cancelDragAndDrop() }
        finishWorkspaceDrag()
    }

    private fun completeWorkspaceDrag() {
        if (folderDialog?.collapsed == true) {
            if (dragDropCommitted) folderDialog?.dismiss() else folderDialog?.restore()
        }
        dragSession?.cancel()
        dragSession = null
        dragging = false
        dragOrigin = null
        dragSource?.parent?.requestDisallowInterceptTouchEvent(false)
        dragSource = null
        if (pendingWorkspaceRefresh && !workspaceRefreshScheduled) {
            workspaceRefreshScheduled = true
            // Atualiza apenas após o ciclo atual de DragEvent terminar.
            pages.post {
                workspaceRefreshScheduled = false
                if (!dragging && pendingWorkspaceRefresh) {
                    pendingWorkspaceRefresh = false
                    val reopen = openFolderId
                    folderDialog?.dismiss()
                    showHome()
                    if (reopen != null && reopen in workspaceStore.folders()) showFolder(reopen)
                }
            }
        }
    }

    private fun refreshWorkspaceSafely() {
        if (dragging) {
            pendingWorkspaceRefresh = true
        } else {
            val reopen = openFolderId
            folderDialog?.dismiss()
            showHome()
            if (reopen != null && reopen in workspaceStore.folders()) showFolder(reopen)
        }
    }

    private fun dropOnVisiblePage(surface: View, event: DragEvent, localX: Float = event.x, localY: Float = event.y): Boolean {
        val key = draggedKey(event) ?: return false
        if (dragDropCommitted) return false
        val visible = pages.currentView ?: return false
        val grid = visible.findViewWithTag<GridLayout>("workspace_grid_$pageIndex") ?: return false
        val scroll = visible.findViewWithTag<ScrollView>("home_icons_scroll") ?: return false
        val origin = IntArray(2); val gridOrigin = IntArray(2); val viewport = IntArray(2)
        surface.getLocationOnScreen(origin); grid.getLocationOnScreen(gridOrigin); scroll.getLocationOnScreen(viewport)
        val screenX = origin[0] + localX; val screenY = origin[1] + localY
        if (screenY < viewport[1] || screenY >= viewport[1] + scroll.height || grid.childCount == 0) return false
        // Children retain their actual measured positions, padding and scroll offset.
        val x = screenX - gridOrigin[0]; val y = screenY - gridOrigin[1]
        val tile = (0 until grid.childCount).map { grid.getChildAt(it) }.minByOrNull {
            val dx = x - (it.left + it.width / 2f); val dy = y - (it.top + it.height / 2f)
            dx * dx + dy * dy
        } ?: return false
        val position = grid.indexOfChild(tile)
        val center = x - tile.left in tile.width * 0.2f..tile.width * 0.8f &&
            y - tile.top in tile.height * 0.2f..tile.height * 0.8f
        return placeOnPage(key, pageIndex, position, group = center && pageCells(pageIndex)[position].isNotEmpty())
    }

    private fun placeOnPage(key: String, index: Int, position: Int? = null, group: Boolean = false): Boolean {
        if (dragging && dragDropCommitted) return false
        val state = runCatching { workspaceItems() }.getOrNull() ?: return false
        if (index !in state.pages.indices) return false
        val cells = state.pages[index]
        val target = position?.takeIf { it in cells.indices }
            ?: cells.indexOfFirst { it.isEmpty() }.takeIf { it >= 0 }
            ?: cells.indexOf(key).takeIf { it >= 0 }
        if (target == null) {
            Toast.makeText(this, "Página cheia; escolha outra célula ou crie uma pasta sobre um aplicativo", Toast.LENGTH_SHORT).show()
            return false
        }
        val proposed = WorkspaceEdits.page(state, key, index, target, group)
        val previousPage = pageIndex
        pageIndex = index
        val saved = saveWorkspaceEdit(proposed)
        if (!saved) pageIndex = previousPage
        return saved
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

    private fun placeInDock(key: String, position: Int): Boolean {
        if (dragging && dragDropCommitted) return false
        val state = runCatching { workspaceItems() }.getOrNull() ?: return false
        return saveWorkspaceEdit(WorkspaceEdits.dock(state, key, position.coerceIn(0, 3)))
    }

    private fun removeShortcutFromPage(key: String, page: Int, cell: Int): Boolean {
        if (WorkspaceItems.isFolder(key)) {
            Toast.makeText(this, "Abra a pasta para editar seus aplicativos com segurança", Toast.LENGTH_SHORT).show()
            return false
        }
        val state = runCatching { workspaceItems() }.getOrNull() ?: return false
        // The confirmation is bound to the exact shortcut location that opened it.
        // If that cell changed while the dialog was open, leave the workspace untouched.
        if (state.pages.getOrNull(page)?.getOrNull(cell) != key) return false
        return saveWorkspaceEdit(WorkspaceEdits.removeFromPage(state, key, page, cell))
    }

    private fun folderTile(id: String, compact: Boolean): View {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull()
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(2), dp(6), dp(2), dp(8))
            contentDescription = "Pasta ${folder?.name ?: "indisponível"}"
        }
        val preview = GridLayout(this).apply {
            columnCount = 2; rowCount = 2
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = glass(10, false)
        }
        val previewSize = if (compact) 45 else 53
        folder?.apps?.take(4)?.forEach { key ->
            preview.addView(ImageView(this).apply {
                resolveKey(key)?.let { setImageDrawable(customIcon(it, includeFallbackFrame = true)) }
                scaleType = ImageView.ScaleType.FIT_CENTER
            }, GridLayout.LayoutParams().apply {
                width = dp((previewSize - 8) / 2); height = dp((previewSize - 8) / 2)
            })
        }
        tile.addView(preview, LinearLayout.LayoutParams(dp(previewSize), dp(previewSize)))
        if (!compact) tile.addView(label(folder?.name ?: "Pasta indisponível", 10f).apply {
            maxLines = 2; minLines = 2; setPadding(0, dp(6), 0, 0)
        })
        tile.setOnClickListener { showFolder(id) }
        tile.setOnLongClickListener {
            if (!startItemDrag(tile, id)) Toast.makeText(this, "Não foi possível iniciar o arraste. Tente novamente.", Toast.LENGTH_SHORT).show()
            true
        }
        return tile
    }

    private fun showFolder(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull()
        if (folder == null) {
            Toast.makeText(this, "Dados da pasta indisponíveis; nada foi alterado", Toast.LENGTH_SHORT).show()
            return
        }
        folderDialog?.dismiss()
        openFolderId = id
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.argb(242, 18, 8, 29)); cornerRadius = dp(16).toFloat()
                setStroke(dp(1).coerceAtLeast(1), Color.argb(130, 171, 107, 213))
            }
            tag = "folder_panel"
        }
        panel.addView(label(folder.name, 19f, true).apply { setOnClickListener { renameFolder(id) } })
        val grid = GridLayout(this).apply { columnCount = 3; tag = "folder_grid" }
        folder.apps.forEachIndexed { index, key ->
            val app = resolveKey(key)
            val tile = if (app != null) appTile(app, false, false, dense = true) else label(key.substringAfterLast('/'), 10f)
            tile.tag = "folder_member_$index"
            tile.setOnLongClickListener { startFolderHomeDrag(tile, key) }
            acceptDrop(tile) { incoming ->
                val state = runCatching { workspaceItems() }.getOrNull()
                state != null && saveWorkspaceEdit(WorkspaceEdits.intoFolder(state, incoming, id, index))
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0; height = dp(88)
                columnSpec = GridLayout.spec(index % 3, 1f); rowSpec = GridLayout.spec(index / 3)
            })
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(((folder.apps.size + 2) / 3).coerceIn(1, 2) * 88)))
        val tools = LinearLayout(this)
        listOf("Adicionar" to { addFolderApps(id) }, "Renomear" to { renameFolder(id) },
            "Organizar" to { folderAppActions(id) }).forEach { (title, action) ->
            tools.addView(label(title, 12f).apply { background = optionHighlight(); setPadding(0, dp(8), 0, dp(8)); setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        panel.addView(tools)
        panel.addView(label("Fechar", 12f).apply { setPadding(0, dp(8), 0, 0); setOnClickListener { folderDialog?.dismiss() } })
        val surface = FolderSurface(this, homeHost, panel,
            onCancel = { if (dragging) cancelWorkspaceDrag() },
            onDismiss = { folderDialog = null; openFolderId = null })
        folderDialog = surface
        surface.show()
    }

    private fun startFolderHomeDrag(source: View, key: String): Boolean {
        if (!startItemDrag(source, key)) return false
        folderDialog?.collapse()
        return true
    }

    private fun renameFolder(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull() ?: return
        val input = EditText(this).apply { setText(folder.name); setSingleLine(true) }
        SmokeDialogBuilder(this).setTitle("Renomear pasta").setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val state = runCatching { workspaceItems() }.getOrNull()
                if (state != null) saveWorkspaceEdit(WorkspaceEdits.rename(state, id, input.text.toString()))
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun addFolderApps(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull() ?: return
        val available = apps.filter { appKey(it) !in folder.apps }
        val selected = BooleanArray(available.size)
        SmokeDialogBuilder(this).setTitle("Adicionar aplicativos")
            .setMultiChoiceItems(available.map { appLabel(it) }.toTypedArray(), selected) { _, index, checked ->
                selected[index] = checked
            }.setPositiveButton("Adicionar") { _, _ ->
                var next: WorkspaceItems? = runCatching { workspaceItems() }.getOrNull()
                available.forEachIndexed { index, app -> if (selected[index])
                    next = next?.let { WorkspaceEdits.intoFolder(it, appKey(app), id) }
                }
                saveWorkspaceEdit(next)
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun folderAppActions(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull() ?: return
        SmokeDialogBuilder(this).setTitle("Organizar aplicativos da pasta")
            .setItems(folder.apps.map { resolveKey(it)?.loadLabel(packageManager)?.toString() ?: it.substringAfterLast('/') }.toTypedArray()) { _, index ->
                val key = folder.apps[index]
                SmokeDialogBuilder(this).setTitle(resolveKey(key)?.loadLabel(packageManager)?.toString() ?: key)
                    .setItems(arrayOf("Mover para a tela inicial", "Remover atalho da pasta", "Mover antes", "Mover depois")) { _, action ->
                        val state = runCatching { workspaceItems() }.getOrNull() ?: return@setItems
                        val next = when (action) {
                            0 -> WorkspaceEdits.extract(state, id, key, pageIndex)
                            1 -> WorkspaceEdits.removeApp(state, id, key)
                            2 -> WorkspaceEdits.intoFolder(state, key, id, (index - 1).coerceAtLeast(0))
                            else -> WorkspaceEdits.intoFolder(state, key, id, index + 1)
                        }
                        saveWorkspaceEdit(next)
                    }.setNegativeButton("Cancelar", null).show()
            }.setNegativeButton("Cancelar", null).show()
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
        SmokeDialogBuilder(this)
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
    private fun smokePanel(): GradientDrawable = SmokeUi.panel(this)
    private fun optionHighlight(): android.graphics.drawable.StateListDrawable {
        val selected = GradientDrawable().apply {
            setColor(Color.argb(75, 139, 67, 184)); cornerRadius = dp(10).toFloat()
        }
        return android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), selected)
            addState(intArrayOf(android.R.attr.state_focused), selected)
            addState(intArrayOf(), android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        }
    }
    private fun settingsMenu() {
        optionsDialog?.dismiss()
        val dialog = Dialog(this)
        optionsDialog = dialog
        val panel = LinearLayout(this).apply {
            tag = "smoked_options"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(12))
            background = smokePanel()
        }
        panel.addView(label("✦ Purple Charm • Configurações", 18f, true))
        listOf("✦ Configurações do tema", "▦ Configurações do launcher")
            .forEachIndexed { choice, text ->
                panel.addView(label(text, 15f).apply {
                    tag = "settings_command_$choice"
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    setPadding(dp(12), dp(14), dp(12), dp(14))
                    background = optionHighlight()
                    setOnClickListener {
                        dialog.dismiss()
                        when (choice) {
                            0 -> settingsCategory("Tema")
                            1 -> settingsCategory("Launcher")
                        }
                    }
                })
            }
        dialog.setOnDismissListener { if (optionsDialog === dialog) optionsDialog = null }
        dialog.setContentView(panel); dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setLayout((resources.displayMetrics.widthPixels - dp(32)).coerceAtMost(dp(390)), -2)
        }
    }

    private fun settingsCategory(category: String) {
        val dialog = Dialog(this)
        val frame = FrameLayout(this).apply { background = smokePanel() }
        val scroll = ScrollView(this)
        val menu = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(28), dp(16), dp(32))
        }
        menu.addView(label("✦ " + category, 22f, true).apply {
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
        if (category == "Tema") {
        option("✦ Máscara para ícones sem tema: " +
            if (setting("fallback_skin", "original") == "ghost") "Cristal Ghost Fire" else "Original") {
            chooseSetting("Ícones sem arte original", "fallback_skin",
                arrayOf("Ícone original do aplicativo", "Cristal violeta • inspirado em Ghost Fire"),
                arrayOf("original", "ghost"), "original")
        }
        option("✦ Papéis de parede Purple Charm") { wallpaperMenu() }
        } else {
        option("✦ Deslizar para cima: " + gestureName(setting("swipe_up", "options"))) {
            gestureSetting("Deslizar para cima", "swipe_up", "options")
        }
        option("✦ Deslizar para baixo: " + gestureName(setting("swipe_down", "drawer"))) {
            gestureSetting("Deslizar para baixo", "swipe_down", "drawer")
        }
        option("✦ Toque duplo: " + gestureName(setting("double_tap", "none"))) {
            gestureSetting("Toque duplo", "double_tap", "none")
        }
        option("✦ Ícones por linha (início e gaveta): " + horizontalIconColumns()) {
            chooseSetting("Ícones por linha (início e gaveta)", "columns",
                arrayOf("4 colunas", "5 colunas", "6 colunas"),
                arrayOf("4", "5", "6"), "5")
        }
        option("✦ Espaço superior para widgets: " +
            setting("widget_space_dp", "180") + " dp") {
            chooseSetting("Espaço superior para widgets", "widget_space_dp",
                arrayOf("Sem espaço", "120 dp • compacto", "180 dp • padrão",
                    "240 dp • amplo", "300 dp • extra"),
                arrayOf("0", "120", "180", "240", "300"), "180")
        }
        option("✦ Gerenciar páginas (${pageCount()})") { managePages() }
        option("✦ Efeito de transição: " + transitionName(setting("transition", "cube"))) {
            chooseSetting("Transição entre páginas", "transition",
                arrayOf("Cubo 3D", "Giro 3D", "Zoom Crystal", "Deslizar", "Sem animação"),
                arrayOf("cube", "flip", "zoom", "slide", "none"), "cube")
        }
        option("✦ Restaurar gestos e grade") {
            SmokeDialogBuilder(this).setTitle("Restaurar configurações?")
                .setMessage("Isso não altera seus wallpapers nem a organização.")
                .setPositiveButton("Restaurar") { _, _ ->
                    prefs.edit().remove("pc_swipe_up").remove("pc_swipe_down").remove("pc_double_tap")
                        .remove("pc_columns").apply()
                    showHome()
                }.setNegativeButton("Cancelar", null).show()
        }
        option("✦ Restaurar organização dos ícones") {
            SmokeDialogBuilder(this).setTitle("Restaurar organização?")
                .setMessage("As posições das páginas, pastas e dock serão restauradas. Aplicativos não serão desinstalados.")
                .setPositiveButton("Restaurar") { _, _ ->
                    if (workspaceStore.reset()) {
                        folderDialog?.dismiss()
                        showHome()
                    } else Toast.makeText(this, "Não foi possível restaurar; organização anterior mantida", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Cancelar", null).show()
        }
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
        "options" -> "Dock de opções"
        "drawer" -> "Abrir aplicativos"
        "search" -> "Pesquisa Crystal Glass"
        "settings" -> "Configurações"
        "wallpaper" -> "Papel de parede"
        "home" -> "Página inicial"
        else -> "Nenhuma ação"
    }

    private fun gestureSetting(title: String, key: String, default: String) {
        val values = arrayOf("options", "drawer", "search", "settings", "wallpaper", "home", "none")
        chooseSetting(title, key, values.map { gestureName(it) }.toTypedArray(), values, default)
    }

    private fun runGesture(action: String): Boolean {
        when (action) {
            "options" -> showClassicCustomizationMenu()
            "drawer" -> showDrawer()
            "search" -> showCrystalSearch()
            "settings" -> settingsMenu()
            "wallpaper" -> wallpaperMenu()
            "home" -> changePage(HomePageSelection.read(prefs, pageCount()))
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
        pageIndex = HomePageSelection.read(prefs, pageCount())
        loadApps()
        showHome()
    }

    private fun dp(n: Int): Int =
        (n * resources.displayMetrics.density).toInt()

    private var appsLoaded = false

    private val packageChanges = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.data?.schemeSpecificPart == packageName) return
            appsLoaded = false
            labelCache.evictAll()
            iconCache.evictAll()
            cachedDrawerHost = null
            loadApps()
            if (::pages.isInitialized) {
                if (drawerOpen) showDrawer() else refreshWorkspaceSafely()
            }
        }
    }

    private fun loadApps() {
        // O catálogo muda por eventos do PackageManager; reutilize-o entre Home/gaveta.
        if (appsLoaded) return
        labelCache.evictAll(); iconCache.evictAll()
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
        appsLoaded = true
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
        background = optionHighlight()
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun buildRoot(backgroundResource: Int? = null): LinearLayout {
        val frame = object : FrameLayout(this) {
            override fun dispatchDragEvent(event: DragEvent): Boolean {
                if (ownsDrag(event)) {
                    if (event.action == DragEvent.ACTION_DRAG_LOCATION) {
                        val folder = folderDialog
                        if (folder != null && !folder.collapsed) {
                            val at = IntArray(2); getLocationOnScreen(at)
                            if (!folder.containsScreenPoint(at[0] + event.x.toInt(), at[1] + event.y.toInt())) folder.collapse()
                        }
                    }
                    if (folderDialog?.collapsed == true && event.action in listOf(
                            DragEvent.ACTION_DRAG_LOCATION, DragEvent.ACTION_DRAG_ENTERED,
                            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DROP)) {
                        val workspace = findViewWithTag<WorkspaceDragLayout>("workspace_drag_surface")
                        if (workspace != null) {
                            val from = IntArray(2); val to = IntArray(2)
                            getLocationOnScreen(from); workspace.getLocationOnScreen(to)
                            return workspace.route(event, event.x + from[0] - to[0], event.y + from[1] - to[1])
                        }
                    }
                    if (event.action == DragEvent.ACTION_DRAG_ENDED) finishWorkspaceDrag()
                }
                return super.dispatchDragEvent(event)
            }
        }
        homeHost = frame
        frame.setBackgroundColor(Color.BLACK)

        backgroundImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
        }

        frame.addView(
            backgroundImage,
            FrameLayout.LayoutParams(-1, -1)
        )

        if (backgroundResource != null) backgroundImage.setImageDrawable(packagedImage(backgroundResource, wallpaper = true))
        else applyWallpaper()

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
            "eye_crystal" -> backgroundImage.setImageDrawable(packagedImage(R.drawable.purple_eye_crystal, wallpaper = true))
            "eye_royal" -> backgroundImage.setImageDrawable(packagedImage(R.drawable.purple_eye_crystal, wallpaper = true))
            "ghost_fire" -> backgroundImage.setImageDrawable(packagedImage(R.drawable.wallpaper_ghost_fire, wallpaper = true))
            "cosmic_azathoth" -> backgroundImage.setImageDrawable(packagedImage(R.drawable.cosmic_horror_azathoth, wallpaper = true))
            "geometrico_neon" -> backgroundImage.setImageDrawable(packagedImage(R.drawable.wallpaper_neon, wallpaper = true))
            "gallery" -> {
                val saved = prefs.getString("wallpaper_uri", null)
                if (!saved.isNullOrEmpty()) {
                    try {
                        backgroundImage.setImageURI(Uri.parse(saved))
                        if (backgroundImage.drawable != null) return
                    } catch (_: Exception) { }
                }
                backgroundImage.setImageDrawable(packagedImage(R.drawable.purple_original_wallpaper, wallpaper = true))
            }
            else -> backgroundImage.setImageDrawable(packagedImage(R.drawable.purple_original_wallpaper, wallpaper = true))
        }
    }

    private fun chooseBuiltInWallpaper(value: String) {
        prefs.edit().putString("pc_wallpaper_choice", value).apply()
        applyWallpaper()
        Toast.makeText(this, "Wallpaper aplicado à tela inicial", Toast.LENGTH_SHORT).show()
    }

    private val wallpaperOptions = listOf(
        Triple("Floral original", "floral", R.drawable.purple_original_wallpaper),
        Triple("Olho de Crystal", "eye_crystal", R.drawable.purple_eye_crystal),
        Triple("Ghost Fire", "ghost_fire", R.drawable.wallpaper_ghost_fire),
        Triple("Azathoth — O Caos Primordial", "cosmic_azathoth", R.drawable.cosmic_horror_azathoth),
        Triple("Geométrico Purple Charm", "geometrico_neon", R.drawable.wallpaper_neon)
    )

    private fun wallpaperGlass(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.argb(250, 17, 3, 29), Color.argb(248, 3, 1, 8))
        ).apply {
            cornerRadius = dp(26).toFloat()
            setStroke(dp(2), Color.rgb(205, 73, 255))
        }

    private fun wallpaperMenu() {
        val dialog = Dialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = smokePanel()
            setPadding(dp(14), dp(20), dp(14), dp(12))
        }
        val header = glassButton("‹     ✦ Papéis de parede", 21f).apply {
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(header)
        root.addView(label("Toque para visualizar • segure para escolher onde aplicar", 12f))
        val scroll = ScrollView(this)
        val grid = GridLayout(this).apply { columnCount = 2 }
        wallpaperOptions.forEach { (name, key, res) ->
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(5), dp(9), dp(5), dp(9))
            }
            val picture = ImageView(this).apply {
                tag = "wallpaper_option_$key"
                setImageDrawable(packagedImage(res, wallpaper = true))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = wallpaperGlass()
                clipToOutline = true
                setOnClickListener { wallpaperPreview(key, res, null) }
                setOnLongClickListener { wallpaperDestination(key, res, null); true }
            }
            tile.addView(picture, LinearLayout.LayoutParams(-1, dp(238)))
            tile.addView(label(name, 13f))
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        prefs.getString("wallpaper_uri", null)?.let { savedUri ->
            val uri = Uri.parse(savedUri)
            val tile = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(5), dp(9), dp(5), dp(9)) }
            val picture = ImageView(this).apply {
                tag = "wallpaper_option_gallery"
                setImageURI(uri)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = wallpaperGlass()
                clipToOutline = true
                setOnClickListener { wallpaperPreview("gallery", null, uri) }
                setOnLongClickListener { wallpaperDestination("gallery", null, uri); true }
            }
            tile.addView(picture, LinearLayout.LayoutParams(-1, dp(238)))
            tile.addView(label("Imagem da galeria", 13f))
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            })
        }
        val gallery = glassButton("＋ Escolher imagem do celular", 16f).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                }
                try {
                    dialog.dismiss()
                    startActivityForResult(intent, wallpaperRequest)
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "Não foi possível abrir a galeria", Toast.LENGTH_LONG).show()
                }
            }
        }
        grid.addView(gallery, GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(0, 2)
            width = -1
        })
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun wallpaperPreview(key: String, res: Int?, uri: Uri?) {
        val dialog = Dialog(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (res != null) setImageDrawable(packagedImage(res, wallpaper = true)) else setImageURI(uri)
        }
        layout.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(glassButton("✦ Aplicar papel de parede", 17f).apply {
            tag = "wallpaper_preview_apply"
            setOnClickListener { dialog.dismiss(); wallpaperDestination(key, res, uri) }
        })
        layout.addView(glassButton("Voltar", 14f).apply {
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(layout)
        dialog.show()
        dialog.window?.setLayout(-1, -1)
    }

    private fun wallpaperDestination(key: String, res: Int?, uri: Uri?) {
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = wallpaperGlass()
        }
        panel.addView(label("✧ Aplicar plano de fundo a", 19f))
        listOf("♙ Tela de bloqueio", "⌂ Tela inicial", "⌂♙ Ambos").forEachIndexed { index, title ->
            panel.addView(glassButton(title, 17f).apply {
                tag = "wallpaper_destination_$index"
                setOnClickListener {
                    dialog.dismiss()
                    applyWallpaperDestination(key, res, uri, index)
                }
            })
        }
        panel.addView(glassButton("Cancelar", 16f).apply {
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(-1, -2)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { dimAmount = 0.65f }
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
    }

    private fun applyWallpaperDestination(key: String, res: Int?, uri: Uri?, destination: Int) {
        // Tela inicial usa o fundo do launcher; tela de bloqueio usa a API do Android.
        if (destination == 0 || destination == 2) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                Toast.makeText(this, "Tela de bloqueio não suportada nesta versão", Toast.LENGTH_LONG).show()
                return
            }
            try {
                val manager = WallpaperManager.getInstance(this)
                if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
                    Toast.makeText(this, "O sistema não permite alterar o fundo", Toast.LENGTH_LONG).show()
                    return
                }
                val stream = if (res != null) resources.openRawResource(res)
                    else contentResolver.openInputStream(uri ?: return) ?: return
                stream.use { manager.setStream(it, null, true, WallpaperManager.FLAG_LOCK) }
            } catch (_: Exception) {
                Toast.makeText(this,
                    "O POCO não permitiu alterar a tela de bloqueio. Use as configurações do sistema.",
                    Toast.LENGTH_LONG).show()
                return
            }
        }
        if (destination == 1 || destination == 2) {
            val editor = prefs.edit().putString("pc_wallpaper_choice", key)
            if (uri != null) editor.putString("wallpaper_uri", uri.toString())
            editor.apply()
            applyWallpaper()
        }
        Toast.makeText(this, "Papel de parede aplicado", Toast.LENGTH_SHORT).show()
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

        if (androidWidgets.onResult(requestCode, resultCode, data)) return
        if (
            requestCode != wallpaperRequest ||
            resultCode != RESULT_OK
        ) return

        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            wallpaperPreview("gallery", null, uri)
        } catch (_: Exception) {
            Toast.makeText(this, "Não foi possível abrir a imagem", Toast.LENGTH_LONG).show()
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

    private fun customIcon(app: ResolveInfo, includeFallbackFrame: Boolean = false): Drawable {
        val key = "${appKey(app)}:${setting("fallback_skin", "original")}:$includeFallbackFrame"
        iconCache.get(key)?.let { return it.state.newDrawable(resources).mutate() }
        return uncachedIcon(app, includeFallbackFrame).also { icon ->
            val bytes = iconBytes(icon)
            if (bytes <= iconCache.maxSize()) icon.constantState?.let { iconCache.put(key, CachedIcon(it, bytes)) }
        }
    }
    // Original GO-style theme layers are applied only to unthemed application icons.
    // All three PNG resources are reused unchanged; themed icons keep their own artwork.
    private fun originalThemeFallback(icon: Drawable): Drawable {
        val size = 144
        fun paintResource(id: Int, canvas: Canvas) {
            val drawable = resources.getDrawable(id, theme).mutate()
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        paintResource(R.drawable.iconback, canvas)
        val foreground = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val foregroundCanvas = Canvas(foreground)
        icon.mutate().apply { setBounds(0, 0, size, size) }.draw(foregroundCanvas)
        val mask = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        paintResource(R.drawable.iconmask, Canvas(mask))
        val maskedCanvas = Canvas(foreground)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        maskedCanvas.drawBitmap(mask, 0f, 0f, maskPaint)
        maskPaint.xfermode = null
        canvas.drawBitmap(foreground, 0f, 0f, null)
        paintResource(R.drawable.iconupon, canvas)
        mask.recycle()
        foreground.recycle()
        return BitmapDrawable(resources, result)
    }

    private fun uncachedIcon(app: ResolveInfo, includeFallbackFrame: Boolean): Drawable {
        val pkg = app.activityInfo.packageName
            .lowercase(Locale.ROOT)

        val name = normalize(
            appLabel(app)
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
            "youtube" in name ||
            "yt music" in name ||
            "youtube music" in name -> "icon_youtube"

            "gmail" in pkg ||
            "gmail" in name -> "gmail"

            "google.android.apps.maps" in pkg ||
            name == "maps" ||
            name == "mapas" -> "maps"

            "google.android.apps.docs" in pkg ||
            name == "google drive" -> "googledrive"

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

        val original = app.loadIcon(packageManager)
        // Automatically skin every unthemed app using the authentic original layers.
        // The pre-existing PNG themed icons above remain completely unchanged.
        if (setting("fallback_skin", "original") != "ghost") {
            return originalThemeFallback(original)
        }
        if (!includeFallbackFrame) {
            return originalThemeFallback(original)
        }

        // Usa o fundo autêntico recuperado do Purple Charm original.
        // Os ícones personalizados existentes permanecem intocados.
        @Suppress("DEPRECATION")
        val originalBorder = resources.getDrawable(R.drawable.purple_original_iconback).mutate()
        return LayerDrawable(arrayOf(originalBorder, InsetDrawable(original, dp(9)))).apply {
            setLayerInset(1, dp(4), dp(4), dp(4), dp(4))
        }
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
            label("Ghost Fire Stable • 3.0", 12f).apply {
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
        val duration = 260L
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
        if (drawerOpen || dragging || !::pages.isInitialized) return
        val target = destination.coerceIn(0, pageCount() - 1)
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
        // Não bloquear novos gestos por um temporizador após a animação.
        switchingPage = false
    }

    private fun showHome() {
        widgetEditor?.endEditing()
        widgetEditor = null
        drawerOpen = false
        loadApps()

        val root = buildRoot()

        // Layout clássico: ícones e wallpaper têm prioridade visual.
        // O título permanece disponível nas configurações.

        pages = ViewFlipper(this)
        switchingPage = false

        for (index in 0 until pageCount()) {
            pages.addView(homePage(index))
        }

        // Área de trabalho com bordas sensíveis ao arraste entre páginas.
        val workspace = WorkspaceDragLayout(this).apply {
            tag = "workspace_drag_surface"
            clipChildren = true
            clipToPadding = true
            minimumHeight = 0
            owns = { ownsDrag(it) }
            canNavigate = { dragging && !dragDropCommitted && !drawerOpen }
            navigate = { direction ->
                val target = pageIndex + direction
                if (target in 0 until pageCount()) {
                    pages.inAnimation = null
                    pages.outAnimation = null
                    pageIndex = target
                    pages.displayedChild = target
                    updateDots()
                    true
                } else false
            }
            drop = { event -> dropOnVisiblePage(this, event) }
            dropAt = { event, x, y -> dropOnVisiblePage(this, event, x, y) }
            ended = { finishWorkspaceDrag() }
        }
        workspace.addView(pages, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            workspace,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        pageIndex = pageIndex.coerceIn(0, pageCount() - 1)
        pages.displayedChild = pageIndex

        dots = label("", 19f).apply {
            setTextColor(lilac)
            setPadding(0, dp(4), 0, dp(5))
        }

        dots.setOnClickListener { changePage((pageIndex + 1) % pageCount()) }
        root.addView(dots)
        updateDots()

        root.addView(dock().apply { tag = "home_dock" })
        // A edição permanece acessível pelo menu de personalização do gesto inferior.
        // A antiga faixa duplicada não é adicionada à tela inicial.
    }

    private val weatherHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val weatherClient by lazy { MetWeatherClient(prefs) }
    private val weatherLocation by lazy {
        WeatherLocationController(this, prefs) { coordinatesChanged ->
            if (coordinatesChanged) {
                weatherClient.clearForecast()
                refreshGothicWeather()
            }
            refreshCelestialView()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        weatherLocation.onPermissionResult(requestCode)
    }

    private val androidWidgets by lazy { AndroidWidgetController(this, prefs, { pageCount() }, { horizontalIconColumns() }, { refreshWorkspaceSafely() }, { page -> pageIndex = page; refreshWorkspaceSafely() }) }
    private var packageReceiverRegistered = false
    override fun onStart() {
        super.onStart()
        androidWidgets.start()
        if (!packageReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageChanges, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(packageChanges, filter)
            }
            packageReceiverRegistered = true
        }
    }
    override fun onStop() {
        androidWidgets.stop()
        super.onStop()
    }
    private fun widgetCenter() {
        SmokeDialogBuilder(this).setTitle("✦ Central de widgets")
            .setItems(arrayOf("Purple Charm • clima gótico", "Adicionar widget Android", "Gerenciar widgets adicionados")) { _, choice ->
                when (choice) {
                    0 -> { if (!prefs.getBoolean("gothic_moon_visible", true)) { prefs.edit().putBoolean("gothic_moon_visible", true).apply(); weatherLocation.start(); refreshGothicWeather(); refreshWorkspaceSafely() }; moonWidgetOptions() }
                    1 -> androidWidgets.choose()
                    2 -> androidWidgets.manage()
                }
            }.show()
    }
    private fun dockConfiguration() {
        SmokeDialogBuilder(this).setTitle("✦ Dock • quatro atalhos e gaveta central")
            .setItems((0..3).map { i -> "Posição ${i + 1}: " + dockSlots()[i].let { key -> resolveKey(key)?.let { appLabel(it) } ?: workspaceStore.folders()[key]?.name ?: "Vazia" } }.toTypedArray()) { _, i -> dockSlotOptions(i) }
            .setNeutralButton("Sobre importação") { _, _ -> SmokeDialogBuilder(this).setMessage("A organização privada de outro launcher não é exposta por API pública Android. Configure manualmente as quatro posições.").setPositiveButton("Fechar", null).show() }.show()
    }
    private fun dockChooseApp(slot: Int) {
        loadApps()
        SmokeDialogBuilder(this).setTitle("Aplicativo para posição ${slot + 1}")
            .setItems(apps.map { appLabel(it) }.toTypedArray()) { _, i ->
                val state = workspaceItems()
                saveWorkspaceEdit(WorkspaceEdits.configureDock(state, appKey(apps[i]), slot))
            }.show()
    }
    private fun dockSlotOptions(slot: Int) {
        SmokeDialogBuilder(this).setTitle("Dock • posição ${slot + 1}")
            .setItems(arrayOf("Adicionar / substituir aplicativo", "Remover do dock", "Mover à esquerda", "Mover à direita")) { _, action ->
                val state = workspaceItems(); val key = state.dock[slot]
                when (action) {
                    0 -> dockChooseApp(slot)
                    1 -> if (WorkspaceItems.isFolder(key)) {
                        val p = state.pages.indices.firstOrNull { state.pages[it].any(String::isEmpty) }
                        if (p != null) saveWorkspaceEdit(WorkspaceEdits.page(state, key, p, state.pages[p].indexOf("")))
                    } else saveWorkspaceEdit(state.copy(dock = state.dock.toMutableList().apply { set(slot, "") }))
                    else -> {
                        val target = slot + if (action == 2) -1 else 1
                        if (key.isNotEmpty() && target in 0..3) saveWorkspaceEdit(WorkspaceEdits.dock(state, key, target))
                    }
                }
            }.show()
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            graphicCache.clear(); iconCache.evictAll(); labelCache.evictAll()
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) cachedDrawerHost = null
            androidWidgets.releaseHiddenViews()
        }
    }
    override fun onDestroy() {
        if (packageReceiverRegistered) {
            unregisterReceiver(packageChanges)
            packageReceiverRegistered = false
        }
        weatherHandler.removeCallbacks(weatherTick)
        weatherLocation.close()
        androidWidgets.close()
        graphicCache.clear(); iconCache.evictAll(); labelCache.evictAll()
        optionsDialog?.dismiss()
        folderDialog?.dismiss()
        dragSession?.cancel()
        dragSession = null
        super.onDestroy()
    }
    private val weatherTick = object : Runnable {
        override fun run() {
            refreshGothicWeather()
            refreshCelestialView()
            weatherHandler.postDelayed(this, 60_000L)
        }
    }
    private var celestialView: View? = null
    private var liveWeatherLabel: TextView? = null
    private var widgetEditor: WeatherResizeLayout? = null
    private var widgetResizeActive = false
    private var widgetResizeGesture = false
    private var externalWidgetGesture = false

    // Replace only the widget, preserving pages, scroll state and drag/drop.
    private fun refreshCelestialView() {
        if (isFinishing || isDestroyed || dragging || widgetResizeActive || drawerOpen) return
        val old = celestialView ?: return
        if (!old.isAttachedToWindow) return
        val parent = old.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(old)
        val updated = gothicMoonWidget(reuseUnchanged = true)
        if (updated === old) return
        parent.removeViewAt(index)
        parent.addView(updated, index)
    }

    private fun refreshGothicWeather() {
        if (!prefs.getBoolean("gothic_moon_visible", true)) return
        weatherClient.refresh {
            runOnUiThread {
                if (!isFinishing && !isDestroyed) refreshCelestialView()
            }
        }
    }

    private fun gothicWeatherSettings() { weatherLocation.showOptions() }

    private fun gothicMoonWidget(reuseUnchanged: Boolean = false): View {
        // Ciclo sinódico médio de 29,530588853 dias; aproximação offline.
        // Referência: Lua Nova de 06/01/2000 às 18:14 UTC.
        val now = System.currentTimeMillis()
        val lunarDays = ((now - 947182440000L) / 86400000.0) % 29.530588853
        val age = if (lunarDays < 0) lunarDays + 29.530588853 else lunarDays
        val automaticPhase = when {
            age < 1.84566 || age >= 27.68493 -> 3 // Nova
            age < 12.91963 -> 0 // Crescente (inclui quartos e gibosa)
            age < 16.61096 -> 1 // Cheia
            else -> 2 // Minguante
        }
        val phase = if (prefs.getBoolean("gothic_moon_auto", true)) automaticPhase
            else prefs.getInt("gothic_moon_phase", 0).coerceIn(0, 3)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val mode = prefs.getInt("gothic_celestial_mode", 0) // 0 auto, 1 sol, 2 lua
        val forecast = weatherClient.current()
        val daylight = SolarDaylight.isDay(now, prefs.getString("gothic_weather_lat", null)?.toDoubleOrNull(),
            prefs.getString("gothic_weather_lon", null)?.toDoubleOrNull()) ?: forecast?.isDay
        val isSun = when (mode) {
            1 -> true
            2 -> false
            else -> daylight ?: false
        }
        val sunStyle = prefs.getInt("gothic_sun_style", 0).coerceIn(0, 3)
        val weatherActive = prefs.contains("gothic_weather_lat")
        val weatherLoaded = weatherActive && forecast != null
        val weatherCode = forecast?.code ?: 0
        val rainy = weatherLoaded && (weatherCode in 51..67 || weatherCode in 80..82 || weatherCode in 95..99)
        val snowy = weatherLoaded && (weatherCode in 71..77 || weatherCode in 85..86)
        val cold = weatherLoaded && (forecast?.temperature ?: 99.0) <= 12
        val size = prefs.getInt("gothic_moon_size", 1).coerceIn(0, 2)
        val selectedSun = if (mode == 0) {
            when {
                rainy || snowy || cold || weatherCode in 2..3 || weatherCode == 45 -> 2
                hour in 6..9 -> 3
                (forecast?.temperature ?: 0.0) >= 30 -> 0
                else -> 1
            }
        } else sunStyle
        // Ordenação das artes lunares: nova, crescente, cheia, minguante.
        // Ordenação interna do widget: crescente, cheia, minguante, nova.
        val phaseArtIndex = intArrayOf(1, 2, 3, 0)[phase]
        val weatherArtIndex = when {
            snowy || cold -> 3
            rainy -> 2
            weatherLoaded && (weatherCode in 2..3 || weatherCode == 45) -> 1
            else -> 0
        }
        val selectedArt = WeatherArtworkCatalog.select(isSun, selectedSun, weatherArtIndex,
            phaseArtIndex, prefs.getString("gothic_manual_art", null), automaticWeather = mode == 0)
        // PNGs remain intact; live data is outside the artwork and in the details dialog.
        val columns = horizontalIconColumns()
        // Panoramic alternatives open at full grid width; a deliberate per-art resize persists.
        // Existing global sizing preferences remain unchanged for the original and other arts.
        val spanKey = if (selectedArt.panoramic) "gothic_widget_span_${selectedArt.slug}" else "gothic_widget_span"
        val defaultSpan = if (selectedArt.panoramic) columns else intArrayOf(2, 3, columns)[size]
        val initialSpan = prefs.getInt(spanKey, defaultSpan).coerceIn(1, columns)
        val visualKey = "${selectedArt.slug}:$columns:$initialSpan"
        val liveText = if (forecast != null) "MET Norway: ${kotlin.math.round(forecast.temperature).toInt()}°C • previsão atual"
            else if (weatherActive) "Previsão indisponível • tocar para detalhes" else "Escolher localização • dados MET"
        if (reuseUnchanged && visualKey == lastCelestialKey && celestialView != null) {
            if (liveWeatherLabel?.text?.toString() != liveText) liveWeatherLabel?.text = liveText
            liveWeatherLabel?.contentDescription = "Dados meteorológicos MET Norway para ${weatherLocation.summary}. Os valores impressos no PNG são ilustrativos."
            return celestialView!!
        }
        lastCelestialKey = visualKey
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
        val artwork = ImageView(this).apply {
            setImageDrawable(packagedImage(selectedArt.resource))
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = selectedArt.label
        }
        val editor = WeatherResizeLayout(this, artwork, columns, initialSpan,
            { active -> widgetResizeActive = active },
            { span -> prefs.edit().putInt(spanKey, span).apply() },
            { weatherDetails() })
        editor.contentDescription = "Widget meteorológico Purple Charm. Toque para ver os detalhes; toque longo para redimensionar. Para configurar, abra Widgets no modo de edição."
        widgetEditor = editor
        panel.addView(editor, LinearLayout.LayoutParams(-1, 0, 1f))
        liveWeatherLabel = label(liveText, 11f).apply {
            tag = "live_weather_readout"
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            contentDescription = "Dados meteorológicos MET Norway para ${weatherLocation.summary}. Os valores impressos no PNG são ilustrativos."
            setOnClickListener { weatherDetails() }
        }
        panel.addView(liveWeatherLabel, LinearLayout.LayoutParams(-1, -2))
        panel.setOnClickListener { weatherDetails() }
        panel.setOnLongClickListener { editor.beginEditing(); true }
        celestialView = panel
        return panel.apply { layoutParams = ViewGroup.LayoutParams(-1, -1) }
    }

    private fun weatherDetails() {
        val forecast = weatherClient.current()
        val weatherActive = prefs.contains("gothic_weather_lat")
        val liveText = when {
            !weatherActive -> "Escolha localização automática ou busque uma cidade"
            forecast == null -> "Previsão indisponível • tentando atualizar"
            else -> {
                val time = java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                    .format(java.util.Date(forecast.time))
                val stale = if (System.currentTimeMillis() - forecast.time > 2 * 60 * 60 * 1000L)
                    " • desatualizada" else ""
                "${kotlin.math.round(forecast.temperature).toInt()}°C • ${forecast.description}" +
                    (forecast.humidity?.let { " • Umidade ${kotlin.math.round(it).toInt()}%" } ?: "") +
                    (forecast.wind?.let { " • Vento ${kotlin.math.round(it * 3.6).toInt()} km/h" } ?: "") +
                    "\nMET Norway • previsão $time$stale"
            }
        }
        val upcoming = weatherClient.upcoming()
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm XXX", Locale.getDefault())
            .withZone(java.time.ZoneId.systemDefault())
        val hourly = if (upcoming.isEmpty()) "Próximas horas indisponíveis" else
            "Próximas horas • fuso do aparelho\n" + upcoming.joinToString("\n") {
                "${formatter.format(java.time.Instant.ofEpochMilli(it.time))} • ${kotlin.math.round(it.temperature).toInt()}°C • ${it.description}"
            }
        val details = "${weatherLocation.summary}\nCoordenadas: ${prefs.getString("gothic_weather_lat", "—")}, ${prefs.getString("gothic_weather_lon", "—")}\n$liveText\nEstado de rede: ${prefs.getString("met_weather_error", "sem erro registrado")}.\nRecebimento/validação: ${prefs.getLong("met_forecast_received", 0).takeIf { it > 0 }?.let { java.text.SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(java.util.Date(it)) } ?: "não registrado"}.\nHorário da previsão no fuso do aparelho (${java.time.ZoneId.systemDefault()}). Temperatura instantânea prevista, não mínima/máxima nem observação de estação.\n\nOs valores impressos nas artes são ilustrativos. Eclipses manuais não confirmam evento ou visibilidade. Dados MET Norway, CC BY 4.0.\n\n$hourly"
        val content = label(details, 14f).apply {
            tag = "weather_hourly_details"
            setPadding(dp(16), dp(8), dp(16), dp(16))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            tag = "weather_details_scroll"
            addView(content, ViewGroup.LayoutParams(-1, -2))
        }
        SmokeDialogBuilder(this).setTitle("Previsão real • MET Norway")
            .setView(scroll).setPositiveButton("Fechar", null).show()
    }

    private fun moonWidgetOptions() {
        SmokeDialogBuilder(this).setTitle("Clima • automático por padrão")
            .setItems(arrayOf("Localização e clima", "Personalizar arte (opcional)",
                "Restaurar comportamento automático", "Redimensionar com alças", "Ocultar widget", "Sobre as artes", "Ver previsão real", "Atualizar previsão", "Mover widget para página", "Ampliar arte inteira")) { _, which ->
                when (which) {
                    0 -> gothicWeatherSettings()
                    1 -> SmokeDialogBuilder(this).setTitle("Escolher uma das ${WeatherArtworkCatalog.all.size} artes")
                        .setItems(WeatherArtworkCatalog.all.map { it.label }.toTypedArray()) { _, index ->
                            prefs.edit().putString("gothic_manual_art", WeatherArtworkCatalog.all[index].slug)
                                .putInt("gothic_celestial_mode", 0).putBoolean("gothic_moon_auto", true).apply()
                            refreshCelestialView()
                        }.setNegativeButton("Cancelar", null).show()
                    2 -> {
                        prefs.edit().remove("gothic_manual_art").putInt("gothic_celestial_mode", 0)
                            .putBoolean("gothic_moon_auto", true).apply()
                        refreshCelestialView()
                    }
                    3 -> widgetEditor?.beginEditing()
                    4 -> {
                        prefs.edit().putBoolean("gothic_moon_visible", false).apply()
                        weatherLocation.stop()
                        showHome()
                    }
                    7 -> if (!prefs.contains("gothic_weather_lat")) gothicWeatherSettings() else {
                        Toast.makeText(this, "Consultando previsão • limites de rede respeitados", Toast.LENGTH_SHORT).show()
                        val started = weatherClient.refresh(force = true) { runOnUiThread { if (!isFinishing && !isDestroyed) { refreshCelestialView(); weatherDetails() } } }
                        if (!started) weatherDetails()
                    }
                    8 -> SmokeDialogBuilder(this).setTitle("Página do clima")
                        .setItems((1..pageCount()).map { "Página $it" }.toTypedArray()) { _, p ->
                            if (androidWidgets.hasWidgets(p)) Toast.makeText(this, "Escolha uma página sem widgets Android para preservar a área completa do clima", Toast.LENGTH_LONG).show()
                            else { prefs.edit().putInt("weather_widget_page", p).apply(); refreshWorkspaceSafely() }
                        }.show()
                    9 -> {
                        if (celestialView == null) return@setItems
                        val image = (widgetEditor?.getChildAt(0) as? ViewGroup)?.getChildAt(0) as? ImageView
                        if (image != null) {
                            val full = Dialog(this)
                            full.setContentView(ImageView(this).apply { setImageDrawable(image.drawable.constantState?.newDrawable(resources)); scaleType = ImageView.ScaleType.FIT_CENTER; setBackgroundColor(Color.BLACK); setOnClickListener { full.dismiss() } })
                            full.show(); full.window?.setLayout(-1, -1)
                        }
                    }
                    6 -> weatherDetails()
                    5 -> SmokeDialogBuilder(this).setTitle("Artes originais")
                        .setMessage("As ${WeatherArtworkCatalog.all.size} artes permanecem completas, incluindo personagens, relógios, textos, cores e molduras. A previsão real permanece separada dos valores ilustrativos. Dados: MET Norway, CC BY 4.0.")
                        .setPositiveButton("Fechar", null).show()
                }
            }.setNegativeButton("Fechar", null).show()
    }

    private fun homePage(index: Int): View {
        val scroll = ScrollView(this).apply {
            tag = "home_icons_scroll"
            isFillViewport = true
            clipToPadding = true
            clipChildren = true
            setPadding(0, 0, 0, dp(3))
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val grid = GridLayout(this).apply {
            tag = "workspace_grid_$index"
            columnCount = horizontalIconColumns()
            setPadding(dp(1), 0, dp(1), dp(5))
        }
        pageCells(index).forEachIndexed { position, key ->
            val app = resolveKey(key)
            val tile = when {
                WorkspaceItems.isFolder(key) -> folderTile(key, false)
                app != null -> appTile(app, false, false, homeShortcut = true)
                else -> View(this).apply { setOnLongClickListener { enterEditMode(); true } }
            }
            tile.tag = "workspace_cell_${index}_$position"
            acceptDropAt(tile) { dropped, event ->
                val center = event.x in tile.width * 0.2f..tile.width * 0.8f &&
                    event.y in tile.height * 0.2f..tile.height * 0.8f
                placeOnPage(dropped, index, position, group = center && key.isNotEmpty())
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = homeTileHeight()
                rowSpec = GridLayout.spec(position / horizontalIconColumns())
                columnSpec = GridLayout.spec(position % horizontalIconColumns(), 1f)
            })
        }
        content.addView(grid)
        scroll.addView(content)
        // O espaço vazio pode pertencer ao conteúdo, à grade ou ao ScrollView.
        // Encaminhe o toque longo desses contêineres ao modo de edição.
        val editOnEmptySpace = View.OnLongClickListener {
            if (!drawerOpen) enterEditMode()
            true
        }
        scroll.setOnLongClickListener(editOnEmptySpace)
        content.setOnLongClickListener(editOnEmptySpace)
        grid.setOnLongClickListener(editOnEmptySpace)
        // Permite soltar um atalho também em espaços vazios da página.
        // A posição do dedo determina a célula de destino, inclusive no espaço vazio.
        scroll.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED ->
                    ownsDrag(event)
                DragEvent.ACTION_DROP -> {
                    val key = draggedKey(event)
                    if (key != null) {
                        val columns = horizontalIconColumns()
                        val cellWidth = (grid.width / columns).coerceAtLeast(1)
                        val cellHeight = (grid.getChildAt(0)?.height ?: homeTileHeight()).coerceAtLeast(1)
                        val x = event.x.coerceAtLeast(0f)
                        val y = (event.y + scroll.scrollY - grid.top).coerceAtLeast(0f)
                        val column = (x.toInt() / cellWidth).coerceIn(0, columns - 1)
                        val row = (y.toInt() / cellHeight).coerceAtLeast(0)
                        val position = (row * columns + column).coerceIn(0, pageCells(index).lastIndex)
                        placeOnPage(key, index, position)
                    } else false
                }
                DragEvent.ACTION_DRAG_ENDED -> { finishWorkspaceDrag(); true }
                else -> true
            }
        }
        val weatherPage = prefs.getInt("weather_widget_page", 0).coerceIn(0, pageCount() - 1)
        val showWeather = index == weatherPage && prefs.getBoolean("gothic_moon_visible", true)
        val external = androidWidgets.pageView(index)
        if (!showWeather && external == null && index != 0) return scroll
        val widget: View = if (showWeather) gothicMoonWidget()
            else external ?: FrameLayout(this).apply { setOnLongClickListener { enterEditMode(); true } }
        // Keep weather's entire viewport. External widgets on the same page are exposed in their own scrollable zone via the center.
        return HomeWorkspaceLayout(this, if (showWeather && external != null)
            androidWidgets.combineWithWeather(widget, external) else widget, scroll)
    }

    private fun editWorkspaceMenu() {
        val page = pageIndex
        val dialog = Dialog(this)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(16))
            background = glass(20, true)
        }
        panel.addView(label("✦ Organizar Área de Trabalho • Página ${page + 1}", 17f, true))
        for (destination in 0 until pageCount()) {
            if (destination == page) continue
            val target = glassButton("⇢ Arraste um ícone para a página ${destination + 1}", 12f)
            acceptDrop(target) { key -> dialog.dismiss(); placeOnPage(key, destination) }
            panel.addView(target)
        }
        val add = glassButton("＋ Arraste um ícone para adicionar à página ${page + 1}", 12f)
        acceptDrop(add) { key -> dialog.dismiss(); placeOnPage(key, page) }
        panel.addView(add)
        val remove = glassButton("× Arraste um ícone para remover o atalho", 12f)
        acceptDrop(remove) { key ->
            dialog.dismiss()

            val state = runCatching { workspaceItems() }.getOrNull()
            val cell = state?.pages?.getOrNull(page)?.indexOf(key) ?: -1

            if (cell >= 0) {
                removeShortcutFromPage(key, page, cell)
            } else {
                false
            }
        }
        panel.addView(remove)
        panel.addView(glassButton("✦ Gerenciar páginas", 13f).apply {
            setOnClickListener { dialog.dismiss(); managePages() }
        })
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }

    private fun updateDots() {
        dots.text = (0 until pageCount()).joinToString("  ") {
            if (it == pageIndex) "◉" else "○"
        }
    }

    private fun dock(): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            // Crystal Glass: dock 100% transparente; ícones continuam usando customIcon(app).
            background = null
            setPadding(dp(4), dp(5), dp(4), dp(5))
        }
        val keys = dockSlots()
        for (slot in 0..4) {
            val child: View = if (slot == 2) {
                ImageView(this).apply {
                    setImageResource(R.drawable.purple_original_apps)
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setOnClickListener {
                        showDrawer()
                    }
                    contentDescription = "Abrir gaveta de aplicativos"
                    tag = "dock_drawer"
                    // Botão central abre a gaveta; navegação da página inicial permanece independente.
                }
            } else {
                val dockIndex = if (slot < 2) slot else slot - 1
                val item = keys.getOrNull(dockIndex).orEmpty()
                val app = item.takeIf { it.isNotEmpty() }?.let { resolveKey(it) }
                val view = when {
                    WorkspaceItems.isFolder(item) -> folderTile(item, true)
                    app != null -> appTile(app, false, true)
                    else -> label("+", 22f).apply { setTextColor(lilac); setOnClickListener { dockChooseApp(dockIndex) } }
                }
                val holder = FrameLayout(this).apply {
                    tag = "dock_slot_$dockIndex"
                    addView(view, FrameLayout.LayoutParams(-1, -1))

                }
                acceptDrop(holder) { key -> placeInDock(key, dockIndex) }
                holder
            }
            outer.addView(child, LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return outer
    }

    // Menu independente de personalização do Purple Charm.
    // Menu de personalização compacto: faixa horizontal acima do dock.
    // Mantém o papel de parede visível e evita o painel grande de seis botões.
    private fun showClassicCustomizationMenu() {
        val dialog = Dialog(this)
        val icons = arrayOf("＋", "▧", "⚙", "⌘", "?")
        val names = arrayOf("Organizar", "Papel de parede", "Configurações", "Sistema", "Ajuda")
        val actions: Array<() -> Unit> = arrayOf(
            { editWorkspaceMenu() },
            { wallpaperMenu() },
            { settingsMenu() },
            { openSystemSettings() },
            {
                SmokeDialogBuilder(this)
                    .setTitle("Purple Charm • Ajuda")
                    .setMessage("Deslize para cima a partir do dock para personalizar. Toque no ícone central para abrir os aplicativos.")
                    .setPositiveButton("Entendi", null).show()
            }
        )
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(12), dp(5), dp(12))
            background = glass(20, true)
        }
        for (i in icons.indices) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            val symbol = label(icons[i], 25f, true).apply {
                background = glassTile()
                setTextColor(Color.WHITE)
            }
            tile.addView(symbol, LinearLayout.LayoutParams(dp(43), dp(45)))
            tile.addView(label(names[i], 9f), LinearLayout.LayoutParams(-1, dp(32)))
            tile.setOnClickListener { dialog.dismiss(); actions[i]() }
            strip.addView(tile, LinearLayout.LayoutParams(0, dp(82), 1f))
        }
        dialog.setContentView(strip)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { y = dp(90) }
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.18f)
        }
        dialog.show()
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply { y = dp(90) }
        }
    }

    private fun openSystemSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(this, "Configurações indisponíveis", Toast.LENGTH_SHORT).show()
        }
    }

    // Pesquisa Crystal Glass: busca local por aplicativos instalados.
    private fun showCrystalSearch() {
        loadApps()
        val root = buildRoot(R.drawable.wallpaper_neon)
        // Pesquisa em grade: reutiliza os ícones e as ações da gaveta,
        // evitando duplicar a lógica de abertura e toque longo.
        val results = GridLayout(this).apply {
            columnCount = horizontalIconColumns()
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(18)
            setPadding(dp(10), dp(2), dp(4), dp(2))
        }
        val input = EditText(this).apply {
            hint = "⌕  Pesquisar aplicativos"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(lilac)
            background = null
        }
        searchRow.addView(input, LinearLayout.LayoutParams(0, dp(52), 1f))
        searchRow.addView(TextView(this).apply {
            text = "✕"
            textSize = 23f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setOnClickListener {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(input.windowToken, 0)
                showDrawer()
            }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(searchRow, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(6), dp(12), dp(10))
        })
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setPadding(dp(10), dp(8), dp(10), dp(8))
            addView(results)
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun refresh(query: String) {
            results.removeAllViews()
            if (query.isBlank()) return
            val term = normalize(query.trim())
            val filtered = apps.filter {
                normalize(appLabel(it)).startsWith(term)
            }.sortedWith(compareBy<ResolveInfo> {
                normalize(appLabel(it)) != term
            }.thenBy {
                normalize(appLabel(it))
            })
            filtered.forEach { app ->
                // Toque curto abre; toque longo oferece adicionar à página/dock.
                // O arraste direto entre telas será implementado separadamente:
                // trocar a tela durante ACTION_DRAG pode cancelar o gesto.
                results.addView(appTile(app, false, false),
                    GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    })
            }
            if (filtered.isEmpty()) {
                results.addView(label("Nenhum aplicativo encontrado", 14f))
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refresh(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        input.requestFocus()
        input.post {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun crystalSearchBar(): View =
        glassButton("⌕  Pesquisar aplicativos...", 15f).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setOnClickListener { showCrystalSearch() }
        }

    // Substitui os alvos permanentes da gaveta por um menu no toque longo.
    private fun homeAppActions(app: ResolveInfo, sourceTile: View) {
        val name = appLabel(app)
        val sourceKey = appKey(app)
        val sourcePage = pageIndex
        val sourceCell = pageCells(sourcePage).indexOf(sourceKey)
        SmokeDialogBuilder(this)
            .setTitle(name)
            .setItems(arrayOf("Mover para outra página", "Mover por arraste / criar pasta", "Remover atalho da tela inicial")) { _, choice ->
                if (choice == 0) {
                    moveShortcutPageMenu(app)
                } else if (choice == 1) {
                    if (!startAppDrag(sourceTile, app)) {
                        Toast.makeText(this, "Não foi possível iniciar o arraste", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    SmokeDialogBuilder(this)
                        .setTitle("Remover atalho?")
                        .setMessage("Remover $name da tela inicial? O aplicativo continuará instalado.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Remover") { _, _ ->
                            removeShortcutFromPage(sourceKey, sourcePage, sourceCell)
                        }
                        .show()
                }
            }.show()
    }

    private fun moveShortcutPageMenu(app: ResolveInfo) {
        moveWorkspaceItemPageMenu(appKey(app), appLabel(app))
    }

    private fun moveWorkspaceItemPageMenu(key: String, name: String) {
        val sourcePage = pageCells(pageIndex).indexOf(key).takeIf { it >= 0 }?.let { pageIndex }
            ?: (0 until pageCount()).firstOrNull { key in pageCells(it) }
        val destinations = (0 until pageCount()).filter { it != sourcePage }
        if (destinations.isEmpty()) {
            Toast.makeText(this, "Crie outra página antes de mover este atalho", Toast.LENGTH_SHORT).show()
            return
        }
        SmokeDialogBuilder(this)
            .setTitle("Mover $name")
            .setItems(destinations.map { "Página ${it + 1}" }.toTypedArray()) { _, choice ->
                val destination = destinations.getOrNull(choice) ?: return@setItems
                if (!placeOnPage(key, destination)) {
                    Toast.makeText(this, "Não foi possível mover; confira se a página tem espaço", Toast.LENGTH_SHORT).show()
                } else showHome()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun drawerAppActions(app: ResolveInfo) {
        val name = appLabel(app)
        val pkg = app.activityInfo.packageName
        val systemApp = (app.activityInfo.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val options = if (systemApp) {
            arrayOf("Adicionar à tela inicial", "Informações do aplicativo")
        } else {
            arrayOf("Adicionar à tela inicial", "Desinstalar aplicativo", "Informações do aplicativo")
        }
        SmokeDialogBuilder(this)
            .setTitle(name)
            .setItems(options) { _, choice ->
                when {
                    choice == 0 -> appPlacementMenu(app)
                    !systemApp && choice == 1 -> {
                        SmokeDialogBuilder(this)
                            .setTitle("Desinstalar $name?")
                            .setMessage("O Android pedirá sua confirmação antes de desinstalar o aplicativo.")
                            .setNegativeButton("Cancelar", null)
                            .setPositiveButton("Continuar") { _, _ ->
                                try {
                                    startActivity(Intent(Intent.ACTION_DELETE,
                                        Uri.parse("package:$pkg")))
                                } catch (_: Exception) {
                                    Toast.makeText(this, "Não foi possível abrir a desinstalação",
                                        Toast.LENGTH_SHORT).show()
                                }
                            }.show()
                    }
                    else -> try {
                        startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$pkg")))
                    } catch (_: Exception) {
                        Toast.makeText(this, "Não foi possível abrir as informações",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }.show()
    }

    private fun appPlacementMenu(app: ResolveInfo) {
        val options = (1..pageCount()).map { "Adicionar à página $it" } +
            listOf("Adicionar ao dock", "Gerenciar páginas")
        SmokeDialogBuilder(this)
            .setTitle(appLabel(app))
            .setItems(options.toTypedArray()) { _, index ->
                val key = appKey(app)
                when {
                    index < pageCount() -> placeOnPage(key, index)
                    index == pageCount() -> {
                        val free = dockSlots().indexOfFirst { it.isEmpty() }
                        placeInDock(key, if (free >= 0) free else 3)
                    }
                    else -> managePages()
                }
            }.show()
    }

    private fun drawerCategory(app: ResolveInfo): String {
        val pkg = app.activityInfo.packageName.lowercase(Locale.ROOT)
        val name = normalize(appLabel(app))
        return when {
            listOf("whatsapp", "telegram", "discord", "messenger", "facebook", "instagram",
                "signal", "sms", "contacts", "contatos", "telefone", "phone", "gmail",
                "email", "outlook", "messages").any { it in pkg || it in name } -> "Comunicação"
            listOf("youtube", "netflix", "spotify", "music", "musica", "game",
                "jogo", "tiktok", "twitch", "video", "player", "disney",
                "primevideo", "pinterest").any { it in pkg || it in name } -> "Entretenimento"
            else -> "Outros"
        }
    }

    private fun showDrawer() {
        drawerOpen = true
        loadApps()
        cachedDrawerHost?.let { cached ->
            homeHost = cached
            setContentView(cached)
            return
        }
        val root = buildRoot(R.drawable.wallpaper_neon)
        cachedDrawerHost = homeHost
        val categories = listOf("Tudo", "Comunicação", "Entretenimento", "Outros")
        var selected = "Tudo"
        val tabs = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tabsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(tabs)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(tabsScroll, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { settingsMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        header.tag = "drawer_header"
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply {
            clipToPadding = false
            addView(content)
        }
        val alphabet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val main = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        main.addView(scroll, LinearLayout.LayoutParams(0, -1, 1f))
        main.addView(alphabet, LinearLayout.LayoutParams(dp(32), -1))
        alphabet.tag = "drawer_alphabet"
        root.addView(main, LinearLayout.LayoutParams(-1, 0, 1f))

        fun render() {
            content.removeAllViews()
            val visible = if (selected == "Tudo") apps else apps.filter {
                drawerCategory(it) == selected
            }
            val sorted = visible.sortedBy { normalize(appLabel(it)) }
            val grid = DrawerAppGrid(this).apply { tag = "drawer_grid" }
            val anchors = linkedMapOf<String, Int>()
            sorted.forEachIndexed { index, app ->
                val letter = normalize(appLabel(app)).firstOrNull()?.uppercaseChar()?.toString() ?: "#"
                anchors.putIfAbsent(letter, index)
                grid.addView(appTile(app, true, false, dense = true).apply { tag = "drawer_app_${appKey(app)}" })
            }
            content.addView(grid, LinearLayout.LayoutParams(-1, -2))
            alphabet.removeAllViews()
            val letters = anchors.keys.toList()
            letters.forEach { letter ->
                alphabet.addView(TextView(this).apply {
                    text = letter
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(lilac)
                }, LinearLayout.LayoutParams(-1, 0, 1f))
            }
            // Deslizar sobre o índice percorre a lista sem exigir toques individuais.
            alphabet.setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                        if (letters.isNotEmpty() && view.height > 0) {
                            val first = alphabet.getChildAt(0)
                            val last = alphabet.getChildAt(alphabet.childCount - 1)
                            val bandHeight = (last.bottom - first.top).coerceAtLeast(1)
                            val index = (((event.y - first.top) / bandHeight) * letters.size)
                                .toInt().coerceIn(0, letters.lastIndex)
                            val target = grid.getChildAt(anchors.getValue(letters[index]))
                            if (target != null) scroll.scrollTo(0, target.top)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> true
                    else -> false
                }
            }
            tabs.removeAllViews()
            categories.forEach { category ->
                tabs.addView(TextView(this).apply {
                    text = category
                    textSize = 14f
                    maxLines = 1
                    setTextColor(if (selected == category) lilac else Color.WHITE)
                    setPadding(dp(11), dp(10), dp(11), dp(10))
                    setOnClickListener {
                        if (selected == category) return@setOnClickListener
                        selected = category
                        render()
                        scroll.scrollTo(0, 0)
                    }
                })
            }
        }
        render()
        root.addView(crystalSearchBar().apply {
            tag = "drawer_search"
            setPadding(dp(12), dp(7), dp(12), dp(7))
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(8), dp(3), dp(8), dp(3)) })
    }

    private fun appGrid(
        items: List<ResolveInfo>,
        allowFavorite: Boolean,
        columns: Int = horizontalIconColumns()
    ): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = columns
            background = null
            setPadding(dp(1), 0, dp(1), 0)
        }

        items.forEach { app ->
            grid.addView(
                appTile(app, allowFavorite, false, dense = true),
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(88)
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
        compact: Boolean,
        dense: Boolean = false,
        homeShortcut: Boolean = false
    ): View {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = null
            setPadding(dp(2), dp(if (dense) 2 else 6), dp(2), dp(if (dense) 2 else 8))
        }

        val iconSize = if (compact) 35 else 43

        val iconFrame = FrameLayout(this).apply {
            // The icon itself supplies its original art or the fallback frame.
            background = null
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

        if (allowFavorite && !compact) iconFrame.addView(TextView(this).apply {
            text = "⋮"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(lilac)
            contentDescription = "Ações de ${app.loadLabel(packageManager)}"
            setOnClickListener { drawerAppActions(app) }
        }, FrameLayout.LayoutParams(dp(22), dp(24), Gravity.TOP or Gravity.END))

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
                    appLabel(app),
                    if (homeShortcut) 9f else 10f
                ).apply {
                    maxLines = if (homeShortcut) 3 else 2
                    minLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                    setPadding(0, dp(4), 0, 0)
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

        tile.setOnLongClickListener {
            if (drawerOpen) {
                val sourceTile = tile
                pendingDrawerDrag = true
                showHome()
                window.decorView.post {
                    pendingDrawerDrag = false
                    if (!startItemDrag(pages, appKey(app), View.DragShadowBuilder(sourceTile))) {
                        Toast.makeText(this, "Não foi possível iniciar o arraste. Tente novamente.", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            } else if (homeShortcut) {
                // Keep the same touch sequence alive: a dialog would consume the
                // long press and make the user lift their finger before dragging.
                if (!startAppDrag(tile, app)) {
                    Toast.makeText(this, "Não foi possível iniciar o arraste. Tente novamente.", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                // O primeiro toque longo inicia o arraste com o mesmo dedo.
                // Não reconstruir a tela com enterEditMode() enquanto o dedo
                // está pressionado: isso cancelaria o gesto em andamento.
                if (!startAppDrag(tile, app)) Toast.makeText(this, "Não foi possível iniciar o arraste. Tente novamente.", Toast.LENGTH_SHORT).show()
                true
            }
        }

        return tile
    }

    private fun cancelDispatchedTouch(event: MotionEvent) {
        val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
        try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
    }

    private fun gestureStartedInWorkspace(event: MotionEvent): Boolean {
        val workspace = window.decorView.findViewWithTag<View>("workspace_drag_surface") ?: return false
        val visibleBounds = android.graphics.Rect()
        if (!workspace.getGlobalVisibleRect(visibleBounds)) return false
        val location = IntArray(2)
        workspace.getLocationInWindow(location)
        val bounds = android.graphics.Rect(
            location[0], location[1], location[0] + workspace.width, location[1] + workspace.height
        )
        val rootInsets = window.decorView.rootWindowInsets
        val gestureInsets = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                rootInsets?.getInsets(WindowInsets.Type.systemGestures())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                @Suppress("DEPRECATION")
                rootInsets?.systemGestureInsets
            }
            else -> null
        }
        gestureInsets?.let { bounds.inset(it.left, it.top, it.right, it.bottom) }
        return bounds.contains(event.x.toInt(), event.y.toInt())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            workspaceGestureCandidate = gestureStartedInWorkspace(event)
        }
        val workspaceGesture = workspaceGestureCandidate
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            workspaceGestureCandidate = false
        }
        if (action == MotionEvent.ACTION_DOWN && ::pages.isInitialized) {
            val external = pages.currentView?.findViewWithTag<View>("external_widgets_scroll")
            val bounds = android.graphics.Rect()
            externalWidgetGesture = external != null && external.getGlobalVisibleRect(bounds) &&
                bounds.contains(event.rawX.toInt(), event.rawY.toInt())
        }
        if (externalWidgetGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) externalWidgetGesture = false
            return super.dispatchTouchEvent(event)
        }
        if (pendingDrawerDrag) return super.dispatchTouchEvent(event)
        if (widgetResizeActive || widgetResizeGesture) {
            if (action == MotionEvent.ACTION_DOWN) {
                widgetResizeGesture = true
                val bounds = android.graphics.Rect()
                val editor = widgetEditor
                if (editor == null || !editor.getGlobalVisibleRect(bounds) ||
                    !bounds.contains(event.rawX.toInt(), event.rawY.toInt())) editor?.endEditing()
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) widgetResizeGesture = false
            return super.dispatchTouchEvent(event)
        }
        if (!drawerOpen && !dragging && workspaceGesture) {
            when (action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    swipeConsumed = false
                    pendingVerticalSwipe = false
                }
                MotionEvent.ACTION_MOVE -> if (!swipeConsumed) {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    when (WorkspaceGestureClassifier.classify(dx, dy, dp(65).toFloat())) {
                        WorkspaceSwipe.HORIZONTAL -> {
                            swipeConsumed = true
                            lastTapAt = 0L
                            cancelDispatchedTouch(event)
                            changePage(pageIndex + if (dx < 0) 1 else -1)
                            return true
                        }
                        WorkspaceSwipe.VERTICAL -> {
                            swipeConsumed = true
                            pendingVerticalSwipe = true
                            cancelDispatchedTouch(event)
                            return true
                        }
                        WorkspaceSwipe.NONE -> Unit
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (swipeConsumed) {
                        val runVertical = pendingVerticalSwipe
                        pendingVerticalSwipe = false
                        swipeConsumed = false
                        if (runVertical) {
                            val dy = event.y - downY
                            val configured = if (dy < 0) setting("swipe_up", "options")
                                else setting("swipe_down", "drawer")
                            runGesture(configured)
                        }
                        return true
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) < dp(22) && abs(dy) < dp(22)) {
                        val now = android.os.SystemClock.uptimeMillis()
                        val doubleAction = setting("double_tap", "none")
                        if (doubleAction != "none" && now - lastTapAt in 50..350 &&
                            abs(event.x - lastTapX) < dp(48) && abs(event.y - lastTapY) < dp(48)) {
                            lastTapAt = 0L
                            cancelDispatchedTouch(event)
                            if (runGesture(doubleAction)) return true
                        }
                        lastTapAt = now; lastTapX = event.x; lastTapY = event.y
                    }
                    when (WorkspaceGestureClassifier.classify(dx, dy, dp(65).toFloat())) {
                        WorkspaceSwipe.HORIZONTAL -> {
                            lastTapAt = 0L
                            cancelDispatchedTouch(event)
                            changePage(pageIndex + if (dx < 0) 1 else -1)
                            return true
                        }
                        WorkspaceSwipe.VERTICAL -> {
                            val configured = if (dy < 0) setting("swipe_up", "options")
                                else setting("swipe_down", "drawer")
                            cancelDispatchedTouch(event)
                            runGesture(configured)
                            return true
                        }
                        WorkspaceSwipe.NONE -> Unit
                    }
                }
                MotionEvent.ACTION_CANCEL -> { swipeConsumed = false; pendingVerticalSwipe = false }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (dragging) { cancelWorkspaceDrag(); return }
        if (widgetResizeActive) { widgetEditor?.endEditing(); return }
        if (folderDialog?.isShowing == true) { folderDialog?.cancel(); return }
        if (editMode) {
            leaveEditMode()
            return
        }
        val homePage = HomePageSelection.read(prefs, pageCount())
        if (drawerOpen) {
            pageIndex = homePage
            showHome()
        } else if (pageIndex != homePage) {
            changePage(homePage)
        } else {
            super.onBackPressed()
        }
    }
}
