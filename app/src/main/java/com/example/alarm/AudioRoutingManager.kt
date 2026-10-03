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

    private var originalSpeakerphoneState: Boolean? = null
    private var originalAudioMode: Int? = null

    /**
     * Checks if any Bluetooth or Wired audio device (headphones, earbugs, speaker) is currently connected.
     */
    fun checkConnectedDevices(context: Context): ConnectedAudioDevicesInfo {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return ConnectedAudioDevicesInfo(false, false, emptyList())

        var hasBt = false
        var hasWired = false
        val names = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                val devName = device.productName.toString().ifBlank { "Dispositivo de Áudio" }
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

        return ConnectedAudioDevicesInfo(
            hasBluetooth = hasBt,
            hasWiredHeadset = hasWired,
            deviceNames = names.distinct()
        )
    }

    /**
     * Creates AudioAttributes configured to force dual output (Internal Speaker + Headset/Bluetooth)
     * using USAGE_ALARM and FLAG_AUDIBILITY_ENFORCED.
     */
    fun createDualOutputAudioAttributes(): AudioAttributes {
        val builder = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
        } else {
            @Suppress("DEPRECATION")
            builder.setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
        }

        return builder.build()
    }

    /**
     * Enforces audio routing so that audio plays out of the phone's internal speaker
     * as well as any connected Bluetooth/wired headphones.
     */
    fun configureDualAudioOutput(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            @Suppress("DEPRECATION")
            if (originalSpeakerphoneState == null) {
                originalSpeakerphoneState = audioManager.isSpeakerphoneOn
            }
            if (originalAudioMode == null) {
                originalAudioMode = audioManager.mode
            }

            // Ensure mode is normal for alarm playback
            audioManager.mode = AudioManager.MODE_NORMAL

            // Enable speakerphone mode to force internal speaker output alongside connected bluetooth/headset
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true

            // Maximize alarm stream volume to guarantee audibility
            val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            if (currentVol < (maxAlarmVol * 0.7f)) {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (maxAlarmVol * 0.9f).toInt(), 0)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Restores original audio routing when the alarm is dismissed or stopped.
     */
    fun restoreAudioRouting(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            originalSpeakerphoneState?.let {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = it
            }
            originalAudioMode?.let {
                audioManager.mode = it
            }
        } catch (_: Exception) {
        } finally {
            originalSpeakerphoneState = null
            originalAudioMode = null
        }
    }
}
