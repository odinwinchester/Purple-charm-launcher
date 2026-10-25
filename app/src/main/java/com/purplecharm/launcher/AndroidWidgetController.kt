package com.purplecharm.launcher

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import kotlin.math.ceil

/** Official host; authorization/configuration belong to Android/provider, not to the launcher. */
internal class AndroidWidgetController(private val activity: Activity, private val prefs: SharedPreferences,
    private val pageCount: () -> Int, private val columns: () -> Int, private val changed: () -> Unit, private val added: (Int) -> Unit = { changed() }) {
    companion object { const val HOST_ID = 701; const val BIND_REQUEST = 7310; const val CONFIG_REQUEST = 7311 }
    private val manager = AppWidgetManager.getInstance(activity)
    private val host = AppWidgetHost(activity, HOST_ID)
    private val store = HostedWidgetStore(prefs)
    private val views = mutableMapOf<Int, AppWidgetHostView>()
    private val sizedViews = mutableSetOf<Int>()
    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
    private fun message(value: String) = Toast.makeText(activity, value, Toast.LENGTH_LONG).show()
    private fun values(): List<HostedWidget>? = runCatching { store.read() }.getOrElse { message("Organização de widgets ilegível; dados preservados"); null }
    private fun save(list: List<HostedWidget>): Boolean = runCatching { store.save(list) }.getOrDefault(false).also { if (it) changed() else message("Não foi possível salvar; organização preservada") }
    fun start() { runCatching { host.startListening() }; cleanup() }
    fun stop() { runCatching { host.stopListening() } }
    fun close() { stop(); views.clear(); sizedViews.clear() }
    fun hasWidgets(page: Int) = values()?.any { it.page.coerceAtMost(pageCount() - 1) == page } == true
    private fun providerLabel(info: AppWidgetProviderInfo): String {
        val origin = runCatching { activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(info.provider.packageName, 0)).toString() }.getOrDefault(info.provider.packageName)
        return "${info.loadLabel(activity.packageManager)} • $origin"
    }
    fun choose() {
        if (values() == null) return
        if (prefs.getInt("widget_pending_id", 0) != 0) { pendingOptions(); return }
        val providers = runCatching { manager.installedProviders }.getOrDefault(emptyList()).sortedBy { providerLabel(it).lowercase() }
        if (providers.isEmpty()) { message("Nenhum widget Android disponível neste perfil"); return }
        SmokeDialogBuilder(activity).setTitle("Widgets Android • aplicativo de origem")
            .setItems(providers.map { providerLabel(it) }.toTypedArray()) { _, i -> add(providers[i]) }
            .setNegativeButton("Cancelar", null).show()
    }
    internal fun add(info: AppWidgetProviderInfo) {
        val existing = values() ?: return
        if (existing.size >= 32 || prefs.getInt("widget_pending_id", 0) != 0) { message("Conclua o widget pendente ou remova um widget"); return }
        val available = (activity.resources.displayMetrics.widthPixels - dp(36)).coerceAtLeast(1)
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        if (info.minWidth + padding.left + padding.right > available - dp(8) || ceil(info.minHeight / activity.resources.displayMetrics.density).toInt() > 1200) {
            message("Este widget exige área maior que a suportada nesta tela"); return
        }
        val id = runCatching { host.allocateAppWidgetId() }.getOrElse { message("O Android não permitiu criar widget"); return }
        if (id <= 0) { message("ID de widget inválido"); return }
        if (!prefs.edit().putInt("widget_pending_id", id).putString("widget_pending_provider", info.provider.flattenToString()).putString("widget_pending_stage", "bind").commit()) {
            runCatching { host.deleteAppWidgetId(id) }; return
        }
        bindPending()
    }
    private fun bindPending() {
        val id = prefs.getInt("widget_pending_id", 0)
        val provider = ComponentName.unflattenFromString(prefs.getString("widget_pending_provider", "") ?: "") ?: return cancelPending()
        val bound = runCatching { manager.bindAppWidgetIdIfAllowed(id, provider, Bundle().apply { putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) }) }.getOrDefault(false)
        if (bound || runCatching { manager.getAppWidgetInfo(id) }.getOrNull() != null) configurePending()
        else try {
            activity.startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider), BIND_REQUEST)
        } catch (_: Exception) { cancelPending(); message("Autorização de widgets indisponível neste aparelho") }
    }
    private fun configurePending() {
        val id = prefs.getInt("widget_pending_id", 0)
        val info = runCatching { manager.getAppWidgetInfo(id) }.getOrNull() ?: return cancelPending()
        val expected = prefs.getString("widget_pending_provider", null)
        if (info.provider.flattenToString() != expected) { cancelPending(); return }
        if (info.configure == null) finishPending()
        else {
            prefs.edit().putString("widget_pending_stage", "configure").commit()
            try { host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIG_REQUEST, null) }
            catch (_: Exception) { cancelPending(); message("Não foi possível configurar o widget") }
        }
    }
    fun onResult(request: Int, result: Int, data: Intent?): Boolean {
        if (request != BIND_REQUEST && request != CONFIG_REQUEST) return false
        val id = prefs.getInt("widget_pending_id", 0)
        if (id <= 0) return true
        val expectedStage = if (request == BIND_REQUEST) "bind" else "configure"
        if (prefs.getString("widget_pending_stage", null) != expectedStage) return true
        val returned = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) ?: id
        if (result != Activity.RESULT_OK || returned != id) { cancelPending(); return true }
        if (request == BIND_REQUEST) configurePending() else finishPending()
        return true
    }
    private fun finishPending() {
        val list = values() ?: return
        val id = prefs.getInt("widget_pending_id", 0)
        val info = runCatching { manager.getAppWidgetInfo(id) }.getOrNull() ?: return cancelPending()
        if (info.provider.flattenToString() != prefs.getString("widget_pending_provider", null)) { cancelPending(); return }
        if (list.any { it.id == id }) { clearPending(); return }
        val density = activity.resources.displayMetrics.density
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        val height = ceil((info.minHeight + padding.top + padding.bottom) / density).toInt().coerceIn(80, 1200)
        val weatherPage = prefs.getInt("weather_widget_page", 0)
        val target = (0 until pageCount()).firstOrNull { it != weatherPage } ?: 0
        val availableWidth = activity.resources.displayMetrics.widthPixels - dp(36)
        val span = ceil((info.minWidth + padding.left + padding.right + dp(8)).toDouble() * columns() / availableWidth).toInt().coerceIn(1, columns())
        val item = HostedWidget(id, info.provider.flattenToString(), target, span, height, list.count { it.page == target })
        // Persist the instance and clear pending state atomically before notifying UI.
        val old = prefs.getString("hosted_widgets_v1", null)
        val stage = prefs.getString("widget_pending_stage", "bind")
        if (!prefs.edit().putString("hosted_widgets_v1", HostedWidgetStore.encode(list + item))
                .remove("widget_pending_id").remove("widget_pending_provider").remove("widget_pending_stage").commit()) {
            val edit = prefs.edit().putInt("widget_pending_id", id).putString("widget_pending_provider", info.provider.flattenToString()).putString("widget_pending_stage", stage); if (old == null) edit.remove("hosted_widgets_v1") else edit.putString("hosted_widgets_v1", old)
            edit.commit(); message("Widget não salvo; tente continuar pela central"); return
        }
        added(target)
    }
    private fun clearPending(): Boolean = prefs.edit().remove("widget_pending_id").remove("widget_pending_provider").remove("widget_pending_stage").commit()
    internal fun cancelPending() {
        val id = prefs.getInt("widget_pending_id", 0)
        val list = values() ?: return
        if (!clearPending()) { message("Não foi possível cancelar; widget pendente preservado"); return }
        if (id > 0 && list.none { it.id == id }) deleteId(id)
    }
    private fun deleteId(id: Int) {
        views.remove(id); sizedViews.remove(id)
        if (runCatching { host.deleteAppWidgetId(id) }.isFailure) {
            val old = prefs.getStringSet("widget_cleanup_ids", emptySet())!!.toMutableSet()
            old.add(id.toString()); prefs.edit().putStringSet("widget_cleanup_ids", old).commit()
        }
    }
    private fun cleanup() {
        val used = values()?.map { it.id }?.toSet() ?: return
        val pending = prefs.getInt("widget_pending_id", 0)
        val left = prefs.getStringSet("widget_cleanup_ids", emptySet())!!.filter { raw ->
            val id = raw.toIntOrNull() ?: return@filter false
            id in used || id == pending || runCatching { host.deleteAppWidgetId(id) }.isFailure
        }.toSet()
        if (left != prefs.getStringSet("widget_cleanup_ids", emptySet())) prefs.edit().putStringSet("widget_cleanup_ids", left).apply()
    }
    private fun pendingOptions() {
        SmokeDialogBuilder(activity).setTitle("Widget Android pendente")
            .setItems(arrayOf("Continuar autorização/configuração", "Cancelar e liberar ID")) { _, i ->
                if (i == 1) cancelPending() else if (prefs.getString("widget_pending_stage", "bind") == "configure") configurePending() else bindPending()
            }.show()
    }
    fun manage() {
        val list = values() ?: return
        if (prefs.getInt("widget_pending_id", 0) > 0) { pendingOptions(); return }
        if (list.isEmpty()) { message("Nenhum widget Android adicionado"); return }
        SmokeDialogBuilder(activity).setTitle("Widgets adicionados")
            .setItems(list.map { "${it.provider.substringBefore('/')} • Página ${it.page + 1}" }.toTypedArray()) { _, i -> options(list[i].id) }.show()
    }
    private fun options(id: Int) {
        val widget = values()?.firstOrNull { it.id == id } ?: return
        SmokeDialogBuilder(activity).setTitle("Widget Android • opções")
            .setItems(arrayOf("Mover para página", "Mover antes", "Mover depois", "Redimensionar", "Remover widget")) { _, action ->
                val list = values() ?: return@setItems
                when (action) {
                    0 -> SmokeDialogBuilder(activity).setTitle("Página")
                        .setItems((1..pageCount()).map { "Página $it" }.toTypedArray()) { _, p ->
                            if (p == prefs.getInt("weather_widget_page", 0) && prefs.getBoolean("gothic_moon_visible", true)) message("Escolha outra página: o clima usa esta área completa")
                            else save(list.map { if (it.id == id) it.copy(page = p, order = list.count { w -> w.page == p }) else it })
                        }.show()
                    1, 2 -> {
                        val ordered = list.filter { it.page == widget.page }.sortedBy { it.order }.toMutableList()
                        val index = ordered.indexOfFirst { it.id == id }; val target = index + if (action == 1) -1 else 1
                        if (target in ordered.indices) {
                            ordered.add(target, ordered.removeAt(index))
                            val updated = ordered.mapIndexed { n, w -> w.copy(order = n) }.associateBy { it.id }
                            save(list.map { updated[it.id] ?: it })
                        }
                    }
                    3 -> resize(widget)
                    4 -> if (save(list.filterNot { it.id == id })) deleteId(id)
                }
            }.show()
    }
    private fun resize(widget: HostedWidget) {
        val info = runCatching { manager.getAppWidgetInfo(widget.id) }.getOrNull() ?: return message("Aplicativo de origem indisponível")
        val horizontal = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val vertical = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        if (!horizontal && !vertical) { message("Este aplicativo não permite redimensionar o widget"); return }
        val density = activity.resources.displayMetrics.density
        val availableDp = (activity.resources.displayMetrics.widthPixels - dp(36)) / density
        val padding = AppWidgetHostView.getDefaultPaddingForWidget(activity, info.provider, null)
        val minW = ((if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) + padding.left + padding.right + dp(8)) / density
        val minH = ceil(((if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) + padding.top + padding.bottom) / density).toInt().coerceAtLeast(40)
        val maxW = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeWidth > 0) info.maxResizeWidth / density else availableDp
        val maxH = if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) (info.maxResizeHeight / density).toInt().coerceAtMost(1200) else 1200
        val options = mutableListOf<Pair<String, HostedWidget>>()
        if (horizontal) for (span in 1..columns()) {
            val width = availableDp * span / columns()
            if (width >= minW && width <= maxW) options.add("Largura: $span colunas" to widget.copy(span = span))
        }
        if (vertical) for (height in listOf(minH, widget.heightDp - 40, widget.heightDp + 40, widget.heightDp + 80).distinct())
            if (height in minH..maxH) options.add("Altura: $height dp" to widget.copy(heightDp = height))
        if (options.isEmpty()) { message("Nenhum tamanho adicional compatível nesta tela"); return }
        SmokeDialogBuilder(activity).setTitle("Tamanhos permitidos pelo aplicativo")
            .setItems(options.map { it.first }.toTypedArray()) { _, i -> values()?.let { list -> save(list.map { if (it.id == widget.id) options[i].second else it }) } }.show()
    }
    fun pageView(page: Int): View? {
        val list = values()?.filter { it.page.coerceAtMost(pageCount() - 1) == page }?.sortedBy { it.order } ?: return null
        if (list.isEmpty()) return null
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        for (w in list) {
            val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = SmokeUi.panel(activity); setPadding(dp(4), dp(4), dp(4), dp(4)) }
            box.addView(TextView(activity).apply {
                text = "${w.provider.substringBefore('/')}  ⋮"; textSize = 12f; setTextColor(android.graphics.Color.WHITE)
                setPadding(dp(8), dp(6), dp(8), dp(6)); setOnClickListener { options(w.id) }
            }, LinearLayout.LayoutParams(-1, -2))
            val info = runCatching { manager.getAppWidgetInfo(w.id) }.getOrNull()
            if (info != null && info.provider.flattenToString() == w.provider) {
                val view = runCatching { views.getOrPut(w.id) { host.createView(activity, w.id, info) } }.getOrNull()
                if (view != null) {
                    (view.parent as? ViewGroup)?.removeView(view)
                    box.addView(view, LinearLayout.LayoutParams(-1, dp(w.heightDp)))
                    if (sizedViews.add(w.id)) view.addOnLayoutChangeListener { _, l, t, r, b, oldL, oldT, oldR, oldB ->
                        if (r - l != oldR - oldL || b - t != oldB - oldT) {
                            val density = activity.resources.displayMetrics.density
                            val width = ((r - l) / density).toInt(); val height = ((b - t) / density).toInt()
                            if (width > 0 && height > 0) runCatching { view.updateAppWidgetSize(null, width, height, width, height) }
                        }
                    }
                }
            } else box.addView(TextView(activity).apply { text = "Aplicativo de origem indisponível • dados preservados"; setTextColor(android.graphics.Color.WHITE) })
            content.addView(box, LinearLayout.LayoutParams(((activity.resources.displayMetrics.widthPixels - dp(36)) * w.span.coerceAtMost(columns()) / columns()).coerceAtLeast(dp(48)), -2).apply { gravity = Gravity.CENTER_HORIZONTAL; setMargins(0, dp(3), 0, dp(3)) })
        }
        return ScrollView(activity).apply { tag = "external_widgets_scroll"; addView(content); isFillViewport = false }
    }
    fun combineWithWeather(weather: View, external: View): View {
        // A scrollable viewport, never layers over icons/dock; each widget keeps its complete height.
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(weather, LinearLayout.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.4f).toInt().coerceAtLeast(dp(180))))
        content.addView(external, LinearLayout.LayoutParams(-1, dp(320)))
        return ScrollView(activity).apply { addView(content) }
    }
}
