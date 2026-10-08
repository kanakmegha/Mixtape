package com.example.privatemusiclibrary.domain.extraction

import android.net.Uri
import java.io.File

data class ExtractedMedia(
    val sourceUrl: String,
    val sourceId: String?,
    val platform: String?,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val file: File
)

interface AudioExtractor {
    /**
     * @return true if this extractor can handle the given URL.
     */
    fun supports(url: String): Boolean

    /**
     * Extracts/downloads the audio from the given URL and writes it to a temporary file.
     * @param url The supported URL.
     * @param outputDir The directory to place the temporary extracted file.
     * @param progressCallback Callback reporting percentage complete (0 to 100).
     * @return ExtractedMedia containing the temporary file and associated metadata.
     */
    suspend fun extract(
        url: String, 
        outputDir: File,
        progressCallback: (Int) -> Unit
    ): ExtractedMedia
}
