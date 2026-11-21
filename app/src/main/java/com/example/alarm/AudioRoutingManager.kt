package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log

data class ConnectedAudioDevicesInfo(
    val hasBluetooth: Boolean,
    val hasWiredHeadset: Boolean,
    val deviceNames: List<String>
) {
    val isExternalConnected: Boolean get() = hasBluetooth || hasWiredHeadset
}

object AudioRoutingManager {

    private const val TAG = "AudioRoutingManager"

    @Volatile
    private var hasSavedMusicVolume = false
    private var previousMusicVolume: Int = -1
    private var activeFocusRequest: AudioFocusRequest? = null

    /**
     * Checks if any Bluetooth or Wired audio device (headphones, earbuds, speaker, USB headset) is currently connected.
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
                            AudioDeviceInfo.TYPE_USB_HEADSET,
                            AudioDeviceInfo.TYPE_USB_DEVICE -> {
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
     * Returns the built-in loudspeaker AudioDeviceInfo when available (API 23+).
     */
    fun findBuiltInSpeaker(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Returns the primary connected external headphone/Bluetooth AudioDeviceInfo (API 23+).
     */
    fun findExternalOutputDevice(context: Context): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outputs.firstOrNull { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE -> true
                    else -> (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                            (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET || device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)) ||
                            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                                    device.type == AudioDeviceInfo.TYPE_HEARING_AID)
                }
            } ?: outputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Forces a MediaPlayer instance to output through the phone's built-in speaker (API 28+).
     */
    fun applyPreferredSpeakerDevice(context: Context, mediaPlayer: MediaPlayer): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val speaker = findBuiltInSpeaker(context)
                if (speaker != null) {
                    return mediaPlayer.setPreferredDevice(speaker)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao definir preferredDevice (speaker) no MediaPlayer", t)
            }
        }
        return false
    }

    /**
     * Forces a MediaPlayer instance to output through the connected external headphone/Bluetooth device (API 28+).
     */
    fun applyPreferredExternalDevice(context: Context, mediaPlayer: MediaPlayer): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val external = findExternalOutputDevice(context)
                if (external != null) {
                    return mediaPlayer.setPreferredDevice(external)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao definir preferredDevice (fone) no MediaPlayer", t)
            }
        }
        return false
    }

    /**
     * Forces an AudioTrack instance to output through the phone's built-in speaker (API 23+).
     */
    fun applyPreferredSpeakerDevice(context: Context, audioTrack: AudioTrack) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val speaker = findBuiltInSpeaker(context)
                if (speaker != null) {
                    audioTrack.setPreferredDevice(speaker)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao definir preferredDevice no AudioTrack", t)
            }
        }
    }

    /**
     * Creates AudioAttributes configured for alarm output on the phone's built-in speaker.
     * Uses USAGE_ALARM in MODE_NORMAL so it bypasses silent/DND and plays cleanly alongside headphone audio.
     */
    fun createAlarmAudioAttributes(forceBuiltInSpeaker: Boolean = false): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    /**
     * Creates AudioAttributes configured for simultaneous output on connected headphones/Bluetooth.
     */
    fun createHeadphoneOutputAudioAttributes(): AudioAttributes {
        return AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }

    /**
     * Kept for backwards compatibility with callers expecting USAGE_ALARM attributes.
     */
    fun createDualOutputAudioAttributes(): AudioAttributes = createAlarmAudioAttributes(false)

    /**
     * Prepares audio streams and focus for simultaneous speaker + headphone output in MODE_NORMAL.
     * Keeps MODE_NORMAL so both the built-in speaker (USAGE_ALARM) and connected Bluetooth/wired
     * headphones (USAGE_MEDIA) can play together simultaneously.
     */
    @Synchronized
    fun configureDualAudioOutput(
        context: Context,
        volumePercent: Float = 0.8f,
        forceBuiltInSpeaker: Boolean = checkConnectedDevices(context).isExternalConnected
    ) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val clampedPercent = volumePercent.coerceIn(0.05f, 1.0f)

            // 1. Garante MODE_NORMAL para permitir reprodução simultânea no alto-falante e no fone/Bluetooth
            try {
                if (audioManager.mode != AudioManager.MODE_NORMAL) {
                    audioManager.mode = AudioManager.MODE_NORMAL
                }
            } catch (_: Throwable) {}

            // 2. Pausar músicas/vídeos ativos solicitando foco exclusivo de áudio
            requestAlarmAudioFocus(audioManager)

            // 3. Ajustar volume do fluxo de alarme (alto-falante do celular)
            try {
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val desiredStreamVol = kotlin.math.round(maxAlarmVol * clampedPercent).toInt().coerceIn(1, maxAlarmVol)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, desiredStreamVol, 0)
            } catch (_: Throwable) {}

            // 4. Se houver fone conectado, ajustar também o volume do fluxo de mídia para que o toque saia audível no fone
            // (salvando o volume original para restaurar ao desligar/adiar o alarme)
            if (forceBuiltInSpeaker) {
                try {
                    if (!hasSavedMusicVolume) {
                        previousMusicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        hasSavedMusicVolume = true
                    }
                    val maxMusicVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val desiredMusicVol = kotlin.math.round(maxMusicVol * clampedPercent).toInt().coerceIn(1, maxMusicVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, desiredMusicVol, 0)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro ao configurar roteamento de áudio duplo", t)
        }
    }

    private fun requestAlarmAudioFocus(audioManager: AudioManager) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let {
                    try { audioManager.abandonAudioFocusRequest(it) } catch (_: Throwable) {}
                }
                val attrs = createAlarmAudioAttributes(false)
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .build()
                activeFocusRequest = req
                audioManager.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (_: Throwable) {}
    }

    /**
     * Restores original music stream volume and audio focus when the alarm stops.
     */
    @Synchronized
    fun restoreAudioRouting(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let {
                    try { audioManager.abandonAudioFocusRequest(it) } catch (_: Throwable) {}
                }
                activeFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }

            if (hasSavedMusicVolume && previousMusicVolume >= 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousMusicVolume, 0)
                } catch (_: Throwable) {}
                hasSavedMusicVolume = false
                previousMusicVolume = -1
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Erro ao restaurar roteamento de áudio", t)
        }
    }
}
