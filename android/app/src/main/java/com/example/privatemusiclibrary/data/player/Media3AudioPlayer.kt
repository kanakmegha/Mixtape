package com.example.privatemusiclibrary.data.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.player.AudioPlayer
import com.example.privatemusiclibrary.domain.player.PlayerState
import com.example.privatemusiclibrary.domain.player.RepeatMode
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Media3AudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioPlayer, Player.Listener {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var queueTracks: List<Track> = emptyList()

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(this)
            updateState()
            startPositionUpdater()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionUpdater() {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (true) {
                if (controller?.isPlaying == true) {
                    updateState()
                }
                kotlinx.coroutines.delay(250)
            }
        }
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        queueTracks = tracks
        val mediaItems = tracks.map { it.toMediaItem() }
        controller?.setMediaItems(mediaItems, startIndex, 0L)
        controller?.prepare()
        controller?.play()
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    override fun skipNext() {
        controller?.seekToNextMediaItem()
    }

    override fun skipPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    override fun setQueue(tracks: List<Track>) {
        queueTracks = tracks
        val mediaItems = tracks.map { it.toMediaItem() }
        controller?.setMediaItems(mediaItems)
    }

    override fun addToQueue(track: Track) {
        queueTracks = queueTracks + track
        controller?.addMediaItem(track.toMediaItem())
    }

    override fun removeFromQueue(index: Int) {
        if (index in queueTracks.indices) {
            val mutableList = queueTracks.toMutableList()
            mutableList.removeAt(index)
            queueTracks = mutableList
            controller?.removeMediaItem(index)
        }
    }

    override fun clearQueue() {
        queueTracks = emptyList()
        controller?.clearMediaItems()
    }

    override fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    override fun setRepeatMode(mode: RepeatMode) {
        controller?.repeatMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
        }
    }

    override fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller?.removeListener(this)
    }

    // Player.Listener methods
    override fun onPlaybackStateChanged(playbackState: Int) {
        updateState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateState()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        updateState()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        updateState()
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateState()
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateState()
    }

    private fun updateState() {
        val c = controller ?: return
        val mediaItem = c.currentMediaItem
        val trackId = mediaItem?.mediaId
        val currentTrack = queueTracks.find { it.id == trackId }

        _playerState.update {
            it.copy(
                currentTrack = currentTrack,
                isPlaying = c.isPlaying,
                positionMs = c.currentPosition.coerceAtLeast(0),
                durationMs = c.duration.coerceAtLeast(0),
                queue = queueTracks,
                queueIndex = c.currentMediaItemIndex.coerceAtLeast(0),
                shuffleEnabled = c.shuffleModeEnabled,
                repeatMode = when (c.repeatMode) {
                    Player.REPEAT_MODE_OFF -> RepeatMode.OFF
                    Player.REPEAT_MODE_ONE -> RepeatMode.REPEAT_ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.REPEAT_ALL
                    else -> RepeatMode.OFF
                },
                isBuffering = c.playbackState == Player.STATE_BUFFERING
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .setAlbumTitle(this.album)
            .build()
            
        return MediaItem.Builder()
            .setMediaId(this.id)
            .setUri(this.localUri)
            .setMediaMetadata(metadata)
            .build()
    }
}
