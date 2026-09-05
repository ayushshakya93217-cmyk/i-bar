package com.statusbar.ios

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.TelephonyManager
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyCallback

/**
 * Pushes real device state into a callback whenever anything changes. Only
 * updates the fields that actually changed values are reused otherwise, so
 * the overlay isn't invalidated/redrawn needlessly (keeps battery drain low).
 */
class SystemStateProvider(private val context: Context) {

    private var current = SystemState()
    var onChanged: ((SystemState) -> Unit)? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else current.batteryPercent
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            update(current.copy(batteryPercent = pct, isCharging = charging))
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            update(current.copy(wifiConnected = wifi, cellularConnected = cellular || current.cellularConnected))
        }
        override fun onLost(network: Network) {
            update(current.copy(wifiConnected = false))
        }
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private var telephonyCallback: Any? = null

    fun start() {
        context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        startTelephonyListener()
        pollWifiLevel()
    }

    fun stop() {
        try { context.unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
        try { connectivityManager.unregisterNetworkCallback(networkCallback) } catch (_: Exception) {}
        stopTelephonyListener()
    }

    private fun pollWifiLevel() {
        try {
            val info = wifiManager.connectionInfo
            val level = WifiManager.calculateSignalLevel(info.rssi, 5) // 0..4
            update(current.copy(wifiLevel = level))
        } catch (_: SecurityException) { /* location perm not granted; keep default */ }
    }

    private fun startTelephonyListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DisplayInfoListener {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    val level = signalStrength.level // 0..4 already
                    update(current.copy(cellularLevel = level, cellularConnected = true))
                }
                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    update(current.copy(networkTypeLabel = networkTypeToLabel(telephonyDisplayInfo)))
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            } catch (_: SecurityException) { /* READ_PHONE_STATE not granted */ }
        }
    }

    private fun stopTelephonyListener() {
        val cb = telephonyCallback as? TelephonyCallback ?: return
        try { telephonyManager.unregisterTelephonyCallback(cb) } catch (_: Exception) {}
    }

    private fun networkTypeToLabel(info: TelephonyDisplayInfo): String {
        return when (info.overrideNetworkType) {
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA_MMWAVE -> "5G"
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA,
            TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "LTE+"
            else -> when (info.networkType) {
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                else -> "4G"
            }
        }
    }

    private fun update(newState: SystemState) {
        if (newState != current) {
            current = newState
            onChanged?.invoke(current)
        }
    }
}
