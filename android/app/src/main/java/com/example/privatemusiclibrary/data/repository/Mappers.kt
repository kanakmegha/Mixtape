package com.example.privatemusiclibrary.data.repository

import com.example.privatemusiclibrary.database.TrackEntity
import com.example.privatemusiclibrary.database.FolderEntity
import com.example.privatemusiclibrary.database.PlaylistEntity
import com.example.privatemusiclibrary.database.ImportJobEntity
import com.example.privatemusiclibrary.database.PlaybackStateEntity
import com.example.privatemusiclibrary.database.AppSettingsEntity
import com.example.privatemusiclibrary.domain.model.Track
import com.example.privatemusiclibrary.domain.model.Folder
import com.example.privatemusiclibrary.domain.model.Playlist
import com.example.privatemusiclibrary.domain.model.ImportJob
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.example.privatemusiclibrary.domain.model.PlaybackState
import com.example.privatemusiclibrary.domain.model.AppSettings

fun TrackEntity.toDomain() = Track(
    id = id,
    title = title,
    artist = artist,
    creator = creator,
    album = album,
    thumbnailUri = thumbnailUri,
    sourceUrl = sourceUrl,
    sourcePlatform = sourcePlatform,
    sourceId = sourceId,
    localUri = localUri,
    mimeType = mimeType,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    dateAdded = dateAdded,
    lastPlayedAt = lastPlayedAt,
    playbackPositionMs = playbackPositionMs,
    isFavorite = isFavorite,
    folderId = folderId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Track.toEntity() = TrackEntity(
    id = id,
    title = title,
    artist = artist,
    creator = creator,
    album = album,
    thumbnailUri = thumbnailUri,
    sourceUrl = sourceUrl,
    sourcePlatform = sourcePlatform,
    sourceId = sourceId,
    localUri = localUri,
    mimeType = mimeType,
    durationMs = durationMs,
    fileSizeBytes = fileSizeBytes,
    dateAdded = dateAdded,
    lastPlayedAt = lastPlayedAt,
    playbackPositionMs = playbackPositionMs,
    isFavorite = isFavorite,
    folderId = folderId,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    createdAt = createdAt
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    createdAt = createdAt
)

fun PlaylistEntity.toDomain() = Playlist(
    id = id,
    name = name,
    description = description,
    artworkUri = artworkUri,
    createdAt = createdAt
)

fun Playlist.toEntity() = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    artworkUri = artworkUri,
    createdAt = createdAt
)

fun ImportJobEntity.toDomain() = ImportJob(
    id = id,
    sourceUrl = sourceUrl,
    title = title,
    status = ImportJobStatus.fromString(status),
    progress = progress,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun ImportJob.toEntity() = ImportJobEntity(
    id = id,
    sourceUrl = sourceUrl,
    title = title,
    status = status.name,
    progress = progress,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun PlaybackStateEntity.toDomain() = PlaybackState(
    trackId = trackId,
    positionMs = positionMs,
    updatedAt = updatedAt
)

fun PlaybackState.toEntity() = PlaybackStateEntity(
    id = 1,
    trackId = trackId,
    positionMs = positionMs,
    updatedAt = updatedAt
)

fun AppSettingsEntity.toDomain() = AppSettings(
    storageUri = storageUri,
    defaultPlaybackSpeed = defaultPlaybackSpeed,
    defaultAudioQuality = defaultAudioQuality,
    resumePlayback = resumePlayback
)

fun AppSettings.toEntity() = AppSettingsEntity(
    id = 1,
    storageUri = storageUri,
    defaultPlaybackSpeed = defaultPlaybackSpeed,
    defaultAudioQuality = defaultAudioQuality,
    resumePlayback = resumePlayback
)
