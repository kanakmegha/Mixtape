package com.example.privatemusiclibrary.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import com.example.privatemusiclibrary.domain.repository.StorageError
import com.example.privatemusiclibrary.domain.repository.StorageResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class DefaultLocalAudioStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : LocalAudioStorage {

    override suspend fun save(source: File, destinationName: String): StorageResult = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettings()
            val customUriStr = settings.storageUri

            // If user configured a custom SAF folder, attempt saving to it
            if (!customUriStr.isNullOrBlank() && customUriStr != "default") {
                val safResult = saveToSaf(source, destinationName, customUriStr)
                if (safResult is StorageResult.Success) {
                    return@withContext safResult
                }
                // If SAF write failed due to missing permission, fall back to default Music/Mixtape/
            }

            // Default destination: Music/Mixtape/
            saveToDefaultMusicDirectory(source, destinationName)
        } catch (e: Exception) {
            StorageResult.Error(StorageError.Unknown(e))
        }
    }

    private fun saveToDefaultMusicDirectory(source: File, destinationName: String): StorageResult {
        val sanitizedName = sanitizeFilename(destinationName)
        val mimeType = getMimeType(sanitizedName)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ (API 29+): Scoped Storage via MediaStore with RELATIVE_PATH and IS_PENDING
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, sanitizedName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, com.example.privatemusiclibrary.BuildConfig.AUDIO_RELATIVE_PATH)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = context.contentResolver.insert(collection, values)
                ?: return StorageResult.Error(StorageError.WriteFailed)

            try {
                context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                    source.inputStream().use { inStream ->
                        inStream.copyTo(outStream)
                    }
                } ?: throw IOException("Could not open output stream for $itemUri")

                // Mark available after successful write
                val publishValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.IS_PENDING, 0)
                }
                context.contentResolver.update(itemUri, publishValues, null, null)
                StorageResult.Success(itemUri.toString())
            } catch (e: Exception) {
                // Remove incomplete entry if writing fails
                try {
                    context.contentResolver.delete(itemUri, null, null)
                } catch (_: Exception) {}
                StorageResult.Error(StorageError.WriteFailed)
            }
        } else {
            // Android 7.0 - 9.0 (API 24-28): Legacy public external storage
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val mixtapeDir = File(musicDir, com.example.privatemusiclibrary.BuildConfig.AUDIO_STORAGE_DIRECTORY)
            if (!mixtapeDir.exists() && !mixtapeDir.mkdirs()) {
                if (!mixtapeDir.exists()) {
                    return StorageResult.Error(StorageError.PermissionDenied)
                }
            }

            val tempFile = File(mixtapeDir, ".temp_${System.currentTimeMillis()}_$sanitizedName")
            val targetFile = File(mixtapeDir, sanitizedName)

            try {
                source.inputStream().use { inStream ->
                    tempFile.outputStream().use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                if (!tempFile.renameTo(targetFile)) {
                    throw IOException("Failed to rename temp file to target file")
                }

                // Scan file to register with MediaStore and obtain content URI
                var scannedUri: Uri? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    scannedUri = uri
                    latch.countDown()
                }
                latch.await(3, java.util.concurrent.TimeUnit.SECONDS)

                val finalUri = scannedUri ?: Uri.fromFile(targetFile)
                StorageResult.Success(finalUri.toString())
            } catch (e: Exception) {
                if (tempFile.exists()) tempFile.delete()
                StorageResult.Error(StorageError.WriteFailed)
            }
        }
    }

    private fun saveToSaf(source: File, destinationName: String, treeUriStr: String): StorageResult {
        try {
            val treeUri = Uri.parse(treeUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDir == null || !rootDir.exists() || !rootDir.canWrite()) {
                return StorageResult.Error(StorageError.PermissionDenied)
            }

            val sanitizedName = sanitizeFilename(destinationName)
            val mimeType = getMimeType(sanitizedName)
            val newFile = rootDir.createFile(mimeType, sanitizedName)
                ?: return StorageResult.Error(StorageError.WriteFailed)

            context.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                source.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: run {
                newFile.delete()
                return StorageResult.Error(StorageError.WriteFailed)
            }

            return StorageResult.Success(newFile.uri.toString())
        } catch (e: Exception) {
            return StorageResult.Error(StorageError.Unknown(e))
        }
    }

    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parsedUri = Uri.parse(uri)
            when (parsedUri.scheme) {
                "file" -> {
                    val path = parsedUri.path ?: return@withContext false
                    File(path).delete()
                }
                "content" -> {
                    // Try ContentResolver delete first (works for MediaStore and DocumentProvider)
                    val rowsDeleted = try {
                        context.contentResolver.delete(parsedUri, null, null)
                    } catch (_: Exception) {
                        0
                    }
                    if (rowsDeleted > 0) return@withContext true

                    // Fallback for SAF DocumentFile
                    val doc = try {
                        DocumentFile.fromSingleUri(context, parsedUri)
                    } catch (_: Exception) {
                        null
                    }
                    if (doc != null && doc.exists()) {
                        return@withContext doc.delete()
                    }
                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val parsedUri = Uri.parse(uri)
            when (parsedUri.scheme) {
                "file" -> {
                    val path = parsedUri.path ?: return@withContext false
                    File(path).exists()
                }
                "content" -> {
                    // Check via ContentResolver query
                    var found = false
                    try {
                        context.contentResolver.query(
                            parsedUri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            found = cursor.moveToFirst()
                        }
                    } catch (_: Exception) {}
                    if (found) return@withContext true

                    // Check via DocumentFile (SAF)
                    val doc = try {
                        DocumentFile.fromSingleUri(context, parsedUri)
                    } catch (_: Exception) {
                        null
                    }
                    if (doc != null && doc.exists()) return@withContext true

                    // Check via AssetFileDescriptor as fallback
                    try {
                        context.contentResolver.openAssetFileDescriptor(parsedUri, "r")?.use {
                            return@withContext true
                        }
                    } catch (_: Exception) {}

                    false
                }
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun size(uri: String): Long? = withContext(Dispatchers.IO) {
        try {
            val parsedUri = Uri.parse(uri)
            when (parsedUri.scheme) {
                "file" -> {
                    val path = parsedUri.path ?: return@withContext null
                    val f = File(path)
                    if (f.exists()) f.length() else null
                }
                "content" -> {
                    var querySize: Long? = null
                    try {
                        context.contentResolver.query(
                            parsedUri,
                            arrayOf(OpenableColumns.SIZE),
                            null,
                            null,
                            null
                        )?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                                if (idx != -1 && !cursor.isNull(idx)) {
                                    querySize = cursor.getLong(idx)
                                }
                            }
                        }
                    } catch (_: Exception) {}
                    if (querySize != null && querySize!! > 0L) return@withContext querySize

                    val doc = try {
                        DocumentFile.fromSingleUri(context, parsedUri)
                    } catch (_: Exception) {
                        null
                    }
                    if (doc != null && doc.exists()) {
                        val len = doc.length()
                        if (len > 0L) return@withContext len
                    }

                    try {
                        context.contentResolver.openAssetFileDescriptor(parsedUri, "r")?.use {
                            val len = it.length
                            if (len > 0L) return@withContext len
                        }
                    } catch (_: Exception) {}

                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun open(uri: String): InputStream {
        val parsedUri = Uri.parse(uri)
        return context.contentResolver.openInputStream(parsedUri)
            ?: throw IOException("Could not open input stream for $uri")
    }

    override suspend fun listAudioFiles(): List<String> = withContext(Dispatchers.IO) {
        val resultUris = LinkedHashSet<String>()
        val validExtensions = listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")

        // 1. Scan default Music/Mixtape/ via MediaStore
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.RELATIVE_PATH
                )
                val relPath = com.example.privatemusiclibrary.BuildConfig.AUDIO_RELATIVE_PATH
                val selection = "(${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?) AND ${MediaStore.Audio.Media.IS_PENDING} = 0"
                val selectionArgs = arrayOf("$relPath%", relPath)

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                    val nameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = if (nameCol != -1) cursor.getString(nameCol) else null
                        if (name == null || validExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }) {
                            val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                            resultUris.add(contentUri.toString())
                        }
                    }
                }
            } else {
                // Android 7.0 - 9.0: query MediaStore or check public directory
                val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val mixtapeDir = File(musicDir, com.example.privatemusiclibrary.BuildConfig.AUDIO_STORAGE_DIRECTORY)
                if (mixtapeDir.exists() && mixtapeDir.isDirectory) {
                    mixtapeDir.listFiles()?.filter { file ->
                        file.isFile && validExtensions.any { ext -> file.name.endsWith(ext, ignoreCase = true) }
                    }?.forEach { file ->
                        resultUris.add(Uri.fromFile(file).toString())
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DefaultAudioStorage", "Error querying MediaStore for ${com.example.privatemusiclibrary.BuildConfig.AUDIO_RELATIVE_PATH}: ${e.message}")
        }

        // 2. Scan custom SAF folder if one was configured (backward compatibility)
        try {
            val settings = settingsRepository.getSettings()
            val customUriStr = settings.storageUri
            if (!customUriStr.isNullOrBlank() && customUriStr != "default") {
                val treeUri = Uri.parse(customUriStr)
                val rootDir = DocumentFile.fromTreeUri(context, treeUri)
                if (rootDir != null && rootDir.exists() && rootDir.isDirectory) {
                    rootDir.listFiles()
                        .filter { it.isFile && validExtensions.any { ext -> it.name?.endsWith(ext, ignoreCase = true) == true } }
                        .forEach { resultUris.add(it.uri.toString()) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("DefaultAudioStorage", "Error querying SAF folder: ${e.message}")
        }

        resultUris.toList()
    }

    private fun sanitizeFilename(name: String): String {
        val clean = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (clean.contains(".")) clean else "$clean.mp3"
    }

    private fun getMimeType(filename: String): String {
        return when {
            filename.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            filename.endsWith(".aac", ignoreCase = true) -> "audio/aac"
            filename.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            filename.endsWith(".flac", ignoreCase = true) -> "audio/flac"
            filename.endsWith(".ogg", ignoreCase = true) || filename.endsWith(".opus", ignoreCase = true) -> "audio/ogg"
            else -> "audio/mpeg"
        }
    }
}
