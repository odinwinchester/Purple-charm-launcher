package com.purplecharm.launcher

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
    private var folderDialog: Dialog? = null
    private var openFolderId: String? = null
    private var pendingDrawerDrag = false

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
        if (editMode || drawerOpen) return
        editMode = true
        // Organização por agitação desativada: não alterar atalhos involuntariamente.
        showHome()
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
    private val maxPages = 8

    private fun pageCount(): Int = prefs.getInt("layout_page_count", 3).coerceIn(3, maxPages)

    private fun managePages() {
        val options = arrayOf("＋ Adicionar página", "－ Excluir página atual (se vazia)")
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
                } else {
                    if (pageCount() <= 3) {
                        Toast.makeText(this, "Mantenha pelo menos 3 páginas", Toast.LENGTH_SHORT).show()
                    } else if (savedPage(pageIndex).isNotEmpty()) {
                        Toast.makeText(this, "Remova os atalhos desta página antes de excluir", Toast.LENGTH_LONG).show()
                    } else {
                        val count = pageCount()
                        val edit = prefs.edit()
                        for (i in pageIndex until count - 1) {
                            edit.putString("layout_page_$i", savedPage(i + 1).joinToString("|"))
                            val moved = prefs.getString("layout_page_cells_${i + 1}", null)
                            if (moved == null) edit.remove("layout_page_cells_$i")
                            else edit.putString("layout_page_cells_$i", moved)
                        }
                        edit.remove("layout_page_cells_${count - 1}")
                        edit.remove("layout_page_${count - 1}")
                            .putInt("layout_page_count", count - 1).apply()
                        pageIndex = pageIndex.coerceAtMost(count - 2)
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
        dragSession?.cancel()
        runCatching { dragSource?.cancelDragAndDrop() }
        finishWorkspaceDrag()
    }

    private fun completeWorkspaceDrag() {
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
                    if (reopen != null) showFolder(reopen)
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
            if (reopen != null) showFolder(reopen)
        }
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

    private fun removeShortcut(key: String): Boolean {
        val state = runCatching { workspaceItems() }.getOrNull() ?: return false
        if (WorkspaceItems.isFolder(key)) {
            Toast.makeText(this, "Abra a pasta para mover ou remover seus atalhos com segurança", Toast.LENGTH_SHORT).show()
            return false
        }
        return saveWorkspaceEdit(WorkspaceEdits.removeShortcut(state, key))
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
                resolveKey(key)?.let { setImageDrawable(customIcon(it)) }
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
        val dialog = Dialog(this)
        folderDialog = dialog
        openFolderId = id
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = glass(18, true)
        }
        panel.addView(label(folder.name, 19f, true).apply { setOnClickListener { renameFolder(id) } })
        panel.addView(label("Toque para abrir • segure e arraste para reorganizar", 11f))
        val grid = GridLayout(this).apply { columnCount = 4 }
        folder.apps.forEachIndexed { index, key ->
            val app = resolveKey(key)
            val tile = if (app != null) appTile(app, false, false) else label(key.substringAfterLast('/'), 10f)
            tile.tag = "folder_member_$index"
            tile.setOnLongClickListener { startItemDrag(tile, key) }
            acceptDrop(tile) { incoming ->
                val state = runCatching { workspaceItems() }.getOrNull()
                state != null && saveWorkspaceEdit(WorkspaceEdits.intoFolder(state, incoming, id, index))
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0; height = dp(92)
                columnSpec = GridLayout.spec(index % 4, 1f); rowSpec = GridLayout.spec(index / 4)
            })
        }
        val scroll = ScrollView(this).apply { addView(grid) }
        panel.addView(scroll, LinearLayout.LayoutParams(-1, dp(230)))
        val addTarget = glassButton("Solte aqui para adicionar à pasta", 12f)
        acceptDrop(addTarget) { incoming ->
            val state = runCatching { workspaceItems() }.getOrNull()
            state != null && saveWorkspaceEdit(WorkspaceEdits.intoFolder(state, incoming, id))
        }
        panel.addView(addTarget)
        val extractTarget = glassButton("Solte aqui para mover à tela inicial", 12f)
        acceptDrop(extractTarget) { incoming ->
            val state = runCatching { workspaceItems() }.getOrNull()
            state != null && saveWorkspaceEdit(WorkspaceEdits.extract(state, id, incoming, pageIndex))
        }
        panel.addView(extractTarget)
        val tools = LinearLayout(this)
        listOf("Adicionar" to { addFolderApps(id) }, "Renomear" to { renameFolder(id) },
            "Organizar" to { folderAppActions(id) }).forEach { (title, action) ->
            tools.addView(glassButton(title, 12f).apply { setOnClickListener { action() } },
                LinearLayout.LayoutParams(0, -2, 1f))
        }
        panel.addView(tools)
        panel.addView(glassButton("Fechar", 12f).apply { setOnClickListener { dialog.dismiss() } })
        dialog.setOnCancelListener { if (dragging) cancelWorkspaceDrag() }
        dialog.setOnDismissListener {
            if (folderDialog === dialog) { folderDialog = null; openFolderId = null }
        }
        dialog.setContentView(panel)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun renameFolder(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull() ?: return
        val input = EditText(this).apply { setText(folder.name); setSingleLine(true) }
        AlertDialog.Builder(this).setTitle("Renomear pasta").setView(input)
            .setPositiveButton("Salvar") { _, _ ->
                val state = runCatching { workspaceItems() }.getOrNull()
                if (state != null) saveWorkspaceEdit(WorkspaceEdits.rename(state, id, input.text.toString()))
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun addFolderApps(id: String) {
        val folder = runCatching { workspaceStore.folders()[id] }.getOrNull() ?: return
        val available = apps.filter { appKey(it) !in folder.apps }
        val selected = BooleanArray(available.size)
        AlertDialog.Builder(this).setTitle("Adicionar aplicativos")
            .setMultiChoiceItems(available.map { it.loadLabel(packageManager).toString() }.toTypedArray(), selected) { _, index, checked ->
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
        AlertDialog.Builder(this).setTitle("Organizar aplicativos da pasta")
            .setItems(folder.apps.map { resolveKey(it)?.loadLabel(packageManager)?.toString() ?: it.substringAfterLast('/') }.toTypedArray()) { _, index ->
                val key = folder.apps[index]
                AlertDialog.Builder(this).setTitle(resolveKey(key)?.loadLabel(packageManager)?.toString() ?: key)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("✦ Purple Charm • Configurações")
            .setItems(arrayOf("⚙ Configurações do sistema", "✦ Configurações do tema",
                "▦ Configurações do launcher")) { _, choice ->
                when (choice) {
                    0 -> try {
                        startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                    } catch (_: Exception) {
                        android.widget.Toast.makeText(this, "Não foi possível abrir o sistema", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    1 -> settingsCategory("Tema")
                    2 -> settingsCategory("Launcher")
                }
            }.show()
    }

    private fun settingsCategory(category: String) {
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
        option("✦ Importar tema Purple Charm Retro 0.4") {
            startActivity(Intent(this, RetroImporterActivity::class.java))
        }
        option("✦ Máscara para ícones sem tema: " +
            if (setting("fallback_skin", "original") == "ghost") "Cristal Ghost Fire" else "Original") {
            chooseSetting("Ícones sem arte original", "fallback_skin",
                arrayOf("Ícone original do aplicativo", "Cristal violeta • inspirado em Ghost Fire"),
                arrayOf("original", "ghost"), "original")
        }
        option("✦ Papéis de parede Purple Charm") { wallpaperMenu() }
        option("✦ Aplicar Olho de Cristal na tela de bloqueio") {
            wallpaperDestination("eye_crystal", R.drawable.purple_eye_crystal, null)
        }
        } else {
        option("✦ Deslizar para cima: " + gestureName(setting("swipe_up", "drawer"))) {
            gestureSetting("Deslizar para cima", "swipe_up", "drawer")
        }
        option("✦ Deslizar para baixo: " + gestureName(setting("swipe_down", "none"))) {
            gestureSetting("Deslizar para baixo", "swipe_down", "none")
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
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Restaurar configurações?")
                .setMessage("Isso não altera seus wallpapers nem a organização.")
                .setPositiveButton("Restaurar") { _, _ ->
                    prefs.edit().remove("pc_swipe_up").remove("pc_swipe_down").remove("pc_double_tap")
                        .remove("pc_columns").apply()
                    showHome()
                }.setNegativeButton("Cancelar", null).show()
        }
        option("✦ Restaurar organização dos ícones") {
            AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert).setTitle("Restaurar organização?")
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
        "drawer" -> "Abrir aplicativos"
        "search" -> "Pesquisa Crystal Glass"
        "settings" -> "Configurações"
        "wallpaper" -> "Papel de parede"
        "home" -> "Página inicial"
        else -> "Nenhuma ação"
    }

    private fun gestureSetting(title: String, key: String, default: String) {
        val values = arrayOf("drawer", "search", "settings", "wallpaper", "home", "none")
        chooseSetting(title, key, values.map { gestureName(it) }.toTypedArray(), values, default)
    }

    private fun runGesture(action: String): Boolean {
        when (action) {
            "drawer" -> showDrawer()
            "search" -> showCrystalSearch()
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

    private var appsLoadedAt = 0L

    private fun loadApps() {
        // Evita consultar e ordenar todos os aplicativos a cada retorno à Home.
        val now = android.os.SystemClock.uptimeMillis()
        if (apps.isNotEmpty() && now - appsLoadedAt < 10_000L) return
        appsLoadedAt = now
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
            "ghost_fire" -> backgroundImage.setImageResource(R.drawable.wallpaper_ghost_fire)
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

    private val wallpaperOptions = listOf(
        Triple("Floral original", "floral", R.drawable.purple_original_wallpaper),
        Triple("Olho de Crystal", "eye_crystal", R.drawable.purple_eye_crystal),
        Triple("Ghost Fire", "ghost_fire", R.drawable.wallpaper_ghost_fire)
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
            setBackgroundColor(Color.BLACK)
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
                setImageResource(res)
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
            if (res != null) setImageResource(res) else setImageURI(uri)
        }
        layout.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(glassButton("✦ Aplicar papel de parede", 17f).apply {
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

        val original = app.loadIcon(packageManager)
        if (setting("fallback_skin", "original") != "ghost") return original

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
        val workspace = FrameLayout(this).apply {
            clipChildren = true
            clipToPadding = true
            minimumHeight = 0
        }
        workspace.addView(pages, FrameLayout.LayoutParams(-1, -1))
        for (direction in listOf(-1, 1)) {
            val edge = View(this)
            var pending: Runnable? = null
            fun cancelPending() {
                pending?.let { edge.removeCallbacks(it) }
                pending = null
            }
            edge.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED ->
                        ownsDrag(event)
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        cancelPending()
                        val task = object : Runnable {
                            override fun run() {
                                if (!dragging || drawerOpen) return
                                val target = pageIndex + direction
                                if (target in 0 until pageCount()) {
                                    // Sem animação: não recria views nem cancela o drag ativo.
                                    pages.inAnimation = null
                                    pages.outAnimation = null
                                    pageIndex = target
                                    pages.displayedChild = target
                                    updateDots()
                                    // Mantendo o dedo na borda, pode atravessar outras páginas.
                                    edge.postDelayed(this, 650L)
                                    pending = this
                                }
                            }
                        }
                        pending = task
                        edge.postDelayed(task, 550L)
                        true
                    }
                    DragEvent.ACTION_DRAG_EXITED -> { cancelPending(); true }
                    DragEvent.ACTION_DROP -> {
                        cancelPending()
                        val key = draggedKey(event)
                        if (key != null) placeOnPage(key, pageIndex) else false
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        cancelPending()
                        finishWorkspaceDrag()
                        true
                    }
                    else -> true
                }
            }
            workspace.addView(edge, FrameLayout.LayoutParams(
                dp(32), -1,
                if (direction < 0) Gravity.START else Gravity.END
            ))
        }
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

        root.addView(dock())
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

    override fun onDestroy() {
        weatherHandler.removeCallbacks(weatherTick)
        weatherLocation.close()
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
    private var widgetEditor: WeatherResizeLayout? = null
    private var widgetResizeActive = false
    private var widgetResizeGesture = false
    private var iconViewportGesture = false

    // Replace only the widget, preserving pages, scroll state and drag/drop.
    private fun refreshCelestialView() {
        if (isFinishing || isDestroyed || dragging || widgetResizeActive) return
        val old = celestialView ?: return
        val parent = old.parent as? ViewGroup ?: return
        val index = parent.indexOfChild(old)
        parent.removeViewAt(index)
        parent.addView(gothicMoonWidget(), index)
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

    private fun gothicMoonWidget(): View {
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
        val isSun = when (mode) {
            1 -> true
            2 -> false
            else -> forecast?.isDay ?: (hour in 6..17)
        }
        val sunStyle = prefs.getInt("gothic_sun_style", 0).coerceIn(0, 3)
        val weatherActive = prefs.contains("gothic_weather_lat")
        val weatherLoaded = weatherActive && forecast != null
        val weatherCode = forecast?.code ?: 0
        val rainy = weatherLoaded && (weatherCode in 51..67 || weatherCode in 80..82 || weatherCode in 95..99)
        val snowy = weatherLoaded && (weatherCode in 71..77 || weatherCode in 85..86)
        val cold = weatherLoaded && (forecast?.temperature ?: 99.0) <= 12
        val size = prefs.getInt("gothic_moon_size", 1).coerceIn(0, 2)
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
        }
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
            phaseArtIndex, prefs.getString("gothic_manual_art", null))
        val artwork = ImageView(this).apply {
            setImageResource(selectedArt.resource)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = selectedArt.label
        }
        // Original PNGs remain byte-for-byte intact, including their sample lettering.
        // Real forecast is explicitly separated so sample values cannot be mistaken for live data.
        val columns = horizontalIconColumns()
        val initialSpan = prefs.getInt("gothic_widget_span", intArrayOf(2, 3, columns)[size]).coerceIn(1, columns)
        val editor = WeatherResizeLayout(this, artwork, columns, initialSpan,
            { active -> widgetResizeActive = active },
            { span -> prefs.edit().putInt("gothic_widget_span", span).apply() },
            { moonWidgetOptions() })
        widgetEditor = editor
        panel.addView(editor, LinearLayout.LayoutParams(-1, 0, 2f))
        val readout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        readout.addView(label("Arte original • valores impressos ilustrativos", 10f))
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
        val locationText = weatherLocation.summary
        readout.addView(label("$locationText\n$liveText", 11f).apply {
            contentDescription = "$locationText. $liveText"
        })
        val readoutScroll = ScrollView(this).apply {
            clipChildren = true
            clipToPadding = true
            addView(readout)
        }
        panel.addView(readoutScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.setOnClickListener { moonWidgetOptions() }
        panel.setOnLongClickListener { editor.beginEditing(); true }
        celestialView = panel
        return panel.apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
    }

    private fun moonWidgetOptions() {
        AlertDialog.Builder(this).setTitle("Clima • automático por padrão")
            .setItems(arrayOf("Localização e clima", "Personalizar arte (opcional)",
                "Restaurar comportamento automático", "Redimensionar com alças", "Ocultar widget", "Sobre as artes")) { _, which ->
                when (which) {
                    0 -> gothicWeatherSettings()
                    1 -> AlertDialog.Builder(this).setTitle("Escolher uma das 20 artes originais")
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
                    5 -> AlertDialog.Builder(this).setTitle("Artes originais")
                        .setMessage("As 20 artes permanecem completas, incluindo personagens, relógios, textos, cores e molduras. A previsão real permanece separada dos valores ilustrativos. Dados: MET Norway, CC BY 4.0.")
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
            columnCount = horizontalIconColumns()
            setPadding(dp(1), 0, dp(1), dp(5))
        }
        pageCells(index).forEachIndexed { position, key ->
            val app = resolveKey(key)
            val tile = when {
                WorkspaceItems.isFolder(key) -> folderTile(key, false)
                app != null -> appTile(app, false, false)
                else -> View(this)
            }
            tile.tag = "workspace_cell_${index}_$position"
            acceptDropAt(tile) { dropped, event ->
                val center = event.x in tile.width * 0.2f..tile.width * 0.8f &&
                    event.y in tile.height * 0.2f..tile.height * 0.8f
                placeOnPage(dropped, index, position, group = center && key.isNotEmpty())
            }
            grid.addView(tile, GridLayout.LayoutParams().apply {
                width = 0
                height = dp(86)
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
                        val cellHeight = (grid.getChildAt(0)?.height ?: dp(86)).coerceAtLeast(1)
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
        if (index != 0) return scroll
        val widget = if (prefs.getBoolean("gothic_moon_visible", true)) gothicMoonWidget()
            else FrameLayout(this).also { celestialView = null }
        return HomeWorkspaceLayout(this, widget, scroll)
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
        acceptDrop(remove) { key -> dialog.dismiss(); removeShortcut(key) }
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
                        if (pageIndex != 0 || drawerOpen) {
                            pageIndex = 0
                            showHome()
                        }
                    }
                    // Toque longo reservado: personalização permanece no gesto de deslizar para cima.
                }
            } else {
                val dockIndex = if (slot < 2) slot else slot - 1
                val item = keys.getOrNull(dockIndex).orEmpty()
                val app = item.takeIf { it.isNotEmpty() }?.let { resolveKey(it) }
                val view = when {
                    WorkspaceItems.isFolder(item) -> folderTile(item, true)
                    app != null -> appTile(app, false, true)
                    else -> label("+", 22f).apply { setTextColor(lilac) }
                }
                acceptDrop(view) { key -> placeInDock(key, dockIndex) }
                view
            }
            outer.addView(child, LinearLayout.LayoutParams(0, dp(61), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        return outer
    }

    // Menu clássico do GO Launcher EX, redesenhado para o Purple Charm.
    // Menu de personalização compacto: faixa horizontal acima do dock.
    // Mantém o papel de parede visível e evita o painel grande de seis botões.
    private fun showClassicCustomizationMenu() {
        val dialog = Dialog(this)
        val icons = arrayOf("＋", "▧", "✦", "⚙", "⌘", "?")
        val names = arrayOf("Organizar", "Papel de parede", "Tema",
            "GO config.", "Sistema", "Ajuda")
        val actions: Array<() -> Unit> = arrayOf(
            { editWorkspaceMenu() },
            { wallpaperMenu() },
            { settingsMenu() },
            { settingsMenu() },
            {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "Configurações indisponíveis", Toast.LENGTH_SHORT).show()
                }
            },
            {
                AlertDialog.Builder(this)
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

    // Pesquisa Crystal Glass: busca local por aplicativos instalados.
    private fun showCrystalSearch() {
        loadApps()
        val root = buildRoot()
        backgroundImage.setImageResource(R.drawable.wallpaper_neon)
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
                normalize(it.loadLabel(packageManager).toString()).startsWith(term)
            }.sortedWith(compareBy<ResolveInfo> {
                normalize(it.loadLabel(packageManager).toString()) != term
            }.thenBy {
                normalize(it.loadLabel(packageManager).toString())
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
    private fun homeAppActions(app: ResolveInfo) {
        val name = app.loadLabel(packageManager).toString()
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(name)
            .setItems(arrayOf("Mover para outra página", "Remover atalho da tela inicial")) { _, choice ->
                if (choice == 0) {
                    appPlacementMenu(app)
                } else {
                    AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Remover atalho?")
                        .setMessage("Remover $name da tela inicial? O aplicativo continuará instalado.")
                        .setNegativeButton("Cancelar", null)
                        .setPositiveButton("Remover") { _, _ -> removeShortcut(appKey(app)) }
                        .show()
                }
            }.show()
    }

    private fun drawerAppActions(app: ResolveInfo) {
        val name = app.loadLabel(packageManager).toString()
        val pkg = app.activityInfo.packageName
        val systemApp = (app.activityInfo.applicationInfo.flags and
            android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val options = if (systemApp) {
            arrayOf("Adicionar à tela inicial", "Informações do aplicativo")
        } else {
            arrayOf("Adicionar à tela inicial", "Desinstalar aplicativo", "Informações do aplicativo")
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(name)
            .setItems(options) { _, choice ->
                when {
                    choice == 0 -> appPlacementMenu(app)
                    !systemApp && choice == 1 -> {
                        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
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
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(app.loadLabel(packageManager).toString())
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
        val name = normalize(app.loadLabel(packageManager).toString())
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
        val root = buildRoot()
        backgroundImage.setImageResource(R.drawable.wallpaper_neon)
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
        header.addView(tabsScroll, LinearLayout.LayoutParams(0, dp(55), 1f))
        header.addView(TextView(this).apply {
            text = "⚙"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setOnClickListener { settingsMenu() }
        }, LinearLayout.LayoutParams(dp(48), dp(52)))
        root.addView(header)

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
        main.addView(alphabet, LinearLayout.LayoutParams(dp(22), -1))
        root.addView(main, LinearLayout.LayoutParams(-1, 0, 1f))

        fun render() {
            content.removeAllViews()
            val visible = if (selected == "Tudo") apps else apps.filter {
                drawerCategory(it) == selected
            }
            val sorted = visible.sortedBy { normalize(it.loadLabel(packageManager).toString()) }
            val sections = sorted.groupBy {
                normalize(it.loadLabel(packageManager).toString()).firstOrNull()
                    ?.uppercaseChar()?.toString() ?: "#"
            }
            // Grade contínua: sem títulos A/B/C entre os aplicativos.
            sections.forEach { (letter, group) ->
                val section = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    tag = "section_$letter"
                }
                section.addView(appGrid(group, true, 5))
                content.addView(section)
            }
            alphabet.removeAllViews()
            val letters = sections.keys.toList()
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
                            val index = ((event.y.coerceIn(0f, view.height.toFloat() - 1f) /
                                view.height) * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            val target = content.findViewWithTag<View>("section_${letters[index]}")
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
                    textSize = 16f
                    setTextColor(if (selected == category) lilac else Color.WHITE)
                    setPadding(dp(11), dp(10), dp(11), dp(10))
                    setOnClickListener {
                        selected = category
                        render()
                        scroll.scrollTo(0, 0)
                    }
                })
            }
        }
        render()
        root.addView(crystalSearchBar(), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(8), dp(8), dp(8), dp(12))
        })
    }

    private fun appGrid(
        items: List<ResolveInfo>,
        allowFavorite: Boolean,
        columns: Int = horizontalIconColumns()
    ): GridLayout {
        val grid = GridLayout(this).apply {
            columnCount = columns
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

    override fun dispatchTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (pendingDrawerDrag) return super.dispatchTouchEvent(event)
        if (widgetResizeActive || widgetResizeGesture) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                widgetResizeGesture = true
                val bounds = android.graphics.Rect()
                val editor = widgetEditor
                if (editor == null || !editor.getGlobalVisibleRect(bounds) ||
                    !bounds.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    editor?.endEditing()
                }
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL)
                widgetResizeGesture = false
            return super.dispatchTouchEvent(event)
        }
        if (!drawerOpen && !dragging) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    val iconScroll = if (::pages.isInitialized) pages.getChildAt(pageIndex)
                        ?.findViewWithTag<ScrollView>("home_icons_scroll") else null
                    val bounds = android.graphics.Rect()
                    iconViewportGesture = iconScroll != null && iconScroll.getGlobalVisibleRect(bounds) &&
                        bounds.contains(event.rawX.toInt(), event.rawY.toInt()) &&
                        (iconScroll.canScrollVertically(1) || iconScroll.canScrollVertically(-1))
                }

                MotionEvent.ACTION_MOVE -> {
                    // Intercepta o gesto horizontal antes que o ScrollView vertical o consuma.
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (!downX.isNaN() && abs(dx) > dp(85) &&
                        abs(dx) > abs(dy) * 1.6f) {
                        downX = Float.NaN // apenas uma troca por gesto
                        changePage(pageIndex + if (dx < 0) 1 else -1)
                        return true
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (downX.isNaN()) {
                        downX = event.x
                        return true
                    }
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dx) < dp(22) && abs(dy) < dp(22)) {
                        val now = android.os.SystemClock.uptimeMillis()
                        val doubleAction = setting("double_tap", "none")
                        if (doubleAction != "none" &&
                            now - lastTapAt in 50..350 &&
                            abs(event.x - lastTapX) < dp(48) &&
                            abs(event.y - lastTapY) < dp(48)) {
                            lastTapAt = 0L
                            if (runGesture(doubleAction)) return true
                        }
                        lastTapAt = now
                        lastTapX = event.x
                        lastTapY = event.y
                    }

                    if (
                        abs(dx) > dp(65) &&
                        abs(dx) > abs(dy) * 1.3f
                    ) {
                        changePage(pageIndex + if (dx < 0) 1 else -1)
                        return true
                    }

                    // A vertical scroll begun in the bounded icon viewport belongs to it.
                    // Horizontal page gestures and bottom-menu gestures remain available elsewhere.
                    if (iconViewportGesture && abs(dy) > abs(dx)) return super.dispatchTouchEvent(event)
                    if (
                        dy < -dp(95) &&
                        abs(dy) > abs(dx) * 1.5f
                    ) {
                        // Deslizar para cima começando na área inferior abre o menu Purple Charm.
                        // Em outras áreas, preserva o gesto configurável (gaveta por padrão).
                        val screenHeight = resources.displayMetrics.heightPixels
                        if (downY >= screenHeight - dp(260)) {
                            showClassicCustomizationMenu()
                            return true
                        }
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
        if (dragging) { cancelWorkspaceDrag(); return }
        if (widgetResizeActive) { widgetEditor?.endEditing(); return }
        if (editMode) {
            leaveEditMode()
            return
        }
        if (drawerOpen || pageIndex != 0) {
            pageIndex = 0
            showHome()
        } else {
            super.onBackPressed()
        }
    }
}
