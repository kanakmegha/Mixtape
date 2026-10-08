package com.example.privatemusiclibrary.domain.usecase

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.privatemusiclibrary.data.worker.ImportWorker
import com.example.privatemusiclibrary.domain.model.ImportJob
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class ImportUrlUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val importJobRepository: ImportJobRepository,
    private val validateUrlUseCase: ValidateUrlUseCase
) {
    suspend operator fun invoke(url: String): Result<String> {
        val validation = validateUrlUseCase(url)
        if (validation is ValidateUrlUseCase.ValidationResult.Invalid) {
            return Result.failure(Exception(validation.reason))
        }

        val jobId = UUID.randomUUID().toString()
        val job = ImportJob(
            id = jobId,
            sourceUrl = url,
            title = null,
            status = ImportJobStatus.QUEUED,
            progress = 0,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        importJobRepository.insert(job)
        
        val inputData = Data.Builder()
            .putString("jobId", jobId)
            .build()
            
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueue(workRequest)
        
        return Result.success(jobId)
    }
}
