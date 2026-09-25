package com.gporrata.gcalalerts

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GcalApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AlertService.createChannel(this)
        RescheduleWorker.setup(this)
    }
}

/** Recomputes alarms. Runs every 15 min and whenever the calendar provider content changes. */
class RescheduleWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Scheduler.reschedule(applicationContext)
        if (tags.contains(TAG_CONTENT)) enqueueContentTrigger(applicationContext)
        return Result.success()
    }

    companion object {
        private const val TAG_CONTENT = "content"

        fun setup(c: Context) {
            val periodic = PeriodicWorkRequestBuilder<RescheduleWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(c)
                .enqueueUniquePeriodicWork("periodic-reschedule", ExistingPeriodicWorkPolicy.KEEP, periodic)
            enqueueContentTrigger(c, ExistingWorkPolicy.KEEP)
        }

        fun enqueueContentTrigger(c: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val constraints = Constraints.Builder()
                .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                .setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
                .build()
            val req = OneTimeWorkRequestBuilder<RescheduleWorker>()
                .setConstraints(constraints)
                .addTag(TAG_CONTENT)
                .build()
            WorkManager.getInstance(c).enqueueUniqueWork("calendar-changed", policy, req)
        }
    }
}

/** Boot, app update, time/zone change, exact-alarm permission change, calendar provider change. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        thread {
            try {
                Scheduler.reschedule(app)
                RescheduleWorker.setup(app)
            } finally {
                pending.finish()
            }
        }
    }
}
