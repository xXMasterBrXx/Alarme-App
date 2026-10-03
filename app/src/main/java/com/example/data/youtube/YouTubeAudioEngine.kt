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

    private var fallbackWebView: WebView? = null
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

            // Also spin up a background headless instance for background receiver alarms
            try {
                val webView = WebView(context.applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    webViewClient = WebViewClient()
                    webChromeClient = WebChromeClient()
                }
                val html = createYouTubePlayerHtml(videoId, volPercent, loop = true)
                webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                fallbackWebView = webView
            } catch (_: Exception) {
            }
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

            try {
                fallbackWebView?.loadUrl("about:blank")
                fallbackWebView?.destroy()
            } catch (_: Exception) {
            } finally {
                fallbackWebView = null
            }
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
                    iframe { width:100%; height:100%; border:none; }
                </style>
            </head>
            <body>
                <iframe id="yt_player"
                    src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&enablejsapi=1&controls=0&rel=0&loop=$loopParam&playlist=$videoId"
                    allow="autoplay; encrypted-media; picture-in-picture"
                    allowfullscreen>
                </iframe>
                <script>
                    var tag = document.createElement('script');
                    tag.src = "https://www.youtube.com/iframe_api";
                    var firstScriptTag = document.getElementsByTagName('script')[0];
                    firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                    var player;
                    function onYouTubeIframeAPIReady() {
                        player = new YT.Player('yt_player', {
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
