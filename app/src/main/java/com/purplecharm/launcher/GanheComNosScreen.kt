package com.purplecharm.launcher

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.text.InputType
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Tela única de indicações. Os ícones definitivos e links verificados serão
 * adicionados depois. Não atribuir status de parceria sem confirmação.
 */
internal object GanheComNosScreen {
    private const val TTCOIN_REFERRAL_URL = "https://play.google.com/store/apps/details?id=com.ttcoin.network2&referrer=7QdiIalSbJgdBTb2Xbg0zjtJuQT2"
    private const val BIXBERRY_REFERRAL_URL = "https://join.bixberry.com/MORTLA418A"
    private val recommended = listOf("TTcoin", "BixBerry", "UpRock")

    private const val PREFS = "purple_charm_bixberry_admin"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_REMAINING = "remaining"
    private const val MAX_SLOTS = 5

    private fun hashPin(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120000, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        } finally {
            spec.clearPassword()
        }
    }

    private fun pinDialog(activity: Activity, onAuthenticated: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val setup = !prefs.contains(KEY_HASH)
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 16, 36, 0)
        }
        fun field(hintText: String) = EditText(activity).apply {
            hint = hintText
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(android.text.InputFilter.LengthFilter(8))
            container.addView(this)
        }
        val first = field(if (setup) "Crie um PIN (4 a 8 números)" else "PIN do Purple Charm")
        val confirm = if (setup) field("Confirme o PIN") else null
        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (setup) "Proteger vagas da BixBerry" else "Editar vagas da BixBerry")
            .setView(container)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton(if (setup) "Salvar PIN" else "Desbloquear", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = first.text.toString()
                if (setup) {
                    if (!pin.matches(Regex("[0-9]{4,8}")) || pin != confirm?.text.toString()) {
                        Toast.makeText(activity, "Use 4 a 8 números iguais nos dois campos", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
                    val encoded = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
                    prefs.edit().putString(KEY_SALT, encoded).putString(KEY_HASH, hashPin(pin, salt)).apply()
                } else {
                    val saltText = prefs.getString(KEY_SALT, null) ?: return@setOnClickListener
                    val stored = prefs.getString(KEY_HASH, null) ?: return@setOnClickListener
                    val computed = hashPin(pin, android.util.Base64.decode(saltText, android.util.Base64.NO_WRAP))
                    if (!java.security.MessageDigest.isEqual(stored.toByteArray(), computed.toByteArray())) {
                        Toast.makeText(activity, "PIN incorreto", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                }
                dialog.dismiss()
                onAuthenticated()
            }
        }
        dialog.show()
    }

    private fun editSlots(activity: Activity, onChanged: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        pinDialog(activity) {
            val picker = NumberPicker(activity).apply {
                minValue = 0
                maxValue = MAX_SLOTS
                value = prefs.getInt(KEY_REMAINING, MAX_SLOTS).coerceIn(0, MAX_SLOTS)
                wrapSelectorWheel = false
            }
            AlertDialog.Builder(activity)
                .setTitle("Vagas restantes da BixBerry")
                .setMessage("Altere somente após confirmar uma indicação. Abrir o link não reduz vagas.")
                .setView(picker)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salvar") { _, _ ->
                    prefs.edit().putInt(KEY_REMAINING, picker.value).apply()
                    onChanged()
                }
                .show()
        }
    }

    private class ReferralRing(activity: Activity) : View(activity) {
        var remaining: Int = MAX_SLOTS
            set(value) { field = value.coerceIn(0, MAX_SLOTS); invalidate() }
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val density = resources.displayMetrics.density
            paint.strokeWidth = 3.5f * density
            val inset = paint.strokeWidth / 2 + density
            val bounds = RectF(inset, inset, width - inset, height - inset)
            for (i in 0 until MAX_SLOTS) {
                paint.color = if (i < remaining) Color.rgb(188, 111, 255) else Color.rgb(67, 49, 83)
                canvas.drawArc(bounds, -90f + i * 72f + 5f, 62f, false, paint)
            }
        }
    }

    fun show(activity: Activity) {
        val scale = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * scale + 0.5f).toInt()
        fun text(value: String, size: Float, color: Int = Color.WHITE): TextView =
            TextView(activity).apply {
                this.text = value
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }
        fun background(stroke: Boolean = false) = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(39, 20, 65), Color.rgb(13, 12, 28))
        ).apply {
            cornerRadius = dp(20).toFloat()
            if (stroke) setStroke(dp(1), Color.rgb(150, 91, 230))
        }
        val dialog = Dialog(activity)
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(18))
            this.background = background(true)
        }
        root.addView(text("Ganhe com Nós", 22f))
        root.addView(text("Aplicativos e oportunidades escolhidos por você", 12f, Color.LTGRAY))
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(content)
        }
        fun section(title: String, names: List<String>, official: Boolean) {
            val heading = text(title, 17f).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(0, dp(18), 0, dp(10))
            }
            content.addView(heading)
            val rows = (names.size + 2) / 3
            repeat(rows) { rowIndex ->
                val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                repeat(3) { column ->
                    val index = rowIndex * 3 + column
                    val name = names.getOrNull(index)
                    val cell = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(3), dp(8), dp(3), dp(8))
                    }
                    val circle = text(if (official) "＋" else name?.take(1) ?: "", 25f).apply {
                        background = GradientDrawable(
                            GradientDrawable.Orientation.TL_BR,
                            intArrayOf(Color.rgb(55, 106, 202), Color.rgb(105, 37, 174))
                        ).apply {
                            shape = GradientDrawable.OVAL
                            if (official) setStroke(dp(2), Color.rgb(176, 116, 247))
                        }
                    }
                    if (!official && (name == "TTcoin" || name == "BixBerry" || name == "UpRock")) {
                        val icon = ImageView(activity).apply {
                            setImageResource(when (name) {
                                "TTcoin" -> R.drawable.ttcoin_ganhe_com_nos
                                "BixBerry" -> R.drawable.bixberry_ganhe_com_nos
                                else -> R.drawable.uprock_ganhe_com_nos
                            })
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            contentDescription = name
                        }
                        if (name == "BixBerry") {
                            val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                            val frame = FrameLayout(activity)
                            frame.addView(icon, FrameLayout.LayoutParams(dp(54), dp(54), Gravity.CENTER))
                            val ring = ReferralRing(activity).apply {
                                remaining = prefs.getInt(KEY_REMAINING, MAX_SLOTS)
                            }
                            frame.addView(ring, FrameLayout.LayoutParams(dp(70), dp(70), Gravity.CENTER))
                            cell.addView(frame, LinearLayout.LayoutParams(dp(70), dp(70)))
                            cell.setOnLongClickListener {
                                editSlots(activity) {
                                    ring.remaining = prefs.getInt(KEY_REMAINING, MAX_SLOTS)
                                }
                                true
                            }
                        } else {
                            cell.addView(icon, LinearLayout.LayoutParams(dp(62), dp(62)))
                        }
                    } else {
                        cell.addView(circle, LinearLayout.LayoutParams(dp(62), dp(62)))
                    }
                    cell.addView(text(name ?: "Disponível", 11f), LinearLayout.LayoutParams(-1, dp(32)))
                    if (!official && name != null) {
                        cell.setOnClickListener {
                            if (name == "TTcoin") {
                                try {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TTCOIN_REFERRAL_URL)))
                                } catch (error: android.content.ActivityNotFoundException) {
                                    Toast.makeText(activity, "Não foi possível abrir o link da TTcoin", Toast.LENGTH_SHORT).show()
                                }
                            } else if (name == "BixBerry") {
                                try {
                                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BIXBERRY_REFERRAL_URL)))
                                } catch (error: android.content.ActivityNotFoundException) {
                                    Toast.makeText(activity, "Não foi possível abrir o link da BixBerry", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(activity, "Link de ${name} aguardando validação", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    row.addView(cell, LinearLayout.LayoutParams(0, dp(110), 1f))
                }
                content.addView(row)
            }
        }
        section("Parceiros oficiais", List(6) { "Disponível" }, true)
        section("Recomendados", recommended, false)
        content.addView(text("Indicações opcionais. Links serão ativados somente após verificação.", 11f, Color.LTGRAY))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("Fechar", 15f).apply {
            setPadding(dp(8), dp(12), dp(8), dp(8))
            setOnClickListener { dialog.dismiss() }
        })
        dialog.setContentView(root)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout((activity.resources.displayMetrics.widthPixels - dp(24)).coerceAtMost(dp(440)), (activity.resources.displayMetrics.heightPixels * 0.78f).toInt())
        }
    }
}
