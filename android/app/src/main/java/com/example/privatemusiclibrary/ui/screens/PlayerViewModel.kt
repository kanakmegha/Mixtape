package com.example.privatemusiclibrary.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.player.PlayerController
import com.example.privatemusiclibrary.domain.player.PlayerState
import com.example.privatemusiclibrary.domain.player.RepeatMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    fun play(track: Track) = playerController.play(track)
    fun pause() = playerController.pause()
    fun resume() = playerController.resume()
    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)
    fun skipNext() = playerController.skipNext()
    fun skipPrevious() = playerController.skipPrevious()
    
    fun toggleShuffle() {
        val currentState = playerState.value.shuffleEnabled
        playerController.setShuffle(!currentState)
    }

    fun toggleRepeatMode() {
        val nextMode = when (playerState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.REPEAT_ALL
            RepeatMode.REPEAT_ALL -> RepeatMode.REPEAT_ONE
            RepeatMode.REPEAT_ONE -> RepeatMode.OFF
        }
        playerController.setRepeatMode(nextMode)
    }

    override fun onCleared() {
        super.onCleared()
    }
}
