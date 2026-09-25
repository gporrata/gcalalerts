package com.gporrata.gcalalerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * Short-lived foreground service: shows the alert notification and plays the chosen sound
 * (looping, on the alarm stream) until dismissed or [MAX_PLAY_MS] elapses.
 */
class AlertService : Service() {
    companion object {
        const val ACTION_START = "com.gporrata.gcalalerts.START"
        const val EXTRA_NID = "nid"
        /** Broadcast (package-local) sent whenever the ringing alert stops, so AlertActivity can close. */
        const val ACTION_ALERT_STOPPED = "com.gporrata.gcalalerts.ALERT_STOPPED"
        const val CHANNEL_ID = "meeting_alerts"
        private const val MAX_PLAY_MS = 60_000L

        @Volatile
        var currentNid: Int = 0
            private set

        fun createChannel(c: Context) {
            val ch = NotificationChannel(CHANNEL_ID, "Meeting alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts before calendar meetings (sound is played by the app)"
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 300, 500)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            c.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }

        /** Stops the sound (if this alert is the one ringing), removes its notification, closes AlertActivity. */
        fun dismiss(c: Context, nid: Int) {
            NotificationManagerCompat.from(c).cancel(nid)
            if (nid == currentNid || nid == 0 || currentNid == 0) {
                c.stopService(Intent(c, AlertService::class.java))
            }
            c.sendBroadcast(Intent(ACTION_ALERT_STOPPED).setPackage(c.packageName).putExtra(EXTRA_NID, nid))
        }

        /** Stops this alert and re-fires it in [minutes] minutes. */
        fun snooze(c: Context, alert: Intent, nid: Int, minutes: Int = 5) {
            Scheduler.scheduleSnooze(c, alert, minutes * 60_000L)
            dismiss(c, nid)
        }

        fun nidFor(i: Intent): Int {
            val id = i.getLongExtra(Scheduler.EXTRA_EVENT_ID, -1)
            val begin = i.getLongExtra(Scheduler.EXTRA_BEGIN, 0)
            return ("$id/$begin".hashCode() and 0x7fffffff).coerceAtLeast(1)
        }

        fun buildNotification(c: Context, i: Intent, nid: Int, withActions: Boolean): Notification {
            val title = i.getStringExtra(Scheduler.EXTRA_TITLE) ?: "Meeting"
            val begin = i.getLongExtra(Scheduler.EXTRA_BEGIN, System.currentTimeMillis())
            val end = i.getLongExtra(Scheduler.EXTRA_END, begin)
            val loc = i.getStringExtra(Scheduler.EXTRA_LOCATION)
            val whenText = DateUtils.formatDateRange(
                c, begin, end, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            )
            val rel = DateUtils.getRelativeTimeSpanString(begin, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            val text = buildString {
                append(whenText).append(" (").append(rel).append(")")
                if (loc != null) append("\n").append(loc)
            }
            val stopPi = PendingIntent.getBroadcast(
                c, nid, Intent(c, DismissReceiver::class.java).putExtra(EXTRA_NID, nid),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val b = NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setWhen(begin)
                .setShowWhen(true)
                .setDeleteIntent(stopPi)
                .setAutoCancel(true)
            if (withActions) {
                // Full-screen alert activity: shown over the lock screen (or as heads-up if the phone is in use).
                val alertActivity = Intent(c, AlertActivity::class.java)
                    .putExtras(i)
                    .putExtra(EXTRA_NID, nid)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
                val fullScreenPi = PendingIntent.getActivity(
                    c, nid, alertActivity, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val snoozePi = PendingIntent.getBroadcast(
                    c, nid, Intent(c, SnoozeReceiver::class.java).putExtras(i).putExtra(EXTRA_NID, nid),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                b.setFullScreenIntent(fullScreenPi, true)
                    .setContentIntent(fullScreenPi)
                    .setOngoing(true)
                    // Broadcast actions (not activities) so they work from the lock screen without unlocking.
                    .addAction(0, "Stop", stopPi)
                    .addAction(0, "Snooze 5 min", snoozePi)
            } else {
                val openPi = PendingIntent.getActivity(
                    c, nid, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                b.setContentIntent(openPi)
            }
            return b.build()
        }

        fun postPlainNotification(c: Context, i: Intent) {
            try {
                NotificationManagerCompat.from(c).notify(nidFor(i), buildNotification(c, i, nidFor(i), false))
            } catch (_: SecurityException) {
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var lastIntent: Intent? = null

    private val timeout = Runnable {
        stopSound()
        // Leave a silent notification behind so the user still sees the reminder.
        val i = lastIntent
        val nid = currentNid
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (i != null && nid != 0) {
            try {
                NotificationManagerCompat.from(this).notify(nid, buildNotification(this, i, nid, false))
            } catch (_: SecurityException) {
            }
        }
        currentNid = 0
        stopSelf()
    }

    private val loopCheck = object : Runnable {
        override fun run() {
            val r = ringtone ?: return
            if (!r.isPlaying) r.play()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action != ACTION_START) {
            stopSelf()
            return START_NOT_STICKY
        }
        val nid = nidFor(intent)
        val notif = buildNotification(this, intent, nid, true)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        try {
            ServiceCompat.startForeground(this, nid, notif, type)
        } catch (e: Exception) {
            postPlainNotification(this, intent)
            stopSelf()
            return START_NOT_STICKY
        }
        currentNid = nid
        lastIntent = intent

        val eventId = intent.getLongExtra(Scheduler.EXTRA_EVENT_ID, -1)
        val sound = intent.getStringExtra(Scheduler.EXTRA_SOUND)?.let(Uri::parse)
            ?: Prefs.soundFor(this, eventId)
        play(sound)
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, MAX_PLAY_MS)
        return START_NOT_STICKY
    }

    private fun play(uri: Uri) {
        stopSound()
        val r = RingtoneManager.getRingtone(this, uri)
            ?: RingtoneManager.getRingtone(this, Settings.System.DEFAULT_ALARM_ALERT_URI)
            ?: return
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
            r.play()
            ringtone = r
        } else {
            r.play()
            ringtone = r
            handler.postDelayed(loopCheck, 1000)
        }
    }

    private fun stopSound() {
        handler.removeCallbacks(loopCheck)
        ringtone?.stop()
        ringtone = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(timeout)
        stopSound()
        sendBroadcast(Intent(ACTION_ALERT_STOPPED).setPackage(packageName).putExtra(EXTRA_NID, currentNid))
        currentNid = 0
        super.onDestroy()
    }
}
