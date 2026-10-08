package com.example.privatemusiclibrary.domain.usecase

import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

interface AudioMetadataExtractor {
    suspend fun extractMetadata(uri: String): AudioMetadata?
}

data class AudioMetadata(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val mimeType: String,
    val sizeBytes: Long?
)

class ScanLocalAudioUseCase @Inject constructor(
    private val localAudioStorage: LocalAudioStorage,
    private val trackRepository: TrackRepository,
    private val metadataExtractor: AudioMetadataExtractor
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val audioUris = localAudioStorage.listAudioFiles()
        
        val existingTracks = trackRepository.getAllTracks()
        val existingLocalUris = existingTracks.map { it.localUri }.toSet()
        
        val newUris = audioUris.filter { !existingLocalUris.contains(it) }
        
        for (uriStr in newUris) {
            val metadata = metadataExtractor.extractMetadata(uriStr) ?: continue
            
            val track = Track(
                id = UUID.randomUUID().toString(),
                title = metadata.title,
                artist = metadata.artist,
                creator = metadata.artist,
                album = metadata.album,
                thumbnailUri = null,
                sourceUrl = null,
                sourcePlatform = null,
                sourceId = null,
                localUri = uriStr,
                mimeType = metadata.mimeType,
                durationMs = metadata.durationMs,
                fileSizeBytes = metadata.sizeBytes,
                dateAdded = System.currentTimeMillis(),
                lastPlayedAt = null,
                playbackPositionMs = 0L,
                isFavorite = false,
                folderId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            trackRepository.insert(track)
        }
    }
}
