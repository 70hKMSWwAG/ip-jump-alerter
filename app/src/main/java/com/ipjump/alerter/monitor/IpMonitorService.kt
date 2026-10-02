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
    private var lastCapsKey: Int = Int.MIN_VALUE
    private var lastNotifyIp: String? = null
    private var lastNotifyInterval: Int? = null
    private var loopInterval: Int = 0
    private var foregroundStarted = false
    private lateinit var prefs: Prefs

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onNetworkEvent()
        }

        override fun onLost(network: Network) {
            onNetworkEvent()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val key = capabilityKey(networkCapabilities)
            if (key == lastCapsKey) return
            lastCapsKey = key
            onNetworkEvent()
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        prefs = Prefs.get(this)
        AlertNotifier.ensureChannels(this)
        updateMonitorNotification(prefs.lastKnownIp, prefs.intervalSeconds, force = true)
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (!prefs.monitoringEnabled) {
            stopMonitor()
            return START_NOT_STICKY
        }
        updateMonitorNotification(prefs.lastKnownIp, prefs.intervalSeconds)
        val interval = prefs.intervalSeconds
        if (loopJob?.isActive != true || loopInterval != interval) {
            restartLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        debounceJob?.cancel()
        loopJob?.cancel()
        unregisterNetworkCallback()
        super.onDestroy()
    }

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager?.registerDefaultNetworkCallback(callback)
            } else {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager?.registerNetworkCallback(request, callback)
            }
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
        loopInterval = prefs.intervalSeconds
        loopJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val startedAt = SystemClock.elapsedRealtime()
                if (!prefs.monitoringEnabled) {
                    withContext(Dispatchers.Main.immediate) { stopMonitor() }
                    break
                }
                val result = IpChecker.check(this@IpMonitorService, IpChecker.REASON_PERIODIC)
                val interval = prefs.intervalSeconds
                loopInterval = interval
                val ip = result.ip.ifBlank { prefs.lastKnownIp }
                withContext(Dispatchers.Main.immediate) {
                    updateMonitorNotification(ip, interval)
                }
                val wait = interval * 1000L - (SystemClock.elapsedRealtime() - startedAt)
                delay(wait.coerceAtLeast(500L))
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
        foregroundStarted = false
        stopSelf()
    }

    private fun updateMonitorNotification(ip: String, interval: Int, force: Boolean = false) {
        if (!force && foregroundStarted && ip == lastNotifyIp && interval == lastNotifyInterval) return
        lastNotifyIp = ip
        lastNotifyInterval = interval
        val notification = AlertNotifier.monitorNotification(this, ip, interval)
        if (!foregroundStarted) {
            val started = runCatching {
                startForeground(AlertNotifier.ID_MONITOR, notification)
                true
            }.getOrElse { error ->
                if (error.javaClass.simpleName == "ForegroundServiceStartNotAllowedException") {
                    false
                } else {
                    throw error
                }
            }
            foregroundStarted = started
            return
        }
        AlertNotifier.updateMonitor(this, notification)
    }

    private fun onNetworkEvent() {
        debounceJob?.cancel()
        debounceJob = lifecycleScope.launch(Dispatchers.IO) {
            delay(NETWORK_DEBOUNCE_MS)
            val current = NetworkSnapshot.capture(this@IpMonitorService)
            val previous = lastSnapshot
            if (previous == current) return@launch
            lastSnapshot = current
            if (!prefs.monitoringEnabled) return@launch
            val reason = detectReason(previous, current)
            val result = IpChecker.check(this@IpMonitorService, reason)
            val interval = prefs.intervalSeconds
            val ip = result.ip.ifBlank { prefs.lastKnownIp }
            withContext(Dispatchers.Main.immediate) {
                updateMonitorNotification(ip, interval)
            }
        }
    }

    private fun capabilityKey(caps: NetworkCapabilities): Int {
        var key = 0
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) key = key or 1
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) key = key or 2
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) key = key or 4
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) key = key or 8
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) key = key or 16
        return key
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

        @Volatile
        var running: Boolean = false
            private set
    }
}
