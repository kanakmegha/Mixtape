package com.example.privatemusiclibrary.data.extraction

import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import com.example.privatemusiclibrary.domain.extraction.ExtractedMedia
import com.example.privatemusiclibrary.domain.model.ImportJobStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import javax.inject.Inject

class DirectAudioUrlExtractor @Inject constructor() : AudioExtractor {

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf(
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus"
        )
    }

    override fun supports(url: String): Boolean {
        val cleanPath = try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            uri.path?.lowercase() ?: return false
        } catch (_: Exception) {
            val stripped = url.substringBefore("?").substringBefore("#").lowercase()
            if (!stripped.startsWith("http://") && !stripped.startsWith("https://")) return false
            stripped.substringAfterLast("/", "")
        }

        val extension = cleanPath.substringAfterLast(".", "")
        return SUPPORTED_EXTENSIONS.contains(extension)
    }

    override suspend fun extract(
        url: String, 
        outputDir: File, 
        progressCallback: (stage: ImportJobStatus, progress: Int) -> Unit
    ): ExtractedMedia {
        return withContext(Dispatchers.IO) {
            progressCallback(ImportJobStatus.VALIDATING, 0)

            var connection: HttpURLConnection? = null
            
            val filenameFromUrl = try {
                val uri = URI(url)
                uri.path?.substringAfterLast("/")?.ifBlank { null } ?: "audio"
            } catch (_: Exception) {
                url.substringBefore("?").substringBefore("#").substringAfterLast("/")
            }

            val title = if (filenameFromUrl.contains(".")) {
                filenameFromUrl.substringBeforeLast(".")
            } else {
                "Direct Audio"
            }

            val ext = if (filenameFromUrl.contains(".")) {
                filenameFromUrl.substringAfterLast(".")
            } else {
                "mp3"
            }
            
            val tempFile = File(outputDir, "import_${UUID.randomUUID()}.$ext")
            
            try {
                progressCallback(ImportJobStatus.DOWNLOADING, 0)

                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mixtape/1.0 (Android)")
                }
                
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("Server returned HTTP $responseCode ${connection.responseMessage}")
                }
                
                val fileLength = connection.contentLengthLong
                var lastReportedProgress = -1
                
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val data = ByteArray(16384)
                        var total: Long = 0
                        var count: Int
                        
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (fileLength > 0) {
                                val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                                if (progress != lastReportedProgress) {
                                    lastReportedProgress = progress
                                    progressCallback(ImportJobStatus.DOWNLOADING, progress)
                                }
                            } else {
                                progressCallback(ImportJobStatus.DOWNLOADING, -1)
                            }
                        }
                    }
                }

                if (!tempFile.exists() || tempFile.length() == 0L) {
                    throw IllegalStateException("Download completed but output file is empty")
                }
                
                progressCallback(ImportJobStatus.SAVING, 90)

                ExtractedMedia(
                    sourceUrl = url,
                    sourceId = null,
                    platform = "Direct",
                    title = title.replace("_", " ").replace("-", " ").trim(),
                    artist = "Unknown Artist",
                    artworkUrl = null,
                    file = tempFile
                )
            } catch (e: Exception) {
                // Ensure partial file is deleted so failed downloads don't linger on disk
                if (tempFile.exists()) {
                    try { tempFile.delete() } catch (_: Exception) {}
                }
                throw e
            } finally {
                connection?.disconnect()
            }
        }
    }
}
