package com.ipjump.alerter.monitor

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

data class NetworkSnapshot(
    val connected: Boolean,
    val hasWifi: Boolean,
    val hasCellular: Boolean,
    val hasVpn: Boolean,
    val hasEthernet: Boolean
) {
    fun label(): String {
        if (!connected) return "未连接"
        var label = ""
        if (hasWifi) label = "Wi-Fi"
        if (hasCellular) label = if (label.isEmpty()) "蜂窝网络" else "$label + 蜂窝网络"
        if (hasEthernet) label = if (label.isEmpty()) "有线" else "$label + 有线"
        if (hasVpn) label = if (label.isEmpty()) "VPN" else "$label + VPN"
        return label.ifEmpty { "其他网络" }
    }

    companion object {
        private val OFFLINE = NetworkSnapshot(
            connected = false,
            hasWifi = false,
            hasCellular = false,
            hasVpn = false,
            hasEthernet = false
        )

        @Volatile
        private var connectivityManager: ConnectivityManager? = null

        fun capture(context: Context): NetworkSnapshot {
            val cm = connectivityManager ?: (
                context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                ).also { connectivityManager = it }
            val caps = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) } ?: return OFFLINE
            return NetworkSnapshot(
                connected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
                hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
                hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
                hasVpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
                hasEthernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )
        }
    }
}
