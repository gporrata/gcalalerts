package com.gporrata.gcalalerts

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STOP_ALERT = "stop_alert"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleStop(intent)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AppScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStop(intent)
    }

    private fun handleStop(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_STOP_ALERT, false) == true) {
            stopService(Intent(this, AlertService::class.java))
        }
    }
}

private const val TARGET_DEFAULT = Long.MIN_VALUE

data class PickRequest(val existing: Uri?, val title: String)

class PickRingtone : ActivityResultContract<PickRequest, Uri?>() {
    override fun createIntent(context: Context, input: PickRequest): Intent =
        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_ALARM_ALERT_URI)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, input.existing)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, input.title)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (resultCode != Activity.RESULT_OK || intent == null) return null
        return IntentCompat.getParcelableExtra(intent, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
    }
}

private fun soundTitle(c: Context, uri: Uri?): String {
    if (uri == null) return "Default"
    return try {
        RingtoneManager.getRingtone(c, uri)?.getTitle(c) ?: uri.lastPathSegment ?: "Sound"
    } catch (e: Exception) {
        "Sound"
    }
}

private fun notifGranted(c: Context): Boolean {
    val runtime = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    return runtime && NotificationManagerCompat.from(c).areNotificationsEnabled()
}

private fun ignoringBattery(c: Context) =
    c.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(c.packageName)

private data class LoadResult(
    val calendars: List<CalInfo>,
    val meetings: List<Meeting>,
    val scheduled: Int,
    val titles: Map<String, String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var hasCal by remember { mutableStateOf(CalendarRepo.hasPermission(ctx)) }
    var hasNotif by remember { mutableStateOf(notifGranted(ctx)) }
    var canExact by remember { mutableStateOf(Scheduler.canExact(ctx)) }
    var batteryOk by remember { mutableStateOf(ignoringBattery(ctx)) }

    var enabled by remember { mutableStateOf(Prefs.alertsEnabled(ctx)) }
    var lead by remember { mutableIntStateOf(Prefs.leadMinutes(ctx)) }
    var data by remember { mutableStateOf(LoadResult(emptyList(), emptyList(), 0, emptyMap())) }
    var loading by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Meeting?>(null) }
    var pickTarget by remember { mutableStateOf<Long?>(null) }

    LifecycleResumeEffect(Unit) {
        hasCal = CalendarRepo.hasPermission(ctx)
        hasNotif = notifGranted(ctx)
        canExact = Scheduler.canExact(ctx)
        batteryOk = ignoringBattery(ctx)
        refresh++
        onPauseOrDispose { }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasCal = CalendarRepo.hasPermission(ctx)
        hasNotif = notifGranted(ctx)
        refresh++
    }
    fun requestPerms() {
        val p = mutableListOf(Manifest.permission.READ_CALENDAR)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(p.toTypedArray())
    }
    LaunchedEffect(Unit) { if (!hasCal || !hasNotif) requestPerms() }

    val picker = rememberLauncherForActivityResult(PickRingtone()) { uri ->
        val t = pickTarget
        if (uri != null && t != null) {
            if (t == TARGET_DEFAULT) Prefs.setDefaultSound(ctx, uri) else Prefs.setEventSound(ctx, t, uri)
            refresh++
        }
        pickTarget = null
    }
    fun pick(target: Long, existing: Uri?, title: String) {
        pickTarget = target
        picker.launch(PickRequest(existing, title))
    }

    LaunchedEffect(refresh, hasCal) {
        loading = true
        data = withContext(Dispatchers.IO) {
            val cals = CalendarRepo.calendars(ctx)
            val meetings = CalendarRepo.upcoming(ctx)
            val n = Scheduler.reschedule(ctx)
            val uris = (meetings.mapNotNull { Prefs.eventSound(ctx, it.eventId) } + Prefs.defaultSound(ctx)).toSet()
            LoadResult(cals, meetings, n, uris.associate { it.toString() to soundTitle(ctx, it) })
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("gcalalerts") },
                actions = {
                    IconButton(onClick = { refresh++ }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") }
                }
            )
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ---- Permissions ----
            if (!hasCal || !hasNotif || !canExact || !batteryOk) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Setup needed", fontWeight = FontWeight.Bold)
                            if (!hasCal || !hasNotif) {
                                Text("Calendar access and notifications are required.")
                                Button(onClick = {
                                    if (!hasNotif && hasCal && Build.VERSION.SDK_INT >= 26 &&
                                        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                                    ) {
                                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                                    } else requestPerms()
                                }) { Text("Grant permissions") }
                                OutlinedButton(onClick = {
                                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                                }) { Text("Open app settings") }
                            }
                            if (!canExact && Build.VERSION.SDK_INT >= 31) {
                                Text("Exact alarms are off, so alerts may be late.")
                                Button(onClick = {
                                    ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                                }) { Text("Allow exact alarms") }
                            }
                            if (!batteryOk) {
                                Text("Optional: exempt from battery optimization for more reliable alerts.")
                                OutlinedButton(onClick = {
                                    try {
                                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
                                    } catch (e: Exception) {
                                        ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                    }
                                }) { Text("Disable battery optimization") }
                            }
                        }
                    }
                }
            }

            // ---- Settings ----
            item {
                Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Alerts enabled", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            Switch(checked = enabled, onCheckedChange = {
                                enabled = it; Prefs.setAlertsEnabled(ctx, it); refresh++
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Alert before meeting", Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                lead = (lead - 1).coerceAtLeast(0); Prefs.setLeadMinutes(ctx, lead); refresh++
                            }) { Text("−") }
                            Text("$lead min", Modifier.padding(horizontal = 8.dp))
                            OutlinedButton(onClick = {
                                lead = (lead + 1).coerceAtMost(1440); Prefs.setLeadMinutes(ctx, lead); refresh++
                            }) { Text("+") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Default sound")
                                Text(
                                    data.titles[Prefs.defaultSound(ctx).toString()] ?: "…",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            OutlinedButton(onClick = {
                                pick(TARGET_DEFAULT, Prefs.defaultSound(ctx), "Default alert sound")
                            }) { Text("Change") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val begin = System.currentTimeMillis() + lead * 60_000L
                                val i = Intent(ctx, AlertService::class.java)
                                    .setAction(AlertService.ACTION_START)
                                    .putExtra(Scheduler.EXTRA_EVENT_ID, -1L)
                                    .putExtra(Scheduler.EXTRA_BEGIN, begin)
                                    .putExtra(Scheduler.EXTRA_END, begin + 30 * 60_000L)
                                    .putExtra(Scheduler.EXTRA_TITLE, "Test meeting")
                                    .putExtra(Scheduler.EXTRA_TEST, true)
                                    .putExtra(Scheduler.EXTRA_SOUND, Prefs.defaultSound(ctx).toString())
                                ContextCompat.startForegroundService(ctx, i)
                            }) { Text("Test alert") }
                            OutlinedButton(onClick = {
                                Scheduler.scheduleTest(ctx, 60_000L)
                                Toast.makeText(ctx, "Test alert in 1 minute (you can lock the phone)", Toast.LENGTH_LONG).show()
                            }) { Text("Test in 1 min") }
                        }
                        Text(
                            if (!hasCal) "No calendar access" else if (loading) "Updating…"
                            else "${data.scheduled} alert(s) scheduled for the next ${CalendarRepo.DAYS_AHEAD} days",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ---- Accounts & calendars ----
            item { Text("Accounts & calendars", style = MaterialTheme.typography.titleMedium) }
            val selected = Prefs.selectedAccounts(ctx)
            val disabled = Prefs.disabledCalendars(ctx)
            val byAccount = data.calendars.groupBy { it.account }
            if (byAccount.isEmpty()) {
                item { Text(if (hasCal) "No calendars found on this phone." else "Grant calendar access to see accounts.") }
            } else if (selected.none { it in byAccount.keys }) {
                item { Text("Select which account(s) should give alerts:", fontWeight = FontWeight.Bold) }
            }
            byAccount.forEach { (account, cals) ->
                item(key = "acct-$account") {
                    Card {
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            val on = account in selected
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = on, onCheckedChange = {
                                    Prefs.setAccountSelected(ctx, account, it); refresh++
                                })
                                Column {
                                    Text(account, fontWeight = FontWeight.Bold)
                                    Text(cals.first().accountType, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (on) {
                                cals.forEach { cal ->
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 32.dp)) {
                                        Checkbox(checked = cal.id !in disabled, onCheckedChange = {
                                            Prefs.setCalendarEnabled(ctx, cal.id, it); refresh++
                                        })
                                        Text(cal.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- Upcoming meetings ----
            item { Text("Upcoming meetings", style = MaterialTheme.typography.titleMedium) }
            if (data.meetings.isEmpty()) {
                item { Text("No upcoming timed meetings in selected calendars.") }
            }
            items(data.meetings, key = { "m-${it.eventId}-${it.begin}" }) { m ->
                val soundOverride = Prefs.eventSound(ctx, m.eventId)
                val leadOverride = Prefs.eventLead(ctx, m.eventId)
                val defaultTitle = data.titles[Prefs.defaultSound(ctx).toString()] ?: "default"
                Card(Modifier.fillMaxWidth().clickable { editing = m }) {
                    Column(Modifier.padding(12.dp)) {
                        Text(m.title, fontWeight = FontWeight.Bold)
                        Text(
                            DateUtils.formatDateRange(
                                ctx, m.begin, m.end,
                                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
                                    DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
                            )
                        )
                        Text("${m.account} · ${m.calendarName}", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "🔔 ${leadOverride ?: lead} min before · " +
                                (soundOverride?.let { data.titles[it.toString()] ?: "custom" } ?: "$defaultTitle (default)"),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (soundOverride != null || leadOverride != null) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }

    editing?.let { m ->
        var leadText by remember(m) { mutableStateOf(Prefs.eventLead(ctx, m.eventId)?.toString() ?: "") }
        val soundOverride = Prefs.eventSound(ctx, m.eventId)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(m.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Applies to this meeting (all occurrences if recurring).", style = MaterialTheme.typography.bodySmall)
                    Text("Sound: " + (soundOverride?.let { data.titles[it.toString()] ?: soundTitle(ctx, it) } ?: "Default"))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pick(m.eventId, soundOverride ?: Prefs.defaultSound(ctx), "Sound for ${m.title}")
                        }) { Text("Choose sound") }
                        if (soundOverride != null) {
                            TextButton(onClick = { Prefs.setEventSound(ctx, m.eventId, null); refresh++ }) { Text("Use default") }
                        }
                    }
                    OutlinedTextField(
                        value = leadText,
                        onValueChange = { v -> leadText = v.filter { it.isDigit() }.take(4) },
                        label = { Text("Minutes before (blank = default $lead)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedButton(onClick = {
                        val i = Intent(ctx, AlertService::class.java)
                            .setAction(AlertService.ACTION_START)
                            .putExtra(Scheduler.EXTRA_EVENT_ID, m.eventId)
                            .putExtra(Scheduler.EXTRA_BEGIN, m.begin)
                            .putExtra(Scheduler.EXTRA_END, m.end)
                            .putExtra(Scheduler.EXTRA_TITLE, m.title)
                            .putExtra(Scheduler.EXTRA_LOCATION, m.location)
                            .putExtra(Scheduler.EXTRA_TEST, true)
                        ContextCompat.startForegroundService(ctx, i)
                    }) { Text("Preview alert") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.setEventLead(ctx, m.eventId, leadText.toIntOrNull())
                    editing = null
                    refresh++
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}
