package com.example.privatemusiclibrary.data.extraction

import com.example.privatemusiclibrary.domain.extraction.AudioExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

class DirectAudioUrlExtractor @Inject constructor() : AudioExtractor {

    override fun supports(url: String): Boolean {
        // Support direct HTTP/HTTPS links to standard audio extensions
        val lowerUrl = url.lowercase()
        return (lowerUrl.startsWith("http://") || lowerUrl.startsWith("https://")) &&
                (lowerUrl.endsWith(".mp3") || 
                 lowerUrl.endsWith(".wav") || 
                 lowerUrl.endsWith(".ogg") || 
                 lowerUrl.endsWith(".m4a") ||
                 lowerUrl.endsWith(".flac"))
    }

    override suspend fun extract(url: String, outputDestination: File, progressCallback: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                
                if (connection.responseCode !in 200..299) {
                    throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
                }
                
                val fileLength = connection.contentLength
                
                connection.inputStream.use { input ->
                    FileOutputStream(outputDestination).use { output ->
                        val data = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (fileLength > 0) {
                                val progress = ((total * 100) / fileLength).toInt()
                                progressCallback(progress)
                            } else {
                                // Indeterminate
                                progressCallback(-1)
                            }
                        }
                    }
                }
            } finally {
                connection?.disconnect()
            }
        }
    }
}
