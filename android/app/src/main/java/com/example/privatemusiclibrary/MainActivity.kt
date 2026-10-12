package com.example.privatemusiclibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.example.privatemusiclibrary.theme.PrivateMusicLibraryTheme
import com.example.privatemusiclibrary.ui.navigation.MainNavigation
import com.example.privatemusiclibrary.update.AppUpdateCheckResult
import com.example.privatemusiclibrary.update.AppUpdateChecker
import com.example.privatemusiclibrary.update.AppUpdateDownloader
import com.example.privatemusiclibrary.update.AvailableAppUpdate

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import com.example.privatemusiclibrary.domain.usecase.ScanLocalAudioUseCase
import kotlinx.coroutines.launch
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject
  lateinit var scanLocalAudioUseCase: ScanLocalAudioUseCase

  @Inject
  lateinit var pendingImportManager: com.example.privatemusiclibrary.data.importing.PendingImportManager

    private val _invitationToken =
        kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _availableUpdate =
        kotlinx.coroutines.flow.MutableStateFlow<AvailableAppUpdate?>(null)
    private val _updatePromptError =
        kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private var lastHandledIntentFingerprint: String? = null
    private var lastHandledIntentTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleIntent(intent)
        }

        // Check for a newer published release in the background. Prompt once per
        // available version; users can always check manually in Settings.
        lifecycleScope.launch {
            val prefs = getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
            when (val result = AppUpdateChecker.checkForUpdate()) {
                is AppUpdateCheckResult.UpdateAvailable -> {
                    val version = result.update.versionName
                    if (prefs.getString(KEY_DISMISSED_UPDATE_VERSION, null) != version) {
                        _availableUpdate.value = result.update
                    }
                }
                is AppUpdateCheckResult.UpToDate,
                is AppUpdateCheckResult.Failed -> Unit
            }
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                lifecycleScope.launch {
                    try {
                        scanLocalAudioUseCase()
                    } catch (e: Exception) {
                        // Ignore background scan errors
                    }
                    try {
                        com.example.privatemusiclibrary.data.worker.SyncWorker.enqueueOneTime(this@MainActivity)
                    } catch (e: Exception) {
                        // Ignore background sync enqueue errors
                    }
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            val invitationToken = _invitationToken.collectAsState().value
            val availableUpdate = _availableUpdate.collectAsState().value
            val updatePromptError = _updatePromptError.collectAsState().value

            PrivateMusicLibraryTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        MainNavigation(
                            pendingImportManager = pendingImportManager,
                            invitationToken = invitationToken
                        )

                        availableUpdate?.let { update ->
                            AlertDialog(
                                onDismissRequest = { dismissUpdatePrompt(update.versionName) },
                                title = { Text("Mixtape update available") },
                                text = {
                                    Text(
                                        "Mixtape ${update.versionName} is available. Download the update now? " +
                                            "When the download completes, open its notification and approve the Android installer. " +
                                            "Android will keep your app data when it accepts this as an update."
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            try {
                                                AppUpdateDownloader.enqueue(this@MainActivity, update)
                                                dismissUpdatePrompt(update.versionName)
                                            } catch (_: Exception) {
                                                _availableUpdate.value = null
                                                _updatePromptError.value =
                                                    "Mixtape couldn't start the download. Open Settings and check for updates again, or visit the GitHub Releases page."
                                            }
                                        }
                                    ) { Text("Download APK") }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = { dismissUpdatePrompt(update.versionName) }
                                    ) { Text("Later") }
                                }
                            )
                        }

                        updatePromptError?.let { message ->
                            AlertDialog(
                                onDismissRequest = { _updatePromptError.value = null },
                                title = { Text("Update download") },
                                text = { Text(message) },
                                confirmButton = {
                                    TextButton(onClick = { _updatePromptError.value = null }) {
                                        Text("OK")
                                    }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            try {
                                                startActivity(
                                                    android.content.Intent(
                                                        android.content.Intent.ACTION_VIEW,
                                                        android.net.Uri.parse(
                                                            "https://github.com/kanakmegha/Mixtape/releases"
                                                        )
                                                    )
                                                )
                                            } catch (_: Exception) {
                                                // The message remains available if no browser can open.
                                            }
                                            _updatePromptError.value = null
                                        }
                                    ) { Text("Releases page") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun dismissUpdatePrompt(versionName: String) {
        getSharedPreferences(UPDATE_PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_DISMISSED_UPDATE_VERSION, versionName)
            .apply()
        _availableUpdate.value = null
    }

    private fun handleIntent(intent: android.content.Intent?) {
        if (intent == null) return

        when (intent.action) {
            android.content.Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                val token = com.example.privatemusiclibrary.ui.navigation.InviteLinkParser.parse(
                    scheme = uri.scheme,
                    host = uri.host,
                    path = uri.path,
                    token = runCatching { uri.getQueryParameter("token") }.getOrNull()
                )
                if (token != null) {
                    _invitationToken.value = token
                }
            }

            android.content.Intent.ACTION_SEND -> {
                val extractedText = extractSharedText(intent)
                val extractedUrl = com.example.privatemusiclibrary.util.SharedUrlExtractor.extractUrl(extractedText)

                if (extractedUrl != null) {
                    val currentTime = System.currentTimeMillis()
                    val fingerprint = "${intent.action}_$extractedUrl"
                    if (fingerprint == lastHandledIntentFingerprint && (currentTime - lastHandledIntentTime) < 1500L) {
                        return // Avoid rapid duplicate processing from bouncing lifecycle callbacks
                    }
                    lastHandledIntentFingerprint = fingerprint
                    lastHandledIntentTime = currentTime
                    pendingImportManager.setPendingUrl(extractedUrl)
                }
            }
        }
    }

    private fun extractSharedText(intent: android.content.Intent): String? {
        val candidates = mutableListOf<String>()

        intent.getStringExtra(android.content.Intent.EXTRA_TEXT)?.let { candidates.add(it) }
        intent.getCharSequenceExtra(android.content.Intent.EXTRA_TEXT)?.toString()?.let { candidates.add(it) }
        intent.getStringExtra(android.content.Intent.EXTRA_HTML_TEXT)?.let { candidates.add(it) }

        val clipData = intent.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                val item = clipData.getItemAt(i)
                item.text?.toString()?.let { candidates.add(it) }
                item.htmlText?.let { candidates.add(it) }
                item.uri?.toString()?.let { candidates.add(it) }
            }
        }

        intent.data?.toString()?.let { candidates.add(it) }

        // Find candidate that contains a recognized or valid URL
        for (candidate in candidates) {
            val url = com.example.privatemusiclibrary.util.SharedUrlExtractor.extractUrl(candidate)
            if (url != null) return candidate
        }

        return candidates.firstOrNull { it.isNotBlank() }
    }

    private companion object {
        const val UPDATE_PREFS = "mixtape_update_preferences"
        const val KEY_DISMISSED_UPDATE_VERSION = "dismissed_update_version"
    }
}
