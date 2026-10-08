package com.example.privatemusiclibrary.di

import com.example.privatemusiclibrary.data.player.Media3AudioPlayer
import com.example.privatemusiclibrary.domain.player.AudioPlayer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindAudioPlayer(
        media3AudioPlayer: Media3AudioPlayer
    ): AudioPlayer
}
