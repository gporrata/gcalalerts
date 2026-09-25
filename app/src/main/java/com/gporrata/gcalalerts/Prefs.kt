package com.gporrata.gcalalerts

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

/** Simple SharedPreferences-backed settings store. */
object Prefs {
    private const val FILE = "gcalalerts"
    const val DEFAULT_LEAD_MINUTES = 5

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun alertsEnabled(c: Context) = sp(c).getBoolean("enabled", true)
    fun setAlertsEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun leadMinutes(c: Context) = sp(c).getInt("lead", DEFAULT_LEAD_MINUTES)
    fun setLeadMinutes(c: Context, v: Int) = sp(c).edit().putInt("lead", v.coerceIn(0, 1440)).apply()

    fun defaultSound(c: Context): Uri =
        sp(c).getString("sound", null)?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(c, RingtoneManager.TYPE_ALARM)
            ?: Settings.System.DEFAULT_ALARM_ALERT_URI

    fun setDefaultSound(c: Context, uri: Uri) = sp(c).edit().putString("sound", uri.toString()).apply()

    /** Google (or other) account names whose calendars produce alerts. */
    fun selectedAccounts(c: Context): Set<String> = sp(c).getStringSet("accounts", emptySet())!!.toSet()
    fun setAccountSelected(c: Context, account: String, selected: Boolean) {
        val s = selectedAccounts(c).toMutableSet()
        if (selected) s.add(account) else s.remove(account)
        sp(c).edit().putStringSet("accounts", s).apply()
    }

    /** Individual calendar ids the user switched off (within selected accounts). */
    fun disabledCalendars(c: Context): Set<Long> =
        sp(c).getStringSet("disabledCals", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun setCalendarEnabled(c: Context, id: Long, enabled: Boolean) {
        val s = sp(c).getStringSet("disabledCals", emptySet())!!.toMutableSet()
        if (enabled) s.remove(id.toString()) else s.add(id.toString())
        sp(c).edit().putStringSet("disabledCals", s).apply()
    }

    /** Per-meeting overrides, keyed by calendar event id (applies to all occurrences of a series). */
    fun eventSound(c: Context, eventId: Long): Uri? =
        sp(c).getString("sound_$eventId", null)?.let(Uri::parse)

    fun setEventSound(c: Context, eventId: Long, uri: Uri?) {
        val e = sp(c).edit()
        if (uri == null) e.remove("sound_$eventId") else e.putString("sound_$eventId", uri.toString())
        e.apply()
    }

    fun eventLead(c: Context, eventId: Long): Int? =
        sp(c).getInt("lead_$eventId", -1).takeIf { it >= 0 }

    fun setEventLead(c: Context, eventId: Long, minutes: Int?) {
        val e = sp(c).edit()
        if (minutes == null) e.remove("lead_$eventId") else e.putInt("lead_$eventId", minutes.coerceIn(0, 1440))
        e.apply()
    }

    fun soundFor(c: Context, eventId: Long): Uri = eventSound(c, eventId) ?: defaultSound(c)
    fun leadFor(c: Context, eventId: Long): Int = eventLead(c, eventId) ?: leadMinutes(c)

    /** Keys (data URIs) of alarms currently scheduled, so they can be cancelled. */
    fun scheduledKeys(c: Context): Set<String> = sp(c).getStringSet("scheduled", emptySet())!!.toSet()
    fun setScheduledKeys(c: Context, keys: Set<String>) = sp(c).edit().putStringSet("scheduled", keys).apply()
}
