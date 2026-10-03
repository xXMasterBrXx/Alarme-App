package com.example.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val changelog: String,
    val publishedAt: String,
    val downloadUrl: String,
    val isNewer: Boolean
)

object GitHubUpdateManager {

    const val DEFAULT_REPO = "xXMasterBrXx/Alarme-App"

    fun getTargetRepo(context: Context): String {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        val saved = prefs.getString("github_update_repo", null)
        if (saved.isNullOrBlank() || saved == "mathausgomes/ChronoClock") {
            return DEFAULT_REPO
        }
        return saved
    }

    fun setTargetRepo(context: Context, repo: String) {
        val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("github_update_repo", repo.trim()).apply()
    }

    suspend fun checkForUpdates(context: Context): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        val repo = getTargetRepo(context)
        try {
            val apiUrl = "https://api.github.com/repos/$repo/releases/latest"
            val url = URL(apiUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "ChronoClock-Android-Updater")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val code = conn.responseCode
            if (code == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val releaseObj = JSONObject(jsonStr)

                val tagName = releaseObj.optString("tag_name", "")
                val name = releaseObj.optString("name", tagName)
                val body = releaseObj.optString("body", "Nenhuma nota de lançamento informada.")
                val publishedAt = releaseObj.optString("published_at", "")

                // Find APK asset
                var downloadUrl = ""
                val assets = releaseObj.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        val assetDownloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) || assetDownloadUrl.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = assetDownloadUrl
                            break
                        }
                    }
                }

                // If no asset found directly in assets array, fallback to release HTML or zipball
                if (downloadUrl.isBlank()) {
                    downloadUrl = releaseObj.optString("html_url", "https://github.com/$repo/releases/latest")
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val remoteVersion = tagName.removePrefix("v").trim()
                val isNewer = isVersionNewer(remoteVersion, currentVersion)

                val info = AppReleaseInfo(
                    tagName = tagName,
                    versionName = remoteVersion,
                    releaseTitle = name,
                    changelog = body,
                    publishedAt = publishedAt,
                    downloadUrl = downloadUrl,
                    isNewer = isNewer
                )

                return@withContext Result.success(info)
            } else if (code == 404) {
                return@withContext Result.failure(Exception("Nenhum release público encontrado para o repositório $repo."))
            } else {
                return@withContext Result.failure(Exception("Erro na verificação do GitHub (HTTP $code)"))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote.isBlank()) return false
        if (remote.equals(current, ignoreCase = true)) return false

        try {
            val remoteParts = remote.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }
            val currentParts = current.split(".").mapNotNull { it.takeWhile { char -> char.isDigit() }.toIntOrNull() }

            val maxLen = maxOf(remoteParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val r = remoteParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) return false
            }
        } catch (_: Exception) {
            return remote != current
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        if (!downloadUrl.endsWith(".apk", ignoreCase = true)) {
            // Open browser if direct APK asset is not linked
            withContext(Dispatchers.Main) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    onError("Não foi possível abrir o link do release: ${e.localizedMessage}")
                }
            }
            return@withContext
        }

        try {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.cacheDir
            val destinationFile = File(downloadsDir, "chronoclock-update.apk")

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.setRequestProperty("User-Agent", "ChronoClock-Updater")

            var redirectCount = 0
            var finalConn = conn
            while (finalConn.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 5) {
                val newUrl = finalConn.getHeaderField("Location")
                finalConn.disconnect()
                finalConn = URL(newUrl).openConnection() as HttpURLConnection
                finalConn.setRequestProperty("User-Agent", "ChronoClock-Updater")
                redirectCount++
            }

            val totalBytes = finalConn.contentLength.toLong()
            var downloadedBytes = 0L

            finalConn.inputStream.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val progress = downloadedBytes.toFloat() / totalBytes
                            withContext(Dispatchers.Main) {
                                onProgress(progress.coerceIn(0f, 1f))
                            }
                        }
                    }
                    output.flush()
                }
            }

            withContext(Dispatchers.Main) {
                onProgress(1f)
                installApk(context, destinationFile, onError)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError("Falha ao baixar o APK: ${e.localizedMessage}")
            }
        }
    }

    private fun installApk(context: Context, apkFile: File, onError: (String) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                if (!hasInstallPermission) {
                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            onError("Erro ao iniciar instalador: ${e.localizedMessage}")
        }
    }
}
