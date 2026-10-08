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

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "private_music_library.db"
        ).build()
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
}
