package com.example.privatemusiclibrary.data.importing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages pending URLs received via Android share sheets (ACTION_SEND)
 * across application lifecycles, authentication transitions, and navigation.
 */
@Singleton
class PendingImportManager @Inject constructor() {
    private val _pendingUrl = MutableStateFlow<String?>(null)
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    fun setPendingUrl(url: String?) {
        val trimmed = url?.trim()
        if (!trimmed.isNullOrBlank()) {
            _pendingUrl.value = trimmed
        }
    }

    /**
     * Consumes the pending URL when work is accepted by the Import system.
     */
    fun consumePendingUrl(): String? {
        val current = _pendingUrl.value
        _pendingUrl.value = null
        return current
    }

    fun clear() {
        _pendingUrl.value = null
    }

    fun dismissPendingUrl() {
        clear()
    }
}
