package com.example.privatemusiclibrary.domain.usecase

import com.example.privatemusiclibrary.domain.model.Folder
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.model.AppSettings
import com.example.privatemusiclibrary.domain.repository.FolderRepository
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import com.example.privatemusiclibrary.domain.repository.TrackRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(): Flow<List<Track>> = trackRepository.observeAll()
}

class GetFavoriteTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(): Flow<List<Track>> = trackRepository.observeFavorites()
}

class GetRecentlyPlayedTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    operator fun invoke(): Flow<List<Track>> = trackRepository.observeRecentlyPlayed()
}

class SearchTracksUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(query: String): List<Track> = trackRepository.search(query)
}

class AddFavoriteUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(trackId: String) {
        val track = trackRepository.getById(trackId)
        if (track != null) {
            trackRepository.update(track.copy(isFavorite = true, updatedAt = System.currentTimeMillis()))
        }
    }
}

class RemoveFavoriteUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(trackId: String) {
        val track = trackRepository.getById(trackId)
        if (track != null) {
            trackRepository.update(track.copy(isFavorite = false, updatedAt = System.currentTimeMillis()))
        }
    }
}

class DeleteTrackUseCase @Inject constructor(
    private val trackRepository: TrackRepository,
    private val localAudioStorage: LocalAudioStorage
) {
    suspend operator fun invoke(trackId: String): Boolean {
        val track = trackRepository.getById(trackId) ?: return false
        
        // Delete local file first
        val deleted = localAudioStorage.delete(track.localUri)
        if (!deleted) {
            // Check if it exists at all
            if (localAudioStorage.exists(track.localUri)) {
                return false // Could not delete existing file
            }
        }
        
        // Delete database record
        trackRepository.delete(track)
        return true
    }
}

class CreateFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository
) {
    suspend operator fun invoke(id: String, name: String) {
        folderRepository.insert(Folder(id, name, System.currentTimeMillis()))
    }
}

class DeleteFolderUseCase @Inject constructor(
    private val folderRepository: FolderRepository,
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(folderId: String) {
        // First unassign all tracks
        // Need a way to unassign tracks or we can just delete the folder
        // For now, just delete the folder. Tracks will have a dangling folderId if no foreign key cascade
        folderRepository.delete(Folder(folderId, "", 0L))
    }
}

class MoveTrackToFolderUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke(trackId: String, folderId: String?) {
        val track = trackRepository.getById(trackId)
        if (track != null) {
            trackRepository.update(track.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
        }
    }
}

class SetStorageLocationUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(uri: String) {
        val settings = settingsRepository.getSettings()
        settingsRepository.updateSettings(settings.copy(storageUri = uri))
    }
}

class GetStorageInfoUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val trackRepository: TrackRepository
) {
    // Basic stub for getting storage info
    suspend operator fun invoke(): AppSettings = settingsRepository.getSettings()
}
