package com.gporrata.gcalalerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * Full-screen alert shown over the lock screen (via the notification's full-screen intent).
 * "Stop" works without unlocking the phone. Back also stops the alert.
 */
class AlertActivity : ComponentActivity() {
    private var alert by mutableStateOf<Intent?>(null)

    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val nid = intent.getIntExtra(AlertService.EXTRA_NID, 0)
            val mine = alert?.getIntExtra(AlertService.EXTRA_NID, 0) ?: 0
            if (nid == 0 || nid == mine) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        alert = intent

        ContextCompat.registerReceiver(
            this, stoppedReceiver, IntentFilter(AlertService.ACTION_ALERT_STOPPED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = stop()
        })

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                AlertScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        alert = intent
    }

    override fun onDestroy() {
        unregisterReceiver(stoppedReceiver)
        super.onDestroy()
    }

    private fun nid() = alert?.getIntExtra(AlertService.EXTRA_NID, 0) ?: 0

    private fun stop() {
        AlertService.dismiss(this, nid())
        finish()
    }

    private fun snooze() {
        alert?.let { AlertService.snooze(this, it, nid()) }
        finish()
    }

    @androidx.compose.runtime.Composable
    private fun AlertScreen() {
        val i = alert
        val title = i?.getStringExtra(Scheduler.EXTRA_TITLE) ?: "Meeting"
        val begin = i?.getLongExtra(Scheduler.EXTRA_BEGIN, System.currentTimeMillis()) ?: System.currentTimeMillis()
        val end = i?.getLongExtra(Scheduler.EXTRA_END, begin) ?: begin
        val location = i?.getStringExtra(Scheduler.EXTRA_LOCATION)
        val whenText = DateUtils.formatDateRange(
            this, begin, end,
            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL
        )
        val rel = DateUtils.getRelativeTimeSpanString(begin, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)

        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Spacer(Modifier.weight(1f))
                Text("🔔 Upcoming meeting", style = MaterialTheme.typography.titleMedium)
                Text(
                    title, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 36.sp
                )
                Text(whenText, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(rel.toString(), style = MaterialTheme.typography.titleMedium)
                if (location != null) {
                    Text(location, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { stop() },
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White)
                ) {
                    Text("STOP", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { snooze() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text("Snooze 5 min", fontSize = 18.sp)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
