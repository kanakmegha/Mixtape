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
    private val scanLocalAudioUseCase: ScanLocalAudioUseCase
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
                updateStatus(jobId, ImportJobStatus.FAILED, 0, "No supported extractor found for this URL")
                return@withContext Result.failure()
            }
            
            updateStatus(jobId, ImportJobStatus.DOWNLOADING, 10)
            
            val tempFile = File(context.cacheDir, "import_${UUID.randomUUID()}.audio")
            
            try {
                extractor.extract(job.sourceUrl, tempFile) { progress ->
                    // Map 0-100 to 10-80
                    if (progress >= 0 && !isStopped) {
                        val scaledProgress = 10 + (progress * 0.7).toInt()
                        // Launch fire-and-forget or just update DB
                        // Be careful not to overwhelm DB with progress updates, but ok for now
                        kotlinx.coroutines.runBlocking {
                            updateStatus(jobId, ImportJobStatus.DOWNLOADING, scaledProgress)
                        }
                    }
                }
                
                if (isStopped) {
                    updateStatus(jobId, ImportJobStatus.CANCELLED, 0, "Cancelled by user")
                    return@withContext Result.failure()
                }
                
                updateStatus(jobId, ImportJobStatus.SAVING, 80)
                
                // Save to local storage
                val filename = generateSafeFilename(job.sourceUrl)
                val saveResult = localAudioStorage.save(tempFile, filename)
                
                if (saveResult !is com.example.privatemusiclibrary.domain.repository.StorageResult.Success) {
                    updateStatus(jobId, ImportJobStatus.FAILED, 80, "Failed to save file to local storage")
                    return@withContext Result.failure()
                }
                
                updateStatus(jobId, ImportJobStatus.COMPLETED, 100)
                
                // Trigger scanner to register the track in Room so Library picks it up
                scanLocalAudioUseCase()
                
                return@withContext Result.success()
                
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        } catch (e: Exception) {
            val error = e.message ?: "Unknown error occurred during extraction"
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
    
    private fun generateSafeFilename(url: String): String {
        val lastSegment = url.substringAfterLast("/", "imported_audio")
        val sanitized = lastSegment.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return if (sanitized.contains(".")) sanitized else "$sanitized.mp3"
    }
}
