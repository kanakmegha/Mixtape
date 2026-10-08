package com.example.privatemusiclibrary.domain.repository

import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.model.Folder
import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.PlaybackState
import com.example.privatemusiclibrary.domain.model.ImportJob
import com.example.privatemusiclibrary.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface TrackRepository {
    fun observeAll(): Flow<List<Track>>
    fun observeFavorites(): Flow<List<Track>>
    fun observeRecentlyAdded(): Flow<List<Track>>
    fun observeRecentlyPlayed(): Flow<List<Track>>
    suspend fun getById(id: String): Track?
    suspend fun insert(track: Track)
    suspend fun update(track: Track)
    suspend fun delete(track: Track)
    suspend fun search(query: String): List<Track>
    suspend fun getAllTracks(): List<Track>
}

interface FolderRepository {
    fun observeAll(): Flow<List<Folder>>
    suspend fun insert(folder: Folder)
    suspend fun update(folder: Folder)
    suspend fun delete(folder: Folder)
}

interface PlaylistRepository {
    fun observeAll(): Flow<List<Playlist>>
    fun observeById(id: String): Flow<Playlist?>
    suspend fun getById(id: String): Playlist?
    suspend fun insert(playlist: Playlist)
    suspend fun update(playlist: Playlist)
    suspend fun delete(playlist: Playlist)
    fun observeTracksForPlaylist(playlistId: String): Flow<List<Track>>
    fun observePlaylistsForTrack(trackId: String): Flow<List<String>>
    fun observeTrackCount(playlistId: String): Flow<Int>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String, position: Int)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun updateTrackPosition(playlistId: String, trackId: String, position: Int)
}

interface PlaybackRepository {
    fun observeState(): Flow<PlaybackState?>
    suspend fun getState(): PlaybackState?
    suspend fun updateState(state: PlaybackState)
}

interface ImportJobRepository {
    fun observeAll(): Flow<List<ImportJob>>
    suspend fun getById(id: String): ImportJob?
    suspend fun insert(job: ImportJob)
    suspend fun update(job: ImportJob)
}

interface SettingsRepository {
    fun observeSettings(): Flow<AppSettings>
    suspend fun getSettings(): AppSettings
    suspend fun updateSettings(settings: AppSettings)
}
