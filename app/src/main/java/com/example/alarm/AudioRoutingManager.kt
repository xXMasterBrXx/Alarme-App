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
    private var hasModifiedRouting = false
    private var previousAudioMode: Int = AudioManager.MODE_NORMAL
    private var previousSpeakerphoneOn: Boolean = false
    private var previousBluetoothScoOn: Boolean = false
    private var previousVoiceCallVolume: Int = -1
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
     * Forces a MediaPlayer instance to output through the phone's built-in speaker (API 28+).
     */
    fun applyPreferredSpeakerDevice(context: Context, mediaPlayer: MediaPlayer) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val speaker = findBuiltInSpeaker(context)
                if (speaker != null) {
                    mediaPlayer.setPreferredDevice(speaker)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Falha ao definir preferredDevice no MediaPlayer", t)
            }
        }
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
     * Creates AudioAttributes configured for alarm output.
     * When [forceBuiltInSpeaker] is true (headphones/Bluetooth connected), uses USAGE_VOICE_COMMUNICATION
     * paired with MODE_IN_COMMUNICATION + setCommunicationDevice(TYPE_BUILTIN_SPEAKER) so Android's
     * AudioPolicyManager strictly routes audio out of the phone's built-in loudspeaker instead of headphones.
     */
    fun createAlarmAudioAttributes(forceBuiltInSpeaker: Boolean = false): AudioAttributes {
        return if (forceBuiltInSpeaker) {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        } else {
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        }
    }

    /**
     * Kept for backwards compatibility with callers expecting USAGE_ALARM attributes.
     */
    fun createDualOutputAudioAttributes(): AudioAttributes = createAlarmAudioAttributes(false)

    /**
     * Enforces audio routing to the phone's built-in speaker when headphones/Bluetooth are connected,
     * requests exclusive transient audio focus, and sets hardware volumes according to [volumePercent].
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

            // 1. Pausar músicas/vídeos ativos solicitando foco exclusivo de áudio
            requestAlarmAudioFocus(audioManager, forceBuiltInSpeaker)

            // 2. Ajustar volume do fluxo de alarme
            try {
                val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                val desiredStreamVol = kotlin.math.round(maxAlarmVol * clampedPercent).toInt().coerceIn(1, maxAlarmVol)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, desiredStreamVol, 0)
            } catch (_: Throwable) {}

            // 3. Se fone de ouvido (com fio, USB ou Bluetooth) estiver conectado,
            // força o roteamento de hardware diretamente para o alto-falante embutido do celular
            if (forceBuiltInSpeaker) {
                if (!hasModifiedRouting) {
                    previousAudioMode = audioManager.mode
                    @Suppress("DEPRECATION")
                    previousSpeakerphoneOn = audioManager.isSpeakerphoneOn
                    @Suppress("DEPRECATION")
                    previousBluetoothScoOn = audioManager.isBluetoothScoOn
                    previousVoiceCallVolume = try {
                        audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    } catch (_: Throwable) { -1 }
                    hasModifiedRouting = true
                }

                try {
                    audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha ao definir MODE_IN_COMMUNICATION", t)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        val speakerCommDevice = audioManager.availableCommunicationDevices
                            .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                            ?: findBuiltInSpeaker(context)
                        if (speakerCommDevice != null) {
                            audioManager.setCommunicationDevice(speakerCommDevice)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Falha em setCommunicationDevice, usando fallback isSpeakerphoneOn", t)
                        enableLegacySpeakerphone(audioManager)
                    }
                } else {
                    enableLegacySpeakerphone(audioManager)
                }

                // Garante volume audível no fluxo de viva-voz enquanto o alarme toca no alto-falante
                try {
                    val maxVoiceVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    val desiredVoiceVol = kotlin.math.round(maxVoiceVol * clampedPercent).toInt().coerceIn(1, maxVoiceVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, desiredVoiceVol, 0)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Erro ao configurar roteamento de áudio", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun enableLegacySpeakerphone(audioManager: AudioManager) {
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.isSpeakerphoneOn = true
        } catch (_: Throwable) {}
    }

    private fun requestAlarmAudioFocus(audioManager: AudioManager, forceBuiltInSpeaker: Boolean) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeFocusRequest?.let {
                    try { audioManager.abandonAudioFocusRequest(it) } catch (_: Throwable) {}
                }
                val attrs = createAlarmAudioAttributes(forceBuiltInSpeaker)
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
                    if (forceBuiltInSpeaker) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
        } catch (_: Throwable) {}
    }

    /**
     * Restores original audio routing, speakerphone state, voice volume, and audio focus when the alarm stops.
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

            if (hasModifiedRouting) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        audioManager.clearCommunicationDevice()
                    } catch (_: Throwable) {}
                }

                @Suppress("DEPRECATION")
                try {
                    audioManager.isSpeakerphoneOn = previousSpeakerphoneOn
                    audioManager.isBluetoothScoOn = previousBluetoothScoOn
                } catch (_: Throwable) {}

                try {
                    audioManager.mode = previousAudioMode
                } catch (_: Throwable) {}

                if (previousVoiceCallVolume >= 0) {
                    try {
                        audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, previousVoiceCallVolume, 0)
                    } catch (_: Throwable) {}
                }

                hasModifiedRouting = false
                previousVoiceCallVolume = -1
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Erro ao restaurar roteamento de áudio", t)
        }
    }
}
