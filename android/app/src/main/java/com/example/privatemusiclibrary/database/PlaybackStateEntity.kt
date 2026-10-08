package com.example.privatemusiclibrary.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_state")
data class PlaybackStateEntity(
    @PrimaryKey val id: Int = 1, // Singleton
    val trackId: String?,
    val positionMs: Long,
    val updatedAt: Long
)
