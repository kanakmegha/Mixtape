package com.example.privatemusiclibrary.domain.usecase

import android.content.Context
import androidx.work.WorkManager
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

open class CancelImportJobUseCase @Inject constructor(
    @ApplicationContext private val context: Context?,
    private val importJobRepository: ImportJobRepository?
) {
    constructor() : this(null, null)

    open suspend operator fun invoke(jobId: String) {
        val repo = importJobRepository ?: return
        val job = repo.getById(jobId) ?: return
        
        if (job.status != ImportJobStatus.COMPLETED && job.status != ImportJobStatus.FAILED && job.status != ImportJobStatus.CANCELLED) {
            repo.update(job.copy(
                status = ImportJobStatus.CANCELLED,
                errorMessage = "Cancelled by user",
                updatedAt = System.currentTimeMillis()
            ))
        }
    }
}
