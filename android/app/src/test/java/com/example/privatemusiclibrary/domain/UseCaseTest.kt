package com.example.privatemusiclibrary.domain
import com.example.privatemusiclibrary.domain.extraction.ExtractedMedia
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.StorageResult
import com.example.privatemusiclibrary.domain.repository.TrackRepository
import com.example.privatemusiclibrary.domain.usecase.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

class FakeTrackRepository : TrackRepository {
    val tracks = mutableListOf<Track>()

    override fun observeAll(): Flow<List<Track>> = flowOf(tracks)
    override fun observeFavorites(): Flow<List<Track>> = flowOf(tracks.filter { it.isFavorite })
    override fun observeRecentlyAdded(): Flow<List<Track>> = flowOf(tracks)
    override fun observeRecentlyPlayed(): Flow<List<Track>> = flowOf(tracks)
    override suspend fun getById(id: String): Track? = tracks.find { it.id == id }
    override suspend fun insert(track: Track) { tracks.add(track) }
    
    override suspend fun update(track: Track) {
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index != -1) tracks[index] = track
    }
    
    override suspend fun delete(track: Track) {
        tracks.removeIf { it.id == track.id }
    }
    
    override suspend fun search(query: String): List<Track> = tracks.filter { it.title.contains(query, ignoreCase = true) }
    override suspend fun getAllTracks(): List<Track> = tracks
}

class FakeLocalAudioStorage : LocalAudioStorage {
    val files = mutableSetOf<String>()
    var failDelete: Boolean = false
    
    override suspend fun save(source: File, destinationName: String): StorageResult {
        val uri = "file://$destinationName"
        files.add(uri)
        return StorageResult.Success(uri)
    }

    override suspend fun delete(uri: String): Boolean {
        if (failDelete) return false
        return files.remove(uri)
    }

    override suspend fun exists(uri: String): Boolean = files.contains(uri)
    override suspend fun size(uri: String): Long? = if (files.contains(uri)) 100L else null
    override fun open(uri: String): InputStream = throw NotImplementedError()
    override suspend fun listAudioFiles(): List<String> = files.toList()
}

class FakeAudioPlayer : com.example.privatemusiclibrary.domain.player.AudioPlayer {
    val preparedDeletionTrackIds = mutableListOf<String>()
    var currentTrackId: String? = null
    val queueTrackIds = mutableListOf<String>()
    var playbackStopped: Boolean = false

    private val _playerState = kotlinx.coroutines.flow.MutableStateFlow(
        com.example.privatemusiclibrary.domain.player.PlayerState()
    )
    override val playerState: kotlinx.coroutines.flow.StateFlow<com.example.privatemusiclibrary.domain.player.PlayerState> = _playerState

    fun setCurrentTrack(track: Track?) {
        currentTrackId = track?.id
        _playerState.value = _playerState.value.copy(currentTrack = track)
    }

    override fun setQueue(tracks: List<Track>) {
        queueTrackIds.clear()
        queueTrackIds.addAll(tracks.map { it.id })
        _playerState.value = _playerState.value.copy(queue = tracks)
    }

    override fun addToQueue(track: Track) {
        queueTrackIds.add(track.id)
        _playerState.value = _playerState.value.copy(queue = _playerState.value.queue + track)
    }

    override fun removeFromQueue(index: Int) {
        if (index in queueTrackIds.indices) {
            queueTrackIds.removeAt(index)
            val mutableList = _playerState.value.queue.toMutableList()
            mutableList.removeAt(index)
            _playerState.value = _playerState.value.copy(queue = mutableList)
        }
    }

    override fun clearQueue() {
        queueTrackIds.clear()
        _playerState.value = _playerState.value.copy(queue = emptyList())
    }

    override suspend fun prepareForTrackDeletion(trackId: String) {
        preparedDeletionTrackIds.add(trackId)
        if (currentTrackId == trackId) {
            playbackStopped = true
            currentTrackId = null
            queueTrackIds.remove(trackId)
            _playerState.value = _playerState.value.copy(
                currentTrack = null,
                queue = _playerState.value.queue.filter { it.id != trackId }
            )
        } else if (queueTrackIds.contains(trackId)) {
            queueTrackIds.remove(trackId)
            _playerState.value = _playerState.value.copy(
                queue = _playerState.value.queue.filter { it.id != trackId }
            )
        }
    }

    override fun playQueue(tracks: List<Track>, startIndex: Int) {
        setQueue(tracks)
        val selected = tracks.getOrNull(startIndex)
        setCurrentTrack(selected)
    }
    override fun pause() {}
    override fun resume() {}
    override fun seekTo(positionMs: Long) {}
    override fun skipNext() {}
    override fun skipPrevious() {}
    override fun setShuffle(enabled: Boolean) {}
    override fun setRepeatMode(mode: com.example.privatemusiclibrary.domain.player.RepeatMode) {}
    override fun release() {}
}

class UseCaseTest {
    private lateinit var trackRepository: FakeTrackRepository
    private lateinit var localStorage: FakeLocalAudioStorage
    private lateinit var audioPlayer: FakeAudioPlayer

    @Before
    fun setup() {
        trackRepository = FakeTrackRepository()
        localStorage = FakeLocalAudioStorage()
        audioPlayer = FakeAudioPlayer()
    }

    private fun createTrack(id: String, localUri: String = "file://$id.mp3") = Track(
        id = id, title = "Test $id", artist = "Artist", creator = null,
        album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
        sourceId = null, localUri = localUri, mimeType = "audio/mpeg",
        durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
        playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
    )

    @Test
    fun testFavoriteUseCases() = runBlocking {
        val track = createTrack("t1")
        trackRepository.insert(track)
        
        val addFavorite = AddFavoriteUseCase(trackRepository)
        addFavorite("t1")
        assertTrue(trackRepository.getById("t1")?.isFavorite == true)
        
        val removeFavorite = RemoveFavoriteUseCase(trackRepository)
        removeFavorite("t1")
        assertFalse(trackRepository.getById("t1")?.isFavorite == true)
    }

    @Test
    fun testDeleteTrackUseCase() = runBlocking {
        val track = createTrack("t1", "file://t1.mp3")
        trackRepository.insert(track)
        localStorage.files.add("file://t1.mp3")
        
        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val success = deleteTrack("t1")
        
        assertTrue(success)
        assertNull(trackRepository.getById("t1"))
        assertFalse(localStorage.exists("file://t1.mp3"))
        assertTrue(audioPlayer.preparedDeletionTrackIds.contains("t1"))
    }

    @Test
    fun testDeleteTrackUseCase_activeTrack_stopsPlaybackAndEvictsBeforeDeleting() = runBlocking {
        val playingTrack = createTrack("active_1", "file://active_1.mp3")
        val otherTrack = createTrack("queued_2", "file://queued_2.mp3")
        trackRepository.insert(playingTrack)
        trackRepository.insert(otherTrack)
        localStorage.files.add(playingTrack.localUri)
        localStorage.files.add(otherTrack.localUri)

        audioPlayer.setCurrentTrack(playingTrack)
        audioPlayer.setQueue(listOf(playingTrack, otherTrack))

        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val result = deleteTrack(playingTrack)

        assertTrue(result)
        // Playback for active track must be halted and track evicted
        assertTrue(audioPlayer.playbackStopped)
        assertNull(audioPlayer.currentTrackId)
        assertFalse(audioPlayer.queueTrackIds.contains("active_1"))
        assertTrue(audioPlayer.queueTrackIds.contains("queued_2"))
        // Local file and DB must be deleted
        assertFalse(localStorage.exists(playingTrack.localUri))
        assertNull(trackRepository.getById("active_1"))
        // Unrelated track must remain untouched
        assertNotNull(trackRepository.getById("queued_2"))
        assertTrue(localStorage.exists(otherTrack.localUri))
    }

    @Test
    fun testDeleteTrackUseCase_queuedTrack_evictsFromQueueWithoutStoppingActivePlayback() = runBlocking {
        val playingTrack = createTrack("active_1", "file://active_1.mp3")
        val queuedTrack = createTrack("queued_2", "file://queued_2.mp3")
        trackRepository.insert(playingTrack)
        trackRepository.insert(queuedTrack)
        localStorage.files.add(playingTrack.localUri)
        localStorage.files.add(queuedTrack.localUri)

        audioPlayer.setCurrentTrack(playingTrack)
        audioPlayer.setQueue(listOf(playingTrack, queuedTrack))

        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val result = deleteTrack(queuedTrack)

        assertTrue(result)
        // Active playback should NOT be stopped
        assertFalse(audioPlayer.playbackStopped)
        assertEquals("active_1", audioPlayer.currentTrackId)
        // Only the deleted track is removed from queue
        assertFalse(audioPlayer.queueTrackIds.contains("queued_2"))
        assertTrue(audioPlayer.queueTrackIds.contains("active_1"))
        // Queued track file and DB deleted
        assertFalse(localStorage.exists(queuedTrack.localUri))
        assertNull(trackRepository.getById("queued_2"))
        // Playing track untouched
        assertNotNull(trackRepository.getById("active_1"))
    }

    @Test
    fun testDeleteTrackUseCase_nonPlayingTrack_deletesStorageAndRepository() = runBlocking {
        val nonPlayingTrack = createTrack("idle_3", "file://idle_3.mp3")
        trackRepository.insert(nonPlayingTrack)
        localStorage.files.add(nonPlayingTrack.localUri)

        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val result = deleteTrack(nonPlayingTrack)

        assertTrue(result)
        assertTrue(audioPlayer.preparedDeletionTrackIds.contains("idle_3"))
        assertFalse(localStorage.exists(nonPlayingTrack.localUri))
        assertNull(trackRepository.getById("idle_3"))
    }

    @Test
    fun testDeleteTrackUseCase_failedStorageDeletion_preservesDatabaseRecord() = runBlocking {
        val track = createTrack("t_fail", "file://t_fail.mp3")
        trackRepository.insert(track)
        localStorage.files.add(track.localUri)
        localStorage.failDelete = true // Storage deletion fails while file still exists

        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val result = deleteTrack(track)

        assertFalse(result) // Operation fails
        // Critical: DB record MUST be preserved so app doesn't lose track of existing file on disk
        assertNotNull(trackRepository.getById("t_fail"))
        assertTrue(localStorage.exists(track.localUri))
    }

    @Test
    fun testDeleteTrackUseCase_fileAlreadyAbsent_cleansUpOrphanDatabaseRecord() = runBlocking {
        val orphanTrack = createTrack("t_orphan", "file://t_orphan.mp3")
        trackRepository.insert(orphanTrack)
        // File does not exist in localStorage

        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage, audioPlayer)
        val result = deleteTrack(orphanTrack)

        assertTrue(result) // Since file doesn't exist, successfully cleans up DB record
        assertNull(trackRepository.getById("t_orphan"))
    }
    
    // Note: The ScanLocalAudioUseCase is fully tested below using Fakes!
}

class FakeAudioMetadataExtractor : AudioMetadataExtractor {
    val metadataMap = mutableMapOf<String, AudioMetadata>()
    
    override suspend fun extractMetadata(uri: String): AudioMetadata? {
        return metadataMap[uri]
    }
}

class ScannerTest {
    private lateinit var trackRepository: FakeTrackRepository
    private lateinit var localStorage: FakeLocalAudioStorage
    private lateinit var metadataExtractor: FakeAudioMetadataExtractor
    private lateinit var scanLocalAudioUseCase: ScanLocalAudioUseCase

    @Before
    fun setup() {
        trackRepository = FakeTrackRepository()
        localStorage = FakeLocalAudioStorage()
        metadataExtractor = FakeAudioMetadataExtractor()
        scanLocalAudioUseCase = ScanLocalAudioUseCase(localStorage, trackRepository, metadataExtractor)
    }

    @Test
    fun testInitialAutomaticScanEmptyFolder() = runBlocking {
        scanLocalAudioUseCase()
        assertTrue(trackRepository.getAllTracks().isEmpty())
    }
    
    @Test
    fun testNewFileDiscovered() = runBlocking {
        localStorage.files.add("content://tree/primary:Music/song1.mp3")
        metadataExtractor.metadataMap["content://tree/primary:Music/song1.mp3"] = AudioMetadata(
            title = "Song 1", artist = "Artist 1", album = null, durationMs = 120000L, mimeType = "audio/mpeg", sizeBytes = 1000L
        )
        
        scanLocalAudioUseCase()
        
        val tracks = trackRepository.getAllTracks()
        assertEquals(1, tracks.size)
        assertEquals("Song 1", tracks[0].title)
    }
    
    @Test
    fun testRepeatedScanDoesNotDuplicate() = runBlocking {
        localStorage.files.add("content://tree/primary:Music/song1.mp3")
        metadataExtractor.metadataMap["content://tree/primary:Music/song1.mp3"] = AudioMetadata(
            title = "Song 1", artist = "Artist 1", album = null, durationMs = 120000L, mimeType = "audio/mpeg", sizeBytes = 1000L
        )
        
        scanLocalAudioUseCase()
        assertEquals(1, trackRepository.getAllTracks().size)
        
        // Scan again
        scanLocalAudioUseCase()
        assertEquals(1, trackRepository.getAllTracks().size)
    }
    
    @Test
    fun testUnsupportedFileExtension() = runBlocking {
        // Handled at storage layer (which returns only supported audio files).
        // Since we are mocking storage directly in the fake, we just verify the scanner ignores metadata failures.
        localStorage.files.add("content://tree/primary:Music/document.pdf")
        // No metadata mapped -> extraction returns null
        scanLocalAudioUseCase()
        assertEquals(0, trackRepository.getAllTracks().size)
    }
    
    @Test
    fun testMultipleSupportedFiles() = runBlocking {
        localStorage.files.add("content://tree/primary:Music/song1.mp3")
        localStorage.files.add("content://tree/primary:Music/song2.flac")
        
        metadataExtractor.metadataMap["content://tree/primary:Music/song1.mp3"] = AudioMetadata(
            title = "Song 1", artist = "Artist", album = null, durationMs = 120000L, mimeType = "audio/mpeg", sizeBytes = 1000L
        )
        metadataExtractor.metadataMap["content://tree/primary:Music/song2.flac"] = AudioMetadata(
            title = "Song 2", artist = "Artist", album = null, durationMs = 150000L, mimeType = "audio/flac", sizeBytes = 2000L
        )
        
        scanLocalAudioUseCase()
        assertEquals(2, trackRepository.getAllTracks().size)
    }
}


class ValidateUrlUseCaseTest {

    private fun fakeExtractor(supported: Boolean) =
        object : com.example.privatemusiclibrary.domain.extraction.AudioExtractor {

            override fun supports(url: String): Boolean = supported

            override suspend fun extract(
                url: String,
                outputDir: java.io.File,
                progressCallback: (com.example.privatemusiclibrary.domain.model.ImportJobStatus, Int) -> Unit
            ): ExtractedMedia {
                return ExtractedMedia(
                    sourceUrl = url,
                    sourceId = null,
                    platform = "Test",
                    title = "Test audio",
                    artist = null,
                    artworkUrl = null,
                    file = java.io.File(outputDir, "test.mp3")
                )
            }
        }

    @Test
    fun testValidUrl() {
        val useCase = ValidateUrlUseCase(setOf(fakeExtractor(true)))

        val result = useCase("https://example.com/audio.mp3")

        assertTrue(result is ValidateUrlUseCase.ValidationResult.Valid)
    }

    @Test
    fun testInvalidScheme() {
        val useCase = ValidateUrlUseCase(setOf(fakeExtractor(true)))

        val result = useCase("file:///path/to/local/file.mp3")

        assertTrue(result is ValidateUrlUseCase.ValidationResult.Invalid)
    }

    @Test
    fun testUnsupportedSource() {
        val useCase = ValidateUrlUseCase(setOf(fakeExtractor(false)))

        val result = useCase("https://example.com/audio.mp3")

        assertTrue(result is ValidateUrlUseCase.ValidationResult.Invalid)
        assertEquals(
            "This source isn't supported yet.",
            (result as ValidateUrlUseCase.ValidationResult.Invalid).reason
        )
    }
}



class ExtractorRoutingTest {

    @Test
    fun testDirectExtractorRouting() {
        val directExtractor =
            com.example.privatemusiclibrary.data.extraction.DirectAudioUrlExtractor()

        assertTrue(directExtractor.supports("https://example.com/audio.mp3"))
        assertTrue(directExtractor.supports("https://example.com/audio.mp3?token=abc&expires=123"))
        assertTrue(directExtractor.supports("http://example.com/audio.m4a#part1"))

        assertFalse(directExtractor.supports("https://www.instagram.com/p/123/"))
        assertFalse(directExtractor.supports("https://www.youtube.com/watch?v=123"))
        assertFalse(directExtractor.supports("https://example.com/page.html"))
    }
}
