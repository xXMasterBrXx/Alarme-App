package com.example.alarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

data class SystemTone(
    val title: String,
    val uriString: String
)

object SystemRingtoneHelper {

    fun getAvailableTones(context: Context): List<SystemTone> {
        val list = mutableListOf<SystemTone>()

        // 1. Default system alarm tone
        try {
            val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            if (defaultAlarmUri != null) {
                val ringtone = RingtoneManager.getRingtone(context, defaultAlarmUri)
                val defaultTitle = ringtone?.getTitle(context) ?: "Padrão do Sistema"
                list.add(SystemTone(title = "Padrão ($defaultTitle)", uriString = defaultAlarmUri.toString()))
            }
        } catch (_: Exception) {
        }

        // 2. Query all device alarm and ringtone sounds
        try {
            val ringtoneManager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            }
            val cursor = ringtoneManager.cursor
            while (cursor != null && cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = ringtoneManager.getRingtoneUri(cursor.position)
                if (uri != null && !title.isNullOrBlank()) {
                    list.add(SystemTone(title = title, uriString = uri.toString()))
                }
            }
        } catch (_: Exception) {
        }

        // If list is empty (e.g. headless emulator with no media database), provide standard system defaults
        if (list.isEmpty()) {
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            list.add(SystemTone(title = "Alarme Padrão do Telefone", uriString = defaultUri?.toString() ?: "default"))
        }

        return list.distinctBy { it.uriString }
    }

    fun getToneTitle(context: Context, uriString: String): String {
        if (uriString.startsWith("youtube://")) {
            val parsed = com.example.data.youtube.YouTubeSoundService.parseSoundTone(uriString)
            return parsed?.second?.let { "▶️ $it" } ?: "YouTube Som"
        }

        if (uriString.isBlank() || uriString == "default" || uriString == "gentle") {
            return try {
                val defaultAlarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                val ringtone = defaultAlarmUri?.let { RingtoneManager.getRingtone(context, it) }
                ringtone?.getTitle(context) ?: "Padrão do Sistema"
            } catch (_: Exception) {
                "Padrão do Sistema"
            }
        }

        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Toque do Telefone"
        } catch (_: Exception) {
            "Toque do Telefone"
        }
    }
}
