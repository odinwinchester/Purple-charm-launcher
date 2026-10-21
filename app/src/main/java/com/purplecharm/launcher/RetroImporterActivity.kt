package com.purplecharm.launcher

import android.app.Activity
import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import java.io.File
import java.util.zip.ZipInputStream

/** Importa somente imagens de um APK escolhido; nunca instala nem executa código do APK. */
class RetroImporterActivity : Activity() {
    private val requestApk = 404
    private lateinit var status: TextView
    private lateinit var gallery: LinearLayout
    private val maxEntry = 12L * 1024 * 1024
    private val maxTotal = 90L * 1024 * 1024
    private val maxImages = 600

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(24, 8, 38))
            setPadding(20, 24, 20, 12)
        }
        fun text(s: String, size: Float): TextView = TextView(this).apply {
            this.text = s; textSize = size; setTextColor(Color.rgb(229, 164, 255))
            setPadding(4, 12, 4, 12)
        }
        root.addView(text("PURPLE CHARM RETRO 0.4", 25f))
        status = text("Selecione o APK original Purple Charm para recuperar imagens.", 16f)
        root.addView(status)
        val pick = Button(this).apply {
            text = "IMPORTAR APK ORIGINAL"
            setOnClickListener {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, requestApk)
            }
        }
        root.addView(pick)
        val scroll = ScrollView(this)
        gallery = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(gallery)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        showSaved()
    }

    @Deprecated("Legacy result callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != requestApk || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        status.text = "Importando imagens..."
        Thread {
            val result = try {
                val dir = File(filesDir, "retro_images").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                var total = 0L
                var count = 0
                contentResolver.openInputStream(uri)?.use { input ->
                    ZipInputStream(input.buffered()).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            val path = entry.name.lowercase(java.util.Locale.ROOT)
                            if (!entry.isDirectory && (path.endsWith(".png") ||
                                path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                                path.endsWith(".webp")) && count < maxImages) {
                                val name = count.toString().padStart(4, '0') + "_" +
                                    entry.name.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
                                val output = File(dir, name)
                                var size = 0L
                                var valid = true
                                output.outputStream().use { out ->
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val n = zip.read(buffer)
                                        if (n < 0) break
                                        size += n; total += n
                                        if (size > maxEntry || total > maxTotal) {
                                            valid = false
                                            break
                                        }
                                        out.write(buffer, 0, n)
                                    }
                                }
                                if (!valid) { output.delete(); break }
                                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                BitmapFactory.decodeFile(output.absolutePath, options)
                                if (options.outWidth > 0 && options.outHeight > 0) count++
                                else output.delete()
                            }
                            zip.closeEntry()
                        }
                    }
                } ?: error("Não foi possível abrir o APK")
                "Importação concluída: $count imagens recuperadas."
            } catch (e: Exception) { "Falha na importação: ${e.message}" }
            runOnUiThread { status.text = result; showSaved() }
        }.start()
    }

    private fun showSaved() {
        gallery.removeAllViews()
        val dir = File(filesDir, "retro_images")
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
        for (file in files) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }
            val image = ImageView(this).apply {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, opts))
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            row.addView(image, LinearLayout.LayoutParams(120, 110))
            val btn = Button(this).apply {
                text = "APLICAR\n${file.name.take(26)}"
                setOnClickListener {
                    try {
                        BitmapFactory.decodeFile(file.absolutePath)?.let {
                            WallpaperManager.getInstance(this@RetroImporterActivity).setBitmap(it)
                            it.recycle()
                            Toast.makeText(this@RetroImporterActivity, "Wallpaper aplicado", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@RetroImporterActivity, "Erro: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
            row.addView(btn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            gallery.addView(row)
        }
    }
}
