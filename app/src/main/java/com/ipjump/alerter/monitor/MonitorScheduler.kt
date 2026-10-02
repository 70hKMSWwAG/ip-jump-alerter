package com.ipjump.alerter.monitor

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ipjump.alerter.data.Prefs
import java.util.concurrent.TimeUnit

object MonitorScheduler {
    private const val UNIQUE_WORK = "ip_jump_periodic_check"

    fun ensure(context: Context) {
        val app = context.applicationContext
        if (!Prefs.get(app).monitoringEnabled) return
        startService(app)
        enqueueWork(app)
    }

    fun start(context: Context) {
        val app = context.applicationContext
        Prefs.get(app).monitoringEnabled = true
        startService(app)
        enqueueWork(app)
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        Prefs.get(app).monitoringEnabled = false
        WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_WORK)
        app.stopService(Intent(app, IpMonitorService::class.java))
    }

    fun restart(context: Context) {
        if (!Prefs.get(context).monitoringEnabled) return
        val app = context.applicationContext
        val intent = Intent(app, IpMonitorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                app.startForegroundService(intent)
            } else {
                app.startService(intent)
            }
        }
    }

    private fun enqueueWork(app: Context) {
        val work = PeriodicWorkRequestBuilder<IpCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(UNIQUE_WORK)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun startService(context: Context) {
        if (IpMonitorService.running) return
        val intent = Intent(context, IpMonitorService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
