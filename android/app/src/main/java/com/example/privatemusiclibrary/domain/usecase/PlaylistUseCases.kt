package com.example.privatemusiclibrary.domain.usecase

import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject

class GetPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(): Flow<List<Playlist>> = playlistRepository.observeAll()
}

class GetPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(id: String): Flow<Playlist?> = playlistRepository.observeById(id)
}

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Playlist name cannot be empty")
        val id = UUID.randomUUID().toString()
        val playlist = Playlist(
            id = id,
            name = trimmed,
            description = null,
            artworkUri = null,
            createdAt = System.currentTimeMillis()
        )
        playlistRepository.insert(playlist)
        return id
    }
}

class RenamePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) throw IllegalArgumentException("Playlist name cannot be empty")
        val playlist = playlistRepository.getById(id) ?: throw IllegalArgumentException("Playlist not found")
        playlistRepository.update(playlist.copy(name = trimmed))
    }
}

class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: String) {
        val playlist = playlistRepository.getById(id) ?: return
        playlistRepository.delete(playlist)
    }
}

class GetPlaylistTracksUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(playlistId: String): Flow<List<Track>> {
        return playlistRepository.observeTracksForPlaylist(playlistId)
    }
}

class AddTrackToPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: String, trackId: String) {
        // Find current max position or just append
        // Using a high number for now, or could query the max position
        playlistRepository.addTrackToPlaylist(playlistId, trackId, position = System.currentTimeMillis().toInt())
    }
}

class RemoveTrackFromPlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: String, trackId: String) {
        playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
    }
}

class ReorderPlaylistTracksUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(playlistId: String, tracks: List<Track>) {
        tracks.forEachIndexed { index, track ->
            playlistRepository.updateTrackPosition(playlistId, track.id, index)
        }
    }
}

class GetPlaylistsForTrackUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(trackId: String): Flow<List<String>> {
        return playlistRepository.observePlaylistsForTrack(trackId)
    }
}
