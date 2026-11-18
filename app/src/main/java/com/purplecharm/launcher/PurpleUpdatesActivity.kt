package com.purplecharm.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.io.File

internal object PurpleUpdateUiState {
    fun canInstall(installedCode: Long, candidateCode: Long, verified: Boolean): Boolean =
        verified && candidateCode > installedCode

    fun message(
        result: String,
        versionName: String?,
        downloaded: Long,
        total: Long,
        verified: Boolean
    ): String = when {
        verified -> "A versão ${versionName ?: ""} foi validada e está pronta para instalar."
        result == "checking" -> "Verificando atualizações oficiais…"
        result == "up-to-date" -> "Você já está usando a versão mais recente."
        result == "available" -> "A versão ${versionName ?: "nova"} foi encontrada. Preparando o download…"
        result == "downloading" -> {
            val percent = if (total > 0L) ((downloaded.coerceAtMost(total) * 100L) / total).toInt() else 0
            "Baixando atualização: $percent%."
        }
        result == "network-error" || result == "download-error" ->
            "A conexão foi interrompida. Confira a rede; o download poderá ser retomado."
        result == "failed" -> "A atualização não passou na validação. Verifique novamente."
        result == "error" -> "Não foi possível verificar atualizações agora."
        else -> "Toque em “Verificar atualizações” para consultar uma versão oficial."
    }
}

internal object PurpleUpdateNotifications {
    private const val CHANNEL_ID = "purple_updates"
    private const val AVAILABLE_ID = 7301
    private const val PROGRESS_ID = 7302
    private const val READY_ID = 7303
    private const val FAILURE_ID = 7304

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Atualizações Purple Charm", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    fun available(context: Context, version: String) =
        show(context, AVAILABLE_ID, "Atualização disponível", "Purple Charm $version foi encontrada.", false)

    fun progress(context: Context, version: String, downloaded: Long, total: Long) {
        if (total <= 0L) return
        val percent = ((downloaded.coerceAtMost(total) * 100L) / total).toInt()
        val notification = builder(context, "Baixando Purple Charm $version")
            .setContentText("$percent% concluído")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(context, PROGRESS_ID, notification)
    }

    fun ready(context: Context, version: String) {
        NotificationManagerCompat.from(context).cancel(PROGRESS_ID)
        show(context, READY_ID, "APK pronto para instalar", "Purple Charm $version foi validado. Toque para revisar e instalar.", false)
    }

    // Routine check/download failures remain visible in the update screen, not the notification bar.
    @Suppress("UNUSED_PARAMETER")
    fun failure(context: Context, message: String) = Unit

    private fun show(context: Context, id: Int, title: String, text: String, ongoing: Boolean) {
        post(context, id, builder(context, title).setContentText(text).setOngoing(ongoing).build())
    }

    private fun builder(context: Context, title: String): NotificationCompat.Builder {
        val intent = Intent(context, PurpleUpdatesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, 7300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    private fun post(context: Context, id: Int, notification: android.app.Notification) {
        createChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

class PurpleUpdatesActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var installed: TextView
    private lateinit var available: TextView
    private lateinit var progress: ProgressBar
    private lateinit var progressLabel: TextView
    private lateinit var installButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var verifiedFileKey: String? = null
    private var verifiedFileValue = false
    private val refreshTask = object : Runnable {
        override fun run() {
            refreshState()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PurpleUpdateNotifications.createChannel(this)
        requestNotificationPermissionIfNeeded()
        buildScreen()
        refreshState()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(refreshTask)
        handler.post(refreshTask)
    }

    override fun onPause() {
        handler.removeCallbacks(refreshTask)
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(refreshTask)
        super.onDestroy()
    }

    private fun buildScreen() {
        window.statusBarColor = Color.rgb(12, 5, 24)
        window.navigationBarColor = Color.rgb(12, 5, 24)

        val root = android.widget.FrameLayout(this).apply {
            setBackgroundColor(Color.rgb(12, 5, 24))
        }
        val artwork = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.purple_update_background)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        root.addView(artwork, android.widget.FrameLayout.LayoutParams(-1, -1))

        // The illustration is decorative; Android renders every status and action live.
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(Color.TRANSPARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(34), dp(20), dp(26))
        }
        val heading = text("✦  Atualização", 27f, true).apply {
            setShadowLayer(dp(5).toFloat(), 0f, dp(2).toFloat(), Color.BLACK)
        }
        content.addView(heading, params(top = 10))

        val infoPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.argb(218, 15, 5, 32))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.rgb(206, 120, 245))
            }
        }
        installed = text("", 16f)
        infoPanel.addView(installed)
        available = text("", 15f)
        infoPanel.addView(available, params(top = 12))
        status = text("", 15f)
        infoPanel.addView(status, params(top = 12))
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(207, 116, 255))
        }
        infoPanel.addView(progress, LinearLayout.LayoutParams(-1, dp(10)).apply {
            topMargin = dp(12)
        })
        progressLabel = text("", 13f)
        infoPanel.addView(progressLabel, params(top = 5))
        content.addView(infoPanel, params(top = 24))

        // Keep the moon and castle visible between the information panel and actions.
        content.addView(android.widget.Space(this), LinearLayout.LayoutParams(1, 0, 1f))

        val check = button("Verificar atualizações").apply {
            tag = "purple_update_check"
            setOnClickListener { requestCheck() }
        }
        content.addView(check, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(24)
        })
        installButton = button("Instalar atualização").apply {
            tag = "purple_update_install"
            visibility = View.GONE
            setOnClickListener { verifyAndInstall() }
        }
        content.addView(installButton, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })
        val close = button("Voltar").apply { setOnClickListener { finish() } }
        content.addView(close, LinearLayout.LayoutParams(-1, dp(52)).apply {
            topMargin = dp(10)
        })
        scroll.addView(content)
        // Keep the last action above the Android navigation area, including edge-to-edge layouts.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val navigationBottom = if (Build.VERSION.SDK_INT >= 30) {
                insets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetBottom
            }
            view.setPadding(0, 0, 0, navigationBottom + dp(16))
            insets
        }
        root.addView(scroll, android.widget.FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        scroll.requestApplyInsets()
    }

    private fun requestCheck() {
        getSharedPreferences("purple_update_state", MODE_PRIVATE).edit()
            .putString("last_result", "checking").apply()
        status.text = PurpleUpdateUiState.message("checking", null, 0L, 0L, false)
        PurpleUpdateManager.schedule(applicationContext)
    }

    private fun refreshState() {
        if (!::status.isInitialized) return
        val prefs = getSharedPreferences("purple_update_state", MODE_PRIVATE)
        val current = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        val currentCode = current?.let {
            if (Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong()
        } ?: 0L
        installed.text = "Instalada: ${current?.versionName ?: "desconhecida"} (code $currentCode)"

        var candidate = readCandidate(prefs)
        if (candidate != null && candidate.versionCode <= currentCode) {
            File(File(cacheDir, "updates"), "purple-${candidate.versionCode}.apk").delete()
            prefs.edit().remove("available_version_code").remove("available_version_name")
                .remove("available_tag").remove("available_url").remove("available_size")
                .remove("available_sha").putString("last_result", "up-to-date").apply()
            candidate = null
        }
        val apk = candidate?.let { File(File(cacheDir, "updates"), "purple-${it.versionCode}.apk") }
        val verificationKey = if (candidate != null && apk != null && apk.isFile)
            "${candidate.sha256}:${apk.length()}:${apk.lastModified()}" else null
        val verified = if (candidate != null && apk != null && verificationKey != null) {
            if (verificationKey == verifiedFileKey) verifiedFileValue
            else PurpleUpdateApkSecurity.verify(this, apk, candidate).also {
                verifiedFileKey = verificationKey
                verifiedFileValue = it
            }
        } else false
        if (candidate != null && apk?.exists() == true && !verified) apk.delete()

        available.text = candidate?.let { "Disponível: ${it.versionName} (code ${it.versionCode})" }
            ?: "Nenhuma atualização validada encontrada."
        val result = prefs.getString("last_result", "") ?: ""
        val bytes = prefs.getLong("downloaded_bytes", 0L)
        val total = candidate?.sizeBytes ?: 0L
        status.text = PurpleUpdateUiState.message(result, candidate?.versionName, bytes, total, verified)

        val downloading = result == "downloading"
        progress.visibility = if (downloading || result == "checking") View.VISIBLE else View.GONE
        progress.isIndeterminate = result == "checking"
        if (downloading && total > 0L) {
            progress.isIndeterminate = false
            progress.progress = ((bytes.coerceAtMost(total) * 100L) / total).toInt()
            progressLabel.text = "${formatBytes(bytes)} de ${formatBytes(total)}"
        } else {
            progressLabel.text = ""
        }
        installButton.visibility = if (PurpleUpdateUiState.canInstall(currentCode, candidate?.versionCode ?: -1L, verified)) View.VISIBLE else View.GONE
    }

    private fun verifyAndInstall() {
        val prefs = getSharedPreferences("purple_update_state", MODE_PRIVATE)
        val candidate = readCandidate(prefs)
        val apk = candidate?.let { File(File(cacheDir, "updates"), "purple-${it.versionCode}.apk") }
        if (candidate == null || apk == null || !PurpleUpdateApkSecurity.verify(this, apk, candidate)) {
            apk?.delete()
            prefs.edit().putString("last_result", "failed").apply()
            Toast.makeText(this, "O APK não passou na validação e não será instalado.", Toast.LENGTH_LONG).show()
            refreshState()
            return
        }
        if (!PurpleUpdateUiState.canInstall(installedVersionCode(), candidate.versionCode, true)) {
            Toast.makeText(this, "A versão baixada não é posterior à versão instalada.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val intent = PurpleUpdateInstaller.createUserConfirmedIntent(this, apk)
            if (intent.action == Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES) {
                AlertDialog.Builder(this)
                    .setTitle("Permitir instalação pelo Android")
                    .setMessage("Ative a permissão para esta instalação. Ao voltar, toque novamente em Instalar atualização; o APK será validado outra vez.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Abrir configurações") { _, _ -> startActivity(intent) }
                    .show()
            } else {
                startActivityForResult(intent, INSTALL_REQUEST)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "O Android não conseguiu abrir o instalador.", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == INSTALL_REQUEST) {
            if (resultCode == Activity.RESULT_CANCELED) {
                val candidateCode = getSharedPreferences("purple_update_state", MODE_PRIVATE)
                    .getLong("available_version_code", -1L)
                val installedCode = installedVersionCode()
                val message = if (candidateCode > 0L && installedCode >= candidateCode)
                    "Atualização instalada pelo Android."
                else "Instalação cancelada. Nenhuma alteração foi feita."
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            refreshState()
        }
    }

    private fun readCandidate(prefs: android.content.SharedPreferences): PurpleUpdateRelease.Candidate? {
        val code = prefs.getLong("available_version_code", -1L)
        val name = prefs.getString("available_version_name", null) ?: return null
        val tag = prefs.getString("available_tag", null) ?: return null
        val url = prefs.getString("available_url", null) ?: return null
        val size = prefs.getLong("available_size", -1L)
        val sha = prefs.getString("available_sha", null) ?: return null
        val tagVersion = Regex("^v([0-9]+(?:\\.[0-9]+){0,2})(?:-stable)?$").matchEntire(tag)
            ?.groupValues?.get(1) ?: return null
        val expectedName = "Purple-Charm-v$tagVersion-OFFICIAL-RELEASE.apk"
        val expectedUrl = "https://github.com/odinwinchester/Purple-charm-launcher/releases/download/$tag/$expectedName"
        if (code !in 1L..Int.MAX_VALUE.toLong() || size !in 1L..PurpleUpdateRelease.MAX_APK_SIZE_BYTES ||
            !name.matches(Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$")) ||
            !sha.matches(Regex("^[0-9a-fA-F]{64}$")) || url != expectedUrl
        ) return null
        return PurpleUpdateRelease.Candidate(code, name, tag, url, size, sha)
    }

    private fun installedVersionCode(): Long = runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrDefault(0L)

    @Suppress("DEPRECATION")
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
    }

    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun button(value: String) = Button(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(105, 52, 148), Color.rgb(65, 35, 112))
        ).apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.argb(200, 226, 180, 255))
        }
    }

    private fun params(top: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun formatBytes(value: Long): String = String.format("%.1f MB", value / (1024f * 1024f))

    companion object {
        private const val INSTALL_REQUEST = 7305
        private const val NOTIFICATION_REQUEST = 7306
    }
}
