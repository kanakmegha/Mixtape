package com.example.privatemusiclibrary.domain.player

import com.example.privatemusiclibrary.domain.model.Track
import kotlinx.coroutines.flow.StateFlow

enum class RepeatMode {
    OFF, REPEAT_ONE, REPEAT_ALL
}

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val queueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isBuffering: Boolean = false
)

interface AudioPlayer {
    val playerState: StateFlow<PlayerState>

    fun play(track: Track)
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun setQueue(tracks: List<Track>)
    fun addToQueue(track: Track)
    fun removeFromQueue(index: Int)
    fun clearQueue()
    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)
    fun release()
}
