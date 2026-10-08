package com.example.privatemusiclibrary.domain.extraction

import android.net.Uri
import java.io.File

interface AudioExtractor {
    /**
     * @return true if this extractor can handle the given URL.
     */
    fun supports(url: String): Boolean

    /**
     * Extracts/downloads the audio from the given URL and writes it to the output destination.
     * @param url The supported URL.
     * @param outputDestination A temporary file to write the extracted audio to.
     * @param progressCallback Callback reporting percentage complete (0 to 100).
     */
    suspend fun extract(
        url: String, 
        outputDestination: File,
        progressCallback: (Int) -> Unit
    )
}
