package com.example.alarm

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmService : Service() {

    companion object {
        const val CHANNEL_ID = "chrono_alarm_channel_v2"
        const val NOTIFICATION_ID = 5050

        const val ACTION_START_ALARM = "com.example.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"

        fun startAlarm(context: Context, intent: Intent) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtras(intent)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (_: Exception) {
                try {
                    context.startService(serviceIntent)
                } catch (_: Exception) {}
            }
        }

        fun stopAlarm(context: Context) {
            val serviceIntent = Intent(context, AlarmService::class.java).apply {
                action = ACTION_STOP_ALARM
            }
            context.startService(serviceIntent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_STOP_ALARM) {
            stopAlarmInternal()
            return START_NOT_STICKY
        }

        if (action == ACTION_START_ALARM) {
            val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
            val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarme"
            val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
            val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
            val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
            val math = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_MATH, false)
            val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 10)
            val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)

            // Acquire WakeLock to turn screen on immediately
            acquireWakeLock()

            // Play audio / vibration
            AlarmSoundPlayer.play(this, sound, vibrate, vibrationOnly, volume)

            // Create notification channel
            createNotificationChannel()

            // Intent to launch AlarmRingingActivity
            val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, vibrationOnly)
                putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
            }

            // Prepare background activity launch options (Required on Android 14+ / 15)
            val activityOptionsBundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } else {
                ActivityOptions.makeBasic().toBundle()
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                this,
                alarmId.toInt(),
                ringingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                activityOptionsBundle
            )

            // Dismiss intent
            val dismissIntent = Intent(this, AlarmReceiver::class.java).apply {
                this.action = AlarmScheduler.ACTION_ALARM_DISMISS
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
            }
            val dismissPendingIntent = PendingIntent.getBroadcast(
                this,
                (alarmId + 100).toInt(),
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Snooze intent
            val snoozeIntent = Intent(this, AlarmReceiver::class.java).apply {
                this.action = AlarmScheduler.ACTION_ALARM_SNOOZE
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                this,
                (alarmId + 200).toInt(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⏰ $label")
                .setContentText("Alarme disparando! Toque para interagir.")
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

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                try {
                    startForeground(NOTIFICATION_ID, notification)
                } catch (e2: Exception) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, notification)
                }
            }

            // Show floating popup if overlay permission is granted
            AlarmOverlayManager.showOverlay(
                context = this,
                alarmId = alarmId,
                label = label,
                sound = sound,
                vibrate = vibrate,
                math = math,
                snoozeMinutes = snoozeMins,
                volume = volume
            )

            // Launch AlarmRingingActivity directly with background start options
            try {
                startActivity(ringingIntent, activityOptionsBundle)
            } catch (_: Exception) {
                try {
                    startActivity(ringingIntent)
                } catch (_: Exception) {}
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            wakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "ChronoClock:AlarmWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout max
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Disparo de Alarme Chrono"
            val descriptionText = "Notificações de alarme em tela cheia e popup"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableVibration(true)
                setSound(null, null) // Continuous sound and vibration are driven by AlarmSoundPlayer
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun stopAlarmInternal() {
        AlarmSoundPlayer.stop()
        AlarmOverlayManager.dismissOverlay()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }
}
