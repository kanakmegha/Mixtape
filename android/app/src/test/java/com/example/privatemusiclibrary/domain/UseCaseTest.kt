package com.example.privatemusiclibrary.domain

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
    
    override suspend fun save(source: File, destinationName: String): StorageResult {
        val uri = "file://$destinationName"
        files.add(uri)
        return StorageResult.Success(uri)
    }

    override suspend fun delete(uri: String): Boolean {
        return files.remove(uri)
    }

    override suspend fun exists(uri: String): Boolean = files.contains(uri)
    override suspend fun size(uri: String): Long? = if (files.contains(uri)) 100L else null
    override fun open(uri: String): InputStream = throw NotImplementedError()
    override suspend fun listAudioFiles(): List<String> = files.toList()
}

class UseCaseTest {
    private lateinit var trackRepository: FakeTrackRepository
    private lateinit var localStorage: FakeLocalAudioStorage

    @Before
    fun setup() {
        trackRepository = FakeTrackRepository()
        localStorage = FakeLocalAudioStorage()
    }

    @Test
    fun testFavoriteUseCases() = runBlocking {
        val track = Track(
            id = "t1", title = "Test", artist = null, creator = null,
            album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
            sourceId = null, localUri = "file://t1.mp3", mimeType = "audio/mpeg",
            durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
            playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
        )
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
        val track = Track(
            id = "t1", title = "Test", artist = null, creator = null,
            album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
            sourceId = null, localUri = "file://t1.mp3", mimeType = "audio/mpeg",
            durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
            playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
        )
        trackRepository.insert(track)
        localStorage.files.add("file://t1.mp3")
        
        val deleteTrack = DeleteTrackUseCase(trackRepository, localStorage)
        val success = deleteTrack("t1")
        
        assertTrue(success)
        assertNull(trackRepository.getById("t1"))
        assertFalse(localStorage.exists("file://t1.mp3"))
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
    @Test
    fun testValidUrl() {
        val extractor = object : com.example.privatemusiclibrary.domain.extraction.AudioExtractor {
            override fun supports(url: String): Boolean = true
            override suspend fun extract(url: String, dest: java.io.File, cb: (Int) -> Unit) {}
        }
        val validateUrlUseCase = ValidateUrlUseCase(setOf(extractor))
        
        val result = validateUrlUseCase("https://example.com/audio.mp3")
        assertTrue(result is ValidateUrlUseCase.ValidationResult.Valid)
    }
    
    @Test
    fun testInvalidScheme() {
        val extractor = object : com.example.privatemusiclibrary.domain.extraction.AudioExtractor {
            override fun supports(url: String): Boolean = true
            override suspend fun extract(url: String, dest: java.io.File, cb: (Int) -> Unit) {}
        }
        val validateUrlUseCase = ValidateUrlUseCase(setOf(extractor))
        
        val result = validateUrlUseCase("file:///path/to/local/file.mp3")
        assertTrue(result is ValidateUrlUseCase.ValidationResult.Invalid)
    }
    
    @Test
    fun testUnsupportedSource() {
        val extractor = object : com.example.privatemusiclibrary.domain.extraction.AudioExtractor {
            override fun supports(url: String): Boolean = false
            override suspend fun extract(url: String, dest: java.io.File, cb: (Int) -> Unit) {}
        }
        val validateUrlUseCase = ValidateUrlUseCase(setOf(extractor))
        
        val result = validateUrlUseCase("https://example.com/audio.mp3")
        assertTrue(result is ValidateUrlUseCase.ValidationResult.Invalid)
        assertEquals(
            "Only supported URLs and content you are authorized to save can be imported. Supported types: Direct audio links (.mp3, .wav, etc)", 
            (result as ValidateUrlUseCase.ValidationResult.Invalid).reason
        )
    }
}
