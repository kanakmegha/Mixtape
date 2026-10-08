package com.example.privatemusiclibrary.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        TrackEntity::class,
        FolderEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlaybackStateEntity::class,
        ImportJobEntity::class,
        AppSettingsEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun folderDao(): FolderDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun appSettingsDao(): AppSettingsDao
}
