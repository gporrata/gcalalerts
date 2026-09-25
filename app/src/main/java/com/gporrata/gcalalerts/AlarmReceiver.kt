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

/** Handles the "Stop" notification action / swipe-away (works from the lock screen). */
class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nid = intent.getIntExtra(AlertService.EXTRA_NID, 0)
        AlertService.dismiss(context, nid)
    }
}

/** Handles the "Snooze 5 min" notification action (works from the lock screen). */
class SnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nid = intent.getIntExtra(AlertService.EXTRA_NID, 0)
        AlertService.snooze(context, intent, nid)
    }
}
