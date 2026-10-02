package com.ipjump.alerter.data

import android.content.Context
import android.content.SharedPreferences

class Prefs private constructor(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    @Volatile private var cachedDarkMode: Boolean? = null
    @Volatile private var cachedMonitoring: Boolean? = null
    @Volatile private var cachedBaseline: String? = null
    @Volatile private var cachedLastIp: String? = null
    @Volatile private var cachedLastLocation: String? = null
    @Volatile private var cachedLastChange: Long? = null
    @Volatile private var cachedInterval: Int? = null
    @Volatile private var cachedIgnoreVpn: Boolean? = null
    @Volatile private var cachedWifiCellularOnly: Boolean? = null
    @Volatile private var cachedQuietEnabled: Boolean? = null
    @Volatile private var cachedQuietStart: Int? = null
    @Volatile private var cachedQuietEnd: Int? = null

    var darkMode: Boolean
        get() = cachedDarkMode ?: sp.getBoolean(KEY_DARK_MODE, false).also { cachedDarkMode = it }
        set(value) {
            if (cachedDarkMode == value) return
            cachedDarkMode = value
            sp.edit().putBoolean(KEY_DARK_MODE, value).apply()
        }

    var monitoringEnabled: Boolean
        get() = cachedMonitoring ?: sp.getBoolean(KEY_MONITORING, false).also { cachedMonitoring = it }
        set(value) {
            if (cachedMonitoring == value) return
            cachedMonitoring = value
            sp.edit().putBoolean(KEY_MONITORING, value).commit()
        }

    var baselineIp: String
        get() = cachedBaseline ?: (sp.getString(KEY_BASELINE, "") ?: "").also { cachedBaseline = it }
        set(value) {
            if (cachedBaseline == value) return
            cachedBaseline = value
            sp.edit().putString(KEY_BASELINE, value).commit()
        }

    var lastKnownIp: String
        get() = cachedLastIp ?: (sp.getString(KEY_LAST_IP, "") ?: "").also { cachedLastIp = it }
        set(value) {
            if (cachedLastIp == value) return
            cachedLastIp = value
            sp.edit().putString(KEY_LAST_IP, value).commit()
        }

    var lastLocation: String
        get() = cachedLastLocation ?: (sp.getString(KEY_LAST_LOCATION, "") ?: "").also { cachedLastLocation = it }
        set(value) {
            if (cachedLastLocation == value) return
            cachedLastLocation = value
            sp.edit().putString(KEY_LAST_LOCATION, value).commit()
        }

    var lastChangeAt: Long
        get() = cachedLastChange ?: sp.getLong(KEY_LAST_CHANGE, 0L).also { cachedLastChange = it }
        set(value) {
            if (cachedLastChange == value) return
            cachedLastChange = value
            sp.edit().putLong(KEY_LAST_CHANGE, value).commit()
        }

    var intervalSeconds: Int
        get() = cachedInterval ?: sp.getInt(KEY_INTERVAL, 30).coerceIn(MIN_INTERVAL, MAX_INTERVAL).also {
            cachedInterval = it
        }
        set(value) {
            val next = value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)
            if (cachedInterval == next) return
            cachedInterval = next
            sp.edit().putInt(KEY_INTERVAL, next).commit()
        }

    var ignoreVpn: Boolean
        get() = cachedIgnoreVpn ?: sp.getBoolean(KEY_IGNORE_VPN, false).also { cachedIgnoreVpn = it }
        set(value) {
            if (cachedIgnoreVpn == value) return
            cachedIgnoreVpn = value
            sp.edit().putBoolean(KEY_IGNORE_VPN, value).apply()
        }

    var wifiCellularOnly: Boolean
        get() = cachedWifiCellularOnly ?: sp.getBoolean(KEY_WIFI_CELLULAR_ONLY, false).also {
            cachedWifiCellularOnly = it
        }
        set(value) {
            if (cachedWifiCellularOnly == value) return
            cachedWifiCellularOnly = value
            sp.edit().putBoolean(KEY_WIFI_CELLULAR_ONLY, value).apply()
        }

    var quietHoursEnabled: Boolean
        get() = cachedQuietEnabled ?: sp.getBoolean(KEY_QUIET_ENABLED, false).also { cachedQuietEnabled = it }
        set(value) {
            if (cachedQuietEnabled == value) return
            cachedQuietEnabled = value
            sp.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()
        }

    var quietStartHour: Int
        get() = cachedQuietStart ?: sp.getInt(KEY_QUIET_START, 2).also { cachedQuietStart = it }
        set(value) {
            if (cachedQuietStart == value) return
            cachedQuietStart = value
            sp.edit().putInt(KEY_QUIET_START, value).apply()
        }

    var quietEndHour: Int
        get() = cachedQuietEnd ?: sp.getInt(KEY_QUIET_END, 6).also { cachedQuietEnd = it }
        set(value) {
            if (cachedQuietEnd == value) return
            cachedQuietEnd = value
            sp.edit().putInt(KEY_QUIET_END, value).apply()
        }

    fun persistObservedIp(ip: String, location: String, setBaselineIfEmpty: Boolean) {
        val needBaseline = setBaselineIfEmpty && baselineIp.isBlank()
        if (lastKnownIp == ip && lastLocation == location && !needBaseline) return
        cachedLastIp = ip
        cachedLastLocation = location
        val editor = sp.edit()
            .putString(KEY_LAST_IP, ip)
            .putString(KEY_LAST_LOCATION, location)
        if (needBaseline) {
            cachedBaseline = ip
            editor.putString(KEY_BASELINE, ip)
        }
        editor.commit()
    }

    fun persistQuietHours(start: Int, end: Int) {
        if (quietStartHour == start && quietEndHour == end) return
        cachedQuietStart = start
        cachedQuietEnd = end
        sp.edit()
            .putInt(KEY_QUIET_START, start)
            .putInt(KEY_QUIET_END, end)
            .apply()
    }

    fun persistIpChange(ip: String, location: String, changedAt: Long) {
        cachedLastIp = ip
        cachedLastLocation = location
        cachedBaseline = ip
        cachedLastChange = changedAt
        sp.edit()
            .putString(KEY_LAST_IP, ip)
            .putString(KEY_LAST_LOCATION, location)
            .putString(KEY_BASELINE, ip)
            .putLong(KEY_LAST_CHANGE, changedAt)
            .commit()
    }

    companion object {
        private const val NAME = "ip_jump_prefs"
        private const val KEY_MONITORING = "monitoring"
        private const val KEY_DARK_MODE = "dark_mode"
        private const val KEY_BASELINE = "baseline_ip"
        private const val KEY_LAST_IP = "last_ip"
        private const val KEY_LAST_LOCATION = "last_location"
        private const val KEY_LAST_CHANGE = "last_change"
        private const val KEY_INTERVAL = "interval_seconds"
        private const val KEY_IGNORE_VPN = "ignore_vpn"
        private const val KEY_WIFI_CELLULAR_ONLY = "wifi_cellular_only"
        private const val KEY_QUIET_ENABLED = "quiet_enabled"
        private const val KEY_QUIET_START = "quiet_start"
        private const val KEY_QUIET_END = "quiet_end"
        const val MIN_INTERVAL = 1
        const val MAX_INTERVAL = 86400

        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs {
            return instance ?: synchronized(this) {
                instance ?: Prefs(context.applicationContext).also { instance = it }
            }
        }
    }
}
