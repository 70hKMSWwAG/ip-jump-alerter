package com.ipjump.alerter.monitor

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.ipjump.alerter.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class IpMonitorService : LifecycleService() {
    private var loopJob: Job? = null
    private var debounceJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var callbackRegistered = false
    private var lastSnapshot: NetworkSnapshot? = null
    private var lastNotifyIp: String? = null
    private var lastNotifyInterval: Int? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkEvent()
        }

        override fun onLost(network: Network) {
            onNetworkEvent()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            onNetworkEvent()
        }
    }

    override fun onCreate() {
        super.onCreate()
        AlertNotifier.ensureChannels(this)
        val prefs = Prefs(this)
        updateMonitorNotification(prefs.lastKnownIp, prefs.intervalSeconds, force = true)
        registerNetworkCallback()
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val prefs = Prefs(this)
        if (!prefs.monitoringEnabled) {
            stopMonitor()
            return START_NOT_STICKY
        }
        updateMonitorNotification(prefs.lastKnownIp, prefs.intervalSeconds, force = true)
        restartLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        debounceJob?.cancel()
        loopJob?.cancel()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager?.registerNetworkCallback(request, callback)
            callbackRegistered = true
        }
        lastSnapshot = NetworkSnapshot.capture(this)
    }

    private fun unregisterNetworkCallback() {
        if (!callbackRegistered) return
        runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
        callbackRegistered = false
    }

    private fun restartLoop() {
        loopJob?.cancel()
        loopJob = null
        startLoop()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                val prefs = Prefs(this@IpMonitorService)
                if (!prefs.monitoringEnabled) {
                    withContext(Dispatchers.Main.immediate) { stopMonitor() }
                    break
                }
                val result = IpChecker.check(this@IpMonitorService, IpChecker.REASON_PERIODIC)
                val ip = result.ip.ifBlank { prefs.lastKnownIp }
                withContext(Dispatchers.Main.immediate) {
                    updateMonitorNotification(ip, prefs.intervalSeconds)
                }
                val wait = prefs.intervalSeconds * 1000L - (SystemClock.elapsedRealtime() - startedAt)
                if (wait > 0L) delay(wait)
            }
        }
    }

    private fun stopMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun updateMonitorNotification(ip: String, interval: Int, force: Boolean = false) {
        if (!force && ip == lastNotifyIp && interval == lastNotifyInterval) return
        lastNotifyIp = ip
        lastNotifyInterval = interval
        startForeground(
            AlertNotifier.ID_MONITOR,
            AlertNotifier.monitorNotification(this, ip, interval)
        )
    }

    private fun onNetworkEvent() {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(NETWORK_DEBOUNCE_MS)
            val current = NetworkSnapshot.capture(this@IpMonitorService)
            val previous = lastSnapshot
            if (previous == current) return@launch
            lastSnapshot = current
            if (!Prefs(this@IpMonitorService).monitoringEnabled) return@launch
            val reason = detectReason(previous, current)
            val result = IpChecker.check(this@IpMonitorService, reason)
            val prefs = Prefs(this@IpMonitorService)
            val ip = result.ip.ifBlank { prefs.lastKnownIp }
            withContext(Dispatchers.Main.immediate) {
                updateMonitorNotification(ip, prefs.intervalSeconds)
            }
        }
    }

    private fun detectReason(
        previous: NetworkSnapshot?,
        current: NetworkSnapshot
    ): String {
        if (previous == null) return IpChecker.REASON_NETWORK
        return when {
            previous.hasVpn != current.hasVpn -> IpChecker.REASON_VPN
            previous.hasWifi != current.hasWifi -> IpChecker.REASON_WIFI
            previous.hasCellular != current.hasCellular -> IpChecker.REASON_CELLULAR
            else -> IpChecker.REASON_NETWORK
        }
    }

    companion object {
        private const val NETWORK_DEBOUNCE_MS = 800L
    }
}
