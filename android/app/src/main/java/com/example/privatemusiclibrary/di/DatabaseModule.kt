package com.example.privatemusiclibrary.di

import android.content.Context
import androidx.room.Room
import com.example.privatemusiclibrary.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create new table with foreign keys and cascade delete
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `playlist_tracks_new` (`playlistId` TEXT NOT NULL, `trackId` TEXT NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
            )
            // Copy data
            db.execSQL(
                "INSERT INTO `playlist_tracks_new` (`playlistId`, `trackId`, `position`) SELECT `playlistId`, `trackId`, `position` FROM `playlist_tracks`"
            )
            // Drop old table
            db.execSQL("DROP TABLE `playlist_tracks`")
            // Rename new table
            db.execSQL("ALTER TABLE `playlist_tracks_new` RENAME TO `playlist_tracks`")
            // Re-create indices
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` (`playlistId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Add trackKey and isLocalAvailable to tracks
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `trackKey` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `tracks` ADD COLUMN `isLocalAvailable` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_trackKey` ON `tracks` (`trackKey`)")

            // Add updatedAt and isDeleted to playlists
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `playlists` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")

            // Create sync_tombstones table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_tombstones` (`entityType` TEXT NOT NULL, `entityId` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`entityType`, `entityId`))"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "private_music_library.db"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    }

    @Provides
    fun provideTrackDao(database: AppDatabase) = database.trackDao()

    @Provides
    fun provideFolderDao(database: AppDatabase) = database.folderDao()

    @Provides
    fun providePlaylistDao(database: AppDatabase) = database.playlistDao()

    @Provides
    fun providePlaylistTrackDao(database: AppDatabase) = database.playlistTrackDao()

    @Provides
    fun providePlaybackStateDao(database: AppDatabase) = database.playbackStateDao()

    @Provides
    fun provideImportJobDao(database: AppDatabase) = database.importJobDao()

    @Provides
    fun provideAppSettingsDao(database: AppDatabase) = database.appSettingsDao()

    @Provides
    fun provideSyncTombstoneDao(database: AppDatabase) = database.syncTombstoneDao()
}
