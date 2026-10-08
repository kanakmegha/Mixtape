package com.example.privatemusiclibrary.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.usecase.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase
) : ViewModel() {

    val uiState: StateFlow<PlaylistsUiState> = getPlaylistsUseCase()
        .map { PlaylistsUiState(playlists = it, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaylistsUiState(isLoading = true)
        )

    fun createPlaylist(name: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                createPlaylistUseCase(name)
                onComplete()
            } catch (e: Exception) {
                // Ignore or handle
            }
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            deletePlaylistUseCase(id)
        }
    }
}

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val tracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val getPlaylistUseCase: GetPlaylistUseCase,
    private val getPlaylistTracksUseCase: GetPlaylistTracksUseCase,
    private val renamePlaylistUseCase: RenamePlaylistUseCase,
    private val deletePlaylistUseCase: DeletePlaylistUseCase,
    private val removeTrackFromPlaylistUseCase: RemoveTrackFromPlaylistUseCase,
    private val reorderPlaylistTracksUseCase: ReorderPlaylistTracksUseCase
) : ViewModel() {

    private val _playlistId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PlaylistDetailUiState> = _playlistId
        .filterNotNull()
        .flatMapLatest { id ->
            combine(
                getPlaylistUseCase(id),
                getPlaylistTracksUseCase(id)
            ) { playlist, tracks ->
                PlaylistDetailUiState(
                    playlist = playlist,
                    tracks = tracks,
                    isLoading = false
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaylistDetailUiState(isLoading = true)
        )

    fun loadPlaylist(id: String) {
        _playlistId.value = id
    }

    fun renamePlaylist(newName: String) {
        val id = _playlistId.value ?: return
        viewModelScope.launch {
            try {
                renamePlaylistUseCase(id, newName)
            } catch (e: Exception) {}
        }
    }

    fun deletePlaylist(onComplete: () -> Unit) {
        val id = _playlistId.value ?: return
        viewModelScope.launch {
            deletePlaylistUseCase(id)
            onComplete()
        }
    }

    fun removeTrack(trackId: String) {
        val pId = _playlistId.value ?: return
        viewModelScope.launch {
            removeTrackFromPlaylistUseCase(pId, trackId)
        }
    }

    fun reorderTracks(fromIndex: Int, toIndex: Int) {
        val pId = _playlistId.value ?: return
        val currentTracks = uiState.value.tracks.toMutableList()
        if (fromIndex in currentTracks.indices && toIndex in currentTracks.indices) {
            val moved = currentTracks.removeAt(fromIndex)
            currentTracks.add(toIndex, moved)
            viewModelScope.launch {
                reorderPlaylistTracksUseCase(pId, currentTracks)
            }
        }
    }
}

data class AddToPlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val trackPlaylists: Set<String> = emptySet(),
    val isLoading: Boolean = true
)

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val getPlaylistsForTrackUseCase: GetPlaylistsForTrackUseCase,
    private val addTrackToPlaylistUseCase: AddTrackToPlaylistUseCase,
    private val removeTrackFromPlaylistUseCase: RemoveTrackFromPlaylistUseCase,
    private val createPlaylistUseCase: CreatePlaylistUseCase
) : ViewModel() {

    private val _trackId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AddToPlaylistUiState> = _trackId
        .filterNotNull()
        .flatMapLatest { trackId ->
            combine(
                getPlaylistsUseCase(),
                getPlaylistsForTrackUseCase(trackId)
            ) { all, forTrack ->
                AddToPlaylistUiState(
                    playlists = all,
                    trackPlaylists = forTrack.toSet(),
                    isLoading = false
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AddToPlaylistUiState(isLoading = true)
        )

    fun loadForTrack(trackId: String) {
        _trackId.value = trackId
    }

    fun togglePlaylist(playlistId: String) {
        val tId = _trackId.value ?: return
        val currentlyIn = uiState.value.trackPlaylists.contains(playlistId)
        viewModelScope.launch {
            if (currentlyIn) {
                removeTrackFromPlaylistUseCase(playlistId, tId)
            } else {
                addTrackToPlaylistUseCase(playlistId, tId)
            }
        }
    }
    
    fun createAndAdd(name: String) {
        val tId = _trackId.value ?: return
        viewModelScope.launch {
            try {
                val newId = createPlaylistUseCase(name)
                addTrackToPlaylistUseCase(newId, tId)
            } catch (e: Exception) {}
        }
    }
}
