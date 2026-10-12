package com.example.privatemusiclibrary.ui.screens
import com.example.privatemusiclibrary.data.network.CreateInvitationRequest
import com.example.privatemusiclibrary.data.network.MixtapeApi
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatemusiclibrary.domain.model.AppSettings
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.usecase.GetLibraryTracksUseCase
import com.example.privatemusiclibrary.domain.usecase.GetStorageInfoUseCase
import com.example.privatemusiclibrary.domain.usecase.SetStorageLocationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LibraryUiState {
    object Loading : LibraryUiState()
    data class Success(val tracks: List<Track>) : LibraryUiState()
    object Empty : LibraryUiState()
    data class Error(val message: String) : LibraryUiState()
}

data class HomeUiState(
    val recentlyPlayed: List<Track> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRecentlyPlayedTracksUseCase: com.example.privatemusiclibrary.domain.usecase.GetRecentlyPlayedTracksUseCase,
    private val getFavoriteTracksUseCase: com.example.privatemusiclibrary.domain.usecase.GetFavoriteTracksUseCase
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        getRecentlyPlayedTracksUseCase(),
        getFavoriteTracksUseCase()
    ) { recentlyPlayed, favorites ->
        HomeUiState(
            recentlyPlayed = recentlyPlayed,
            favorites = favorites,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase,
    private val scanLocalAudioUseCase: com.example.privatemusiclibrary.domain.usecase.ScanLocalAudioUseCase,
    private val fixMetadataUseCase: com.example.privatemusiclibrary.domain.usecase.FixMetadataUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadTracks()
        scanLibrary()
        viewModelScope.launch {
            fixMetadataUseCase()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private fun loadTracks() {
        viewModelScope.launch {
            combine(
                getLibraryTracksUseCase(),
                _searchQuery
            ) { tracks, query ->
                if (query.isBlank()) {
                    tracks
                } else {
                    val q = query.trim().lowercase()
                    tracks.filter {
                        it.title.lowercase().contains(q) ||
                        (it.artist?.lowercase()?.contains(q) == true)
                    }
                }
            }
            .catch { e -> _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error") }
            .collect { filteredTracks ->
                if (filteredTracks.isEmpty()) {
                    _uiState.value = LibraryUiState.Empty
                } else {
                    _uiState.value = LibraryUiState.Success(filteredTracks)
                }
            }
        }
    }

    fun scanLibrary() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                scanLocalAudioUseCase()
            } finally {
                _isScanning.value = false
            }
        }
    }
}

data class SettingsUiState(
    val isLoading: Boolean = true,
    val storageUri: String? = null,
    val storageUsageBytes: Long = 0,
    val trackCount: Int = 0,
    val isCreatingInvitation: Boolean = false,
    val invitationToken: String? = null,
    val invitationError: String? = null,
    val syncStatus: com.example.privatemusiclibrary.domain.model.SyncStatus = com.example.privatemusiclibrary.domain.model.SyncStatus.Idle,
    val lastSyncTime: Long = 0L,
    val syncErrorMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val setStorageLocationUseCase: SetStorageLocationUseCase,
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase,
    private val scanLocalAudioUseCase: com.example.privatemusiclibrary.domain.usecase.ScanLocalAudioUseCase,
    private val mixtapeApi: MixtapeApi,
    private val syncRepository: com.example.privatemusiclibrary.domain.repository.SyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            syncRepository.syncStatus.collect { status ->
                _uiState.update {
                    it.copy(
                        syncStatus = status,
                        syncErrorMessage = if (status is com.example.privatemusiclibrary.domain.model.SyncStatus.Error) status.message else null
                    )
                }
            }
        }
        viewModelScope.launch {
            syncRepository.lastSyncTime.collect { time ->
                _uiState.update { it.copy(lastSyncTime = time) }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.sync()
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = getStorageInfoUseCase()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    storageUri = settings.storageUri
                )
            }

            getLibraryTracksUseCase().collect { tracks ->
                _uiState.update { it.copy(trackCount = tracks.size) }
            }
        }
    }

    fun onStorageLocationSelected(uri: Uri?) {
        if (uri == null) return
        onStorageLocationSelected(uri.toString())
    }

    fun onStorageLocationSelected(uriString: String) {
        viewModelScope.launch {
            setStorageLocationUseCase(uriString)
            _uiState.update { it.copy(storageUri = uriString) }
        }
    }

    fun resetStorageToDefault() {
        viewModelScope.launch {
            setStorageLocationUseCase(null)
            _uiState.update { it.copy(storageUri = null) }
        }
    }

    fun scanStorage() {
        viewModelScope.launch {
            try {
                scanLocalAudioUseCase()
            } catch (_: Exception) {}
        }
    }

    fun createInvitation() {
        if (_uiState.value.isCreatingInvitation) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isCreatingInvitation = true,
                    invitationError = null,
                    invitationToken = null
                )
            }

            try {
                val response = mixtapeApi.createInvitation(
                    CreateInvitationRequest(expiresInDays = 7)
                )

                if (response.isSuccessful) {
                    val invitation = response.body()

                    if (invitation?.token.isNullOrBlank()) {
                        _uiState.update {
                            it.copy(invitationError = "The server returned an invalid invitation.")
                        }
                    } else {
                        _uiState.update {
                            it.copy(invitationToken = invitation!!.token)
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            invitationError = when (response.code()) {
                                401 -> "Your session expired. Please sign in again."
                                403 -> "You are not allowed to create invitations."
                                else -> "Couldn't create an invitation. Please try again."
                            }
                        )
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        invitationError =
                        "Couldn't reach the server. Check your internet connection."
                    )
                }
            } finally {
                _uiState.update { it.copy(isCreatingInvitation = false) }
            }
        }
    }
}


data class ImportUiState(
    val storageUri: String? = null,
    val isStorageConfigured: Boolean = true,
    val isStorageConfirmed: Boolean = false,
    val isStorageLoading: Boolean = true,
    val urlInput: String = "",
    val pendingSharedUrl: String? = null,
    val isImporting: Boolean = false,
    val error: String? = null,
    val showStorageConfirmationDialog: Boolean = false,
    val currentJob: com.example.privatemusiclibrary.domain.model.ImportJob? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val settingsRepository: com.example.privatemusiclibrary.domain.repository.SettingsRepository,
    private val pendingImportManager: com.example.privatemusiclibrary.data.importing.PendingImportManager,
    private val importUrlUseCase: com.example.privatemusiclibrary.domain.usecase.ImportUrlUseCase,
    private val cancelImportJobUseCase: com.example.privatemusiclibrary.domain.usecase.CancelImportJobUseCase,
    private val importJobRepository: com.example.privatemusiclibrary.domain.repository.ImportJobRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var currentJobId: String? = null
    private var isImportStarting = false

    init {
        // Continuously observe storage settings
        viewModelScope.launch {
            settingsRepository.observeSettings().collect { settings ->
                val confirmed = settings.isStorageConfirmed || !settings.storageUri.isNullOrBlank()
                _uiState.update {
                    it.copy(
                        storageUri = settings.storageUri,
                        isStorageConfigured = true,
                        isStorageConfirmed = confirmed,
                        isStorageLoading = false
                    )
                }

                // Check and trigger pending import once storage status is loaded
                checkAndStartPendingImport()
            }
        }

        // Observe incoming pending shared URLs
        viewModelScope.launch {
            pendingImportManager.pendingUrl.collect { pendingUrl ->
                if (!pendingUrl.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(
                            urlInput = pendingUrl,
                            pendingSharedUrl = pendingUrl
                        )
                    }
                    checkAndStartPendingImport()
                }
            }
        }

        // Observe import jobs
        viewModelScope.launch {
            importJobRepository.observeAll().collect { jobs ->
                val job = if (currentJobId != null) {
                    jobs.find { it.id == currentJobId }
                } else {
                    jobs.filter { !it.status.isTerminal() }.maxByOrNull { it.createdAt }?.also {
                        currentJobId = it.id
                    }
                }

                if (job != null) {
                    _uiState.update { it.copy(currentJob = job, isImporting = false) }

                    if (job.status == com.example.privatemusiclibrary.domain.model.ImportJobStatus.COMPLETED) {
                        viewModelScope.launch {
                            kotlinx.coroutines.delay(3000)
                            if (currentJobId == job.id) {
                                clearCurrentJob()
                            }
                        }
                    }
                } else {
                    _uiState.update { it.copy(currentJob = null) }
                }
            }
        }
    }

    fun onUrlInputChanged(url: String) {
        _uiState.update { it.copy(urlInput = url, error = null) }
    }

    fun onSharedUrlReceived(url: String?) {
        if (!url.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    urlInput = url,
                    pendingSharedUrl = url
                )
            }
            pendingImportManager.setPendingUrl(url)
            checkAndStartPendingImport()
        }
    }

    private fun checkAndStartPendingImport() {
        val state = _uiState.value
        val pending = state.pendingSharedUrl ?: pendingImportManager.pendingUrl.value
        if (pending.isNullOrBlank()) return

        // 1. Wait for storage settings to finish loading before deciding
        if (state.isStorageLoading) return

        // 2. First-use: If storage destination is not yet confirmed by user, prompt confirmation
        if (!state.isStorageConfirmed) {
            _uiState.update {
                it.copy(
                    urlInput = pending,
                    pendingSharedUrl = pending,
                    showStorageConfirmationDialog = true
                )
            }
            return
        }

        // 3. Guard against duplicate scheduling or concurrent triggers
        if (isImportStarting || state.isImporting || (state.currentJob != null && !state.currentJob.status.isTerminal())) {
            return
        }

        // 4. Consume URL and start import
        isImportStarting = true
        pendingImportManager.consumePendingUrl()
        _uiState.update { it.copy(pendingSharedUrl = null, showStorageConfirmationDialog = false) }

        startImportInternal(pending)
    }

    fun confirmStorage() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            settingsRepository.updateSettings(settings.copy(isStorageConfirmed = true))
            _uiState.update { it.copy(isStorageConfirmed = true, showStorageConfirmationDialog = false) }
        }
    }

    fun confirmStorageAndStartImport() {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            settingsRepository.updateSettings(settings.copy(isStorageConfirmed = true))
            _uiState.update { it.copy(isStorageConfirmed = true, showStorageConfirmationDialog = false) }
            val url = _uiState.value.urlInput.trim()
            val targetUrl = if (url.isNotBlank()) url else _uiState.value.pendingSharedUrl.orEmpty()
            if (targetUrl.isNotBlank()) {
                if (_uiState.value.pendingSharedUrl == targetUrl) {
                    pendingImportManager.consumePendingUrl()
                    _uiState.update { it.copy(pendingSharedUrl = null) }
                }
                startImportInternal(targetUrl)
            }
        }
    }

    fun dismissStorageConfirmationDialog() {
        _uiState.update { it.copy(showStorageConfirmationDialog = false) }
    }

    fun onStorageLocationSelected(uri: Uri?) {
        if (uri == null) return
        onStorageLocationSelected(uri.toString())
    }

    fun onStorageLocationSelected(uriString: String) {
        viewModelScope.launch {
            val settings = settingsRepository.getSettings()
            settingsRepository.updateSettings(settings.copy(storageUri = uriString, isStorageConfirmed = true))
            _uiState.update {
                it.copy(
                    storageUri = uriString,
                    isStorageConfirmed = true,
                    showStorageConfirmationDialog = false
                )
            }
            val url = _uiState.value.urlInput.trim()
            val targetUrl = if (url.isNotBlank()) url else _uiState.value.pendingSharedUrl.orEmpty()
            if (targetUrl.isNotBlank()) {
                if (_uiState.value.pendingSharedUrl == targetUrl) {
                    pendingImportManager.consumePendingUrl()
                    _uiState.update { it.copy(pendingSharedUrl = null) }
                }
                startImportInternal(targetUrl)
            }
        }
    }

    fun startImport() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        if (!_uiState.value.isStorageConfirmed) {
            _uiState.update { it.copy(showStorageConfirmationDialog = true) }
            return
        }

        if (_uiState.value.pendingSharedUrl == url) {
            pendingImportManager.consumePendingUrl()
            _uiState.update { it.copy(pendingSharedUrl = null) }
        }

        startImportInternal(url)
    }

    private fun startImportInternal(url: String) {
        if (url.isBlank()) {
            isImportStarting = false
            return
        }

        _uiState.update { it.copy(isImporting = true, error = null) }

        viewModelScope.launch {
            try {
                val result = importUrlUseCase(url)
                if (result.isSuccess) {
                    currentJobId = result.getOrNull()
                    _uiState.update { it.copy(urlInput = "") }
                } else {
                    _uiState.update {
                        it.copy(
                            isImporting = false,
                            error = result.exceptionOrNull()?.message ?: "Failed to start import"
                        )
                    }
                }
            } finally {
                isImportStarting = false
            }
        }
    }

    fun dismissPendingUrl() {
        pendingImportManager.clear()
        _uiState.update {
            it.copy(
                pendingSharedUrl = null,
                urlInput = if (it.urlInput == it.pendingSharedUrl) "" else it.urlInput,
                error = null
            )
        }
    }

    fun cancelImport(jobId: String) {
        viewModelScope.launch {
            cancelImportJobUseCase(jobId)
            clearCurrentJob()
        }
    }

    fun clearCurrentJob() {
        currentJobId = null
        _uiState.update { it.copy(currentJob = null) }
    }
}
