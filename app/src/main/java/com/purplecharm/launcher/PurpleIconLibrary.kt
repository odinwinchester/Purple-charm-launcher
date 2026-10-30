package com.purplecharm.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Optional online icon collection. No installed-app inventory is uploaded.
 * Catalog is public and cached; only SHA-256 verified PNG files are used.
 * Existing bundled themed icons always take precedence in MainActivity.
 */
internal class PurpleIconLibrary(private val context: Context) {
    private val root = File(context.filesDir, "purple_icon_library").apply { mkdirs() }
    private val worker = Executors.newSingleThreadExecutor()
    private val catalogFile = File(root, "catalog.json")
    private val prefs = context.getSharedPreferences("purple_icon_library", Context.MODE_PRIVATE)
    @Volatile private var catalog = readCatalog()

    private fun readCatalog(): JSONObject = runCatching {
        JSONObject(catalogFile.readText(Charsets.UTF_8))
    }.getOrDefault(JSONObject())

    fun cachedIcon(packageName: String): Drawable? {
        if (!packageName.matches(Regex("[a-zA-Z0-9_.]{3,200}"))) return null
        val entry = synchronized(this) { catalog.optJSONObject(packageName) } ?: return null
        val hash = entry.optString("sha256")
        if (!hash.matches(Regex("[a-f0-9]{64}"))) return null
        val file = File(root, "$hash.png")
        if (!file.isFile || file.length() > MAX_ICON_BYTES) return null
        return runCatching {
            val bytes = file.readBytes()
            if (digest(bytes) != hash) return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            if (bitmap.width !in 1..1024 || bitmap.height !in 1..1024) return null
            BitmapDrawable(context.resources, bitmap)
        }.getOrNull()
    }

    fun refreshIfDue(onChanged: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_attempt", 0L) < 24L * 60 * 60 * 1000) return
        prefs.edit().putLong("last_attempt", now).apply()
        worker.execute {
            try {
                val data = fetch(CATALOG_URL, MAX_CATALOG_BYTES)
                val parsed = JSONObject(String(data, Charsets.UTF_8))
                if (parsed.optInt("schema") != 1) return@execute
                val icons = parsed.optJSONObject("icons") ?: return@execute
                if (icons.length() > 3000) return@execute
                var changed = false
                val accepted = JSONObject()
                val names = icons.keys()
                while (names.hasNext()) {
                    val pkg = names.next()
                    if (!pkg.matches(Regex("[a-zA-Z0-9_.]{3,200}"))) continue
                    val entry = icons.optJSONObject(pkg) ?: continue
                    val hash = entry.optString("sha256")
                    val filename = entry.optString("file")
                    if (!hash.matches(Regex("[a-f0-9]{64}")) ||
                        !filename.matches(Regex("[a-zA-Z0-9_-]{1,100}\\.png"))) continue
                    val file = File(root, "$hash.png")
                    if (!file.exists()) {
                        val bytes = fetch("$ICONS_URL/$filename", MAX_ICON_BYTES)
                        if (digest(bytes) != hash) continue
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                        if (bitmap.width !in 1..1024 || bitmap.height !in 1..1024) continue
                        file.writeBytes(bytes)
                        changed = true
                    }
                    accepted.put(pkg, JSONObject().put("sha256", hash))
                }
                val newCatalog = JSONObject().put("schema", 1).put("icons", accepted)
                // Write the catalog last so no incomplete download becomes visible.
                val temp = File(root, "catalog.tmp")
                temp.writeText(newCatalog.toString())
                if (!temp.renameTo(catalogFile)) {
                    catalogFile.writeText(newCatalog.toString())
                    temp.delete()
                }
                synchronized(this) { catalog = accepted }
                if (changed) context.getMainExecutor().execute(onChanged)
            } catch (_: Exception) {
                // Network errors must never block launcher startup.
            }
        }
    }

    private fun fetch(url: String, max: Int): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "application/json,image/png")
            if (conn.responseCode != 200) throw IllegalStateException("HTTP error")
            if (conn.contentLengthLong > max) throw IllegalStateException("Oversized file")
            return conn.inputStream.use { input ->
                val bytes = input.readNBytes(max + 1)
                if (bytes.size > max) throw IllegalStateException("Oversized file")
                bytes
            }
        } finally { conn.disconnect() }
    }

    private fun digest(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    companion object {
        private const val BASE = "https://raw.githubusercontent.com/odinwinchester/Purple-charm-launcher/main/icon-library"
        private const val CATALOG_URL = "$BASE/catalog.json"
        private const val ICONS_URL = "$BASE/icons"
        private const val MAX_CATALOG_BYTES = 250_000
        private const val MAX_ICON_BYTES = 1_000_000
    }
}
