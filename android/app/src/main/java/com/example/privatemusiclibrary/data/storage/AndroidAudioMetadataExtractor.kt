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
        
        var displayName: String? = null
        var querySizeBytes: Long? = null
        var queryMimeType: String? = null

        try {
            context.contentResolver.query(
                parsedUri,
                arrayOf(android.provider.OpenableColumns.DISPLAY_NAME, android.provider.OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1 && !cursor.isNull(nameIdx)) {
                        displayName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        querySizeBytes = cursor.getLong(sizeIdx)
                    }
                }
            }
            queryMimeType = context.contentResolver.getType(parsedUri)
        } catch (_: Exception) {}

        val document = try {
            DocumentFile.fromSingleUri(context, parsedUri)
        } catch (e: Exception) { null }
        
        if (title == "Unknown Title" || title.isBlank()) {
            val fallbackName = displayName ?: document?.name
            if (!fallbackName.isNullOrBlank()) {
                title = fallbackName.substringBeforeLast(".")
            }
        }
        
        val sizeBytes = querySizeBytes ?: document?.length()
        val mimeType = queryMimeType ?: document?.type ?: "audio/mpeg"
        
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
