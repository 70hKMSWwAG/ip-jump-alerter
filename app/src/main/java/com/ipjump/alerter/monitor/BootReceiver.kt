package com.ipjump.alerter.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ipjump.alerter.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs(context).monitoringEnabled) {
            MonitorScheduler.start(context)
        }
    }
}
