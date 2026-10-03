package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.MainActivity
import com.example.data.model.AlarmEntity
import java.util.Calendar

object AlarmScheduler {

    const val ACTION_ALARM_TRIGGER = "com.example.ALARM_TRIGGER"
    const val ACTION_ALARM_DISMISS = "com.example.ALARM_DISMISS"
    const val ACTION_ALARM_SNOOZE = "com.example.ALARM_SNOOZE"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_ALARM_LABEL = "extra_alarm_label"
    const val EXTRA_ALARM_HOUR = "extra_alarm_hour"
    const val EXTRA_ALARM_MINUTE = "extra_alarm_minute"
    const val EXTRA_ALARM_SOUND = "extra_alarm_sound"
    const val EXTRA_ALARM_VIBRATE = "extra_alarm_vibrate"
    const val EXTRA_ALARM_VIBRATION_ONLY = "extra_alarm_vibration_only"
    const val EXTRA_ALARM_MATH = "extra_alarm_math"
    const val EXTRA_ALARM_SNOOZE_MINS = "extra_alarm_snooze_mins"
    const val EXTRA_ALARM_VOLUME = "extra_alarm_volume"

    fun scheduleAlarm(context: Context, alarm: AlarmEntity) {
        if (!alarm.isEnabled) {
            cancelAlarm(context, alarm)
            return
        }

        val triggerTime = calculateNextTriggerTime(alarm.hour, alarm.minute, alarm.getDaysList(), alarm.specificDateMillis)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_ALARM_HOUR, alarm.hour)
            putExtra(EXTRA_ALARM_MINUTE, alarm.minute)
            putExtra(EXTRA_ALARM_SOUND, alarm.soundTone)
            putExtra(EXTRA_ALARM_VIBRATE, alarm.vibrate)
            putExtra(EXTRA_ALARM_VIBRATION_ONLY, alarm.vibrationOnly)
            putExtra(EXTRA_ALARM_MATH, alarm.mathMission)
            putExtra(EXTRA_ALARM_SNOOZE_MINS, alarm.snoozeMinutes)
            putExtra(EXTRA_ALARM_VOLUME, alarm.volume)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show Intent when user taps alarm icon in lockscreen/status bar
        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.toInt() + 100000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun scheduleSnooze(
        context: Context,
        alarmId: Long,
        label: String,
        sound: String,
        vibrate: Boolean,
        math: Boolean,
        snoozeMinutes: Int,
        vibrationOnly: Boolean = false,
        volume: Float = 0.8f
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val triggerTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_LABEL, "$label (Soneca)")
            putExtra(EXTRA_ALARM_SOUND, sound)
            putExtra(EXTRA_ALARM_VIBRATE, vibrate)
            putExtra(EXTRA_ALARM_VIBRATION_ONLY, vibrationOnly)
            putExtra(EXTRA_ALARM_MATH, math)
            putExtra(EXTRA_ALARM_SNOOZE_MINS, snoozeMinutes)
            putExtra(EXTRA_ALARM_VOLUME, volume)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            (alarmId + 50000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val showPendingIntent = PendingIntent.getActivity(
            context,
            (alarmId + 150000).toInt(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setAlarmClock(
                        AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent),
                    pendingIntent
                )
            }
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelAlarm(context: Context, alarm: AlarmEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM_TRIGGER
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun calculateNextTriggerTime(hour: Int, minute: Int, daysOfWeek: List<Int>, specificDateMillis: Long? = null): Long {
        if (specificDateMillis != null) {
            val dateCal = Calendar.getInstance().apply {
                timeInMillis = specificDateMillis
            }
            val target = Calendar.getInstance().apply {
                set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return target.timeInMillis
        }

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (daysOfWeek.isEmpty()) {
            // One-time alarm
            if (target.timeInMillis <= now.timeInMillis) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis
        }

        // Repeating alarm: ISO days: 1=Monday, 2=Tuesday, ..., 7=Sunday
        // Calendar day: Sunday=1, Monday=2, Tuesday=3, Wednesday=4, Thursday=5, Friday=6, Saturday=7
        for (dayOffset in 0..7) {
            val candidate = (target.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val isoDay = when (candidate.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            if (isoDay in daysOfWeek) {
                if (dayOffset > 0 || candidate.timeInMillis > now.timeInMillis) {
                    return candidate.timeInMillis
                }
            }
        }

        target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    fun getTimeRemainingDescription(targetTimeMillis: Long): String {
        val diff = targetTimeMillis - System.currentTimeMillis()
        if (diff <= 0) return "agora"

        val totalMinutes = diff / (60 * 1000)
        val days = totalMinutes / (60 * 24)
        val remainingHoursAfterDays = (totalMinutes % (60 * 24)) / 60
        val remainingMinutes = totalMinutes % 60

        return when {
            days > 0 && remainingHoursAfterDays > 0 -> "em $days d e $remainingHoursAfterDays h"
            days > 0 -> "em $days d"
            remainingHoursAfterDays > 0 && remainingMinutes > 0 -> "em $remainingHoursAfterDays h e $remainingMinutes min"
            remainingHoursAfterDays > 0 -> "em $remainingHoursAfterDays h"
            remainingMinutes > 0 -> "em $remainingMinutes min"
            else -> "em menos de 1 min"
        }
    }
}
