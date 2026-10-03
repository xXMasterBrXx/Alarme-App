package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.youtube.YouTubeAudioEngine

/**
 * Headless YouTube Audio Player Host embedded into the active Compose hierarchy.
 * Uses key(state.token) to load HTML exactly once per playback request,
 * eliminating audio stutters caused by WebView reloads during recomposition.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeAudioHost() {
    val playbackState by YouTubeAudioEngine.currentPlaybackState

    if (playbackState != null) {
        val state = playbackState!!
        Box(
            modifier = Modifier
                .size(1.dp)
                .alpha(0.01f)
        ) {
            key(state.token) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.mediaPlaybackRequiresUserGesture = false
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            webViewClient = WebViewClient()
                            webChromeClient = WebChromeClient()

                            val html = YouTubeAudioEngine.createYouTubePlayerHtml(
                                videoId = state.videoId,
                                volumePercent = state.volumePercent,
                                loop = state.isLoop
                            )
                            loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
                        }
                    },
                    update = {
                        // Intentional no-op: HTML is loaded once in factory to prevent recomposition audio stutters.
                    },
                    onRelease = { webView ->
                        try {
                            webView.loadUrl("about:blank")
                            webView.destroy()
                        } catch (_: Exception) {
                        }
                    }
                )
            }
        }
    }
}
