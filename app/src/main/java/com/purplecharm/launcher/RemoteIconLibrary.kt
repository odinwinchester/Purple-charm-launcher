package com.purplecharm.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import org.json.JSONObject
import org.json.JSONArray
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
    private companion object {
        // 16 normalized 256px bitmaps: approximately 4 MiB, keyed by verified content.
        val normalizedCache = android.util.LruCache<String, Drawable.ConstantState>(16)
    }
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

    // A lista pode ser consultada offline; os PNGs continuam independentes do catálogo.
    private val catalogCacheFile get() = File(directory, "catalog-cache.json")
    private val catalogPrefs get() = context.getSharedPreferences("purple_icon_catalog", Context.MODE_PRIVATE)
    // Published icons are validated and cataloged by the dedicated icon publisher branch.
    // The launcher reads updates remotely; a new APK is not needed for each icon.
    private val catalogUrl = "https://raw.githubusercontent.com/odinwinchester/Purple-charm-launcher/icon-library-publishing/icon-library/catalog.json"

    @Synchronized
    fun cachedCatalog(): List<Entry> = runCatching {
        if (!catalogCacheFile.isFile) return@runCatching emptyList()
        parseCatalog(catalogCacheFile.readText(Charsets.UTF_8))
    }.getOrDefault(emptyList())

    private fun parseCatalog(jsonText: String): List<Entry> {
        val json = JSONObject(jsonText)
        val icons = json.getJSONArray("icons")
        require(icons.length() <= 2000)
        return (0 until icons.length()).map { i ->
            val item = icons.getJSONObject(i)
            Entry(item.getString("packageName"), item.getInt("version"),
                item.getString("url"), item.getString("sha256").lowercase()).also {
                require(packagePattern.matches(it.packageName) && it.version > 0 &&
                    it.url.startsWith("https://") && hashPattern.matches(it.sha256))
            }
        }
    }

    // Chamado somente de thread de trabalho, nunca da thread da interface.
    @Synchronized
    fun fetchCatalogWithCache(): List<Entry> = runCatching {
        val bytes = readUrl(catalogUrl, maxCatalogBytes)
        val jsonText = String(bytes, Charsets.UTF_8)
        val entries = parseCatalog(jsonText)
        directory.mkdirs()
        val temp = File(directory, "catalog-cache.tmp")
        temp.writeText(jsonText, Charsets.UTF_8)
        if (!temp.renameTo(catalogCacheFile)) {
            temp.delete()
            error("Não foi possível salvar o catálogo")
        }
        entries
    }.getOrElse { cachedCatalog() }

    // Na abertura do launcher, no máximo uma consulta a cada seis horas.
    // Não baixa nem aplica PNGs; apenas atualiza a lista local.
    fun refreshCatalogIfDue() {
        val now = System.currentTimeMillis()
        val last = catalogPrefs.getLong("last_check", 0L)
        if (now >= last && now - last < 6L * 60 * 60 * 1000) return
        catalogPrefs.edit().putLong("last_check", now).apply()
        Thread {
            runCatching { fetchCatalogWithCache() }
        }.start()
    }

    private fun fileFor(packageName: String): File {
        require(packagePattern.matches(packageName))
        return File(directory, "$packageName.png")
    }

    fun installedIcon(packageName: String): Drawable? {
        // Android launchers and Robolectric tests can expose synthetic/invalid
        // package identifiers. They must retain their normal system icon.
        if (!packagePattern.matches(packageName)) return null
        val file = fileFor(packageName)
        if (!file.isFile) return null
        val installed = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return null
        val expected = installed.optString(packageName)
        if (!hashPattern.matches(expected) || sha256(file.readBytes()) != expected) return null
        val cacheKey = "$packageName:$expected"
        normalizedCache.get(cacheKey)?.let { return it.newDrawable(context.resources).mutate() }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return IconVisualNormalizer.normalize(context.resources, BitmapDrawable(context.resources, bitmap)).also {
            it.constantState?.let { state -> normalizedCache.put(cacheKey, state) }
        }
    }

    /** Compara o hash do catálogo sem decodificar o PNG na interface. */
    fun isCurrent(entry: Entry): Boolean {
        if (!packagePattern.matches(entry.packageName) || !hashPattern.matches(entry.sha256)) return false
        val installed = runCatching { JSONObject(manifestFile.readText()) }.getOrNull() ?: return false
        if (!installed.optString(entry.packageName).equals(entry.sha256, ignoreCase = true)) return false
        return fileFor(entry.packageName).isFile
    }

    /** Prévia limitada a 192 px, com verificação SHA-256; não instala a arte. */
    fun previewBitmap(entry: Entry): Bitmap {
        require(packagePattern.matches(entry.packageName) && hashPattern.matches(entry.sha256))
        val bytes = readUrl(entry.url, maxIconBytes)
        require(sha256(bytes).equals(entry.sha256, ignoreCase = true)) { "SHA-256 inválido" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth in 32..2048 && bounds.outHeight in 32..2048) { "Imagem inválida" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 192) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("PNG inválido")
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
