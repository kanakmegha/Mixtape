package com.example.privatemusiclibrary.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "tracks",
    indices = [
        Index("title"),
        Index("artist"),
        Index("sourcePlatform"),
        Index("sourceId"),
        Index("isFavorite"),
        Index("folderId"),
        Index("dateAdded"),
        Index("lastPlayedAt"),
        Index("trackKey")
    ]
)
data class TrackEntity(
    @PrimaryKey val id: String,
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
