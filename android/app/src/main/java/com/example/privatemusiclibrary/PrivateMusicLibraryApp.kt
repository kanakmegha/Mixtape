package com.example.privatemusiclibrary

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrivateMusicLibraryApp : Application(), Configuration.Provider {
    
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var ytDlpUpdateManager: com.example.privatemusiclibrary.data.extraction.YtDlpUpdateManager
    
    companion object {
        var ytDlpInitialized = false
        var ffmpegInitialized = false
    }

    override fun onCreate() {
        super.onCreate()
        try {
            YoutubeDL.getInstance().init(this)
            ytDlpInitialized = true
            val versionStr = com.example.privatemusiclibrary.data.extraction.YtDlpUpdateManager.getInstalledVersion(this)
            android.util.Log.d("PrivateMusicApp", "[YT_DLP_INIT] SUCCESS (version: $versionStr)")
        } catch (e: Exception) {
            ytDlpInitialized = false
            android.util.Log.e("PrivateMusicApp", "[YT_DLP_INIT] FAILED", e)
        }
        
        try {
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
            ffmpegInitialized = true
            android.util.Log.d("PrivateMusicApp", "[FFMPEG_INIT] SUCCESS")
        } catch (e: Exception) {
            ffmpegInitialized = false
            android.util.Log.e("PrivateMusicApp", "[FFMPEG_INIT] FAILED", e)
        }

        if (ytDlpInitialized) {
            ytDlpUpdateManager.initOrUpdateAsync()
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
