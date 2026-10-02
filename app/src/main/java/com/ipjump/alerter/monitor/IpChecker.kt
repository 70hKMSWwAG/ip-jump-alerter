package com.ipjump.alerter.monitor

import android.content.Context
import com.ipjump.alerter.data.AppDatabase
import com.ipjump.alerter.data.IpChangeRecord
import com.ipjump.alerter.data.Prefs
import com.ipjump.alerter.network.IpLookup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IpChecker {
    const val REASON_PERIODIC = "periodic"
    const val REASON_WIFI = "wifi"
    const val REASON_CELLULAR = "cellular"
    const val REASON_VPN = "vpn"
    const val REASON_NETWORK = "network"
    const val REASON_MANUAL = "manual"

    private val mutex = Mutex()
    private val timeFormat = object : ThreadLocal<Pair<SimpleDateFormat, Date>>() {
        override fun initialValue(): Pair<SimpleDateFormat, Date> {
            return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) to Date()
        }
    }

    suspend fun check(context: Context, reason: String): CheckResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            performCheck(context.applicationContext, reason)
        }
    }

    private suspend fun performCheck(app: Context, reason: String): CheckResult {
        val prefs = Prefs.get(app)
        val network = NetworkSnapshot.capture(app)
        if (!network.connected) {
            return CheckResult(skipped = true, message = "offline", network = network)
        }

        val previous = prefs.lastKnownIp.ifBlank { prefs.baselineIp }
        val refreshLocation = prefs.lastLocation.isBlank() || prefs.lastLocation == "未知"
        val info = IpLookup.fetch(previous, refreshLocation) ?: return CheckResult(
            skipped = true,
            message = "lookup_failed",
            network = network
        )
        val location = resolveLocation(info.locationLabel(), previous, info.ip, prefs.lastLocation)
        val changed = previous.isNotBlank() && previous != info.ip

        if (!changed) {
            prefs.persistObservedIp(info.ip, location, setBaselineIfEmpty = true)
            return CheckResult(
                skipped = false,
                changed = false,
                ip = info.ip,
                location = location,
                network = network
            )
        }

        val shouldAlert = shouldAlert(prefs, reason)
        val now = System.currentTimeMillis()
        AppDatabase.get(app).ipChangeDao().insert(
            IpChangeRecord(
                oldIp = previous,
                newIp = info.ip,
                location = location,
                reason = reason,
                networkType = network.label(),
                changedAt = now
            )
        )
        prefs.persistIpChange(info.ip, location, now)

        if (shouldAlert) {
            AlertNotifier.notifyJump(
                context = app,
                oldIp = previous,
                newIp = info.ip,
                location = location,
                timeText = formatTime(now)
            )
        }
        return CheckResult(
            skipped = false,
            changed = true,
            alerted = shouldAlert,
            ip = info.ip,
            location = location,
            network = network
        )
    }

    private fun resolveLocation(
        lookedUp: String,
        previousIp: String,
        currentIp: String,
        stored: String
    ): String {
        if (lookedUp != "未知") return lookedUp
        if (previousIp == currentIp && stored.isNotBlank()) return stored
        return lookedUp
    }

    private fun shouldAlert(prefs: Prefs, reason: String): Boolean {
        if (!prefs.monitoringEnabled) return false
        if (reason == REASON_MANUAL) return false
        if (prefs.quietHoursEnabled && inQuietHours(prefs.quietStartHour, prefs.quietEndHour)) {
            return false
        }
        if (prefs.ignoreVpn && reason == REASON_VPN) {
            return false
        }
        if (prefs.wifiCellularOnly) {
            return reason == REASON_WIFI || reason == REASON_CELLULAR
        }
        return true
    }

    private fun inQuietHours(start: Int, end: Int): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val s = start.coerceIn(0, 23)
        val e = end.coerceIn(0, 23)
        return if (s == e) {
            false
        } else if (s < e) {
            hour in s until e
        } else {
            hour >= s || hour < e
        }
    }

    fun formatTime(ts: Long): String {
        if (ts <= 0L) return "从未"
        val pair = timeFormat.get()!!
        pair.second.time = ts
        return pair.first.format(pair.second)
    }

    fun reasonLabel(reason: String): String = when (reason) {
        REASON_PERIODIC -> "定时检测"
        REASON_WIFI -> "Wi-Fi 变化"
        REASON_CELLULAR -> "蜂窝网络变化"
        REASON_VPN -> "VPN 变化"
        REASON_MANUAL -> "手动检测"
        else -> "网络切换"
    }
}

data class CheckResult(
    val skipped: Boolean = false,
    val changed: Boolean = false,
    val alerted: Boolean = false,
    val ip: String = "",
    val location: String = "",
    val message: String = "",
    val network: NetworkSnapshot? = null
)
