package com.example.privatemusiclibrary.domain.sync

import com.example.privatemusiclibrary.domain.repository.SyncRepository
import com.example.privatemusiclibrary.domain.repository.SyncResult
import javax.inject.Inject

class SyncUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {
    enum class Outcome { SUCCESS, RETRYABLE_FAILURE, PERMANENT_FAILURE }

    suspend operator fun invoke(): Outcome {
        return when (val result = syncRepository.sync()) {
            is SyncResult.Success -> Outcome.SUCCESS
            is SyncResult.NetworkError -> Outcome.RETRYABLE_FAILURE
            is SyncResult.AuthError -> Outcome.PERMANENT_FAILURE
            is SyncResult.ServerError -> {
                if (result.code == 408 || result.code == 429 || result.code >= 500) {
                    Outcome.RETRYABLE_FAILURE
                } else {
                    Outcome.PERMANENT_FAILURE
                }
            }
        }
    }
}
