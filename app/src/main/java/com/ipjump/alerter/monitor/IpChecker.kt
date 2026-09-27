package com.ipjump.alerter.monitor

import android.content.Context
import com.ipjump.alerter.data.AppDatabase
import com.ipjump.alerter.data.IpChangeRecord
import com.ipjump.alerter.data.Prefs
import com.ipjump.alerter.network.IpLookup
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IpChecker {
    const val ACTION_STATUS = "com.ipjump.alerter.STATUS"

    suspend fun check(context: Context, reason: String): CheckResult {
        val app = context.applicationContext
        val prefs = Prefs(app)
        val network = NetworkSnapshot.capture(app)
        if (!network.connected) {
            return CheckResult(skipped = true, message = "offline", network = network)
        }

        val info = IpLookup.fetch() ?: return CheckResult(
            skipped = true,
            message = "lookup_failed",
            network = network
        )

        val previous = prefs.lastKnownIp.ifBlank { prefs.baselineIp }
        if (prefs.baselineIp.isBlank()) {
            prefs.baselineIp = info.ip
        }
        prefs.lastKnownIp = info.ip
        prefs.lastLocation = info.locationLabel()

        val changed = previous.isNotBlank() && previous != info.ip
        if (!changed) {
            return CheckResult(
                skipped = false,
                changed = false,
                ip = info.ip,
                location = info.locationLabel(),
                network = network
            )
        }

        val shouldAlert = shouldAlert(prefs, reason)
        val now = System.currentTimeMillis()
        val record = IpChangeRecord(
            oldIp = previous,
            newIp = info.ip,
            location = info.locationLabel(),
            reason = reason,
            networkType = network.label(),
            changedAt = now
        )
        AppDatabase.get(app).ipChangeDao().insert(record)
        prefs.lastChangeAt = now
        prefs.baselineIp = info.ip

        if (shouldAlert) {
            AlertNotifier.notifyJump(
                context = app,
                oldIp = previous,
                newIp = info.ip,
                location = info.locationLabel(),
                timeText = formatTime(now)
            )
        }
        return CheckResult(
            skipped = false,
            changed = true,
            alerted = shouldAlert,
            ip = info.ip,
            location = info.locationLabel(),
            network = network
        )
    }

    private fun shouldAlert(prefs: Prefs, reason: String): Boolean {
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
        return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
    }

    fun formatTime(ts: Long): String {
        if (ts <= 0L) return "从未"
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(ts))
    }

    const val REASON_PERIODIC = "periodic"
    const val REASON_WIFI = "wifi"
    const val REASON_CELLULAR = "cellular"
    const val REASON_VPN = "vpn"
    const val REASON_NETWORK = "network"

    fun reasonLabel(reason: String): String = when (reason) {
        REASON_PERIODIC -> "定时检测"
        REASON_WIFI -> "Wi-Fi 变化"
        REASON_CELLULAR -> "蜂窝网络变化"
        REASON_VPN -> "VPN 变化"
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
