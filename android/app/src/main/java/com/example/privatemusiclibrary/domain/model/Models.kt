package com.example.privatemusiclibrary.domain.model

data class Track(
    val id: String,
    val title: String,
    val artist: String?,
    val creator: String?,
    val album: String?,
    val thumbnailUri: String?,
    val sourceUrl: String?,
    val sourcePlatform: String?,
    val sourceId: String?,
    val localUri: String,
    val mimeType: String,
    val durationMs: Long?,
    val fileSizeBytes: Long?,
    val dateAdded: Long,
    val lastPlayedAt: Long?,
    val playbackPositionMs: Long,
    val isFavorite: Boolean,
    val folderId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val trackKey: String = "",
    val isLocalAvailable: Boolean = true
)

data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long
)

data class Playlist(
    val id: String,
    val name: String,
    val description: String?,
    val artworkUri: String?,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val isDeleted: Boolean = false
)

sealed class SyncStatus {
    object Idle : SyncStatus()
    object Syncing : SyncStatus()
    object Synced : SyncStatus()
    object WaitingForNetwork : SyncStatus()
    data class PendingChanges(val count: Int) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}

enum class ImportJobStatus {
    QUEUED, VALIDATING, RESOLVING, DOWNLOADING, CONVERTING, SAVING, COMPLETED, FAILED, CANCELLED;

    fun isTerminal(): Boolean = this == COMPLETED || this == FAILED || this == CANCELLED

    companion object {
        fun fromString(status: String): ImportJobStatus = try {
            valueOf(status.uppercase())
        } catch (e: Exception) {
            FAILED
        }
    }
}

data class ImportJob(
    val id: String,
    val sourceUrl: String,
    val title: String?,
    val status: ImportJobStatus,
    val progress: Int,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

data class PlaybackState(
    val trackId: String?,
    val positionMs: Long,
    val updatedAt: Long
)

data class AppSettings(
    val storageUri: String? = null,
    val defaultPlaybackSpeed: Float = 1.0f,
    val defaultAudioQuality: String = "192k",
    val resumePlayback: Boolean = true,
    val isStorageConfirmed: Boolean = false
)
