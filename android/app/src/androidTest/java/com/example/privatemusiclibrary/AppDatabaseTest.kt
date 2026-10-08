package com.example.privatemusiclibrary.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var folderDao: FolderDao
    private lateinit var playlistDao: PlaylistDao
    private lateinit var playlistTrackDao: PlaylistTrackDao
    private lateinit var importJobDao: ImportJobDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        trackDao = db.trackDao()
        folderDao = db.folderDao()
        playlistDao = db.playlistDao()
        playlistTrackDao = db.playlistTrackDao()
        importJobDao = db.importJobDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testTrackDao() = runBlocking {
        val track = TrackEntity(
            id = "t1", title = "Test Song", artist = "Artist", creator = null,
            album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
            sourceId = null, localUri = "file://t1.mp3", mimeType = "audio/mpeg",
            durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
            playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
        )
        
        // Insert
        trackDao.insert(track)
        val fetched = trackDao.getById("t1")
        assertEquals(track, fetched)
        
        // Update
        trackDao.update(track.copy(isFavorite = true))
        val favorites = trackDao.observeFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals(true, favorites[0].isFavorite)
        
        // Search
        val searchResults = trackDao.search("Test")
        assertEquals(1, searchResults.size)
        
        // Delete
        trackDao.delete(track)
        assertNull(trackDao.getById("t1"))
    }
    
    @Test
    fun testFolderDao() = runBlocking {
        val folder = FolderEntity("f1", "My Folder", 1L)
        folderDao.insert(folder)
        
        val folders = folderDao.observeAll().first()
        assertEquals(1, folders.size)
        assertEquals("My Folder", folders[0].name)
        
        folderDao.update(folder.copy(name = "Renamed Folder"))
        val renamed = folderDao.observeAll().first()
        assertEquals("Renamed Folder", renamed[0].name)
        
        folderDao.delete(folder)
        assertEquals(0, folderDao.observeAll().first().size)
    }

    @Test
    fun testPlaylistDao() = runBlocking {
        val playlist = PlaylistEntity("p1", "My Playlist", null, null, 1L)
        playlistDao.insert(playlist)
        
        val track = TrackEntity(
            id = "t1", title = "Test Song", artist = "Artist", creator = null,
            album = null, thumbnailUri = null, sourceUrl = null, sourcePlatform = null,
            sourceId = null, localUri = "file://t1.mp3", mimeType = "audio/mpeg",
            durationMs = 1000L, fileSizeBytes = 1024L, dateAdded = 1L, lastPlayedAt = null,
            playbackPositionMs = 0L, isFavorite = false, folderId = null, createdAt = 1L, updatedAt = 1L
        )
        trackDao.insert(track)
        
        playlistTrackDao.insert(PlaylistTrackEntity("p1", "t1", 0))
        
        val tracks = playlistTrackDao.observeTracksForPlaylist("p1").first()
        assertEquals(1, tracks.size)
        assertEquals("t1", tracks[0].id)
        
        playlistTrackDao.delete(PlaylistTrackEntity("p1", "t1", 0))
        assertEquals(0, playlistTrackDao.observeTracksForPlaylist("p1").first().size)
    }
    
    @Test
    fun testImportJobDao() = runBlocking {
        val job = ImportJobEntity("j1", "http://example.com", "Title", "QUEUED", 0, null, 1L, 1L)
        importJobDao.insert(job)
        
        var fetched = importJobDao.getById("j1")
        assertEquals("QUEUED", fetched?.status)
        
        importJobDao.update(job.copy(status = "DOWNLOADING", progress = 50))
        fetched = importJobDao.getById("j1")
        assertEquals("DOWNLOADING", fetched?.status)
        assertEquals(50, fetched?.progress)
    }
}
