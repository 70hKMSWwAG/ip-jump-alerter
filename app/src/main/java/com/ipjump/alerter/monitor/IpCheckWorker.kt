package com.ipjump.alerter.monitor

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ipjump.alerter.data.Prefs

class IpCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        if (!prefs.monitoringEnabled) return Result.success()
        return runCatching {
            val result = IpChecker.check(applicationContext, IpChecker.REASON_PERIODIC)
            if (result.skipped && result.message == "lookup_failed") {
                Result.retry()
            } else {
                Result.success()
            }
        }.getOrDefault(Result.retry())
    }
}
