package com.example.privatemusiclibrary.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1, // Singleton
    val storageUri: String?,
    val defaultPlaybackSpeed: Float = 1.0f,
    val defaultAudioQuality: String = "192k",
    val resumePlayback: Boolean = true
)
