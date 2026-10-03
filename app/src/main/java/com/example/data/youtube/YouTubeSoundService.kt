package com.example.data.youtube

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YouTubeVideoSound(
    val videoId: String,
    val title: String,
    val author: String,
    val durationFormatted: String,
    val thumbnailUrl: String
)

object YouTubeSoundService {

    // Curated high quality presets for alarms across multiple rich categories
    val POPULAR_PRESETS = listOf(
        YouTubeVideoSound(
            videoId = "2OEL4P1Rz04",
            title = "Sons de Pássaros & Manhã na Floresta Tropical",
            author = "Relaxing Nature Sounds",
            durationFormatted = "10:00",
            thumbnailUrl = "https://img.youtube.com/vi/2OEL4P1Rz04/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "jfKfPfyJRdk",
            title = "Lofi Hip Hop - Relaxing Morning Beats",
            author = "Lofi Girl",
            durationFormatted = "3:45",
            thumbnailUrl = "https://img.youtube.com/vi/jfKfPfyJRdk/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "sVzV5m7P04M",
            title = "Som de Chuva Suave com Trovões Distantes",
            author = "Rain Ambience",
            durationFormatted = "8:30",
            thumbnailUrl = "https://img.youtube.com/vi/sVzV5m7P04M/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "mIYzp5maSm8",
            title = "Despertar com Violão Acústico Suave & Paz",
            author = "Acoustic Peace",
            durationFormatted = "4:12",
            thumbnailUrl = "https://img.youtube.com/vi/mIYzp5maSm8/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "jhpN8yG95t0",
            title = "Música Clássica Suave para Acordar (Vivaldi - Primavera)",
            author = "Classical Morning",
            durationFormatted = "5:20",
            thumbnailUrl = "https://img.youtube.com/vi/jhpN8yG95t0/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "5qap5aO4i9A",
            title = "Lofi Morning Coffee - Relaxing Instrumental",
            author = "ChillHop Music",
            durationFormatted = "3:30",
            thumbnailUrl = "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "WPni755-Krg",
            title = "Ondas do Mar & Brisa Suave da Manhã",
            author = "Ocean Waves Meditation",
            durationFormatted = "15:00",
            thumbnailUrl = "https://img.youtube.com/vi/WPni755-Krg/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "btPJPFnesV4",
            title = "Despertador Energético - Upbeat Morning Anthem",
            author = "Morning Energy",
            durationFormatted = "3:10",
            thumbnailUrl = "https://img.youtube.com/vi/btPJPFnesV4/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "1ZYbU8csArI",
            title = "Música de Piano Relaxante para Despertar",
            author = "Peaceful Piano",
            durationFormatted = "6:40",
            thumbnailUrl = "https://img.youtube.com/vi/1ZYbU8csArI/hqdefault.jpg"
        ),
        YouTubeVideoSound(
            videoId = "DWcJFNfaw9c",
            title = "Harpa Suave dos Sonhos & Aurora Boreal",
            author = "Ethereal Melody",
            durationFormatted = "5:15",
            thumbnailUrl = "https://img.youtube.com/vi/DWcJFNfaw9c/hqdefault.jpg"
        )
    )

    suspend fun searchVideos(query: String): List<YouTubeVideoSound> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@withContext POPULAR_PRESETS
        }

        // Method 1: YouTube Official InnerTube API
        try {
            val innerTubeResults = searchInnerTube(trimmed)
            if (innerTubeResults.isNotEmpty()) {
                return@withContext innerTubeResults
            }
        } catch (_: Exception) {
        }

        // Method 2: YouTube Search HTML Scraper
        try {
            val htmlResults = searchYouTubeHtml(trimmed)
            if (htmlResults.isNotEmpty()) {
                return@withContext htmlResults
            }
        } catch (_: Exception) {
        }

        // Method 3: Smart Presets Filtering
        val matchedPresets = POPULAR_PRESETS.filter {
            it.title.contains(trimmed, ignoreCase = true) || it.author.contains(trimmed, ignoreCase = true)
        }
        if (matchedPresets.isNotEmpty()) {
            return@withContext matchedPresets
        }

        // Return popular presets if all methods fail to ensure the user always has playable sounds
        POPULAR_PRESETS
    }

    private fun searchInnerTube(query: String): List<YouTubeVideoSound> {
        val url = URL("https://www.youtube.com/youtubei/v1/search")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        conn.doOutput = true
        conn.connectTimeout = 5000
        conn.readTimeout = 5000

        val requestBody = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240101.01.00")
                    put("hl", "pt")
                    put("gl", "BR")
                })
            })
            put("query", query)
        }

        conn.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val results = mutableListOf<YouTubeVideoSound>()

            val contents = root.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents")

            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val section = contents.optJSONObject(i)
                    val itemSection = section?.optJSONObject("itemSectionRenderer")
                    val items = itemSection?.optJSONArray("contents")
                    if (items != null) {
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j)
                            val videoRenderer = item?.optJSONObject("videoRenderer")
                            if (videoRenderer != null) {
                                val videoId = videoRenderer.optString("videoId")
                                val titleRuns = videoRenderer.optJSONObject("title")?.optJSONArray("runs")
                                val title = titleRuns?.optJSONObject(0)?.optString("text")
                                    ?: videoRenderer.optJSONObject("title")?.optJSONArray("accessibility")?.optJSONObject(0)?.optString("label")
                                    ?: ""
                                val author = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: videoRenderer.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    ?: "YouTube"
                                val lengthText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "Vídeo"

                                if (videoId.isNotBlank() && title.isNotBlank()) {
                                    results.add(
                                        YouTubeVideoSound(
                                            videoId = videoId,
                                            title = title,
                                            author = author,
                                            durationFormatted = lengthText,
                                            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                return results
            }
        }
        return emptyList()
    }

    private fun searchYouTubeHtml(query: String): List<YouTubeVideoSound> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = URL("https://www.youtube.com/results?search_query=$encoded")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        conn.setRequestProperty("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
        conn.connectTimeout = 4000
        conn.readTimeout = 4000

        if (conn.responseCode == 200) {
            val html = conn.inputStream.bufferedReader().use { it.readText() }
            val marker = "var ytInitialData = "
            val startIndex = html.indexOf(marker)
            if (startIndex != -1) {
                val jsonStart = startIndex + marker.length
                val jsonEnd = html.indexOf(";</script>", jsonStart)
                if (jsonEnd != -1) {
                    val jsonStr = html.substring(jsonStart, jsonEnd)
                    val root = JSONObject(jsonStr)
                    val results = mutableListOf<YouTubeVideoSound>()
                    val contents = root.optJSONObject("contents")
                        ?.optJSONObject("twoColumnSearchResultsRenderer")
                        ?.optJSONObject("primaryContents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")

                    if (contents != null) {
                        for (i in 0 until contents.length()) {
                            val section = contents.optJSONObject(i)
                            val itemSection = section?.optJSONObject("itemSectionRenderer")
                            val items = itemSection?.optJSONArray("contents")
                            if (items != null) {
                                for (j in 0 until items.length()) {
                                    val item = items.optJSONObject(j)
                                    val videoRenderer = item?.optJSONObject("videoRenderer")
                                    if (videoRenderer != null) {
                                        val videoId = videoRenderer.optString("videoId")
                                        val titleRuns = videoRenderer.optJSONObject("title")?.optJSONArray("runs")
                                        val title = titleRuns?.optJSONObject(0)?.optString("text") ?: ""
                                        val author = videoRenderer.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "YouTube"
                                        val lengthText = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: "Vídeo"

                                        if (videoId.isNotBlank() && title.isNotBlank()) {
                                            results.add(
                                                YouTubeVideoSound(
                                                    videoId = videoId,
                                                    title = title,
                                                    author = author,
                                                    durationFormatted = lengthText,
                                                    thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (results.isNotEmpty()) {
                        return results
                    }
                }
            }
        }
        return emptyList()
    }

    fun encodeSoundTone(video: YouTubeVideoSound): String {
        return "youtube://${video.videoId}|${Uri.encode(video.title)}"
    }

    fun parseSoundTone(soundTone: String): Pair<String, String>? {
        if (!soundTone.startsWith("youtube://")) return null
        val content = soundTone.removePrefix("youtube://")
        val parts = content.split("|")
        val videoId = parts.getOrNull(0) ?: return null
        val title = parts.getOrNull(1)?.let { Uri.decode(it) } ?: "YouTube Som"
        return Pair(videoId, title)
    }
}
