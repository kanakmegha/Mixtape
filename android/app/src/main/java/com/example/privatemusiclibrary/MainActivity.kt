package com.example.privatemusiclibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import com.example.privatemusiclibrary.theme.PrivateMusicLibraryTheme
import com.example.privatemusiclibrary.ui.navigation.MainNavigation

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

    private val _sharedUrl = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val _invitationToken =
    kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) {
                lifecycleScope.launch {
                    try {
                        scanLocalAudioUseCase()
                    } catch (e: Exception) {
                        // Ignore background scan errors
                    }
                }
            }
        })

        enableEdgeToEdge()
        setContent {
            val sharedUrl = _sharedUrl.collectAsState().value
            PrivateMusicLibraryTheme { 
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { 
                    MainNavigation(
                        sharedUrl = sharedUrl,
                        onSharedUrlHandled = { _sharedUrl.value = null },
                        invitationToken = _invitationToken.collectAsState().value
                    ) 
                } 
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
                if (intent.type == "text/plain") {
                    val text = intent.getStringExtra(
                        android.content.Intent.EXTRA_TEXT
                    )
                    val urlRegex =
                        "(https?://[^\\s]+)".toRegex()
                    val match = text?.let { urlRegex.find(it) }

                    if (match != null) {
                        _sharedUrl.value = match.value
                    }
                }
            }
        }
    }
}
