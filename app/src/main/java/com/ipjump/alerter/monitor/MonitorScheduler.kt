package com.ipjump.alerter.monitor

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.BackoffPolicy
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
        if (Prefs(context).monitoringEnabled) {
            start(context)
        }
    }

    fun start(context: Context) {
        val app = context.applicationContext
        Prefs(app).monitoringEnabled = true
        startService(app)
        val work = PeriodicWorkRequestBuilder<IpCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(UNIQUE_WORK)
            .build()
        WorkManager.getInstance(app).enqueueUniquePeriodicWork(
            UNIQUE_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

    fun stop(context: Context) {
        val app = context.applicationContext
        Prefs(app).monitoringEnabled = false
        WorkManager.getInstance(app).cancelUniqueWork(UNIQUE_WORK)
        app.stopService(Intent(app, IpMonitorService::class.java))
    }

    fun restart(context: Context) {
        if (Prefs(context).monitoringEnabled) {
            start(context)
        }
    }

    private fun startService(context: Context) {
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
