package com.example

import com.example.alarm.AlarmScheduler
import com.example.data.model.AlarmEntity
import com.example.ui.theme.TimePeriod
import com.example.ui.theme.getTimePeriodForHour
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testTimePeriodDetection() {
    assertEquals(TimePeriod.DAWN, getTimePeriodForHour(5))
    assertEquals(TimePeriod.DAWN, getTimePeriodForHour(7))
    assertEquals(TimePeriod.DAY, getTimePeriodForHour(8))
    assertEquals(TimePeriod.DAY, getTimePeriodForHour(12))
    assertEquals(TimePeriod.DAY, getTimePeriodForHour(16))
    assertEquals(TimePeriod.SUNSET, getTimePeriodForHour(17))
    assertEquals(TimePeriod.SUNSET, getTimePeriodForHour(19))
    assertEquals(TimePeriod.NIGHT, getTimePeriodForHour(20))
    assertEquals(TimePeriod.NIGHT, getTimePeriodForHour(23))
    assertEquals(TimePeriod.NIGHT, getTimePeriodForHour(2))
  }

  @Test
  fun testAlarmEntityFormatting() {
    val alarm = AlarmEntity(
      hour = 7,
      minute = 5,
      label = "Acordar",
      daysOfWeek = "1,2,3,4,5",
      vibrationOnly = true
    )
    assertEquals("07:05", alarm.getFormattedTime())
    assertTrue(alarm.isRepeating())
    assertTrue(alarm.vibrationOnly)
    assertEquals(listOf(1, 2, 3, 4, 5), alarm.getDaysList())
    assertEquals("Dias úteis (Seg - Sex)", alarm.getDaysDescription())
  }
}

