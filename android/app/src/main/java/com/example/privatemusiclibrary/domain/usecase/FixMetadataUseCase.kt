package com.example.privatemusiclibrary.domain.usecase

import com.example.privatemusiclibrary.domain.repository.TrackRepository
import com.example.privatemusiclibrary.domain.model.Track
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FixMetadataUseCase @Inject constructor(
    private val trackRepository: TrackRepository
) {
    suspend operator fun invoke() = withContext(Dispatchers.IO) {
        val tracks = trackRepository.getAllTracks()
        for (track in tracks) {
            val isBadTitle = track.title.startsWith("_stkn_") || track.title.contains("?stkn=")
            val isBadArtist = track.artist == "Unknown Artist" || track.artist.isNullOrBlank()
            
            if (isBadTitle || isBadArtist) {
                try {
                    val url = track.sourceUrl ?: continue
                    val info = YoutubeDL.getInstance().getInfo(url)
                    
                    val newTitle = if (isBadTitle) {
                        info.title ?: "Instagram Reel"
                    } else {
                        track.title
                    }
                    
                    val newArtist = if (isBadArtist) {
                        info.uploader ?: info.uploaderId ?: "Unknown Artist"
                    } else {
                        track.artist
                    }
                    
                    if (newTitle != track.title || newArtist != track.artist) {
                        trackRepository.update(
                            track.copy(
                                title = newTitle,
                                artist = newArtist,
                                thumbnailUri = info.thumbnail ?: track.thumbnailUri
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Fallback without network if network fails
                    if (isBadTitle) {
                        trackRepository.update(track.copy(title = "Instagram Reel"))
                    }
                }
            }
        }
    }
}
