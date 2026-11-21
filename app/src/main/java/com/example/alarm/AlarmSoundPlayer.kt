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
    private var secondaryMediaPlayer: MediaPlayer? = null
    private var activeRingtone: Ringtone? = null
    private var ringtoneLoopJob: Job? = null
    private var previewRingtone: Ringtone? = null
    private var previewMediaPlayer: MediaPlayer? = null
    private var secondaryPreviewMediaPlayer: MediaPlayer? = null
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

    @Volatile
    private var lastAppContext: Context? = null

    @Synchronized
    fun play(
        context: Context,
        soundTone: String,
        shouldVibrate: Boolean,
        vibrationOnly: Boolean = false,
        volume: Float = 0.5f,
        playAudioInPlayer: Boolean = false
    ) {
        val appCtx = context.applicationContext ?: context
        lastAppContext = appCtx
        val isExternalConnected = AudioRoutingManager.checkConnectedDevices(appCtx).isExternalConnected
        val effectivePlayInPlayer = playAudioInPlayer || isExternalConnected

        val playKey = "$soundTone|$shouldVibrate|$vibrationOnly|$volume|$effectivePlayInPlayer|$isExternalConnected"
        if (isPlaying && currentPlayingKey == playKey) {
            Log.d(TAG, "Toque já está em reprodução ativa com a mesma configuração: $playKey")
            return
        }

        // Interrompe qualquer reprodução anterior ou preview para aplicar as novas configurações
        stop(appCtx)
        isPlaying = true
        currentPlayingKey = playKey

        val targetVolume = volume.coerceIn(0.05f, 1.0f)
        AudioRoutingManager.configureDualAudioOutput(
            context = appCtx,
            volumePercent = targetVolume,
            forceBuiltInSpeaker = isExternalConnected
        )

        val alarmAudioAttrs = AudioRoutingManager.createAlarmAudioAttributes(forceBuiltInSpeaker = false)

        // 1. Iniciar Vibração Contínua somente se solicitado
        if (vibrationOnly || shouldVibrate) {
            startVibration(appCtx, alarmAudioAttrs)
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
                YouTubeAudioEngine.playAlarm(appCtx, parsed.first, targetVolume)
                return
            }
        }

        // 4. Se o canal de notificação do sistema já reproduz nativamente com FLAG_INSISTENT
        // e NÃO há fone conectado, NÃO iniciamos o MediaPlayer aqui para evitar som duplo.
        // Quando há fone conectado, o canal nativo é silenciado e tocamos aqui em saída dupla (celular + fone).
        if (!effectivePlayInPlayer) {
            Log.d(TAG, "Áudio sendo reproduzido nativamente pelo NotificationChannel com FLAG_INSISTENT")
            return
        }

        // 5. Iniciar áudio (simultâneo no alto-falante do celular + fone quando conectado)
        startAudioPlayback(appCtx, soundTone, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones = isExternalConnected)
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
        alarmAudioAttrs: AudioAttributes,
        dualOutputWithHeadphones: Boolean = false
    ) {
        var played = false

        // Caso 1: Sons integrados no app (alarm_gentle, alarm_digital)
        if (soundTone == "gentle" || soundTone == "default" || soundTone.isBlank()) {
            played = playRawResource(context, R.raw.alarm_gentle, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones)
        } else if (soundTone == "digital" || soundTone == "radar") {
            played = playRawResource(context, R.raw.alarm_digital, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones)
        }

        // Caso 2: URI de som selecionado do sistema (content:// ou android.resource://)
        if (!played && (soundTone.startsWith("content://") || soundTone.startsWith("android.resource://"))) {
            val uri = Uri.parse(soundTone)
            played = playUriResource(context, uri, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones)
            if (!played) {
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
                    played = playUriResource(context, systemUri, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones)
                    if (!played) {
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
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Fallback de toque do sistema falhou", t)
            }
        }

        // Caso 4: Fallback absoluto garantido pelo recurso integrado no APK
        if (!played) {
            played = playRawResource(context, R.raw.alarm_gentle, targetVolume, alarmAudioAttrs, dualOutputWithHeadphones)
        }

        // Caso 5: Fallback final de emergência em síntese PCM direta
        if (!played) {
            playSynthesizedTone(context = context, loop = true, volume = targetVolume, forceSpeaker = dualOutputWithHeadphones)
        }
    }

    private fun playUriResource(
        context: Context,
        uri: Uri,
        targetVolume: Float,
        alarmAudioAttrs: AudioAttributes,
        dualOutputWithHeadphones: Boolean
    ): Boolean {
        return try {
            val speakerPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                setAudioAttributes(alarmAudioAttrs)
                if (dualOutputWithHeadphones) {
                    AudioRoutingManager.applyPreferredSpeakerDevice(context, this)
                }
                setVolume(targetVolume, targetVolume)
                isLooping = true
                prepare()
            }

            // Se houver fone conectado no Android 9+, cria uma segunda instância sincronizada direcionada ao fone
            // para tocar SIMULTANEAMENTE no alto-falante do celular e no fone de ouvido/Bluetooth.
            var headphonePlayer: MediaPlayer? = null
            if (dualOutputWithHeadphones && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                AudioRoutingManager.findExternalOutputDevice(context) != null
            ) {
                try {
                    headphonePlayer = MediaPlayer().apply {
                        setDataSource(context, uri)
                        setAudioAttributes(AudioRoutingManager.createHeadphoneOutputAudioAttributes())
                        AudioRoutingManager.applyPreferredExternalDevice(context, this)
                        setVolume(targetVolume, targetVolume)
                        isLooping = true
                        prepare()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha ao preparar player secundário para fone (URI)", t)
                    headphonePlayer = null
                }
            }

            speakerPlayer.start()
            headphonePlayer?.start()
            mediaPlayer = speakerPlayer
            secondaryMediaPlayer = headphonePlayer
            true
        } catch (t: Throwable) {
            Log.w(TAG, "MediaPlayer falhou com URI $uri", t)
            false
        }
    }

    private fun playRawResource(
        context: Context,
        rawResId: Int,
        targetVolume: Float,
        alarmAudioAttrs: AudioAttributes,
        dualOutputWithHeadphones: Boolean = false
    ): Boolean {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId)
            val speakerPlayer = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                if (dualOutputWithHeadphones) {
                    AudioRoutingManager.applyPreferredSpeakerDevice(context, this)
                }
                setVolume(targetVolume, targetVolume)
                isLooping = true
                prepare()
            }

            var headphonePlayer: MediaPlayer? = null
            if (dualOutputWithHeadphones && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                AudioRoutingManager.findExternalOutputDevice(context) != null
            ) {
                try {
                    val afd2 = context.resources.openRawResourceFd(rawResId)
                    headphonePlayer = MediaPlayer().apply {
                        setAudioAttributes(AudioRoutingManager.createHeadphoneOutputAudioAttributes())
                        setDataSource(afd2.fileDescriptor, afd2.startOffset, afd2.length)
                        afd2.close()
                        AudioRoutingManager.applyPreferredExternalDevice(context, this)
                        setVolume(targetVolume, targetVolume)
                        isLooping = true
                        prepare()
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha ao preparar player secundário para fone (raw)", t)
                    headphonePlayer = null
                }
            }

            speakerPlayer.start()
            headphonePlayer?.start()
            mediaPlayer = speakerPlayer
            secondaryMediaPlayer = headphonePlayer
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Erro com openRawResourceFd para $rawResId, tentando MediaPlayer.create", t)
            try {
                val mp = MediaPlayer.create(context, rawResId)
                if (mp != null) {
                    try { mp.setAudioAttributes(alarmAudioAttrs) } catch (_: Throwable) {}
                    if (dualOutputWithHeadphones) {
                        AudioRoutingManager.applyPreferredSpeakerDevice(context, mp)
                    }
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
        volume: Float = 0.5f,
        onFinished: () -> Unit = {}
    ) {
        stopPreview()
        val appCtx = context.applicationContext ?: context
        lastAppContext = appCtx
        val targetVolume = volume.coerceIn(0.05f, 1.0f)
        val isExternalConnected = AudioRoutingManager.checkConnectedDevices(appCtx).isExternalConnected
        AudioRoutingManager.configureDualAudioOutput(
            context = appCtx,
            volumePercent = targetVolume,
            forceBuiltInSpeaker = isExternalConnected
        )

        if (uriString.startsWith("youtube://")) {
            val parsed = YouTubeSoundService.parseSoundTone(uriString)
            if (parsed != null) {
                YouTubeAudioEngine.playPreview(appCtx, parsed.first, targetVolume, durationSeconds = 10) {
                    if (!isPlaying) {
                        AudioRoutingManager.restoreAudioRouting(appCtx)
                    }
                    onFinished()
                }
                return
            }
        }

        previewJob = CoroutineScope(Dispatchers.Main).launch {
            var isAudioPlaying = false
            val alarmAttrs = AudioRoutingManager.createAlarmAudioAttributes(forceBuiltInSpeaker = false)

            if (uriString == "gentle" || uriString == "default" || uriString.isBlank()) {
                isAudioPlaying = playPreviewRaw(appCtx, R.raw.alarm_gentle, targetVolume, alarmAttrs, isExternalConnected)
            } else if (uriString == "digital" || uriString == "radar") {
                isAudioPlaying = playPreviewRaw(appCtx, R.raw.alarm_digital, targetVolume, alarmAttrs, isExternalConnected)
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
                            setDataSource(appCtx, uri)
                            setAudioAttributes(alarmAttrs)
                            if (isExternalConnected) {
                                AudioRoutingManager.applyPreferredSpeakerDevice(appCtx, this)
                            }
                            setVolume(targetVolume, targetVolume)
                            prepare()
                        }
                        var mp2: MediaPlayer? = null
                        if (isExternalConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            AudioRoutingManager.findExternalOutputDevice(appCtx) != null
                        ) {
                            try {
                                mp2 = MediaPlayer().apply {
                                    setDataSource(appCtx, uri)
                                    setAudioAttributes(AudioRoutingManager.createHeadphoneOutputAudioAttributes())
                                    AudioRoutingManager.applyPreferredExternalDevice(appCtx, this)
                                    setVolume(targetVolume, targetVolume)
                                    prepare()
                                }
                            } catch (_: Throwable) {
                                mp2 = null
                            }
                        }
                        mp.start()
                        mp2?.start()
                        previewMediaPlayer = mp
                        secondaryPreviewMediaPlayer = mp2
                        isAudioPlaying = true
                    } catch (_: Throwable) {
                        try {
                            val ringtone = RingtoneManager.getRingtone(appCtx, uri)
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
                playPreviewRaw(appCtx, R.raw.alarm_gentle, targetVolume, alarmAttrs, isExternalConnected)
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
        alarmAudioAttrs: AudioAttributes,
        dualOutputWithHeadphones: Boolean = false
    ): Boolean {
        return try {
            val afd = context.resources.openRawResourceFd(rawResId)
            val mp = MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttrs)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                if (dualOutputWithHeadphones) {
                    AudioRoutingManager.applyPreferredSpeakerDevice(context, this)
                }
                setVolume(targetVolume, targetVolume)
                prepare()
            }
            var mp2: MediaPlayer? = null
            if (dualOutputWithHeadphones && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                AudioRoutingManager.findExternalOutputDevice(context) != null
            ) {
                try {
                    val afd2 = context.resources.openRawResourceFd(rawResId)
                    mp2 = MediaPlayer().apply {
                        setAudioAttributes(AudioRoutingManager.createHeadphoneOutputAudioAttributes())
                        setDataSource(afd2.fileDescriptor, afd2.startOffset, afd2.length)
                        afd2.close()
                        AudioRoutingManager.applyPreferredExternalDevice(context, this)
                        setVolume(targetVolume, targetVolume)
                        prepare()
                    }
                } catch (_: Throwable) {
                    mp2 = null
                }
            }
            mp.start()
            mp2?.start()
            previewMediaPlayer = mp
            secondaryPreviewMediaPlayer = mp2
            true
        } catch (_: Throwable) {
            try {
                val mp = MediaPlayer.create(context, rawResId)
                if (mp != null) {
                    try { mp.setAudioAttributes(alarmAudioAttrs) } catch (_: Throwable) {}
                    if (dualOutputWithHeadphones) {
                        AudioRoutingManager.applyPreferredSpeakerDevice(context, mp)
                    }
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

        try {
            secondaryPreviewMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {} finally {
            secondaryPreviewMediaPlayer = null
        }

        if (!isPlaying) {
            lastAppContext?.let { AudioRoutingManager.restoreAudioRouting(it) }
        }
    }

    private fun playSynthesizedTone(
        context: Context,
        loop: Boolean,
        volume: Float = 0.5f,
        forceSpeaker: Boolean = false
    ) {
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
                    .setAudioAttributes(AudioRoutingManager.createAlarmAudioAttributes(forceBuiltInSpeaker = false))
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

                if (forceSpeaker) {
                    AudioRoutingManager.applyPreferredSpeakerDevice(context, track)
                }

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
        val ctx = context?.applicationContext ?: context ?: lastAppContext
        isPlaying = false
        currentPlayingKey = null
        YouTubeAudioEngine.stopAlarm()
        stopPreview()
        ctx?.let { AudioRoutingManager.restoreAudioRouting(it) }

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
            secondaryMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (_: Throwable) {} finally {
            secondaryMediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Throwable) {} finally {
            vibrator = null
        }
    }
}
