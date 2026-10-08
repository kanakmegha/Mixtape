package com.example.privatemusiclibrary.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.privatemusiclibrary.domain.repository.LocalAudioStorage
import com.example.privatemusiclibrary.domain.repository.StorageError
import com.example.privatemusiclibrary.domain.repository.StorageResult
import com.example.privatemusiclibrary.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import javax.inject.Inject

class SafLocalAudioStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : LocalAudioStorage {

    override suspend fun save(source: File, destinationName: String): StorageResult = withContext(Dispatchers.IO) {
        try {
            val settings = settingsRepository.getSettings()
            val uriStr = settings.storageUri ?: return@withContext StorageResult.Error(StorageError.NotFound)
            val treeUri = Uri.parse(uriStr)
            
            val rootDir = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDir == null || !rootDir.exists() || !rootDir.canWrite()) {
                return@withContext StorageResult.Error(StorageError.PermissionDenied)
            }
            
            val sanitizedName = sanitizeFilename(destinationName)
            val newFile = rootDir.createFile("audio/mpeg", sanitizedName)
                ?: return@withContext StorageResult.Error(StorageError.WriteFailed)
            
            context.contentResolver.openOutputStream(newFile.uri)?.use { outStream ->
                source.inputStream().use { inStream ->
                    inStream.copyTo(outStream)
                }
            } ?: return@withContext StorageResult.Error(StorageError.WriteFailed)
            
            StorageResult.Success(newFile.uri.toString())
        } catch (e: Exception) {
            StorageResult.Error(StorageError.Unknown(e))
        }
    }

    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = DocumentFile.fromSingleUri(context, Uri.parse(uri))
            document?.delete() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun exists(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val document = DocumentFile.fromSingleUri(context, Uri.parse(uri))
            document?.exists() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun size(uri: String): Long? = withContext(Dispatchers.IO) {
        try {
            val document = DocumentFile.fromSingleUri(context, Uri.parse(uri))
            if (document?.exists() == true) document.length() else null
        } catch (e: Exception) {
            null
        }
    }

    override fun open(uri: String): InputStream {
        return context.contentResolver.openInputStream(Uri.parse(uri))
            ?: throw Exception("Could not open input stream")
    }
    
    override suspend fun listAudioFiles(): List<String> = withContext(Dispatchers.IO) {
        val settings = settingsRepository.getSettings()
        val uriStr = settings.storageUri ?: return@withContext emptyList()
        val treeUri = Uri.parse(uriStr)
        val rootDir = DocumentFile.fromTreeUri(context, treeUri)
        
        if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory) {
            return@withContext emptyList()
        }
        
        val validExtensions = listOf("mp3", "m4a", "aac", "wav", "flac", "ogg")
        rootDir.listFiles()
            .filter { it.isFile && validExtensions.any { ext -> it.name?.endsWith(ext, ignoreCase = true) == true } }
            .map { it.uri.toString() }
    }
    
    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
