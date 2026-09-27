package com.ipjump.alerter.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.IBinder
import com.ipjump.alerter.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IpMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loopJob: Job? = null
    private var connectivityManager: ConnectivityManager? = null
    private var lastWifi = false
    private var lastCellular = false
    private var lastVpn = false
    private var callbackRegistered = false

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
        startForeground(
            AlertNotifier.ID_MONITOR,
            AlertNotifier.monitorNotification(this, prefs.lastKnownIp, prefs.intervalSeconds)
        )
        registerNetworkCallback()
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = Prefs(this)
        if (!prefs.monitoringEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(
            AlertNotifier.ID_MONITOR,
            AlertNotifier.monitorNotification(this, prefs.lastKnownIp, prefs.intervalSeconds)
        )
        restartLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        loopJob?.cancel()
        if (callbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(callback) }
            callbackRegistered = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            connectivityManager?.registerNetworkCallback(request, callback)
            callbackRegistered = true
        }
        val snap = NetworkSnapshot.capture(this)
        lastWifi = snap.hasWifi
        lastCellular = snap.hasCellular
        lastVpn = snap.hasVpn
    }

    private fun restartLoop() {
        loopJob?.cancel()
        startLoop()
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                val prefs = Prefs(this@IpMonitorService)
                if (!prefs.monitoringEnabled) {
                    stopSelf()
                    break
                }
                val result = IpChecker.check(this@IpMonitorService, IpChecker.REASON_PERIODIC)
                val ip = result.ip.ifBlank { prefs.lastKnownIp }
                startForeground(
                    AlertNotifier.ID_MONITOR,
                    AlertNotifier.monitorNotification(
                        this@IpMonitorService,
                        ip,
                        prefs.intervalSeconds
                    )
                )
                delay(prefs.intervalSeconds * 1000L)
            }
        }
    }

    private fun onNetworkEvent() {
        scope.launch {
            delay(800)
            val snap = NetworkSnapshot.capture(this@IpMonitorService)
            val reason = when {
                snap.hasVpn != lastVpn -> IpChecker.REASON_VPN
                snap.hasWifi != lastWifi -> IpChecker.REASON_WIFI
                snap.hasCellular != lastCellular -> IpChecker.REASON_CELLULAR
                else -> IpChecker.REASON_NETWORK
            }
            lastWifi = snap.hasWifi
            lastCellular = snap.hasCellular
            lastVpn = snap.hasVpn
            if (Prefs(this@IpMonitorService).monitoringEnabled) {
                IpChecker.check(this@IpMonitorService, reason)
            }
        }
    }
}
