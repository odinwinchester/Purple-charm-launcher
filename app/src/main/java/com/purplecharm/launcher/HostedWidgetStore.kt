package com.purplecharm.launcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal data class HostedWidget(val id: Int, val provider: String, val page: Int, val span: Int,
    val heightDp: Int, val order: Int)
internal class HostedWidgetStore(private val prefs: SharedPreferences) {
    companion object {
        fun decode(raw: String?): List<HostedWidget> {
            if (raw == null) return emptyList()
            val root = JSONObject(raw); require(root.getInt("version") == 1)
            val values = root.getJSONArray("widgets")
            return (0 until values.length()).map { i -> values.getJSONObject(i).let { o ->
                HostedWidget(o.getInt("id"), o.getString("provider"), o.getInt("page"), o.getInt("span"), o.getInt("height"), o.getInt("order"))
            } }.also { require(valid(it)) }
        }
        fun valid(values: List<HostedWidget>): Boolean = values.size <= 32 && values.map { it.id }.distinct().size == values.size &&
            values.all { it.id > 0 && it.provider.contains('/') && it.page in 0..7 && it.span in 1..6 && it.heightDp in 40..1200 && it.order >= 0 }
        fun encode(values: List<HostedWidget>): String {
            require(valid(values))
            return JSONObject().put("version", 1).put("widgets", JSONArray().apply { values.forEach { w ->
                put(JSONObject().put("id", w.id).put("provider", w.provider).put("page", w.page).put("span", w.span).put("height", w.heightDp).put("order", w.order))
            } }).toString()
        }
    }
    fun read() = decode(prefs.getString("hosted_widgets_v1", null))
    fun save(values: List<HostedWidget>): Boolean {
        read() // Never overwrite an unreadable existing layout.
        if (!valid(values)) return false
        val old = prefs.getString("hosted_widgets_v1", null)
        if (prefs.edit().putString("hosted_widgets_v1", encode(values)).commit()) return true
        val rollback = prefs.edit()
        if (old == null) rollback.remove("hosted_widgets_v1") else rollback.putString("hosted_widgets_v1", old)
        rollback.commit(); return false
    }
}
