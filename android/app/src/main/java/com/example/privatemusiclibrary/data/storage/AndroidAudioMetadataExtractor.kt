package com.example.privatemusiclibrary.data.storage

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.privatemusiclibrary.domain.usecase.AudioMetadata
import com.example.privatemusiclibrary.domain.usecase.AudioMetadataExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AndroidAudioMetadataExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) : AudioMetadataExtractor {

    override suspend fun extractMetadata(uri: String): AudioMetadata? = withContext(Dispatchers.IO) {
        val parsedUri = Uri.parse(uri)
        val retriever = MediaMetadataRetriever()
        var title = "Unknown Title"
        var artist: String? = null
        var album: String? = null
        var durationMs: Long? = null
        
        try {
            retriever.setDataSource(context, parsedUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let { durationMs = it }
        } catch (e: Exception) {
            // Ignore, proceed with fallback
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {}
        }
        
        val document = try {
            DocumentFile.fromSingleUri(context, parsedUri)
        } catch (e: Exception) { null }
        
        if (title == "Unknown Title") {
            title = document?.name?.substringBeforeLast(".") ?: "Unknown Title"
        }
        
        val sizeBytes = document?.length()
        val mimeType = document?.type ?: "audio/mpeg"
        
        AudioMetadata(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            mimeType = mimeType,
            sizeBytes = sizeBytes
        )
    }
}
