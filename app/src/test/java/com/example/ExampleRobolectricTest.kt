package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Chrono Relógio", appName)
  }

  @Test
  fun `test alarm trigger immediately does not crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val alarm = AlarmEntity(id = 1L, hour = 7, minute = 30, label = "Teste", isEnabled = true)
    val intent = android.content.Intent(context, com.example.alarm.AlarmReceiver::class.java).apply {
      action = com.example.alarm.AlarmScheduler.ACTION_ALARM_TRIGGER
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_ID, alarm.id)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_LABEL, "${alarm.label} (Teste)")
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_HOUR, alarm.hour)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_MINUTE, alarm.minute)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_SOUND, alarm.soundTone)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_VIBRATE, alarm.vibrate)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_VIBRATION_ONLY, alarm.vibrationOnly)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_MATH, alarm.mathMission)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_SNOOZE_MINS, alarm.snoozeMinutes)
      putExtra(com.example.alarm.AlarmScheduler.EXTRA_ALARM_VOLUME, alarm.volume)
    }
    com.example.alarm.AlarmReceiver().onReceive(context, intent)
  }

  @Test
  fun `test triggerAlarmImmediately direct method does not crash`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val alarm = AlarmEntity(id = 2L, hour = 8, minute = 0, label = "Teste Direto", isEnabled = true)
    AlarmScheduler.triggerAlarmImmediately(context, alarm)
  }
}

