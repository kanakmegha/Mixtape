package com.example.privatemusiclibrary.di

import com.example.privatemusiclibrary.data.repository.DefaultTrackRepository
import com.example.privatemusiclibrary.data.repository.DefaultFolderRepository
import com.example.privatemusiclibrary.data.repository.DefaultPlaylistRepository
import com.example.privatemusiclibrary.data.repository.DefaultPlaybackRepository
import com.example.privatemusiclibrary.data.repository.DefaultImportJobRepository
import com.example.privatemusiclibrary.data.repository.DefaultSettingsRepository
import com.example.privatemusiclibrary.data.storage.SafLocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.TrackRepository
import com.example.privatemusiclibrary.domain.repository.FolderRepository
import com.example.privatemusiclibrary.domain.repository.PlaylistRepository
import com.example.privatemusiclibrary.domain.repository.PlaybackRepository
import com.example.privatemusiclibrary.domain.repository.ImportJobRepository
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrackRepository(
        impl: DefaultTrackRepository
    ): TrackRepository

    @Binds
    @Singleton
    abstract fun bindFolderRepository(
        impl: DefaultFolderRepository
    ): FolderRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(
        impl: DefaultPlaylistRepository
    ): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        impl: DefaultPlaybackRepository
    ): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindImportJobRepository(
        impl: DefaultImportJobRepository
    ): ImportJobRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: DefaultSettingsRepository
    ): SettingsRepository
    
    @Binds
    @Singleton
    abstract fun bindLocalAudioStorage(
        impl: com.example.privatemusiclibrary.data.storage.DefaultLocalAudioStorage
    ): LocalAudioStorage
    
    @Binds
    @Singleton
    abstract fun bindAudioMetadataExtractor(
        impl: com.example.privatemusiclibrary.data.storage.AndroidAudioMetadataExtractor
    ): com.example.privatemusiclibrary.domain.usecase.AudioMetadataExtractor
    
    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindDirectAudioUrlExtractor(
        impl: com.example.privatemusiclibrary.data.extraction.DirectAudioUrlExtractor
    ): com.example.privatemusiclibrary.domain.extraction.AudioExtractor

    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindYtDlpAudioExtractor(
        impl: com.example.privatemusiclibrary.data.extraction.YtDlpAudioExtractor
    ): com.example.privatemusiclibrary.domain.extraction.AudioExtractor

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        impl: com.example.privatemusiclibrary.data.repository.DefaultSyncRepository
    ): com.example.privatemusiclibrary.domain.repository.SyncRepository
}
