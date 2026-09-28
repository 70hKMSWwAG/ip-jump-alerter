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
        val parts = buildList {
            if (hasWifi) add("Wi-Fi")
            if (hasCellular) add("蜂窝网络")
            if (hasEthernet) add("有线")
            if (hasVpn) add("VPN")
        }
        return parts.joinToString(" + ").ifEmpty { "其他网络" }
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
