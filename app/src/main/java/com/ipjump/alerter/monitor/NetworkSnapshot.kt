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
        val parts = mutableListOf<String>()
        if (hasWifi) parts += "Wi-Fi"
        if (hasCellular) parts += "蜂窝网络"
        if (hasEthernet) parts += "有线"
        if (hasVpn) parts += "VPN"
        return if (parts.isEmpty()) "其他网络" else parts.joinToString(" + ")
    }

    fun primaryType(): String {
        return when {
            !connected -> "none"
            hasVpn -> "vpn"
            hasWifi -> "wifi"
            hasCellular -> "cellular"
            hasEthernet -> "ethernet"
            else -> "other"
        }
    }

    companion object {
        fun capture(context: Context): NetworkSnapshot {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val caps = network?.let { cm.getNetworkCapabilities(it) }
            if (caps == null) {
                return NetworkSnapshot(
                    connected = false,
                    hasWifi = false,
                    hasCellular = false,
                    hasVpn = false,
                    hasEthernet = false
                )
            }
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
