package com.example.privatemusiclibrary.ui.screens

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
    private val scanLocalAudioUseCase: com.example.privatemusiclibrary.domain.usecase.ScanLocalAudioUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadTracks()
        scanLibrary()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            getLibraryTracksUseCase()
                .catch { e -> _uiState.value = LibraryUiState.Error(e.message ?: "Unknown error") }
                .collect { tracks ->
                    if (tracks.isEmpty()) {
                        _uiState.value = LibraryUiState.Empty
                    } else {
                        _uiState.value = LibraryUiState.Success(tracks)
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
    val trackCount: Int = 0
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val setStorageLocationUseCase: SetStorageLocationUseCase,
    private val getStorageInfoUseCase: GetStorageInfoUseCase,
    private val getLibraryTracksUseCase: GetLibraryTracksUseCase
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
}

data class ImportUiState(
    val storageUri: String? = null,
    val isStorageConfigured: Boolean = false,
    val urlInput: String = "",
    val isImporting: Boolean = false,
    val error: String? = null,
    val activeJobs: List<com.example.privatemusiclibrary.domain.model.ImportJob> = emptyList()
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
                _uiState.update { 
                    it.copy(activeJobs = jobs)
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
                _uiState.update { it.copy(isImporting = false, urlInput = "") }
            } else {
                _uiState.update { it.copy(isImporting = false, error = result.exceptionOrNull()?.message) }
            }
        }
    }
    
    fun cancelImport(jobId: String) {
        viewModelScope.launch {
            cancelImportJobUseCase(jobId)
        }
    }
}
