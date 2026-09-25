# gcalalerts

A small Android app that alerts you a configurable number of minutes before each meeting in your calendar,
with a selectable alert sound per meeting and per-account filtering.

## Features

- **Uses the calendars already on your phone.** Events come from Android's calendar provider
  (`CalendarContract`), which syncs every Google account you've added. No Google sign-in or API keys needed.
- **Choose accounts and calendars.** Calendars are grouped by account (e.g. each Gmail address). Tick the
  account(s) that should give alerts, then untick any individual calendars you don't want.
- **Default lead time** (minutes before the meeting) and **default alert sound**.
- **Upcoming meetings list** (next 7 days). Recurring events are expanded; all-day, declined and cancelled
  events are skipped. Tap a meeting to set **its own sound** (any ringtone, notification or alarm sound)
  and optionally **its own lead time**. Overrides are stored per event, so they apply to every occurrence of a recurring series.
- **Reliable alerts:** alerts are scheduled as alarm clocks (`AlarmManager.setAlarmClock`), which fire on time even in Doze/battery saver,
  plus a high-priority alarm notification. While an alert is scheduled, Android shows the **alarm-clock icon in the status bar**
  (and your next meeting alert may appear as the "next alarm" on the lock screen/clock); tapping it opens gcalalerts.
  The chosen sound loops on the **alarm** audio stream for up to 60 s or until you stop it.
- **Stop from the lock screen, no unlock needed:**
  - When the phone is locked or the screen is off, the screen turns on and a **full-screen alert** appears over
    the lock screen showing the meeting title, time and location, with a big red **STOP** button (plus **Snooze 5 min**).
    Pressing Back also stops the alert.
  - The notification itself has **Stop** and **Snooze 5 min** buttons, visible on the lock screen. They stop the
    sound directly without asking you to unlock.
  - When you're using the phone, the alert appears as a heads-up notification with the same **Stop** button.
    Tapping the notification opens the full-screen alert.
- **Rescheduling** happens automatically on boot, app update, time/time-zone change, calendar changes
  (WorkManager content-URI trigger), every 15 minutes (periodic WorkManager job), and whenever you change settings.
- **Test alert** (right now) and **Test in 1 min** (runs through the real alarm path, so you can lock the phone to check it).

## First-run permissions

| Permission | Why |
|---|---|
| Calendar (read) | to read your meetings |
| Notifications (Android 13+) | to show the alert |
| Alarms & reminders / exact alarms | granted automatically on Android 13+ (`USE_EXACT_ALARM`); on Android 12 you may need to allow it. The app shows a button if it's missing |
| Full-screen notifications (Android 14+) | needed for the full-screen STOP screen over the lock screen. If it's off, the app shows an **Allow full-screen alerts** button that opens *Settings → Apps → gcalalerts → Full-screen notifications* (`ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`). The notification's Stop button works either way |

**Troubleshooting (optional):** if alerts arrive late (common on Samsung/Xiaomi and other aggressive battery savers),
use **Disable battery optimization** in the app's *Troubleshooting* section. Most phones don't need it.

Sounds play on the alarm volume stream, so check your **alarm volume**. Do Not Disturb setups that block alarms will also silence alerts.

## Build

Requirements: JDK 17 and the Android SDK (platform `android-35`, build-tools `35.0.0`).

```bash
export ANDROID_HOME=$HOME/Android/Sdk      # or create local.properties with sdk.dir=...
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Install with adb

1. On the phone, enable **Developer options → USB debugging** and plug it in over USB.
2. Accept the "Allow USB debugging?" prompt on the phone.
3. Then:

```bash
adb devices                 # the phone should show as "device" (not "unauthorized")
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Linux, if `adb devices` shows `no permissions`, install udev rules (e.g. `sudo apt install android-sdk-platform-tools-common`)
and replug the phone.

## Usage

1. Open **gcalalerts** and grant calendar and notification access.
2. Under **Accounts & calendars**, tick the account(s) that should give alerts (and untick any calendars you don't want).
3. Set **Alert before meeting** (minutes) and the **Default sound**.
4. Tap any meeting in **Upcoming meetings** to give it a different sound or lead time.
5. Tap **Test alert** to hear it. To try the lock-screen flow, tap **Test in 1 min** and lock the phone.
   When it fires, the full-screen alert appears with **STOP**.

## Tech

Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 35, AlarmManager, a short foreground service
(`mediaPlayback`) to play the sound, and WorkManager for rescheduling.
