package com.example.privatemusiclibrary.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import com.example.privatemusiclibrary.domain.usecase.ScanLocalAudioUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@HiltWorker
class ImportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val importJobRepository: ImportJobRepository,
    private val extractors: Set<@JvmSuppressWildcards AudioExtractor>,
    private val localAudioStorage: LocalAudioStorage,
    private val trackRepository: com.example.privatemusiclibrary.domain.repository.TrackRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString("jobId") ?: return@withContext Result.failure()
        
        val job = importJobRepository.getById(jobId) ?: return@withContext Result.failure()
        
        if (job.status == ImportJobStatus.CANCELLED) {
            return@withContext Result.failure() // Already cancelled
        }

        try {
            updateStatus(jobId, ImportJobStatus.VALIDATING, 0)
            
            val extractor = extractors.find { it.supports(job.sourceUrl) }
            if (extractor == null) {
                updateStatus(jobId, ImportJobStatus.FAILED, 0, "This source isn't supported yet.")
                return@withContext Result.failure()
            }
            
            updateStatus(jobId, ImportJobStatus.DOWNLOADING, 10)
            
            val extractedMedia = extractor.extract(job.sourceUrl, context.cacheDir) { progress ->
                if (progress >= 0 && !isStopped) {
                    val scaledProgress = 10 + (progress * 0.7).toInt()
                    kotlinx.coroutines.runBlocking {
                        updateStatus(jobId, ImportJobStatus.DOWNLOADING, scaledProgress)
                    }
                }
            }
            
            if (isStopped) {
                updateStatus(jobId, ImportJobStatus.CANCELLED, 0, "Cancelled by user")
                if (extractedMedia.file.exists()) extractedMedia.file.delete()
                return@withContext Result.failure()
            }

            // Check for duplicates
            val existingTracks = trackRepository.getAllTracks()
            val isDuplicate = extractedMedia.sourceId != null && existingTracks.any { 
                it.sourceId == extractedMedia.sourceId && it.sourcePlatform == extractedMedia.platform 
            }

            if (isDuplicate) {
                updateStatus(jobId, ImportJobStatus.FAILED, 100, "This track is already in your library.")
                if (extractedMedia.file.exists()) extractedMedia.file.delete()
                return@withContext Result.failure()
            }
            
            updateStatus(jobId, ImportJobStatus.SAVING, 80)
            
            val filename = generateSafeFilename(extractedMedia)
            val saveResult = localAudioStorage.save(extractedMedia.file, filename)
            
            if (saveResult !is com.example.privatemusiclibrary.domain.repository.StorageResult.Success) {
                updateStatus(jobId, ImportJobStatus.FAILED, 80, "Your music storage location is unavailable.")
                if (extractedMedia.file.exists()) extractedMedia.file.delete()
                return@withContext Result.failure()
            }

            // Create Track directly
            val track = com.example.privatemusiclibrary.domain.model.Track(
                id = java.util.UUID.randomUUID().toString(),
                title = extractedMedia.title,
                artist = extractedMedia.artist,
                creator = extractedMedia.artist,
                album = null,
                thumbnailUri = extractedMedia.artworkUrl,
                sourceUrl = extractedMedia.sourceUrl,
                sourcePlatform = extractedMedia.platform,
                sourceId = extractedMedia.sourceId,
                localUri = saveResult.uri,
                mimeType = "audio/mpeg",
                durationMs = null,
                fileSizeBytes = localAudioStorage.size(saveResult.uri),
                dateAdded = System.currentTimeMillis(),
                lastPlayedAt = null,
                playbackPositionMs = 0L,
                isFavorite = false,
                folderId = null,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            
            trackRepository.insert(track)
            
            updateStatus(jobId, ImportJobStatus.COMPLETED, 100)
            
            if (extractedMedia.file.exists()) {
                extractedMedia.file.delete()
            }
            
            return@withContext Result.success()
            
        } catch (e: Exception) {
            val rawMessage = e.message?.lowercase() ?: ""
            val error = when {
                rawMessage.contains("unsupported") -> "This source isn't supported yet."
                rawMessage.contains("forbidden") || rawMessage.contains("403") || rawMessage.contains("unauthorized") || rawMessage.contains("sign in") || rawMessage.contains("login") -> "This content isn't available without access to the source."
                rawMessage.contains("network") || rawMessage.contains("unknownhost") || rawMessage.contains("timeout") || rawMessage.contains("errno 7") -> "Couldn't reach the source. Check your connection and try again."
                rawMessage.contains("rate-limit") -> "The source is temporarily limiting requests. Try again later."
                rawMessage.contains("storage") || rawMessage.contains("space") -> "Your music storage location is unavailable."
                else -> "We couldn't import this media. Try again or use another URL."
            }
            updateStatus(jobId, ImportJobStatus.FAILED, 0, error)
            return@withContext Result.failure()
        }
    }
    
    private suspend fun updateStatus(jobId: String, status: ImportJobStatus, progress: Int, error: String? = null) {
        val job = importJobRepository.getById(jobId) ?: return
        importJobRepository.update(job.copy(
            status = status, 
            progress = progress, 
            errorMessage = error,
            updatedAt = System.currentTimeMillis()
        ))
    }
    
    private fun generateSafeFilename(media: com.example.privatemusiclibrary.domain.extraction.ExtractedMedia): String {
        val baseName = if (media.artist != null && media.artist != "Unknown Artist") {
            "${media.artist} - ${media.title}"
        } else {
            media.title
        }
        val sanitized = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        // Enforce max length
        val truncated = if (sanitized.length > 100) sanitized.substring(0, 100) else sanitized
        return "$truncated.mp3"
    }
}
