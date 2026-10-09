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
    val invitationError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val setStorageLocationUseCase: SetStorageLocationUseCase,
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase,
    private val mixtapeApi: MixtapeApi
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
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

    fun onStorageLocationSelected(uri: Uri) {
        viewModelScope.launch {
            setStorageLocationUseCase(uri.toString())
            _uiState.update { it.copy(storageUri = uri.toString()) }
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
    val isStorageConfigured: Boolean = false,
    val urlInput: String = "",
    val isImporting: Boolean = false,
    val error: String? = null,
    val currentJob: com.example.privatemusiclibrary.domain.model.ImportJob? = null
)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
    private val importUrlUseCase: com.example.privatemusiclibrary.domain.usecase.ImportUrlUseCase,
    private val cancelImportJobUseCase: com.example.privatemusiclibrary.domain.usecase.CancelImportJobUseCase,
    private val importJobRepository: com.example.privatemusiclibrary.domain.repository.ImportJobRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private var currentJobId: String? = null

    init {
        viewModelScope.launch {
            val settings = getStorageInfoUseCase()
            _uiState.update {
                it.copy(
                    storageUri = settings.storageUri,
                    isStorageConfigured = settings.storageUri != null
                )
            }
        }

        viewModelScope.launch {
            importJobRepository.observeAll().collect { jobs ->
                val job = jobs.find { it.id == currentJobId }
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

    fun startImport() {
        val url = _uiState.value.urlInput
        if (url.isBlank()) return

        if (!_uiState.value.isStorageConfigured) {
            _uiState.update { it.copy(error = "Storage location not configured") }
            return
        }

        _uiState.update { it.copy(isImporting = true, error = null) }

        viewModelScope.launch {
            val result = importUrlUseCase(url)
            if (result.isSuccess) {
                currentJobId = result.getOrNull()
                _uiState.update { it.copy(urlInput = "") }
                // isImporting will be set to false when the job is actually observed
            } else {
                _uiState.update { it.copy(isImporting = false, error = result.exceptionOrNull()?.message) }
            }
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
