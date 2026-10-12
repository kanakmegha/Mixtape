package com.example.privatemusiclibrary.domain.repository

import com.example.privatemusiclibrary.domain.model.SyncStatus
import kotlinx.coroutines.flow.StateFlow

sealed class SyncResult {
    object Success : SyncResult()
    data class NetworkError(val message: String) : SyncResult()
    data class AuthError(val message: String) : SyncResult()
    data class ServerError(val code: Int, val message: String) : SyncResult()
}

interface SyncRepository {
    val syncStatus: StateFlow<SyncStatus>
    val lastSyncTime: StateFlow<Long>
    suspend fun sync(): SyncResult
    suspend fun recordPlaylistDeletion(playlistId: String)
    suspend fun recordFavoriteDeletion(trackKey: String)
}
