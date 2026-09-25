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
- **Reliable alerts:** exact alarms (`AlarmManager.setExactAndAllowWhileIdle`) plus a high-priority notification.
  The chosen sound loops on the **alarm** audio stream for up to 60 s or until you tap **Dismiss**, open the
  notification, or swipe it away.
- **Rescheduling** happens automatically on boot, app update, time/time-zone change, calendar changes
  (WorkManager content-URI trigger), every 15 minutes (periodic WorkManager job), and whenever you change settings.
- **Test alert** (right now) and **Test in 1 min** (runs through the real alarm path, so you can lock the phone to check it).

## First-run permissions

| Permission | Why |
|---|---|
| Calendar (read) | to read your meetings |
| Notifications (Android 13+) | to show the alert |
| Alarms & reminders / exact alarms | granted automatically on Android 13+ (`USE_EXACT_ALARM`); on Android 12 you may need to allow it. The app shows a button if it's missing |
| Battery optimization exemption (optional) | the app offers a button. Recommended on phones with aggressive battery savers (Samsung, Xiaomi, etc.) |

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
5. Tap **Test alert** to hear it.

## Tech

Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 35, AlarmManager, a short foreground service
(`mediaPlayback`) to play the sound, and WorkManager for rescheduling.
