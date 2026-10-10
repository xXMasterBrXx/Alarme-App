package com.example.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.R

class AlarmService : Service() {

    companion object {
        const val ACTION_START_ALARM = "com.example.ACTION_START_ALARM"
        const val ACTION_STOP_ALARM = "com.example.ACTION_STOP_ALARM"
        private const val TAG = "AlarmService"

        fun startAlarm(context: Context, intent: Intent) {
            // No-op by default to prevent ForegroundServiceStartNotAllowedException on Android 14.
            // AlarmReceiver handles playback and notification directly.
            try {
                val serviceIntent = Intent(context, AlarmService::class.java).apply {
                    action = ACTION_START_ALARM
                    putExtras(intent)
                }
                context.startService(serviceIntent)
            } catch (t: Throwable) {
                Log.w(TAG, "startService suprimido pelo SO", t)
            }
        }

        fun stopAlarm(context: Context) {
            try {
                AlarmSoundPlayer.stop(context)
                val serviceIntent = Intent(context, AlarmService::class.java)
                context.stopService(serviceIntent)
            } catch (_: Throwable) {}
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
                val sound = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_SOUND) ?: "gentle"
                val vibrate = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, true)
                val vibrationOnly = intent.getBooleanExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, false)
                val volume = intent.getFloatExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, 0.8f)

                acquireWakeLock()

                try {
                    AlarmSoundPlayer.play(this, sound, vibrate, vibrationOnly, volume)
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro ao iniciar AlarmSoundPlayer em AlarmService", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro em onStartCommand de AlarmService", t)
        }

        return START_NOT_STICKY
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Chrono:AlarmServiceWakeLock"
                )
                wakeLock?.acquire(5 * 60 * 1000L) // 5 minutes
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
