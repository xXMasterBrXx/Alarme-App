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
            // First try fetching recent releases list to avoid cache/prerelease issues
            val listUrl = "https://api.github.com/repos/$repo/releases?per_page=10"
            val conn = URL(listUrl).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "ChronoClock-Android-Updater")
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            var releaseObj: JSONObject? = null

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = org.json.JSONArray(jsonStr)
                if (jsonArray.length() > 0) {
                    // Pick the first release that has an APK or is not a draft
                    for (i in 0 until jsonArray.length()) {
                        val item = jsonArray.getJSONObject(i)
                        if (!item.optBoolean("draft", false)) {
                            releaseObj = item
                            break
                        }
                    }
                }
            }

            // Fallback to /releases/latest if list failed
            if (releaseObj == null) {
                val latestUrl = "https://api.github.com/repos/$repo/releases/latest"
                val latestConn = URL(latestUrl).openConnection() as HttpURLConnection
                latestConn.setRequestProperty("User-Agent", "ChronoClock-Android-Updater")
                latestConn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                latestConn.connectTimeout = 8000
                latestConn.readTimeout = 8000

                if (latestConn.responseCode == 200) {
                    val jsonStr = latestConn.inputStream.bufferedReader().use { it.readText() }
                    releaseObj = JSONObject(jsonStr)
                } else if (latestConn.responseCode == 404) {
                    return@withContext Result.failure(Exception("Nenhum release público encontrado para o repositório $repo."))
                } else {
                    return@withContext Result.failure(Exception("Erro na verificação do GitHub (HTTP ${latestConn.responseCode})"))
                }
            }

            if (releaseObj != null) {
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
                val remoteVersion = tagName.removePrefix("v").removePrefix("V").trim()
                val isNewer = isVersionNewer(tagName, currentVersion)

                val info = AppReleaseInfo(
                    tagName = tagName,
                    versionName = if (remoteVersion.isNotBlank()) remoteVersion else tagName,
                    releaseTitle = name,
                    changelog = body,
                    publishedAt = publishedAt,
                    downloadUrl = downloadUrl,
                    isNewer = isNewer
                )

                return@withContext Result.success(info)
            } else {
                return@withContext Result.failure(Exception("Não foi possível obter dados do lançamento no GitHub."))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    private fun extractVersionNumbers(raw: String): List<Int> {
        val regex = Regex("\\d+")
        return regex.findAll(raw).mapNotNull { it.value.toIntOrNull() }.toList()
    }

    private fun isVersionNewer(remoteTag: String, currentVersionStr: String): Boolean {
        val cleanRemoteTag = remoteTag.trim()
        val cleanCurrent = currentVersionStr.trim()
        if (cleanRemoteTag.isBlank()) return false

        val normalizedRemote = cleanRemoteTag.removePrefix("v").removePrefix("V").trim()
        val normalizedCurrent = cleanCurrent.removePrefix("v").removePrefix("V").trim()

        // If versions are completely identical, not newer
        if (normalizedRemote.equals(normalizedCurrent, ignoreCase = true)) {
            return false
        }

        val remoteNums = extractVersionNumbers(normalizedRemote)
        val currentNums = extractVersionNumbers(normalizedCurrent)

        // If current was template default "1.0.0" and remote is 0.1.x, remote is definitely the real newer release
        if (normalizedCurrent == "1.0.0" && normalizedRemote.startsWith("0.")) {
            return true
        }

        // If user tagged 1.0.8 by typo previously, and now published 0.1.9:
        if (currentNums == listOf(1, 0, 8) && remoteNums == listOf(0, 1, 9)) {
            return true
        }

        if (remoteNums.isNotEmpty() && currentNums.isNotEmpty()) {
            // Normal segment comparison
            val maxLen = maxOf(remoteNums.size, currentNums.size)
            for (i in 0 until maxLen) {
                val r = remoteNums.getOrElse(i) { 0 }
                val c = currentNums.getOrElse(i) { 0 }
                if (r > c) return true
                if (r < c) break
            }
        }

        // If the tag name is different from installed version, treat as an available update
        return !normalizedRemote.equals(normalizedCurrent, ignoreCase = true)
    }

    fun getInstalledVersionDisplay(context: Context): String {
        try {
            val prefs = context.getSharedPreferences("chrono_prefs", Context.MODE_PRIVATE)
            if (prefs.contains("installed_release_tag")) {
                prefs.edit().remove("installed_release_tag").apply()
            }
        } catch (_: Exception) {}

        val ver = BuildConfig.VERSION_NAME.trim()
        return if (ver.startsWith("v") || ver.startsWith("V")) ver else "v$ver"
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        releaseTag: String = "",
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
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: context.externalCacheDir
                ?: File(context.cacheDir, "updates")
            downloadDir.mkdirs()
            val destinationFile = File(downloadDir, "chronoclock-update.apk")

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "ChronoClock-Updater")

            var redirectCount = 0
            var finalConn = conn
            var currentUrl = downloadUrl
            while (finalConn.responseCode in listOf(301, 302, 303, 307, 308) && redirectCount < 7) {
                val location = finalConn.getHeaderField("Location")
                finalConn.disconnect()
                if (location.isNullOrBlank()) break
                val targetUrl = URL(URL(currentUrl), location).toString()
                currentUrl = targetUrl
                finalConn = URL(targetUrl).openConnection() as HttpURLConnection
                finalConn.setRequestProperty("User-Agent", "ChronoClock-Updater")
                finalConn.connectTimeout = 15000
                finalConn.readTimeout = 30000
                redirectCount++
            }

            if (finalConn.responseCode != HttpURLConnection.HTTP_OK) {
                withContext(Dispatchers.Main) {
                    onError("Servidor retornou erro HTTP ${finalConn.responseCode} ao baixar o arquivo.")
                }
                return@withContext
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

            destinationFile.setReadable(true, false)
            destinationFile.setWritable(true, false)

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
            if (!apkFile.exists() || apkFile.length() < 1000L) {
                onError("Arquivo APK baixado está corrompido ou incompleto.")
                return
            }

            // Verify ZIP magic bytes
            val isZip = try {
                apkFile.inputStream().use { stream ->
                    val header = ByteArray(4)
                    val read = stream.read(header)
                    read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte()
                }
            } catch (_: Exception) { false }

            if (!isZip) {
                onError("O arquivo baixado não é um APK válido.")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                if (!hasInstallPermission) {
                    android.widget.Toast.makeText(
                        context,
                        "Ative a permissão 'Instalar apps desconhecidos' para instalar a atualização.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(settingsIntent)
                    return
                }
            }

            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            }

            // Explicitly grant URI read permissions to system package installers
            val knownInstallers = listOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller",
                "com.samsung.android.packageinstaller",
                "com.miui.packageinstaller",
                "com.coloros.packageinstaller",
                "com.oppo.packageinstaller",
                "com.vivo.packageinstaller",
                "com.huawei.appmarket"
            )
            for (installerPkg in knownInstallers) {
                try {
                    context.grantUriPermission(installerPkg, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) {}
            }

            try {
                val resInfoList = context.packageManager.queryIntentActivities(
                    installIntent,
                    0
                )
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(
                        packageName,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            } catch (_: Exception) {}

            try {
                context.startActivity(installIntent)
            } catch (_: Exception) {
                val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    setDataAndType(contentUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                }
                context.startActivity(fallbackIntent)
            }
        } catch (e: Exception) {
            onError("Erro ao iniciar instalador: ${e.localizedMessage}")
        }
    }
}
