package com.ipjump.alerter.data

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var darkMode: Boolean
        get() = sp.getBoolean(KEY_DARK_MODE, false)
        set(value) = sp.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var monitoringEnabled: Boolean
        get() = sp.getBoolean(KEY_MONITORING, false)
        set(value) = sp.edit().putBoolean(KEY_MONITORING, value).apply()

    var baselineIp: String
        get() = sp.getString(KEY_BASELINE, "") ?: ""
        set(value) = sp.edit().putString(KEY_BASELINE, value).apply()

    var lastKnownIp: String
        get() = sp.getString(KEY_LAST_IP, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_IP, value).apply()

    var lastLocation: String
        get() = sp.getString(KEY_LAST_LOCATION, "") ?: ""
        set(value) = sp.edit().putString(KEY_LAST_LOCATION, value).apply()

    var lastChangeAt: Long
        get() = sp.getLong(KEY_LAST_CHANGE, 0L)
        set(value) = sp.edit().putLong(KEY_LAST_CHANGE, value).apply()

    var intervalSeconds: Int
        get() = sp.getInt(KEY_INTERVAL, 30).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(value) = sp.edit().putInt(KEY_INTERVAL, value.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()

    var ignoreVpn: Boolean
        get() = sp.getBoolean(KEY_IGNORE_VPN, false)
        set(value) = sp.edit().putBoolean(KEY_IGNORE_VPN, value).apply()

    var wifiCellularOnly: Boolean
        get() = sp.getBoolean(KEY_WIFI_CELLULAR_ONLY, false)
        set(value) = sp.edit().putBoolean(KEY_WIFI_CELLULAR_ONLY, value).apply()

    var quietHoursEnabled: Boolean
        get() = sp.getBoolean(KEY_QUIET_ENABLED, false)
        set(value) = sp.edit().putBoolean(KEY_QUIET_ENABLED, value).apply()

    var quietStartHour: Int
        get() = sp.getInt(KEY_QUIET_START, 2)
        set(value) = sp.edit().putInt(KEY_QUIET_START, value).apply()

    var quietEndHour: Int
        get() = sp.getInt(KEY_QUIET_END, 6)
        set(value) = sp.edit().putInt(KEY_QUIET_END, value).apply()

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
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 86400
    }
}
