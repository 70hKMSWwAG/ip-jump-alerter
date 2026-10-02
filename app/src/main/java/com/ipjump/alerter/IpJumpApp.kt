package com.ipjump.alerter

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.ipjump.alerter.data.Prefs
import com.ipjump.alerter.monitor.AlertNotifier
import com.ipjump.alerter.monitor.MonitorScheduler

class IpJumpApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(
            if (Prefs.get(this).darkMode) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
        AlertNotifier.ensureChannels(this)
        MonitorScheduler.ensure(this)
    }
}
