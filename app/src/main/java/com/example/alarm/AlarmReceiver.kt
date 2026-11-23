package com.example.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val MASTER_CHANNEL_ID = "chrono_alarm_master_channel_v6"
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
                staticWakeLock?.acquire(10 * 60 * 1000L) // 10 minutos para garantir áudio mesmo se SO atrasar ForegroundService
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

        fun getSoundUri(context: Context, sound: String): Uri? {
            if (sound == "silent") return null
            if (sound.startsWith("youtube://")) return null
            if (sound == "gentle" || sound == "default" || sound.isBlank()) {
                return Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_gentle}")
            }
            if (sound == "digital" || sound == "radar") {
                return Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_digital}")
            }
            if (sound.startsWith("content://") || sound.startsWith("android.resource://")) {
                return Uri.parse(sound)
            }
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        }

        fun shouldPlayAudioInPlayer(context: Context, sound: String): Boolean {
            if (sound.startsWith("youtube://")) return true
            return AudioRoutingManager.checkConnectedDevices(context).isExternalConnected
        }

        fun getOrCreateAlarmChannel(
            context: Context,
            sound: String,
            vibrate: Boolean,
            vibrationOnly: Boolean,
            muteChannelSound: Boolean = false
        ): String {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return MASTER_CHANNEL_ID
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return MASTER_CHANNEL_ID

            val isSilent = vibrationOnly || sound == "silent" || muteChannelSound
            val isYouTube = sound.startsWith("youtube://")
            val soundUri = if (isSilent || isYouTube) null else getSoundUri(context, sound)

            val soundKey = when {
                isSilent -> "silent"
                isYouTube -> "yt"
                sound == "gentle" || sound.isBlank() -> "gentle"
                sound == "digital" -> "digital"
                else -> "tone_" + sound.hashCode().toString().replace("-", "m")
            }
            val channelId = "chrono_channel_v6_${soundKey}_${if (vibrate) "vib" else "novib"}"

            val existing = notificationManager.getNotificationChannel(channelId)
            if (existing == null) {
                val alarmAudioAttrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    "Disparo de Alarmes",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alertas visuais e reprodução de som nativo de alta prioridade dos alarmes"
                    if (soundUri != null) {
                        setSound(soundUri, alarmAudioAttrs)
                    } else {
                        setSound(null, null)
                    }
                    enableVibration(vibrate)
                    if (vibrate) {
                        vibrationPattern = longArrayOf(0, 800, 400, 800, 400)
                    }
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                }
                notificationManager.createNotificationChannel(channel)
            }
            return channelId
        }

        fun createAlarmNotificationChannel(context: Context) {
            getOrCreateAlarmChannel(context, "gentle", vibrate = true, vibrationOnly = false)
        }

        fun cancelNotification(context: Context) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (_: Throwable) {}
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
            volume: Float,
            muteChannelSound: Boolean = shouldPlayAudioInPlayer(context, sound)
        ): Notification {
            val channelId = getOrCreateAlarmChannel(
                context = context,
                sound = sound,
                vibrate = vibrate,
                vibrationOnly = vibrationOnly,
                muteChannelSound = muteChannelSound
            )

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

            val builder = NotificationCompat.Builder(context, channelId)
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

            val notification = builder.build()
            // FLAG_INSISTENT: faz o Android SystemUI repetir o toque continuamente até ser cancelado!
            notification.flags = notification.flags or Notification.FLAG_INSISTENT or Notification.FLAG_ONGOING_EVENT
            return notification
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        try {
            when (action) {
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.LOCKED_BOOT_COMPLETED" -> {
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

                    // 1. Manter CPU ativa continuamente (com timeout de 10 min por segurança)
                    acquireStaticWakeLock(context)

                    // 2. Garantir volume do fluxo de alarme do sistema (STREAM_ALARM)
                    try {
                        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        if (am != null) {
                            val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                            val desiredVol = (maxVol * volume.coerceIn(0.1f, 1.0f)).toInt().coerceIn(1, maxVol)
                            am.setStreamVolume(AudioManager.STREAM_ALARM, desiredVol, 0)
                        }
                    } catch (_: Throwable) {}

                    val playInPlayer = shouldPlayAudioInPlayer(context, sound)

                    // 3. Exibir notificação em primeiro plano com prioridade máxima.
                    // Quando não há fone conectado, o canal nativo toca de forma insistente.
                    // Quando há fone conectado, o canal fica mudo para que o AlarmSoundPlayer
                    // force o som exclusivamente no alto-falante do celular (evitando som só no fone ou som duplo).
                    try {
                        val notification = buildAlarmNotification(
                            context = context,
                            alarmId = alarmId,
                            label = label,
                            sound = sound,
                            vibrate = vibrate,
                            vibrationOnly = vibrationOnly,
                            math = math,
                            snoozeMins = snoozeMins,
                            volume = volume,
                            muteChannelSound = playInPlayer
                        )
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        notificationManager?.notify(NOTIFICATION_ID, notification)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Erro ao emitir notificação no Receiver", t)
                    }

                    // 4. Disparar áudio pelo AlarmSoundPlayer:
                    // playAudioInPlayer é true para YouTube ou quando há fone conectado (forçando alto-falante do celular).
                    try {
                        AlarmSoundPlayer.play(context, sound, vibrate, vibrationOnly, volume, playAudioInPlayer = playInPlayer)
                    } catch (t: Throwable) {
                        Log.e(TAG, "Erro ao iniciar AlarmSoundPlayer no Receiver", t)
                    }

                    // 5. Tentar abrir a atividade em tela cheia diretamente (se o SO permitir)
                    try {
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
                        context.startActivity(ringingIntent)
                    } catch (_: Throwable) {
                        // Se a Xiaomi suprimir startActivity em segundo plano, a notificação com fullScreenIntent assegura a tela cheia
                    }

                    // 6. Acionar o AlarmService como Foreground Service (reforço para manter processo ativo)
                    try {
                        AlarmService.startAlarm(context, intent)
                    } catch (t: Throwable) {
                        Log.w(TAG, "AlarmService suprimido pelo SO (ex: Xiaomi em segundo plano)", t)
                    }
                }

                AlarmScheduler.ACTION_ALARM_DISMISS -> {
                    releaseStaticWakeLock()
                    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                    try { AlarmRingingActivity.finishIfActive(context) } catch (_: Throwable) {}
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
                    releaseStaticWakeLock()
                    val alarmId = intent.getLongExtra(AlarmScheduler.EXTRA_ALARM_ID, 0L)
                    val label = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_LABEL) ?: "Alarme"
                    val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                    val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                    val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                    val math = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_MATH, false)
                    val snoozeMins = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, 5)
                    val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.5f)

                    try { AlarmRingingActivity.finishIfActive(context) } catch (_: Throwable) {}
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
