package com.ipjump.alerter.monitor

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ipjump.alerter.data.Prefs
import java.util.concurrent.TimeUnit

object MonitorScheduler {
    private const val UNIQUE_WORK = "ip_jump_periodic_check"

    fun ensure(context: Context) {
        val prefs = Prefs(context)
        if (prefs.monitoringEnabled) {
            start(context)
        }
    }

    fun start(context: Context) {
        val prefs = Prefs(context)
        prefs.monitoringEnabled = true
        startService(context)
        val work = PeriodicWorkRequestBuilder<IpCheckWorker>(15, TimeUnit.MINUTES)
            .addTag(UNIQUE_WORK)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    fun stop(context: Context) {
        Prefs(context).monitoringEnabled = false
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
        context.stopService(Intent(context, IpMonitorService::class.java))
    }

    fun restart(context: Context) {
        if (Prefs(context).monitoringEnabled) {
            start(context)
        }
    }

    private fun startService(context: Context) {
        val intent = Intent(context, IpMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
