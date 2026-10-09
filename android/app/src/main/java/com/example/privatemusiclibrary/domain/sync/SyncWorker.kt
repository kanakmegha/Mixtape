package com.example.privatemusiclibrary.domain.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncUseCase: SyncUseCase
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val outcome = try {
            syncUseCase()
        } catch (e: Exception) {
            SyncUseCase.Outcome.RETRYABLE_FAILURE
        }
        when (outcome) {
            SyncUseCase.Outcome.SUCCESS -> Result.success()
            // Not signed in / rejected: retrying won't help. Local data is untouched.
            SyncUseCase.Outcome.PERMANENT_FAILURE -> Result.failure()
            SyncUseCase.Outcome.RETRYABLE_FAILURE ->
                if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
