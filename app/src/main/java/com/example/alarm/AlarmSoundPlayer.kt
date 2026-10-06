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
import android.provider.Settings
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
    private var activeRingtone: Ringtone? = null
    private var ringtoneLoopJob: Job? = null
    private var previewRingtone: Ringtone? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var synthJob: Job? = null
    private var streamingAudioTrack: AudioTrack? = null
    private var previewJob: Job? = null
    private var isPlaying = false

    fun play(
        context: Context,
        soundTone: String,
        shouldVibrate: Boolean,
        vibrationOnly: Boolean = false,
        volume: Float = 0.8f
    ) {
        if (isPlaying) {
            // Already actively ringing for this alarm event, do not cancel or interrupt
            return
        }
        stop()
        isPlaying = true

        AudioRoutingManager.configureDualAudioOutput(context)
        val devicesInfo = AudioRoutingManager.checkConnectedDevices(context)

        val alarmAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // 1. Handle vibration with USAGE_ALARM so it loops and bypasses silent mode
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
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), alarmAudioAttrs)
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
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_ALARM_ALERT_URI
        }

        var played = false

        if (uri != null) {
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

                    // Loop runner in case device doesn't loop Ringtone automatically
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

            // Fallback to MediaPlayer if Ringtone failed
            if (!played) {
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
        }

        // Fallback to synthesized melody if MediaPlayer & Ringtone both fail
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
            val sampleRate = 22050
            val frequencies = listOf(523.25, 659.25, 783.99, 1046.50) // C5, E5, G5, C6 (Major chime)
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
                        val durationMs = 220
                        val numSamples = (durationMs * sampleRate) / 1000
                        val buffer = ShortArray(numSamples)
                        for (i in 0 until numSamples) {
                            val dVal = sin(2.0 * PI * i.toDouble() / (sampleRate.toDouble() / freq))
                            val envelope = when {
                                i < numSamples * 0.15 -> i.toDouble() / (numSamples * 0.15)
                                i > numSamples * 0.8 -> (numSamples - i).toDouble() / (numSamples * 0.2)
                                else -> 1.0
                            }
                            buffer[i] = (dVal * envelope * 24000 * gain).toInt().toShort()
                        }
                        track.write(buffer, 0, buffer.size)
                        delay(60L)
                    }
                    delay(400L)
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
