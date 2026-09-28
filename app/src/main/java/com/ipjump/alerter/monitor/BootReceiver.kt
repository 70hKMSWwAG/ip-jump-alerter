package com.ipjump.alerter.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ipjump.alerter.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }
        if (!Prefs(context).monitoringEnabled) return
        val pending = goAsync()
        try {
            MonitorScheduler.start(context)
        } finally {
            pending.finish()
        }
    }
}
