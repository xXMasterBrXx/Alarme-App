package com.example.ui.alarm

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.alarm.AlarmReceiver
import com.example.alarm.AlarmScheduler
import com.example.data.local.AppDatabase
import com.example.data.model.AlarmEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val alarmDao = AppDatabase.getDatabase(application).alarmDao()

    val alarms: StateFlow<List<AlarmEntity>> = alarmDao.getAllAlarms()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Pre-populate default sample alarms ONLY on the very first install run
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
            val alreadyInitialized = prefs.getBoolean("alarms_init_done", false)

            if (!alreadyInitialized) {
                prefs.edit().putBoolean("alarms_init_done", true).apply()
                val totalCount = alarmDao.getAlarmCount()
                if (totalCount == 0) {
                    val sampleAlarms = listOf(
                        AlarmEntity(
                            hour = 7,
                            minute = 0,
                            label = "Despertar Matinal",
                            isEnabled = true,
                            daysOfWeek = "1,2,3,4,5", // Weekdays
                            vibrate = true,
                            soundTone = "default",
                            snoozeMinutes = 10
                        ),
                        AlarmEntity(
                            hour = 8,
                            minute = 30,
                            label = "Treino / Corrida",
                            isEnabled = false,
                            daysOfWeek = "6,7", // Weekend
                            vibrate = true,
                            vibrationOnly = true,
                            soundTone = "default",
                            snoozeMinutes = 5
                        ),
                        AlarmEntity(
                            hour = 22,
                            minute = 45,
                            label = "Rotina Noturna & Sono",
                            isEnabled = true,
                            daysOfWeek = "1,2,3,4,5,6,7",
                            vibrate = false,
                            soundTone = "default",
                            snoozeMinutes = 15
                        )
                    )
                    for (a in sampleAlarms) {
                        val id = alarmDao.insertAlarm(a)
                        if (a.isEnabled) {
                            AlarmScheduler.scheduleAlarm(application, a.copy(id = id))
                        }
                    }
                }
            }
        }
    }

    fun saveAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            if (alarm.id == 0L) {
                val newId = alarmDao.insertAlarm(alarm)
                val savedAlarm = alarm.copy(id = newId)
                if (savedAlarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(getApplication(), savedAlarm)
                }
            } else {
                alarmDao.updateAlarm(alarm)
                if (alarm.isEnabled) {
                    AlarmScheduler.scheduleAlarm(getApplication(), alarm)
                } else {
                    AlarmScheduler.cancelAlarm(getApplication(), alarm)
                }
            }
        }
    }

    fun toggleAlarm(alarm: AlarmEntity, isEnabled: Boolean) {
        viewModelScope.launch {
            val updated = alarm.copy(isEnabled = isEnabled)
            alarmDao.updateAlarm(updated)
            if (isEnabled) {
                AlarmScheduler.scheduleAlarm(getApplication(), updated)
            } else {
                AlarmScheduler.cancelAlarm(getApplication(), updated)
            }
        }
    }

    fun deleteAlarm(alarm: AlarmEntity) {
        viewModelScope.launch {
            val prefs = getApplication<Application>().getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("alarms_init_done", true).apply()
            AlarmScheduler.cancelAlarm(getApplication(), alarm)
            alarmDao.deleteAlarm(alarm)
        }
    }

    fun testAlarmImmediately(context: Context, alarm: AlarmEntity) {
        val triggerIntent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_ALARM_TRIGGER
            putExtra(AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
            putExtra(AlarmScheduler.EXTRA_ALARM_LABEL, "${alarm.label} (Teste)")
            putExtra(AlarmScheduler.EXTRA_ALARM_SOUND, alarm.soundTone)
            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATE, alarm.vibrate)
            putExtra(AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, alarm.vibrationOnly)
            putExtra(AlarmScheduler.EXTRA_ALARM_MATH, alarm.mathMission)
            putExtra(AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, alarm.snoozeMinutes)
            putExtra(AlarmScheduler.EXTRA_ALARM_VOLUME, alarm.volume)
        }
        context.sendBroadcast(triggerIntent)
    }

    fun getNextUpcomingAlarmInfo(alarmsList: List<AlarmEntity>): Pair<AlarmEntity, String>? {
        val activeAlarms = alarmsList.filter { it.isEnabled }
        if (activeAlarms.isEmpty()) return null

        var nearestAlarm: AlarmEntity? = null
        var nearestTime = Long.MAX_VALUE

        for (alarm in activeAlarms) {
            val trigger = AlarmScheduler.calculateNextTriggerTime(alarm.hour, alarm.minute, alarm.getDaysList(), alarm.specificDateMillis)
            if (trigger < nearestTime) {
                nearestTime = trigger
                nearestAlarm = alarm
            }
        }

        return nearestAlarm?.let {
            Pair(it, AlarmScheduler.getTimeRemainingDescription(nearestTime))
        }
    }
}
