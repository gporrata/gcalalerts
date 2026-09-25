package com.gporrata.gcalalerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/** Fired by AlarmManager; hands off to the foreground AlertService which plays the sound. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val test = intent.getBooleanExtra(Scheduler.EXTRA_TEST, false)
        if (!test && !Prefs.alertsEnabled(context)) return
        val svc = Intent(context, AlertService::class.java)
            .setAction(AlertService.ACTION_START)
            .putExtras(intent)
        try {
            ContextCompat.startForegroundService(context, svc)
        } catch (e: Exception) {
            Log.w("gcalalerts", "Could not start alert service, posting plain notification", e)
            AlertService.postPlainNotification(context, intent)
        }
    }
}

/** Handles the "Dismiss" notification action / swipe-away. */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nid = intent.getIntExtra(AlertService.EXTRA_NID, 0)
        AlertService.dismiss(context, nid)
    }
}
