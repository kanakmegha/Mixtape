package com.example.privatemusiclibrary.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.privatemusiclibrary.R
import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.StorageResult
import com.example.privatemusiclibrary.domain.repository.TrackRepository
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
    private val trackRepository: TrackRepository
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "mixtape_imports"
    }

    private var lastStatusUpdate = 0L
    private var lastStage: ImportJobStatus? = null
    private var lastReportedProgress = -1

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("Mixtape Import", 0, "Processing audio...")
    }

    private fun createForegroundInfo(title: String, progress: Int, statusText: String): ForegroundInfo {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Media Imports",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio extraction and imports"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Importing to Mixtape")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString("jobId") ?: return@withContext Result.failure()
        val job = importJobRepository.getById(jobId) ?: return@withContext Result.failure()

        if (job.status == ImportJobStatus.CANCELLED) {
            return@withContext Result.failure()
        }

        // Elevate to foreground service to protect long-running downloads when backgrounded
        try {
            setForeground(createForegroundInfo(job.sourceUrl, 0, "Validating media source..."))
        } catch (e: Exception) {
            android.util.Log.w("ImportWorker", "Could not start foreground execution: ${e.message}")
        }

        var tempOutputFile: File? = null

        try {
            updateStatus(jobId, ImportJobStatus.VALIDATING, 2)

            val extractor = extractors.find { it.supports(job.sourceUrl) }
            if (extractor == null) {
                updateStatus(jobId, ImportJobStatus.FAILED, 0, "This source isn't supported yet.")
                return@withContext Result.failure()
            }

            updateStatus(jobId, ImportJobStatus.RESOLVING, 5)

            val extractedMedia = extractor.extract(job.sourceUrl, context.cacheDir) { stage, progress ->
                if (!isStopped) {
                    val overallProgress = when (stage) {
                        ImportJobStatus.VALIDATING -> 2
                        ImportJobStatus.RESOLVING -> (5 + (progress * 0.1)).toInt().coerceIn(5, 15)
                        ImportJobStatus.DOWNLOADING -> (15 + (progress * 0.55)).toInt().coerceIn(15, 70)
                        ImportJobStatus.CONVERTING -> (70 + (progress * 0.15)).toInt().coerceIn(70, 85)
                        ImportJobStatus.SAVING -> (85 + (progress * 0.14)).toInt().coerceIn(85, 99)
                        else -> progress.coerceIn(0, 100)
                    }

                    val statusText = when (stage) {
                        ImportJobStatus.VALIDATING -> "Validating URL..."
                        ImportJobStatus.RESOLVING -> "Resolving media metadata..."
                        ImportJobStatus.DOWNLOADING -> if (progress >= 0) "Downloading audio ($progress%)..." else "Downloading audio..."
                        ImportJobStatus.CONVERTING -> "Converting audio format..."
                        ImportJobStatus.SAVING -> "Saving to library..."
                        else -> "Processing..."
                    }

                    throttledUpdateStatus(jobId, stage, overallProgress, job.sourceUrl, statusText)
                }
            }

            tempOutputFile = extractedMedia.file

            if (isStopped) {
                updateStatus(jobId, ImportJobStatus.CANCELLED, 0, "Cancelled by user")
                tempOutputFile?.let { if (it.exists()) it.delete() }
                return@withContext Result.failure()
            }

            // Check for duplicates
            val existingTracks = trackRepository.getAllTracks()
            val isDuplicate = extractedMedia.sourceId != null && existingTracks.any {
                it.sourceId == extractedMedia.sourceId && it.sourcePlatform == extractedMedia.platform
            }

            if (isDuplicate) {
                updateStatus(jobId, ImportJobStatus.FAILED, 100, "This track is already in your library.")
                tempOutputFile?.let { if (it.exists()) it.delete() }
                return@withContext Result.failure()
            }

            updateStatus(jobId, ImportJobStatus.SAVING, 88)

            val filename = generateSafeFilename(extractedMedia)
            val saveResult = localAudioStorage.save(extractedMedia.file, filename)

            if (saveResult !is StorageResult.Success) {
                updateStatus(jobId, ImportJobStatus.FAILED, 88, "Your music storage location is unavailable. Check storage settings.")
                tempOutputFile?.let { if (it.exists()) it.delete() }
                return@withContext Result.failure()
            }

            // Create and persist Track record
            val track = Track(
                id = UUID.randomUUID().toString(),
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

            return@withContext Result.success()

        } catch (e: Exception) {
            val sanitized = com.example.privatemusiclibrary.data.extraction.YtDlpErrorClassifier.sanitizeMessage(e.message)
            android.util.Log.e("ImportWorker", "ImportWorker failed for jobId $jobId: $sanitized", e)
            tempOutputFile?.let { if (it.exists()) it.delete() }

            val error = when {
                e is IllegalStateException && !e.message.isNullOrBlank() -> e.message!!
                else -> com.example.privatemusiclibrary.data.extraction.YtDlpErrorClassifier.classifyError(e)
            }
            updateStatus(jobId, ImportJobStatus.FAILED, 0, error)
            return@withContext Result.failure()
        } finally {
            tempOutputFile?.let {
                if (it.exists()) {
                    try { it.delete() } catch (_: Exception) {}
                }
            }
        }
    }

    private fun throttledUpdateStatus(
        jobId: String,
        stage: ImportJobStatus,
        progress: Int,
        sourceUrl: String,
        statusText: String
    ) {
        val now = System.currentTimeMillis()
        if (stage != lastStage || progress - lastReportedProgress >= 3 || now - lastStatusUpdate >= 300) {
            lastStatusUpdate = now
            lastStage = stage
            lastReportedProgress = progress
            kotlinx.coroutines.runBlocking {
                updateStatus(jobId, stage, progress)
                try {
                    setForeground(createForegroundInfo(sourceUrl, progress, statusText))
                } catch (_: Exception) {}
            }
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
        val truncated = if (sanitized.length > 100) sanitized.substring(0, 100) else sanitized
        return "$truncated.mp3"
    }
}
