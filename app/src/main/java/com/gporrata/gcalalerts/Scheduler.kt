package com.gporrata.gcalalerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log

/** Schedules one exact alarm per upcoming meeting instance. */
object Scheduler {
    private const val TAG = "gcalalerts"

    const val EXTRA_EVENT_ID = "eventId"
    const val EXTRA_BEGIN = "begin"
    const val EXTRA_END = "end"
    const val EXTRA_TITLE = "title"
    const val EXTRA_LOCATION = "location"
    const val EXTRA_SOUND = "sound"
    const val EXTRA_TEST = "test"

    fun canExact(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun alarmIntent(c: Context, key: Uri) =
        Intent(c, AlarmReceiver::class.java).setData(key)

    private fun key(eventId: Long, begin: Long): Uri = Uri.parse("gcalalerts://alert/$eventId/$begin")

    /** Cancels all previously scheduled alarms and schedules fresh ones. Returns number scheduled. */
    @Synchronized
    fun reschedule(c: Context): Int {
        val am = c.getSystemService(AlarmManager::class.java)
        for (k in Prefs.scheduledKeys(c)) {
            PendingIntent.getBroadcast(
                c, 0, alarmIntent(c, Uri.parse(k)),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { am.cancel(it); it.cancel() }
        }
        if (!Prefs.alertsEnabled(c) || !CalendarRepo.hasPermission(c)) {
            Prefs.setScheduledKeys(c, emptySet())
            return 0
        }
        val now = System.currentTimeMillis()
        val keys = mutableSetOf<String>()
        val exact = canExact(c)
        for (m in CalendarRepo.upcoming(c)) {
            val at = m.begin - Prefs.leadFor(c, m.eventId) * 60_000L
            if (at <= now) continue
            val k = key(m.eventId, m.begin)
            val intent = alarmIntent(c, k)
                .putExtra(EXTRA_EVENT_ID, m.eventId)
                .putExtra(EXTRA_BEGIN, m.begin)
                .putExtra(EXTRA_END, m.end)
                .putExtra(EXTRA_TITLE, m.title)
                .putExtra(EXTRA_LOCATION, m.location)
            val pi = PendingIntent.getBroadcast(
                c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setAlarm(am, at, pi, exact)
            keys += k.toString()
        }
        Prefs.setScheduledKeys(c, keys)
        Log.i(TAG, "Scheduled ${keys.size} alerts (exact=$exact)")
        return keys.size
    }

    private fun setAlarm(am: AlarmManager, at: Long, pi: PendingIntent, exact: Boolean) {
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Schedules a test alert through the real alarm path. */
    fun scheduleTest(c: Context, delayMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val begin = System.currentTimeMillis() + delayMs + Prefs.leadMinutes(c) * 60_000L
        val intent = alarmIntent(c, Uri.parse("gcalalerts://test"))
            .putExtra(EXTRA_EVENT_ID, -1L)
            .putExtra(EXTRA_BEGIN, begin)
            .putExtra(EXTRA_END, begin + 30 * 60_000L)
            .putExtra(EXTRA_TITLE, "Test meeting")
            .putExtra(EXTRA_TEST, true)
            .putExtra(EXTRA_SOUND, Prefs.defaultSound(c).toString())
        val pi = PendingIntent.getBroadcast(
            c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(am, System.currentTimeMillis() + delayMs, pi, canExact(c))
    }

    /** Re-fires an alert (same meeting extras) after [delayMs]. */
    fun scheduleSnooze(c: Context, alert: Intent, delayMs: Long) {
        val am = c.getSystemService(AlarmManager::class.java)
        val id = alert.getLongExtra(EXTRA_EVENT_ID, -1)
        val begin = alert.getLongExtra(EXTRA_BEGIN, 0)
        val intent = alarmIntent(c, Uri.parse("gcalalerts://snooze/$id/$begin"))
        alert.extras?.let { intent.putExtras(it) }
        intent.removeExtra(AlertService.EXTRA_NID)
        val pi = PendingIntent.getBroadcast(
            c, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        setAlarm(am, System.currentTimeMillis() + delayMs, pi, canExact(c))
    }
}
