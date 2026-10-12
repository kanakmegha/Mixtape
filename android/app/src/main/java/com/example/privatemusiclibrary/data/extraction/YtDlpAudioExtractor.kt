package com.example.privatemusiclibrary.data.extraction

import com.example.privatemusiclibrary.PrivateMusicLibraryApp
import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import com.example.privatemusiclibrary.domain.extraction.ExtractedMedia
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.example.privatemusiclibrary.domain.extraction.MediaPlatform
import com.example.privatemusiclibrary.domain.extraction.SourceResolutionResult
import com.example.privatemusiclibrary.domain.extraction.SupportedSourceResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

class YtDlpAudioExtractor @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val updateManager: YtDlpUpdateManager
) : AudioExtractor {

    override fun supports(url: String): Boolean {
        return when (val res = SupportedSourceResolver.resolve(url)) {
            is SourceResolutionResult.Supported -> {
                res.platform == MediaPlatform.YOUTUBE ||
                res.platform == MediaPlatform.INSTAGRAM ||
                res.platform == MediaPlatform.SOUNDCLOUD
            }
            is SourceResolutionResult.Unsupported -> false
        }
    }

    private fun ensureInitialized() {
        if (!PrivateMusicLibraryApp.ytDlpInitialized) {
            try {
                YoutubeDL.getInstance().init(context)
                PrivateMusicLibraryApp.ytDlpInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("YtDlpAudioExtractor", "Failed to lazy-init yt-dlp", e)
            }
        }
        if (!PrivateMusicLibraryApp.ffmpegInitialized) {
            try {
                com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
                PrivateMusicLibraryApp.ffmpegInitialized = true
            } catch (e: Exception) {
                android.util.Log.e("YtDlpAudioExtractor", "Failed to lazy-init FFmpeg", e)
            }
        }
        if (!PrivateMusicLibraryApp.ytDlpInitialized || !PrivateMusicLibraryApp.ffmpegInitialized) {
            throw IllegalStateException("Extraction engine could not be initialized on this device.")
        }
    }

    override suspend fun extract(
        url: String,
        outputDir: File,
        progressCallback: (stage: ImportJobStatus, progress: Int) -> Unit
    ): ExtractedMedia = withContext(Dispatchers.IO) {
        ensureInitialized()

        val resolved = SupportedSourceResolver.resolve(url)
        val canonicalUrl = if (resolved is SourceResolutionResult.Supported) resolved.canonicalUrl else url.trim()

        val isYouTube = (resolved as? SourceResolutionResult.Supported)?.platform == MediaPlatform.YOUTUBE ||
                canonicalUrl.contains("youtube.com") || canonicalUrl.contains("youtu.be")
        updateManager.ensureReadyForExtraction(isYouTube = isYouTube)

        progressCallback(ImportJobStatus.VALIDATING, 0)

        // 1. Fetch metadata (RESOLVING stage)
        progressCallback(ImportJobStatus.RESOLVING, 0)

        var metadataTitle = "Unknown Title"
        var metadataArtist: String? = null
        var metadataSourceId: String? = (resolved as? SourceResolutionResult.Supported)?.mediaId
        var metadataArtworkUrl: String? = null
        val metadataPlatform = if (resolved is SourceResolutionResult.Supported) {
            resolved.platformDisplayName
        } else {
            when {
                canonicalUrl.contains("instagram.com") -> "Instagram"
                canonicalUrl.contains("youtube.com") || canonicalUrl.contains("youtu.be") -> "YouTube"
                canonicalUrl.contains("soundcloud.com") -> "SoundCloud"
                else -> "Web"
            }
        }

        try {
            val info = YoutubeDL.getInstance().getInfo(canonicalUrl)
            if (info != null) {
                metadataTitle = info.title ?: "Unknown Title"
                metadataArtist = info.uploader
                if (!info.id.isNullOrBlank()) {
                    metadataSourceId = info.id
                }
                metadataArtworkUrl = info.thumbnail
            }
        } catch (e: Exception) {
            val sanitized = YtDlpErrorClassifier.sanitizeMessage(e.message)
            android.util.Log.w("YtDlpAudioExtractor", "Metadata lookup failed: $sanitized", e)

            val raw = (e.message.orEmpty() + " " + (e.cause?.message.orEmpty())).lowercase()
            // If the failure indicates unreachability, private content, or definite unavailability, fail early
            if (raw.contains("errno 7") || raw.contains("no address associated") ||
                raw.contains("unable to resolve host") || raw.contains("network is unreachable") ||
                raw.contains("connection refused")
            ) {
                throw IllegalStateException(YtDlpErrorClassifier.classifyError(e), e)
            }
            if (YtDlpErrorClassifier.isTrulyPrivateOrLoginRequired(raw)) {
                throw IllegalStateException(YtDlpErrorClassifier.classifyError(e), e)
            }
            if (raw.contains("does not contain a video") || raw.contains("no video formats found") ||
                raw.contains("there is no video in this post")
            ) {
                throw IllegalStateException(YtDlpErrorClassifier.classifyError(e), e)
            }
            if (raw.contains("video unavailable") || raw.contains("this video has been removed") ||
                raw.contains("does not exist") || raw.contains("not found") ||
                raw.contains("this post may have been deleted")
            ) {
                throw IllegalStateException(YtDlpErrorClassifier.classifyError(e), e)
            }
            // If download can still proceed safely, continue with fallback metadata
        }

        // Sanitize metadata
        metadataTitle = metadataTitle.trim().replace(Regex("\\s+"), " ")
        if (metadataTitle.startsWith("https://") || metadataTitle.startsWith("http://") ||
            metadataTitle.isBlank() || metadataTitle == "Unknown Title"
        ) {
            metadataTitle = when (metadataPlatform) {
                "Instagram" -> {
                    if (!metadataSourceId.isNullOrBlank()) "Instagram Reel $metadataSourceId" else "Instagram Reel"
                }
                "YouTube" -> "YouTube Audio"
                else -> "Imported Audio"
            }
        }

        metadataArtist = metadataArtist?.trim()?.replace(Regex("\\s+"), " ")?.removePrefix("@")
        if (metadataArtist.isNullOrBlank()) {
            metadataArtist = if (metadataPlatform == "Instagram") "Instagram Creator" else "Unknown Artist"
        }

        // 2. Download and Convert Audio (DOWNLOADING and CONVERTING stages)
        progressCallback(ImportJobStatus.DOWNLOADING, 0)

        val baseName = "import_${UUID.randomUUID()}"
        val request = YoutubeDLRequest(canonicalUrl).apply {
            addOption("--force-ipv4")
            addOption("-f", "bestaudio/best")
            addOption("-x")
            addOption("--audio-format", "mp3")
            addOption("--audio-quality", "192")
            addOption("--no-playlist")
            addOption("--no-mtime")
            addOption("--socket-timeout", "30")
            val tempOutputPath = File(outputDir, "$baseName.%(ext)s").absolutePath
            addOption("-o", tempOutputPath)
        }

        var currentStage = ImportJobStatus.DOWNLOADING
        var lastProgress = -1

        try {
            YoutubeDL.getInstance().execute(request) { progress, _, line ->
                if (line != null) {
                    if (line.contains("[ExtractAudio]") || line.contains("[ffmpeg]") || line.contains("Post-process")) {
                        if (currentStage != ImportJobStatus.CONVERTING) {
                            currentStage = ImportJobStatus.CONVERTING
                            progressCallback(ImportJobStatus.CONVERTING, 0)
                        }
                    } else if (line.contains("[download]")) {
                        val p = progress.toInt().coerceIn(0, 100)
                        if (p != lastProgress) {
                            lastProgress = p
                            progressCallback(ImportJobStatus.DOWNLOADING, p)
                        }
                    }
                } else {
                    val p = progress.toInt().coerceIn(0, 100)
                    if (p != lastProgress) {
                        lastProgress = p
                        progressCallback(ImportJobStatus.DOWNLOADING, p)
                    }
                }
            }

            val extractedFile = outputDir.listFiles()?.find {
                it.nameWithoutExtension == baseName && it.exists() && it.length() > 0
            }

            if (extractedFile != null) {
                progressCallback(ImportJobStatus.SAVING, 90)

                ExtractedMedia(
                    sourceUrl = canonicalUrl,
                    sourceId = metadataSourceId,
                    platform = metadataPlatform,
                    title = metadataTitle,
                    artist = metadataArtist,
                    artworkUrl = metadataArtworkUrl,
                    file = extractedFile
                )
            } else {
                throw IllegalStateException("Audio conversion failed: no output file generated")
            }
        } catch (e: Exception) {
            val sanitized = YtDlpErrorClassifier.sanitizeMessage(e.message)
            android.util.Log.e("YtDlpAudioExtractor", "Download and extraction failed: $sanitized", e)

            // Delete any partial files generated during failed extraction
            outputDir.listFiles()?.filter { it.name.startsWith(baseName) }?.forEach {
                try { it.delete() } catch (_: Exception) {}
            }

            if (e is IllegalStateException) throw e

            val classified = YtDlpErrorClassifier.classifyError(e)
            throw IllegalStateException(classified, e)
        }
    }
}
