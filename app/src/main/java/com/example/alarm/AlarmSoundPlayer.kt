package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.data.youtube.YouTubeAudioEngine
import com.example.data.youtube.YouTubeSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var previewRingtone: Ringtone? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var synthJob: Job? = null
    private var previewJob: Job? = null
    private var isPlaying = false

    fun play(
        context: Context,
        soundTone: String,
        shouldVibrate: Boolean,
        vibrationOnly: Boolean = false,
        volume: Float = 0.8f
    ) {
        stop()
        isPlaying = true

        // Configure dual audio routing (Forces internal speaker output alongside connected Bluetooth/Headsets)
        AudioRoutingManager.configureDualAudioOutput(context)
        val devicesInfo = AudioRoutingManager.checkConnectedDevices(context)

        // 1. Handle vibration
        if (shouldVibrate || vibrationOnly) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 600, 400, 600, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (_: Exception) {
            }
        }

        // 2. Play audio (skip if vibration-only mode is active)
        if (vibrationOnly || soundTone == "silent") {
            return
        }

        val targetVolume = volume.coerceIn(0.05f, 1.0f)

        // YouTube sound playback via official embedded HTML5 audio engine
        if (soundTone.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(soundTone)
            if (parsed != null) {
                YouTubeAudioEngine.playAlarm(context, parsed.first, targetVolume)
                if (devicesInfo.isExternalConnected) {
                    // When Bluetooth/headsets are connected, also play a concurrent speaker alarm chime
                    playSynthesizedTone(soundTone, loop = true, volume = targetVolume)
                }
                return
            }
        }

        // Resolve phone ringtone URI
        val uri: Uri? = if (soundTone.startsWith("content://") || soundTone.startsWith("android.resource://")) {
            Uri.parse(soundTone)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        var played = false

        if (uri != null) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(AudioRoutingManager.createDualOutputAudioAttributes())
                    setVolume(targetVolume, targetVolume)
                    isLooping = true
                    prepare()
                    start()
                }
                mediaPlayer = mp
                played = true
            } catch (_: Exception) {
                played = false
            }
        }

        // Fallback to pleasant melody if MediaPlayer fails
        if (!played) {
            playSynthesizedTone(soundTone, loop = true, volume = targetVolume)
        }
    }

    fun playPreview(
        context: Context,
        uriString: String,
        volume: Float = 0.8f,
        onFinished: () -> Unit = {}
    ) {
        stopPreview()
        AudioRoutingManager.configureDualAudioOutput(context)

        val targetVolume = volume.coerceIn(0.05f, 1.0f)

        // YouTube preview via official embedded HTML5 audio engine
        if (uriString.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(uriString)
            if (parsed != null) {
                YouTubeAudioEngine.playPreview(context, parsed.first, targetVolume, durationSeconds = 12) {
                    onFinished()
                }
                return
            }
        }

        val uri: Uri? = if (uriString.startsWith("content://") || uriString.startsWith("android.resource://")) {
            Uri.parse(uriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        }

        previewJob = CoroutineScope(Dispatchers.Main).launch {
            var isAudioPlaying = false

            // Try RingtoneManager first
            try {
                if (uri != null) {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    if (ringtone != null) {
                        ringtone.audioAttributes = AudioRoutingManager.createDualOutputAudioAttributes()
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone.volume = targetVolume
                        }
                        ringtone.play()
                        previewRingtone = ringtone
                        isAudioPlaying = true
                    }
                }
            } catch (_: Exception) {
                isAudioPlaying = false
            }

            // Fallback to MediaPlayer
            if (!isAudioPlaying && uri != null) {
                try {
                    val mp = MediaPlayer().apply {
                        setDataSource(context, uri)
                        setAudioAttributes(AudioRoutingManager.createDualOutputAudioAttributes())
                        setVolume(targetVolume, targetVolume)
                        prepare()
                        start()
                    }
                    previewMediaPlayer = mp
                    isAudioPlaying = true
                } catch (_: Exception) {
                    isAudioPlaying = false
                }
            }

            // Fallback to synth if device has no media audio available
            if (!isAudioPlaying) {
                withContext(Dispatchers.Default) {
                    playSynthesizedTone(uriString, loop = false, volume = targetVolume)
                }
            }

            // Let preview play for 3.5 seconds
            delay(3500L)

            stopPreview()
            onFinished()
        }
    }

    fun stopPreview() {
        YouTubeAudioEngine.stopPreview()
        previewJob?.cancel()
        previewJob = null

        try {
            previewRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (_: Exception) {
        } finally {
            previewRingtone = null
        }

        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            previewMediaPlayer = null
        }
    }

    private fun playSynthesizedTone(toneType: String, loop: Boolean, volume: Float = 0.8f) {
        synthJob?.cancel()
        synthJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 44100
            val frequencies = listOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6 (Major chime)

            do {
                for (freq in frequencies) {
                    if (!isActive || (!isPlaying && !loop)) break
                    playTone(freq, 220, sampleRate, volume)
                    delay(70)
                }
                delay(500)
            } while (isActive && (isPlaying || loop))
        }
    }

    private fun playTone(freq: Double, durationMs: Int, sampleRate: Int, volume: Float = 0.8f) {
        val numSamples = (durationMs * sampleRate) / 1000
        val generatedSnd = ByteArray(2 * numSamples)
        val gain = volume.coerceIn(0.05f, 1.0f)

        for (i in 0 until numSamples) {
            val dVal = sin(2.0 * PI * i.toDouble() / (sampleRate.toDouble() / freq))
            val envelope = when {
                i < numSamples * 0.15 -> i.toDouble() / (numSamples * 0.15)
                i > numSamples * 0.8 -> (numSamples - i).toDouble() / (numSamples * 0.2)
                else -> 1.0
            }
            val sample = (dVal * envelope * 24000 * gain).toInt().toShort()
            generatedSnd[2 * i] = (sample.toInt() and 0x00ff).toByte()
            generatedSnd[2 * i + 1] = ((sample.toInt() and 0xff00) ushr 8).toByte()
        }

        try {
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(AudioRoutingManager.createDualOutputAudioAttributes())
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(generatedSnd.size)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(generatedSnd, 0, generatedSnd.size)
            audioTrack.play()
            Thread.sleep(durationMs.toLong())
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Exception) {
        }
    }

    fun stop(context: Context? = null) {
        isPlaying = false
        YouTubeAudioEngine.stopAlarm()
        stopPreview()
        context?.let { AudioRoutingManager.restoreAudioRouting(it) }
        synthJob?.cancel()
        synthJob = null

        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            vibrator = null
        }
    }
}
