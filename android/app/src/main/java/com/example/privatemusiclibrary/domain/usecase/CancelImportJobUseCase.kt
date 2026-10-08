package com.example.privatemusiclibrary.domain.usecase

import android.content.Context
import androidx.work.WorkManager
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class CancelImportJobUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importJobRepository: ImportJobRepository
) {
    suspend operator fun invoke(jobId: String) {
        val job = importJobRepository.getById(jobId) ?: return
        
        if (job.status != ImportJobStatus.COMPLETED && job.status != ImportJobStatus.FAILED && job.status != ImportJobStatus.CANCELLED) {
            importJobRepository.update(job.copy(
                status = ImportJobStatus.CANCELLED,
                errorMessage = "Cancelled by user",
                updatedAt = System.currentTimeMillis()
            ))
            // Cancel via WorkManager by tag/id if we knew it. But we just set DB to cancelled.
            // The worker checks this state.
            // Wait, WorkManager needs to be cancelled by UUID. We don't have the WorkRequest UUID stored.
            // We can cancel by unique work or tag. It's better to cancel by a tag or just let the worker check the DB state.
            // To be thorough, we can cancel all work... wait, let's just rely on the DB status check inside the loop.
        }
    }
}
