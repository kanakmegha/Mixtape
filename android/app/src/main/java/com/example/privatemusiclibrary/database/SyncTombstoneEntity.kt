package com.example.privatemusiclibrary.database

import androidx.room.Entity

@Entity(
    tableName = "sync_tombstones",
    primaryKeys = ["entityType", "entityId"]
)
data class SyncTombstoneEntity(
    val entityType: String, // "playlist" or "favorite"
    val entityId: String,   // playlistId or trackKey
    val deletedAt: Long
)
