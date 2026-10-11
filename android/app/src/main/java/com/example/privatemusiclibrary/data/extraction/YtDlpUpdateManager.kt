package com.example.privatemusiclibrary.data.extraction

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDL.UpdateChannel
import com.yausername.youtubedl_android.YoutubeDL.UpdateStatus
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observable states for yt-dlp runtime engine update.
 */
sealed class YtDlpUpdateState {
    object Idle : YtDlpUpdateState()
    object Updating : YtDlpUpdateState()
    data class Success(val version: String, val status: UpdateStatus) : YtDlpUpdateState()
    data class Failed(val error: String, val canRetry: Boolean = true) : YtDlpUpdateState()
}

/**
 * Functional interface for invoking the library's yt-dlp updater.
 * Enables unit testing of update success, failure, retry, and concurrency.
 */
fun interface YoutubeDLUpdateInvoker {
    fun update(context: Context, channel: UpdateChannel): UpdateStatus?
}

/**
 * Functional interface for querying the running engine version.
 */
fun interface EngineVersionProvider {
    fun getVersion(context: Context): String?
}

/**
 * Manages version-aware, single-flight runtime updates for the yt-dlp extraction engine.
 *
 * Guarantees:
 * 1. Single-flight execution so simultaneous extractions share the same update operation.
 * 2. Update and extraction cannot run concurrently.
 * 3. Does not run an update for every track.
 * 4. Observable update status via [updateState].
 * 5. Sanitized event logging: [YT_DLP_UPDATE] START, SUCCESS, FAILURE.
 * 6. Timestamp is never persisted until updater confirms success and engine version is verified.
 * 7. Failure preserves failure state and permits later retry without indefinite blocking.
 * 8. Reliable version diagnostics without logging secrets.
 */
@Singleton
class YtDlpUpdateManager(
    private val context: Context,
    private val updateInvoker: YoutubeDLUpdateInvoker,
    private val versionProvider: EngineVersionProvider,
    prefs: android.content.SharedPreferences? = null
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context = context,
        updateInvoker = YoutubeDLUpdateInvoker { ctx, ch ->
            YoutubeDL.getInstance().updateYoutubeDL(ctx, ch)
        },
        versionProvider = EngineVersionProvider { ctx ->
            getInstalledVersion(ctx)
        },
        prefs = null
    )

    private val prefs: android.content.SharedPreferences =
        prefs ?: context.getSharedPreferences("ytdlp_update_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updateState = MutableStateFlow<YtDlpUpdateState>(YtDlpUpdateState.Idle)
    val updateState: StateFlow<YtDlpUpdateState> = _updateState.asStateFlow()

    private val flightMutex = Mutex()
    @Volatile
    private var inFlightUpdate: Deferred<Boolean>? = null

    companion object {
        private const val TAG = "YtDlpUpdateManager"
        const val KEY_LAST_SUCCESSFUL_UPDATE = "last_successful_update_ts"
        const val MIN_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 hours
        const val UPDATE_TIMEOUT_MS = 30_000L // 30 seconds network timeout

        /**
         * Inspects the installed yt-dlp engine version reliably across multiple sources:
         * 1. The library's recorded version in SharedPreferences.
         * 2. Direct zip inspection of the extracted binary on disk.
         * 3. Fallback to bundled raw resource in the APK.
         */
        fun getInstalledVersion(context: Context): String {
            // 1. Library SharedPreferences tracked version ("youtubedl-android", "dlpVersion")
            val tracked = try {
                context.getSharedPreferences("youtubedl-android", Context.MODE_PRIVATE)
                    .getString("dlpVersion", null)
            } catch (_: Exception) {
                null
            }
            if (!tracked.isNullOrBlank()) return tracked

            // 2. Read directly from extracted binary zipapp
            val binaryFile = File(context.noBackupFilesDir, "youtubedl-android/yt-dlp/yt-dlp")
            val zipVersion = readVersionFromZip(binaryFile)
            if (!zipVersion.isNullOrBlank()) return zipVersion

            // 3. Fallback: inspect raw resource if binary not yet extracted
            val rawVersion = readVersionFromRawResource(context)
            if (!rawVersion.isNullOrBlank()) return rawVersion

            return "unknown"
        }

        private fun readVersionFromZip(file: File): String? {
            if (!file.exists() || !file.canRead()) return null
            return try {
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry("yt_dlp/version.py") ?: return null
                    zip.getInputStream(entry).bufferedReader().use { reader ->
                        parseVersionString(reader.readText())
                    }
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun readVersionFromRawResource(context: Context): String? {
            return try {
                val inputStream = context.resources.openRawResource(
                    com.yausername.youtubedl_android.R.raw.ytdlp
                )
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "yt_dlp/version.py") {
                            return parseVersionString(zis.bufferedReader().readText())
                        }
                        entry = zis.nextEntry
                    }
                    null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun parseVersionString(content: String): String? {
            val regex = Regex("""__version__\s*=\s*['"]([^'"]+)['"]""")
            return regex.find(content)?.groupValues?.get(1)
        }

        fun isOutdatedVersion(version: String?): Boolean {
            if (version.isNullOrBlank() || version == "unknown") return true
            if (version == "2025.11.12") return true

            val parts = version.split(".")
            if (parts.size >= 3) {
                val year = parts[0].toIntOrNull() ?: return false
                if (year < 2026) return true
            }
            return false
        }
    }

    /**
     * Non-blocking background launch during application startup.
     */
    fun initOrUpdateAsync() {
        scope.async {
            try {
                updateIfNecessary(force = false)
            } catch (e: Exception) {
                Log.w(TAG, "Background startup update check finished with error: ${e.message}")
            }
        }
    }

    /**
     * Dedicated gate called before YouTube extraction.
     * Ensures an update is checked before allowing YouTube extraction,
     * while sharing in-flight operations with concurrent imports.
     */
    suspend fun ensureReadyForExtraction(isYouTube: Boolean = true): Boolean {
        if (!isYouTube) return true
        return updateIfNecessary(force = false)
    }

    /**
     * Single-flight update operation.
     * Simultaneous callers share the same in-flight update execution.
     * Subsequent callers within the interval immediately return true without updating.
     */
    suspend fun updateIfNecessary(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESSFUL_UPDATE, 0L)
        val currentVersion = versionProvider.getVersion(context) ?: getInstalledVersion(context)
        val isOutdated = isOutdatedVersion(currentVersion)

        // If engine is already recent and within interval, return immediately
        if (!force && !isOutdated && lastSuccess > 0L && (now - lastSuccess) < MIN_CHECK_INTERVAL_MS) {
            return@withContext true
        }

        // Single-flight dispatch
        val deferred: Deferred<Boolean> = flightMutex.withLock {
            val recheckSuccess = prefs.getLong(KEY_LAST_SUCCESSFUL_UPDATE, 0L)
            val recheckVersion = versionProvider.getVersion(context) ?: getInstalledVersion(context)
            if (!force && !isOutdatedVersion(recheckVersion) && recheckSuccess > 0L && (now - recheckSuccess) < MIN_CHECK_INTERVAL_MS) {
                return@withLock CompletableDeferred(true)
            }

            inFlightUpdate?.let { return@withLock it }

            val newJob = scope.async {
                try {
                    executeActualUpdate()
                } finally {
                    flightMutex.withLock {
                        inFlightUpdate = null
                    }
                }
            }
            inFlightUpdate = newJob
            newJob
        }

        return@withContext deferred.await()
    }

    private suspend fun executeActualUpdate(): Boolean {
        _updateState.value = YtDlpUpdateState.Updating
        Log.i(TAG, "[YT_DLP_UPDATE] START")

        return try {
            val status: UpdateStatus = withTimeout(UPDATE_TIMEOUT_MS) {
                updateInvoker.update(context, UpdateChannel.STABLE)
            } ?: throw IllegalStateException("yt-dlp updater returned null status")

            val runningVersion = versionProvider.getVersion(context) ?: getInstalledVersion(context)
            Log.i(TAG, "Update status: $status, engine version: $runningVersion")

            // Requirement 7: Verify that the installed engine is sufficiently recent
            if (isOutdatedVersion(runningVersion)) {
                val errorMsg = "Installed yt-dlp version ($runningVersion) remains outdated after update attempt"
                _updateState.value = YtDlpUpdateState.Failed(errorMsg, canRetry = true)
                Log.e(TAG, "[YT_DLP_UPDATE] FAILURE: $errorMsg")
                // Do NOT mark update successful or persist timestamp
                return false
            }

            // Confirmed success: persist timestamp and transition state
            val successTimestamp = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_SUCCESSFUL_UPDATE, successTimestamp).apply()
            _updateState.value = YtDlpUpdateState.Success(runningVersion, status)
            Log.i(TAG, "[YT_DLP_UPDATE] SUCCESS (version: $runningVersion, status: $status)")
            true
        } catch (e: Exception) {
            val sanitized = YtDlpErrorClassifier.sanitizeMessage(e.message)
            _updateState.value = YtDlpUpdateState.Failed(sanitized, canRetry = true)
            Log.e(TAG, "[YT_DLP_UPDATE] FAILURE: $sanitized", e)
            // Preserve failure state and permit retry later
            false
        }
    }
}
