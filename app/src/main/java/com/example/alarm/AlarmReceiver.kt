package com.example.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
        const val MASTER_CHANNEL_ID = "chrono_alarm_master_channel_v4"
        const val NOTIFICATION_ID = 4040
        private const val TAG = "AlarmReceiver"

        fun createAlarmNotificationChannel(context: Context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        ?: return

                    val defaultSoundUri = try {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    } catch (_: Throwable) {
                        null
                    }

                    val audioAttrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()

                    val channel = NotificationChannel(
                        MASTER_CHANNEL_ID,
                        "Disparo de Alarmes Chrono",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alertas sonoros, vibração e notificações em tela cheia dos alarmes"
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 800, 400, 800, 400)
                        if (defaultSoundUri != null) {
                            setSound(defaultSoundUri, audioAttrs)
                        }
                        lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                        setBypassDnd(true)
                    }

                    notificationManager.createNotificationChannel(channel)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Erro ao criar canal de notificação", t)
            }
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
                    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                    val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarme"
                    val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                    val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                    val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                    val math = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_MATH, false)
                    val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 10)
                    val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)

                    // 1. Acordar tela via WakeLock
                    try {
                        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                        @Suppress("DEPRECATION")
                        val wl = pm?.newWakeLock(
                            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP or
                            PowerManager.ON_AFTER_RELEASE,
                            "Chrono:AlarmTriggerWakeLock"
                        )
                        wl?.acquire(3 * 60 * 1000L) // 3 minutos
                    } catch (_: Throwable) {}

                    // 2. Tocar áudio e vibração com sistema à prova de falhas
                    try {
                        AlarmSoundPlayer.play(context, sound, vibrate, vibrationOnly, volume)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Erro ao iniciar AlarmSoundPlayer", t)
                    }

                    // 3. Preparar Intent da tela cheia de disparo
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

                    // 4. Publicar notificação de alta prioridade com fullScreenIntent
                    try {
                        showAlarmNotification(
                            context = context,
                            ringingIntent = ringingIntent,
                            alarmId = alarmId,
                            label = label,
                            sound = sound,
                            vibrate = vibrate,
                            vibrationOnly = vibrationOnly,
                            math = math,
                            snoozeMins = snoozeMins,
                            volume = volume
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Erro ao exibir notificação de alarme", t)
                    }

                    // 5. Exibir popup flutuante de sobreposição se autorizado
                    try {
                        AlarmOverlayManager.showOverlay(
                            context = context,
                            alarmId = alarmId,
                            label = label,
                            sound = sound,
                            vibrate = vibrate,
                            vibrationOnly = vibrationOnly,
                            math = math,
                            snoozeMinutes = snoozeMins,
                            volume = volume
                        )
                    } catch (t: Throwable) {
                        Log.e(TAG, "Erro ao exibir overlay de alarme", t)
                    }

                    // 6. Iniciar atividade em tela cheia diretamente
                    try {
                        context.startActivity(ringingIntent)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Início direto de atividade suprimido pelo SO, alerta em andamento via notificação", t)
                    }
                }

                AlarmScheduler.ACTION_ALARM_DISMISS -> {
                    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
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

    private fun showAlarmNotification(
        context: Context,
        ringingIntent: Intent,
        alarmId: Long,
        label: String,
        sound: String,
        vibrate: Boolean,
        vibrationOnly: Boolean,
        math: Boolean,
        snoozeMins: Int,
        volume: Float
    ) {
        createAlarmNotificationChannel(context)

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

        val notification = NotificationCompat.Builder(context, MASTER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_alarm_notification)
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
            .addAction(android.R.drawable.ic_menu_revert, "Soneca (${snoozeMins}m)", snoozePendingIntent)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}
