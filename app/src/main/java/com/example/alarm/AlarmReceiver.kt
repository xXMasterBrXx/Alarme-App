package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "chrono_alarm_channel"
        const val NOTIFICATION_ID = 4040
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // Reschedule all enabled alarms on device boot
                CoroutineScope(Dispatchers.IO).launch {
                    val db = AppDatabase.getDatabase(context)
                    val enabledAlarms = db.alarmDao().getEnabledAlarms()
                    for (alarm in enabledAlarms) {
                        AlarmScheduler.scheduleAlarm(context, alarm)
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_TRIGGER -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarme"
                val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                val math = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_MATH, false)
                val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 10)
                val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)

                // Start audio/vibration
                AlarmSoundPlayer.play(context, sound, vibrate, vibrationOnly, volume)

                // Launch Ringing Activity directly
                val ringingIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_USER_ACTION
                    putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                    putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                    putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                    putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, vibrationOnly)
                    putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                    putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                    putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
                }

                // Show notification with full-screen intent
                createNotificationChannel(context)
                showAlarmNotification(context, ringingIntent, alarmId, label, sound, vibrate, math, snoozeMins)

                // Also try launching activity
                try {
                    context.startActivity(ringingIntent)
                } catch (_: Exception) {
                }
            }

            AlarmScheduler.ACTION_ALARM_DISMISS -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                AlarmSoundPlayer.stop()
                cancelNotification(context)

                // Disable if one-time alarm
                if (alarmId > 0) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(context)
                        val alarm = db.alarmDao().getAlarmById(alarmId)
                        if (alarm != null && !alarm.isRepeating()) {
                            db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
                        }
                    }
                }
            }

            AlarmScheduler.ACTION_ALARM_SNOOZE -> {
                val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarme"
                val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                val math = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_MATH, false)
                val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 10)
                val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)

                AlarmSoundPlayer.stop()
                cancelNotification(context)

                AlarmScheduler.scheduleSnooze(context, alarmId, label, sound, vibrate, math, snoozeMins, vibrationOnly, volume)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Alarmes Chrono"
            val descriptionText = "Notificações de disparo de alarme em tela cheia"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(null, null) // Audio handled by AlarmSoundPlayer
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showAlarmNotification(
        context: Context,
        ringingIntent: Intent,
        alarmId: Long,
        label: String,
        sound: String,
        vibrate: Boolean,
        math: Boolean,
        snoozeMins: Int
    ) {
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Dismiss action intent
        val dismissIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_DISMISS
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + 100).toInt(),
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action intent
        val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_SNOOZE
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
            putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
            putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + 200).toInt(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ $label")
            .setContentText("Alarme disparando! Toque para abrir.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desligar", dismissPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Soneca (${snoozeMins}m)", snoozePendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
