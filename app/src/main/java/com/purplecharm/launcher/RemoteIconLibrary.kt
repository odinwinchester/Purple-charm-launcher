package com.purplecharm.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Catálogo opcional de ícones remotos. Nunca altera o ícone do aplicativo no Android.
 * A interface deve chamar downloadAndApply somente após consentimento explícito.
 */
internal class RemoteIconLibrary(private val context: Context) {
    data class Entry(val packageName: String, val version: Int, val url: String, val sha256: String)
    private val directory = File(context.filesDir, "purple_charm_icons")
    private val manifestFile = File(directory, "installed.json")
    private val packagePattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")
    private val hashPattern = Regex("[a-fA-F0-9]{64}")
    private val maxIconBytes = 2 * 1024 * 1024
    private val maxCatalogBytes = 512 * 1024

    private fun readUrl(url: String, maxBytes: Int): ByteArray {
        require(url.startsWith("https://")) { "Somente HTTPS" }
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000
            readTimeout = 10000
            instanceFollowRedirects = false
        }
        try {
            require(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val output = java.io.ByteArrayOutputStream()
            connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= maxBytes) { "Arquivo excede limite" }
                    output.write(buffer, 0, count)
                }
            }
            return output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }

    fun fetchCatalog(catalogUrl: String): List<Entry> {
        val json = JSONObject(String(readUrl(catalogUrl, maxCatalogBytes), Charsets.UTF_8))
        val icons = json.getJSONArray("icons")
        require(icons.length() <= 2000) { "Catálogo muito grande" }
        return (0 until icons.length()).map { i ->
            val item = icons.getJSONObject(i)
            Entry(
                item.getString("packageName"),
                item.getInt("version"),
                item.getString("url"),
                item.getString("sha256").lowercase()
            ).also {
                require(packagePattern.matches(it.packageName))
                require(it.version > 0 && it.url.startsWith("https://") && hashPattern.matches(it.sha256))
            }
        }
    }

    private fun fileFor(packageName: String): File {
        require(packagePattern.matches(packageName))
        return File(directory, "$packageName.png")
    }

    fun installedIcon(packageName: String): Drawable? {
        val file = fileFor(packageName)
        if (!file.isFile) return null
        val installed = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return null
        val expected = installed.optString(packageName)
        if (!hashPattern.matches(expected) || sha256(file.readBytes()) != expected) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return BitmapDrawable(context.resources, bitmap)
    }

    @Synchronized
    fun downloadAndApply(entry: Entry): Boolean {
        require(packagePattern.matches(entry.packageName) && hashPattern.matches(entry.sha256))
        directory.mkdirs()
        val bytes = readUrl(entry.url, maxIconBytes)
        require(sha256(bytes).equals(entry.sha256, ignoreCase = true)) { "SHA-256 inválido" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 32..2048 && bounds.outHeight in 32..2048) { "Imagem inválida" }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("PNG inválido")
        require(bitmap.width > 0 && bitmap.height > 0)
        bitmap.recycle()
        val installed = runCatching { JSONObject(manifestFile.readText()) }.getOrDefault(JSONObject())
        val temp = File(directory, "${entry.packageName}.tmp")
        try {
            temp.writeBytes(bytes)
            val target = fileFor(entry.packageName)
            require(temp.renameTo(target)) { "Não foi possível salvar o ícone" }
            installed.put(entry.packageName, entry.sha256.lowercase())
            manifestFile.writeText(installed.toString())
        } finally {
            temp.delete()
        }
        return true
    }

    @Synchronized
    fun restore(packageName: String) {
        fileFor(packageName).delete()
        val installed = runCatching { JSONObject(manifestFile.readText()) }.getOrDefault(JSONObject())
        installed.remove(packageName)
        manifestFile.writeText(installed.toString())
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
