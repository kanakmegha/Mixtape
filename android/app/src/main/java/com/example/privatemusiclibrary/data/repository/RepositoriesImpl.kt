package com.example.privatemusiclibrary.data.repository

import com.example.privatemusiclibrary.database.TrackDao
import com.example.privatemusiclibrary.database.FolderDao
import com.example.privatemusiclibrary.database.PlaylistDao
import com.example.privatemusiclibrary.database.PlaylistTrackDao
import com.example.privatemusiclibrary.database.PlaybackStateDao
import com.example.privatemusiclibrary.database.ImportJobDao
import com.example.privatemusiclibrary.database.AppSettingsDao
import com.example.privatemusiclibrary.database.PlaylistTrackEntity
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.model.Folder
import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.PlaybackState
import com.example.privatemusiclibrary.domain.model.ImportJob
import com.example.privatemusiclibrary.domain.model.AppSettings
import com.example.privatemusiclibrary.domain.repository.TrackRepository
import com.example.privatemusiclibrary.domain.repository.FolderRepository
import com.example.privatemusiclibrary.domain.repository.PlaylistRepository
import com.example.privatemusiclibrary.domain.repository.PlaybackRepository
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DefaultTrackRepository @Inject constructor(
    private val trackDao: TrackDao
) : TrackRepository {
    override fun observeAll(): Flow<List<Track>> = trackDao.observeAll().map { it.map { t -> t.toDomain() } }
    override fun observeFavorites(): Flow<List<Track>> = trackDao.observeFavorites().map { it.map { t -> t.toDomain() } }
    override fun observeRecentlyAdded(): Flow<List<Track>> = trackDao.observeRecentlyAdded().map { it.map { t -> t.toDomain() } }
    override fun observeRecentlyPlayed(): Flow<List<Track>> = trackDao.observeRecentlyPlayed().map { it.map { t -> t.toDomain() } }
    
    override suspend fun getById(id: String): Track? = trackDao.getById(id)?.toDomain()
    override suspend fun insert(track: Track) { trackDao.insert(track.toEntity()) }
    override suspend fun update(track: Track) { trackDao.update(track.toEntity()) }
    override suspend fun delete(track: Track) { trackDao.delete(track.toEntity()) }
    override suspend fun search(query: String): List<Track> = trackDao.search(query).map { it.toDomain() }
    override suspend fun getAllTracks(): List<Track> = trackDao.getAll().map { it.toDomain() }
}

class DefaultFolderRepository @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {
    override fun observeAll(): Flow<List<Folder>> = folderDao.observeAll().map { it.map { f -> f.toDomain() } }
    override suspend fun insert(folder: Folder) { folderDao.insert(folder.toEntity()) }
    override suspend fun update(folder: Folder) { folderDao.update(folder.toEntity()) }
    override suspend fun delete(folder: Folder) { folderDao.delete(folder.toEntity()) }
}

class DefaultPlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val playlistTrackDao: PlaylistTrackDao
) : PlaylistRepository {
    override fun observeAll(): Flow<List<Playlist>> = playlistDao.observeAll().map { it.map { p -> p.toDomain() } }
    override suspend fun insert(playlist: Playlist) { playlistDao.insert(playlist.toEntity()) }
    override suspend fun update(playlist: Playlist) { playlistDao.update(playlist.toEntity()) }
    override suspend fun delete(playlist: Playlist) { playlistDao.delete(playlist.toEntity()) }
    
    override fun observeTracksForPlaylist(playlistId: String): Flow<List<Track>> {
        return playlistTrackDao.observeTracksForPlaylist(playlistId).map { it.map { t -> t.toDomain() } }
    }
    
    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String, position: Int) {
        playlistTrackDao.insert(PlaylistTrackEntity(playlistId, trackId, position))
    }
    
    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistTrackDao.delete(PlaylistTrackEntity(playlistId, trackId, 0)) // Note: position might not be matched in delete depending on PK. PK is playlistId, trackId so it deletes by those.
    }
}

class DefaultPlaybackRepository @Inject constructor(
    private val playbackStateDao: PlaybackStateDao
) : PlaybackRepository {
    override fun observeState(): Flow<PlaybackState?> = playbackStateDao.observeState().map { it.firstOrNull()?.toDomain() }
    override suspend fun getState(): PlaybackState? = playbackStateDao.getState()?.toDomain()
    override suspend fun updateState(state: PlaybackState) { playbackStateDao.insertOrUpdate(state.toEntity()) }
}

class DefaultImportJobRepository @Inject constructor(
    private val importJobDao: ImportJobDao
) : ImportJobRepository {
    override fun observeAll(): Flow<List<ImportJob>> = importJobDao.observeAll().map { it.map { j -> j.toDomain() } }
    override suspend fun getById(id: String): ImportJob? = importJobDao.getById(id)?.toDomain()
    override suspend fun insert(job: ImportJob) { importJobDao.insert(job.toEntity()) }
    override suspend fun update(job: ImportJob) { importJobDao.update(job.toEntity()) }
}

class DefaultSettingsRepository @Inject constructor(
    private val appSettingsDao: AppSettingsDao
) : SettingsRepository {
    override fun observeSettings(): Flow<AppSettings> = appSettingsDao.observeSettings().map { 
        it.firstOrNull()?.toDomain() ?: AppSettings(null, 1.0f, "192k", true)
    }
    
    override suspend fun getSettings(): AppSettings {
        return appSettingsDao.getSettings()?.toDomain() ?: AppSettings(null, 1.0f, "192k", true)
    }
    
    override suspend fun updateSettings(settings: AppSettings) {
        appSettingsDao.insertOrUpdate(settings.toEntity())
    }
}
