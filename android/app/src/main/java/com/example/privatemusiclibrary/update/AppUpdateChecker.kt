package com.example.privatemusiclibrary.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.example.privatemusiclibrary.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AvailableAppUpdate(
    val versionName: String,
    val apkDownloadUrl: String,
    val releasePageUrl: String
)

sealed interface AppUpdateCheckResult {
    data class UpdateAvailable(val update: AvailableAppUpdate) : AppUpdateCheckResult
    data class UpToDate(val currentVersion: String, val latestVersion: String) : AppUpdateCheckResult
    data class Failed(val message: String) : AppUpdateCheckResult
}

/** Checks the latest stable GitHub Release. */
object AppUpdateChecker {
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/kanakmegha/Mixtape/releases/latest"
    private const val APK_ASSET_NAME = "Mixtape.apk"

    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): AppUpdateCheckResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Mixtape-Android")
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) {
                throw IOException("GitHub returned HTTP $statusCode")
            }

            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(payload)
            val tagName = release.optString("tag_name").trim()
            if (tagName.isEmpty()) throw IOException("The latest release has no version tag.")

            val latestVersion = normalizeVersion(tagName)
            val assets = release.optJSONArray("assets")
                ?: throw IOException("No release assets were found.")
            val apkUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name") == APK_ASSET_NAME }
                ?.optString("browser_download_url")
                ?.takeIf { it.startsWith("https://") }
                ?: throw IOException("The latest release does not include Mixtape.apk.")

            val releasePageUrl = release.optString("html_url")
                .takeIf { it.startsWith("https://") }
                ?: "https://github.com/kanakmegha/Mixtape/releases"

            if (compareVersions(latestVersion, currentVersion) > 0) {
                AppUpdateCheckResult.UpdateAvailable(
                    AvailableAppUpdate(
                        versionName = latestVersion,
                        apkDownloadUrl = apkUrl,
                        releasePageUrl = releasePageUrl
                    )
                )
            } else {
                AppUpdateCheckResult.UpToDate(
                    currentVersion = currentVersion,
                    latestVersion = latestVersion
                )
            }
        } catch (e: Exception) {
            AppUpdateCheckResult.Failed(
                message = when (e) {
                    is java.net.UnknownHostException ->
                        "Couldn't connect to GitHub. Check your internet and try again."
                    is java.net.SocketTimeoutException ->
                        "GitHub took too long to respond. Try again."
                    else -> "Couldn't check for updates. Please try again later."
                }
            )
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * Starts an APK download through Android's Download Manager.
 * Users still need to open/approve the installer; this app does not silently install APKs.
 */
object AppUpdateDownloader {
    fun enqueue(context: Context, update: AvailableAppUpdate): Long {
        require(update.apkDownloadUrl.startsWith("https://")) {
            "The update URL must use HTTPS."
        }

        val request = DownloadManager.Request(Uri.parse(update.apkDownloadUrl)).apply {
            setTitle("Mixtape update ${update.versionName}")
            setDescription("Downloading the latest Mixtape update")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            // Use app-specific external Downloads storage for compatibility with modern
            // scoped-storage rules. The Download Manager completion notification can open
            // the downloaded APK for the user to confirm installation.
            setDestinationInExternalFilesDir(
                context.applicationContext,
                Environment.DIRECTORY_DOWNLOADS,
                "Mixtape-${update.versionName}.apk"
            )
        }

        val manager = context.applicationContext
            .getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}

internal fun normalizeVersion(version: String): String =
    version.trim().removePrefix("v").removePrefix("V")

/** Compares numeric dotted versions, e.g. 1.0.10 > 1.0.9. */
internal fun compareVersions(candidate: String, installed: String): Int {
    fun parts(value: String): List<Int> {
        val normalized = normalizeVersion(value).substringBefore('-').substringBefore('+')
        require(normalized.isNotEmpty()) { "Version cannot be empty." }
        return normalized.split('.').map { component ->
            component.toIntOrNull() ?: throw IllegalArgumentException("Invalid version: $value")
        }
    }

    val candidateParts = parts(candidate)
    val installedParts = parts(installed)
    for (index in 0 until maxOf(candidateParts.size, installedParts.size)) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val installedPart = installedParts.getOrElse(index) { 0 }
        if (candidatePart != installedPart) return candidatePart.compareTo(installedPart)
    }
    return 0
}
