package com.purplecharm.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.widget.EditText
import java.util.Locale
import java.util.concurrent.Executors

/** Approximate, foreground-only city tracking. No background service or GPS polling. */
internal class WeatherLocationController(
    private val activity: Activity,
    private val prefs: SharedPreferences,
    private val onChanged: (coordinatesChanged: Boolean) -> Unit
) {
    companion object { const val PERMISSION_REQUEST = 2402 }
    private val manager = activity.getSystemService(Activity.LOCATION_SERVICE) as LocationManager
    private val worker = Executors.newSingleThreadExecutor()
    private var foreground = false
    private var registered = false
    private var generation = 0
    private var fixVersion = 0
    private var needsFirstFix = false
    private var searchDialog: AlertDialog? = null
    private val ui = android.os.Handler(Looper.getMainLooper())
    private var searchVersion = 0
    var status: String = ""
        private set
    val summary: String get() = when (prefs.getString("gothic_weather_location_mode", null)) {
        "auto" -> listOfNotNull(prefs.getString("gothic_weather_city", null), status.takeIf { it.isNotBlank() })
            .joinToString(" • ").ifBlank { "Localização automática" }
        "city" -> prefs.getString("gothic_weather_city", "Cidade selecionada") ?: "Cidade selecionada"
        else -> if (prefs.contains("gothic_weather_lat")) "Local configurado anteriormente" else "Local não definido"
    }
    private fun allowed() = activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun automatic() = prefs.getString("gothic_weather_location_mode", null) == "auto"
    private fun notifyStatus(message: String) { status = message; onChanged(false) }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) { accept(location) }
        override fun onProviderEnabled(provider: String) { if (foreground) restart() }
        override fun onProviderDisabled(provider: String) {
            notifyStatus("Localização indisponível; mantendo o último local")
        }
        @Deprecated("Legacy Android callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    fun start() {
        foreground = true
        if (!automatic() || registered || !prefs.getBoolean("gothic_moon_visible", true)) return
        if (!allowed()) { notifyStatus("Permissão não concedida • busque uma cidade"); return }
        try {
            val network = manager.allProviders.contains(LocationManager.NETWORK_PROVIDER) &&
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            if (!network) {
                notifyStatus("Ative a localização por rede ou busque uma cidade")
                return
            }
            notifyStatus("Buscando localização aproximada")
            manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { accept(it) }
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, WeatherLocationPolicy.INTERVAL_MS,
                WeatherLocationPolicy.MOVEMENT_METERS, listener, Looper.getMainLooper())
            registered = true
        } catch (_: SecurityException) {
            notifyStatus("Permissão indisponível • busque uma cidade")
        } catch (_: IllegalArgumentException) {
            notifyStatus("Localização indisponível • busque uma cidade")
        }
    }
    fun stop() {
        foreground = false
        generation++
        fixVersion++
        searchVersion++
        searchDialog?.dismiss()
        searchDialog = null
        ui.removeCallbacksAndMessages(null)
        try { manager.removeUpdates(listener) } catch (_: SecurityException) { }
        registered = false
    }
    private fun stopTracking() {
        val active = foreground
        stop()
        foreground = active
    }
    private fun restart() { stop(); start() }
    fun close() { stop(); worker.shutdownNow() }

    private fun accept(location: Location) {
        if (!foreground || !automatic() || !allowed()) return
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        if (!location.hasAccuracy() || !WeatherLocationPolicy.usable(location.latitude, location.longitude,
                location.accuracy, ageMs)) return
        val oldLat = prefs.getString("gothic_weather_lat", null)?.toDoubleOrNull()
        val oldLon = prefs.getString("gothic_weather_lon", null)?.toDoubleOrNull()
        val moved = needsFirstFix || WeatherLocationPolicy.moved(oldLat, oldLon, location.latitude, location.longitude)
        needsFirstFix = false
        // A fix confirms the old city too, without invalidating the forecast on location jitter.
        status = "Localização automática"
        if (!moved && prefs.getString("gothic_weather_city", null) != null) { onChanged(false); return }
        if (moved) {
            prefs.edit().putString("gothic_weather_lat", location.latitude.toString())
                .putString("gothic_weather_lon", location.longitude.toString())
                .remove("gothic_weather_city").apply()
            onChanged(true)
        }
        val version = ++fixVersion
        val token = generation
        geocode(reverse = location, query = null) { results, _ ->
            if (token != generation || version != fixVersion || !automatic()) return@geocode
            results.firstOrNull()?.let {
                prefs.edit().putString("gothic_weather_city", cityLabel(it)).apply()
                onChanged(false)
            }
        }
    }

    fun showOptions() {
        AlertDialog.Builder(activity).setTitle("Local da previsão")
            .setMessage("Local atual: $summary\n\nUse localização aproximada enquanto o launcher estiver aberto ou escolha uma cidade. Coordenadas são enviadas ao MET Norway para consultar a previsão.")
            .setPositiveButton("Usar minha localização") { _, _ -> enableAutomatic() }
            .setNeutralButton("Buscar cidade") { _, _ -> searchCity() }
            .setNegativeButton("Mais opções") { _, _ ->
                AlertDialog.Builder(activity).setTitle("Localização do clima")
                    .setItems(arrayOf("Configurações de localização do Android", "Desativar clima")) { _, which ->
                        if (which == 0) activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        else {
                            stopTracking()
                            prefs.edit().putString("gothic_weather_location_mode", "off")
                                .remove("gothic_weather_lat").remove("gothic_weather_lon")
                                .remove("gothic_weather_city").apply()
                            onChanged(true)
                        }
                    }.setNegativeButton("Cancelar", null).show()
            }.show()
    }
    private fun enableAutomatic() {
        stopTracking()
        needsFirstFix = true
        prefs.edit().putString("gothic_weather_location_mode", "auto").apply()
        if (allowed()) start()
        else {
            activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST)
        }
    }
    fun onPermissionResult(requestCode: Int): Boolean {
        if (requestCode != PERMISSION_REQUEST) return false
        if (!automatic()) return true
        if (allowed()) {
            if (foreground) start()
        } else {
            notifyStatus("Permissão não concedida • busque uma cidade")
            AlertDialog.Builder(activity).setTitle("Localização não autorizada")
                .setMessage("Você pode buscar uma cidade sem conceder localização. Para alterar a permissão, abra as configurações do aplicativo.")
                .setPositiveButton("Buscar cidade") { _, _ -> searchCity() }
                .setNeutralButton("Permissões") { _, _ -> activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}"))) }
                .setNegativeButton("Fechar", null).show()
        }
        return true
    }

    private fun searchCity() {
        val input = EditText(activity).apply {
            hint = "Cidade, estado e país"
            setSingleLine(true)
            setText(prefs.getString("gothic_weather_city", ""))
        }
        val dialog = AlertDialog.Builder(activity).setTitle("Buscar cidade")
            .setMessage("Ex.: Campo Grande, MS, Brasil. A busca depende do serviço de geocodificação do Android.")
            .setView(input).setPositiveButton("Buscar", null).setNegativeButton("Cancelar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val query = input.text.toString().trim()
                if (query.length < 2) { input.error = "Informe o nome da cidade"; return@setOnClickListener }
                val token = generation
                val request = ++searchVersion
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                val timeout = Runnable {
                    if (dialog.isShowing && request == searchVersion) {
                        searchVersion++
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        input.error = "A busca demorou. Verifique a conexão e tente novamente."
                    }
                }
                ui.postDelayed(timeout, 20_000L)
                geocode(reverse = null, query = query) { results, error ->
                    if (!dialog.isShowing || token != generation || request != searchVersion) return@geocode
                    ui.removeCallbacks(timeout)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    val candidates = results.filter { it.hasLatitude() && it.hasLongitude() }
                        .distinctBy { "${cityLabel(it)}:${it.latitude}:${it.longitude}" }
                    if (candidates.isEmpty()) {
                        input.error = error ?: "Cidade não encontrada. Inclua estado e país."
                    } else {
                        dialog.dismiss()
                        AlertDialog.Builder(activity).setTitle("Escolha a cidade")
                            .setItems(candidates.map { cityLabel(it) }.toTypedArray()) { _, which ->
                                val city = candidates[which]
                                stopTracking()
                                prefs.edit().putString("gothic_weather_location_mode", "city")
                                    .putString("gothic_weather_city", cityLabel(city))
                                    .putString("gothic_weather_lat", city.latitude.toString())
                                    .putString("gothic_weather_lon", city.longitude.toString()).apply()
                                status = ""
                                onChanged(true)
                            }.setNegativeButton("Cancelar", null).show()
                    }
                }
            }
        }
        searchDialog = dialog
        dialog.setOnDismissListener {
            searchVersion++
            ui.removeCallbacksAndMessages(null)
            if (searchDialog === dialog) searchDialog = null
        }
        dialog.show()
    }

    private fun cityLabel(address: Address): String = listOfNotNull(
        address.locality ?: address.subAdminArea ?: address.featureName,
        address.adminArea, address.countryName).distinct().joinToString(", ")
        .ifBlank { "Cidade selecionada" }

    @Suppress("DEPRECATION")
    private fun geocode(reverse: Location?, query: String?, done: (List<Address>, String?) -> Unit) {
        val token = generation
        fun deliver(results: List<Address>, error: String?) {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed && foreground && token == generation)
                    done(results, error)
            }
        }
        if (!Geocoder.isPresent()) { deliver(emptyList(), "Busca indisponível neste aparelho. Tente a localização automática."); return }
        val coder = Geocoder(activity.applicationContext, Locale.getDefault())
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) { deliver(addresses, null) }
                override fun onError(errorMessage: String?) { deliver(emptyList(), "Não foi possível buscar. Verifique a conexão e tente novamente.") }
            }
            try {
                if (reverse != null) coder.getFromLocation(reverse.latitude, reverse.longitude, 1, callback)
                else coder.getFromLocationName(query!!, 5, callback)
            } catch (_: Exception) { deliver(emptyList(), "Busca indisponível. Tente novamente.") }
        } else {
            worker.execute {
                try {
                    val result = if (reverse != null) coder.getFromLocation(reverse.latitude, reverse.longitude, 1)
                        else coder.getFromLocationName(query!!, 5)
                    deliver(result ?: emptyList(), null)
                } catch (_: Exception) { deliver(emptyList(), "Não foi possível buscar. Verifique a conexão e tente novamente.") }
            }
        }
    }
}
