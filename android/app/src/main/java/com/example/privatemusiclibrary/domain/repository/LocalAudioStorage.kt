package com.example.privatemusiclibrary.domain.repository

import java.io.File
import java.io.InputStream

sealed class StorageResult {
    data class Success(val uri: String) : StorageResult()
    data class Error(val error: StorageError) : StorageResult()
}

sealed class StorageError : Exception() {
    object PermissionDenied : StorageError()
    object NotFound : StorageError()
    object InsufficientSpace : StorageError()
    object WriteFailed : StorageError()
    object ReadFailed : StorageError()
    object DeleteFailed : StorageError()
    data class Unknown(val causeException: Throwable?) : StorageError()
}

interface LocalAudioStorage {
    suspend fun save(source: File, destinationName: String): StorageResult
    suspend fun delete(uri: String): Boolean
    suspend fun exists(uri: String): Boolean
    suspend fun size(uri: String): Long?
    fun open(uri: String): InputStream
    suspend fun listAudioFiles(): List<String>
}
