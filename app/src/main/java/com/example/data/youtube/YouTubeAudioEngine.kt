package com.example.data.youtube

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

data class YouTubePlaybackState(
    val videoId: String,
    val volumePercent: Int,
    val isLoop: Boolean,
    val token: Long = System.currentTimeMillis()
)

object YouTubeAudioEngine {

    private val _currentPlaybackState = mutableStateOf<YouTubePlaybackState?>(null)
    val currentPlaybackState: State<YouTubePlaybackState?> = _currentPlaybackState

    private val mainHandler = Handler(Looper.getMainLooper())
    private var previewTimeoutRunnable: Runnable? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun playAlarm(context: Context, videoId: String, volume: Float = 0.8f) {
        stop()
        val volPercent = (volume.coerceIn(0.05f, 1f) * 100).toInt()

        mainHandler.post {
            _currentPlaybackState.value = YouTubePlaybackState(
                videoId = videoId,
                volumePercent = volPercent,
                isLoop = true
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun playPreview(
        context: Context,
        videoId: String,
        volume: Float = 0.8f,
        durationSeconds: Int = 15,
        onFinished: () -> Unit = {}
    ) {
        stopPreview()
        val volPercent = (volume.coerceIn(0.05f, 1f) * 100).toInt()

        mainHandler.post {
            _currentPlaybackState.value = YouTubePlaybackState(
                videoId = videoId,
                volumePercent = volPercent,
                isLoop = false
            )

            val timeout = Runnable {
                stopPreview()
                onFinished()
            }
            previewTimeoutRunnable = timeout
            mainHandler.postDelayed(timeout, durationSeconds * 1000L)
        }
    }

    fun stopAlarm() {
        stop()
    }

    fun stopPreview() {
        mainHandler.post {
            previewTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            previewTimeoutRunnable = null
            _currentPlaybackState.value = null
        }
    }

    fun stop() {
        mainHandler.post {
            previewTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
            previewTimeoutRunnable = null
            _currentPlaybackState.value = null
        }
    }

    fun createYouTubePlayerHtml(videoId: String, volumePercent: Int, loop: Boolean): String {
        val loopParam = if (loop) 1 else 0
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    html, body { margin:0; padding:0; background:#000; overflow:hidden; width:100%; height:100%; }
                    #player_container { width:100%; height:100%; border:none; }
                </style>
            </head>
            <body>
                <div id="player_container"></div>
                <script>
                    var tag = document.createElement('script');
                    tag.src = "https://www.youtube.com/iframe_api";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('player_container', {
                            videoId: '$videoId',
                            playerVars: {
                                'autoplay': 1,
                                'playsinline': 1,
                                'controls': 0,
                                'rel': 0,
                                'modestbranding': 1,
                                'loop': $loopParam,
                                'playlist': '$videoId'
                            },
                            events: {
                                'onReady': onPlayerReady,
                                'onError': onPlayerError
                            }
                        });
                    }

                    function onPlayerReady(event) {
                        try {
                            event.target.setVolume($volumePercent);
                            event.target.playVideo();
                        } catch(e) {}
                    }

                    function onPlayerError(event) {
                        try {
                            event.target.playVideo();
                        } catch(e) {}
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
    }
}
