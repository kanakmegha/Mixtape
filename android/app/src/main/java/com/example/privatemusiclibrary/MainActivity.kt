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
                        onSharedUrlHandled = { _sharedUrl.value = null }
                    ) 
                } 
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent) {
        if (intent.action == android.content.Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            if (text != null) {
                val urlRegex = "(https?://[\\w-]+(\\.[\\w-]+)+(/[^\\s]*)?)".toRegex()
                val match = urlRegex.find(text)
                if (match != null) {
                    _sharedUrl.value = match.value
                }
            }
        }
    }
}
