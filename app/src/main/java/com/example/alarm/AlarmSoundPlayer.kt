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
import android.util.Log
import com.example.R
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

    private const val TAG = "AlarmSoundPlayer"

    private var mediaPlayer: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null
    private var ringtoneLoopJob: Job? = null
    private var previewRingtone: Ringtone? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var vibrationHeartbeatJob: Job? = null
    private var synthJob: Job? = null
    private var streamingAudioTrack: AudioTrack? = null
    private var previewJob: Job? = null

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    private var currentPlayingKey: String? = null

    @Synchronized
    fun play(
        context: Context,
        soundTone: String,
        shouldVibrate: Boolean,
        vibrationOnly: Boolean = false,
        volume: Float = 0.8f
    ) {
        val playKey = "$soundTone|$shouldVibrate|$vibrationOnly|$volume"
        if (isPlaying && currentPlayingKey == playKey) {
            Log.d(TAG, "Toque já está em reprodução ativa com a mesma configuração: $playKey")
            return
        }

        // Interrompe qualquer reprodução anterior ou preview para aplicar as novas configurações
        stop(context)
        isPlaying = true
        currentPlayingKey = playKey

        val targetVolume = volume.coerceIn(0.05f, 1.0f)
        AudioRoutingManager.configureDualAudioOutput(context, targetVolume)

        val alarmAudioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        // 1. Iniciar Vibração Contínua somente se solicitado
        if (vibrationOnly || shouldVibrate) {
            startVibration(context, alarmAudioAttrs)
        } else {
            try { vibrator?.cancel() } catch (_: Throwable) {}
        }

        // 2. Se for somente vibração ou silencioso, encerra aqui
        if (vibrationOnly || soundTone == "silent") {
            Log.d(TAG, "Modo Apenas Vibração ativo, áudio suprimido conforme configuração")
            return
        }

        // 3. YouTube áudio
        if (soundTone.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(soundTone)
            if (parsed != null) {
                YouTubeAudioEngine.playAlarm(context, parsed.first, targetVolume)
                return
            }
        }

        // 4. Iniciar áudio com múltiplas camadas de fallback à prova de falhas
        startAudioPlayback(context, soundTone, targetVolume, alarmAudioAttrs)
    }

    private fun startVibration(context: Context, audioAttrs: AudioAttributes) {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            // Pattern: 0ms wait, 800ms vibrate, 400ms wait, 800ms vibrate, 400ms wait, repeat at index 0 indefinitely
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0), audioAttrs)
                } catch (_: Throwable) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao iniciar vibrador", t)
        }
    }

    private fun startAudioPlayback(
        context: Context,
        soundTone: String,
        targetVolume: Float,
        alarmAudioAttrs: AudioAttributes
    ) {
        var played = false

        // Caso 1: Sons integrados no app (alarm_gentle, alarm_digital)
        if (soundTone == "gentle" || soundTone == "default" || soundTone.isBlank()) {
            played = playRawResource(context, R.raw.alarm_gentle, targetVolume, alarmAudioAttrs)
        } else if (soundTone == "digital" || soundTone == "radar") {
            played = playRawResource(context, R.raw.alarm_digital, targetVolume, alarmAudioAttrs)
        }

        // Caso 2: URI de som selecionado do sistema (content:// ou android.resource://)
        if (!played && (soundTone.startsWith("content://") || soundTone.startsWith("android.resource://"))) {
            try {
                val uri = Uri.parse(soundTone)
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
            } catch (t: Throwable) {
                Log.w(TAG, "MediaPlayer falhou com URI $soundTone, tentando RingtoneManager", t)
                try {
                    val uri = Uri.parse(soundTone)
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
                        startRingtoneLoopWatchdog()
                    }
                } catch (_: Throwable) {
                    played = false
                }
            }
        }

        // Caso 3: Fallback padrão do sistema
        if (!played) {
            try {
                val systemUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: Settings.System.DEFAULT_ALARM_ALERT_URI

                if (systemUri != null) {
                    val ringtone = RingtoneManager.getRingtone(context, systemUri)
                    if (ringtone != null) {
                        ringtone.audioAttributes = alarmAudioAttrs
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ringtone.isLooping = true
                            ringtone.volume = targetVolume
                        }
                        ringtone.play()
                        activeRingtone = ringtone
                        played = true
                        startRingtoneLoopWatchdog()
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Fallback de toque do sistema falhou", t)
            }
        }

        // Caso 4: Fallback absoluto garantido pelo recurso integrado no APK
        if (!played) {
            played = playRawResource(context, R.raw.alarm_gentle, targetVolume, alarmAudioAttrs)
        }

        // Caso 5: Fallback final de emergência em síntese PCM direta
        if (!played) {
            playSynthesizedTone(loop = true, volume = targetVolume)
        }
    }

    private fun playRawResource(
        context: Context,
        rawResId: Int,
        targetVolume: Float,
        alarmAudioAttrs: AudioAttributes
    ): Boolean {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId)
            val mp = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(targetVolume, targetVolume)
                isLooping = true
                prepare()
                start()
            }
            mediaPlayer = mp
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Erro com openRawResourceFd para $rawResId, tentando MediaPlayer.create", t)
            try {
                val mp = MediaPlayer.create(context, rawResId)
                if (mp != null) {
                    try { mp.setAudioAttributes(alarmAudioAttrs) } catch (_: Throwable) {}
                    mp.setVolume(targetVolume, targetVolume)
                    mp.isLooping = true
                    mp.start()
                    mediaPlayer = mp
                    true
                } else {
                    false
                }
            } catch (e2: Throwable) {
                Log.w(TAG, "Falha ao reproduzir MediaPlayer para raw resource $rawResId", e2)
                false
            }
        }
    }

    private fun startRingtoneLoopWatchdog() {
        ringtoneLoopJob?.cancel()
        ringtoneLoopJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && isPlaying) {
                delay(2000L)
                try {
                    if (isPlaying && activeRingtone?.isPlaying == false) {
                        activeRingtone?.play()
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    fun playPreview(
        context: Context,
        uriString: String,
        volume: Float = 0.8f,
        onFinished: () -> Unit = {}
    ) {
        stopPreview()
        val targetVolume = volume.coerceIn(0.05f, 1.0f)
        AudioRoutingManager.configureDualAudioOutput(context, targetVolume)

        if (uriString.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(uriString)
            if (parsed != null) {
                YouTubeAudioEngine.playPreview(context, parsed.first, targetVolume, durationSeconds = 10) {
                    onFinished()
                }
                return
            }
        }

        previewJob = CoroutineScope(Dispatchers.Main).launch {
            var isAudioPlaying = false
            val alarmAttrs = AudioRoutingManager.createDualOutputAudioAttributes()

            if (uriString == "gentle" || uriString == "default" || uriString.isBlank()) {
                isAudioPlaying = playPreviewRaw(context, R.raw.alarm_gentle, targetVolume, alarmAttrs)
            } else if (uriString == "digital" || uriString == "radar") {
                isAudioPlaying = playPreviewRaw(context, R.raw.alarm_digital, targetVolume, alarmAttrs)
            } else {
                val uri: Uri? = try {
                    if (uriString.startsWith("content://") || uriString.startsWith("android.resource://")) {
                        Uri.parse(uriString)
                    } else {
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    }
                } catch (_: Throwable) { null }

                if (uri != null) {
                    try {
                        val mp = MediaPlayer().apply {
                            setDataSource(context, uri)
                            setAudioAttributes(alarmAttrs)
                            setVolume(targetVolume, targetVolume)
                            prepare()
                            start()
                        }
                        previewMediaPlayer = mp
                        isAudioPlaying = true
                    } catch (_: Throwable) {
                        try {
                            val ringtone = RingtoneManager.getRingtone(context, uri)
                            if (ringtone != null) {
                                ringtone.audioAttributes = alarmAttrs
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    ringtone.volume = targetVolume
                                }
                                ringtone.play()
                                previewRingtone = ringtone
                                isAudioPlaying = true
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            if (!isAudioPlaying) {
                playPreviewRaw(context, R.raw.alarm_gentle, targetVolume, alarmAttrs)
            }

            delay(3500L)
            stopPreview()
            onFinished()
        }
    }

    private fun playPreviewRaw(
        context: Context,
        rawResId: Int,
        targetVolume: Float,
        alarmAudioAttrs: AudioAttributes
    ): Boolean {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId)
            val mp = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                setVolume(targetVolume, targetVolume)
                prepare()
                start()
            }
            previewMediaPlayer = mp
            true
        } catch (_: Throwable) {
            try {
                val mp = MediaPlayer.create(context, rawResId)
                if (mp != null) {
                    try { mp.setAudioAttributes(alarmAudioAttrs) } catch (_: Throwable) {}
                    mp.setVolume(targetVolume, targetVolume)
                    mp.start()
                    previewMediaPlayer = mp
                    true
                } else {
                    false
                }
            } catch (_: Throwable) {
                false
            }
        }
    }

    fun stopPreview() {
        YouTubeAudioEngine.stopPreview()
        previewJob?.cancel()
        previewJob = null

        try {
            previewRingtone?.let {
                if (it.isPlaying) it.stop()
            }
        } catch (_: Throwable) {} finally {
            previewRingtone = null
        }

        try {
            previewMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {} finally {
            previewMediaPlayer = null
        }
    }

    private fun playSynthesizedTone(loop: Boolean, volume: Float = 0.8f) {
        synthJob?.cancel()
        synthJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                val sampleRate = 22050
                val frequencies = listOf(587.33, 739.99, 880.00, 1174.66) // D5, F#5, A5, D6 joyful chord
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
            } catch (_: Throwable) {
            } finally {
                try {
                    streamingAudioTrack?.stop()
                    streamingAudioTrack?.release()
                } catch (_: Throwable) {}
                streamingAudioTrack = null
            }
        }
    }

    @Synchronized
    fun stop(context: Context? = null) {
        isPlaying = false
        currentPlayingKey = null
        YouTubeAudioEngine.stopAlarm()
        stopPreview()
        context?.let { AudioRoutingManager.restoreAudioRouting(it) }

        vibrationHeartbeatJob?.cancel()
        vibrationHeartbeatJob = null

        synthJob?.cancel()
        synthJob = null

        try {
            streamingAudioTrack?.stop()
            streamingAudioTrack?.release()
        } catch (_: Throwable) {} finally {
            streamingAudioTrack = null
        }

        ringtoneLoopJob?.cancel()
        ringtoneLoopJob = null

        try {
            activeRingtone?.let {
                if (it.isPlaying) it.stop()
            }
        } catch (_: Throwable) {} finally {
            activeRingtone = null
        }

        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {} finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {} finally {
            vibrator = null
        }
    }
}
