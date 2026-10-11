package com.example.privatemusiclibrary.ui.screens

import com.example.privatemusiclibrary.domain.FakeAudioPlayer
import com.example.privatemusiclibrary.domain.FakeLocalAudioStorage
import com.example.privatemusiclibrary.domain.FakeTrackRepository
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.player.PlayerController
import com.example.privatemusiclibrary.domain.usecase.DeleteTrackUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaybackRepository : com.example.privatemusiclibrary.domain.repository.PlaybackRepository {
    private val _state = kotlinx.coroutines.flow.MutableStateFlow<com.example.privatemusiclibrary.domain.model.PlaybackState?>(null)
    override fun observeState(): kotlinx.coroutines.flow.Flow<com.example.privatemusiclibrary.domain.model.PlaybackState?> = _state
    override suspend fun getState(): com.example.privatemusiclibrary.domain.model.PlaybackState? = _state.value
    override suspend fun updateState(state: com.example.privatemusiclibrary.domain.model.PlaybackState) {
        _state.value = state
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeTrackRepository: FakeTrackRepository
    private lateinit var fakeLocalStorage: FakeLocalAudioStorage
    private lateinit var fakeAudioPlayer: FakeAudioPlayer
    private lateinit var fakePlaybackRepository: FakePlaybackRepository
    private lateinit var playerController: PlayerController
    private lateinit var deleteTrackUseCase: DeleteTrackUseCase
    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTrackRepository = FakeTrackRepository()
        fakeLocalStorage = FakeLocalAudioStorage()
        fakeAudioPlayer = FakeAudioPlayer()
        fakePlaybackRepository = FakePlaybackRepository()
        playerController = PlayerController(fakeAudioPlayer, fakePlaybackRepository)
        deleteTrackUseCase = DeleteTrackUseCase(
            trackRepository = fakeTrackRepository,
            localAudioStorage = fakeLocalStorage,
            audioPlayer = fakeAudioPlayer
        )
        viewModel = PlayerViewModel(
            playerController = playerController,
            trackRepository = fakeTrackRepository,
            deleteTrackUseCase = deleteTrackUseCase
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createTrack(id: String, localUri: String = "file://$id.mp3") = Track(
        id = id, title = "Test $id", artist = "Artist", creator = null,
        album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
        sourceId = null, localUri = localUri, mimeType = "audio/mpeg",
        durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
        playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun testDeleteTrack_delegatesToSafeDeleteTrackUseCase() = runTest(testDispatcher) {
        val playingTrack = createTrack("active_1", "file://active_1.mp3")
        fakeTrackRepository.insert(playingTrack)
        fakeLocalStorage.files.add(playingTrack.localUri)
        fakeAudioPlayer.setCurrentTrack(playingTrack)
        fakeAudioPlayer.setQueue(listOf(playingTrack))

        viewModel.deleteTrack(playingTrack)
        advanceUntilIdle()

        // Verify that safe deletion executed end-to-end
        assertTrue(fakeAudioPlayer.preparedDeletionTrackIds.contains("active_1"))
        assertTrue(fakeAudioPlayer.playbackStopped)
        assertFalse(fakeLocalStorage.exists(playingTrack.localUri))
        assertNull(fakeTrackRepository.getById("active_1"))
    }
}
