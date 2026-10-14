package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val MASTER_CHANNEL_ID = "chrono_alarm_master_channel_v5"
        const val NOTIFICATION_ID = 4040
        private const val TAG = "AlarmReceiver"

        private var staticWakeLock: PowerManager.WakeLock? = null

        fun acquireStaticWakeLock(context: Context) {
            try {
                if (staticWakeLock == null) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    @Suppress("DEPRECATION")
                    staticWakeLock = pm?.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                        "Chrono:AlarmReceiverStaticWakeLock"
                    )
                }
                staticWakeLock?.acquire(60 * 1000L) // 60s até o AlarmService assumir o Foreground
            } catch (_: Throwable) {}
        }

        fun releaseStaticWakeLock() {
            try {
                if (staticWakeLock?.isHeld == true) {
                    staticWakeLock?.release()
                }
                staticWakeLock = null
            } catch (_: Throwable) {}
        }

        fun createAlarmNotificationChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return

                    // Remove canais antigos para evitar reprodução dupla pelo sistema operacional
                    try { notificationManager.deleteNotificationChannel("alarm_clock_channel_master") } catch (_: Throwable) {}
                    try { notificationManager.deleteNotificationChannel("chrono_alarm_master_channel_v4") } catch (_: Throwable) {}

                    val channel = NotificationChannel(
                        MASTER_CHANNEL_ID,
                        "Disparo de Alarmes Chrono",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alertas visuais e notificações em tela cheia dos alarmes"
                        setSound(null, null)
                        enableVibration(false)
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                        setBypassDnd(true)
                    }

                    notificationManager.createNotificationChannel(channel)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Erro ao criar canal de notificação", t)
            }
        }

        fun buildAlarmNotification(
            context: Context,
            alarmId: Long,
            label: String,
            sound: String,
            vibrate: Boolean,
            vibrationOnly: Boolean,
            math: Boolean,
            snoozeMins: Int,
            volume: Float
        ): Notification {
            createAlarmNotificationChannel(context)

            val ringingIntent = Intent(context, AlarmRingingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, vibrationOnly)
                putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
            }

            val fullScreenPendingIntent = PendingIntent.getActivity(
                context,
                alarmId.toInt(),
                ringingIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

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

            val snoozeIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = AlarmScheduler.ACTION_ALARM_SNOOZE
                putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarmId)
                putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, label)
                putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, sound)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, vibrate)
                putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, vibrationOnly)
                putExtra(AlarmScheduler.EXTRA_ALARM_MATH, math)
                putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, snoozeMins)
                putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, volume)
            }
            val snoozePendingIntent = PendingIntent.getBroadcast(
                context,
                (alarmId + 200).toInt(),
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, MASTER_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm_notification)
                .setContentTitle("⏰ $label")
                .setContentText("Alarme disparando! Toque para abrir.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSound(null)
                .setVibrate(null)
                .setOnlyAlertOnce(true)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Desligar", dismissPendingIntent)
                .addAction(android.R.drawable.ic_menu_revert, "Soneca (${snoozeMins}m)", snoozePendingIntent)
                .build()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        try {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(context)
                            val enabledAlarms = db.alarmDao().getEnabledAlarms()
                            for (alarm in enabledAlarms) {
                                AlarmScheduler.scheduleAlarm(context, alarm)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Erro ao reagendar alarmes no boot", t)
                        }
                    }
                }

                AlarmScheduler.ACTION_ALARM_TRIGGER -> {
                    // 1. Manter a CPU temporariamente acordada enquanto o serviço em primeiro plano inicia
                    acquireStaticWakeLock(context)

                    // 2. Iniciar o AlarmService como Foreground Service (garantindo execução mesmo com app fechado)
                    AlarmService.startAlarm(context, intent)
                }

                AlarmScheduler.ACTION_ALARM_DISMISS -> {
                    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                    try { AlarmService.stopAlarm(context) } catch (_: Throwable) {}
                    try { AlarmSoundPlayer.stop(context) } catch (_: Throwable) {}
                    try { AlarmOverlayManager.dismissOverlay() } catch (_: Throwable) {}
                    try { cancelNotification(context) } catch (_: Throwable) {}

                    if (alarmId > 0) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val db = AppDatabase.getDatabase(context)
                                val alarm = db.alarmDao().getAlarmById(alarmId)
                                if (alarm != null && !alarm.isRepeating()) {
                                    db.alarmDao().updateAlarm(alarm.copy(isEnabled = false))
                                }
                            } catch (_: Throwable) {}
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

                    try { AlarmService.stopAlarm(context) } catch (_: Throwable) {}
                    try { AlarmSoundPlayer.stop(context) } catch (_: Throwable) {}
                    try { AlarmOverlayManager.dismissOverlay() } catch (_: Throwable) {}
                    try { cancelNotification(context) } catch (_: Throwable) {}

                    try {
                        AlarmScheduler.scheduleSnooze(context, alarmId, label, sound, vibrate, math, snoozeMins, vibrationOnly, volume)
                    } catch (_: Throwable) {}
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Exceção capturada em AlarmReceiver.onReceive", t)
        }
    }

    private fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
