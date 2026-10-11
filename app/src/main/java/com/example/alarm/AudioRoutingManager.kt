package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

data class ConnectedAudioDevicesInfo(
    val hasBluetooth: Boolean,
    val hasWiredHeadset: Boolean,
    val deviceNames: List<String>
) {
    val isExternalConnected: Boolean get() = hasBluetooth || hasWiredHeadset
}

object AudioRoutingManager {

    /**
     * Checks if any Bluetooth or Wired audio device (headphones, earbugs, speaker) is currently connected.
     * Wrapped in try-catch to prevent any SecurityException if BLUETOOTH_CONNECT is not granted on Android 12+.
     */
    fun checkConnectedDevices(context: Context): ConnectedAudioDevicesInfo {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return ConnectedAudioDevicesInfo(false, false, emptyList())

            var hasBt = false
            var hasWired = false
            val names = mutableListOf<String>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    for (device in devices) {
                        val devName = try {
                            device.productName?.toString()?.ifBlank { "Dispositivo de Áudio" } ?: "Dispositivo"
                        } catch (_: Throwable) {
                            "Dispositivo"
                        }

                        when (device.type) {
                            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                                hasBt = true
                                names.add("Bluetooth ($devName)")
                            }
                            AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                            AudioDeviceInfo.TYPE_USB_HEADSET -> {
                                hasWired = true
                                names.add("Fone de Ouvido ($devName)")
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            if (device.type == AudioDeviceInfo.TYPE_HEARING_AID) {
                                hasBt = true
                                names.add("Aparelho Auditivo ($devName)")
                            }
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET || device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                                hasBt = true
                                names.add("Bluetooth LE ($devName)")
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore SecurityException or any framework exception on Android 12+
                }
            } else {
                @Suppress("DEPRECATION")
                if (audioManager.isBluetoothA2dpOn) {
                    hasBt = true
                    names.add("Bluetooth")
                }
                @Suppress("DEPRECATION")
                if (audioManager.isWiredHeadsetOn) {
                    hasWired = true
                    names.add("Fone de Ouvido")
                }
            }

            ConnectedAudioDevicesInfo(
                hasBluetooth = hasBt,
                hasWiredHeadset = hasWired,
                deviceNames = names.distinct()
            )
        } catch (_: Throwable) {
            ConnectedAudioDevicesInfo(false, false, emptyList())
        }
    }

    /**
     * Creates AudioAttributes configured for alarm output using USAGE_ALARM.
     */
    fun createDualOutputAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /**
     * Enforces audio routing and adjusts the phone's hardware alarm stream volume
     * dynamically according to the volume percent chosen for the alarm.
     */
    fun configureDualAudioOutput(context: Context, volumePercent: Float = 0.8f) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val clampedPercent = volumePercent.coerceIn(0.05f, 1.0f)

            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val desiredStreamVol = kotlin.math.round(maxAlarmVol * clampedPercent).toInt().coerceIn(1, maxAlarmVol)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, desiredStreamVol, 0)

            try {
                val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val desiredMusicVol = kotlin.math.round(maxMusicVol * clampedPercent).toInt().coerceIn(1, maxMusicVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, desiredMusicVol, 0)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {
        }
    }

    /**
     * Restores original audio routing when the alarm is dismissed or stopped.
     */
    fun restoreAudioRouting(context: Context) {
        // No custom audio routing mutation to clean up
    }
}
