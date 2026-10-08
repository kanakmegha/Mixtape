package com.example.privatemusiclibrary.data.extraction

import android.net.Uri
import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

import dagger.hilt.android.qualifiers.ApplicationContext

class YtDlpAudioExtractor @Inject constructor(
    @ApplicationContext private val context: android.content.Context
) : AudioExtractor {

    override fun supports(url: String): Boolean {
        // Simple heuristic: accept known media hostnames
        val host = try {
            java.net.URL(url).host.lowercase()
        } catch (e: Exception) {
            return false
        }
        
        return host.contains("youtube.com") || 
               host.contains("youtu.be") || 
               host.contains("soundcloud.com") ||
               host.contains("instagram.com")
    }

    override suspend fun extract(
        url: String,
        outputDir: File,
        progressCallback: (Int) -> Unit
    ): com.example.privatemusiclibrary.domain.extraction.ExtractedMedia {
        if (!com.example.privatemusiclibrary.PrivateMusicLibraryApp.ytDlpInitialized || !com.example.privatemusiclibrary.PrivateMusicLibraryApp.ffmpegInitialized) {
            val errorMsg = "Extraction engine is not initialized. Cannot extract audio."
            android.util.Log.e("EXTRACTOR", errorMsg)
            throw IllegalStateException(errorMsg)
        }
        
        return withContext(Dispatchers.IO) {
            // Optional: update yt-dlp
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context, com.yausername.youtubedl_android.YoutubeDL.UpdateChannel.STABLE)
            } catch (e: Exception) {
                // ignore
            }

            var normalizedUrl = url
            if (normalizedUrl.contains("youtube.com/watch") || normalizedUrl.contains("youtu.be/")) {
                normalizedUrl = normalizedUrl.substringBefore("&list=").substringBefore("?list=")
            }

            // 1. Fetch metadata
            var metadataTitle = "Unknown Title"
            var metadataArtist: String? = null
            var metadataSourceId: String? = null
            var metadataArtworkUrl: String? = null
            var metadataPlatform = when {
                normalizedUrl.contains("instagram.com") -> "Instagram"
                normalizedUrl.contains("youtube.com") || normalizedUrl.contains("youtu.be") -> "YouTube"
                normalizedUrl.contains("soundcloud.com") -> "SoundCloud"
                else -> "Unknown"
            }

            try {
                val info = YoutubeDL.getInstance().getInfo(normalizedUrl)
                metadataTitle = info.title ?: "Unknown Title"
                metadataArtist = info.uploader
                metadataSourceId = info.id
                metadataArtworkUrl = info.thumbnail
            } catch (e: Exception) {
                android.util.Log.w("EXTRACTOR", "Failed to extract metadata: ${e.message}")
            }

            // Sanitize metadata
            metadataTitle = metadataTitle.trim().replace(Regex("\\s+"), " ")
            if (metadataTitle.startsWith("https://") || metadataTitle.startsWith("http://") || metadataTitle.isBlank()) {
                metadataTitle = if (metadataPlatform == "Instagram") "Instagram Reel" else "Unknown Audio"
            }
            
            metadataArtist = metadataArtist?.trim()?.replace(Regex("\\s+"), " ")?.removePrefix("@")
            if (metadataArtist.isNullOrBlank()) {
                metadataArtist = "Unknown Artist"
            }

            // 2. Extract Audio
            val baseName = "import_${java.util.UUID.randomUUID()}"
            val request = YoutubeDLRequest(normalizedUrl)
            request.addOption("--force-ipv4")
            request.addOption("-f", "bestaudio/best")
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "192")
            request.addOption("--no-playlist")
            
            val tempOutputPath = File(outputDir, "$baseName.%(ext)s").absolutePath
            request.addOption("-o", tempOutputPath)

            try {
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    progressCallback(progress.toInt())
                }
                
                val extractedFile = outputDir.listFiles()?.find { it.nameWithoutExtension == baseName }
                if (extractedFile != null && extractedFile.exists() && extractedFile.length() > 0) {
                    return@withContext com.example.privatemusiclibrary.domain.extraction.ExtractedMedia(
                        sourceUrl = normalizedUrl,
                        sourceId = metadataSourceId,
                        platform = metadataPlatform,
                        title = metadataTitle,
                        artist = metadataArtist,
                        artworkUrl = metadataArtworkUrl,
                        file = extractedFile
                    )
                } else {
                    throw IllegalStateException("yt-dlp failed to produce an audio file")
                }
            } catch (e: Exception) {
                if (e.message?.contains("Errno 7") == true || e.message?.contains("No address associated with hostname") == true) {
                    throw IllegalStateException("Network/DNS error: unable to reach the source. Check your internet connection and try again.", e)
                }
                if (e.message?.contains("login required") == true || e.message?.contains("rate-limit reached") == true) {
                    throw IllegalStateException("This content isn't available without access to the source.", e)
                }
                throw e
            }
        }
    }
}
