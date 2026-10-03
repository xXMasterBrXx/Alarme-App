package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String = "Alarme",
    val isEnabled: Boolean = true,
    // Comma-separated ISO day numbers (1=Segunda, 2=Terça, ..., 7=Domingo), empty string for one-time
    val daysOfWeek: String = "",
    val vibrate: Boolean = true,
    val vibrationOnly: Boolean = false,
    val snoozeMinutes: Int = 10,
    val soundTone: String = "gentle", // gentle, radar, birds, classic, cosmic
    val mathMission: Boolean = false,
    val volume: Float = 0.8f, // 0.1f .. 1.0f
    val specificDateMillis: Long? = null // Specific calendar date if selected
) {
    fun getFormattedTime(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun isRepeating(): Boolean = daysOfWeek.isNotBlank() && specificDateMillis == null

    fun getDaysList(): List<Int> {
        if (daysOfWeek.isBlank() || specificDateMillis != null) return emptyList()
        return daysOfWeek.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
    }

    fun getDaysDescription(): String {
        if (specificDateMillis != null) {
            val sdf = SimpleDateFormat("d 'de' MMMM", Locale("pt", "BR"))
            return "📅 ${sdf.format(Date(specificDateMillis))}"
        }

        val days = getDaysList()
        if (days.isEmpty()) return "Uma vez"
        if (days.size == 7) return "Todos os dias"
        if (days.sorted() == listOf(1, 2, 3, 4, 5)) return "Dias úteis (Seg - Sex)"
        if (days.sorted() == listOf(6, 7)) return "Fim de semana (Sáb - Dom)"

        val names = mapOf(
            1 to "Seg",
            2 to "Ter",
            3 to "Qua",
            4 to "Qui",
            5 to "Sex",
            6 to "Sáb",
            7 to "Dom"
        )
        return days.sorted().mapNotNull { names[it] }.joinToString(", ")
    }
}
