package com.example.alarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.example.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"
        private const val TAG = "AlarmService"

        fun startAlarm(context: Context, intent: Intent) {
            try {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = ACTION_START_ALARM
                    putExtras(intent)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            } catch (t: Throwable) {
                Log.e(TAG, "Falha ao iniciar ForegroundService em AlarmService", t)
                // Fallback de emergência caso o sistema restrinja o serviço
                try {
                    val snd = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                    val vib = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                    val vibOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                    val vol = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)
                    AlarmSoundPlayer.play(context, snd, vib, vibOnly, vol)
                } catch (_: Throwable) {}
            }
        }

        fun stopAlarm(context: Context) {
            try {
                AlarmSoundPlayer.stop(context)
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                context.startService(serviceIntent)
            } catch (_: Throwable) {
                try {
                    val serviceIntent = Intent(context, AlarmService::class.java)
                    context.stopService(serviceIntent)
                } catch (_: Throwable) {}
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        try {
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

                // 1. Manter CPU ativa continuamente
                acquireWakeLock()

                // 2. Construir e fixar imediatamente notificação em primeiro plano
                val notification = AlarmReceiver.buildAlarmNotification(
                    context = this,
                    alarmId = alarmId,
                    label = label,
                    sound = sound,
                    vibrate = vibrate,
                    vibrationOnly = vibrationOnly,
                    math = math,
                    snoozeMins = snoozeMins,
                    volume = volume
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        AlarmReceiver.NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(AlarmReceiver.NOTIFICATION_ID, notification)
                }

                // Libera o WakeLock temporário do AlarmReceiver
                AlarmReceiver.releaseStaticWakeLock()

                // 3. Tocar áudio e vibração com sistema à prova de falhas
                try {
                    AlarmSoundPlayer.play(this, sound, vibrate, vibrationOnly, volume)
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro ao iniciar AlarmSoundPlayer em AlarmService", t)
                }

                // 4. Exibir popup flutuante de sobreposição se autorizado
                try {
                    AlarmOverlayManager.showOverlay(
                        context = this,
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

                // 5. Iniciar atividade em tela cheia diretamente
                try {
                    val ringingIntent = Intent(this, AlarmRingingActivity::class.java).apply {
                        setFlags(
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
                    startActivity(ringingIntent)
                } catch (t: Throwable) {
                    Log.w(TAG, "startActivity suprimido pelo SO, tela cheia assegurada pela notificação", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro em onStartCommand de AlarmService", t)
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "Chrono:AlarmServiceWakeLock"
                )
                wakeLock?.acquire(10 * 60 * 1000L) // 10 minutos
            }
        } catch (_: Throwable) {}
    }

    private fun stopAlarmInternal() {
        try {
            AlarmSoundPlayer.stop(this)
            AlarmOverlayManager.dismissOverlay()
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(AlarmReceiver.NOTIFICATION_ID)
            stopSelf()
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        } catch (_: Throwable) {}
        super.onDestroy()
    }
}
