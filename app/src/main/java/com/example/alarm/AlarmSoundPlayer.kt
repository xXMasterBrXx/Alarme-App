package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.example.data.youtube.YouTubeAudioEngine
import com.example.data.youtube.YouTubeSoundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null
    private var ringtoneLoopJob: Job? = null
    private var previewRingtone: Ringtone? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var synthJob: Job? = null
    private var streamingAudioTrack: AudioTrack? = null
    private var previewJob: Job? = null
    @Volatile
    var isPlaying = false
        private set

    fun play(
        context: Context,
        soundTone: String,
        shouldVibrate: Boolean,
        vibrationOnly: Boolean = false,
        volume: Float = 0.8f
    ) {
        if (isPlaying) {
            return
        }
        stop()
        isPlaying = true

        AudioRoutingManager.configureDualAudioOutput(context)

        val alarmAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // 1. Continuous repeating vibration
        if (shouldVibrate || vibrationOnly) {
            try {
                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }

                val pattern = longArrayOf(0, 800, 400, 800, 400)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAudioAttrs)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            } catch (_: Exception) {
            }
        }

        // 2. Play Audio if not vibration-only or silent
        if (vibrationOnly || soundTone == "silent") {
            return
        }

        val targetVolume = volume.coerceIn(0.1f, 1.0f)

        // YouTube audio playback
        if (soundTone.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(soundTone)
            if (parsed != null) {
                YouTubeAudioEngine.playAlarm(context, parsed.first, targetVolume)
                return
            }
        }

        // Resolve URI
        val uri: Uri? = if (soundTone.startsWith("content://") || soundTone.startsWith("android.resource://")) {
            Uri.parse(soundTone)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        }

        var played = false

        // Primary: MediaPlayer on USAGE_ALARM / STREAM_ALARM with isLooping = true
        if (uri != null) {
            try {
                val mp = MediaPlayer().apply {
                    setDataSource(context, uri)
                    setAudioAttributes(alarmAudioAttrs)
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

        // Secondary fallback: RingtoneManager
        if (!played && uri != null) {
            try {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                if (ringtone != null) {
                    ringtone.audioAttributes = alarmAudioAttrs
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ringtone.isLooping = true
                        ringtone.volume = targetVolume
                    }
                    ringtone.play()
                    activeRingtone = ringtone
                    played = true

                    ringtoneLoopJob?.cancel()
                    ringtoneLoopJob = CoroutineScope(Dispatchers.Main).launch {
                        while (isActive && isPlaying) {
                            delay(2000L)
                            try {
                                if (isPlaying && activeRingtone?.isPlaying == false) {
                                    activeRingtone?.play()
                                }
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                played = false
            }
        }

        // Final fallback: Synthesized alarm chime stream (Guaranteed to work without files or permissions)
        if (!played) {
            playSynthesizedTone(loop = true, volume = targetVolume)
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

        val targetVolume = volume.coerceIn(0.1f, 1.0f)

        if (uriString.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(uriString)
            if (parsed != null) {
                YouTubeAudioEngine.playPreview(context, parsed.first, targetVolume, durationSeconds = 10) {
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

            if (uri != null) {
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

            if (!isAudioPlaying && uri != null) {
                try {
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
                } catch (_: Exception) {
                    isAudioPlaying = false
                }
            }

            if (!isAudioPlaying) {
                playSynthesizedTone(loop = false, volume = targetVolume)
            }

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

    private fun playSynthesizedTone(loop: Boolean, volume: Float = 0.8f) {
        synthJob?.cancel()
        synthJob = CoroutineScope(Dispatchers.Default).launch {
            val sampleRate = 22050
            val frequencies = listOf(587.33, 739.99, 880.00, 1174.66) // D5, F#5, A5, D6 joyful alarm chord
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            streamingAudioTrack = track
            try {
                track.play()
                val gain = volume.coerceIn(0.1f, 1.0f)

                do {
                    for (freq in frequencies) {
                        if (!isActive || (!isPlaying && !loop)) break
                        val durationMs = 200
                        val numSamples = (durationMs * sampleRate) / 1000
                        val buffer = ShortArray(numSamples)
                        for (i in 0 until numSamples) {
                            val dVal = sin(2.0 * PI * i.toDouble() / (sampleRate.toDouble() / freq))
                            val envelope = when {
                                i < numSamples * 0.15 -> i.toDouble() / (numSamples * 0.15)
                                i > numSamples * 0.8 -> (numSamples - i).toDouble() / (numSamples * 0.2)
                                else -> 1.0
                            }
                            buffer[i] = (dVal * envelope * 28000 * gain).toInt().toShort()
                        }
                        track.write(buffer, 0, buffer.size)
                        delay(50L)
                    }
                    delay(350L)
                } while (isActive && (isPlaying || loop))
            } catch (_: Exception) {
            } finally {
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
                if (streamingAudioTrack == track) {
                    streamingAudioTrack = null
                }
            }
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
            streamingAudioTrack?.stop()
            streamingAudioTrack?.release()
        } catch (_: Exception) {
        } finally {
            streamingAudioTrack = null
        }

        ringtoneLoopJob?.cancel()
        ringtoneLoopJob = null

        try {
            activeRingtone?.let {
                if (it.isPlaying) {
                    it.stop()
                }
            }
        } catch (_: Exception) {
        } finally {
            activeRingtone = null
        }

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
