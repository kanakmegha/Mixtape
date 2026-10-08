package com.example.privatemusiclibrary.domain.player

import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.repository.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    private val audioPlayer: AudioPlayer,
    private val playbackRepository: PlaybackRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val playerState: StateFlow<PlayerState> = audioPlayer.playerState

    init {
        scope.launch {
            playerState.collect { state ->
                // Periodically save state if track is present
                state.currentTrack?.let { track ->
                    val playbackState = com.example.privatemusiclibrary.domain.model.PlaybackState(
                        trackId = track.id,
                        positionMs = state.positionMs,
                        updatedAt = System.currentTimeMillis()
                    )
                    playbackRepository.updateState(playbackState)
                }
            }
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) = audioPlayer.playQueue(tracks, startIndex)
    fun pause() = audioPlayer.pause()
    fun resume() = audioPlayer.resume()
    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)
    fun skipNext() = audioPlayer.skipNext()
    fun skipPrevious() = audioPlayer.skipPrevious()
    fun setQueue(tracks: List<Track>) = audioPlayer.setQueue(tracks)
    fun addToQueue(track: Track) = audioPlayer.addToQueue(track)
    fun removeFromQueue(index: Int) = audioPlayer.removeFromQueue(index)
    fun clearQueue() = audioPlayer.clearQueue()
    fun setShuffle(enabled: Boolean) = audioPlayer.setShuffle(enabled)
    fun setRepeatMode(mode: RepeatMode) = audioPlayer.setRepeatMode(mode)
    fun release() = audioPlayer.release()
}
