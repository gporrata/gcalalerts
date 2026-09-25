package com.gporrata.gcalalerts

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

data class CalInfo(
    val id: Long,
    val account: String,
    val accountType: String,
    val name: String,
    val color: Int,
)

data class Meeting(
    val eventId: Long,
    val begin: Long,
    val end: Long,
    val title: String,
    val location: String?,
    val calendarId: Long,
    val account: String,
    val calendarName: String,
)

/** Reads calendars and event instances from the on-device CalendarContract provider. */
object CalendarRepo {
    const val DAYS_AHEAD = 7
    private const val DAY_MS = 24L * 60 * 60 * 1000

    fun hasPermission(c: Context) =
        ContextCompat.checkSelfPermission(c, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun calendars(c: Context): List<CalInfo> {
        if (!hasPermission(c)) return emptyList()
        val out = mutableListOf<CalInfo>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        c.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj, null, null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
        )?.use { cur ->
            while (cur.moveToNext()) {
                out += CalInfo(
                    id = cur.getLong(0),
                    account = cur.getString(1) ?: "(unknown)",
                    accountType = cur.getString(2) ?: "",
                    name = cur.getString(3) ?: "(unnamed)",
                    color = cur.getInt(4),
                )
            }
        }
        return out
    }

    fun enabledCalendars(c: Context, all: List<CalInfo> = calendars(c)): List<CalInfo> {
        val accounts = Prefs.selectedAccounts(c)
        val disabled = Prefs.disabledCalendars(c)
        return all.filter { it.account in accounts && it.id !in disabled }
    }

    /** Timed (non all-day), non-declined, non-cancelled instances starting in the next [days] days. */
    fun upcoming(c: Context, days: Int = DAYS_AHEAD): List<Meeting> {
        if (!hasPermission(c)) return emptyList()
        val cals = enabledCalendars(c).associateBy { it.id }
        if (cals.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + days * DAY_MS)
        }.build()
        val proj = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
            CalendarContract.Instances.STATUS,
        )
        val sel = "${CalendarContract.Instances.ALL_DAY} = 0 AND " +
            "${CalendarContract.Instances.CALENDAR_ID} IN (${cals.keys.joinToString(",")})"
        val out = mutableListOf<Meeting>()
        c.contentResolver.query(uri, proj, sel, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cur ->
            while (cur.moveToNext()) {
                val begin = cur.getLong(1)
                if (begin < now) continue
                if (!cur.isNull(6) && cur.getInt(6) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                if (!cur.isNull(7) && cur.getInt(7) == CalendarContract.Events.STATUS_CANCELED) continue
                val cal = cals[cur.getLong(5)] ?: continue
                out += Meeting(
                    eventId = cur.getLong(0),
                    begin = begin,
                    end = cur.getLong(2),
                    title = cur.getString(3)?.takeIf { it.isNotBlank() } ?: "(no title)",
                    location = cur.getString(4)?.takeIf { it.isNotBlank() },
                    calendarId = cal.id,
                    account = cal.account,
                    calendarName = cal.name,
                )
            }
        }
        return out
    }
}
