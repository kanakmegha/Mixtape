package com.example.privatemusiclibrary.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.player.PlayerController
import com.example.privatemusiclibrary.domain.player.PlayerState
import com.example.privatemusiclibrary.domain.player.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val trackRepository: com.example.privatemusiclibrary.domain.repository.TrackRepository,
    private val localAudioStorage: com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
) : ViewModel() {

    private val _optimisticFavorites = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val favoriteMutex = Mutex()

    val playerState: StateFlow<PlayerState> = combine(
        playerController.playerState,
        _optimisticFavorites,
        trackRepository.observeAll()
    ) { state, favorites, allTracks ->
        val currentTrack = state.currentTrack
        if (currentTrack != null) {
            // Find the most recent track data from DB
            val dbTrack = allTracks.find { it.id == currentTrack.id } ?: currentTrack
            
            // Apply optimistic favorite
            val finalTrack = if (favorites.containsKey(dbTrack.id)) {
                dbTrack.copy(isFavorite = favorites[dbTrack.id]!!)
            } else {
                dbTrack
            }
            state.copy(currentTrack = finalTrack)
        } else {
            state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), playerController.playerState.value)

    fun playQueue(tracks: List<Track>, startIndex: Int) = playerController.playQueue(tracks, startIndex)
    fun pause() = playerController.pause()
    fun resume() = playerController.resume()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    
    fun toggleShuffle() {
        val currentState = playerState.value.shuffleEnabled
        playerController.setShuffle(!currentState)
    }
    
    fun setShuffleModeEnabled(enabled: Boolean) {
        playerController.setShuffle(enabled)
    }

    fun toggleRepeatMode() {
        val nextMode = when (playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        playerController.setRepeatMode(nextMode)
    }
    
    fun toggleFavorite() {
        val currentTrack = playerState.value.currentTrack ?: return
        viewModelScope.launch {
            favoriteMutex.withLock {
                val currentFav = _optimisticFavorites.value[currentTrack.id] ?: currentTrack.isFavorite
                val newIsFavorite = !currentFav
                
                // Optimistic UI update
                _optimisticFavorites.value = _optimisticFavorites.value + (currentTrack.id to newIsFavorite)
                
                // DB update
                trackRepository.update(currentTrack.copy(isFavorite = newIsFavorite))
            }
        }
    }
    
    fun deleteTrack(track: Track) {
        viewModelScope.launch {
            // Stop playback and clear the entire queue before deleting the active file.
            // pause() + skipNext() can keep the deleted item in the Media3 queue,
            // which may allow it to continue playing or replay with repeat enabled.
            if (playerState.value.currentTrack?.id == track.id) {
                playerController.clearQueue()
            }

            trackRepository.delete(track)
            localAudioStorage.delete(track.localUri)
        }
    }
    
    fun renameTrack(track: Track, newTitle: String) {
        viewModelScope.launch {
            val updatedTrack = track.copy(title = newTitle)
            trackRepository.update(updatedTrack)
            
            // If current playing track is renamed, update its state optimistically (handled by playerController observation eventually, but we could add optimistic update if needed)
            if (playerState.value.currentTrack?.id == track.id) {
                // Not perfectly optimistic unless we add title to optimistic state, but flow update is usually fast enough
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
